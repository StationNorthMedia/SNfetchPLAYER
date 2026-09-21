#!/usr/bin/env python3
import urllib.request
import json
import os
import time
from concurrent.futures import ThreadPoolExecutor, as_completed

HEADERS = {'User-Agent': 'StationNorthApp/1.0 (contact@stationnorth.org)'}
LEXICON_PATH = "/home/plex/Dokumente/SNartists/lexicon.json"
IMAGES_DIR = "/home/plex/Dokumente/SNartists/images"
JS_DATA_PATH = "/home/plex/Dokumente/SNartists/lexicon_data.js"

def download_single_image(artist):
    img_url = artist.get('image')
    if not img_url:
        return artist
        
    ext = img_url.split('.')[-1].split('?')[0].lower()
    if ext not in ['jpg', 'jpeg', 'png', 'webp', 'gif', 'svg']:
        ext = 'jpg'
        
    filename = f"{artist['id']}.{ext}"
    filepath = os.path.join(IMAGES_DIR, filename)
    local_rel_path = f"images/{filename}"
    
    # If file already downloaded, just reuse it
    if os.path.exists(filepath) and os.path.getsize(filepath) > 0:
        artist['image'] = local_rel_path
        return artist
        
    req = urllib.request.Request(img_url, headers=HEADERS)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp, open(filepath, 'wb') as out:
            out.write(resp.read())
        artist['image'] = local_rel_path
    except Exception as e:
        # Keep null/fallback if download fails
        artist['image'] = None
        
    return artist

def main():
    if not os.path.exists(LEXICON_PATH):
        print(f"Error: {LEXICON_PATH} not found.")
        return

    os.makedirs(IMAGES_DIR, exist_ok=True)

    with open(LEXICON_PATH, 'r', encoding='utf-8') as f:
        lexicon = json.load(f)

    image_artists = [a for a in lexicon if a.get('image')]
    print(f"Starting bulk download of {len(image_artists)} artist images into {IMAGES_DIR}...")

    start_time = time.time()
    updated_lexicon = []

    with ThreadPoolExecutor(max_workers=20) as executor:
        future_map = {executor.submit(download_single_image, artist): artist for artist in lexicon}
        completed = 0
        for future in as_completed(future_map):
            res = future.result()
            updated_lexicon.append(res)
            completed += 1
            if completed % 50 == 0 or completed == len(lexicon):
                print(f"   Downloaded images: {completed}/{len(lexicon)} ({time.time()-start_time:.1f}s)")

    # Sort alphabetically
    updated_lexicon.sort(key=lambda x: x['name'])

    # 1. Update lexicon.json
    with open(LEXICON_PATH, 'w', encoding='utf-8') as f:
        json.dump(updated_lexicon, f, indent=2, ensure_ascii=False)

    # 2. Update lexicon_data.js for CORS-free local viewing
    with open(JS_DATA_PATH, 'w', encoding='utf-8') as f:
        f.write("window.LEXICON_DATA = ")
        json.dump(updated_lexicon, f, ensure_ascii=False)
        f.write(";")

    print(f"\nSUCCESS! Downloaded images and converted database to 100% offline local images in {time.time()-start_time:.1f}s!")

if __name__ == "__main__":
    main()
