import re

SCRIPT_PATH = "/home/plex/Dokumente/SNfetchPLAYER21.25/Comic/chronicles_script.txt"

with open(SCRIPT_PATH, "r", encoding="utf-8") as f:
    text = f.read()

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

output = "THE MANJARO LOUNGE CHRONICLES // VOL. 01\n\n"
output += "==================================================\n"
output += "[IMAGE: Cover_Vol01.png]\n"
output += "==================================================\n\n"

for ch in parsed_chapters:
    output += f"{ch['title']}\n\n"
    for p in ch['paragraphs']:
        if p['image']:
            output += f"[IMAGE: {p['image']}]\n"
        output += f"{p['text']}\n\n"
    output += "\n"

with open(SCRIPT_PATH, "w", encoding="utf-8") as f:
    f.write(output.strip() + "\n")

print("Successfully formatted chronicles_script.txt")
