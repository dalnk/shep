# hurrdurr NOTES — context for future agents

This file is a standing log of decisions, discoveries, and unknowns for the
hurrdurr Android app. Read it before doing anything that touches the
transport, the herdr bridge, or the home/dashboard screens. It's the
shortest path to "what's actually true" without re-deriving it from the
codebase or from chatting with the user.

## TL;DR (what this app is, in 2026-09)

hurrdurr is an Android (Kotlin/Compose/Hilt) remote control for
[herdr](https://herdr.dev), a tmux-on-steroids terminal session manager
already running on the user's Mac (`neo.local` / `10.0.0.106`). Goal: let
the user see which agent panes are running, what they're doing, and send
follow-up input to any pane — from the phone, while away from the laptop.

The user has multiple herdr panes open at any time (claude, codex, plain
shells, etc., in workspaces like `zero`, `hurrdurr`, `underclass`,
`daybreak`, `claude`) and they want a fast at-a-glance view of who's
working, who's idle, and a one-tap way to nudge or send a follow-up.

This is **not** a multi-agent chat router, **not** a file editor,
**not** an SSH client, **not** a Git client, **not** a cloud IDE. The
prior docs (`USER_STORIES.md`, `screen_by_screen_spec.md`,
`STEP_BY_STEP_TESTING_GUIDE.md`, `CLOUDFLARE_TUNNEL_SETUP.md`) listed
22 unimplemented features. Most of those features are out of scope.
They are deleted; do not bring them back.

## Environment facts (verified 2026-09-02)

- User's Mac: `neo.local` → `10.0.0.106`
- herdr is **already running** locally, version `0.8.2`, protocol `20`,
  `compatible: yes`. Socket: `/Users/dalnk/.config/herdr/herdr.sock`
  (Unix domain socket, **not** TCP).
- herdr is a single binary at `/Users/dalnk/.local/bin/herdr` (Mach-O
  arm64). Source not available locally. **Do not try to "fix" herdr
  itself** — only consume its public API.
- Three Android devices are attached via adb on the LAN:
  - `10.0.0.129:5555`
  - `10.0.0.243:5555`
  - `10.0.0.84:5555`
  All three are the user's. Install on any/all for testing.
- Java toolchain: `java` on PATH is JDK 26. Android Studio bundles
  **JDK 21.0.10** at `~/Library/Java/JavaVirtualMachines/.../Contents/Home`
  (auto-detected by Gradle). **Always build with JDK 21**, not the
  system Java. The `org.gradle.java.home` line in `gradle.properties`
  is the source of truth.
- Python 3.9.6 system, Node 26.4.0 system. The bridge is Python 3.
- mDNS resolves `neo.local` correctly from the same machine. The phone
  on the same WiFi will also resolve it (assume so; verify when an
  install + connect succeeds).

## herdr wire protocol (verified 2026-09-02, refined 2026-09-04)

- **Transport**: newline-delimited JSON over a Unix domain socket.
  One line per request, one line per response (or event, on a
  subscribed connection).
- **Request**: `{"jsonrpc":"2.0","id":"<unique>","method":"<name>","params":{...}}`
- **Response (success)**: `{"id":"<matching>","result":{...}}`
- **Response (error)**: `{"id":"<matching>","error":{"code":"...","message":"..."}}`
- **Event (server-pushed, on a subscribed connection)**: a JSON-RPC
  notification with no `id` and an `event` field naming the type
  (e.g. `pane.scroll_changed`, `pane.agent_status_changed`).
- **CRITICAL: every frame written to herdr MUST have an `id` field.**
  Even `events.subscribe` notifications. The `id` may be any unique
  string. Sending without an `id` produces `invalid_request: missing
  field id`.
- **CRITICAL: herdr's Unix socket is single-shot per subscription.**
  Each connection accepts a small burst of subscribes, drains events
  for a short window, then closes. To deliver a continuous event
  stream to TCP clients, run a long-poll loop: open a fresh socket,
  re-send active subscriptions every few seconds, drain for the
  SUBSCRIPTION_WINDOW (1.5–3.0s), close, repeat.
- **Request/response traffic can multiplex over its own short-lived
  connection.** The bridge holds `self._lock` only during the write
  phase, so the long-poll's read-side does not block request traffic.
  Important: do NOT call `_round_trip` from inside a `with self._lock`
  block (Python's Lock is not re-entrant — that deadlocks).
- Method names verified working through the bridge (2026-09-04):
  - `workspace.list` (no params) → workspace_list result
  - `tab.list` (params: `workspace_id`) → tab_list
  - `pane.list` (params: `tab_id`) → pane_list
  - `pane.send_text` (params: `pane_id`, `text`) → returns ok
  - `pane.read` (params: `pane_id`, `source` ∈ {visible, recent,
    recent_unwrapped, detection}, `lines?`, `format?`, `strip_ansi?`)
  - `agent.list`, `agent.get`, `agent.read` (params: `target` =
    pane_id, `source`, etc.)
  - `events.subscribe` (params: `subscriptions`: `[{type, pane_id?},
    ...]`) — type values include `pane.scroll_changed`,
    `pane.agent_status_changed`, `pane.updated`, `workspace.updated`,
    `tab.updated`. `pane_id` is REQUIRED for pane-scoped types.
- The full JSON Schema is at: `herdr api schema --json` (10k+ lines).
  Use it as the source of truth for method names and param shapes.
- herdr quirk: error responses use `id: ""` (empty string) for
  malformed requests. The app must treat `id: ""` as "no match" and
  drop the frame, not log it as a missing-request warning.
- herdr quirk: `pane.scroll_changed` and `pane.agent_status_changed`
  require a `pane_id` on the subscription; without it, herdr rejects
  the entire `events.subscribe` call. App subscribes only to
  pane-level events that don't need a target (e.g. `pane.updated`,
  `workspace.updated`, `pane.created`, `pane.closed`).
- herdr quirk: subscription type names are dotted (`pane.updated`,
  `workspace.updated`, `tab.created`, etc.) — there is no `tab.updated`.

## Verified end-to-end (2026-09-04, on 10.0.0.84:5555 = DC-1)

- `./gradlew :app:assembleDebug` is green.
- `./gradlew :app:testDebugUnitTest` is green (13 tests pass).
- App installed on the three attached devices (10.0.0.84 DC-1,
  10.0.0.129 BlueBerry, 10.0.0.243 Pixel 3a XL).
- Bridge running on neo.local:8765 forwards to herdr Unix socket.
- App connects to 10.0.0.106:8765 (default in SettingsRepository),
  successfully completes `events.subscribe` ack, then `workspace.list`,
  `tab.list`, `pane.list` for all 9 tabs.
- Home screen shows the user's "Active Agents" list: 3 agent panes
  (codex daybreak, codex GitHub, claude Daybreak and backstep) plus
  6 shell panes.
- Tapping a pane card navigates to the chat screen for that pane.
- Typing "hello_from_hurrdurr_app_1788576059" in the chat and
  tapping send: the app sent `pane.send_text` to the bridge, the
  bridge proxied to herdr, herdr typed the text into the actual
  terminal. Verified by reading the pane back via
  `pane.read --source recent` and seeing the text in the shell
  history (it failed with "command not found" because the text is
  not a real command, which is itself proof the keystrokes landed
  in the actual terminal).

## What's not yet wired (2026-09-04)

- Reading pane output back to the chat view (the
  `readPaneOutput(paneId): Flow<ChatMessage>` interface exists but
  is not yet fed by events; for now, only sent messages echo back).
  This is needed to see agent responses in the chat.
- Per-pane event subscriptions (e.g. `pane.scroll_changed` for a
  specific pane) — these need to be added dynamically as the user
  opens each chat, because herdr requires a `pane_id` on them.
- The home view's "Active Agents" still includes the 6 shell panes;
  the user said "I want to see who is working" — the next iteration
  should filter to only panes with detected agents (claude, codex,
  shell panes that have scroll/agent activity), or visually
  distinguish agent panes from idle shells.
- The Settings screen still has the legacy IROH fields and the
  ConnectionType enum. They don't break anything but should be
  cleaned up so the UI matches the product (one server profile:
  host + port to the bridge).
- The `HerdrMonitoringService` foreground service is launched on
  app start and connects the repository; the Activity's HomeViewModel
  also calls `connect()`. The Mutex around connect/disconnect keeps
  this from racing, but the Service + Activity pattern is awkward.
  Long-term: drop the service and let the ViewModel be the only
  connection owner (the bridge already provides the long-poll
  event stream, so a foreground service for "monitoring" is
  redundant).

## herdr-bridge design (2026-09-04)

A small Python bridge at `bridge/herdr-bridge.py` exposes herdr's
Unix socket over TCP. Default: listens on `0.0.0.0:8765`, forwards
to `~/.config/herdr/herdr.sock`. Use `--sock`, `--host`, `--port`
to override; `HERDR_SOCK`, `HERDR_BRIDGE_HOST`, `HERDR_BRIDGE_PORT`
env vars also work. Pure stdlib, no pip install.

Phone app points at `neo.local:8765` and speaks the same NDJSON
JSON-RPC the herdr CLI uses. The bridge:
- assigns a fresh id to every frame going to herdr (so clients can
  use any id, including empty/missing for notifications);
- rewrites the id back to the client's id on responses;
- runs a long-poll subscription loop that re-sends active
  subscriptions every SUBSCRIPTION_WINDOW seconds and dispatches
  events to all attached TCP clients;
- drains the response for synchronous request/response traffic
  through short-lived upstream connections.

**Bugs found and fixed during bridge development (read these
before touching the bridge):**
1. Original design held a single persistent upstream connection;
   herdr tore it down every ~5s. → switched to long-poll.
2. Subscription re-send was sending without an `id` → herdr
   rejected with `missing field id`. → always assign an id.
3. The first attempt held `self._lock` for the full round_trip
   window, blocking concurrent request traffic. → split into
   write phase (locked) and read phase (unlocked).
4. `request()` and `notify()` were calling `_round_trip` while
   holding `self._lock`, and `_round_trip` tried to acquire the
   same lock internally. Python's `Lock` is not re-entrant →
   deadlock. → `_round_trip` is the only lock acquirer; callers
   do not pre-lock.
5. `_round_trip` originally returned `None` instead of the
   matching frame, so request() callers got nothing back. →
   fixed to capture and return the response.
6. Client wrapper did not rewrite the bridge-assigned id back
   to the client's id on responses, so the client saw uuids
   instead of the ids it sent. → fixed.

## How to run the bridge (2026-09-04)

```bash
# foreground
python3 /Users/dalnk/hurrdurr/bridge/herdr-bridge.py

# background, log to file
python3 /Users/dalnk/hurrdurr/bridge/herdr-bridge.py > /tmp/bridge.log 2>&1 &

# kill
pkill -f herdr-bridge.py
```

Default port 8765, default socket path
`/Users/dalnk/.config/herdr/herdr.sock`. When herdr isn't running
the bridge will keep retrying (it logs each connect failure).

## App structure (verified 2026-09-02)

- `app/src/main/kotlin/herdr/dev/app/`
  - `MainActivity.kt`, `HerdrApplication.kt`
  - `data/` — repositories. `HerdrSocketRepository` is the interface.
    `HerdrSocketRepositoryImpl` is the live one (currently broken:
    targets a fictional port 8765 HTTP/TCP server that doesn't exist).
    `MockHerdrSocketRepository` is the in-memory fake for tests.
    `HybridHerdrSocketRepository` is the chooser (local vs iroh vs
    mock) — this whole hybrid layer is being removed.
  - `data/iroh/` — iroh P2P bridge client. Being removed (irrelevant;
    herdr is already on a Unix socket; tunneling is the right solution,
    not P2P).
  - `data/socket/JsonRpcModels.kt` — the JSON envelope. **This is
    correct** and matches herdr. Keep it.
  - `data/models/` — `Workspace`, `Tab`, `Pane`, `ChatMessage`,
    `AgentState`. Fine. May need a `PaneSnapshot` field for scroll
    history and `last_output_lines` for the home view.
  - `ui/screens/` — `HomeScreen`, `DashboardScreen`, `ChatScreen`,
    `FileExplorerScreen`, `SettingsScreen`, `OnboardingScreen`,
    `SearchScreen`, `SSHConnectionScreen`, `GitCloneScreen`. The
    last four plus `FileExplorerScreen` are out of scope and being
    removed.
  - `ui/components/AgentStatusBadge.kt` — keep, use everywhere.
  - `viewmodel/` — one ViewModel per screen, generally clean.
  - `di/AppModule.kt` — Hilt graph. Binds `HerdrSocketRepository` →
    `HybridHerdrSocketRepository`. Will be repointed to
    `HerdrSocketRepositoryImpl` directly.
- `herdr-iroh-bridge/` — separate Rust crate. Being deleted.
- `build/`, `app/build/`, `socket/` — build artifacts, ignored.
- Tests: `app/src/test/`. Currently 22 unit tests passing. Some
  needed fixes (see Decisions below). Two files had stale
  constructor calls that I corrected; see git log for specifics.

## Decisions (with date and reason)

- **2026-09-02** — Use a tiny Python TCP↔Unix forwarder (`bridge/`)
  rather than a WebSocket or a full HTTP server. herdr's protocol is
  already NDJSON, so a raw TCP forwarder is ~80 lines and adds zero
  herdr-specific knowledge. When we move to a real herdr
  remote-control server later, the bridge disappears.
- **2026-09-02** — Delete the iroh bridge, the `Hybrid` repository,
  and the out-of-scope screens. They are not in the product. The
  app should be a thin client; herdr does the work.
- **2026-09-02** — Default app host = `neo.local`, default port =
  `8765`. Settings let the user override. No more hardcoded
  `10.0.0.106` in source.
- **2026-09-02** — Home screen becomes a flat list of currently
  active agent panes, sorted by status (working → blocked → idle →
  unknown) and then by recent activity. This matches the user's
  stated workflow ("a bunch of agents open, I want to see who is
  working and tell them follow-ups"). The workspace→tab→pane tree
  is still reachable from a "All workspaces" affordance but is not
  the primary view.
- **2026-09-02** — Replace the 2-second polling for pane output
  with the real event subscription, once we know the exact method
  name. Polling is the existing fallback if events fail.

## Open questions / unknowns

- Exact event subscription method name (probably `event.subscribe`
  with a list of event types; verify from schema).
- Exact event frame shape for pane output (does it deliver full
  screen, diff, scroll deltas?). Verify empirically.
- Whether `pane.send_input` to a claude/codex TUI actually triggers
  the agent (it should, because the agent's TUI reads its stdin and
  the pane is a real terminal — but I haven't tested).
- Whether the app should accumulate persistent chat history per
  pane (probably yes, with a max size) or always read from herdr
  (`pane.read` for the latest N lines).
- Whether to ship a launchd plist for the bridge. Defer until the
  user has used the manual `python3 bridge/herdr-bridge.py` flow
  for a while.
- The `CLOUDFLARE_TUNNEL_SETUP.md` doc is deleted in this rewrite.
  When we add the permanent tunnel, write a new doc named after the
  chosen tunnel.

## How to verify a change works

1. `JAVA_HOME=.../Contents/Home ./gradlew :app:assembleDebug` — must be
   green.
2. `./gradlew :app:test7DebugUnitTest` — must be green.
3. Start bridge: `python3 bridge/herdr-bridge.py` in a background
   process.
4. `adb -s <device>:5555 install -r app/build/outputs/apk/debug/app-debug.apk`
5. Open the app on the device, set host = `neo.local`, port = `8765`.
6. Confirm dashboard shows the real 9 panes from the current herdr
   session (use `herdr api snapshot` on the host to compare).
7. Send a test message from the app to a real pane, watch
   `adb -s <device> logcat | grep HerdrSocketRepo` and
   `herdr api pane.read --pane_id ...` to confirm the wire round-trip.
