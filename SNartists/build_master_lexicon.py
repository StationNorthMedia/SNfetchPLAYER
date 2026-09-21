#!/usr/bin/env python3
import urllib.request
import urllib.parse
import json
import time
import os
import sys
import re
from concurrent.futures import ThreadPoolExecutor, as_completed

HEADERS = {'User-Agent': 'StationNorthMusicArchive/1.0 (contact@stationnorth.org)'}

SEED_FILE = "/home/plex/Dokumente/SNartists/artist_seed_list.json"
LEXICON_FILE = "/home/plex/Dokumente/SNartists/lexicon.json"
JS_DATA_FILE = "/home/plex/Dokumente/SNartists/lexicon_data.js"
IMAGES_DIR = "/home/plex/Dokumente/SNartists/images"
ARTISTS_DIR = "/home/plex/Dokumente/SNartists/artists"
SITEMAP_FILE = "/home/plex/Dokumente/SNartists/sitemap.xml"
BASE_URL = "https://stationnorth.local"

TOP_LEGENDS = [
    "Mariah Carey", "Erykah Badu", "D'Angelo", "Alicia Keys", "Sade", "Usher",
    "Aretha Franklin", "Whitney Houston", "Anita Baker", "Aaliyah", "Stevie Wonder",
    "Marvin Gaye", "Lauryn Hill", "Frank Ocean", "Maxwell", "Jill Scott", "Solange",
    "SZA", "Victoria Monét", "H.E.R.", "Daniel Caesar", "Jhené Aiko", "Mary J. Blige",
    "Toni Braxton", "Janet Jackson", "Luther Vandross", "Boyz II Men", "TLC", "Destiny's Child"
]

def clean_id(name):
    clean = "".join(c if c.isalnum() or c in " -_" else "" for c in name)
    return clean.lower().replace(" ", "-").strip("-")

def get_display_name(name):
    return re.sub(r'\s*\([^)]*\)', '', name).strip() if name else ''

def fetch_wikipedia_summary(artist_name):
    encoded = urllib.parse.quote(artist_name.replace(" ", "_"))
    url = f"https://en.wikipedia.org/api/rest_v1/page/summary/{encoded}"
    req = urllib.request.Request(url, headers=HEADERS)
    
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read().decode('utf-8'))
            if data.get('type') == 'disambiguation':
                return None
                
            title = data.get('title') or artist_name
            return {
                'id': clean_id(title),
                'name': get_display_name(title),
                'raw_title': title,
                'qid': data.get('wikibase_item'),
                'raw_image_url': data.get('thumbnail', {}).get('source') if data.get('thumbnail') else None,
                'bio': data.get('extract')
            }
    except Exception:
        return None

def fetch_deezer_discography_and_tracks(artist_name):
    url = f"https://api.deezer.com/search/artist?q={urllib.parse.quote(artist_name)}"
    req = urllib.request.Request(url, headers=HEADERS)
    
    artist_id = None
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read().decode('utf-8'))
            artists = data.get('data', [])
            if artists:
                artist_id = artists[0]['id']
    except Exception:
        pass
        
    if not artist_id:
        return []

    albums = []
    albums_url = f"https://api.deezer.com/artist/{artist_id}/albums?limit=25"
    req_alb = urllib.request.Request(albums_url, headers=HEADERS)
    
    try:
        with urllib.request.urlopen(req_alb, timeout=10) as resp:
            adata = json.loads(resp.read().decode('utf-8'))
            album_list = adata.get('data', [])
            
            seen_titles = set()
            for alb in album_list:
                title = alb.get('title', '').strip()
                album_id = alb.get('id')
                year = alb.get('release_date', 'N/A')[:4]
                record_type = alb.get('record_type', 'album')
                
                if title and title.lower() not in seen_titles and 'tribute' not in title.lower() and 'karaoke' not in title.lower():
                    seen_titles.add(title.lower())
                    tracks = fetch_deezer_tracks_retry(album_id)
                    albums.append({
                        'year': year if year else 'N/A',
                        'title': title,
                        'record_type': record_type,
                        'tracks': tracks
                    })
                    time.sleep(0.05)
    except Exception:
        pass
        
    albums.sort(key=lambda x: x['year'] if x['year'].isdigit() else '9999')
    return albums

def fetch_deezer_tracks_retry(album_id):
    url = f"https://api.deezer.com/album/{album_id}/tracks"
    for attempt in range(3):
        try:
            req = urllib.request.Request(url, headers=HEADERS)
            with urllib.request.urlopen(req, timeout=10) as resp:
                data = json.loads(resp.read().decode('utf-8'))
                if 'data' in data and len(data['data']) > 0:
                    return [t.get('title') for t in data['data'] if t.get('title')]
        except Exception:
            time.sleep(0.3 * (attempt + 1))
    return []

def download_and_save_image(artist_id, raw_url):
    if not raw_url:
        return None
        
    os.makedirs(IMAGES_DIR, exist_ok=True)
    ext = raw_url.split('.')[-1].split('?')[0].lower()
    if ext not in ['jpg', 'jpeg', 'png', 'webp', 'gif']:
        ext = 'jpg'
        
    filename = f"{artist_id}.{ext}"
    filepath = os.path.join(IMAGES_DIR, filename)
    local_path = f"images/{filename}"
    
    if os.path.exists(filepath) and os.path.getsize(filepath) > 0:
        return local_path
        
    req = urllib.request.Request(raw_url, headers=HEADERS)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp, open(filepath, 'wb') as out:
            out.write(resp.read())
        return local_path
    except Exception:
        return None

def process_single_artist(artist_name):
    summary = fetch_wikipedia_summary(artist_name)
    if not summary or not summary.get('bio'):
        return None
        
    artist_id = summary['id']
    raw_img = summary.pop('raw_image_url', None)
    local_img = download_and_save_image(artist_id, raw_img)
    
    summary['image'] = local_img
    summary['discography'] = fetch_deezer_discography_and_tracks(summary['name'])
    return summary

def generate_json_ld(artist):
    albums_ld = []
    for alb in artist.get('discography', []):
        tracks_ld = [{"@type": "MusicRecording", "name": t} for t in alb.get('tracks', [])]
        albums_ld.append({
            "@type": "MusicAlbum",
            "name": alb['title'],
            "datePublished": alb.get('year'),
            "track": tracks_ld
        })
        
    ld_data = {
        "@context": "https://schema.org",
        "@type": "MusicGroup",
        "name": artist['name'],
        "description": artist.get('bio', ''),
        "image": f"{BASE_URL}/{artist.get('image', 'logo.png')}",
        "album": albums_ld
    }
    return json.dumps(ld_data, indent=2, ensure_ascii=False)

def generate_artist_html(artist):
    artist_name = artist['name']
    bio = artist.get('bio', 'No biography available.')
    image_src = f"../{artist['image']}" if artist.get('image') else "../logo.png"
    
    albums_html = ""
    for alb in artist.get('discography', []):
        tracks = alb.get('tracks', [])
        tracks_html = "".join([f"<li>{t}</li>" for t in tracks])
        
        albums_html += f"""
        <details class="album-details-card" open>
          <summary class="album-summary-header">
            <span class="album-year">{alb.get('year', 'N/A')}</span>
            <span class="album-name">{alb['title']}</span>
            <span class="album-track-badge">🎵 {len(tracks)} Tracks</span>
          </summary>
          {f'<ol class="album-tracklist">{tracks_html}</ol>' if tracks else '<p class="no-tracks">No tracks registered.</p>'}
        </details>
        """
        
    json_ld = generate_json_ld(artist)

    return f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>{artist_name} - Discography, Bio & Tracklists | Station North Lexicon</title>
<meta name="description" content="{bio[:150].replace('\"', '&quot;')}...">
<link rel="stylesheet" href="../style.css">

<!-- Google Rich Snippet Structured Data (SEO) -->
<script type="application/ld+json">
{json_ld}
</script>
</head>
<body>

  <!-- LAYER 1 & 2: Fixed kinetic background visual center -->
  <div class="fixed-visuals">
    <div class="ring"></div><div class="ring"></div><div class="ring"></div>
    <div class="ring"></div><div class="ring"></div><div class="ring"></div>
    <div class="shockwave"></div><div class="shockwave"></div><div class="shockwave"></div>
    <div class="center-logo-container">
      <img src="../logo.png" alt="Station North Logo" class="custom-logo">
    </div>
  </div>

  <!-- LAYER 3: Scrollable Content -->
  <div class="scroll-layer">
    <header>
      <div class="header-brand">
        <a href="../index.html" style="text-decoration:none;display:flex;align-items:center;gap:12px;color:inherit;">
          <img src="../logo.png" alt="Logo" class="header-logo">
          <h1>STATION NORTH</h1>
        </a>
      </div>
      <a href="../index.html" class="header-badge" style="text-decoration:none;">← Back to Main Search</a>
    </header>

    <main>
      <div class="artist-detail-card" style="margin-top:20px;">
        <div class="artist-detail-header">
          <img src="{image_src}" alt="{artist_name}" class="artist-detail-image" onerror="this.onerror=null; this.src='../logo.png';">
          <div class="artist-detail-title-group">
            <div class="artist-tags">
              <span class="artist-tag">R&B / Soul</span>
              <span class="artist-tag">SEO Verified Entity</span>
            </div>
            <h2>{artist_name}</h2>
          </div>
        </div>
        
        <div class="artist-bio">
          {bio}
        </div>

        <div class="discography-section">
          <div class="discography-title">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"></circle>
              <circle cx="12" cy="12" r="3"></circle>
            </svg>
            Complete Discography & Tracklists ({len(artist.get('discography', []))})
          </div>
          <div class="discography-grid">
            {albums_html if albums_html else '<p class="no-discography">No studio album records registered.</p>'}
          </div>
        </div>
      </div>
    </main>

    <footer>
      © 2026 Station North Media Project | SEO Pre-rendered Lexicon Engine
    </footer>
  </div>

</body>
</html>
"""

def generate_sitemap(lexicon):
    urls_xml = ""
    for a in lexicon:
        urls_xml += f"""  <url>
    <loc>{BASE_URL}/artists/{a['id']}.html</loc>
    <changefreq>weekly</changefreq>
    <priority>0.8</priority>
  </url>\n"""
  
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
  <url>
    <loc>{BASE_URL}/index.html</loc>
    <changefreq>daily</changefreq>
    <priority>1.0</priority>
  </url>
{urls_xml}</urlset>
"""

def main():
    os.makedirs(IMAGES_DIR, exist_ok=True)
    os.makedirs(ARTISTS_DIR, exist_ok=True)
    
    artist_names = list(TOP_LEGENDS)
    if os.path.exists(SEED_FILE):
        with open(SEED_FILE, 'r', encoding='utf-8') as f:
            seed_list = json.load(f)
            for name in seed_list:
                if name not in artist_names:
                    artist_names.append(name)
                    
    master_lexicon = []
    processed_ids = set()
    
    # RESUME LOGIC: Check if existing lexicon.json has saved entries
    if os.path.exists(LEXICON_FILE):
        try:
            with open(LEXICON_FILE, 'r', encoding='utf-8') as f:
                master_lexicon = json.load(f)
                processed_ids = {a['id'] for a in master_lexicon if a.get('id')}
                print(f"--- Resuming execution from existing {len(master_lexicon)} artists in {LEXICON_FILE} ---")
        except Exception:
            master_lexicon = []
            processed_ids = set()
            
    # Filter remaining artists to process
    remaining_list = [name for name in artist_names if clean_id(name) not in processed_ids]
    print(f"--- Remaining artists to process: {len(remaining_list)} / {len(artist_names)} ---")
    
    start_time = time.time()
    
    with ThreadPoolExecutor(max_workers=5) as executor:
        future_map = {executor.submit(process_single_artist, name): name for name in remaining_list}
        completed = 0
        for future in as_completed(future_map):
            res = future.result()
            completed += 1
            if res:
                master_lexicon.append(res)
                processed_ids.add(res['id'])
                
            if completed % 25 == 0 or completed == len(remaining_list):
                print(f"   Processed: {completed}/{len(remaining_list)} new artists (Total in DB: {len(master_lexicon)}) [{time.time()-start_time:.1f}s]")
                
                # Checkpoint save database
                master_lexicon.sort(key=lambda x: x['name'])
                with open(LEXICON_FILE, 'w', encoding='utf-8') as f:
                    json.dump(master_lexicon, f, indent=2, ensure_ascii=False)
                with open(JS_DATA_FILE, 'w', encoding='utf-8') as f:
                    f.write("window.LEXICON_DATA = ")
                    json.dump(master_lexicon, f, ensure_ascii=False)
                    f.write(";")

    # Sort final master lexicon
    master_lexicon.sort(key=lambda x: x['name'])

    # 1. Save lexicon.json & lexicon_data.js
    with open(LEXICON_FILE, 'w', encoding='utf-8') as f:
        json.dump(master_lexicon, f, indent=2, ensure_ascii=False)
    with open(JS_DATA_FILE, 'w', encoding='utf-8') as f:
        f.write("window.LEXICON_DATA = ")
        json.dump(master_lexicon, f, ensure_ascii=False)
        f.write(";")

    # 2. Generate all static HTML pages in /artists/
    print(f"\n--- Generating {len(master_lexicon)} static SEO artist pages into {ARTISTS_DIR} ---")
    for artist in master_lexicon:
        html_content = generate_artist_html(artist)
        file_path = os.path.join(ARTISTS_DIR, f"{artist['id']}.html")
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(html_content)

    # 3. Generate sitemap.xml
    sitemap_content = generate_sitemap(master_lexicon)
    with open(SITEMAP_FILE, 'w', encoding='utf-8') as f:
        f.write(sitemap_content)

    total_albums = sum(len(a['discography']) for a in master_lexicon)
    total_tracks = sum(sum(len(alb['tracks']) for alb in a['discography']) for a in master_lexicon)

    print(f"\nFULL PIPELINE COMPLETED IN {time.time()-start_time:.1f}s!")
    print(f"  - Total Artists Extracted: {len(master_lexicon)}")
    print(f"  - Total Albums Extracted: {total_albums}")
    print(f"  - Total Song Tracks Extracted: {total_tracks}")
    print(f"  - Static HTML SEO Pages Created: {len(master_lexicon)}")
    print(f"  - Sitemap XML Created: {SITEMAP_FILE}")

if __name__ == "__main__":
    main()
