#!/usr/bin/env python3
import json
import os
import re
import time
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed

LEXICON_FILE = "/home/plex/Dokumente/SNartists/lexicon.json"
JS_DATA_FILE = "/home/plex/Dokumente/SNartists/lexicon_data.js"
IMAGES_DIR = "/home/plex/Dokumente/SNartists/images"
HEADERS = {'User-Agent': 'StationNorthMusicArchive/1.0 (contact@stationnorth.org)'}

def clean_artist_name(name):
    return re.sub(r'\s*\([^)]*\)', '', name).strip()

def safe_urlopen(url, retries=3):
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers=HEADERS)
            with urllib.request.urlopen(req, timeout=8) as resp:
                return json.loads(resp.read().decode('utf-8'))
        except Exception:
            time.sleep(0.3 * (attempt + 1))
    return None

def download_image(artist_id, image_url):
    if not image_url:
        return None
    os.makedirs(IMAGES_DIR, exist_ok=True)
    filename = f"{artist_id}.jpg"
    filepath = os.path.join(IMAGES_DIR, filename)
    
    if os.path.exists(filepath) and os.path.getsize(filepath) > 0:
        return f"images/{filename}"
        
    try:
        req = urllib.request.Request(image_url, headers=HEADERS)
        with urllib.request.urlopen(req, timeout=10) as resp:
            with open(filepath, 'wb') as f:
                f.write(resp.read())
        return f"images/{filename}"
    except Exception:
        return None

def fetch_deezer_info(artist_name):
    cleaned = clean_artist_name(artist_name)
    if not cleaned:
        return None, None, []
        
    url = f"https://api.deezer.com/search/artist?q={urllib.parse.quote(cleaned)}"
    data = safe_urlopen(url)
    if not data or not data.get('data'):
        return None, None, []
        
    results = data.get('data', [])
    matches = [r for r in results if r.get('name', '').lower() == cleaned.lower()]
    best = max(matches, key=lambda x: x.get('nb_album', 0)) if matches else max(results, key=lambda x: x.get('nb_album', 0))
    
    if not best:
        return None, None, []

    deezer_id = best.get('id')
    picture_url = best.get('picture_big') or best.get('picture_medium')
    
    related = []
    if deezer_id:
        rel_url = f"https://api.deezer.com/artist/{deezer_id}/related"
        rdata = safe_urlopen(rel_url)
        if rdata and rdata.get('data'):
            for r in rdata['data'][:6]:
                rname = r.get('name')
                if rname:
                    related.append(rname)
                    
    return deezer_id, picture_url, related

def process_artist(artist, name_to_id_map):
    artist_id = artist['id']
    artist_name = artist['name']
    has_image = bool(artist.get('image') and os.path.exists(os.path.join("/home/plex/Dokumente/SNartists", artist['image'])))
    has_related = bool(artist.get('related_artists') and len(artist['related_artists']) > 0)
    
    if has_image and has_related:
        return artist, False
        
    deezer_id, picture_url, related_names = fetch_deezer_info(artist_name)
    
    updated = False
    
    # Fill missing image
    if not has_image and picture_url:
        saved_img = download_image(artist_id, picture_url)
        if saved_img:
            artist['image'] = saved_img
            updated = True
            
    # Fill related artists
    if not has_related and related_names:
        matched_related = []
        for rname in related_names:
            rname_clean = clean_artist_name(rname).lower()
            if rname_clean in name_to_id_map and name_to_id_map[rname_clean] != artist_id:
                rel_id = name_to_id_map[rname_clean]
                matched_related.append({
                    'id': rel_id,
                    'name': clean_artist_name(rname)
                })
        if matched_related:
            artist['related_artists'] = matched_related
            updated = True

    return artist, updated

def main():
    if not os.path.exists(LEXICON_FILE):
        print(f"Error: {LEXICON_FILE} not found.")
        return

    with open(LEXICON_FILE, 'r', encoding='utf-8') as f:
        lexicon = json.load(f)

    print(f"Loaded {len(lexicon)} artists from {LEXICON_FILE}.")
    
    # Build name-to-id map for fast related matching
    name_to_id_map = {}
    for a in lexicon:
        clean_n = clean_artist_name(a['name']).lower()
        name_to_id_map[clean_n] = a['id']

    # Filter artists needing image or related artists
    to_process = []
    for a in lexicon:
        has_img = bool(a.get('image') and os.path.exists(os.path.join("/home/plex/Dokumente/SNartists", a['image'])))
        has_rel = bool(a.get('related_artists') and len(a['related_artists']) > 0)
        if not has_img or not has_rel:
            to_process.append(a)

    print(f"Artists needing image or related enrichment: {len(to_process)}")

    updated_count = 0
    start_time = time.time()

    with ThreadPoolExecutor(max_workers=6) as executor:
        futures = {executor.submit(process_artist, artist, name_to_id_map): artist for artist in to_process}
        for i, future in enumerate(as_completed(futures), 1):
            artist, updated = future.result()
            if updated:
                updated_count += 1
                img_str = "IMG✓" if artist.get('image') else "IMG✗"
                rel_str = f"REL({len(artist.get('related_artists', []))})✓" if artist.get('related_artists') else "REL✗"
                print(f"[{i}/{len(to_process)}] ENRICHED: {artist['name']} ({img_str}, {rel_str})")
            else:
                if i % 50 == 0 or i == len(to_process):
                    print(f"[{i}/{len(to_process)}] Processed...")

    print(f"\nCompleted! Enriched {updated_count} artists in {time.time() - start_time:.1f} seconds.")

    # Save lexicon.json
    with open(LEXICON_FILE, 'w', encoding='utf-8') as f:
        json.dump(lexicon, f, indent=2, ensure_ascii=False)
    print(f"Saved {LEXICON_FILE}.")

    # Save lexicon_data.js
    js_content = f"const LEXICON_DATA = {json.dumps(lexicon, ensure_ascii=False)};\n"
    with open(JS_DATA_FILE, 'w', encoding='utf-8') as f:
        f.write(js_content)
    print(f"Saved {JS_DATA_FILE}.")

if __name__ == "__main__":
    main()
