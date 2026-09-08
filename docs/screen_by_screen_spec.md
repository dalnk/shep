# Screen-by-screen product spec for the Herdr Android remote-agent app

## 1. Product vision
The app should feel like a polished, phone-first command center for remote coding agents. It should feel calm, operational, and action-oriented rather than technical, chat-app-like, or overly abstract.

The app should help a user answer three questions immediately:
1. What is connected?
2. What is happening right now?
3. What should I do next?

This spec is intended as a handoff document for product, design, and implementation. It is optimized for the current Kotlin Compose app architecture and for the existing screens: HomeScreen, DashboardScreen, ChatScreen, FileExplorerScreen, and SettingsScreen.

---

## 2. Core user and product goals

### Primary user
A developer who wants to monitor and guide coding agents from an Android phone while away from their laptop.

### Primary jobs to be done
- Connect to a remote host or local agent environment
- See what agents are working on right now
- Send a new instruction quickly
- Understand whether work is progressing, blocked, or idle
- Intervene when needed with nudges, redirects, or stop actions
- Inspect project context such as files or repo structure

### Success definition
The experience is successful when the user can:
- understand the app in under 5 seconds,
- identify urgent work at a glance,
- take a meaningful action with one or two taps,
- feel confident that the system is connected and alive.

---

## 3. Product principles
These principles should govern all screens and interactions.

- Clarity over density: show the most useful information first.
- State over noise: status and urgency should be visually obvious.
- Action over explanation: the user should always know what they can do next.
- Calm over clever: the interface should feel composed and trustworthy.
- Mobile-first: touch targets, spacing, and hierarchy should be optimized for phones.
- Progressive disclosure: surface the core loop first and reveal deeper tools later.

---

## 4. Functional scope

### MVP scope
The first version should focus on the core loop:
- connect to the host,
- see active agent work,
- send a new instruction,
- review output,
- nudge or stop work,
- inspect files.

### Explicitly out of scope for the first release
- full IDE editing experience,
- full git diff review experience,
- multi-user collaboration UI,
- voice input,
- full terminal emulation,
- complex automation flows.

---

## 5. Global interaction model

### Primary navigation flow
Home → Agent Session / Workspace Board → File Explorer / Settings

### Shared interaction patterns
- Tapping an active work card opens the relevant agent session.
- Quick actions should be visible, obvious, and low-friction.
- All screens should preserve the user’s current context where possible.
- Empty, loading, and error states should be clearly designed rather than left blank.
- The app should use consistent status labels and colors across all screens.

### Shared state model
Every major screen should support at least these states:
- loading
- empty
- populated
- connected
- disconnected
- error
- blocked

---

## 6. Visual and interaction design system

### Tone
- calm
- premium
- operational
- trustworthy
- slightly futuristic, but not flashy

### Layout principles
- large touch targets
- generous vertical spacing
- strong hierarchy
- less text, more meaning
- concise labels and short status summaries

### Color system
- Primary accent: for primary actions and selected states
- Success/working: green or teal for healthy or active progress
- Warning/blocked: amber or orange for blocked or intervention-needed states
- Error/disconnected: red for failure or disconnect states
- Neutral surfaces: for background cards and secondary UI

### Typography
- clear hierarchy with short labels
- bold for section names and key status
- body text should be concise and scannable

### Surface treatment
- rounded cards
- subtle elevation or contrast
- clear separation between sections
- consistent padding and spacing rhythm

---

## 7. Screen-by-screen spec

## Screen 1: Home / Control Center

### Purpose
This is the app’s primary entry experience and the main “command center.” It should feel like a fast, focused launchpad for monitoring and steering agent work.

### Primary user goal
Start or continue agent work from the phone without opening a laptop.

### Core experience
The user should be able to:
- see the connection status at a glance,
- enter a new prompt or instruction,
- jump into active work,
- access common tools quickly.

### Layout
Top area
- app title or product name
- connection status chip
- settings icon

Hero area
- large rounded card with the prompt composer
- single-line or multi-line input area
- primary send button
- optional suggested prompts, such as:
  - “Fix the auth bug”
  - “Review this diff”
  - “Continue from last checkpoint”

Quick actions row
- Files
- Source
- SSH
- Search

Active work area
- heading: “Active work” or “Agent sessions”
- cards for current agent tasks
- each card includes:
  - agent name
  - task title
  - current state
  - short status summary or last update

### Components
- top app bar
- connection status chip
- hero prompt card
- quick action tiles
- active work cards
- empty state card

### Interaction requirements
- Tapping send should validate that the input is not empty.
- Tapping an active card should open the session view.
- Tapping settings should open the settings screen.
- Tapping a quick action should open the corresponding tool screen.

### States
Connected + work present
- show the prompt composer and active work list

Connected + no active work
- show a calm empty state with a clear CTA

Disconnected
- show a reconnect guidance card and keep the prompt composer available

Loading
- show skeleton placeholders or a neutral loading state

Error
- show a short message with a retry action if the connection or data load fails

### Visual rules
- rounded cards and generous spacing
- primary action should be visually dominant
- blocked or urgently needing attention work should stand out
- the layout should feel like an operation center rather than a feed

### Acceptance criteria
- The user can identify connection state immediately.
- The user can send a new instruction without navigating elsewhere.
- The user can see at least one meaningful next action from the home screen.

---

## Screen 2: Workspace Board / Dashboard

### Purpose
This screen gives the user a broader operational view of workspaces, tabs, and agent panes.

### Primary user goal
Understand the overall state of the system and prioritize work.

### Core experience
The user should be able to:
- see workspaces grouped clearly,
- understand which agents are active, blocked, idle, or done,
- jump into a specific session quickly,
- identify high-priority work.

### Layout
Header
- screen title: “Workspaces” or “Agent board”
- optional search or filter affordance

Workspace sections
- each workspace appears as a grouped section
- each section contains tabs and pane cards

Pane card content
- task title
- agent name
- state badge
- optional quick actions such as Open, Nudge, Stop

### Components
- workspace section headers
- tab labels
- pane cards
- status badge
- quick action icons

### Interaction requirements
- Tapping a pane card should open the relevant session view.
- Tapping quick actions should trigger the intended action without leaving the screen.
- Blocked or active items should be prioritized visually.

### States
Populated board
- show all current workspaces and panes

Empty board
- show an empty state with a helpful message

Loading
- show a lightweight loading state while data is fetched

Error
- show a message and retry action if load fails

### Visual rules
- grouped sections should feel like a board, not a long list
- blocked or active work should be visually more prominent
- compact cards should still remain readable on a phone
- status should remain consistent with the home screen

### Acceptance criteria
- The user can identify which workspaces are important or urgent.
- The user can understand the state of each workspace without extra explanation.
- The user can open a session directly from the board.

---

## Screen 3: Agent Session / Chat

### Purpose
This is the focused view for one agent or one task. It is where the user can inspect output, send a follow-up instruction, and intervene.

### Primary user goal
Guide the ongoing work of a specific agent.

### Core experience
The user should be able to:
- read the latest output,
- send follow-up instructions,
- review the conversation context,
- take action such as nudging or stopping the task.

### Layout
Top bar
- back button
- session title
- agent name
- optional workspace context
- status chip

Message history area
- user messages and agent messages are visually distinct
- code blocks and plain text are formatted clearly
- message spacing should feel calm and readable

Composer area
- text input at the bottom
- send button
- optional quick action chips such as:
  - “Nudge”
  - “Continue”
  - “Stop”

### Components
- top app bar
- message bubbles
- markdown/code formatting blocks
- composer field
- send button
- quick action chips

### Interaction requirements
- Sending a message should append the new draft to the conversation state.
- The view should auto-scroll to the newest message when new content appears.
- The user should have a clear path to intervene when the agent seems stuck.

### States
Streaming output
- show the latest output clearly and keep the message feed readable

Blocked state
- show a clear warning or callout that intervention may be needed

Idle state
- show a calmer view with fewer visual cues

Empty conversation
- show a minimal empty state if no messages exist yet

### Visual rules
- the screen should feel more airy than the dashboard
- code blocks should be easy to scan
- composer should be anchored and reachable with one hand
- the screen should support both quick follow-up and deeper reading

### Acceptance criteria
- The user can understand the current context without reading a large amount of text.
- The user can send a follow-up instruction quickly.
- The user can perceive whether the task needs intervention.

---

## Screen 4: File Explorer

### Purpose
This screen helps the user inspect project files and repository context quickly from the phone.

### Primary user goal
Inspect the current workspace or relevant files without opening a laptop.

### Core experience
The user should be able to:
- browse folders and files,
- navigate to a useful directory,
- open or inspect files,
- understand the structure of the project quickly.

### Layout
Header
- current directory path or breadcrumb
- back action to go to the parent folder

Content list
- folders and files shown clearly
- each row includes:
  - icon
  - name
  - metadata where useful

Actions row
- open
- inspect
- download

### Components
- breadcrumb/header
- file/folder rows
- selection state
- action buttons

### Interaction requirements
- Tapping a folder should open it.
- Tapping a file should open its content or an inspection view.
- The back action should navigate to the parent directory.

### States
Populated directory
- show a clear list of folders and files

Empty directory
- show a helpful empty state message

Loading
- show a placeholder while the list is being loaded

Error
- show a message if the directory fails to load

### Visual rules
- simple and touch-friendly
- low visual noise
- clear hierarchy between folders and files
- larger tap targets than a desktop-style file explorer

### Acceptance criteria
- The user can navigate to a file or folder quickly.
- The user can identify whether an item is a folder or a file.
- The user can understand the current location without confusion.

---

## Screen 5: Settings

### Purpose
This screen manages connection, preferences, and experience configuration.

### Primary user goal
Customize how the app connects and behaves.

### Core experience
The user should be able to:
- configure host / port / connection profile,
- set theme preference,
- manage notifications,
- enable or disable sensitive protections if supported.

### Layout
Sections
- Connection
- Appearance
- Notifications
- Security

Each section uses compact rows with labels and toggles or actions.

### Components
- section headers
- setting rows
- toggles
- text fields
- action buttons

### Interaction requirements
- The user should be able to change connection settings and save them.
- Theme changes should be reflected immediately.
- Important settings should have clear confirmation or saved state feedback.

### States
Default state
- show existing values and current options

Unsaved changes
- indicate that changes have not been saved yet if relevant

Error
- show validation or save failure feedback

### Visual rules
- clear and structured
- calm and predictable
- avoid overwhelming the user with too many settings at once

### Acceptance criteria
- The user can find and adjust the most important settings quickly.
- The user can understand the current connection and preference state at a glance.

---

## 8. Navigation and information architecture

### Primary flow
Home → Agent Session / Workspace Board → File Explorer / Settings

### Secondary flow
Home → Settings
Home → Files
Dashboard → Session
Session → Files

### Navigation expectations
- The app should support back navigation consistently.
- Tapping a work card should feel like entering a focused activity, not jumping into a deep technical tree.
- The home screen should remain the clearest entry point.

---

## 9. Content strategy and copy direction
Use product-style language over technical labels wherever possible.

### Prefer
- Active work
- Agent sessions
- Send instruction
- Continue task
- Need attention
- Connected
- Reconnect

### Avoid where possible
- pane
- dashboard
- socket
- internal implementation terms
- overly technical status labels

### Copy tone
- concise
- calm
- confident
- lightly productized

---

## 10. Accessibility requirements
The app should support a usable experience for a broad range of users.

### Requirements
- Minimum touch target size: 48dp
- All interactive elements should have clear labels
- Text contrast should be strong enough for readability
- States should be communicated with both color and text where relevant
- Screen reader labels should be meaningful
- The layout should support larger font scaling without breaking structure

---

## 11. Performance and responsiveness expectations
- Screen transitions should feel fast and responsive.
- Loading states should appear rather than leaving blank space.
- The home screen should remain usable even when data is partial or stale.
- The chat view should remain smooth while a conversation grows.

---

## 12. Implementation notes for this repo
The current app structure already aligns well with this spec.

### Current screen mapping
- HomeScreen: should become the command center
- DashboardScreen: should become the workspace board
- ChatScreen: should become the focused agent session view
- FileExplorerScreen: should become the lightweight inspection surface
- SettingsScreen: should remain the preferences/configuration surface

### Notes for implementation
- Reuse the existing view model structure where possible.
- Make state styling consistent across screens.
- Introduce consistent empty/loading/error states as shared patterns.
- Prioritize HomeScreen, DashboardScreen, and ChatScreen first because they shape the primary experience.

---

## 13. Recommended implementation priority
### Phase 1
1. HomeScreen
2. DashboardScreen
3. ChatScreen

### Phase 2
4. FileExplorerScreen
5. SettingsScreen

### Phase 3
- refine empty states,
- add richer status treatment,
- improve quick actions,
- tighten copy and visual hierarchy.

---

## 14. Definition of done
The app should feel like a polished, usable remote agent control experience when:
- the home screen immediately communicates connection and action,
- the dashboard feels like a board of live work,
- the chat screen feels like a focused session view,
- the file explorer feels lightweight and useful,
- the settings screen feels calm and configurable,
- and the entire flow feels like a coherent, productized experience rather than a collection of screens.
