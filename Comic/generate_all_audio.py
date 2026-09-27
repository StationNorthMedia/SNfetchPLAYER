import os
import re
import time
import requests

SCRIPT_PATH = "/home/plex/Dokumente/SNfetchPLAYER21.25/Comic/chronicles_script.txt"
AUDIO_DIR = "/home/plex/Dokumente/SNfetchPLAYER21.25/Comic/audio"
ENV_PATH = "/home/plex/Dokumente/SNfetchPLAYER21.25/Comic/.env"

# Load credentials from .env
env_vars = {}
with open(ENV_PATH, "r", encoding="utf-8") as f:
    for line in f:
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            env_vars[k.strip()] = v.strip()

api_key = env_vars.get("ELEVENLABS_API_KEY")
voice_id = env_vars.get("ELEVENLABS_VOICE_ID")

if not api_key or not voice_id:
    print("ERROR: Missing ELEVENLABS_API_KEY or ELEVENLABS_VOICE_ID in .env")
    exit(1)

os.makedirs(AUDIO_DIR, exist_ok=True)

# Parse paragraphs from chronicles_script.txt
with open(SCRIPT_PATH, "r", encoding="utf-8") as f:
    text = f.read()

sections = re.split(r'\n(?=(?:Chapter|CHAPTER)\s+\d+)', text)

paras = []

for sec in sections:
    sec = sec.strip()
    if not sec:
        continue
    lines = [l.strip() for l in sec.split('\n') if l.strip()]
    if lines[0].startswith('THE MANJARO LOUNGE'):
        continue
    
    for line in lines[1:]:
        if line.startswith('=======') or (line.startswith('[IMAGE:') and line.endswith(']')):
            continue
        paras.append(line)

total_tracks = len(paras)
print(f"Loaded {total_tracks} paragraph blocks from {SCRIPT_PATH}")

url = f"https://api.elevenlabs.io/v1/text-to-speech/{voice_id}"
headers = {
    "Accept": "audio/mpeg",
    "Content-Type": "application/json",
    "xi-api-key": api_key
}

success_count = 0
skipped_count = 0

for idx, p_text in enumerate(paras, 1):
    file_name = f"p_{idx:03d}.mp3"
    file_path = os.path.join(AUDIO_DIR, file_name)
    
    # Check if already generated and non-empty (> 1000 bytes)
    if os.path.exists(file_path) and os.path.getsize(file_path) > 1000:
        print(f"[{idx}/{total_tracks}] {file_name} already exists. Skipping.")
        skipped_count += 1
        continue
    
    print(f"[{idx}/{total_tracks}] Generating {file_name} ({len(p_text)} chars)...", end="", flush=True)
    
    payload = {
        "text": p_text,
        "model_id": "eleven_multilingual_v2",
        "voice_settings": {
            "stability": 0.5,
            "similarity_boost": 0.75
        }
    }
    
    resp = requests.post(url, json=payload, headers=headers)
    
    if resp.status_code == 200:
        with open(file_path, "wb") as f:
            f.write(resp.content)
        print(f" OK! ({len(resp.content)} bytes)")
        success_count += 1
    else:
        print(f" ERROR {resp.status_code}: {resp.text}")
        # Sleep briefly if rate limited
        if resp.status_code == 429:
            print("Rate limit hit, waiting 5 seconds...")
            time.sleep(5)

print(f"\nCompleted! Generated: {success_count} | Skipped: {skipped_count} | Total: {total_tracks}")
