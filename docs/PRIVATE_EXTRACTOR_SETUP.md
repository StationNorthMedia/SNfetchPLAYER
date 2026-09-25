# 🚀 Turn-Key Private `yt-dlp` Extractor API Setup

Welcome to the **Station North Private Extractor** deployment guide. This document explains how to set up your own private, 24/7 self-hosted `yt-dlp` extraction server using **Portainer / Docker Compose** and optionally expose it securely via **Cloudflare Zero Trust**.

---

## 🌟 Key Features

1. **Self-Hosted & Private**: Runs on your home network (NAS, Raspberry Pi, Home Assistant server, mini PC, or VPS).
2. **Datacenter Bypass**: Built-in Android / iOS / Android TV player client emulation to bypass YouTube IP blocking.
3. **Automated Watchdog**: An internal background cron job automatically updates `yt-dlp` every **12 hours** (`pip install --upgrade yt-dlp`), ensuring zero downtime when YouTube changes signature algorithms.
4. **Local Network & HTTPS Support**: Connect directly via local IP (`http://192.168.x.x:9003`) or via Cloudflare Zero Trust SSL domain (`https://your-custom-domain.com`).

---

## 📦 Option 1: Deploy via Portainer (Recommended)

1. Open your **Portainer Web UI** (`http://your-nas-ip:9000`).
2. Navigate to **Stacks** -> **Add stack**.
3. Name your stack: `sn-ytdlp-extractor`.
4. Copy and paste the contents of `docker/Portainer-Stack.yml` into the web editor:

```yaml
version: '3.8'

services:
  ytdlp-api:
    image: python:3.11-slim
    container_name: sn-ytdlp-api
    restart: always
    ports:
      - "9003:9003"
    environment:
      - PORT=9003
      - PYTHONUNBUFFERED=1
    command: >
      sh -c "
        apt-get update && apt-get install -y ffmpeg curl cron &&
        pip install --no-cache-dir fastapi uvicorn yt-dlp requests &&
        mkdir -p /app &&
        echo 'import yt_dlp\nfrom fastapi import FastAPI, Request\nfrom fastapi.responses import StreamingResponse\nfrom fastapi.middleware.cors import CORSMiddleware\nimport requests, json, os\n\napp = FastAPI()\napp.add_middleware(CORSMiddleware, allow_origins=[\"*\"], allow_methods=[\"GET\"], allow_headers=[\"*\"])\n\n@app.get(\"/api/health\")\ndef health():\n    return {\"status\": \"ok\", \"engine\": \"yt-dlp\"}\n\n@app.get(\"/api/extract\")\ndef extract(video_id: str = \"\", is_audio: bool = False):\n    if not video_id:\n        return {\"status\": \"error\", \"message\": \"video_id is required\"}\n    url = f\"https://www.youtube.com/watch?v={video_id}\"\n    ydl_opts = {\n        \"quiet\": True,\n        \"no_warnings\": True,\n        \"format\": \"bestaudio/best\" if is_audio else \"bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best\",\n        \"extractor_args\": {\"youtube\": {\"player_client\": [\"android\", \"android_tv\", \"ios\", \"tv\"]}}\n    }\n    try:\n        with yt_dlp.YoutubeDL(ydl_opts) as ydl:\n            info = ydl.extract_info(url, download=False)\n            stream_url = info.get(\"url\")\n            title = info.get(\"title\", \"\")\n            return {\"status\": \"success\", \"url\": stream_url, \"title\": title}\n    except Exception as e:\n        return {\"status\": \"error\", \"message\": str(e)}\n\nif __name__ == \"__main__\":\n    import uvicorn\n    uvicorn.run(app, host=\"0.0.0.0\", port=9003)\n' > /app/main.py &&
        echo '0 */12 * * * pip install --upgrade --no-cache-dir yt-dlp >> /var/log/ytdlp-update.log 2>&1' > /etc/cron.d/ytdlp-cron &&
        chmod 0644 /etc/cron.d/ytdlp-cron &&
        crontab /etc/cron.d/ytdlp-cron &&
        cron &&
        python /app/main.py
      "
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:9003/api/health || exit 1"]
      interval: 30s
      timeout: 5s
      retries: 3
```

5. Click **Deploy the stack**.

---

## 🌐 Option 2: Cloudflare Zero Trust Tunnel (Optional HTTPS Access)

If you want to access your extractor outside your home Wi-Fi without opening router ports:

1. Go to [Cloudflare Zero Trust Dashboard](https://one.dash.cloudflare.com/).
2. Navigate to **Networks** -> **Tunnels** -> **Create a Tunnel**.
3. Name your tunnel (e.g. `sn-ytdlp-tunnel`) and choose **Docker**.
4. Copy the generated **Tunnel Token**.
5. In your Portainer stack or environment file, add:
   ```yaml
   CLOUDFLARE_TUNNEL_TOKEN: "eyJhbGciOi..."
   ```
6. Route your domain (e.g. `https://yt.yourdomain.com`) to `http://sn-ytdlp-api:9003`.

---

## 📱 Option 3: Connecting SNfetchPLAYER App

1. Open **SNfetchPLAYER**.
2. Go to **Settings Hub (Module 8)**.
3. Scroll down to **yt-dlp API (Private Extractor / Portainer Stack)**.
4. Check the box **[x] Enable yt-dlp API**.
5. Enter your server URL:
   - For local network: `http://192.168.1.100:9003` (replace with your server IP).
   - For Cloudflare domain: `https://yt.yourdomain.com`
6. Tap **[ Test Connection ]**.
7. If successful, you will see `🟢 Connected (120ms)` and your private server will handle stream extractions!
