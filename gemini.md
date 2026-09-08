# Gemini.md for Shep App Development

## Project Overview
Shep is a companion app for [herdr.dev](https://herdr.dev) — a remote control and monitoring interface for multi-agent coding swarms. It features a modern web PWA and a native Android app with Jetpack Compose, foldable device support, and adaptive layouts.

**Core Features:**
- Real-time agent status monitoring (Working, Blocked, Done, Idle)
- Send follow-up instructions to any agent pane
- Adaptive layouts for phones, tablets, and foldables
- Background monitoring with notifications
- Fast pairing via LAN, Tailscale, or direct IP

## Tech Stack
- **Language**: Kotlin (Android), TypeScript (Web PWA)
- **Android UI**: Jetpack Compose + Material 3
- **Web UI**: React + Vite + Lucide icons
- **Architecture**: MVVM with ViewModel + StateFlow (Android), single-page app (Web)
- **Dependency Injection**: Hilt (Android)
- **Networking**: NDJSON over TCP via herdr-bridge (Python)
- **State**: Jetpack DataStore Preferences (Android), localStorage (Web)
- **Foldables**: Jetpack WindowManager

## Project Structure
```
app/                          # Android app
├── src/main/kotlin/herdr/dev/app/
│   ├── data/                 # Repositories, models, socket client
│   ├── di/                   # Hilt modules
│   ├── ui/                   # Screens, components, themes
│   ├── viewmodel/            # ViewModels per screen
│   ├── notifications/        # Notification manager
│   ├── services/             # Background monitoring service
│   └── MainActivity.kt
├── src/main/res/             # Drawables, strings, themes
└── build.gradle

web/                          # Web PWA (React + Vite)
├── src/App.tsx               # Main web app
├── public/                   # PWA assets, install scripts
└── vite.config.ts

bridge/                       # Python TCP↔Unix socket bridge
└── herdr-bridge.py

functions/                    # Cloudflare Pages edge function
└── [[path]].js
```

## How It Works
1. herdr runs on the user's workstation (Unix domain socket)
2. `bridge/herdr-bridge.py` exposes herdr over TCP (default port 8765)
3. The app (Android or Web) connects to the bridge and speaks NDJSON JSON-RPC
4. The bridge proxies requests to herdr and relays events back

## Naming
- **Product name**: Shep
- **Android app display name**: Shepard
- **Package**: `herdr.dev.app`
- **Install URL**: `https://shep.sh`
- **GitHub repo**: `github.com/dalnk/shep`

## Development
```bash
# Build Android
./gradlew assembleDebug

# Run bridge
python3 bridge/herdr-bridge.py

# Run web dev server
cd web && npm run dev
```