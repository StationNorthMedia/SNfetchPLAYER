#!/usr/bin/env python3
import urllib.request
import urllib.parse
import json
import time
import os

HEADERS = {'User-Agent': 'StationNorthArtistFetcher/1.0 (contact@stationnorth.org)'}

CATEGORIES = [
    'Category:Neo_soul_singers',
    'Category:American_contemporary_R%26B_singers',
    'Category:Rhythm_and_blues_singers',
    'Category:American_rhythm_and_blues_singers',
    'Category:Soul_singers',
    'Category:American_soul_singers',
    'Category:British_rhythm_and_blues_singers',
    'Category:Motown_artists',
    'Category:Quiet_storm_musicians'
]

def fetch_category_members(category_title):
    members = []
    cmcontinue = None
    
    while True:
        url = f"https://en.wikipedia.org/w/api.php?action=query&list=categorymembers&cmtitle={category_title}&cmlimit=500&format=json"
        if cmcontinue:
            url += f"&cmcontinue={cmcontinue}"
            
        req = urllib.request.Request(url, headers=HEADERS)
        try:
            with urllib.request.urlopen(req) as resp:
                data = json.loads(resp.read().decode('utf-8'))
                query_res = data.get('query', {})
                for cm in query_res.get('categorymembers', []):
                    # ns == 0 means main article (not subcategory or template)
                    if cm.get('ns') == 0:
                        title = cm.get('title')
                        # Filter out non-artist pages like lists or discographies
                        if not (title.startswith("List of") or "discography" in title.lower() or "awards" in title.lower()):
                            members.append(title)
                
                cmcontinue = data.get('continue', {}).get('cmcontinue')
                if not cmcontinue:
                    break
                time.sleep(0.1)
        except Exception as e:
            print(f"Error fetching {category_title}: {e}")
            break
            
    return members

def main():
    print("Fetching R&B / Neo-Soul / Soul artist list from English Wikipedia...")
    all_artists = set()
    
    for cat in CATEGORIES:
        print(f"-> Querying {cat}...")
        cats_members = fetch_category_members(cat)
        print(f"   Found {len(cats_members)} members")
        all_artists.update(cats_members)
        time.sleep(0.2)
        
    artist_list = sorted(list(all_artists))
    print(f"\nTotal unique artists fetched: {len(artist_list)}")
    
    output_path = "/home/plex/Dokumente/SNartists/artist_seed_list.json"
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(artist_list, f, indent=2, ensure_ascii=False)
        
    print(f"Saved artist list to {output_path}")

if __name__ == "__main__":
    main()
