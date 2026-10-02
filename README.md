# OpenMapsRouter (Android Native - Kotlin) 🗺️

A native Android application written in **Kotlin** with **Jetpack Compose** that opens Google Maps links, derives the exact latitude and longitude coordinates, and seamlessly opens them in **[Organic Maps](https://organicmaps.app/)**.

---

## 📦 Download / Exported APK

The compiled and signed APK is ready to install:

- **Root APK**: [`OpenMapsRouter.apk`](file:///Users/admin/Desktop/openmapsrouter/OpenMapsRouter.apk) (11 MB)
- **Release APK**: [`app/build/outputs/apk/release/app-release.apk`](file:///Users/admin/Desktop/openmapsrouter/app/build/outputs/apk/release/app-release.apk)
- **Debug APK**: [`app/build/outputs/apk/debug/app-debug.apk`](file:///Users/admin/Desktop/openmapsrouter/app/build/outputs/apk/debug/app-debug.apk)

### How to Install on Android

#### Option 1: Via ADB
```bash
adb install OpenMapsRouter.apk
```

#### Option 2: Direct Install on Device
Transfer `OpenMapsRouter.apk` to your phone via USB, Google Drive, or messaging, and tap to install (enable "Install from unknown sources" if prompted).

---

## ✨ Features

- **Google Maps Link Parser ([`MapsParser.kt`](file:///Users/admin/Desktop/openmapsrouter/app/src/main/java/com/openmapsrouter/app/parser/MapsParser.kt))**:
  - **Short Links**: Follows HTTP 301/302 redirects for `maps.app.goo.gl` and `goo.gl/maps` using OkHttp coroutines.
  - **Exact Pin Coordinates**: Extracts protobuf exact place pin coordinates (`!3d<lat>!4d<lon>`).
  - **Camera Viewport**: Parses `@<lat>,<lon>` viewport centers.
  - **Query Parameters**: Resolves `?q=`, `?ll=`, `?daddr=`, and embedded `name@lat,lon`.
  - **HTML Meta Tag Fallback**: Inspects `staticmap?center=...` and OpenGraph metadata when coordinates are embedded in web responses.
  - **Place Name Extraction**: Derives place name (e.g., *Golden Gate Bridge*, *Eiffel Tower*) to label bookmarks in Organic Maps.
  - **Direct Coordinates & Geo URIs**: Accepts raw `lat, lon` or `geo:` URIs.

- **Organic Maps Integration ([`OrganicMapsLauncher.kt`](file:///Users/admin/Desktop/openmapsrouter/app/src/main/java/com/openmapsrouter/app/utils/OrganicMapsLauncher.kt))**:
  - Primary scheme: `om://map?v=1&ll=<lat>,<lon>&n=<PlaceName>`
  - Secondary fallback: Standard `geo:<lat>,<lon>?q=<lat>,<lon>(<PlaceName>)`
  - Fallback dialog: If Organic Maps is not installed, offers a direct link to download from Google Play or F-Droid.
  - Quick actions to copy coordinates, copy the `om://` URI, or view in OpenStreetMap.

- **Fast & Intuitive Jetpack Compose UI ([`MainScreen.kt`](file:///Users/admin/Desktop/openmapsrouter/app/src/main/java/com/openmapsrouter/app/ui/MainScreen.kt))**:
  - **Status Bar Breathing Room**: Dynamic `statusBarsPadding()` so the header never sits too close to the top edge.
  - **Clipboard Auto-Detection**: Detects copied Google Maps links on app resume and displays a 1-tap "Convert" banner.
  - **Auto-Open Mode**: Toggle in Settings to automatically launch Organic Maps immediately after deriving coordinates.
  - **Conversion History**: Local persistence with 1-tap re-opening, copying, and deletion.
  - **Quick Sample Chips**: Test instantly with 1-tap locations (Eiffel Tower, Golden Gate Bridge, Statue of Liberty, Fushimi Inari, direct coordinates).

- **Android System Integration ([`AndroidManifest.xml`](file:///Users/admin/Desktop/openmapsrouter/app/src/main/AndroidManifest.xml))**:
  - Registered intent filters to open `maps.app.goo.gl`, `goo.gl/maps`, `maps.google.com`, and `geo:` schemes.
  - **Share Target**: Registered for `text/plain` so you can tap **Share** in the Google Maps app and choose OpenMapsRouter directly!

---

## 🛠️ Build from Source

```bash
# Build Debug APK
./gradlew assembleDebug

# Build Release APK
./gradlew assembleRelease
```

---

## 📂 Project Structure

```
openmapsrouter/
├── OpenMapsRouter.apk                  # Ready-to-install Android APK
├── build.gradle.kts                    # Root build configuration
├── settings.gradle.kts                 # Project settings & repositories
├── local.properties                    # Android SDK path
└── app/
    ├── build.gradle.kts                # App dependencies (Compose, OkHttp, Gson)
    └── src/main/
        ├── AndroidManifest.xml         # Permissions, queries, and intent filters
        ├── java/com/openmapsrouter/app/
        │   ├── MainActivity.kt         # Edge-to-edge Compose activity & intent handling
        │   ├── data/
        │   │   ├── Models.kt           # Data classes for coordinates, history, settings
        │   │   └── StorageManager.kt   # SharedPreferences persistence
        │   ├── parser/
        │   │   └── MapsParser.kt       # OkHttp coroutine Google Maps link derivation
        │   ├── utils/
        │   │   └── OrganicMapsLauncher.kt # om:// & geo: intent launcher & fallbacks
        │   └── ui/
        │       ├── theme/              # Color, Theme, Material 3
        │       └── MainScreen.kt       # Complete Jetpack Compose UI
        └── res/
            ├── drawable/               # Vector adaptive app icons
            └── values/                 # strings.xml, colors.xml, themes.xml
```

---

## 📄 License
MIT
