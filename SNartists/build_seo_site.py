#!/usr/bin/env python3
import json
import os
import urllib.parse

LEXICON_FILE = "/home/plex/Dokumente/SNartists/lexicon.json"
ARTISTS_DIR = "/home/plex/Dokumente/SNartists/artists"
SITEMAP_FILE = "/home/plex/Dokumente/SNartists/sitemap.xml"
BASE_URL = "https://stationnorth.local" # Production domain placeholder

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

def generate_site_header_html(is_subfolder=True):
    prefix = "../" if is_subfolder else ""
    return f"""
    <header class="lexicon-standalone-header">
      <div class="header-brand">
        <a href="{prefix}lexicon.html" style="text-decoration:none;display:flex;align-items:center;gap:12px;color:inherit;">
          <img src="{prefix}logo.png" alt="Station North Logo" class="header-logo" onerror="this.onerror=null; this.src='{prefix}img/logo.png';">
          <h1>LEXICON</h1>
        </a>
      </div>
      <div class="header-nav-group">
        <a href="{prefix}hub.html" class="header-badge" style="text-decoration:none;">🌐 Main Portal</a>
      </div>
    </header>
    """

def generate_site_footer_html(is_subfolder=True):
    prefix = "../" if is_subfolder else ""
    return f"""
    <footer class="lexicon-standalone-footer">
      <div class="footer-nav-links">
        <a href="{prefix}hub.html">🌐 Main Portal</a>
        <span>•</span>
        <a href="{prefix}lexicon.html">🔍 Live Search</a>
        <span>•</span>
        <a href="{prefix}lexicon-directory.html">📚 A-Z Index</a>
        <span>•</span>
        <a href="#" onclick="window.scrollTo({{top:0,behavior:'smooth'}});return false;">⬆️ Back to Top</a>
      </div>
      <div class="footer-copyright">
        © 2026 Station North Media Project | R&B & Neo-Soul Knowledge Archive
      </div>
    </footer>
    """

def generate_artist_html(artist):
    artist_name = artist['name']
    bio = artist.get('bio', 'No biography available.')
    image_src = f"../{artist['image']}" if artist.get('image') else "../logo.png"
    artist_query = urllib.parse.quote(artist_name)
    
    streaming_buttons_html = f"""
    <div class="artist-streaming-group">
      <a href="https://www.youtube.com/results?search_query={artist_query}" target="_blank" rel="noopener noreferrer" class="artist-stream-btn yt">▶️ YouTube</a>
      <a href="https://www.deezer.com/search/{artist_query}" target="_blank" rel="noopener noreferrer" class="artist-stream-btn dz">🎵 Deezer</a>
      <a href="https://open.spotify.com/search/{artist_query}" target="_blank" rel="noopener noreferrer" class="artist-stream-btn sp">🎧 Spotify</a>
      <a href="https://music.apple.com/us/search?term={artist_query}" target="_blank" rel="noopener noreferrer" class="artist-stream-btn ap">🍎 Apple Music</a>
    </div>
    """

    albums_html = ""
    for alb in artist.get('discography', []):
        tracks = alb.get('tracks', [])
        tracks_html_list = []
        for t in tracks:
            t_query = urllib.parse.quote(f"{artist_name} {t}")
            tracks_html_list.append(f"""
            <li>
              <div class="track-item-wrapper">
                <span class="track-name">{t}</span>
                <span class="track-actions">
                  <a href="https://www.youtube.com/results?search_query={t_query}" target="_blank" rel="noopener noreferrer" class="track-action-btn" title="Search on YouTube">▶️ YT</a>
                  <a href="https://www.deezer.com/search/{t_query}" target="_blank" rel="noopener noreferrer" class="track-action-btn" title="Search on Deezer">🎵 DZ</a>
                  <a href="https://open.spotify.com/search/{t_query}" target="_blank" rel="noopener noreferrer" class="track-action-btn" title="Search on Spotify">🎧 SP</a>
                </span>
              </div>
            </li>
            """)
        tracks_html = "".join(tracks_html_list)
        
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

    related_html = ""
    if artist.get('related_artists'):
        cards = ""
        for rel in artist['related_artists'][:6]:
            rel_id = rel['id']
            rel_name = rel['name']
            cards += f"""
            <a href="{rel_id}.html" class="related-card">
              <img src="../images/{rel_id}.jpg" alt="{rel_name}" class="related-card-avatar" onerror="this.onerror=null; this.src='../logo.png';">
              <span class="related-card-name">{rel_name}</span>
            </a>
            """
        related_html = f"""
        <div class="related-section">
          <div class="discography-title">✨ Similar R&B / Soul Artists</div>
          <div class="related-grid">{cards}</div>
        </div>
        """
        
    json_ld = generate_json_ld(artist)

    return f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>{artist_name} - Discography, Bio & Tracklists | Station North Lexicon</title>
<meta name="description" content="{bio[:150].replace('\"', '&quot;')}...">
<link rel="stylesheet" href="../style-lexicon.css">
<script src="../config.js"></script>

<!-- Google Rich Snippet Structured Data (SEO) -->
<script type="application/ld+json">
{json_ld}
</script>
</head>
<body>

  <!-- Cloned Station North Header -->
  {generate_site_header_html(is_subfolder=True)}

  <!-- LAYER 1 & 2: Fixed kinetic background visual center -->
  <div class="fixed-visuals">
    <div class="ring"></div><div class="ring"></div><div class="ring"></div>
    <div class="ring"></div><div class="ring"></div><div class="ring"></div>
    <div class="shockwave"></div><div class="shockwave"></div><div class="shockwave"></div>
    <div class="center-logo-container">
      <img src="../logo.png" alt="Station North Logo" class="custom-logo" onerror="this.onerror=null; this.src='../img/logo.png';">
    </div>
  </div>

  <!-- LAYER 3: Scrollable Content -->
  <div class="scroll-layer">
    <main>
      <div class="lexicon-toolbar">
        <a href="../lexicon.html" class="lexicon-toolbar-link">🔍 Live Search</a>
        <a href="../lexicon-directory.html" class="lexicon-toolbar-link">📚 A-Z Index</a>
      </div>

      <div class="artist-detail-card" style="margin-top:20px;">
        <div class="artist-detail-header">
          <img src="{image_src}" alt="{artist_name}" class="artist-detail-image" onerror="this.onerror=null; this.src='../logo.png'; if(!this.complete||this.naturalWidth==0)this.src='../img/logo.png';">
          <div class="artist-detail-title-group">
            <div class="artist-tags">
              <span class="artist-tag">R&B / Soul</span>
              <span class="artist-tag">SEO Verified Entity</span>
            </div>
            <h2>{artist_name}</h2>
            {streaming_buttons_html}
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

        {related_html}
      </div>

      <div class="lexicon-toolbar">
        <a href="../lexicon.html" class="lexicon-toolbar-link">🔍 Live Search</a>
        <a href="../lexicon-directory.html" class="lexicon-toolbar-link">📚 A-Z Index</a>
      </div>
    </main>

    <!-- Cloned Station North Footer -->
    {generate_site_footer_html(is_subfolder=True)}
  </div>

  <!-- Website Footer and Ticker Script -->
  <script src="../slider.js"></script>
</body>
</html>
"""

def generate_sitemap(lexicon):
    urls_xml = """  <url>
    <loc>{BASE_URL}/lexicon-directory.html</loc>
    <changefreq>daily</changefreq>
    <priority>0.9</priority>
  </url>\n""".format(BASE_URL=BASE_URL)

    letters = [chr(i) for i in range(ord('a'), ord('z') + 1)] + ['num']
    for letter in letters:
        urls_xml += f"""  <url>
    <loc>{BASE_URL}/artists/directory-{letter}.html</loc>
    <changefreq>weekly</changefreq>
    <priority>0.85</priority>
  </url>\n"""

    for a in lexicon:
        urls_xml += f"""  <url>
    <loc>{BASE_URL}/artists/{a['id']}.html</loc>
    <changefreq>weekly</changefreq>
    <priority>0.8</priority>
  </url>\n"""
  
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
  <url>
    <loc>{BASE_URL}/lexicon.html</loc>
    <changefreq>daily</changefreq>
    <priority>1.0</priority>
  </url>
{urls_xml}</urlset>
"""

def main():
    if not os.path.exists(LEXICON_FILE):
        print(f"Error: {LEXICON_FILE} not found.")
        return
        
    os.makedirs(ARTISTS_DIR, exist_ok=True)
    
    with open(LEXICON_FILE, 'r', encoding='utf-8') as f:
        lexicon = json.load(f)
        
    print(f"--- Generating {len(lexicon)} static SEO artist pages into {ARTISTS_DIR} ---")
    
    for artist in lexicon:
        html_content = generate_artist_html(artist)
        file_path = os.path.join(ARTISTS_DIR, f"{artist['id']}.html")
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(html_content)
            
    # Generate sitemap.xml
    sitemap_content = generate_sitemap(lexicon)
    with open(SITEMAP_FILE, 'w', encoding='utf-8') as f:
        f.write(sitemap_content)
        
    print(f"SUCCESS! Generated {len(lexicon)} static HTML pages and {SITEMAP_FILE}!")

if __name__ == "__main__":
    main()
