<div align="center">

<img src="docs/logo.png" alt="Station North Logo" width="220" />

# SNfetchPLAYER • Broadcast & Audio Engine
**Version 1.0.0.4 • Designed for Android Smartphones, Tablets & Android TV**

[![Release](https://img.shields.io/badge/release-v1.0.0.4-teal.svg)](https://github.com/StationNorthMedia/SNfetchPLAYER/releases/tag/v1.0.0.4)
[![Downloads](https://img.shields.io/github/downloads/StationNorthMedia/SNfetchPLAYER/total.svg?color=brightgreen)](https://github.com/StationNorthMedia/SNfetchPLAYER/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%205.0%2B-blue.svg)](https://developer.android.com/)

</div>

---

## 🌟 Overview

**SNfetchPLAYER** is an immersive broadcast and media player engine built for Android. Crafted for high-fidelity audio playback, curated YouTube video streaming, authentic 60 FPS real-time FFT spectrum audio visualization, and deep musical exploration via the **SN-Lexicon** (4,438+ Artists).

---

## 🚀 Key Features

### 📺 SN-TV (MTV Mode)
- **16:9 Video Engine**: Powered by AndroidX Media3 ExoPlayer with adaptive quality streaming.
- **Slanted TV Bauchbinde**: Dynamic lower-thirds overlay displaying title, artist, and station branding.
- **Touch & D-Pad OSD Control Bar**: On-screen heads-up display (HUD) with Exit Fullscreen, Track Skip, Play/Pause, and Loop toggles.

### 📻 SN-RADIO (Audio Mode)
- **Real-Time FFT Spectrum Visualizer**: Custom 60 FPS audio visualizer canvas rendering real-time frequency spectrum bars.
- **Wikipedia Entertainment Overlay**: Automatic artist biography and summary card synced with live audio playback.

### 🛠️ Curator Studio (Settings Hub)
- **Default Main Mix (100 Hits)**: Pre-configured blend of US R&B Top 50 & US Hip-Hop Top 50 automatically loaded on app launch.
- **Custom Playlist Administration**: Create, rename, delete, and switch active playlists strictly inside the Settings Hub.
- **YouTube Playlist Importer**: Paste any YouTube playlist link or ID to instantly extract and append video streams.

### 📖 SN-Lexicon (4,438+ Entities)
- **Deep Music Encyclopedia**: Database covering over 4,438 unique artists across US Hip-Hop, R&B, and Urban Culture.
- **Genre Badges**: Visual indicators (`🎤 US HIP-HOP`, `🎵 R&B / URBAN`, `🔥 R&B & HIP-HOP`).
- **Discographies & Tracklists**: Comprehensive studio albums, track counts, biography overlays, and direct YouTube search links.

### 👑 Her Majesty's Royal User Manual & FAQ
- Written in authentic **Queen's English** by Royal Appointment to Her Majesty's Station North Realm. Located directly inside the **ℹ️ ABOUT** tab.

### 💻 System Diagnostics & Terminal Logs
- Live terminal log inspector with **📋 Copy Logs** (clipboard) and **💾 Save .txt** (Storage Access Framework) export tools.

---

## 📂 Remote Asset Cloud Delivery

To maintain a lightweight app download size, SNfetchPLAYER supports lazy-loading media assets in the background from GitHub:

```
remote_assets/
├── catalog_manifest.json     # Remote asset manifest & versioning
├── tv_ids/                   # Downloadable .mp4 Station TV IDs
├── jingles/                  # Downloadable .mp3 Radio Jingles
└── playlists/                # Remote JSON playlists
    ├── main_top100.json
    ├── us_rnb_top50.json
    └── us_hiphop_top50.json
```

---

## 📥 Download & Installation

1. Download the latest **`SNfetchPLAYER-v1.0.0.4.apk`** from [GitHub Releases](https://github.com/StationNorthMedia/SNfetchPLAYER/releases/latest).
2. Install the APK on your Android device or Android TV box.
3. Launch **SNfetchPLAYER** and enjoy Station North Media streams!

---

## 📄 License & Credits

Built with ❤️ by **StationNorthMedia**. Distributed under the [MIT License](LICENSE).
