# Herdr Android — Complete User Stories

> All user stories for a Claude Code-style vibe coding command center on Android, mapped to implementation status.

## Status Legend

- ✅ **Done** — Implemented, tested, shipping
- ⚡ **Partial** — Implemented but needs UX/tech polish
- 🔧 **In Progress** — Being built
- 📋 **Planned** — Spec'd, not started
- 💡 **Future** — Desired, not yet planned

---

## Core Loop: Connect → Monitor → Act

### US-01: Connect to Herdr Server

**As a** user
**I want** my phone to connect to my Herdr server
**So that** I can control my coding agents remotely.

**Acceptance Criteria:**
- [x] Configure server host and port in Settings
- [x] App auto-connects on launch
- [x] Connection status shown in top bar (WiFi icon)
- [x] Falls back to mock data if server unavailable
- [x] Manual reconnect via tap
- [ ] Connect via Cloudflare tunnel URL (HTTPS)
- [ ] Authenticate with token/password
- [ ] TLS-secured connection

**Status:** ✅ Done (basic), 📋 Secure auth + TLS

---

### US-02: View All Agent Workspaces

**As a** user
**I want** to see all my workspaces and agent panes at a glance
**So that** I can understand what is happening without opening each one.

**Acceptance Criteria:**
- [x] Dashboard displays all workspaces
- [x] Each workspace shows its tabs
- [x] Each tab shows its panes
- [x] Pane shows agent name and title
- [x] Pane shows current state (WORKING, IDLE, DONE, BLOCKED)
- [x] Panes sorted by priority (blocked → working → done → idle)
- [ ] Workspace summary stats (e.g., "4 panes · 2 working · 1 blocked")
- [ ] Workspace expand/collapse
- [ ] Pull-to-refresh

**Status:** ✅ Done (basic), ⚡ Needs board polish

---

### US-03: Send Instructions to Agents

**As a** user
**I want** to send instructions to my agents from my phone
**So that** I can steer work without opening my laptop.

**Acceptance Criteria:**
- [x] Home screen has prompt composer
- [x] Suggested prompts as quick-fill chips
- [x] Send navigates to agent chat
- [x] Message appears in chat with agent response
- [x] Code blocks formatted in chat
- [ ] Voice-to-text input for prompts
- [ ] Continue/redirect existing sessions from composer

**Status:** ✅ Done (text), 📋 Voice input

---

### US-04: Follow Live Agent Output

**As a** user
**I want** to watch agent output in real-time
**So that** I can understand what the agent is doing and catch issues early.

**Acceptance Criteria:**
- [x] Chat screen displays message history
- [x] User and agent messages visually separated
- [x] Markdown formatting (bold, inline code, code blocks)
- [x] Auto-scroll to latest message
- [ ] Syntax-highlighted code blocks
- [ ] Diff rendering (green/red for additions/removals)
- [ ] Progress bars for long-running tasks
- [ ] Lazy loading for long conversations (pagination)
- [ ] Images and screenshots inline

**Status:** ⚡ Partial — basic markdown works, needs rich output

---

### US-05: Nudge Stalled Agents

**As a** user
**I want** to nudge idle or blocked agents
**So that** I can recover progress without typing a full instruction.

**Acceptance Criteria:**
- [x] Dashboard shows nudge button (Send icon) for non-working agents
- [x] Tap sends "Nudge" command
- [x] Agent state updates after nudge
- [ ] Nudge on Home screen agent cards
- [ ] Nudge with context (re-send last prompt)
- [ ] Batch nudge (select multiple agents)

**Status:** ✅ Done (basic)

---

### US-06: Stop/Kill Runaway Agents

**As a** user
**I want** to stop agents that are running too long or making errors
**So that** I can prevent resource waste and damage.

**Acceptance Criteria:**
- [ ] Stop button visible on WORKING agents
- [ ] Confirmation dialog before kill
- [ ] Agent returns to IDLE after stop
- [ ] Can stop from Home, Dashboard, and Chat screens
- [ ] Batch stop (select multiple)
- [ ] Stop with reason (logged)

**Status:** 📋 Planned

---

## Workspace & Project Management

### US-07: Search Across Workspaces

**As a** user
**I want** to search for workspaces, panes, and agents
**So that** I can quickly find what I need.

**Acceptance Criteria:**
- [x] Search screen with real-time filtering
- [x] Filters workspaces by name
- [x] Filters panes by title and agent name
- [ ] Search agent output/message history
- [ ] Search file names in workspace
- [ ] Command palette (⌘K-style overlay)
- [ ] Recent searches

**Status:** ✅ Done (basic workspace/pane), 📋 Full search

---

### US-08: Browse Remote Files

**As a** user
**I want** to browse the workspace filesystem from my phone
**So that** I can inspect what the agent is working on.

**Acceptance Criteria:**
- [x] File explorer displays directory contents
- [x] Navigate into subdirectories and back
- [ ] Browse REMOTE server filesystem (not just phone-local SAF)
- [ ] File type icons
- [ ] File metadata (size, last modified)
- [ ] Search within file explorer
- [ ] Path breadcrumb navigation

**Status:** ⚡ Partial — phone-local only. Need remote filesystem endpoint.

---

### US-09: View and Edit Files

**As a** user
**I want** to view and edit files on the server
**So that** I can make quick fixes without opening an IDE.

**Acceptance Criteria:**
- [ ] View file with syntax highlighting
- [ ] Edit file with code editor (line numbers, monospace)
- [ ] Save changes → send to server
- [ ] Conflict detection (file changed remotely)
- [ ] Auto-save drafts locally

**Status:** 💡 Future

---

### US-10: Upload and Download Files

**As a** user
**I want** to transfer files between my phone and the server
**So that** I can add resources or access files offline.

**Acceptance Criteria:**
- [ ] Upload from phone file picker
- [ ] Download to phone storage
- [ ] Progress indicators for transfers
- [ ] Chunked upload for large files
- [ ] Cancel in-progress transfers
- [ ] Recently transferred list

**Status:** 💡 Future

---

### US-11: Create and Delete Workspaces

**As a** user
**I want** to create and delete workspaces and panes from my phone
**So that** I can manage my projects remotely.

**Acceptance Criteria:**
- [ ] Create workspace with name and description
- [ ] Add tabs and panes to workspace
- [ ] Delete workspaces (with confirmation)
- [ ] Delete individual panes
- [ ] Cannot delete workspace with active agents
- [ ] Bulk delete

**Status:** 📋 Planned

---

## Agent Configuration

### US-12: Select AI Model

**As a** user
**I want** to select which AI model an agent uses
**So that** I can choose the right capability/cost balance.

**Acceptance Criteria:**
- [x] Home screen shows model dropdown
- [x] Available: SWE-1.6, Claude 3.5, GPT-4o, Gemini 1.5
- [ ] Per-pane model selection in settings
- [ ] Per-workspace default model
- [ ] Model info (capabilities, context window)

**Status:** ✅ Done (basic global), 📋 Per-agent config

---

### US-13: Configure Agent Settings

**As a** user
**I want** to configure agent parameters (temperature, max tokens, tools)
**So that** I can customize behavior for different tasks.

**Acceptance Criteria:**
- [ ] Agent settings screen per pane
- [ ] Configure model, temperature, max tokens
- [ ] Toggle tool permissions (files, git, shell, browsing)
- [ ] Save and apply settings
- [ ] Reset to defaults
- [ ] Presets for common configurations

**Status:** 💡 Future

---

## Git & Source Control

### US-14: Clone and Manage Repositories

**As a** user
**I want** to clone, branch, and manage git repos
**So that** I can work on different projects.

**Acceptance Criteria:**
- [x] Git clone screen with URL, branch, directory (UI only)
- [ ] Real clone with JGit library
- [ ] Clone progress display
- [ ] View git status
- [ ] View diff of uncommitted changes
- [ ] Switch branches
- [ ] Create and merge PRs

**Status:** ⚡ Simulated UI only, 📋 Real git

---

## Remote Access & Infrastructure

### US-15: SSH into Remote Machines

**As a** user
**I want** to SSH into remote machines from my phone
**So that** I can access the terminal directly.

**Acceptance Criteria:**
- [x] SSH screen with host, port, username, password
- [x] Password show/hide toggle
- [x] Save credentials securely (EncryptedSharedPreferences)
- [ ] Real SSH connection with JSch
- [ ] Terminal emulator with ANSI support
- [ ] Keepalive and auto-reconnect
- [ ] Multiple SSH profiles
- [ ] Key-based auth (private key upload)

**Status:** ⚡ Simulated UI only, 📋 Real SSH

---

### US-16: Multiple Server Profiles

**As a** user
**I want** to switch between different Herdr servers
**So that** I can manage multiple agent environments.

**Acceptance Criteria:**
- [ ] Named server profiles
- [ ] Add, edit, delete profiles
- [ ] Quick switch from Home screen
- [ ] Per-profile settings (model, theme)
- [ ] Active profile indicator

**Status:** 💡 Future

---

## Notifications & Alerts

### US-17: Push Notifications for Agent Events

**As a** user
**I want** to receive notifications when agents complete or get blocked
**So that** I can respond quickly.

**Acceptance Criteria:**
- [x] Notification when agent completes (WORKING → DONE)
- [x] Notification when agent gets blocked (WORKING → BLOCKED)
- [x] Tap notification → open relevant pane
- [ ] Notification when agent errors
- [ ] Notification preferences (per-workspace, per-event-type)
- [ ] Notification grouping
- [ ] Action buttons on notification (Nudge, Stop, Dismiss)

**Status:** ✅ Done (basic), 📋 Rich notifications

---

### US-18: Foreground Monitoring Service

**As a** user
**I want** the app to maintain connection in the background
**So that** I receive updates even when the app is minimized.

**Acceptance Criteria:**
- [x] Foreground service with persistent notification
- [x] Background event collection
- [x] Auto-reconnect on disconnect
- [ ] Battery-efficient background mode
- [ ] Configurable monitoring interval

**Status:** ✅ Done

---

## Offline & Resilience

### US-19: Offline Mode

**As a** user
**I want** to view cached data when offline
**So that** I can still see my workspaces without internet.

**Acceptance Criteria:**
- [ ] Cache workspaces, panes, recent messages locally
- [ ] Display cached data when offline with "offline" indicator
- [ ] Queue actions when offline
- [ ] Sync queued actions when reconnected
- [ ] Manual refresh option

**Status:** 💡 Future — needs Room DB

---

### US-20: Network Roaming Support

**As a** user
**I want** the connection to survive switching between WiFi and cellular
**So that** I don't lose agent visibility when I move.

**Acceptance Criteria:**
- [ ] Automatic reconnection on roam
- [ ] Minimal data loss during transition
- [ ] State recovery after reconnect
- [ ] Mosh-like UDP resilience (future)

**Status:** 📋 TCP reconnect exists, roaming is a future enhancement

---

## Platform & Security

### US-21: Biometric App Lock

**As a** user
**I want** to secure the app with biometric authentication
**So that** my agent access is protected.

**Acceptance Criteria:**
- [ ] Fingerprint / Face unlock on launch
- [ ] Configurable timeout (instant, 1min, 5min, never)
- [ ] Biometric required for sensitive actions
- [ ] Graceful fallback to PIN/pattern

**Status:** 💡 Future

---

### US-22: Theme and Visual Preferences

**As a** user
**I want** to customize the app appearance
**So that** it fits my preferences.

**Acceptance Criteria:**
- [x] Material 3 dynamic colors (follows system)
- [ ] Dark/light/system toggle
- [ ] Monochrome theme option
- [ ] Font size customization
- [ ] Status indicator style preferences

**Status:** ⚡ Partial — M3 dynamic colors work, needs theme toggle

---

## Vibe Coding (Future Vision)

### US-23: Voice-to-Prompt

**As a** user
**I want** to speak my instructions instead of typing
**So that** I can command agents hands-free.

**Acceptance Criteria:**
- [ ] Mic button in prompt composer
- [ ] Speech-to-text transcription
- [ ] Send transcribed text as prompt
- [ ] Confirmation before sending

**Status:** 💡 Future

---

### US-24: Approval Queue

**As a** user
**I want** to review and approve/reject code changes before they're applied
**So that** I maintain control over what goes into my codebase.

**Acceptance Criteria:**
- [ ] List of pending changes from agents
- [ ] Diff view for each change
- [ ] Approve/reject individual changes
- [ ] Approve/reject all
- [ ] Comment on changes
- [ ] Notification when approval is needed

**Status:** 💡 Future

---

### US-25: Agent Collaboration (Multi-Agent)

**As a** user
**I want** to see multiple agents working in parallel
**So that** complex tasks are completed faster.

**Acceptance Criteria:**
- [ ] View all agents simultaneously on dashboard
- [ ] Assign tasks to specific agents
- [ ] See interdependencies between agents
- [ ] Parallel progress indicators
- [ ] Cross-agent context sharing

**Status:** 📋 Planned (depends on server multi-agent support)

## Implementation Priority Matrix

```
                    High Impact                     Low Impact
                  ┌───────────────┬──────────────────┐
   Easy           │ US-06 Stop    │ US-22 Theme      │
     to           │ US-05 Nudge   │ US-12 Model      │
   Implement      │ US-01 Auth    │   select          │
                  ├───────────────┼──────────────────┤
   Hard           │ US-08 Remote  │ US-19 Offline    │
     to           │   files       │   mode           │
   Implement      │ US-15 Real    │ US-21 Biometric  │
                  │   SSH         │ US-14 Real git   │
                  │ US-04 Rich    │   clone          │
                  │   output      │                  │
                  └───────────────┴──────────────────┘
```

## Story Mapping: User Journey

### First-Time User (New Connection)

```
Install → Open → Settings → Enter server IP:port →
Connect → Home shows "Connected" + active agents →
Tap agent → Chat → Send prompt → See response →
Return to Home → See state change → Success ✓
```

### Daily User (Returning)

```
Open app (biometric unlock) → See connection restored →
Check Home for blocked agents → See red badge →
Tap blocked agent → Read output → Nudge →
Agent resumes → Work continues → ✓
```

### Power User (Active Development)

```
Open → See 4 agents working in parallel →
Tap dashboard → View full workspace tree →
Send new instruction to a different agent →
Browse files to check progress →
Receive notification: agent completed →
Review diff → Approve changes → ✓
```
