#!/usr/bin/env python3
import json
import os
import re

LEXICON_FILE = "/home/plex/Dokumente/SNartists/lexicon.json"
BASE_DIR = "/home/plex/Dokumente/SNartists"
ARTISTS_DIR = "/home/plex/Dokumente/SNartists/artists"
SITEMAP_FILE = "/home/plex/Dokumente/SNartists/sitemap.xml"
BASE_URL = "https://stationnorth.local"

def get_display_name(name):
    return re.sub(r'\s*\([^)]*\)', '', name).strip() if name else ''

def get_letter_filename(char):
    if char == '#':
        return "directory-num.html"
    return f"directory-{char.lower()}.html"

def generate_toolbar_html(active_page="directory", is_subfolder=False):
    rel_path = "../" if is_subfolder else ""
    return f"""
    <div class="lexicon-toolbar">
      <a href="{rel_path}lexicon.html" class="lexicon-toolbar-link {'active-badge' if active_page == 'search' else ''}">🔍 Live Search</a>
      <a href="{rel_path}lexicon-directory.html" class="lexicon-toolbar-link {'active-badge' if active_page != 'search' else ''}">📚 A-Z Index</a>
    </div>
    """

def generate_site_header_html(is_subfolder=False):
    if not is_subfolder:
        return """
  <!-- Dynamic Website Navigation Placeholder for Root Pages -->
  <div id="nav-placeholder"></div>
  <script src="menu.js" defer></script>
        """
    prefix = "../"
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

def generate_site_footer_html(is_subfolder=False):
    if not is_subfolder:
        return """
  <!-- Dynamic Website Footer Placeholder for Root Pages -->
  <div id="footer-placeholder"></div>
  <script src="slider.js"></script>
        """
    prefix = "../"
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
    <script src="../slider.js"></script>
    """

def generate_alphabet_nav(active_char="", is_subfolder=False):
    letters = [chr(i) for i in range(ord('A'), ord('Z') + 1)] + ['#']
    nav_links = []
    prefix = "" if is_subfolder else "artists/"
    
    for char in letters:
        filename = get_letter_filename(char)
        href = f"{prefix}{filename}"
        is_active = (char == active_char)
        nav_links.append(f'<a href="{href}" class="alphabet-link {"active-letter" if is_active else ""}">{char}</a>')
        
    return f'<nav class="alphabet-nav">{"".join(nav_links)}</nav>'

QUEEN_QUOTES = {
    'HUB': "Thousands of vinyl records stacked to the ceiling of The Bricks. From raw basement tapes to timeless R&B royalty—pick a letter below and dive deep into the crates.",
    'A': "A is for Aaliyah, Alicia, and the smooth beginnings. Welcome to the top of the stack.",
    'B': "Baduisms, B-Sides, and deep 808 basslines. Take your time right here.",
    'C': "Chaka, D'Angelo's crew, and velvet soul cuts. Turn the volume up.",
    'D': "Dark rooms, deep grooves, and D'Angelo vibes. Strictly grown and sexy.",
    'E': "Erykah, Earth-shaking vocals, and pure emotion. Breathe it in.",
    'F': "From Faith to Frank... real ones only on this frequency.",
    'G': "Grooves that linger long after 2 AM. Settle in.",
    'H': "Heartbeats, heavy bass, and midnight harmonies.",
    'I': "Intimate tracks for cold nights. Pull your partner closer.",
    'J': "Janet, Jhené, and jams that demand company. Make the call.",
    'K': "K-Ci, Kehlani, and keys that unlock old memories.",
    'L': "Late-night cruising through Baltimore with the windows down.",
    'M': "Mariah, Maxwell, and the heart of the Manjaro Lounge.",
    'N': "Neo-Soul royalty and raw street poetry. Locked into Station North.",
    'O': "Old-school vinyl warmth meets the modern grid.",
    'P': "Pharrell, Prince, and passion dripping off every record.",
    'Q': "Queens, Quiet Storms, and no cheap talk. You're in my territory now.",
    'R': "Real R&B only. Respect the architects who built this sound.",
    'S': "Silk, Sade, and Soul. Turn the lights down low for this section.",
    'T': "TLC, Teena, and tracks that make you wanna misbehave.",
    'U': "Usher, Urban magic, and midnight rides through the city.",
    'V': "Velvet vocals and analog heat coming through the Studer reels.",
    'W': "Whispered secrets over slow-burning 808s. Stay awhile.",
    'X': "Xscape the noise. The frequency is pure down here.",
    'Y': "Young soul, timeless energy, and unreleased fire.",
    'Z': "Zilo, Zapp, and the final groove in the crates. You reached the end of the line.",
    '#': "112, 702, 24hrs... Numbers don't lie when the rhythm is real."
}

def generate_queen_box_html(quote_text, badge_label="👑 MANJARO LOUNGE // ON AIR"):
    return f"""
      <div class="queen-intro-box">
        <div class="queen-badge">{badge_label}</div>
        <p class="queen-quote">„{quote_text}“</p>
      </div>
    """

def main():
    if not os.path.exists(LEXICON_FILE):
        print(f"Error: {LEXICON_FILE} not found.")
        return

    os.makedirs(ARTISTS_DIR, exist_ok=True)

    with open(LEXICON_FILE, 'r', encoding='utf-8') as f:
        lexicon = json.load(f)

    # Sort lexicon alphabetically
    lexicon.sort(key=lambda x: get_display_name(x['name']).lower())

    # Group by starting letter
    grouped = {}
    for artist in lexicon:
        display_name = get_display_name(artist['name'])
        if not display_name:
            continue
            
        first_char = display_name[0].upper()
        if not first_char.isalpha():
            first_char = '#'
            
        if first_char not in grouped:
            grouped[first_char] = []
        grouped[first_char].append(artist)

    sorted_letters = [chr(i) for i in range(ord('A'), ord('Z') + 1)]
    if '#' in grouped:
        sorted_letters.append('#')

    # Remove legacy directory.html, lexikon-directory.html, or directory-*.html from root BASE_DIR if present
    for fname in ["directory.html", "lexikon-directory.html", "lexikon.html"]:
        fpath = os.path.join(BASE_DIR, fname)
        if os.path.exists(fpath):
            os.remove(fpath)

    for fname in os.listdir(BASE_DIR):
        if fname.startswith("directory-") and fname.endswith(".html"):
            os.remove(os.path.join(BASE_DIR, fname))

    # -------------------------------------------------------------
    # 1. GENERATE MAIN DIRECTORY HUB (lexicon-directory.html in root)
    # -------------------------------------------------------------
    letter_cards_html = ""
    for char in sorted_letters:
        count = len(grouped.get(char, []))
        filename = get_letter_filename(char)
        letter_cards_html += f"""
        <a href="artists/{filename}" class="letter-hub-card">
          <span class="letter-hub-char">{char}</span>
          <span class="letter-hub-count">{count} Artists</span>
        </a>
        """

    directory_hub_html = f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>A-Z Artist Directory Hub | Station North R&B & Soul Lexicon</title>
<meta name="description" content="Browse the complete catalogue of 1,995 R&B, Soul, and Neo-Soul artists alphabetically by starting letter.">
<link rel="stylesheet" href="style-lexicon.css">
<script src="config.js"></script>
</head>
<body>

  <!-- Cloned Station North Header -->
  {generate_site_header_html(is_subfolder=False)}

  <!-- LAYER 1 & 2: Fixed kinetic background -->
  <div class="fixed-visuals">
    <div class="ring"></div><div class="ring"></div><div class="ring"></div>
    <div class="ring"></div><div class="ring"></div><div class="ring"></div>
    <div class="shockwave"></div><div class="shockwave"></div><div class="shockwave"></div>
    <div class="center-logo-container">
      <img src="logo.png" alt="Station North Logo" class="custom-logo" onerror="this.onerror=null; this.src='img/logo.png';">
    </div>
  </div>

  <!-- LAYER 3: Scrollable Content -->
  <div class="scroll-layer">
    <main>
      {generate_toolbar_html('directory', is_subfolder=False)}

      {generate_queen_box_html(QUEEN_QUOTES.get('HUB', 'Welcome to the A-Z Vault.'))}

      <div class="directory-title-area">
        <h2>A-Z Artist Directory ({len(lexicon)} Artists)</h2>
        <p>Select a letter below to browse artists alphabetically.</p>
        
        {generate_alphabet_nav('', is_subfolder=False)}
      </div>

      <div class="letter-hub-grid">
        {letter_cards_html}
      </div>

      {generate_toolbar_html('directory', is_subfolder=False)}
    </main>

    <!-- Cloned Station North Footer -->
    {generate_site_footer_html(is_subfolder=False)}
  </div>

  <script src="slider.js"></script>
</body>
</html>
"""

    with open(os.path.join(BASE_DIR, "lexicon-directory.html"), 'w', encoding='utf-8') as f:
        f.write(directory_hub_html)

    # -------------------------------------------------------------
    # 2. GENERATE 27 LETTER PAGES INSIDE ARTISTS_DIR (artists/directory-a.html)
    # -------------------------------------------------------------
    generated_pages = 0
    for char in sorted_letters:
        artists_in_letter = grouped.get(char, [])
        filename = get_letter_filename(char)
        filepath = os.path.join(ARTISTS_DIR, filename)
        
        cards_html = ""
        for a in artists_in_letter:
            disp_name = get_display_name(a['name'])
            album_count = len(a.get('discography', []))
            img_src = f"../{a['image']}" if a.get('image') else "../logo.png"
            
            cards_html += f"""
            <a href="{a['id']}.html" class="dir-artist-card">
              <img src="{img_src}" alt="{disp_name}" class="dir-artist-avatar" onerror="this.onerror=null; this.src='../logo.png'; if(!this.complete||this.naturalWidth==0)this.src='../img/logo.png';">
              <div class="dir-artist-info">
                <span class="dir-artist-name">{disp_name}</span>
                <span class="dir-artist-meta">{album_count} Albums</span>
              </div>
            </a>
            """
            
        char_quote = QUEEN_QUOTES.get(char, f"Exploring artists under '{char}'.")
        page_html = f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Artists Starting with '{char}' | Station North R&B Lexicon</title>
<meta name="description" content="Browse R&B and Soul artists starting with letter {char}. Includes biographies, album discographies, and song tracklists.">
<link rel="stylesheet" href="../style-lexicon.css">
<script src="../config.js"></script>
</head>
<body>

  <!-- Cloned Station North Header -->
  {generate_site_header_html(is_subfolder=True)}

  <!-- LAYER 1 & 2: Fixed kinetic background -->
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
      {generate_toolbar_html(f'letter-{char}', is_subfolder=True)}

      {generate_queen_box_html(char_quote, f"👑 MANJARO LOUNGE // SECTION {char}")}

      <div class="directory-title-area">
        <h2>Artists Starting with '{char}' <span style="font-size:1.1rem;opacity:0.7;">({len(artists_in_letter)} Artists)</span></h2>
        <p><a href="../lexicon-directory.html" style="color:var(--nord8);text-decoration:none;">← Back to Main Directory Index</a></p>
        
        {generate_alphabet_nav(char, is_subfolder=True)}
      </div>

      <div class="directory-container">
        <div class="directory-grid">
          {cards_html if cards_html else '<p class="no-discography">No artists registered starting with this letter.</p>'}
        </div>
      </div>

      {generate_toolbar_html(f'letter-{char}', is_subfolder=True)}
    </main>

    <!-- Cloned Station North Footer -->
    {generate_site_footer_html(is_subfolder=True)}
  </div>

  <script src="../slider.js"></script>
</body>
</html>
"""

        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(page_html)
        generated_pages += 1

    print(f"SUCCESS! Main lexicon-directory.html created in root, and {generated_pages} letter directory pages created in {ARTISTS_DIR}!")

if __name__ == "__main__":
    main()
