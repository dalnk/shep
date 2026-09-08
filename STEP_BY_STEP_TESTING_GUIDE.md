# Step-by-Step Testing Guide for Herdr Android App

## Prerequisites

1. **Herdr Server Running**
   ```bash
   # Verify server is running
   lsof -i :8765
   # Should show Python process listening on port 8765
   ```

2. **Android App Built**
   - Build APK using Android Studio or command line
   - Install on device or emulator

3. **Network Setup**
   - **For Emulator**: Server accessible via `10.0.2.2`
   - **For Physical Device**: Device and server on same WiFi

---

## Step 1: Launch the App

**What You Should See:**
- App launches with splash screen
- Home screen appears with:
  - Top bar showing "Herdr" title
  - Connection status indicator (WiFi icon)
  - Initially red (disconnected) or green (if auto-connected)

**Expected Screen:**
```
┌─────────────────────────────────┐
│ ← Herdr              ⚙️ 🔴      │
├─────────────────────────────────┤
│                                 │
│  Ask anything                   │
│  ┌─────────────────────────┐   │
│  │ Type your message...     │   │
│  └─────────────────────────┘   │
│          [Send]                │
│                                 │
│  Local section                  │
│  ┌─────────────────────────┐   │
│  │ 📁 Open project         │   │
│  │ 📥 Clone repo           │   │
│  │ 🔌 Connect via SSH      │   │
│  └─────────────────────────┘   │
│                                 │
│  Start new session              │
│  • "Fix the bug in main.py"     │
│  • "Review PR #123"             │
│  • "Add tests for auth"         │
│                                 │
│  🤖 Model: SWE-1.6 ▼           │
└─────────────────────────────────┘
```

**Action:**
- Note the connection status (red = disconnected, green = connected)

---

## Step 2: Configure Server Connection

**What You Should Do:**
1. Tap the ⚙️ (settings) icon in top right
2. Settings screen opens

**Expected Screen:**
```
┌─────────────────────────────────┐
│ ← Settings                      │
├─────────────────────────────────┤
│                                 │
│  Server Configuration            │
│  ┌─────────────────────────┐   │
│  │ Server Host              │   │
│  │ 10.0.2.2                │   │
│  └─────────────────────────┘   │
│  ┌─────────────────────────┐   │
│  │ Server Port              │   │
│  │ 8765                    │   │
│  └─────────────────────────┘   │
│                                 │
│  Appearance                     │
│  [Dark Theme]                   │
│                                 │
│  Notifications                  │
│  [Enabled]                      │
│                                 │
│  About                          │
│  Herdr v1.0.0                   │
└─────────────────────────────────┘
```

**Action:**
- For **Emulator**: Enter `10.0.2.2` for host, `8765` for port
- For **Physical Device**: Enter your machine's IP (e.g., `192.168.1.100`), port `8765`
- Tap back to return to Home screen

**Expected Result:**
- Connection status indicator should turn green
- If not, tap the WiFi icon to manually reconnect

---

## Step 3: View Dashboard

**What You Should Do:**
1. From Home screen, tap "Go to agent manager" or navigate to Dashboard
2. Dashboard screen opens

**Expected Screen (if server has workspaces):**
```
┌─────────────────────────────────┐
│ ← Herdr dashboard               │
├─────────────────────────────────┤
│                                 │
│  📂 Project: my-app             │
│  ┌─────────────────────────┐   │
│  │ 📄 main.py              │   │
│  │   🤖 Agent: claude-3.5  │   │
│  │   🟢 WORKING            │   │
│  │   [Nudge Agent]         │   │
│  └─────────────────────────┘   │
│  ┌─────────────────────────┐   │
│  │ 📄 auth.py              │   │
│  │   🤖 Agent: gpt-4o      │   │
│  │   🔵 IDLE               │   │
│  │   [Nudge Agent]         │   │
│  └─────────────────────────┘   │
│                                 │
│  📂 Project: backend-api        │
│  ┌─────────────────────────┐   │
│  │ 📄 server.js            │   │
│  │   🤖 Agent: gemini-1.5  │   │
│  │   🟢 WORKING            │   │
│  │   [Nudge Agent]         │   │
│  └─────────────────────────┘   │
└─────────────────────────────────┘
```

**Expected Screen (if no workspaces):**
```
┌─────────────────────────────────┐
│ ← Herdr dashboard               │
├─────────────────────────────────┤
│                                 │
│  No workspaces found            │
│                                 │
│  Make sure your Herdr server    │
│  is running and connected.      │
│                                 │
│  [Refresh]                      │
└─────────────────────────────────┘
```

**Action:**
- Tap on a pane (e.g., "main.py") to open chat
- Note the agent state (WORKING, IDLE, DONE, BLOCKED)

---

## Step 4: Chat with Agent

**What You Should Do:**
1. Tap on a pane from Dashboard
2. Chat screen opens

**Expected Screen:**
```
┌─────────────────────────────────┐
│ ← main.py - claude-3.5          │
├─────────────────────────────────┤
│                                 │
│  💬 Agent:                      │
│  I'm analyzing the code...      │
│                                 │
│  💬 You:                        │
│  Fix the bug in the auth        │
│  function                       │
│                                 │
│  💬 Agent:                      │
│  I found the issue. The token   │
│  validation is missing.          │
│  Here's the fix:                │
│  ```python                      │
│  def validate_token(token):      │
│      if not token:               │
│          return False            │
│      return verify(token)        │
│  ```                            │
│                                 │
│  [Scroll down for more]          │
│                                 │
├─────────────────────────────────┤
│ ┌─────────────────────────┐     │
│ │ Type your message...     │ [Send]│
│ └─────────────────────────┘     │
└─────────────────────────────────┘
```

**Action:**
- Type a message in the input field
- Tap "Send"
- Watch for agent response
- Messages should appear in real-time

**Expected Result:**
- Your message appears immediately
- Agent response appears after processing
- Code blocks should be formatted with syntax highlighting

---

## Step 5: Nudge Idle Agent

**What You Should Do:**
1. Return to Dashboard
2. Find an agent with IDLE or BLOCKED state
3. Tap "Nudge Agent" button

**Expected Screen Before Nudge:**
```
┌─────────────────────────┐
│ 📄 auth.py              │
│   🤖 Agent: gpt-4o      │
│   🔵 IDLE               │
│   [Nudge Agent]         │
└─────────────────────────┘
```

**Expected Screen After Nudge:**
```
┌─────────────────────────┐
│ 📄 auth.py              │
│   🤖 Agent: gpt-4o      │
│   🟢 WORKING            │
│   [Nudge Agent]         │
└─────────────────────────┘
```

**Expected Result:**
- Agent state changes from IDLE/BLOCKED to WORKING
- Agent resumes processing tasks

---

## Step 6: Search Workspaces

**What You Should Do:**
1. From Home screen, tap search icon or navigate to Search
2. Search screen opens

**Expected Screen:**
```
┌─────────────────────────────────┐
│ ← Search                        │
├─────────────────────────────────┤
│  ┌─────────────────────────┐   │
│  │ 🔍 Search...            │   │
│  └─────────────────────────┘   │
│                                 │
│  Results:                       │
│                                 │
│  📂 Project: my-app             │
│    📄 main.py                  │
│      🤖 claude-3.5              │
│      [Open Chat]                │
│                                 │
│  📂 Project: backend-api        │
│    📄 server.js                │
│      🤖 gemini-1.5              │
│      [Open Chat]                │
└─────────────────────────────────┘
```

**Action:**
- Type "auth" in search field
- Results filter in real-time
- Tap a result to navigate to chat

**Expected Result:**
- Search filters by workspace name, pane title, and agent name
- Results update as you type
- Tapping result opens chat for that pane

---

## Step 7: File Explorer

**What You Should Do:**
1. From Home screen, tap "Open project"
2. File explorer opens

**Expected Screen:**
```
┌─────────────────────────────────┐
│ ← File Explorer                 │
├─────────────────────────────────┤
│  📁 /home/user/projects         │
│                                 │
│  📁 my-app/                     │
│  📁 backend-api/                │
│  📄 README.md                   │
│  📄 .gitignore                  │
│                                 │
│  [↑ Parent Directory]           │
└─────────────────────────────────┘
```

**Action:**
- Tap a folder to navigate inside
- Tap "↑ Parent Directory" to go back
- Tap a file to select it

**Expected Result:**
- Directory contents display
- Navigation works smoothly
- File names, sizes, and types shown

---

## Step 8: SSH Connection

**What You Should Do:**
1. From Home screen, tap "Connect via SSH"
2. SSH connection screen opens

**Expected Screen:**
```
┌─────────────────────────────────┐
│ ← SSH Connection                │
├─────────────────────────────────┤
│                                 │
│  Host                           │
│  ┌─────────────────────────┐   │
│  │ 192.168.1.100           │   │
│  └─────────────────────────┘   │
│                                 │
│  Port                           │
│  ┌─────────────────────────┐   │
│  │ 22                      │   │
│  └─────────────────────────┘   │
│                                 │
│  Username                       │
│  ┌─────────────────────────┐   │
│  │ your-username           │   │
│  └─────────────────────────┘   │
│                                 │
│  Password                       │
│  ┌─────────────────────────┐   │
│  │ ••••••••                │ 👁️│
│  └─────────────────────────┘   │
│                                 │
│  ☑ Save credentials securely    │
│                                 │
│  [🔑 Connect]                  │
└─────────────────────────────────┘
```

**Action:**
- Enter your server's SSH details
- Toggle "Save credentials securely" if desired
- Tap "Connect"

**Expected Screen (Connecting):**
```
┌─────────────────────────────────┐
│ ← SSH Connection                │
├─────────────────────────────────┤
│                                 │
│           ⏳                    │
│  Connecting to SSH server...    │
│                                 │
└─────────────────────────────────┘
```

**Expected Screen (Connected):**
```
┌─────────────────────────────────┐
│ ← SSH Connection                │
├─────────────────────────────────┤
│                                 │
│  ✅ Connected successfully!     │
│                                 │
│  [Continue]                     │
└─────────────────────────────────┘
```

**Expected Result:**
- Connection succeeds with valid credentials
- Credentials saved if checkbox enabled
- Next time, fields auto-fill with saved credentials

---

## Step 9: Quick Action from Home

**What You Should Do:**
1. From Home screen, type in "Ask anything" field
2. Tap "Send"

**Expected Result:**
- Navigates to Chat screen
- Message is pre-filled in chat
- Can immediately send to agent

**Alternative:**
- Tap a session suggestion (e.g., "Fix the bug in main.py")
- Navigates to Chat screen with that message pre-filled

---

## Step 10: Model Selection

**What You Should Do:**
1. On Home screen, tap model dropdown
2. Select a different model

**Expected Screen:**
```
┌─────────────────────────────────┐
│                                 │
│  🤖 Model: SWE-1.6 ▼           │
│                                 │
│  ┌─────────────────────────┐   │
│  │ SWE-1.6                │   │
│  │ Claude 3.5 Sonnet      │   │
│  │ GPT-4o                 │   │
│  │ Gemini 1.5 Pro         │   │
│  └─────────────────────────┘   │
└─────────────────────────────────┘
```

**Action:**
- Tap a model to select it
- Selected model displays in dropdown

**Expected Result:**
- Model selection updates
- Future agent interactions use selected model

---

## Troubleshooting

### Connection Issues

**Problem: Connection status stays red**
- Verify Herdr server is running: `lsof -i :8765`
- Check server host/port in Settings
- For emulator: Use `10.0.2.2`
- For physical device: Use machine's local IP
- Check device and server are on same network

**Problem: Dashboard shows no workspaces**
- Server may have no active workspaces
- Tap "Refresh" on Dashboard
- Check server logs for errors

### SSH Issues

**Problem: SSH connection fails**
- Verify SSH server is running on target machine
- Check credentials are correct
- Verify host and port are correct
- Check network connectivity

### General Issues

**Problem: App crashes**
- Check Android Studio Logcat for errors
- Verify all dependencies are installed
- Try clearing app data and reconnecting

---

## Success Criteria

You'll know the app is working correctly when:

✅ Connection status turns green after configuring server
✅ Dashboard displays workspaces/panes from server
✅ Chat screen allows sending messages and receiving responses
✅ Agent nudging changes agent state
✅ Search filters workspaces/panes in real-time
✅ File explorer navigates directories
✅ SSH connection succeeds with valid credentials
✅ Credentials persist after closing and reopening app
✅ Model selection updates and persists
✅ Quick actions navigate to chat with pre-filled messages

---

## Next Steps After Testing

If everything works:
1. App is ready for production use
2. Consider implementing remaining features from USER_STORIES.md
3. Set up Cloudflare permanent tunnel for remote access
4. Deploy to Play Store if desired

If issues found:
1. Note the specific issue and screen
2. Check logs in Android Studio Logcat
3. Verify server is running and accessible
4. Review error messages in app
