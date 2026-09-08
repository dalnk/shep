# Cloudflare Tunnel Setup for Herdr Android App

This guide explains how to set up a Cloudflare tunnel to make your Herdr server accessible from your Android app anywhere.

## Prerequisites

- Cloudflare account (free tier works)
- Herdr server running locally on port 8765
- `cloudflared` installed on your server

## Installation

### Install cloudflared

**Linux (Ubuntu/Debian):**
```bash
wget https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb
sudo dpkg -i cloudflared-linux-amd64.deb
```

**macOS:**
```bash
brew install cloudflare/cloudflare/cloudflared
```

**Windows:**
Download from https://github.com/cloudflare/cloudflared/releases

## Quick Setup (One-Time Tunnel)

For quick testing without permanent setup, use Cloudflare's quick tunnel:

```bash
cloudflared tunnel --url http://localhost:8765
```

This will:
- Create a temporary tunnel
- Generate a random HTTPS URL (e.g., `https://random-name.trycloudflare.com`)
- Work immediately without DNS configuration
- **Stop when you close the terminal**

**Use the generated URL in your Android app settings:**
- Server Host: `random-name.trycloudflare.com`
- Server Port: `443`

**Limitations:**
- URL changes each time you restart
- No custom domain
- Not suitable for production

## Permanent Setup (Custom Domain)

### 1. Authenticate with Cloudflare

```bash
cloudflared tunnel login
```

This will open a browser window to authenticate with your Cloudflare account and select your domain.

### 2. Create a Tunnel

```bash
cloudflared tunnel create herdr-tunnel
```

Save the tunnel ID that's returned (e.g., `abc123-def456-ghi789`).

### 3. Configure the Tunnel

Create a configuration file at `~/.cloudflared/config.yml`:

```yaml
tunnel: <your-tunnel-id>
credentials-file: /home/<user>/.cloudflared/<tunnel-id>.json

ingress:
  - hostname: herdr.yourdomain.com
    service: http://localhost:8765
  - service: http_status:404
```

Replace:
- `<your-tunnel-id>` with the tunnel ID from step 2
- `herdr.yourdomain.com` with your desired subdomain
- `/home/<user>/.cloudflared/<tunnel-id>.json` with the actual path to credentials file

### 4. Route DNS to Tunnel

```bash
cloudflared tunnel route dns herdr-tunnel herdr.yourdomain.com
```

### 5. Start the Tunnel

**Test run (foreground):**
```bash
cloudflared tunnel run herdr-tunnel
```

**Production (systemd service):**
```bash
sudo cloudflared service install
sudo systemctl start cloudflared
sudo systemctl enable cloudflared
```

## Configure Android App

### Update Server Settings

1. Open the Herdr Android app
2. Navigate to Settings
3. Update Server Host to your Cloudflare tunnel domain:
   - `herdr.yourdomain.com`
4. Update Server Port to `443` (Cloudflare uses HTTPS by default)
5. The app will automatically reconnect

### Test Connection

1. Check the connection status indicator in the top bar
   - Green = Connected
   - Red = Disconnected
2. Navigate to Dashboard to see your workspaces/panes
3. Try sending a message to an agent

## Troubleshooting

### Connection Issues

**Check tunnel status:**
```bash
sudo systemctl status cloudflared
```

**View tunnel logs:**
```bash
sudo journalctl -u cloudflared -f
```

**Test tunnel locally:**
```bash
curl http://localhost:8765
```

**Test tunnel through Cloudflare:**
```bash
curl https://herdr.yourdomain.com
```

### Common Issues

1. **Port 8765 not accessible**
   - Ensure Herdr server is running
   - Check firewall rules: `sudo ufw allow 8765`

2. **DNS not propagating**
   - Wait a few minutes for DNS to propagate
   - Check Cloudflare DNS settings in dashboard

3. **SSL/TLS errors**
   - Cloudflare handles SSL automatically
   - Ensure you're using HTTPS in the app settings

## Security Considerations

1. **Authentication**: Your Herdr server should implement authentication
2. **Access Control**: Consider Cloudflare Access for additional security
3. **Rate Limiting**: Configure rate limiting in Cloudflare dashboard
4. **WAF**: Enable Web Application Firewall rules in Cloudflare

## Testing Checklist

- [ ] cloudflared installed
- [ ] Tunnel created and authenticated
- [ ] DNS route configured
- [ ] Tunnel running (check logs)
- [ ] Local server accessible on port 8765
- [ ] Tunnel accessible via HTTPS
- [ ] Android app configured with tunnel domain
- [ ] Connection status shows green
- [ ] Can view workspaces/panes
- [ ] Can send messages to agents
- [ ] Real-time updates working

## Alternative: ngrok (Quick Testing)

For quick testing without Cloudflare setup:

```bash
# Install ngrok
brew install ngrok  # macOS
# or download from https://ngrok.com

# Start tunnel
ngrok http 8765

# Use the HTTPS URL provided in Android app settings
```

Note: ngrok free tier changes URLs on restart and has limitations.
