#!/usr/bin/env python3
import json
import logging
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import parse_qs, urlparse
import yt_dlp

logging.basicConfig(level=logging.INFO, format="[%(asctime)s] %(levelname)s: %(message)s")

cache = {}

def resolve_youtube_stream(youtube_id, mode):
    cache_key = f"{youtube_id}_{mode}"
    if cache_key in cache:
        logging.info(f"Returning cached stream for {cache_key}")
        return cache[cache_key]

    url = f"https://www.youtube.com/watch?v={youtube_id}"
    ydl_opts = {
        "quiet": True,
        "no_warnings": True,
        "format": "bestvideo+bestaudio/best"
    }

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)
            formats = info.get("formats", [])
            hls = info.get("manifest_url")

            target_url = None

            if mode == "SN_TV":
                if hls:
                    target_url = hls
                else:
                    for f in formats:
                        if f.get("vcodec") != "none" and f.get("acodec") != "none" and f.get("url"):
                            target_url = f.get("url")
                            break
                    if not target_url:
                        for f in formats:
                            if f.get("vcodec") != "none" and f.get("url"):
                                target_url = f.get("url")
                                break
            else:
                # SN_RADIO mode (audio only)
                for f in formats:
                    if f.get("vcodec") == "none" and f.get("acodec") != "none" and f.get("url"):
                        target_url = f.get("url")
                        break

            if not target_url and formats:
                target_url = formats[0].get("url")

            if target_url:
                cache[cache_key] = target_url
                logging.info(f"Successfully resolved {youtube_id} [{mode}] -> {target_url[:60]}...")
                return target_url

    except Exception as e:
        logging.error(f"Error resolving {youtube_id}: {e}")

    return None

def fetch_youtube_playlist(url_or_id):
    target_url = url_or_id
    if not target_url.startswith("http"):
        target_url = f"https://www.youtube.com/playlist?list={url_or_id}"

    ydl_opts = {
        'extract_flat': True,
        'quiet': True,
        'skip_download': True,
        'ignoreerrors': True
    }
    all_tracks = []
    try:
        logging.info(f"Extracting playlist/channel items for {target_url}...")
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(target_url, download=False)
            if not info:
                return []

            entries = info.get('entries', [])
            for e in entries:
                if not e:
                    continue
                entry_url = e.get('url') or ''
                # Check if this entry is a child playlist
                if 'playlist?list=' in entry_url or e.get('_type') == 'playlist':
                    try:
                        logging.info(f"Expanding child playlist {entry_url}")
                        child_info = ydl.extract_info(entry_url, download=False)
                        if child_info:
                            for ce in child_info.get('entries', []):
                                if ce and ce.get('id'):
                                    all_tracks.append({
                                        'youtubeId': ce.get('id'),
                                        'title': ce.get('title') or 'Unknown Title',
                                        'artist': ce.get('uploader') or ce.get('channel') or child_info.get('title') or 'Unknown Artist'
                                    })
                    except Exception as ex:
                        logging.error(f"Error fetching child playlist {entry_url}: {ex}")
                elif e.get('id'):
                    title = e.get('title') or 'Unknown Title'
                    duration = e.get('duration') or 0
                    url_val = e.get('url') or ''
                    if '#shorts' in title.lower() or '#short' in title.lower() or '/shorts/' in url_val.lower() or (duration and duration <= 61):
                        continue
                    all_tracks.append({
                        'youtubeId': e.get('id'),
                        'title': title,
                        'artist': e.get('uploader') or e.get('channel') or info.get('title') or 'Unknown Artist'
                    })
    except Exception as e:
        logging.error(f"Error extracting playlist {target_url}: {e}")

    logging.info(f"Extracted total {len(all_tracks)} non-short tracks from {target_url}")
    return all_tracks

class StreamResolverHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path == "/stream":
            params = parse_qs(parsed.query)
            youtube_id = params.get("id", [None])[0]
            mode = params.get("mode", ["SN_TV"])[0]

            if not youtube_id:
                self.send_response(400)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(json.dumps({"status": "error", "message": "Missing id parameter"}).encode())
                return

            stream_url = resolve_youtube_stream(youtube_id, mode)

            if stream_url:
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
                self.wfile.write(json.dumps({
                    "status": "success",
                    "youtubeId": youtube_id,
                    "mode": mode,
                    "url": stream_url
                }).encode())
            else:
                self.send_response(500)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(json.dumps({"status": "error", "message": "Failed to resolve stream"}).encode())

        elif parsed.path == "/fetch_playlist":
            params = parse_qs(parsed.query)
            url = params.get("url", [None])[0] or params.get("id", [None])[0]

            if not url:
                self.send_response(400)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(json.dumps({"status": "error", "message": "Missing url parameter"}).encode())
                return

            tracks = fetch_youtube_playlist(url)

            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(json.dumps({
                "status": "success",
                "url": url,
                "count": len(tracks),
                "tracks": tracks
            }).encode())
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format, *args):
        pass

def run(port=8080):
    server_address = ("", port)
    httpd = HTTPServer(server_address, StreamResolverHandler)
    logging.info(f"Station North yt-dlp Stream & Playlist Resolver active on port {port}")
    httpd.serve_forever()

if __name__ == "__main__":
    run(8080)
