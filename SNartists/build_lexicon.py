#!/usr/bin/env python3
import urllib.request
import urllib.parse
import json
import time
import os
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed

HEADERS = {'User-Agent': 'StationNorthArtistLexiconBuilder/1.0 (contact@stationnorth.org)'}

SEED_FILE = "/home/plex/Dokumente/SNartists/artist_seed_list.json"
OUTPUT_FILE = "/home/plex/Dokumente/SNartists/lexicon.json"

def fetch_wikipedia_summary(title):
    encoded_title = urllib.parse.quote(title.replace(" ", "_"))
    url = f"https://en.wikipedia.org/api/rest_v1/page/summary/{encoded_title}"
    req = urllib.request.Request(url, headers=HEADERS)
    
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read().decode('utf-8'))
            if data.get('type') == 'disambiguation':
                return None
                
            return {
                'id': clean_id(data.get('title') or title),
                'name': data.get('title') or title,
                'qid': data.get('wikibase_item'),
                'image': data.get('thumbnail', {}).get('source') if data.get('thumbnail') else None,
                'bio': data.get('extract')
            }
    except Exception:
        return None

def fetch_wikidata_discography_batch(qids):
    if not qids:
        return {}
        
    formatted_qids = " ".join([f"wd:{q}" for q in qids if q and q.startswith("Q")])
    if not formatted_qids:
        return {}
        
    sparql_url = 'https://query.wikidata.org/sparql'
    query = f'''
    SELECT ?artist ?album ?albumLabel (MIN(?year) AS ?releaseYear) WHERE {{
      VALUES ?artist {{ {formatted_qids} }}
      ?album wdt:P175 ?artist ;
             wdt:P31/wdt:P279* wd:Q482994 .
      FILTER NOT EXISTS {{ ?album wdt:P31/wdt:P279* wd:Q134556 }}
      OPTIONAL {{ 
        ?album wdt:P577 ?date . 
        BIND(STR(YEAR(?date)) AS ?year)
      }}
      SERVICE wikibase:label {{ bd:serviceParam wikibase:language 'en'. }}
    }}
    GROUP BY ?artist ?album ?albumLabel
    ORDER BY ?artist ?releaseYear
    '''
    
    req = urllib.request.Request(f"{sparql_url}?query={urllib.parse.quote(query)}&format=json", headers=HEADERS)
    discography_map = {q: [] for q in qids}
    
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=20) as resp:
                data = json.loads(resp.read().decode('utf-8'))
                results = data.get('results', {}).get('bindings', [])
                
                seen_albums = {q: set() for q in qids}
                
                for r in results:
                    artist_qid = r.get('artist', {}).get('value', '').split('/')[-1]
                    title = r.get('albumLabel', {}).get('value', '').strip()
                    year = r.get('releaseYear', {}).get('value', '').strip()
                    
                    if artist_qid in discography_map and title and not title.startswith('Q'):
                        title_key = title.lower()
                        if title_key not in seen_albums[artist_qid]:
                            seen_albums[artist_qid].add(title_key)
                            discography_map[artist_qid].append({
                                'year': year if year else 'N/A',
                                'title': title
                            })
                break
        except Exception as e:
            time.sleep(2.0)
            
    return discography_map

def clean_id(name):
    clean = "".join(c if c.isalnum() or c in " -_" else "" for c in name)
    return clean.lower().replace(" ", "-").strip("-")

def main():
    if not os.path.exists(SEED_FILE):
        print(f"Error: Seed file {SEED_FILE} not found. Run fetch_artists.py first.")
        sys.exit(1)
        
    with open(SEED_FILE, "r", encoding="utf-8") as f:
        artists = json.load(f)
        
    limit = len(artists)
    if len(sys.argv) > 1:
        try:
            limit = int(sys.argv[1])
        except ValueError:
            pass
            
    target_artists = artists[:limit]
    print(f"--- 1. Fetching English Bios for {len(target_artists)} artists ---")
    
    artist_records = {}
    
    start_time = time.time()
    with ThreadPoolExecutor(max_workers=20) as executor:
        future_to_artist = {executor.submit(fetch_wikipedia_summary, artist): artist for artist in target_artists}
        completed = 0
        for future in as_completed(future_to_artist):
            res = future.result()
            completed += 1
            if res and res.get('bio'):
                artist_records[res['id']] = res
            if completed % 100 == 0 or completed == len(target_artists):
                print(f"   Bios fetched: {len(artist_records)}/{completed} ({time.time()-start_time:.1f}s)")
                
    qids = [rec['qid'] for rec in artist_records.values() if rec.get('qid')]
    print(f"\n--- 2. Fetching Discographies for {len(qids)} Wikidata QIDs in batches ---")
    
    batch_size = 50
    all_discographies = {}
    
    for i in range(0, len(qids), batch_size):
        batch = qids[i:i+batch_size]
        print(f"   Fetching SPARQL batch {i//batch_size + 1}/{(len(qids)+batch_size-1)//batch_size} ({len(batch)} artists)...")
        disco_batch = fetch_wikidata_discography_batch(batch)
        all_discographies.update(disco_batch)
        time.sleep(0.3)
        
    print("\n--- 3. Assembling final dataset ---")
    final_lexicon = []
    for rec in artist_records.values():
        qid = rec.get('qid')
        rec['discography'] = all_discographies.get(qid, []) if qid else []
        final_lexicon.append(rec)
        
    # Sort lexicon alphabetically by artist name
    final_lexicon.sort(key=lambda x: x['name'])
    
    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        json.dump(final_lexicon, f, indent=2, ensure_ascii=False)
        
    print(f"\nSUCCESS! Built English artist database with {len(final_lexicon)} entries in {time.time()-start_time:.1f} seconds!")
    print(f"File saved to: {OUTPUT_FILE}")

if __name__ == "__main__":
    main()
