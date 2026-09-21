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
BASE_DIR = "/home/plex/Dokumente/SNartists"

HEADERS = {'User-Agent': 'StationNorthMusicArchive/1.0 (contact@stationnorth.org)'}

# Key R&B / Neo-Soul / Soul artists to guarantee insertion
PRIORITY_ARTISTS = [
    "Ari Lennox", "Summer Walker", "Giveon", "Snoh Aalegra", "Brent Faiyaz", "Lucky Daye",
    "Cleo Sol", "Jazmine Sullivan", "Kiana Ledé", "SiR", "Tems", "Amber Mark", "Joyce Wrice",
    "Chlöe", "Kehlani", "Tinashe", "Mahalia", "Syd", "Pink Sweat$", "Muni Long", "Victoria Monét",
    "Jhené Aiko", "H.E.R.", "SZA", "Daniel Caesar", "Sabrina Claudio", "Raveena", "UMI",
    "Alex Isley", "Mac Ayres", "Durand Bernarr", "Joplin", "Leon Thomas III", "Nao",
    "Lianne La Havas", "Gallant", "Samm Henshaw", "Arin Ray", "Tone Stith", "Kiana Ledé",
    "Rini", "Breet", "Destin Conrad", "Maeta", "Sinead Harnett", "DVSN", "Majid Jordan",
    "Chiiild", "Jordan Rakei", "Tom Misch", "Khamari", "Elijah Blake", "Ro James", "BJ the Chicago Kid"
]

CATEGORIES = [
    "Category:Contemporary_R%26B_singers",
    "Category:American_contemporary_R%26B_singers",
    "Category:Neo_soul_singers",
    "Category:Alternative_R%26B_singers",
    "Category:American_soul_singers",
    "Category:British_R%26B_singers",
    "Category:Contemporary_R%26B_musical_groups",
    "Category:Soul_musical_groups",
    "Category:Quiet_storm_musicians",
    "Category:New_jack_swing_musicians",
    "Category:Grammy_Award_winners_in_R%26B_and_Soul",
    "Category:African-American_R%26B_singers",
    "Category:Rhythm_and_blues_singers",
    "Category:Soul_singers",
    "Category:Funk_musicians",
    "Category:American_R%26B_singer-songwriters"
]

def clean_id(name):
    clean = "".join(c if c.isalnum() or c in " -_" else "" for c in name)
    return clean.lower().replace(" ", "-").strip("-")

def get_display_name(name):
    return re.sub(r'\s*\([^)]*\)', '', name).strip() if name else ''

def safe_urlopen(url, retries=3):
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers=HEADERS)
            with urllib.request.urlopen(req, timeout=10) as resp:
                return json.loads(resp.read().decode('utf-8'))
        except Exception:
            time.sleep(0.3 * (attempt + 1))
    return None

def fetch_wikipedia_summary(artist_name):
    encoded = urllib.parse.quote(artist_name.replace(" ", "_"))
    url = f"https://en.wikipedia.org/api/rest_v1/page/summary/{encoded}"
    data = safe_urlopen(url)
    if not data or data.get('type') == 'disambiguation':
        return None
        
    title = data.get('title') or artist_name
    return {
        'id': clean_id(title),
        'name': get_display_name(title),
        'raw_title': title,
        'qid': data.get('wikibase_item'),
        'raw_image_url': data.get('thumbnail', {}).get('source') if data.get('thumbnail') else None,
        'bio': data.get('extract') or f"{get_display_name(title)} is an R&B and Soul artist."
    }

def fetch_deezer_tracks(album_id):
    url = f"https://api.deezer.com/album/{album_id}/tracks"
    data = safe_urlopen(url)
    if data and 'data' in data and len(data['data']) > 0:
        return [t.get('title') for t in data['data'] if t.get('title')]
    return []

def fetch_deezer_discography(artist_name):
    cleaned = get_display_name(artist_name)
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
            time.sleep(0.02)
        
    albums.sort(key=lambda x: x['year'] if x['year'].isdigit() else '9999')
    return albums

def download_and_save_image(artist_id, raw_url):
    if not raw_url:
        return None
        
    os.makedirs(IMAGES_DIR, exist_ok=True)
    ext = raw_url.split('.')[-1].split('?')[0].lower()
    if ext not in ['jpg', 'jpeg', 'png', 'webp', 'gif']:
        ext = 'jpg'
        
    filename = f"{artist_id}.{ext}"
    filepath = os.path.join(IMAGES_DIR, filename)
    
    if os.path.exists(filepath) and os.path.getsize(filepath) > 0:
        return f"images/{filename}"
        
    try:
        req = urllib.request.Request(raw_url, headers=HEADERS)
        with urllib.request.urlopen(req, timeout=8) as resp:
            with open(filepath, 'wb') as f:
                f.write(resp.read())
        return f"images/{filename}"
    except Exception:
        return None

def fetch_category_candidates():
    candidates = set(PRIORITY_ARTISTS)
    print("Fetching candidate artist titles from Wikipedia categories...")
    for cat in CATEGORIES:
        url = f"https://en.wikipedia.org/w/api.php?action=query&list=categorymembers&cmtitle={cat}&cmlimit=500&format=json"
        data = safe_urlopen(url)
        if data and 'query' in data:
            members = data['query'].get('categorymembers', [])
            for m in members:
                title = m.get('title', '')
                if title and not title.startswith('Category:') and not title.startswith('List of') and not title.startswith('Index of'):
                    candidates.add(title)
    print(f"Total candidate artist titles collected: {len(candidates)}")
    return list(candidates)

def process_new_artist(candidate_title):
    try:
        summary = fetch_wikipedia_summary(candidate_title)
        if not summary or not summary.get('id'):
            return None
            
        artist_id = summary['id']
        display_name = summary['name']
        
        # Fetch discography
        discog = fetch_deezer_discography(candidate_title)
        
        # Download image if available
        image_path = download_and_save_image(artist_id, summary.get('raw_image_url'))
        
        return {
            'id': artist_id,
            'name': display_name,
            'raw_title': summary.get('raw_title', candidate_title),
            'qid': summary.get('qid'),
            'bio': summary.get('bio', ''),
            'image': image_path or '',
            'discography': discog
        }
    except Exception as e:
        return None

def main():
    with open(LEXICON_FILE, 'r', encoding='utf-8') as f:
        existing_lexicon = json.load(f)

    existing_ids = set(a['id'] for a in existing_lexicon)
    existing_names = set(get_display_name(a['name']).lower() for a in existing_lexicon)

    print(f"Current lexicon contains {len(existing_lexicon)} artists.")

    candidates = fetch_category_candidates()
    
    # Filter out candidates already in lexicon
    to_fetch = []
    for c in candidates:
        clean_name = get_display_name(c).lower()
        cid = clean_id(c)
        if cid not in existing_ids and clean_name not in existing_names:
            to_fetch.append(c)

    print(f"New unique artist candidates to process: {len(to_fetch)}")

    added_artists = []
    start_time = time.time()

    with ThreadPoolExecutor(max_workers=5) as executor:
        futures = {executor.submit(process_new_artist, title): title for title in to_fetch}
        for i, future in enumerate(as_completed(futures), 1):
            result = future.result()
            if result and result['id'] not in existing_ids:
                existing_ids.add(result['id'])
                added_artists.append(result)
                print(f"[{i}/{len(to_fetch)}] ADDED: {result['name']} ({result['id']}) -> {len(result['discography'])} albums")
            else:
                if i % 25 == 0 or i == len(to_fetch):
                    print(f"[{i}/{len(to_fetch)}] Processed...")

    print(f"\nCompleted fetching! Added {len(added_artists)} new artists in {time.time() - start_time:.1f} seconds.")

    # Combine existing + new
    combined = existing_lexicon + added_artists
    
    # Deduplicate by ID
    dedup = {}
    for a in combined:
        dedup[a['id']] = a
    final_lexicon = list(dedup.values())
    
    # Sort alphabetically by display name
    final_lexicon.sort(key=lambda x: get_display_name(x['name']).lower())

    # Save lexicon.json
    with open(LEXICON_FILE, 'w', encoding='utf-8') as f:
        json.dump(final_lexicon, f, indent=2, ensure_ascii=False)
    print(f"Saved {len(final_lexicon)} artists to {LEXICON_FILE}.")

    # Save lexicon_data.js
    js_content = f"const LEXICON_DATA = {json.dumps(final_lexicon, ensure_ascii=False)};\n"
    with open(JS_DATA_FILE, 'w', encoding='utf-8') as f:
        f.write(js_content)
    print(f"Saved {JS_DATA_FILE}.")

if __name__ == "__main__":
    main()
