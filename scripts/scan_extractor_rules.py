#!/usr/bin/env python3
import urllib.request
import json
import ssl
import sys
import datetime

TEST_YOUTUBE_ID = "GxBSyx85Kp8"
OUTPUT_FILE = "remote_assets/extractor_rules.json"

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

def fetch_json(url, timeout=8):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"})
    with urllib.request.urlopen(req, timeout=timeout, context=ctx) as resp:
        return json.loads(resp.read().decode("utf-8"))

def test_piped_instance(baseUrl):
    clean_url = baseUrl.rstrip("/")
    target_url = f"{clean_url}/streams/{TEST_YOUTUBE_ID}"
    req = urllib.request.Request(target_url, headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"})
    try:
        with urllib.request.urlopen(req, timeout=8, context=ctx) as resp:
            if resp.getcode() != 200:
                return False
            data = json.loads(resp.read().decode("utf-8"))
            hls = data.get("hls", "").strip()
            audio = data.get("audioStreams", [])
            video = data.get("videoStreams", [])
            return bool(hls or audio or video)
    except Exception as e:
        print(f"Piped candidate [{clean_url}] failed: {e}")
        return False

def test_invidious_instance(baseUrl):
    clean_url = baseUrl.rstrip("/")
    target_url = f"{clean_url}/api/v1/videos/{TEST_YOUTUBE_ID}"
    req = urllib.request.Request(target_url, headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"})
    try:
        with urllib.request.urlopen(req, timeout=8, context=ctx) as resp:
            if resp.getcode() != 200:
                return False
            data = json.loads(resp.read().decode("utf-8"))
            fs = data.get("formatStreams", [])
            af = data.get("adaptiveFormats", [])
            return bool(fs or af)
    except Exception as e:
        print(f"Invidious candidate [{clean_url}] failed: {e}")
        return False

def main():
    print("=== STARTING OTA EXTRACTOR INSTANCE CRAWLER ===")
    
    invidious_candidates = set([
        "https://invidious.f5.si",
        "https://inv.nadeko.net",
        "https://invidious.nerdvpn.de",
        "https://yewtu.be"
    ])
    
    piped_candidates = set([
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.tokhmi.xyz",
        "https://pipedapi.drgns.space",
        "https://pipedapi.mha.fi"
    ])

    # Fetch from official Invidious API list
    try:
        data = fetch_json("https://api.invidious.io/instances.json")
        for item in data:
            if isinstance(item, list) and len(item) == 2:
                domain, info = item
                if info.get("api") and info.get("uri", "").startswith("https"):
                    invidious_candidates.add(info.get("uri").rstrip("/"))
    except Exception as e:
        print(f"Could not fetch official Invidious API list: {e}")

    print(f"Testing {len(invidious_candidates)} Invidious candidates...")
    working_invidious = []
    for c in list(invidious_candidates):
        if test_invidious_instance(c):
            print(f"✅ VERIFIED INVIDIOUS: {c}")
            working_invidious.append(c)

    print(f"Testing {len(piped_candidates)} Piped candidates...")
    working_piped = []
    for c in list(piped_candidates):
        if test_piped_instance(c):
            print(f"✅ VERIFIED PIPED: {c}")
            working_piped.append(c)

    print(f"\nFinal Crawl Summary: {len(working_piped)} Piped working, {len(working_invidious)} Invidious working.")

    # Read current rules file to preserve version and existing instances if crawl is empty
    version = "1.0.0.31"
    existing_piped = ["https://pipedapi.kavin.rocks", "https://pipedapi.tokhmi.xyz"]
    existing_invidious = ["https://invidious.f5.si"]
    try:
        with open(OUTPUT_FILE, "r") as f:
            existing = json.load(f)
            version = existing.get("version", "1.0.0.31")
            if existing.get("piped_instances"):
                existing_piped = existing.get("piped_instances")
            if existing.get("invidious_instances"):
                existing_invidious = existing.get("invidious_instances")
    except Exception:
        pass

    now_str = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    output_data = {
        "version": version,
        "updated_at": now_str,
        "piped_instances": working_piped if working_piped else existing_piped,
        "invidious_instances": working_invidious if working_invidious else existing_invidious
    }

    with open(OUTPUT_FILE, "w") as f:
        json.dump(output_data, f, indent=2)

    print(f"Successfully updated {OUTPUT_FILE}")

if __name__ == "__main__":
    main()
