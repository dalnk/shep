#!/usr/bin/env bash
set -e

# Colors
BOLD='\033[1m'
DIM='\033[2m'
GREEN='\033[32m'
CYAN='\033[36m'
YELLOW='\033[33m'
RESET='\033[0m'

echo -e "${BOLD}${GREEN}Shep Companion Setup${RESET}"
echo -e "${DIM}Preparing local herdr bridge & companion relay...${RESET}\n"

# Check Python 3
if ! command -v python3 >/dev/null 2>&1; then
    echo -e "${YELLOW}Error: Python 3 is required but was not found in PATH.${RESET}"
    exit 1
fi

INSTALL_DIR="${HOME}/.shep"
mkdir -p "${INSTALL_DIR}"

echo -e "Fetching bridge script from GitHub..."
curl -fsSL "https://raw.githubusercontent.com/dalnk/shep/main/bridge/herdr-bridge.py" -o "${INSTALL_DIR}/herdr-bridge.py"
chmod +x "${INSTALL_DIR}/herdr-bridge.py"

# Detect herdr socket
HERDR_SOCKET="${HOME}/.config/herdr/herdr.sock"
if [ ! -S "${HERDR_SOCKET}" ]; then
    echo -e "${YELLOW}Note: herdr socket not currently detected at ${HERDR_SOCKET}${RESET}"
    echo -e "Start herdr first, or run herdr-bridge manually."
fi

# Determine LAN IP
LOCAL_IP="127.0.0.1"
if command -v ipconfig >/dev/null 2>&1; then
    LOCAL_IP=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo "127.0.0.1")
elif command -v hostname >/dev/null 2>&1; then
    LOCAL_IP=$(hostname -I 2>/dev/null | awk '{print $1}' || echo "127.0.0.1")
fi

echo -e "\n${BOLD}${GREEN}✓ Installation complete!${RESET}"
echo -e "Bridge installed to: ${CYAN}${INSTALL_DIR}/herdr-bridge.py${RESET}"
echo -e "Your local IP:      ${BOLD}${LOCAL_IP}${RESET}\n"
echo -e "To start the bridge listener, run:"
echo -e "  ${CYAN}python3 ${INSTALL_DIR}/herdr-bridge.py --port 8765${RESET}\n"
echo -e "Then in the Shep Android app, point Settings or scan your bridge QR code."
