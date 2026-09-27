# 📱 F-Droid & IzzyOnDroid Submission Guide for SNfetchPLAYER

This document provides step-by-step instructions and copy-paste-ready templates for submitting **SNfetchPLAYER** to:
1. **IzzyOnDroid Repository** (hosted on Codeberg)
2. **Official F-Droid Repository** (Request for Packaging - RFP on GitLab)

---

## 📋 Pre-Submission Checklist

- [x] **Fastlane Metadata**: Verified at [`fastlane/metadata/android/en-US/`](../fastlane/metadata/android/en-US/) (`title.txt`, `short_description.txt`, `full_description.txt`, phone & tablet screenshots, app icon).
- [x] **Open Source License**: [MIT License](../LICENSE) included in repository root.
- [x] **Automated Releases**: GitHub Actions workflow at [`.github/workflows/release.yml`](../.github/workflows/release.yml) builds and publishes APKs on tag push (`v*`).
- [x] **No Tracker SDKs**: 100% free of telemetry, analytics, and advertising SDKs.

---

## 1. 🚀 IzzyOnDroid Submission (Codeberg)

IzzyOnDroid builds and indexes APKs directly from GitHub Releases. Once submitted, updates will automatically sync when a new GitHub tag (`v*`) is pushed.

### Step-by-Step Instructions

1. Go to the IzzyOnDroid Issue Tracker on Codeberg:  
   🔗 **[https://codeberg.org/Freeyourgadget/fdroid/issues/new](https://codeberg.org/Freeyourgadget/fdroid/issues/new)**
2. Select **"Submit an app"** (or use the blank issue template).
3. Copy the template below, paste it into the issue text box, and click **Submit Issue**.

---

### 📋 Copy-Paste Template for IzzyOnDroid

```markdown
### Application Name
SNfetchPLAYER

### Unique Package Name (Application ID)
net.sn.fetchplayer

### Project License
MIT License

### Source Code Repository
https://github.com/StationNorthMedia/SNfetchPLAYER

### Issue Tracker
https://github.com/StationNorthMedia/SNfetchPLAYER/issues

### Latest Release URL
https://github.com/StationNorthMedia/SNfetchPLAYER/releases

### Short Description
Broadcast & Audio Engine for Android Smartphones, Tablets & Android TV.

### Summary / Description
SNfetchPLAYER is an open-source media broadcast and audio engine designed for Android smartphones, tablets, and Android TV. Built for music lovers and media curators, SNfetchPLAYER turns your Android device into a complete TV & Radio broadcast station.

Key Features:
- **SN-TV Mode**: 16:9 60 FPS video playback engine powered by AndroidX Media3 ExoPlayer with custom lower-third Bauchbinden overlays and full D-Pad / Touch control.
- **SN-RADIO Mode**: 60 FPS real-time FFT frequency spectrum visualizer synchronized with audio streams and dynamic station quotes.
- **Curator Studio**: Central command hub with 100-hit main mix, YouTube playlist importer, community mix export/import, and catalog management.
- **SN-Lexicon**: 4,438+ artist encyclopedia covering Hip-Hop, R&B, and Urban Culture legends with biographies and discographies.
- **Remote Asset Synchronization**: Background cloud delivery for audio jingles and TV station IDs.
- **Privacy First**: No ads, no tracking SDKs, no telemetry.

### Fastlane Metadata
Fastlane metadata and screenshots are included in the repository at `/fastlane/metadata/android/en-US/`.

### Binary Source
APKs are built automatically via GitHub Actions and published under GitHub Releases:
https://github.com/StationNorthMedia/SNfetchPLAYER/releases
```

---

## 2. 🤖 Official F-Droid RFP Submission (GitLab)

F-Droid builds all applications from source using their buildserver infrastructure. A Request for Packaging (RFP) issue notifies the F-Droid inclusion team to create a build recipe.

### Step-by-Step Instructions

1. Log in to GitLab (or register a free account).
2. Open the F-Droid RFP Issue Tracker:  
   🔗 **[https://gitlab.com/fdroid/rfp/-/issues/new](https://gitlab.com/fdroid/rfp/-/issues/new)**
3. Choose the **"App Submission"** template if available, or paste the filled template below.
4. Click **Create Issue**.

---

### 📋 Copy-Paste Template for F-Droid RFP

```markdown
### Name of the app
SNfetchPLAYER

### Package ID
net.sn.fetchplayer

### Source Code Location
https://github.com/StationNorthMedia/SNfetchPLAYER

### License
MIT License

### Summary
Broadcast & Audio Engine for Android Smartphones, Tablets & Android TV.

### Description
SNfetchPLAYER is an open-source media broadcast and audio engine designed for Android smartphones, tablets, and Android TV.

Features:
- **SN-TV Mode**: 16:9 60 FPS ExoPlayer video engine with customizable lower-third metadata overlays.
- **SN-RADIO Mode**: 60 FPS real-time audio FFT spectrum visualizer.
- **Curator Studio**: Pre-configured 100-hit main mix, YouTube playlist importer, and community sharing.
- **SN-Lexicon**: Built-in 4,438+ artist music encyclopedia.
- **Universal TV & Touch Support**: Touchscreen and D-Pad remote control support.
- **Zero Trackers**: Completely open source, free of telemetry, analytics, and non-free dependencies.

### Build Information
- **Build System**: Gradle (`./gradlew assembleRelease`)
- **Java Version**: JDK 17
- **Target SDK**: 34 (Android 14)
- **Minimum SDK**: 24 (Android 7.0)
- **Fastlane Metadata Path**: `fastlane/metadata/android/en-US`

### Additional Notes
- Fastlane structure with screenshots and icons is provided in `fastlane/metadata/android/en-US/`.
- Release tags follow the standard format `v*` (e.g. `v1.0.0.23`).
- Releases are automatically generated via GitHub Actions.
```

---

## 🔗 Direct Submission Links Quick Reference

| Service | Repository / Platform | Action Link |
| :--- | :--- | :--- |
| **IzzyOnDroid** | Codeberg | [Open IzzyOnDroid Issue](https://codeberg.org/Freeyourgadget/fdroid/issues/new) |
| **F-Droid Official** | GitLab RFP | [Open F-Droid RFP Issue](https://gitlab.com/fdroid/rfp/-/issues/new) |
| **GitHub Releases** | GitHub | [View Current Releases](https://github.com/StationNorthMedia/SNfetchPLAYER/releases) |
