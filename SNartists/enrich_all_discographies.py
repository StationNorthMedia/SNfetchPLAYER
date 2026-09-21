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
HEADERS = {'User-Agent': 'StationNorthMusicArchive/1.0 (contact@stationnorth.org)'}

def clean_artist_name(name):
    # Remove parenthetical disambiguations like (musician), (singer), (band), (group), (rapper)
    return re.sub(r'\s*\([^)]*\)', '', name).strip()

def safe_urlopen(url, retries=5):
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers=HEADERS)
            with urllib.request.urlopen(req, timeout=10) as resp:
                return json.loads(resp.read().decode('utf-8'))
        except Exception as e:
            # Handle rate limiting (429) or network hiccups
            time.sleep(0.5 * (2 ** attempt))
    return None

def fetch_deezer_tracks(album_id):
    url = f"https://api.deezer.com/album/{album_id}/tracks"
    data = safe_urlopen(url)
    if data and 'data' in data and len(data['data']) > 0:
        return [t.get('title') for t in data['data'] if t.get('title')]
    return []

def fetch_deezer_discography(artist_name):
    cleaned = clean_artist_name(artist_name)
    if not cleaned:
        return []
        
    url = f"https://api.deezer.com/search/artist?q={urllib.parse.quote(cleaned)}"
    data = safe_urlopen(url)
    if not data or not data.get('data'):
        return []
        
    results = data.get('data', [])
    matches = [r for r in results if r.get('name', '').lower() == cleaned.lower()]
    best = max(matches, key=lambda x: x.get('nb_album', 0)) if matches else max(results, key=lambda x: x.get('nb_album', 0))
    
    if not best or best.get('nb_album', 0) == 0:
        return []

    artist_id = best['id']
    albums = []
    albums_url = f"https://api.deezer.com/artist/{artist_id}/albums?limit=25"
    adata = safe_urlopen(albums_url)
    if not adata or not adata.get('data'):
        return []
        
    album_list = adata.get('data', [])
    seen_titles = set()
    for alb in album_list:
        title = alb.get('title', '').strip()
        album_id = alb.get('id')
        year = alb.get('release_date', 'N/A')[:4]
        record_type = alb.get('record_type', 'album')
        
        if title and title.lower() not in seen_titles and 'tribute' not in title.lower() and 'karaoke' not in title.lower():
            seen_titles.add(title.lower())
            tracks = fetch_deezer_tracks(album_id)
            albums.append({
                'year': year if year else 'N/A',
                'title': title,
                'record_type': record_type,
                'tracks': tracks
            })
            time.sleep(0.05)
        
    albums.sort(key=lambda x: x['year'] if x['year'].isdigit() else '9999')
    return albums

def process_artist(artist):
    discog = artist.get('discography', [])
    if len(discog) > 0:
        return artist, False
        
    name = artist.get('name') or artist.get('raw_title') or ''
    new_discog = fetch_deezer_discography(name)
    if new_discog:
        artist['discography'] = new_discog
        return artist, True
        
    return artist, False

def main():
    if not os.path.exists(LEXICON_FILE):
        print(f"Error: {LEXICON_FILE} not found.")
        return

    with open(LEXICON_FILE, 'r', encoding='utf-8') as f:
        lexicon = json.load(f)

    target_artists = [a for a in lexicon if len(a.get('discography', [])) == 0]
    print(f"Starting rate-limited enrichment for {len(target_artists)} artists missing discography...")

    updated_count = 0
    start_time = time.time()

    # Use 4 workers to avoid Deezer rate limits
    with ThreadPoolExecutor(max_workers=4) as executor:
        futures = {executor.submit(process_artist, artist): artist for artist in target_artists}
        for i, future in enumerate(as_completed(futures), 1):
            artist, updated = future.result()
            if updated:
                updated_count += 1
                print(f"[{i}/{len(target_artists)}] ENRICHED: {artist['name']} ({artist['id']}) -> {len(artist['discography'])} albums")
            else:
                if i % 25 == 0 or i == len(target_artists):
                    print(f"[{i}/{len(target_artists)}] Processed... ({updated_count} enriched so far)")

    print(f"\nDone! Enriched {updated_count} artists in {time.time() - start_time:.1f} seconds.")

    # Save updated lexicon.json
    with open(LEXICON_FILE, 'w', encoding='utf-8') as f:
        json.dump(lexicon, f, indent=2, ensure_ascii=False)
    print(f"Saved updated {LEXICON_FILE}.")

    # Save lexicon_data.js for client-side search
    js_content = f"const LEXICON_DATA = {json.dumps(lexicon, ensure_ascii=False)};\n"
    with open(JS_DATA_FILE, 'w', encoding='utf-8') as f:
        f.write(js_content)
    print(f"Saved updated {JS_DATA_FILE}.")

if __name__ == "__main__":
    main()
