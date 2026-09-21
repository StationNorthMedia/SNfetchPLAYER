import json
import os

input_path = 'SNartists/lexicon.json'
output_dir = 'app/src/main/assets'
output_path = os.path.join(output_dir, 'sn_lexicon.json')

if not os.path.exists(output_dir):
    os.makedirs(output_dir)

with open(input_path, 'r', encoding='utf-8') as f:
    data = json.load(f)

cleaned = []
for item in data:
    if not isinstance(item, dict):
        continue
    name = (item.get('name') or '').strip()
    bio = (item.get('bio') or '').strip()
    qid = (item.get('qid') or '').strip()
    id_str = (item.get('id') or '').strip()
    disco = item.get('discography', [])

    clean_disco = []
    if isinstance(disco, list):
        for alb in disco:
            if not isinstance(alb, dict):
                continue
            t = (alb.get('title') or '').strip()
            y = (alb.get('year') or '').strip()
            rt = (alb.get('record_type') or '').strip()
            tr = [tr.strip() for tr in alb.get('tracks', []) if isinstance(tr, str) and tr.strip()]
            if t or tr:
                clean_disco.append({
                    'title': t,
                    'year': y,
                    'type': rt,
                    'tracks': tr
                })

    if name:
        cleaned.append({
            'id': id_str,
            'name': name,
            'qid': qid,
            'bio': bio,
            'discography': clean_disco
        })

with open(output_path, 'w', encoding='utf-8') as f:
    json.dump(cleaned, f, ensure_ascii=False, separators=(',', ':'))

print(f"Processed {len(cleaned)} artists with discographies.")
print(f"Output saved to {output_path} ({os.path.getsize(output_path) / 1024 / 1024:.2f} MB)")
