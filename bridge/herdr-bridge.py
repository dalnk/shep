#!/usr/bin/env python3
"""
herdr-bridge: tiny TCP <-> Unix-socket forwarder for the herdr control socket.

The herdr daemon speaks newline-delimited JSON over a Unix domain socket
(/Users/dalnk/.config/herdr/herdr.sock). Android phones on the LAN cannot
open a Unix socket, so this bridge exposes the same protocol on a TCP
port (default 0.0.0.0:8765). The phone app points at neo.local:8765 and
speaks the exact same JSON-RPC over NDJSON that herdr itself speaks.

Design notes:
  * Multiplexed: many TCP clients share one upstream socket. Each client
    gets its own request-id namespace; upstream responses are routed back
    to the right client by matching `id`. Events from upstream are
    fanned out to every connected client.
  * Pure stdlib, no pip install. Works on macOS where herdr runs.
  * A SIGINT/SIGTERM closes the listener and waits for in-flight reads.
  * The upstream connection is automatically (re)opened on failure.

This is intentionally minimal. When herdr ships a proper remote-control
server (herdr serve --port N or similar), this bridge goes away.
"""

from __future__ import annotations

import argparse
import glob
import json
import logging
import os
import re
import select
import signal
import socket
import sys
import threading
import time
import uuid
from typing import Optional

DEFAULT_SOCK = os.path.expanduser("~/.config/herdr/herdr.sock")
DEFAULT_HOST = "0.0.0.0"
DEFAULT_PORT = 8765
SUBSCRIPTION_WINDOW = 3.0  # seconds per long-poll connection

log = logging.getLogger("herdr-bridge")


# ---------------------------------------------------------------------------
# Upstream: herdr Unix socket — single-shot per subscription, long-poll style
# ---------------------------------------------------------------------------
#
# herdr's Unix socket is single-shot: each connection accepts a small
# burst of subscribe requests, drains events for a short window, and
# then closes. To deliver a continuous event stream to TCP clients we
# run a long-poll loop: open a fresh connection, re-send active
# subscriptions, read events for SUBSCRIPTION_WINDOW seconds, close,
# repeat. Request/response traffic (workspace.list, pane.read, etc.)
# is multiplexed onto the same long-poll connection when possible;
# if a long-poll is in flight, request traffic briefly opens its own
# short-lived connection.
# ---------------------------------------------------------------------------


class Upstream:
    """Multiplexes TCP clients onto a long-poll loop against herdr.

    Three concerns:
      * Synchronous request/response: workspace.list, pane.read, etc.
        A caller gets a result frame back, matched by `id`.
      * Long-poll event stream: a background thread repeatedly opens
        a connection, re-sends active subscriptions, drains events for
        SUBSCRIPTION_WINDOW seconds, closes. Events fan out to clients.
      * Client management: clients attach/detach; we keep their
        sockets open and write event frames to each of them.
    """

    # SUBSCRIPTION_WINDOW is the module-level constant.

    def __init__(self, sock_path: str):
        self._sock_path = sock_path
        self._lock = threading.Lock()  # serializes all herdr I/O
        self._pending: dict[str, "threading.Event"] = {}
        self._results: dict[str, dict] = {}
        self._client_send_lock = threading.Lock()  # protects fan-out set
        self._clients: set["Client"] = set()
        self._stop = threading.Event()
        self._sub_thread: Optional[threading.Thread] = None
        # Subscriptions requested by clients. Keyed by sorted-JSON of
        # the params so identical subs deduplicate. The long-poll loop
        # re-sends these every iteration.
        self._subs_lock = threading.Lock()
        self._active_subs: dict[str, dict] = {}
        self._waiting_tracker: dict[str, float] = {}  # pane_id -> timestamp (time.time()) when it became blocked/done

    # ----- lifecycle -----

    def start(self) -> None:
        if self._sub_thread is not None:
            return
        self._sub_thread = threading.Thread(
            target=self._subscription_loop, name="herdr-sub-loop", daemon=True
        )
        self._sub_thread.start()
        self._reconcile_thread = threading.Thread(
            target=self._reconcile_loop, name="herdr-reconcile-loop", daemon=True
        )
        self._reconcile_thread.start()

    def stop(self) -> None:
        self._stop.set()

    # ----- client management -----

    def attach(self, client: "Client") -> None:
        with self._client_send_lock:
            self._clients.add(client)

    def detach(self, client: "Client") -> None:
        with self._client_send_lock:
            self._clients.discard(client)

    # ----- request / response -----

    def request(self, payload: dict, timeout: float = 30.0) -> dict:
        """Send a request to herdr and return the matching response frame.

        `payload` is the client's frame (may or may not have an id). The
        bridge assigns a fresh id, sends, and returns the response with
        the bridge-assigned id (caller is responsible for matching via
        the Client wrapper which knows the client's id).
        """
        if "jsonrpc" not in payload:
            payload = {**payload, "jsonrpc": "2.0"}
        bridge_id = uuid.uuid4().hex
        herdr_frame = {**payload, "id": bridge_id}
        # _round_trip takes the lock itself for the write phase.
        return self._round_trip(herdr_frame, want_id=bridge_id, window=timeout)

    def notify(self, payload: dict) -> Optional[dict]:
        """Send a frame to herdr and return the response frame (if any).

        Used for events.subscribe: the bridge records the subscription
        for the long-poll loop. The sub loop will do the actual sending
        every cycle; we do not send it here to avoid duplicate subs.
        """
        if "jsonrpc" not in payload:
            payload = {**payload, "jsonrpc": "2.0"}
        if payload.get("method") == "events.subscribe":
            try:
                key = json.dumps(payload.get("params", {}), sort_keys=True)
                with self._subs_lock:
                    self._active_subs[key] = payload
                log.info("subscribed: %s (now %d active subs)", payload.get("params", {}), len(self._active_subs))
            except (TypeError, ValueError) as exc:
                log.warning("could not record sub: %s", exc)
            # Return a synthetic ack so the caller doesn't hang.
            return {
                "jsonrpc": "2.0",
                "result": {"type": "subscription_started", "params": payload.get("params", {})},
            }
        # For non-subscribe notifications, send through to herdr once.
        bridge_id = uuid.uuid4().hex
        herdr_frame = {**payload, "id": bridge_id}
        return self._round_trip(herdr_frame, want_id=bridge_id, window=5.0)

    def _round_trip(self, payload: dict, want_id: Optional[str], window: float) -> Optional[dict]:
        try:
            s = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
            s.settimeout(window)
            s.connect(self._sock_path)
        except OSError as exc:
            log.warning("upstream connect failed: %s", exc)
            return None
        try:
            line = (json.dumps(payload, separators=(",", ":")) + "\n").encode()
            # Serialized send: only the write needs the lock. Reads
            # happen without the lock so we don't block other traffic
            # (notably the sub loop, which is the dominant user of the
            # upstream socket during long drains).
            with self._lock:
                try:
                    s.sendall(line)
                except OSError as exc:
                    log.warning("upstream write failed: %s", exc)
                    return None

            buf = b""
            deadline = time.monotonic() + window
            result: Optional[dict] = None
            while time.monotonic() < deadline:
                try:
                    chunk = s.recv(65536)
                except socket.timeout:
                    break
                if not chunk:
                    break
                buf += chunk
                while b"\n" in buf:
                    raw, _, buf = buf.partition(b"\n")
                    if not raw.strip():
                        continue
                    try:
                        frame = json.loads(raw.decode("utf-8"))
                    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
                        log.warning("dropping malformed frame: %s", exc)
                        continue
                    # If this is the response we asked for, capture it
                    # and stop. Other frames (subscription acks for our
                    # own subs, or events) get dispatched as usual.
                    if want_id and frame.get("id") == want_id:
                        result = frame
                        # Drain a tiny bit more in case more frames
                        # arrive immediately, but cap to keep latency.
                        deadline = min(deadline, time.monotonic() + 0.2)
                    else:
                        self._dispatch_frame(frame)
                if result is not None:
                    break
            return result
        finally:
            try:
                s.close()
            except OSError:
                pass

    def _dispatch_frame(self, frame: dict) -> None:
        frame_id = frame.get("id")
        if frame_id and frame_id in self._pending:
            self._results[frame_id] = frame
            self._pending[frame_id].set()
            return
        # Event fan-out
        with self._client_send_lock:
            dead: list[Client] = []
            for client in self._clients:
                try:
                    client.send_event(frame)
                except OSError:
                    dead.append(client)
            for client in dead:
                self._clients.discard(client)
                client.close()

    # ----- internals: subscription loop -----

    def _subscription_loop(self) -> None:
        """Continuously re-send active subscriptions and drain events.

        Each cycle: open a fresh socket, write all subs under the lock,
        then drain (lock-free) for the window. This way the long drain
        does not block other clients from talking to herdr.
        """
        while not self._stop.is_set():
            with self._subs_lock:
                subs = list(self._active_subs.values())
            if not subs:
                time.sleep(0.2)
                continue
            try:
                s = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
                s.settimeout(SUBSCRIPTION_WINDOW)
                s.connect(self._sock_path)
            except OSError as exc:
                log.debug("sub-loop connect failed: %s", exc)
                time.sleep(0.5)
                continue
            try:
                # Write phase: take the lock briefly so writes to
                # multiple concurrent sockets don't interleave on herdr.
                with self._lock:
                    for sub in subs:
                        # herdr requires every frame to have an id, even
                        # for subscriptions. Assign a fresh one; we ignore
                        # the subscription_started ack.
                        payload = {**sub, "id": uuid.uuid4().hex}
                        line = (json.dumps(payload, separators=(",", ":")) + "\n").encode()
                        try:
                            s.sendall(line)
                        except OSError as exc:
                            log.debug("sub write failed: %s", exc)
                            break
                # Drain phase: lock-free. Events are dispatched to all
                # attached clients.
                buf = b""
                while True:
                    try:
                        chunk = s.recv(65536)
                    except socket.timeout:
                        break
                    if not chunk:
                        break
                    buf += chunk
                    while b"\n" in buf:
                        raw, _, buf = buf.partition(b"\n")
                        if not raw.strip():
                            continue
                        try:
                            frame = json.loads(raw.decode("utf-8"))
                        except (UnicodeDecodeError, json.JSONDecodeError):
                            continue
                        # Skip subscription_started acks; everything
                        # else is a real event for the clients.
                        if frame.get("id"):
                            continue
                        self._dispatch_frame(frame)
            finally:
                try:
                    s.close()
                except OSError:
                    pass

    # ----- internals: under/_ agent reconciliation loop -----

    def _reconcile_loop(self) -> None:
        """Continuously check for panes running `under` or `_` and sync their state with Herdr."""
        state_cache: dict[str, str] = {}
        while not self._stop.is_set():
            try:
                self._reconcile_under_panes(state_cache)
            except Exception as exc:
                log.debug("reconcile error: %s", exc)
            self._stop.wait(1.5)

    def _reconcile_under_panes(self, state_cache: dict[str, str]) -> None:
        list_id = uuid.uuid4().hex
        panes_frame = self._round_trip(
            {"jsonrpc": "2.0", "method": "pane.list", "params": {}, "id": list_id},
            want_id=list_id,
            window=2.0,
        )
        if not panes_frame or not isinstance(panes_frame.get("result"), dict):
            return
        panes = panes_frame["result"].get("panes", [])
        current_under_panes: set[str] = set()
        for p in panes:
            pane_id = p.get("pane_id")
            if not pane_id:
                continue
            info_id = uuid.uuid4().hex
            pinfo_frame = self._round_trip(
                {"jsonrpc": "2.0", "method": "pane.process_info", "params": {"pane_id": pane_id}, "id": info_id},
                want_id=info_id,
                window=2.0,
            )
            if not pinfo_frame or not isinstance(pinfo_frame.get("result"), dict):
                continue
            pinfo = pinfo_frame["result"].get("process_info", {})
            procs = pinfo.get("foreground_processes", [])
            cmdlines = [proc.get("cmdline", "") for proc in procs]
            is_under = any("under" in c for c in cmdlines)
            if is_under:
                current_under_panes.add(pane_id)
                read_id = uuid.uuid4().hex
                read_frame = self._round_trip(
                    {"jsonrpc": "2.0", "method": "pane.read", "params": {"pane_id": pane_id, "source": "recent", "lines": 4}, "id": read_id},
                    want_id=read_id,
                    window=2.0,
                )
                text = ""
                if read_frame and isinstance(read_frame.get("result"), dict):
                    text = read_frame["result"].get("read", {}).get("text", "")
                text = text.strip()
                is_idle = bool(re.search(r"(_|under)>\s*$", text))
                new_state = "idle" if is_idle else "working"

                herdr_agent = p.get("agent")
                herdr_status = p.get("agent_status")
                if herdr_agent != "_" or herdr_status != new_state or state_cache.get(pane_id) != new_state:
                    state_cache[pane_id] = new_state
                    rep_id = uuid.uuid4().hex
                    self._round_trip(
                        {
                            "jsonrpc": "2.0",
                            "method": "pane.report_agent",
                            "params": {
                                "pane_id": pane_id,
                                "source": "herdr:bridge",
                                "agent": "_",
                                "state": new_state,
                                "seq": int(time.time() * 1000),
                            },
                            "id": rep_id,
                        },
                        want_id=rep_id,
                        window=2.0,
                    )

        for pane_id in list(state_cache.keys()):
            if pane_id not in current_under_panes:
                del state_cache[pane_id]
                rel_id = uuid.uuid4().hex
                self._round_trip(
                    {
                        "jsonrpc": "2.0",
                        "method": "pane.release_agent",
                        "params": {
                            "pane_id": pane_id,
                            "source": "herdr:bridge",
                            "agent": "_",
                            "seq": int(time.time() * 1000),
                        },
                        "id": rel_id,
                    },
                    want_id=rel_id,
                    window=2.0,
                )

    @staticmethod
    def extract_model_shortname(model: Optional[str] = "", agent: Optional[str] = "") -> str:
        """Extract a clean, one-word shortname with no version numbers for the active model."""
        m = (model or "").strip()
        if "/" in m:
            m = m.split("/")[-1]
        if ":" in m:
            m = m.split(":")[0]

        m_lower = m.lower()
        if "astra" in m_lower:
            return "Astra"
        if "fable" in m_lower:
            return "Fable"
        if "nemotron" in m_lower and "ultra" in m_lower:
            return "Nemotron Ultra"
        if "gpt" in m_lower or "chatgpt" in m_lower:
            return "GPT"
        if "claude" in m_lower:
            return "Claude"
        if "gemini" in m_lower:
            return "Gemini"
        if "grok" in m_lower:
            return "Grok"
        if "qwen" in m_lower:
            return "Qwen"
        if "llama" in m_lower:
            return "Llama"
        if "mistral" in m_lower or "mixtral" in m_lower or "codestral" in m_lower:
            return "Mistral"
        if "deepseek" in m_lower:
            return "DeepSeek"
        if "nemotron" in m_lower:
            return "Nemotron"
        if "gemma" in m_lower:
            return "Gemma"
        if "sonnet" in m_lower:
            return "Sonnet"
        if "opus" in m_lower:
            return "Opus"
        if "haiku" in m_lower:
            return "Haiku"
        if "mai" in m_lower:
            return "MAI"
        if "phi" in m_lower:
            return "Phi"
        if "command" in m_lower:
            return "Command"
        if "lfm" in m_lower or "liquid" in m_lower:
            return "Liquid"

        parts = m.replace("_", "-").split("-")
        for p in parts:
            letters = "".join(c for c in p if c.isalpha())
            if letters:
                return letters.capitalize()

        a = (agent or "").strip().lower()
        if a in ("agy", "gemini"):
            return "Gemini"
        if a in ("codex",):
            return "GPT"
        if a in ("claude",):
            return "Claude"
        if a in ("grok",):
            return "Grok"
        if a in ("under", "_"):
            return "Under"
        if a in ("copilot",):
            return "Copilot"
        return a.capitalize() if a else "Agent"

    def get_pane_transcript(self, pane_id: Optional[str]) -> dict:
        """Read structured turns, thinking, and paused question states for a pane."""
        if not pane_id:
            return {"pane_id": "", "turns": [], "has_transcript": False}

        # First find pane details (agent, session) from pane.list
        list_id = uuid.uuid4().hex
        pane_list_frame = self._round_trip(
            {"jsonrpc": "2.0", "method": "pane.list", "params": {}, "id": list_id},
            want_id=list_id,
            window=3.0,
        )
        panes = []
        if pane_list_frame and isinstance(pane_list_frame.get("result"), dict):
            raw_panes = pane_list_frame["result"].get("panes", [])
            panes = self.enrich_panes(raw_panes)
        
        target = None
        for p in panes:
            if p.get("pane_id") == pane_id:
                target = p
                break

        agent = target.get("agent") if target else None
        agent_status = target.get("agent_status") if target else None
        session = target.get("agent_session") if target else {}
        session_id = session.get("value") if isinstance(session, dict) else None

        cwd = target.get("cwd") or target.get("foreground_cwd") if target else None

        kind, path = self._resolve_session_path(agent, session_id, cwd=cwd)
        turns, question, model, context_tokens = self._parse_session_transcript(kind, path)
        raw_title = target.get("title") or target.get("terminal_title_stripped") or target.get("terminal_title") if target else None
        display_agent = "under" if agent == "_" else (agent or "shell")
        cwd_name = os.path.basename((cwd or "").rstrip("/")) if cwd else ""
        if raw_title and not re.match(r"^w\d+:p", raw_title) and not raw_title.startswith("Pane "):
            title = raw_title
        elif cwd_name and cwd_name not in ("dalnk", "Users"):
            title = f"{cwd_name} · {display_agent}"
        else:
            title = f"{display_agent.capitalize()} session" if display_agent != "shell" else f"Terminal"

        if target and (not agent_status or agent_status in ("working", "unknown", "idle")):
            # Check if terminal shows completion/prompt or question
            read_id = uuid.uuid4().hex
            rf = self._round_trip(
                {"jsonrpc": "2.0", "method": "pane.read", "params": {"pane_id": pane_id, "source": "recent", "lines": 6}, "id": read_id},
                want_id=read_id,
                window=1.5,
            )
            rtext = ""
            if rf and isinstance(rf.get("result"), dict):
                rtext = rf["result"].get("read", {}).get("text", "")
            if agent == "codex":
                # Check active rollout file modification time first (goal mode or tools scrolling fast)
                is_file_active = False
                if path and os.path.exists(path):
                    try:
                        if time.time() - os.path.getmtime(path) < 15.0:
                            is_file_active = True
                    except Exception:
                        pass
                if is_file_active:
                    agent_status = "working"
                elif "Working" in rtext or "Thinking" in rtext or "esc to interrupt" in rtext:
                    agent_status = "working"
                elif "› Ask Codex" in rtext or re.search(r"›\s*$", rtext):
                    agent_status = "done"
                    question = None  # Agent is idle at prompt, any pending question was already handled
                elif agent_status == "done" and not re.search(r"›\s*$", rtext):
                    agent_status = "working"
            elif agent == "copilot":
                if "Thinking" in rtext or "Working" in rtext or "Running" in rtext or "esc to cancel" in rtext:
                    agent_status = "working"
                elif "❯" in rtext or "commands · ? help" in rtext or re.search(r"❯\s*$", rtext):
                    agent_status = "done"
                    question = None
            elif agent == "claude":
                if "❯" in rtext or "auto mode on" in rtext:
                    agent_status = "done"
                    question = None
            elif agent in ("under", "_"):
                if re.search(r"(_|under)>\s*$", rtext):
                    agent_status = "done"
                    question = None
            elif agent == "grok":
                if re.search(r">\s*$", rtext) or "Done" in rtext:
                    agent_status = "done"
                    question = None
            elif agent in ("agy", "gemini"):
                if re.search(r">\s*$", rtext) or "? for shortcuts" in rtext or "edit queued messages" in rtext:
                    agent_status = "done"
                    question = None

            if question:
                agent_status = "blocked"

        model_shortname = self.extract_model_shortname(model, display_agent)

        # Extract active action / status text for live thinking indicator
        active_action = None
        if agent_status == "working":
            # Check terminal output first for live running command / tool
            lines = [l.strip() for l in rtext.splitlines() if l.strip()]
            for l in reversed(lines):
                clean = re.sub(r"^[⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏⣾⣽⣻⢿⡿⣟⣯⣷●○◐◑◒◓✻※⚡✨ ]+", "", l).strip()
                if not clean or clean.startswith("─") or clean.startswith(">") or "shortcuts" in clean or "queued messages" in clean or "esc to cancel" in clean or "ctrl+o" in clean:
                    continue
                if re.search(r"\b(Running command|Generating|Thinking|Reading|Editing|Writing|Searching|Analyzing|Testing)\b", clean, re.I):
                    active_action = clean
                    break
                if re.search(r"^[A-Z][a-zA-Z0-9_-]+\(", clean):
                    active_action = clean
                    break
            # Fallback to last tool in transcript
            if not active_action and turns:
                last_turn = turns[-1]
                if last_turn.get("role") == "assistant" and last_turn.get("tools"):
                    active_action = last_turn["tools"][-1]

        resp = {
            "pane_id": pane_id,
            "title": title,
            "agent": display_agent,
            "agent_status": agent_status,
            "model": model,
            "model_shortname": model_shortname,
            "context_tokens": context_tokens,
            "has_transcript": bool(turns),
            "turns": turns,
            "question": question,
        }
        if active_action:
            resp["active_action"] = active_action
        return resp

    def enrich_panes(self, panes: list[dict]) -> list[dict]:
        """Enrich pane.list results with true agent status (done/blocked/working) and human readable titles."""
        enriched = []
        for p in panes:
            p_copy = dict(p)
            pane_id = p_copy.get("pane_id")
            agent = p_copy.get("agent")
            cwd = p_copy.get("cwd") or p_copy.get("foreground_cwd") or ""

            # Standardize title: if title is missing, raw pane_id (e.g. w1:p2), or generic
            raw_title = p_copy.get("terminal_title_stripped") or p_copy.get("terminal_title") or p_copy.get("title") or ""
            cwd_name = os.path.basename(cwd.rstrip("/")) if cwd else ""
            display_agent = "under" if agent == "_" else (agent or "shell")

            if not raw_title or raw_title == pane_id or raw_title.startswith("w1:") or raw_title.startswith("Pane "):
                if cwd_name and cwd_name not in ("dalnk", "Users"):
                    computed_title = f"{cwd_name} · {display_agent}"
                elif display_agent != "shell":
                    computed_title = f"{display_agent.capitalize()} session ({pane_id})"
                else:
                    computed_title = f"Terminal {pane_id}"
            else:
                computed_title = raw_title

            p_copy["title"] = computed_title
            p_copy["terminal_title"] = computed_title
            p_copy["terminal_title_stripped"] = computed_title

            # If agent is codex, claude, under, grok, agy, or copilot, detect if it's waiting for user (done / blocked)
            if pane_id and agent in ("codex", "claude", "_", "under", "agy", "grok", "copilot"):
                read_id = uuid.uuid4().hex
                rf = self._round_trip(
                    {"jsonrpc": "2.0", "method": "pane.read", "params": {"pane_id": pane_id, "source": "recent", "lines": 6}, "id": read_id},
                    want_id=read_id,
                    window=1.0,
                )
                rtext = ""
                if rf and isinstance(rf.get("result"), dict):
                    rtext = rf["result"].get("read", {}).get("text", "")
                
                if agent == "codex":
                    is_file_active = False
                    session = p_copy.get("agent_session") or {}
                    session_id = session.get("value") if isinstance(session, dict) else None
                    _, s_path = self._resolve_session_path(agent, session_id, cwd=cwd)
                    if s_path and os.path.exists(s_path):
                        try:
                            if time.time() - os.path.getmtime(s_path) < 15.0:
                                is_file_active = True
                        except Exception:
                            pass
                    if is_file_active:
                        p_copy["agent_status"] = "working"
                    elif "Working" in rtext or "Thinking" in rtext or "esc to interrupt" in rtext:
                        p_copy["agent_status"] = "working"
                    elif "› Ask Codex" in rtext or re.search(r"›\s*$", rtext):
                        p_copy["agent_status"] = "done"
                    elif p_copy.get("agent_status") == "done" and not re.search(r"›\s*$", rtext):
                        p_copy["agent_status"] = "working"
                elif agent == "copilot":
                    if "Thinking" in rtext or "Working" in rtext or "Running" in rtext or "esc to cancel" in rtext:
                        p_copy["agent_status"] = "working"
                    elif "❯" in rtext or "commands · ? help" in rtext or re.search(r"❯\s*$", rtext):
                        p_copy["agent_status"] = "done"
                    elif p_copy.get("agent_status") == "done" and not re.search(r"❯\s*$", rtext):
                        p_copy["agent_status"] = "working"
                elif agent == "claude":
                    if "❯" in rtext or "auto mode on" in rtext:
                        p_copy["agent_status"] = "done"
                    elif p_copy.get("agent_status") == "done" and "❯" not in rtext:
                        p_copy["agent_status"] = "working"
                elif agent in ("under", "_"):
                    if re.search(r"(_|under)>\s*$", rtext):
                        p_copy["agent_status"] = "done"
                    elif p_copy.get("agent_status") == "done" and not re.search(r"(_|under)>\s*$", rtext):
                        p_copy["agent_status"] = "working"
                elif agent == "grok":
                    if re.search(r">\s*$", rtext) or "Done" in rtext:
                        p_copy["agent_status"] = "done"
                    elif p_copy.get("agent_status") == "done" and ("Thinking" in rtext or "Working" in rtext):
                        p_copy["agent_status"] = "working"
                elif agent in ("agy", "gemini"):
                    if re.search(r">\s*$", rtext) or "? for shortcuts" in rtext or "edit queued messages" in rtext:
                        p_copy["agent_status"] = "done"
                    elif "Thinking" in rtext or "Working" in rtext or "Running command" in rtext or "Generating" in rtext:
                        p_copy["agent_status"] = "working"

            # Track waiting duration stats (how long agent has been waiting for user approval or review)
            final_status = p_copy.get("agent_status")
            now = time.time()
            if pane_id:
                if final_status in ("done", "blocked"):
                    if pane_id not in self._waiting_tracker:
                        # Estimate initial waiting start time from session file mtime if available
                        session = p_copy.get("agent_session") or {}
                        session_id = session.get("value") if isinstance(session, dict) else None
                        _, s_path = self._resolve_session_path(agent, session_id, cwd=cwd)
                        if s_path and os.path.exists(s_path):
                            mtime = os.path.getmtime(s_path)
                            self._waiting_tracker[pane_id] = min(mtime, now)
                        else:
                            self._waiting_tracker[pane_id] = now
                    
                    start_ts = self._waiting_tracker[pane_id]
                    duration = max(0, int(now - start_ts))
                    p_copy["waiting_since_ms"] = int(start_ts * 1000)
                    p_copy["waiting_duration_seconds"] = duration
                else:
                    self._waiting_tracker.pop(pane_id, None)

            enriched.append(p_copy)
        return enriched

    def _resolve_session_path(self, agent: Optional[str], session_id: Optional[str], cwd: Optional[str] = None) -> tuple[Optional[str], Optional[str]]:
        if agent == "codex":
            if session_id:
                matches = glob.glob(os.path.expanduser(f"~/.codex/sessions/**/rollout-*{session_id}*.jsonl"), recursive=True)
                if matches:
                    return ("codex", matches[0])
            if cwd:
                # Find newest codex session matching this cwd
                base_dir = os.path.expanduser("~/.codex/sessions")
                candidates = []
                for f in glob.glob(f"{base_dir}/**/rollout-*.jsonl", recursive=True):
                    try:
                        mtime = os.path.getmtime(f)
                        # Check files modified in the last 7 days
                        if time.time() - mtime < 7 * 86400:
                            candidates.append((mtime, f))
                    except Exception:
                        pass
                if candidates:
                    candidates.sort(reverse=True)
                    # Check top 5 newest candidates for matching cwd
                    for _, cf in candidates[:5]:
                        try:
                            with open(cf, "r", encoding="utf-8", errors="ignore") as fp:
                                # First 20 lines usually contain turn_context with cwd
                                for _ in range(25):
                                    l = fp.readline()
                                    if not l:
                                        break
                                    if f'"{cwd}"' in l:
                                        return ("codex", cf)
                        except Exception:
                            pass
        elif agent == "claude" and session_id:
            matches = glob.glob(os.path.expanduser(f"~/.claude/projects/*/{session_id}.jsonl"))
            if matches:
                return ("claude", matches[0])
        elif agent in ("under", "_"):
            if session_id:
                matches = glob.glob(os.path.expanduser(f"~/.underclass/sessions/*{session_id}*.jsonl"))
                if matches:
                    return ("under", matches[0])
            if cwd:
                # Find newest session matching this cwd
                session_dir = os.path.expanduser("~/.underclass/sessions")
                candidates = []
                for f in glob.glob(f"{session_dir}/*.jsonl"):
                    try:
                        # Quick check on first line for cwd
                        with open(f, "r", encoding="utf-8", errors="ignore") as fp:
                            line = fp.readline()
                            if line and f'"{cwd}"' in line:
                                candidates.append((os.path.getmtime(f), f))
                    except Exception:
                        pass
                if candidates:
                    candidates.sort(reverse=True)
                    return ("under", candidates[0][1])
            return (None, None)
        elif agent == "grok" and session_id:
            matches = glob.glob(os.path.expanduser(f"~/.grok/sessions/*/{session_id}/chat_history.jsonl"))
            if matches:
                return ("grok", matches[0])
        elif agent in ("agy", "gemini") and session_id:
            p = os.path.expanduser(f"~/.gemini/antigravity-cli/brain/{session_id}/.system_generated/logs/transcript.jsonl")
            if os.path.exists(p):
                return ("agy", p)
            p2 = os.path.expanduser(f"~/.gemini/antigravity-cli/brain/{session_id}/transcript.jsonl")
            if os.path.exists(p2):
                return ("agy", p2)
        elif agent == "copilot":
            if session_id:
                p = os.path.expanduser(f"~/.copilot/session-state/{session_id}/events.jsonl")
                if os.path.exists(p):
                    return ("copilot", p)
            # Find newest copilot session
            session_dirs = glob.glob(os.path.expanduser("~/.copilot/session-state/*/events.jsonl"))
            if session_dirs:
                session_dirs.sort(key=lambda f: os.path.getmtime(f), reverse=True)
                return ("copilot", session_dirs[0])
        return (None, None)

    def _parse_session_transcript(self, kind: Optional[str], path: Optional[str], limit: int = 50) -> tuple[list[dict], Optional[dict], Optional[str], Optional[int]]:
        turns: list[dict] = []
        question: Optional[dict] = None
        latest_model: Optional[str] = None
        latest_tokens: Optional[int] = None
        if not path or not os.path.exists(path):
            return (turns, question, latest_model, latest_tokens)

        try:
            with open(path, "r", encoding="utf-8", errors="ignore") as fp:
                for line in fp:
                    line = line.strip()
                    if not line:
                        continue
                    try:
                        row = json.loads(line)
                    except Exception:
                        continue

                    if kind == "codex":
                        p = row.get("payload") or {}
                        ptype = p.get("type")
                        if p.get("model"):
                            latest_model = str(p["model"])
                        # Check thread/turn token usage
                        ttu = p.get("thread_token_usage") or p.get("turn_token_usage")
                        if isinstance(ttu, dict) and ttu.get("total_tokens"):
                            latest_tokens = ttu["total_tokens"]

                        if ptype == "message":
                            role = p.get("role")
                            if role == "user":
                                question = None  # user replied, clear any active question
                            if role in ("user", "assistant"):
                                text = ""
                                for c in p.get("content", []):
                                    if isinstance(c, dict) and c.get("text"):
                                        t = c["text"]
                                        t = re.sub(r"<environment_context>[\s\S]*?</environment_context>", "", t)
                                        t = re.sub(r"<ADDITIONAL_METADATA>[\s\S]*?</ADDITIONAL_METADATA>", "", t)
                                        t = re.sub(r"<USER_SETTINGS_CHANGE>[\s\S]*?</USER_SETTINGS_CHANGE>", "", t)
                                        t = re.sub(r"<CONTEXT_SUMMARY>[\s\S]*?</CONTEXT_SUMMARY>", "", t)
                                        t = re.sub(r"<USER_REQUEST>\n?", "", t)
                                        t = re.sub(r"\n?</USER_REQUEST>", "", t)
                                        t = re.sub(r"<system-reminder>[\s\S]*?</system-reminder>", "", t)
                                        if "<codex_internal_context" in t:
                                            # Goal mode loop: extract user objective instead of massive internal scaffolding prompt
                                            m_obj = re.search(r"<objective>([\s\S]*?)</objective>", t)
                                            if m_obj and m_obj.group(1).strip():
                                                t = f"🎯 Goal: {m_obj.group(1).strip()}"
                                            else:
                                                t = ""
                                        if t.strip():
                                            text += t + "\n"
                                if text.strip():
                                    turns.append({"role": role, "text": text.strip()})
                        elif ptype in ("custom_tool_call", "function_call"):
                            tname = p.get("name") or "tool"
                            tl = tname.lower()
                            # Match explicit question/confirmation tools (e.g. ask_user, request_user_input, confirm_action)
                            # Avoid matching 'followup_task' or other tool names containing 'task'
                            is_ask = False
                            if "request_user_input" in tl or "ask_question" in tl:
                                is_ask = True
                            elif "task" not in tl and re.search(r"\b(ask|confirm|permission|approve)\b", tl):
                                is_ask = True

                            if is_ask:
                                args = p.get("arguments", "")
                                prompt_text = str(args)
                                if isinstance(args, str):
                                    try:
                                        parsed_args = json.loads(args)
                                        if isinstance(parsed_args, dict):
                                            if "questions" in parsed_args and isinstance(parsed_args["questions"], list) and parsed_args["questions"]:
                                                q0 = parsed_args["questions"][0]
                                                prompt_text = q0.get("title") or q0.get("question") or str(q0)
                                            elif "prompt" in parsed_args:
                                                prompt_text = str(parsed_args["prompt"])
                                            elif "question" in parsed_args:
                                                prompt_text = str(parsed_args["question"])
                                    except Exception:
                                        pass
                                question = {
                                    "kind": "choice",
                                    "prompt": prompt_text,
                                    "options": ["y", "n", "p"],
                                }
                        elif ptype in ("custom_tool_call_output", "function_call_output", "task_complete", "turn_aborted"):
                            # The tool call or turn has finished/received output, clear question
                            question = None

                    elif kind == "claude":
                        rtype = row.get("type")
                        msg = row.get("message") or {}
                        if isinstance(msg, dict):
                            if msg.get("model"):
                                latest_model = str(msg["model"])
                            usage = msg.get("usage")
                            if isinstance(usage, dict):
                                inp = usage.get("input_tokens", 0) + usage.get("cache_creation_input_tokens", 0) + usage.get("cache_read_input_tokens", 0)
                                out = usage.get("output_tokens", 0)
                                if inp + out > 0:
                                    latest_tokens = inp + out
                        if rtype in ("user", "assistant"):
                            role = msg.get("role") or rtype
                            content = msg.get("content")
                            text = ""
                            if isinstance(content, str):
                                text = content
                            elif isinstance(content, list):
                                for c in content:
                                    if isinstance(c, dict) and c.get("type") == "text":
                                        text += c.get("text", "") + "\n"
                            if text.strip():
                                turns.append({"role": role, "text": text.strip()})

                    elif kind == "copilot":
                        etype = row.get("type")
                        data = row.get("data") or {}
                        if data.get("model"):
                            latest_model = str(data["model"])
                        elif data.get("newModel"):
                            latest_model = str(data["newModel"])

                        if etype == "user.message":
                            question = None
                            c = data.get("content")
                            if isinstance(c, str) and c.strip():
                                turns.append({"role": "user", "text": c.strip()})
                        elif etype == "assistant.message":
                            c = data.get("content")
                            if isinstance(c, str) and c.strip():
                                # Group consecutive assistant text chunks into one message turn
                                if turns and turns[-1].get("role") == "assistant":
                                    turns[-1]["text"] += "\n" + c.strip()
                                else:
                                    turns.append({"role": "assistant", "text": c.strip()})
                        elif etype == "permission.requested":
                            pr = data.get("permissionRequest") or data.get("promptRequest") or {}
                            p_prompt = pr.get("intention") or pr.get("fullCommandText") or "Copilot requests command permission"
                            question = {
                                "kind": "choice",
                                "prompt": p_prompt,
                                "options": ["y", "n", "p"],
                            }
                        elif etype == "permission.completed":
                            question = None

                    elif kind == "under":
                        rtype = row.get("type")
                        if rtype == "model_change" and row.get("model"):
                            m = row.get("model")
                            latest_model = m.get("id") if isinstance(m, dict) else str(m)
                        if rtype == "message":
                            msg = row.get("message") or {}
                            if msg.get("model"):
                                latest_model = str(msg["model"])
                            usage = msg.get("usage")
                            if isinstance(usage, dict) and usage.get("totalTokens"):
                                latest_tokens = usage["totalTokens"]
                            role = msg.get("role")
                            if role in ("user", "assistant"):
                                text = ""
                                thinking = ""
                                tools = []
                                for c in msg.get("content", []):
                                    if isinstance(c, dict):
                                        ctype = c.get("type")
                                        if ctype == "text":
                                            text += c.get("text", "") + "\n"
                                        elif ctype == "thinking":
                                            thinking += c.get("thinking", "") + "\n"
                                        elif ctype in ("toolCall", "tool_call"):
                                            t_name = c.get("name") or "tool"
                                            args = c.get("arguments")
                                            summary = f"{t_name}"
                                            if isinstance(args, dict):
                                                for k in ("command", "path", "pattern", "query"):
                                                    if k in args:
                                                        summary = f"{t_name} {args[k]}"
                                                        break
                                            elif isinstance(args, str) and args.strip():
                                                summary = f"{t_name} {args.strip()[:60]}"
                                            tools.append(summary)
                                if text.strip() or thinking.strip() or tools:
                                    turn = {"role": role, "text": text.strip()}
                                    if thinking.strip():
                                        turn["thinking"] = thinking.strip()
                                    if tools:
                                        turn["tools"] = tools
                                    turns.append(turn)

                    elif kind == "grok":
                        t = row.get("type")
                        if row.get("model_id"):
                            latest_model = str(row["model_id"])
                        elif not latest_model:
                            latest_model = "grok"
                        if t in ("user", "assistant"):
                            c = row.get("content")
                            text = ""
                            if isinstance(c, str):
                                text = c
                            elif isinstance(c, list):
                                for item in c:
                                    if isinstance(item, dict) and item.get("type") == "text":
                                        sub = item.get("text", "")
                                        if not sub.startswith("<user_info>") and not sub.startswith("<system-reminder>"):
                                            text += sub + "\n"
                            if text.strip():
                                turns.append({"role": t, "text": text.strip()})

                    elif kind == "agy":
                        t = row.get("type")
                        if t == "USER_INPUT":
                            raw_content = row.get("content", "")
                            if "<USER_SETTINGS_CHANGE>" in raw_content:
                                m = re.search(r"Model Selection` from .*? to (.*?)(?:\s*\([^\)]*\))?\.\s*No need", raw_content)
                                if not m:
                                    m = re.search(r"Model Selection` from .*? to ([A-Za-z0-9\.\-_ ]+)", raw_content)
                                if m:
                                    latest_model = m.group(1).strip()
                            content = raw_content
                            content = re.sub(r"<ADDITIONAL_METADATA>[\s\S]*?</ADDITIONAL_METADATA>", "", content)
                            content = re.sub(r"<USER_SETTINGS_CHANGE>[\s\S]*?</USER_SETTINGS_CHANGE>", "", content)
                            content = re.sub(r"<CONTEXT_SUMMARY>[\s\S]*?</CONTEXT_SUMMARY>", "", content)
                            content = re.sub(r"<USER_REQUEST>\n?", "", content)
                            content = re.sub(r"\n?</USER_REQUEST>", "", content)
                            if content.strip():
                                turns.append({"role": "user", "text": content.strip()})
                            question = None  # user replied, question is answered
                        elif not latest_model:
                            latest_model = "Gemini 3.8 Flash"
                        elif t == "PLANNER_RESPONSE":
                            content = row.get("content", "")
                            thinking = row.get("thinking", "")
                            tool_calls = row.get("tool_calls", [])
                            tools = []
                            question = None  # reset question unless raised in this step
                            for tc in tool_calls:
                                if isinstance(tc, dict):
                                    t_name = tc.get("toolName") or tc.get("name") or ""
                                    args = tc.get("args") or {}
                                    summary = tc.get("toolSummary") or tc.get("toolAction") or args.get("toolSummary") or args.get("toolAction") or t_name
                                    if isinstance(summary, str):
                                        summary = summary.strip().strip('"').strip("'")
                                    if summary:
                                        tools.append(summary)
                                    if "ask_question" in t_name:
                                        opts = args.get("options", ["y", "n"])
                                        if isinstance(opts, list) and len(opts) == 2 and set(opts) == {"y", "n"}:
                                            opts = ["y", "n", "p"]
                                        question = {
                                            "kind": "choice",
                                            "prompt": args.get("question", "Agent asks for confirmation:"),
                                            "options": opts,
                                        }
                            # Only include turn if there is content or thinking or meaningful tools
                            if content.strip() or thinking.strip() or tools:
                                turn = {"role": "assistant", "text": content.strip()}
                                if thinking.strip():
                                    turn["thinking"] = thinking.strip()
                                if tools:
                                    turn["tools"] = tools
                                turns.append(turn)
        except Exception as exc:
            log.warning("error parsing session transcript for %s (%s): %s", kind, path, exc)

        # Coalesce adjacent assistant turns (e.g. several tool call steps into one turn)
        coalesced: list[dict] = []
        for t in turns:
            if coalesced and coalesced[-1].get("role") == "assistant" and t.get("role") == "assistant":
                prev = coalesced[-1]
                # Merge tools
                if t.get("tools"):
                    prev["tools"] = prev.get("tools", []) + t["tools"]
                # Append or update text
                if t.get("text"):
                    if prev.get("text"):
                        prev["text"] = prev["text"] + "\n\n" + t["text"]
                    else:
                        prev["text"] = t["text"]
                # Update thinking
                if t.get("thinking"):
                    prev["thinking"] = t["thinking"]
            else:
                coalesced.append(t)

        # Filter out standalone proceed/approval turns ("p" / "y") so they don't clutter the chat
        filtered_turns = [
            t for t in coalesced
            if not (t.get("role") == "user" and t.get("text", "").strip().lower() in ("p", "y"))
        ]

        return (filtered_turns[-limit:], question, latest_model, latest_tokens)


# ---------------------------------------------------------------------------
# A connected phone app
# ---------------------------------------------------------------------------


class Client:
    """One TCP connection from a phone app."""

    def __init__(self, sock: socket.socket, addr, upstream: Upstream):
        self._sock = sock
        self._addr = addr
        self._upstream = upstream
        self._read_buf = b""
        self._write_lock = threading.Lock()
        self._closed = threading.Event()

    def serve(self) -> None:
        """Drive this client until it disconnects or errors."""
        self._upstream.attach(self)
        try:
            self._sock.settimeout(None)
            while not self._closed.is_set():
                try:
                    data = self._sock.recv(65536)
                except OSError as exc:
                    log.info("client %s read error: %s", self._addr, exc)
                    return
                if not data:
                    log.info("client %s disconnected", self._addr)
                    return
                self._read_buf += data
                while b"\n" in self._read_buf:
                    line, _, self._read_buf = self._read_buf.partition(b"\n")
                    if not line.strip():
                        continue
                    self._handle_line(line)
        finally:
            self._upstream.detach(self)
            self.close()

    def send_event(self, frame: dict) -> None:
        """Send a server-pushed event frame to this client."""
        line = (json.dumps(frame, separators=(",", ":")) + "\n").encode()
        with self._write_lock:
            try:
                self._sock.sendall(line)
            except OSError as exc:
                log.info("client %s write error: %s", self._addr, exc)
                self._closed.set()
                raise

    def close(self) -> None:
        if self._closed.is_set():
            return
        self._closed.set()
        try:
            self._sock.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass
        try:
            self._sock.close()
        except OSError:
            pass

    # ----- internals -----

    def _handle_line(self, line: bytes) -> None:
        try:
            frame = json.loads(line.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            log.warning("client %s sent malformed frame: %s", self._addr, exc)
            return

        method = frame.get("method")
        if not method:
            # Looks like a response; ignore (we never write responses
            # back to the client — they only ever come from upstream).
            log.debug("client %s sent stray response: %r", self._addr, frame)
            return

        # If the frame has no `id`, treat it as a notification/one-shot.
        # If it has an `id`, wait for the upstream response and relay it
        # back, rewriting the id so the client sees the same id it sent.
        client_id = frame.get("id")
        is_notification = client_id is None
        try:
            if method == "pane.transcript":
                params = frame.get("params", {})
                resp = self._upstream.get_pane_transcript(params.get("pane_id"))
                if not is_notification:
                    self._send_response({"jsonrpc": "2.0", "id": client_id, "result": resp})
                return

            if is_notification:
                self._upstream.notify(frame)
            else:
                response = self._upstream.request(frame, timeout=60.0)
                if response is not None:
                    if method == "pane.list" and isinstance(response.get("result"), dict) and "panes" in response["result"]:
                        enriched = self._upstream.enrich_panes(response["result"]["panes"])
                        response["result"]["panes"] = enriched
                    response = {**response, "id": client_id}
                    self._send_response(response)
        except (ConnectionError, TimeoutError) as exc:
            log.warning("upstream error for %s from %s: %s", method, self._addr, exc)
            if not is_notification:
                self._send_error(client_id, "upstream_error", str(exc))

    def _send_response(self, response: dict) -> None:
        line = (json.dumps(response, separators=(",", ":")) + "\n").encode()
        with self._write_lock:
            try:
                self._sock.sendall(line)
            except OSError as exc:
                log.info("client %s write error: %s", self._addr, exc)
                self._closed.set()

    def _send_error(self, frame_id, code: str, message: str) -> None:
        if not frame_id:
            return
        err = {
            "jsonrpc": "2.0",
            "id": frame_id,
            "error": {"code": code, "message": message},
        }
        self._send_response(err)


# ---------------------------------------------------------------------------
# Listener
# ---------------------------------------------------------------------------


def serve(sock_path: str, host: str, port: int) -> int:
    upstream = Upstream(sock_path)
    upstream.start()

    listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        listener.bind((host, port))
    except OSError as exc:
        log.error("could not bind %s:%d: %s", host, port, exc)
        return 2
    listener.listen(8)
    listener.settimeout(0.5)
    log.info("listening on %s:%d -> %s", host, port, sock_path)

    stop = threading.Event()

    def _shutdown(*_):
        log.info("shutting down")
        stop.set()

    signal.signal(signal.SIGINT, _shutdown)
    signal.signal(signal.SIGTERM, _shutdown)

    try:
        while not stop.is_set():
            try:
                client_sock, addr = listener.accept()
            except socket.timeout:
                continue
            except OSError as exc:
                if stop.is_set():
                    break
                log.warning("accept error: %s", exc)
                continue
            log.info("client connected: %s", addr)
            client = Client(client_sock, addr, upstream)
            t = threading.Thread(
                target=client.serve, name=f"client-{addr}", daemon=True
            )
            t.start()
    finally:
        upstream.stop()
        try:
            listener.close()
        except OSError:
            pass
    return 0


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__.splitlines()[1] if __doc__ else "")
    p.add_argument(
        "--sock",
        default=os.environ.get("HERDR_SOCK", DEFAULT_SOCK),
        help="Path to herdr Unix socket (default: %(default)s)",
    )
    p.add_argument(
        "--host",
        default=os.environ.get("HERDR_BRIDGE_HOST", DEFAULT_HOST),
        help="TCP host to bind (default: %(default)s)",
    )
    p.add_argument(
        "--port",
        type=int,
        default=int(os.environ.get("HERDR_BRIDGE_PORT", DEFAULT_PORT)),
        help="TCP port to bind (default: %(default)s)",
    )
    p.add_argument(
        "-v", "--verbose", action="store_true", help="Enable debug logging"
    )
    args = p.parse_args()

    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
    return serve(args.sock, args.host, args.port)


if __name__ == "__main__":
    sys.exit(main())
