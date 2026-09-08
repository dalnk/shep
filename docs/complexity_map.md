# Herdr Android App — Complexity Map

> Full architectural and feature complexity map for turning the Herdr Android app into a Claude Code-style vibe coding command center.

## System Architecture

```
┌─────────────────────────────────────────────┐
│              Phone (Android)                  │
│                                               │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │ UI Layer  │  │ ViewModel│  │ Repo     │   │
│  │ (Compose) │──│   Layer  │──│  Layer   │   │
│  └──────────┘  └──────────┘  └──────────┘   │
│                                     │         │
│                            ┌────────▼──────┐ │
│                            │  TCP Socket     │ │
│                            │  (Ktor Client)  │ │
│                            └────────┬──────┘ │
└─────────────────────────────────────┼─────────┘
                                      │
                              JSON-RPC (TLS)
                                      │
                          ┌───────────▼────────┐
                          │   Herdr Server      │
                          │  (Remote/Local)      │
                          │   Port 8765          │
                          └────────────────────┘
```

## Current Data Flow

1. Phone opens TCP connection to herdr.dev server on port 8765
2. Sends JSON-RPC requests: `workspace.list`, `tab.list`, `pane.list`
3. Subscribes to events: `pane.agent_status_changed`, `pane.output_matched`
4. Sends commands: `pane.send_input`, `pane.send_text`
5. Server pushes real-time events back over the same TCP socket
6. Hybrid fallback: 5s timeout → mock data if server unreachable

## Current Architecture Layers

### Layer 1: Data/Network (`data/`)

| File | Role | Status |
|------|------|--------|
| `HerdrSocketRepository.kt` | Interface — defines contract | ✅ Done |
| `HerdrSocketRepositoryImpl.kt` | Real TCP implementation | ✅ Done |
| `HybridHerdrSocketRepository.kt` | Real→Mock fallback | ✅ Done |
| `MockHerdrSocketRepository.kt` | Mock for dev/testing | ✅ Done |
| `SettingsRepository.kt` | Preferences persistence | ✅ Done |
| `SecureCredentialsRepository.kt` | Encrypted SSH creds | ✅ Done |
| `socket/JsonRpcModels.kt` | JSON-RPC 2.0 models | ✅ Done |
| `models/` (Workspace, Tab, Pane, AgentState, ChatMessage) | Domain models | ✅ Done |

### Layer 2: ViewModels (`viewmodel/`)

| File | Role | Status |
|------|------|--------|
| `HomeViewModel.kt` | Connection + workspace state | ✅ Done |
| `DashboardViewModel.kt` | Workspace list + quick actions | ✅ Done |
| `ChatViewModel.kt` | Message flow per pane | ✅ Done |
| `SearchViewModel.kt` | Real-time search | ✅ Done |
| `SettingsViewModel.kt` | Server config | ✅ Done |
| `SSHConnectionViewModel.kt` | SSH lifecycle | ⚠️ Simulated |
| `GitCloneViewModel.kt` | Git clone flow | ⚠️ Simulated |

### Layer 3: UI Screens (`ui/screens/`)

| Screen | Purpose | Status | UX Maturity |
|--------|---------|--------|-------------|
| `HomeScreen.kt` | Command center / prompt entry | ✅ Done | ⚡ Needs polish |
| `DashboardScreen.kt` | Workspace board | ✅ Done | ⚡ Needs board UX |
| `ChatScreen.kt` | Agent chat + output | ✅ Done | ⚡ Needs rich output |
| `FileExplorerScreen.kt` | Remote file browse | ✅ Done | ⚡ Phone-local only |
| `SearchScreen.kt` | Cross-workspace search | ✅ Done | ⚡ Needs server side |
| `SettingsScreen.kt` | Server config + prefs | ✅ Done | ⚡ Needs profiles |
| `SSHConnectionScreen.kt` | SSH terminal | ⚠️ Simulated | 🔧 Needs real SSH |
| `GitCloneScreen.kt` | Git clone UI | ⚠️ Simulated | 🔧 Needs JGit |

### Layer 4: Services/Infrastructure

| Component | Role | Status |
|-----------|------|--------|
| `HerdrMonitoringService.kt` | BG monitoring + notifications | ✅ Done |
| `HerdrNotificationManager.kt` | Push notification orchestration | ✅ Done |
| `HerdrTheme.kt` | Material 3 theme | ✅ Done |
| `HerdrApp.kt` | Nav graph + DI | ✅ Done |
| `AppModule.kt` | Hilt DI bindings | ✅ Done |

## JSON-RPC Protocol Surface

### Current Methods

| Method | Params | Response |
|--------|--------|----------|
| `workspace.list` | `{}` | `{ workspaces: [{ workspace_id, label }] }` |
| `tab.list` | `{ workspace_id }` | `{ tabs: [{ tab_id, label }] }` |
| `pane.list` | `{ tab_id }` | `{ panes: [{ pane_id, agent, agent_status }] }` |
| `pane.send_input` | `{ pane_id, input }` | `{}` |
| `pane.send_text` | `{ pane_id, text }` | `{}` |
| `events.subscribe` | `{ subscriptions: [{ type }] }` | `{}` |

### Current Events

| Event | Payload |
|-------|---------|
| `pane.agent_status_changed` | `{ pane_id, agent_status, previous_agent_status }` |
| `workspace.created` | `{ workspace_id, label }` |
| `workspace.updated` | `{ workspace_id, label }` |
| `workspace.closed` | `{ workspace_id }` |
| `pane.output_matched` | `{ pane_id, content }` |

## Agent State Machine

```
                    ┌──────────┐
                    │  IDLE     │◄──────────┐
                    └────┬─────┘            │
                         │                  │
                    ┌────▼─────┐            │
                    │ WORKING  │            │
                    └──┬───┬───┘            │
                       │   │                │
              ┌────────▼┐  └───────────────┐│
              │ BLOCKED  │                 ││
              └──────────┘            ┌────▼▼────┐
                                       │   DONE    │
                                       └───────────┘
```

## Complexity Score by Feature Area

| Feature Area | Lines of Code | Complexity | Priority | Dependencies |
|-------------|---------------|------------|----------|-------------|
| TCP socket networking | ~400 (Impl) | 🟡 Medium | P0 | Ktor client |
| Settings/preferences | ~150 | 🟢 Low | P0 | DataStore |
| Home command center | ~250 | 🟢 Low | P0 | ViewModel + Repo |
| Workspace dashboard | ~160 | 🟢 Low | P0 | ViewModel |
| Chat/agent session | ~300 | 🟡 Medium | P0 | Markdown parser |
| File explorer | ~240 | 🟡 Medium | P1 | SAF/DocumentFile |
| Search | ~100 | 🟢 Low | P1 | — |
| Notifications | ~75 | 🟢 Low | P1 | FCM |
| SSH | ~400 | 🟡 Medium | P2 | JSch library |
| Git clone | ~200 | 🟡 Medium | P2 | JGit library |
| Agent history | — | 🟠 High | P2 | Server endpoint |
| Multi-profile | — | 🟠 High | P2 | Room DB |
| Offline mode | — | 🔴 Very High | P3 | Room + sync engine |
| Voice input | — | 🔴 Very High | P3 | Speecg recognizer |
| File editing | — | 🟠 High | P3 | Code editor lib |

## Missing Pieces for "Claude Code on Phone" Vision

### Gaps vs. Claude Code / Replit

| Capability | Current | Target | Effort |
|-----------|---------|--------|--------|
| **Always-on persistent connection** | TCP reconnect with backoff | Mosh-like roaming (UDP SSP) | 🔴 High |
| **Rich markdown/code rendering** | Basic parser | Full syntax highlighting + diff view | 🟡 Medium |
| **Live agent output streaming** | Push events (output_matched) | Chunked streaming with progress indicators | 🟢 Low |
| **Stop/kill agent** | ❌ Missing | One-tap stop with confirmation | 🟢 Low |
| **Real git awareness** | None | Diff viewer, branch switcher, commit log | 🟠 High |
| **Voice input** | None | Speech-to-text prompt entry | 🟠 High |
| **File editing on device** | Read-only | Syntax-highlighted editor + save | 🟠 High |
| **Multi-server profiles** | Single hardcoded | Named profiles + quick switch | 🟡 Medium |
| **Agent run history** | None | Timeline per pane + replay | 🟠 High |
| **Offline cache** | None | Room-based workspace cache | 🟠 High |
| **Biometric lock** | None | App-level security | 🟢 Low |
| **Deep linking** | Basic paneId | Agent completion → notification → deep link | 🟢 Low |

## Key Technical Decisions Needed

1. **Network resilience** — Pure TCP (current) vs. WebSocket vs. Mosh-like UDP. TCP breaks on network roam. WebSocket adds HTTP upgrade overhead. UDP/SSP enables roaming but needs server changes.

2. **File protocol** — Current SAF (Storage Access Framework) browses phone-local files. Need a remote file protocol over the same JSON-RPC socket for true remote workspace access.

3. **Code editor** — Need a Compose-compatible code editor or WebView-based editor (CodeMirror/Monaco via `html preview`-style rendering).

4. **Offline strategy** — Room DB for workspace/pane cache + action queue for offline commands + sync when reconnected.

5. **Auth model** — Currently no auth. Need token-based auth over TLS (Cloudflare tunnel provides TLS termination).

## Build Phases

### Phase 1 (Current State): Remote Agent Dashboard ✅
- [x] TCP connection to herdr.dev
- [x] Workspace/pane tree view
- [x] Chat with agents
- [x] Nudge idle agents
- [x] Connection status indicators
- [x] Background monitoring + notifications

### Phase 2: Rich Agent Control (Next — 2-4 weeks)
- [ ] Stop/kill agents from phone
- [ ] Rich markdown rendering (syntax-highlighted code blocks, tables)
- [ ] Create workspaces/panes from phone
- [ ] Delete workspaces/panes from phone
- [ ] Agent settings (model, temperature, tools)
- [ ] Multiple server profiles

### Phase 3: Workspace Deep Access (4-8 weeks)
- [ ] Remote file browser (server filesystem over JSON-RPC)
- [ ] File download to phone
- [ ] File upload from phone to server
- [ ] File viewer (syntax highlighted)
- [ ] Basic file editing
- [ ] Git status + diff viewer
- [ ] Agent run history

### Phase 4: Mobile-First Vibe Coding (8-16 weeks)
- [ ] Voice-to-prompt input
- [ ] Full code editor with syntax highlighting
- [ ] Offline workspace cache
- [ ] Action queue (offline commands → sync)
- [ ] Biometric app lock
- [ ] Network roaming support (Mosh-like)
- [ ] Native file sharing (share-to-agent)
- [ ] Agent approval queue (approve/reject code changes)

## Risk Areas

1. **Apple App Store** — If targeting iOS later, Apple is actively hostile to vibe coding apps. Android-first strategy avoids this.
2. **Server dependency** — App is useless without a reachable herdr.dev server. Offline mode mitigates but doesn't eliminate.
3. **TCP fragility** — Current TCP socket breaks on network roam. Users switching WiFi↔cellular will lose connection.
4. **Mock mode gap** — Mock mode works for UI dev but diverges from real server behavior.
5. **No auth** — Anything with Cloudflare tunnel access can connect. Need authentication before production.
