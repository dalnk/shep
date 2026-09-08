# Shepard (`shep`)

> Companion remote control and monitor for [herdr](https://herdr.dev) swarms on Android & e-paper devices.

Shepard connects directly to your workstation or compute swarm running `herdr` over LAN / Tailscale / tunnels. It provides a real-time, responsive interface for overseeing multi-agent swarms (Codex, Claude, Underclass, Grok, Antigravity, GitHub Copilot) with full foldable and adaptive tablet support.

---

## ✨ Features

- **Real-Time Swarm Oversight**: Live agent status (Working, Blocked, Done, Idle), execution tokens, and active reasoning step tracking.
- **Adaptive Layouts**:
  - **Single-pane phone mode**: Smooth transition with predictive back navigation.
  - **Two-pane tablet & foldable mode**: Collapsible master sidebar with quick-focus split view.
  - **Seamless Orientation Sync**: Keeps your active thread open across rotation between landscape and portrait.
- **Autonomous Velocity Modes (Danger Levels)**:
  - `0 Danger`: Strictly manual confirmation for all agent commands.
  - `Normal`: Standard interactive confirmation.
  - `Dangermaxxing`: High-velocity autonomous auto-proceed for blocked agents.
- **E-Paper & Daylight Optimized**:
  - Ultra-minimal integrated input bar with high-contrast inline send actions.
  - Physical/hardware keyboard support: immediate `Enter` to submit, `Shift+Enter` for multiline input.
  - Zero-latency warm rehydration cache across screen transitions and re-renders.
- **Fast-Pairing & Connectivity**:
  - mDNS / local LAN discovery (`neo.local`, static IP fallback).
  - Background monitoring foreground service with smart status notifications.

---

## 🏗️ Architecture & Tech Stack

- **Language & Runtime**: Kotlin 2.1+, Java 21 toolchain
- **UI Framework**: Jetpack Compose, Material 3, Compose Navigation
- **Architecture**: Clean MVVM with Hilt Dependency Injection & Kotlin Coroutines StateFlow
- **Networking**: Low-latency NDJSON Unix domain socket bridge (`bridge/herdr-bridge.py`) over TCP
- **Local State & Persistence**: Jetpack DataStore Preferences

---

## 🚀 Getting Started

### 1. One-Line Setup (Mac / Linux)

Run the quickstart installer directly:

```bash
curl -fsSL https://sheperd.sh | sh
```

Or start the bridge manually from source:

```bash
python3 bridge/herdr-bridge.py -v
```

By default, the bridge listens on port `8765` and proxies to `~/.config/herdr/herdr.sock`.

### 2. Build & Install Android App

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or run release build:

```bash
./gradlew assembleRelease
```

---

## 📜 License

MIT License. See [LICENSE](LICENSE) for details.
