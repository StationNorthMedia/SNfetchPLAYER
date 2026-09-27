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

# Nord Palette HTML Template
html_content = """<!DOCTYPE html>
<html lang="de">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>The Manjaro Lounge Chronicles // Vol. 01</title>
    <style>
        /* Official Nord Color Palette */
        :root {
            --nord0: #2e3440;  /* Polar Night - Darkest background */
            --nord1: #3b4252;  /* Polar Night - Card background */
            --nord2: #434c5e;  /* Polar Night - Hover / Active */
            --nord3: #4c566a;  /* Polar Night - Muted borders & text */
            --nord4: #d8dee9;  /* Snow Storm - Main text */
            --nord5: #e5e9f0;  /* Snow Storm - Emphasized text */
            --nord6: #eceff4;  /* Snow Storm - White headings */
            --nord7: #8fbcbb;  /* Frost - Turquoise */
            --nord8: #88c0d0;  /* Frost - Ice Blue */
            --nord9: #81a1c1;  /* Frost - Steel Blue */
            --nord10: #5e81ac; /* Frost - Deep Blue */
            --nord11: #bf616a; /* Aurora - Red (Accent & ON AIR) */
            --nord12: #d08770; /* Aurora - Orange */
            --nord13: #ebcb8b; /* Aurora - Yellow / Gold */
            --nord14: #a3be8c; /* Aurora - Green */
            --nord15: #b48ead; /* Aurora - Purple */
        }

        * {
            box-sizing: border-box;
        }

        body {
            background-color: var(--nord0);
            color: var(--nord4);
            font-family: 'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            margin: 0;
            padding: 20px 20px 100px 20px; /* Bottom padding for floating player */
            line-height: 1.65;
        }

        header {
            text-align: center;
            padding: 35px 15px 25px 15px;
            border-bottom: 2px solid var(--nord2);
            margin-bottom: 35px;
        }

        h1 {
            font-size: 2.3rem;
            letter-spacing: 2px;
            margin: 0 0 10px 0;
            color: var(--nord6);
            text-transform: uppercase;
        }

        .subtitle {
            color: var(--nord13);
            font-size: 1.1rem;
            letter-spacing: 1.5px;
            text-transform: uppercase;
            font-weight: 600;
        }

        .on-air-badge {
            display: inline-block;
            background-color: var(--nord11);
            color: var(--nord6);
            font-weight: 800;
            padding: 4px 14px;
            border-radius: 4px;
            font-size: 0.85rem;
            letter-spacing: 1.5px;
            margin-top: 12px;
            box-shadow: 0 0 14px rgba(191, 97, 106, 0.5);
            animation: pulse 2s infinite;
        }

        @keyframes pulse {
            0% { opacity: 1; }
            50% { opacity: 0.65; }
            100% { opacity: 1; }
        }

        .container {
            max-width: 850px;
            margin: 0 auto;
        }

        .cover-box {
            text-align: center;
            margin-bottom: 45px;
        }

        .cover-box img {
            max-width: 100%;
            height: auto;
            border-radius: 12px;
            border: 2px solid var(--nord3);
            box-shadow: 0 12px 32px rgba(0,0,0,0.6);
        }

        .chapter-title {
            font-size: 1.6rem;
            font-weight: 700;
            color: var(--nord13);
            border-left: 4px solid var(--nord11);
            padding-left: 16px;
            margin: 50px 0 24px 0;
            letter-spacing: 0.5px;
        }

        .paragraph-card {
            background-color: var(--nord1);
            border: 1px solid var(--nord2);
            border-radius: 10px;
            padding: 22px;
            margin-bottom: 24px;
            box-shadow: 0 4px 18px rgba(0,0,0,0.35);
            transition: all 0.25s ease-in-out;
            position: relative;
        }

        .paragraph-card:hover {
            border-color: var(--nord9);
            transform: translateY(-2px);
        }

        .paragraph-card.active-card {
            border-color: var(--nord11) !important;
            box-shadow: 0 0 20px rgba(191, 97, 106, 0.4);
            background-color: var(--nord2);
        }

        .paragraph-img {
            width: 100%;
            height: auto;
            border-radius: 8px;
            margin-bottom: 18px;
            border: 1px solid var(--nord3);
            display: block;
        }

        .paragraph-text {
            font-size: 1.05rem;
            color: var(--nord4);
            margin-bottom: 18px;
            white-space: pre-wrap;
        }

        .audio-player-box {
            background-color: var(--nord0);
            border: 1px solid var(--nord2);
            border-radius: 8px;
            padding: 10px 16px;
            display: flex;
            align-items: center;
            gap: 15px;
        }

        .audio-label {
            font-size: 0.82rem;
            font-weight: 700;
            color: var(--nord8);
            white-space: nowrap;
            letter-spacing: 0.5px;
        }

        audio {
            width: 100%;
            height: 36px;
            outline: none;
        }

        /* Persistent Floating Continuous Player Bar */
        .player-bar {
            position: fixed;
            bottom: 0;
            left: 0;
            width: 100%;
            background-color: var(--nord1);
            border-top: 2px solid var(--nord3);
            padding: 12px 24px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            z-index: 1000;
            box-shadow: 0 -6px 25px rgba(0,0,0,0.6);
            backdrop-filter: blur(8px);
        }

        .player-info {
            display: flex;
            align-items: center;
            gap: 14px;
            color: var(--nord5);
            font-size: 0.95rem;
            font-weight: 600;
        }

        .player-info .now-playing {
            color: var(--nord8);
        }

        .player-controls-group {
            display: flex;
            align-items: center;
            gap: 12px;
        }

        .btn-control {
            background-color: var(--nord2);
            color: var(--nord6);
            border: 1px solid var(--nord3);
            padding: 8px 16px;
            border-radius: 6px;
            font-weight: 700;
            cursor: pointer;
            transition: all 0.2s ease;
            font-size: 0.9rem;
        }

        .btn-control:hover {
            background-color: var(--nord10);
            border-color: var(--nord8);
        }

        .btn-control.active {
            background-color: var(--nord11);
            border-color: var(--nord11);
            color: var(--nord6);
        }

        footer {
            text-align: center;
            padding: 40px 0 20px 0;
            color: var(--nord3);
            font-size: 0.9rem;
            border-top: 1px solid var(--nord2);
            margin-top: 50px;
        }
    </style>
</head>
<body>

    <header>
        <h1>The Manjaro Lounge Chronicles</h1>
        <div class="subtitle">Vol. 01 // Noir E-Book & Continuous Voice Audio Player</div>
        <div><span class="on-air-badge">ON AIR</span></div>
    </header>

    <div class="container">
        <div class="cover-box">
            <img src="Cover_Vol01.png" alt="Manjaro Lounge Chronicles Vol 01 Cover" loading="lazy">
        </div>
"""

global_p_count = 1

for ch in parsed_chapters:
    title_escaped = ch['title'].replace('<', '&lt;').replace('>', '&gt;')
    html_content += f'\n        <div class="chapter-title">{title_escaped}</div>\n'
    
    for p in ch['paragraphs']:
        img_tag = f'            <img class="paragraph-img" src="{p["image"]}" alt="{p["image"]}" loading="lazy">\n' if p['image'] else ''
        txt_escaped = p['text'].replace('<', '&lt;').replace('>', '&gt;')
        audio_num = f'p_{global_p_count:03d}.mp3'
        
        html_content += f"""        <div class="paragraph-card" id="card-{global_p_count}" data-track="{global_p_count}">
{img_tag}            <div class="paragraph-content">
                <div class="paragraph-text">{txt_escaped}</div>
                <div class="audio-player-box">
                    <span class="audio-label">🎧 AUDIO #{global_p_count}</span>
                    <audio id="audio-{global_p_count}" controls preload="none">
                        <source src="audio/{audio_num}" type="audio/mpeg">
                    </audio>
                </div>
            </div>
        </div>
"""
        global_p_count += 1

html_content += f"""
    </div>

    <!-- Floating Continuous Audio Player Bar -->
    <div class="player-bar">
        <div class="player-info">
            <span>▶ STATUS:</span>
            <span class="now-playing" id="now-playing-title">🎧 AUDIO #1 Bereit</span>
        </div>
        <div class="player-controls-group">
            <button class="btn-control" id="btn-prev">⏮ Vorheriger</button>
            <button class="btn-control active" id="btn-toggle-play">▶ Play All</button>
            <button class="btn-control" id="btn-next">Nächster ⏭</button>
            <button class="btn-control" id="btn-auto-scroll" title="Automatisches Scrollen aktivieren">📜 Auto-Scroll: AN</button>
        </div>
    </div>

    <footer>
        <p>Station North Media &copy; 2026 // The Manjaro Lounge Chronicles</p>
    </footer>

    <script>
        document.addEventListener('DOMContentLoaded', () => {{
            const totalTracks = {global_p_count - 1};
            let currentTrack = 1;
            let isAutoScroll = true;

            const nowPlayingTitle = document.getElementById('now-playing-title');
            const btnTogglePlay = document.getElementById('btn-toggle-play');
            const btnPrev = document.getElementById('btn-prev');
            const btnNext = document.getElementById('btn-next');
            const btnAutoScroll = document.getElementById('btn-auto-scroll');

            function getAudio(id) {{
                return document.getElementById(`audio-${{id}}`);
            }}

            function getCard(id) {{
                return document.getElementById(`card-${{id}}`);
            }}

            function highlightCard(id) {{
                document.querySelectorAll('.paragraph-card').forEach(card => card.classList.remove('active-card'));
                const activeCard = getCard(id);
                if (activeCard) {{
                    activeCard.classList.add('active-card');
                    if (isAutoScroll) {{
                        activeCard.scrollIntoView({{ behavior: 'smooth', block: 'center' }});
                    }}
                }}
            }}

            function playTrack(id) {{
                // Pause all audios
                for (let i = 1; i <= totalTracks; i++) {{
                    const a = getAudio(i);
                    if (a && i !== id) {{
                        a.pause();
                    }}
                }}

                currentTrack = id;
                const audio = getAudio(id);
                if (audio) {{
                    highlightCard(id);
                    nowPlayingTitle.textContent = `🎧 AUDIO #${{id}} wird abgespielt...`;
                    audio.play().catch(e => console.log('Autoplay constraint:', e));
                }}
            }}

            // Event Listeners for native audio tags
            for (let i = 1; i <= totalTracks; i++) {{
                const audio = getAudio(i);
                if (audio) {{
                    audio.addEventListener('play', () => {{
                        currentTrack = i;
                        highlightCard(i);
                        nowPlayingTitle.textContent = `🎧 AUDIO #${{i}} wird abgespielt...`;
                        btnTogglePlay.textContent = '⏸ Pause';
                    }});

                    audio.addEventListener('pause', () => {{
                        if (currentTrack === i) {{
                            btnTogglePlay.textContent = '▶ Weiter';
                        }}
                    }});

                    // CONTINUOUS PLAYBACK: Auto-advance to next track when finished
                    audio.addEventListener('ended', () => {{
                        if (i < totalTracks) {{
                            playTrack(i + 1);
                        }} else {{
                            nowPlayingTitle.textContent = '✅ Vol. 01 Beendet!';
                            btnTogglePlay.textContent = '▶ Play All';
                        }}
                    }});
                }}
            }}

            // Player Bar Controls
            btnTogglePlay.addEventListener('click', () => {{
                const audio = getAudio(currentTrack);
                if (audio) {{
                    if (audio.paused) {{
                        playTrack(currentTrack);
                    }} else {{
                        audio.pause();
                        btnTogglePlay.textContent = '▶ Weiter';
                    }}
                }}
            }});

            btnPrev.addEventListener('click', () => {{
                if (currentTrack > 1) {{
                    playTrack(currentTrack - 1);
                }}
            }});

            btnNext.addEventListener('click', () => {{
                if (currentTrack < totalTracks) {{
                    playTrack(currentTrack + 1);
                }}
            }});

            btnAutoScroll.addEventListener('click', () => {{
                isAutoScroll = !isAutoScroll;
                btnAutoScroll.textContent = `📜 Auto-Scroll: ${{isAutoScroll ? 'AN' : 'AUS'}}`;
                btnAutoScroll.style.opacity = isAutoScroll ? '1' : '0.6';
            }});
        }});
    </script>
</body>
</html>
"""

with open(HTML_PATH, "w", encoding="utf-8") as f:
    f.write(html_content)

print(f"Successfully generated Nord-themed HTML with JS Continuous Audio Player ({global_p_count - 1} tracks).")
