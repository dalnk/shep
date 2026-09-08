# Android Remote for Coding Agents

## Vision
This app should feel like a Claude Code-style command center for your phone, but built for Android and designed for remote control of coding agents running on a desktop or server.

The core idea is simple: a developer should be able to pick up their phone, connect to a remote agent session, and immediately understand what is happening, send a new instruction, review the latest output, and jump into files or git state without needing to open a laptop.

This is not a generic chat app. It is a lightweight, touch-first control surface for software agents.

---

## Problem Statement
Most AI coding agents are powerful, but they are awkward to manage from a phone. The experience is either:
- too passive, where the phone only receives notifications,
- too technical, where the phone feels like a terminal clone,
- or too disconnected from the actual work happening on the host machine.

What users want is a mobile-first remote workspace that feels like a calm, focused control panel:
- see what agents are doing,
- issue a new instruction in one tap,
- understand state at a glance,
- inspect the latest files or diffs,
- approve or redirect work when needed.

---

## Primary User Story
As a developer, I want to turn my Android phone into a remote command center for my coding agents so that I can monitor, guide, and intervene in my work from anywhere without opening my laptop.

### In plain English
I want my phone to feel like a pocket-sized Claude Code cockpit:
- not a full IDE,
- not a chat app,
- but a fast, focused way to stay connected to the agents working on my projects.

---

## Core Experience Principles
1. One-tap awareness
   - The user should instantly understand which agents are working, blocked, done, or idle.

2. Low-friction steering
   - The user should be able to send a new prompt or nudge without navigating through multiple screens.

3. Context over noise
   - The app should show the most relevant state first: agent status, recent output, workspace, and next action.

4. Trust and safety
   - Sensitive actions should require confirmation before destructive behavior.

5. Android-native feel
   - Large touch targets, clear hierarchy, bottom-friendly layout, visible status, and fast navigation.

---

## Detailed User Stories

### 1. Connect to a remote agent host
As a user, I want to connect my phone to a remote machine or local server so that I can control running agents from anywhere.

Acceptance criteria:
- User can enter host, port, and optional auth details.
- App shows connected/disconnected state clearly.
- App remembers the last successful connection.
- If the host is unreachable, the app shows a friendly offline or retry state.

### 2. See all active agent workspaces
As a user, I want to see all my workspaces and agent panes at a glance so that I can understand what is happening without opening each one.

Acceptance criteria:
- Home screen shows a board of active workspaces and panes.
- Each pane shows agent name, current task title, and status.
- States are visually distinct: working, blocked, idle, done.
- Highest-priority or most urgent work appears first.

### 3. Start or redirect a task from my phone
As a user, I want to send a new instruction from my phone so that I can keep work moving even when I am away from my desk.

Acceptance criteria:
- User can open a composer from the home screen.
- Composer supports quick prompts like “fix the auth bug”, “review this diff”, or “continue from last checkpoint”.
- Sending the prompt opens the conversation/session view.
- The app shows that the message was sent and that an agent is reacting.

### 4. Follow live agent output
As a user, I want to watch the agent’s output and conversation history so that I can understand what it is doing and whether it needs help.

Acceptance criteria:
- Message history is displayed in a chat-like layout.
- User messages and agent messages are clearly separated.
- Output supports code blocks and simple markdown formatting.
- The latest message is visible without needing to scroll excessively.

### 5. Nudge or steer stalled agents
As a user, I want to nudge an idle or blocked agent so that I can recover progress without waiting.

Acceptance criteria:
- User can tap a quick action such as “Nudge” or “Continue”.
- The action sends a lightweight instruction to the agent.
- The UI reflects that the new instruction has been sent.

### 6. Review files and repository state
As a user, I want to browse files and repository context from my phone so that I can inspect what the agent is working on.

Acceptance criteria:
- User can open the file explorer from the home screen or workspace view.
- Files and folders are displayed with clear metadata.
- User can open a file and see its content or a summary.
- User can navigate back to the parent directory.

### 7. Approve or stop risky work
As a user, I want to stop or approve risky actions so that I can keep control over the work and avoid mistakes.

Acceptance criteria:
- Destructive actions require confirmation.
- User can stop a running session.
- The app shows a clear warning state for risky actions.

### 8. Stay updated when I am away
As a user, I want to receive useful notifications when an agent finishes, gets blocked, or needs my attention so that I can respond quickly.

Acceptance criteria:
- User receives notifications for completion, blocking, and error states.
- Tapping a notification opens the relevant workspace or chat session.
- Notification preferences can be configured.

### 9. Customize the experience
As a user, I want to change my connection profile, theme, and preferences so that the app fits my workflow.

Acceptance criteria:
- User can manage server profiles.
- Theme can switch between dark/light/system.
- Preferences persist across app restarts.

---

## MVP Scope
The first version should focus on the core loop:
- connect to host,
- see live agent workspaces,
- send a new instruction,
- read agent output,
- nudge or stop work,
- inspect files.

### MVP screens
1. Home / Control Center
2. Workspace Dashboard
3. Chat / Agent Session
4. File Explorer
5. Settings

---

## Future Enhancements
- voice-to-text prompt entry,
- richer diff previews,
- approval queue for code changes,
- full terminal view,
- background sync and offline cache,
- multi-profile server switching,
- secure biometric lock.

---

## Visual Mockups

### 1. Home / Control Center
A calm, rounded, card-based layout with:
- a large prompt box at the top,
- fast action buttons for files, source, SSH, search,
- a scrollable list of active agents,
- a visible connection status chip.

```text
┌────────────────────────────┐
│ Herdr          [wifi]      │
│ Ask your agents...   [↑]   │
│ [Files] [Source] [SSH] [Search] │
│ Active agents               │
│ • Fix auth bug      ● Working │
│ • Review PR         ○ Blocked │
│ • Polish UI         ○ Idle    │
└────────────────────────────┘
```

### 2. Workspace Dashboard
A board-style screen grouped by workspace and tab, with each pane showing:
- agent name,
- task title,
- status badge,
- quick nudge button.

```text
┌────────────────────────────┐
│ Workspaces                  │
│ ────────────────────────── │
│ Workspace: app-api          │
│   Tab: backend              │
│   [Fix auth bug] Claude  ●  │
│   [Review PR] Gemini   ○   │
│ Workspace: ui-redesign      │
│   Tab: mobile               │
│   [Polish onboarding] Codex ○ │
└────────────────────────────┘
```

### 3. Agent Session / Chat
A message-first screen that combines chat and terminal-style feedback:
- user prompt at the top,
- agent output in bubbles or blocks,
- bottom composer for sending the next instruction.

```text
┌────────────────────────────┐
│ ← Fix auth bug      Claude │
│ You: Add retry logic       │
│ Agent: I’m tracing the flow │
│ Agent: Found a timeout path│
│ Agent: I can patch it now  │
│                            │
│ [Type your next instruction] [→] │
└────────────────────────────┘
```

### 4. File Explorer
A simple, touch-friendly file browser:
- folders and files with icons,
- path breadcrumb,
- quick actions for open / download / inspect.

```text
┌────────────────────────────┐
│ /workspace/app/src          │
│ ├─ auth/                    │
│ ├─ ui/                      │
│ ├─ services/                │
│ └─ README.md                │
│                              │
│ [Open] [Download] [Inspect]  │
└────────────────────────────┘
```

---

## Recommended Product Direction
The app should feel like a calm, premium remote-control surface for coding agents rather than a generic messaging app. The strongest version of this product is:
- phone-first,
- low-noise,
- fast to understand,
- clearly focused on action and supervision.

If the goal is to make the app feel “Claude Code style” on Android, the best framing is:
- agent command center,
- live session board,
- touch-friendly conversation and control surface,
- not a full IDE, but a focused remote companion.
