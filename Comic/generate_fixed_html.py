import os
import re

SCRIPT_PATH = "/home/plex/Dokumente/SNfetchPLAYER21.25/Comic/chronicles_script.txt"
HTML_PATH = "/home/plex/Dokumente/SNfetchPLAYER21.25/Comic/chronicles_reader.html"

with open(SCRIPT_PATH, "r", encoding="utf-8") as f:
    text = f.read()

# Split by chapter headings
sections = re.split(r'\n(?=(?:Chapter|CHAPTER)\s+\d+)', text)

parsed_chapters = []

for sec in sections:
    sec = sec.strip()
    if not sec:
        continue
    lines = [l.strip() for l in sec.split('\n') if l.strip()]
    if lines[0].startswith('THE MANJARO LOUNGE'):
        continue
    
    ch_title = lines[0]
    paras = []
    
    current_img = None
    for line in lines[1:]:
        if line.startswith('================================='):
            continue
        if line.startswith('[IMAGE:') and line.endswith(']'):
            current_img = line[7:-1].strip()
        else:
            paras.append({'image': current_img, 'text': line})
            current_img = None
            
    parsed_chapters.append({'title': ch_title, 'paragraphs': paras})

# Adjust image mapping for Chapter 13 if needed
for ch in parsed_chapters:
    if "Chapter 13" in ch['title']:
        # Move Kapitel_13_1.png from paragraph 0 (winter/space heater) to paragraph 4 ("The whole fucking brick factory was lit up...")
        if ch['paragraphs'][0]['image'] == 'Kapitel_13_1.png':
            ch['paragraphs'][0]['image'] = None
            for p in ch['paragraphs']:
                if p['text'].startswith("The whole fucking brick factory was lit up"):
                    p['image'] = 'Kapitel_13_1.png'
                    break

# Re-write chronicles_script.txt cleanly
script_output = "THE MANJARO LOUNGE CHRONICLES // VOL. 01\n\n"
script_output += "==================================================\n"
script_output += "[IMAGE: Cover_Vol01.png]\n"
script_output += "==================================================\n\n"

for ch in parsed_chapters:
    script_output += f"{ch['title']}\n\n"
    for p in ch['paragraphs']:
        if p['image']:
            script_output += f"[IMAGE: {p['image']}]\n"
        script_output += f"{p['text']}\n\n"
    script_output += "\n"

with open(SCRIPT_PATH, "w", encoding="utf-8") as f:
    f.write(script_output.strip() + "\n")

# Generate HTML
html_content = """<!DOCTYPE html>
<html lang="de">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>The Manjaro Lounge Chronicles // Vol. 01</title>
    <style>
        :root {
            --bg-color: #0b0c10;
            --card-bg: #14161d;
            --card-border: #222633;
            --text-main: #e1e4ed;
            --text-dim: #949ab1;
            --accent-red: #ff2a5f;
            --accent-gold: #e5b94c;
            --accent-blue: #00d2ff;
        }

        body {
            background-color: var(--bg-color);
            color: var(--text-main);
            font-family: 'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            margin: 0;
            padding: 20px;
            line-height: 1.6;
        }

        header {
            text-align: center;
            padding: 30px 10px 20px 10px;
            border-bottom: 2px solid var(--card-border);
            margin-bottom: 30px;
            position: relative;
        }

        h1 {
            font-size: 2.2rem;
            letter-spacing: 2px;
            margin: 0 0 10px 0;
            color: #ffffff;
            text-transform: uppercase;
        }

        .subtitle {
            color: var(--accent-gold);
            font-size: 1.1rem;
            letter-spacing: 1.5px;
            text-transform: uppercase;
            font-weight: 600;
        }

        .on-air-badge {
            display: inline-block;
            background-color: var(--accent-red);
            color: white;
            font-weight: 800;
            padding: 4px 12px;
            border-radius: 4px;
            font-size: 0.85rem;
            letter-spacing: 1.5px;
            margin-top: 10px;
            box-shadow: 0 0 12px rgba(255, 42, 95, 0.6);
            animation: pulse 2s infinite;
        }

        @keyframes pulse {
            0% { opacity: 1; }
            50% { opacity: 0.6; }
            100% { opacity: 1; }
        }

        .container {
            max-width: 850px;
            margin: 0 auto;
        }

        .cover-box {
            text-align: center;
            margin-bottom: 40px;
        }

        .cover-box img {
            max-width: 100%;
            height: auto;
            border-radius: 12px;
            border: 2px solid var(--card-border);
            box-shadow: 0 10px 30px rgba(0,0,0,0.8);
        }

        .chapter-title {
            font-size: 1.6rem;
            font-weight: 700;
            color: var(--accent-gold);
            border-left: 4px solid var(--accent-red);
            padding-left: 14px;
            margin: 45px 0 20px 0;
            letter-spacing: 0.5px;
        }

        .paragraph-card {
            background-color: var(--card-bg);
            border: 1px solid var(--card-border);
            border-radius: 10px;
            padding: 20px;
            margin-bottom: 22px;
            box-shadow: 0 4px 15px rgba(0,0,0,0.4);
            transition: transform 0.2s ease, border-color 0.2s ease;
        }

        .paragraph-card:hover {
            border-color: #333a4e;
            transform: translateY(-2px);
        }

        .paragraph-img {
            width: 100%;
            height: auto;
            border-radius: 8px;
            margin-bottom: 16px;
            border: 1px solid #2a2f42;
            display: block;
        }

        .paragraph-text {
            font-size: 1.05rem;
            color: var(--text-main);
            margin-bottom: 16px;
            white-space: pre-wrap;
        }

        .audio-player-box {
            background-color: #0a0b0f;
            border: 1px solid #1a1d28;
            border-radius: 8px;
            padding: 10px 14px;
            display: flex;
            align-items: center;
            gap: 15px;
        }

        .audio-label {
            font-size: 0.82rem;
            font-weight: 700;
            color: var(--accent-blue);
            white-space: nowrap;
            letter-spacing: 0.5px;
        }

        audio {
            width: 100%;
            height: 36px;
            outline: none;
        }

        footer {
            text-align: center;
            padding: 40px 0;
            color: var(--text-dim);
            font-size: 0.9rem;
            border-top: 1px solid var(--card-border);
            margin-top: 50px;
        }
    </style>
</head>
<body>

    <header>
        <h1>The Manjaro Lounge Chronicles</h1>
        <div class="subtitle">Vol. 01 // Noir E-Book & Voice Audio Player</div>
        <div><span class="on-air-badge">ON AIR</span></div>
    </header>

    <div class="container">
        <div class="cover-box">
            <img src="Cover_Vol01.png" alt="Manjaro Lounge Chronicles Vol 01 Cover">
        </div>
"""

global_p_count = 1

for ch in parsed_chapters:
    title_escaped = ch['title'].replace('<', '&lt;').replace('>', '&gt;')
    html_content += f'\n        <div class="chapter-title">{title_escaped}</div>\n'
    
    for p in ch['paragraphs']:
        img_tag = f'            <img class="paragraph-img" src="{p["image"]}" alt="{p["image"]}">\n' if p['image'] else ''
        txt_escaped = p['text'].replace('<', '&lt;').replace('>', '&gt;')
        audio_num = f'p_{global_p_count:03d}.mp3'
        
        html_content += f"""        <div class="paragraph-card">
{img_tag}            <div class="paragraph-content">
                <div class="paragraph-text">{txt_escaped}</div>
                <div class="audio-player-box">
                    <span class="audio-label">🎧 AUDIO #{global_p_count}</span>
                    <audio controls><source src="audio/{audio_num}" type="audio/mpeg"></audio>
                </div>
            </div>
        </div>
"""
        global_p_count += 1

html_content += """
    </div>

    <footer>
        <p>Station North Media &copy; 2026 // The Manjaro Lounge Chronicles</p>
    </footer>

</body>
</html>
"""

with open(HTML_PATH, "w", encoding="utf-8") as f:
    f.write(html_content)

print(f"Successfully updated HTML and Script with relocated Kapitel_13_1.png.")
