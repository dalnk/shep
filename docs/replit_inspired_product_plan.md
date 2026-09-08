# Replit-inspired product plan for the Herdr Android remote-agent app

## Goal
Turn the current app from a generic remote-agent dashboard into a polished, phone-first “agent command center” that feels more like a focused product experience and less like a technical prototype.

The design inspiration comes from Replit’s pattern:
- clear product promise,
- strong first-run success,
- polished screen states,
- fast action loops,
- and a calm but premium visual experience.

---

## Product positioning
The app should be positioned as:

“Herdr is a phone-first command center for coding agents — a calm, polished way to monitor, guide, and intervene in remote software work from anywhere.”

This is more compelling than saying “it’s a remote agent dashboard” because it emphasizes action, control, and momentum.

---

## Core user promise
From the home screen, the user should immediately feel that they can:
- connect to their remote agent environment,
- see what is happening now,
- send a new instruction,
- follow the latest output,
- intervene if something is blocked or running too long.

---

## Recommended UX direction

### 1. Home screen becomes a command center
The current HomeScreen is already close to the right domain, but it should feel more intentional and polished.

#### New experience goals
- feel like an operation panel, not a generic dashboard
- answer the user’s immediate questions in one glance
- make the primary action obvious

#### Proposed structure
- top bar with connection status and profile/settings access
- large prompt composer as the hero action
- quick actions row for Files / Source / SSH / Search
- “Active work” section with cards for each agent/session
- optional “Suggested next actions” strip for common tasks

#### Visual treatment
- rounded cards
- stronger visual hierarchy
- clear state chips for working / blocked / idle / done
- primary CTA for “Send prompt”

### 2. Dashboard becomes a board, not a list
The current DashboardScreen is functional but still looks like a technical list.

#### New experience goals
- feel like a workspace board with priority and urgency
- make important work stand out
- show the system as alive and organized

#### Proposed structure
- grouped by workspace
- each workspace with a compact summary line
- each pane card showing agent, task title, status, and quick actions
- urgent items appear earlier
- blocked work gets more visual weight

#### Visual treatment
- status-driven card colors
- compact but rich metadata
- subtle grouping separators
- quick action buttons for nudge / open / stop

### 3. Chat becomes a live session view
The current ChatScreen is already close to the correct domain, but it should feel more like a live operation surface.

#### New experience goals
- feel like a focused remote session, not a generic chat screen
- support real-time feedback and follow-up actions
- highlight the next action clearly

#### Proposed structure
- header with agent name, workspace context, and connection status
- message history with user/agent separation
- code blocks and plain text formatting
- composer at the bottom with clear send affordance
- optional quick action chips such as “Nudge”, “Continue”, “Stop”

#### Visual treatment
- more generous spacing
- grouping around major turns
- better code block presentation
- stronger affordance for the next step

### 4. File explorer becomes a lightweight workspace inspection tool
The current FileExplorerScreen should be framed less like a generic file browser and more like a context tool for the active agent.

#### New experience goals
- help the user inspect the project without friction
- make file inspection feel fast and mobile-friendly

#### Proposed structure
- breadcrumb path
- list of files/folders
- clear file icons and metadata
- quick actions for open / inspect / download

#### Visual treatment
- simpler hierarchy
- larger tap targets
- reduced visual noise

---

## Suggested screen hierarchy
1. Home / Control Center
   - primary entry point
   - shows active work and allows prompt entry

2. Workspace Board
   - deeper overview of all workspaces and panes

3. Agent Session
   - conversation + output + next actions

4. File Explorer
   - inspect workspace context

5. Settings
   - connection, profiles, theme, notifications

This hierarchy is better than presenting the app as a generic “app with many screens.” It creates a clear operational flow.

---

## Replit-inspired interaction principles

### A. Clear primary action
Every screen should have one obvious next step.
- Home: send prompt
- Dashboard: jump into a current task
- Chat: continue or intervene
- Files: inspect an item

### B. Visible system state
The user should see whether the system is:
- connected,
- busy,
- blocked,
- idle,
- or needs intervention.

### C. Low-friction recovery
If an agent is blocked, the user should quickly be able to:
- nudge,
- continue,
- stop,
- or redirect.

### D. Progressive disclosure
The app should not dump every capability at once. It should lead with the main loop and reveal deeper tools as needed.

### E. Premium, calm polish
The product should feel like it has been designed, not just assembled. That means:
- deliberate spacing,
- intentional colors,
- fewer but stronger visual cues,
- and a consistent hierarchy.

---

## Implementation plan for this repo

### Phase 1: Reframe the current experience
Focus on the existing screens first.

#### HomeScreen
Changes to make:
- rename the top title to something more product-like, such as “Agent Control” or “Remote Control”
- make the prompt composer the visual centerpiece
- add a status chip for connection
- make active agent cards feel more like operational cards
- add quick-action tiles instead of a generic icon row

#### DashboardScreen
Changes to make:
- add a stronger section header and summary
- group panes with workspace names and section separators
- make blocked or working work visually distinct
- add quick actions per pane: open / nudge / stop

#### ChatScreen
Changes to make:
- improve the header so it feels like a session context view
- make the composer more prominent
- improve code block and formatting presentation
- add a small footer row with quick actions if appropriate

### Phase 2: Add product polish
Focus on the experience layer rather than just the data layer.

#### Add stronger empty state patterns
- no active agents → show a calm “No active work” state with a clear next action
- disconnected → show a friendly reconnect prompt

#### Add richer status visuals
- green for working
- amber for blocked
- neutral for idle
- blue or purple for completed or selected

#### Add better action affordances
- nudge
- continue
- stop
- open in chat
- open files

### Phase 3: Add a stronger first-run experience
This is where Replit’s approach matters most.

#### Suggested first flow
1. App opens
2. Connection status visible
3. Prompt composer ready
4. One suggested action visible
5. Active agents shown immediately
6. User can act without digging

This is a much better first impression than a blank or overly technical screen.

---

## Recommended UI components to introduce

### 1. Status chip
A compact pill showing:
- Connected
- Reconnecting
- Offline
- Blocked

### 2. Hero prompt card
A large rounded card that contains:
- a prompt input,
- a strong send action,
- and optional suggested prompts.

### 3. Agent summary card
Each card should show:
- agent name,
- task title,
- state badge,
- recent action or last update,
- quick action button.

### 4. Empty-state card
Used when there is no active work or no connection.

### 5. Section header
Used on Home / Dashboard to create clear hierarchy and reduce cognitive load.

---

## Content strategy for the product
To make the app feel more productized, the UI copy should be more outcome-oriented.

Instead of:
- “Dashboard”
- “Pane”
- “Send message”

Use:
- “Active work”
- “Agent sessions”
- “Send instruction”
- “Continue task”
- “Need attention”

This small shift makes the experience feel more intentional and less technical.

---

## Related Docs

- [Market Research](./vibe_coding_market_research) — competitive landscape, trends, positioning
- [Complexity Map](./complexity_map) — architecture layers, protocol surface, risk areas, build phases
- [Visual Mockups](./visual_mockups) — screen designs, component specs, states, layout
- [Full User Stories](./full_user_stories) — acceptance criteria, priority matrix, user journeys

## Summary
The Replit-inspired direction for this app is not to copy Replit’s exact UI, but to adopt its product discipline:
- make the first experience feel useful immediately,
- make the app feel polished from the start,
- emphasize action, not complexity,
- and make the user feel like they are operating a focused product rather than navigating a technical dashboard.

The best next step is to implement the HomeScreen and DashboardScreen first with this new framing, because those are the screens that define the product experience most strongly.
