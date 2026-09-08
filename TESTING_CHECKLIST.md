# Testing Checklist for Herdr Android App

## Before Testing

### Prerequisites
- [ ] Herdr server running locally on port 8765
- [ ] Android app built and installed on device/emulator
- [ ] Device/emulator on same network as server (or use Cloudflare tunnel)

### Server Setup Options

**Option 1: Local Network (Same WiFi)**
1. Find your machine's IP: `ifconfig` or `ip addr`
2. Use IP like `192.168.1.100` in app settings
3. Port: `8765`

**Option 2: Android Emulator**
1. Use `10.0.2.2` in app settings (special IP for emulator to access host)
2. Port: `8765`

**Option 3: Cloudflare Quick Tunnel**
1. Run: `cloudflared tunnel --url http://localhost:8765`
2. Copy the generated URL (e.g., `https://random-name.trycloudflare.com`)
3. Use hostname in app settings (e.g., `random-name.trycloudflare.com`)
4. Port: `443`

## Core Functionality Tests

### 1. App Launch & Connection
- [ ] App launches without crash
- [ ] Home screen displays correctly
- [ ] Connection status indicator shows (WiFi icon)
- [ ] Initial connection attempt to server

### 2. Server Configuration
- [ ] Navigate to Settings
- [ ] Enter server host (IP or tunnel URL)
- [ ] Enter server port (8765 or 443 for tunnel)
- [ ] Save settings
- [ ] Connection status updates to connected (green)

### 3. Dashboard & Workspaces
- [ ] Navigate to Dashboard
- [ ] Workspaces load from server
- [ ] Workspace names display correctly
- [ ] Tabs and panes show under workspaces
- [ ] Agent names and titles display
- [ ] Agent states show (WORKING, IDLE, DONE, BLOCKED)
- [ ] Panes sorted by attention score

### 4. Chat & Commands
- [ ] Click on a pane to open chat
- [ ] Chat screen displays with pane ID
- [ ] Type a message in input field
- [ ] Send message
- [ ] Message appears in chat
- [ ] Agent response displays
- [ ] Markdown rendering works (if any code blocks)
- [ ] Can scroll through message history

### 5. Agent Nudging
- [ ] On Dashboard, find idle or blocked agent
- [ ] Click "Nudge Agent" button
- [ ] Agent state updates
- [ ] Agent resumes working

### 6. Search
- [ ] Navigate to Search screen
- [ ] Enter search query
- [ ] Results filter by workspace name
- [ ] Results filter by pane title/agent name
- [ ] Click result to navigate
- [ ] Search is real-time (updates as you type)

### 7. File Explorer
- [ ] Navigate to File Explorer
- [ ] Directory contents display
- [ ] Navigate into subdirectory
- [ ] Navigate back to parent
- [ ] File names, sizes, types display
- [ ] Can select files

### 8. Model Selection
- [ ] On Home screen, click model dropdown
- [ ] All models display (SWE-1.6, Claude 3.5, GPT-4o, Gemini 1.5)
- [ ] Select a model
- [ ] Selected model displays

### 9. SSH Connection
- [ ] Navigate to SSH Connection screen
- [ ] Enter host (your server IP)
- [ ] Enter port (22)
- [ ] Enter username
- [ ] Enter password
- [ ] Toggle "Save credentials securely"
- [ ] Click Connect
- [ ] Connection succeeds
- [ ] Navigate away and back to SSH screen
- [ ] Credentials auto-fill (if saved)
- [ ] Execute a command (via executeCommand or terminal)
- [ ] Command output displays

### 10. Quick Actions
- [ ] On Home screen, type in "Ask anything" field
- [ ] Click Send
- [ ] Navigates to chat with message
- [ ] Click a session suggestion
- [ ] Navigates to chat with suggestion

## Edge Cases & Error Handling

### Connection Issues
- [ ] Start app with server offline
- [ ] Connection status shows disconnected (red)
- [ ] Error message displays when trying to use features
- [ ] Start server while app is running
- [ ] Reconnect manually via settings or app restart
- [ ] Connection status updates to connected (green)

### Network Changes
- [ ] Connect on WiFi
- [ ] Switch to cellular
- [ ] Connection may drop (expected with TCP)
- [ ] Reconnect manually or wait for auto-reconnect

### Invalid Inputs
- [ ] Enter invalid server IP
- [ ] Connection fails gracefully
- [ ] Error message displays
- [ ] Enter invalid SSH credentials
- [ ] Connection fails gracefully
- [ ] Error message displays

### Large Data
- [ ] Workspace with many panes (10+)
- [ ] Chat with many messages (50+)
- [ ] File explorer with many files

## Security Tests

### Credential Storage
- [ ] Save SSH credentials with checkbox
- [ ] Close and reopen app
- [ ] Credentials persist and auto-fill
- [ ] Uncheck "Save credentials"
- [ ] Credentials are cleared

### Connection Security
- [ ] Server uses HTTPS (if using Cloudflare tunnel)
- [ ] SSH connection uses encrypted channel

## Known Limitations (Not Yet Implemented)

- **Git Clone**: UI complete but simulated (needs JGit library)
- **File Editing**: Read-only explorer (needs code editor)
- **Agent Stopping**: Can nudge but not kill agents
- **Workspace Creation**: View only
- **Agent History**: No historical view
- **Push Notifications**: No FCM integration
- **Offline Mode**: No local caching
- **Multiple Server Profiles**: Single server only
- **Biometric Auth**: No security layer
- **Theme Toggle**: Dark theme only

## After Testing

### If Issues Found
1. Note the specific issue
2. Check logs in Android Studio Logcat
3. Verify server is running and accessible
4. Check network connectivity
5. Review error messages in app

### If Everything Works
1. App is ready for production use
2. Consider implementing remaining features from USER_STORIES.md
3. Set up Cloudflare permanent tunnel for remote access
4. Deploy to Play Store (if desired)

## Quick Test Commands

### Verify Server Running
```bash
curl http://localhost:8765
# Or with tunnel:
curl https://your-tunnel-url.trycloudflare.com
```

### Test SSH Connection
```bash
ssh user@your-server-ip
```

### Check Cloudflare Tunnel
```bash
cloudflared tunnel --url http://localhost:8765
```

## Notes

- The app uses HerdrSocketRepositoryImpl for real server connections
- No mock mode - requires actual Herdr server to function
- SSH uses JSch library with keepalive and auto-reconnection
- Credentials stored using AndroidX EncryptedSharedPreferences
- Tests are written but require real server to execute
- Use emulator IP `10.0.2.2` to access host machine from emulator
