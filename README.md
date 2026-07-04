# Tellyfin

A native Android TV app for watching Live TV through your [Jellyfin](https://jellyfin.org) server. Designed for D-pad navigation and a lean-back TV experience — think waipu.tv or Zattoo, but self-hosted.

## Features

- **Full-screen live TV** via ExoPlayer / HLS, streamed through your Jellyfin server
- **D-pad channel zapping** — UP/DOWN previews the next channel in an OSD banner with a 3-second countdown before switching; press OK to confirm immediately or Back to cancel; hardware CHANNEL +/− buttons switch instantly
- **Numeric zap** — type a channel number to jump directly
- **Channel list** — slide-in overlay (LEFT while watching) with channel logos, current programme, and a "NOW" badge
- **EPG guide** — press RIGHT or GUIDE while watching for a scrollable programme grid with a live "now" indicator line
- **Search** — diacritic-insensitive, ranked search across channel names/numbers, programme titles, genres, and descriptions; live programmes surface first
- **Automatic stream recovery** — transient stream errors are retried quietly before an error is shown
- **Header-based auth** — the access token is sent via `Authorization` headers (never in URLs), safe for publicly exposed servers
- **Update prompt** — on start the app checks for a newer version and offers to download and install it
- **Now Playing panel** — press OK while watching to see current programme details, remaining time, and upcoming slots
- **Quick Menu** — press Menu to toggle favourite, refresh the stream, or open settings
- **Favourites** — mark channels as favourites; persisted locally; filter the home screen to show only favourites
- **Bandwidth cap** — settings screen lets you pick Auto / 2 / 4 / 8 / 12 / 20 / 40 Mbps
- **Google TV–style home screen** — "Läuft jetzt" horizontal card row, filter tabs (All / Favourites), EPG channel list with progress bars

## Requirements

- Android TV device (Fire TV, Android TV box, Google TV, etc.) running Android 5.0+
- A running [Jellyfin](https://jellyfin.org) instance with Live TV configured (DVB tuner, M3U IPTV plugin, etc.)

## Building

1. Clone the repo
2. Open in [Android Studio](https://developer.android.com/studio) (Ladybug or later)
3. Let Gradle sync — requires Gradle 8.7 and Kotlin 2.2
4. Connect a device or emulator and press **Run**

```bash
./gradlew assembleDebug
# APK lands in app/build/outputs/apk/debug/
```

Run the unit tests (search ranking, version comparison, auth header, programme model):

```bash
./gradlew testDebugUnitTest
```

## Installation (sideload)

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or transfer the APK to your device and install via a file manager.

## Tech stack

| Layer | Library |
|---|---|
| UI | Jetpack Compose, `androidx.tv:tv-material` |
| Video | Media3 / ExoPlayer (HLS) |
| Jellyfin | `org.jellyfin.sdk:jellyfin-core:1.8.x` |
| Persistence | DataStore Preferences |
| Image loading | Coil |

## Package

`app.tellyfin.androidtv`

## License

MIT
