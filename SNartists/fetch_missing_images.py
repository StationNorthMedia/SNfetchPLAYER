#!/usr/bin/env python3
import os
import json
import urllib.request
import urllib.parse
import re
import time

def slugify(text):
    text = text.lower()
    text = re.sub(r'[^a-z0-9]+', '-', text)
    return text.strip('-')

def clean_display_name(name):
    # Remove parenthetical details e.g. "Usher (musician)" -> "Usher"
    return re.sub(r'\s*\([^)]*\)', '', name).strip()

def fetch_deezer_image(artist_name):
    clean_name = clean_display_name(artist_name)
    url = f"https://api.deezer.com/search/artist?q={urllib.parse.quote(clean_name)}"
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64)'})
        with urllib.request.urlopen(req, timeout=8) as resp:
            data = json.loads(resp.read().decode('utf-8'))
            if data.get('data') and len(data['data']) > 0:
                for match in data['data']:
                    matched_name = match.get('name', '')
                    # Check if names match reasonably well
                    if clean_name.lower() in matched_name.lower() or matched_name.lower() in clean_name.lower():
                        img_url = match.get('picture_big') or match.get('picture_medium') or match.get('picture')
                        if img_url and 'default' not in img_url and 'artist//' not in img_url:
                            return img_url
    except Exception as e:
        pass
    return None

def fetch_itunes_image(artist_name):
    clean_name = clean_display_name(artist_name)
    url = f"https://itunes.apple.com/search?term={urllib.parse.quote(clean_name)}&entity=musicArtist&limit=3"
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64)'})
        with urllib.request.urlopen(req, timeout=8) as resp:
            data = json.loads(resp.read().decode('utf-8'))
            if data.get('results'):
                for match in data['results']:
                    matched_name = match.get('artistName', '')
                    if clean_name.lower() in matched_name.lower() or matched_name.lower() in clean_name.lower():
                        artist_id = match.get('artistId')
                        album_url = f"https://itunes.apple.com/lookup?id={artist_id}&entity=album&limit=1"
                        req_alb = urllib.request.Request(album_url, headers={'User-Agent': 'Mozilla/5.0'})
                        with urllib.request.urlopen(req_alb, timeout=8) as resp_alb:
                            alb_data = json.loads(resp_alb.read().decode('utf-8'))
                            for res in alb_data.get('results', []):
                                if res.get('artworkUrl100'):
                                    return res['artworkUrl100'].replace('100x100bb', '600x600bb')
    except Exception as e:
        pass
    return None

def download_image(img_url, dest_path):
    try:
        req = urllib.request.Request(img_url, headers={'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64)'})
        with urllib.request.urlopen(req, timeout=12) as resp:
            content = resp.read()
            if len(content) > 2000:
                with open(dest_path, 'wb') as f:
                    f.write(content)
                return True
    except Exception as e:
        pass
    return False

def main():
    os.makedirs('images', exist_ok=True)
    lexicon_path = 'lexicon.json'
    with open(lexicon_path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    total = len(data)
    missing = [a for a in data if not a.get('image') or not os.path.exists(a['image'].replace('../', ''))]
    print(f"Total artists: {total}. Missing images: {len(missing)}")

    success_count = 0
    fail_count = 0

    for idx, artist in enumerate(missing, 1):
        name = artist['name']
        slug = slugify(name)
        if not slug:
            slug = f"artist-{idx}"
        
        target_filename = f"images/{slug}.jpg"
        
        # 1. Try Deezer
        img_url = fetch_deezer_image(name)
        
        # 2. Try iTunes if Deezer failed
        if not img_url:
            img_url = fetch_itunes_image(name)

        if img_url:
            ok = download_image(img_url, target_filename)
            if ok:
                artist['image'] = target_filename
                success_count += 1
                print(f"[{idx}/{len(missing)}] SUCCESS: {name} -> {target_filename}")
            else:
                fail_count += 1
                print(f"[{idx}/{len(missing)}] FAILED DL: {name} ({img_url})")
        else:
            fail_count += 1
            print(f"[{idx}/{len(missing)}] NO MATCH: {name}")

        time.sleep(0.1)

        if idx % 50 == 0 or idx == len(missing):
            with open(lexicon_path, 'w', encoding='utf-8') as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
            print(f"--- Saved progress to lexicon.json ({success_count} downloaded so far) ---")

    print(f"\nCompleted fetching! Downloaded: {success_count}, Failed/No match: {fail_count}")

    with open(lexicon_path, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

    with open('lexicon_data.js', 'w', encoding='utf-8') as f:
        f.write('window.LEXICON_DATA = ' + json.dumps(data, ensure_ascii=False, indent=2) + ';')

    print("Updated lexicon.json and lexicon_data.js successfully.")

if __name__ == '__main__':
    main()
