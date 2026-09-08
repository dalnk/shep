# Herdr Android — Visual Mockups & Screen Designs

> Phone-first command center for remote coding agents. All designs are Material 3 / Jetpack Compose-native.

## Coverage Map: User Stories → Screen Elements

Every user story is mapped to the visual element that fulfills it. If a box is empty, there's no UI yet.

| US | Story | Screen | Element | Status |
|----|-------|--------|---------|--------|
| 01 | Connect to server | Settings → Home | Host/port fields + WiFi chip in top bar | ✅ |
| 02 | View workspaces | Dashboard | WorkspaceSection + PaneCard list | ✅ |
| 03 | Send instructions | Home → Chat | Prompt composer + send button | ✅ |
| 04 | Live agent output | Chat | Message bubbles + MarkdownText | ⚡ |
| 05 | Nudge agents | Dashboard | Send icon on non-working PaneCards | ✅ |
| 06 | Stop agents | _missing_ | Stop button + confirm dialog | 📋 |
| 07 | Search | Search | Search input + filtered results list | ✅ |
| 08 | Browse remote files | File Explorer | Directory listing (phone-local only) | ⚡ |
| 09 | View/edit files | _missing_ | Code viewer with syntax highlight + save | 💡 |
| 10 | Upload/download | _missing_ | Transfer buttons + progress bars | 💡 |
| 11 | Create/delete workspaces | _missing_ | Create form + delete with confirm | 📋 |
| 12 | Select model | Home | Model dropdown | ✅ |
| 13 | Configure agent | _missing_ | Settings form per pane | 💡 |
| 14 | Git clone/manage | Git Clone | URL/branch/dir inputs (simulated) | ⚡ |
| 15 | SSH connect | SSH | Host/port/user/pass inputs (simulated) | ⚡ |
| 16 | Multiple profiles | _missing_ | Profile switcher + management | 💡 |
| 17 | Push notifications | System tray | FCM notification + tap to open pane | ✅ |
| 18 | Background monitoring | System | Foreground service (invisible) | ✅ |
| 19 | Offline mode | _missing_ | Cached data + offline indicator | 💡 |
| 20 | Network roaming | _missing_ | Auto-reconnect logic (exists) | 📋 |
| 21 | Biometric lock | _missing_ | Fingerprint/face gate on launch | 💡 |
| 22 | Theme preferences | Settings | Dark/light toggle (missing) | ⚡ |
| 23 | Voice input | _missing_ | Mic button in composer | 💡 |
| 24 | Approval queue | _missing_ | Diff list + approve/reject buttons | 💡 |
| 25 | Multi-agent view | Dashboard | Multiple PaneCards per workspace | ✅ |

---

## Design Principles

1. **Calm control** — Status at a glance, action in one tap
2. **Progressive disclosure** — Surface the main loop first, reveal depth on demand
3. **Touch-first** — Bottom-friendly, large targets, thumb zone layout
4. **Status-driven** — Color and icon show state before text
5. **Claude Code DNA** — Terminal-inspired but touch-adapted

---

## Screen 1: Home / Command Center

**Covers: US-01 (status), US-03 (composer), US-05 (nudge nav), US-06 (stop), US-07 (search nav), US-08 (file nav), US-12 (model), US-15 (SSH nav), US-23 (voice)**

```
┌──────────────────────────────────────────┐
│  Agent Control           [🔒] [🔄●] [⚙] │  ← US-01 + US-21
│  ┌──────────────────────────────────────┐│
│  │  What do you want the agents to do? ││  ← US-03
│  │                          [🎤] [▲]  ││  ← US-23 voice
│  │  ○ Fix the login bug                ││
│  │  ○ Review PR #42      [SWE-1.6 ▼]   ││  ← US-12 model
│  │  ○ Continue the refactor            ││
│  └──────────────────────────────────────┘│
│                                          │
│  [📁]  [🔍]  [💻]  [📋]  [🔄]         │  ← US-08,07,15,24,19
│  Files  Search  SSH  Queue  Sync        │
│                                          │
│  ── Active Agents (3) ────────────────── │
│                                          │
│  ┌─ ● Fix auth token          ● WORK  ─┐│  ← US-02, US-04
│  │ Claude · api-service                  ││
│  │ Adding retry with backoff...          ││
│  │                         [⏹] [→]     ││  ← US-06 stop
│  └──────────────────────────────────────┘│
│                                          │
│  ┌─ ⚠ Review PR #42           ● BLC   ─┐│  ← US-05 nudge
│  │ GPT-4o · api-service                  ││
│  │ Needs API key to proceed              ││
│  │                    [👆Nudge] [→]     ││
│  └──────────────────────────────────────┘│
│                                          │
│  ┌─ ✓ Polish onboarding         ● DNE  ─┐│  ← US-25 multi-agent
│  │ Gemini · mobile-app                   ││
│  │ Completed 3m ago           [Replay]   ││
│  └──────────────────────────────────────┘│
└──────────────────────────────────────────┘
```

---

## Screen 2: Workspace Board / Dashboard

**Covers: US-02 (workspaces), US-05 (nudge), US-06 (stop), US-11 (create/delete), US-25 (multi-agent)**

```
┌──────────────────────────────────────────┐
│  ← Workspaces            [+ Add] [🔍]  │  ← US-11 create
│                                          │
│  ── api-service ── 4 panes ── 1 blocked ─│
│                                          │
│  ┌── Tab: backend ─────────────────────┐│
│  │  ● Fix auth token          ● WORK  ││
│  │  Claude                [⏹] [→]    ││  ← US-06 stop, US-03
│  │  ⚠ Review rate limit       ● BLC   ││
│  │  GPT-4o           [👆Nudge] [→]    ││  ← US-05 nudge
│  └──────────────────────────────────────┘│
│                                          │
│  ┌── Tab: frontend ────────────────────┐│
│  │  ○ Polish UI                ● IDLE  ││
│  │  Gemini                  [👆] [→]   ││
│  └──────────────────────────────────────┘│
│                                          │
│  ── mobile-app ── 1 pane ───────────────│
│                                          │
│  ┌── Tab: android ─────────────────────┐│
│  │  ○ Add biometric lock      ● BLOCK  ││
│  │  Claude            [Nudge] [Delete] ││  ← US-11 delete
│  └──────────────────────────────────────┘│
└──────────────────────────────────────────┘
```

---

## Screen 3: Agent Session / Chat

**Covers: US-03 (send), US-04 (live output), US-05 (nudge), US-06 (stop), US-09 (file view), US-24 (approval)**

```
┌──────────────────────────────────────────┐
│  ← Fix auth token                ● WORK │
│  api-service · Claude 3.5 Sonnet         │
│──────────────────────────────────────────│
│                                          │
│  ┌── You ──────────────────────────────┐│
│  │  Add retry logic with exponential   ││  ← US-03 send
│  │  backoff and jitter.                ││
│  └──────────────────────────────────────┘│
│                                          │
│  ┌── Claude ───────────────────────────┐│
│  │  Tracing the auth flow to find the  ││  ← US-04 live output
│  │  timeout...                          ││
│  │                                      ││
│  │  ```kotlin                           ││
│  │  fun refreshToken(): Result<T> {    ││
│  │      while (retry < MAX) { ... }   ││
│  │  }                                   ││
│  │  ```                                 ││
│  │                                      ││
│  │  _Found: endpoint has no timeout._   ││
│  └──────────────────────────────────────┘│
│                                          │
│  ╔══ Patching... ═════════════════════╗ ││
│  ╚════════════════════════════════════╝ ││
│                                          │
│  ┌── Changes Ready ────────────────────┐│
│  │  3 files modified                   ││  ← US-24 approval
│  │  [View Diff] [Approve] [Reject]     ││
│  └──────────────────────────────────────┘│
│                                          │
│  ┌──────────────────────────────────────┐│
│  │  [📎] Type a response...   [🎤][➤]  ││  ← US-03, US-23
│  └──────────────────────────────────────┘│
└──────────────────────────────────────────┘
```

---

## Screen 4: Remote File Explorer

**Covers: US-08 (browse), US-09 (view/edit), US-10 (upload/download)**

```
┌──────────────────────────────────────────┐
│  ← /workspace/api-service/src           │  ← US-08 breadcrumb
│                                          │
│  📁 auth/                   Apr 14 2:30 │
│  📁 middleware/              Apr 14 1:15 │
│  📄 main.py                 Apr 14 3:00 │
│  📄 requirements.txt        Apr 12 5:10 │
│  📄 Dockerfile              Apr 10 9:00 │
│                                          │
│  [Download Selected] [📤 Upload]        │  ← US-10 transfer
│                                          │
│  ┌── main.py ──────────────────────────┐│
│  │  1 │ import auth                     ││  ← US-09 syntax view
│  │  2 │ from models import User         ││  (slide-up panel)
│  │  3 │                                  ││
│  │  4 │ def handle_login(req):          ││
│  │  5 │     user = User.find(...)      ││
│  │                                      ││
│  │  [Edit] [Download] [Close]          ││
│  └──────────────────────────────────────┘│
└──────────────────────────────────────────┘
```

---

## Screen 5: Settings

**Covers: US-01 (server config), US-12 (model), US-13 (agent config), US-16 (profiles), US-21 (biometric), US-22 (theme)**

```
┌──────────────────────────────────────────┐
│  ← Settings                               │
│                                          │
│  ── Server ──────────────────────────────│
│  ○ Profile: [Production        ▼]       │  ← US-16 profiles
│  ○ Host:     [herdr.example.com   ]     │
│  ○ Port:     [8765                 ]     │  ← US-01 config
│  ○ Token:    [··················   ]     │
│  ○ TLS:      [🔘 Enabled]              │
│                                          │
│  ── Agent Defaults ──────────────────────│
│  ○ Model:     [Claude 3.5 Sonnet ▼]     │  ← US-12
│  ○ Max tokens: [4096               ]     │  ← US-13
│  ○ Tools:      [✓ Files] [✓ Git] [○ Web]│
│  ○ Temp:       ═══●═══════════ 0.7      │
│                                          │
│  ── Appearance ──────────────────────────│
│  ○ Theme:     [System ▼]                │  ← US-22
│  ○ Dynamic colors: [🔘 On]              │
│                                          │
│  ── Security ────────────────────────────│
│  ○ Biometric lock: [🔘 On]              │  ← US-21
│  ○ Lock timeout:  [1 min ▼]             │
│                                          │
│  ── Notifications ───────────────────────│
│  ○ Agent done:     [🔘 On]              │  ← US-17 prefs
│  ○ Agent blocked:  [🔘 On]              │
│  ○ Agent error:    [🔘 On]              │
└──────────────────────────────────────────┘
```

---

## Screen 6: Git / Source Control

**Covers: US-14 (clone, branch, diff, PR)**

```
┌──────────────────────────────────────────┐
│  ← Source Control                         │
│                                          │
│  ┌── Clone ────────────────────────────┐│
│  │ URL:  [https://github.com/...  ]    ││
│  │ Branch: [main              ▼]        ││
│  │ Dir:   [/workspace/           ]      ││
│  │                  [Clone ▼]          ││
│  └──────────────────────────────────────┘│
│                                          │
│  ── api-service (main) ──────────────────│
│  🔴 M auth.py    [View Diff] [Commit]   │  ← US-14 diff
│  🟢 A test_auth.py                       │
│  ● 3 commits behind origin               │
│                                          │
│  ┌── auth.py diff ─────────────────────┐│
│  │  - old_token = get_token()          ││
│  │  + token = refresh_with_retry()     ││
│  │  + if token == null: raise AuthErr  ││
│  └──────────────────────────────────────┘│
└──────────────────────────────────────────┘
```

---

## Screen 7: SSH Terminal

**Covers: US-15 (SSH connect + terminal)**

```
┌──────────────────────────────────────────┐
│  ← SSH Connection                        │
│                                          │
│  ┌── Connection ───────────────────────┐│
│  │ Host: [192.168.1.100          ]     ││
│  │ Port: [22                     ]     ││
│  │ User: [developer              ]     ││
│  │ Pass: [········        [👁]  ]      ││
│  │ Key:  [Choose file...        ]      ││
│  │       [🔘 Save credentials]         ││
│  │       [🔗 Connect]                  ││
│  └──────────────────────────────────────┘│
│                                          │
│  ┌── Terminal ──────────────────────────┐│
│  │ $ cd /workspace/api-service          ││
│  │ $ git status                         ││
│  │ On branch main                        ││
│  │ Your branch is up to date             ││
│  │                                      ││
│  │ $ _                                  ││
│  └──────────────────────────────────────┘│
└──────────────────────────────────────────┘
```

---

## Screen 8: Command Palette / Global Search

**Covers: US-07 (search), quick commands**

```
┌──────────────────────────────────────────┐
│  🔍  >                                    │
│  ── Agents ──────────────────────────────│
│  > Fix auth token           ● api-svc    │
│  > Review PR #42            ● api-svc    │
│  > Polish UI                ● mobile     │
│  ── Workspaces ──────────────────────────│
│  > api-service (4 panes, 1 blocked)     │
│  > mobile-app (1 pane, idle)            │
│  ── Actions ────────────────────────────│
│  > Nudge all stalled                     │
│  > Create workspace...                   │
│  > Connect to server...                  │
└──────────────────────────────────────────┘
```

---

## Screen 9: Notification / Alert

**Covers: US-17 (push), US-18 (monitoring)**

```
┌──────────────────────────────────────────┐
│  ┌──────────────────────────────────────┐│
│  │ 🤖 Herdr                              ││
│  │ ⚠ Agent blocked: Review PR #42      ││  ← US-17 push
│  │   Needs API key to continue          ││
│  │                     [Nudge] [Open]   ││  ← action buttons
│  └──────────────────────────────────────┘│
│                                          │
│  ┌──────────────────────────────────────┐│
│  │ 🤖 Herdr                              ││
│  │ ✓ Agent done: Fix auth token         ││
│  │   Completed in 4m 32s                ││
│  │                      [Review] [Open] ││
│  └──────────────────────────────────────┘│
└──────────────────────────────────────────┘
```

---

## Offline / Error States

**Covers: US-19 (offline), US-20 (roaming)**

```
┌──────────────────────────────────────────┐
│  ● Herdr          [📶 Offline]  [⚙]     │  ← US-19 indicator
│                                          │
│  ┌──────────────────────────────────────┐│
│  │  📡 No connection to server           ││
│  │                                      ││
│  │  Showing cached data from 2m ago     ││  ← US-19 cached
│  │  Actions will queue and sync when     ││
│  │  reconnected.                         ││
│  │                                      ││
│  │           [Try Again] [Settings]      ││
│  └──────────────────────────────────────┘│
│                                          │
│  ┌─ Cached Agents ─────────────────────┐│
│  │  ● Fix auth token         [CACHED]  ││  ← stale indicator
│  │  ○ Review PR #42          [CACHED]  ││
│  └──────────────────────────────────────┘│
│                                          │
│  ⏳ 3 queued actions pending sync        ││  ← US-19 queue
└──────────────────────────────────────────┘
```

---

## Visual Token System

| Token | Meaning | Used In |
|-------|---------|---------|
| ● | Working (green) | Agent cards, badges |
| ⚠ | Blocked (red) | Agent cards, notifications |
| ✓ | Done (muted) | Agent cards, approvals |
| ○ | Idle (gray) | Agent cards |
| ⏹ | Stop | Agent cards, chat |
| 👆 | Nudge | Agent cards, notification |
| 🎤 | Voice input | Composer |
| 🔒 | Biometric | Top bar, Settings |
| 📶 Offline | Disconnected | Top bar banner |
| 🔄● | Connected | Top bar chip |
| [CACHED] | Stale data | Offline cards |

---

## Color / Status System

| Agent State | Badge | Card Tint |
|-------------|-------|-----------|
| `WORKING` | `primaryContainer` | Default |
| `BLOCKED` | `errorContainer` | `errorContainer` bg |
| `DONE` | `tertiaryContainer` | `tertiaryContainer` bg |
| `IDLE` | `surfaceVariant` | Default |

| Connection | Chip |
|-----------|------|
| CONNECTED | Green + "Connected" |
| RECONNECTING | Yellow pulse |
| DISCONNECTED | Red + "Offline" |

---

## Empty States

| Scenario | Message | Action |
|----------|---------|--------|
| No connection | "Not connected to any server" | [Configure Settings] |
| No workspaces | "No workspaces yet" | [Create Workspace] |
| No active agents | "All agents are idle" | [View All Workspaces] |
| No files | "No files in this directory" | [Upload File] |
| Search: no results | "No results found" | [Clear Search] |
| Chat: first message | — | Composer focused |
