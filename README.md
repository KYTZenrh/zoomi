# Zoomi

Free, open-source navigation for Android. No ads, no tracking, no Google Play Services.

**Status: milestone (b), online car routing with turn-by-turn.** Offline regions, other travel modes,
accounts, alerts and traffic are planned (see Roadmap) and **not in the app yet**. Nothing in the app is a placeholder.

## What works now

### Milestone (b): search and car navigation (online)
- Search with autocomplete (Photon). Starts after 3 letters, waits 400 ms after typing stops, max 1 request/s.
  Results show the place type (Museum, Bus stop, …). Long-press the map to route to any point (reverse geocoded).
- Car routes from Valhalla with up to 2 alternatives (tap a grey route or a chip to pick it), duration, distance, arrival time.
  ETAs are **without live traffic**.
- Turn-by-turn via Ferrostar: big maneuver banner, lane arrows where OSM has `turn:lanes`, voice (Android TTS, phone language;
  Valhalla supports Romanian), speed-limit sign where OSM has `maxspeed`, current speed, automatic reroute when off route
  (at most one new route every 5 s), arrival screen.
- Keeps working with the screen off (location foreground service + ongoing notification with an End action).
- Saved places: one Home, one Work, any number of favourites (stored on the phone only).
- Share ETA: Android share sheet with destination, arrival time and an OpenStreetMap link. Never your current position.
- Settings: routing and search server URLs are editable (point them at your own server any time).

### Milestone (a): map, location, theme

- Vector map (OpenStreetMap data via OpenFreeMap) with Zoomi's own driving-focused style, light and dark.
- Position from the **GPS chip only** (`LocationManager.GPS_PROVIDER`). No network positioning, works without internet.
- Follow camera, with heading-up (map rotates with your direction) or north-up.
- Heading arrow appears only once you move faster than 2.5 m/s with an accurate GPS course. When you stop,
  it **holds the last course** instead of following GPS noise. Before that you see a plain dot.
- Theme: **Light / Dark / Auto**.
  - Auto = dark from sunset to sunrise at your position (computed on the phone, offline), with hysteresis:
    dark below −2° sun elevation, light above +1°, at most one switch per minute.
  - Auto also turns dark **in tunnels**: the map must show a tunnel road under you, plus GPS lost ≥3 s or light < 30 lux for 2 s.
    Entry also requires that you were driving (>= 4 m/s) in the last 15 s. Exit needs fresh GPS and the map
    showing no tunnel for 3 s, 8 s of accurate GPS (<= 25 m) when the map is unknown, or > 200 lux for 5 s.
  - Settings → "Why the theme is what it is now" shows the live inputs (sun elevation, lux, GPS age, tunnel).

## Build

Requirements: JDK 17+ (tested with Temurin 21.0.13), Android SDK with platforms `android-37.0` (compile) and `android-36` (target). Gradle 9.9.0 comes with the wrapper.

```
# local.properties (not committed)
sdk.dir=C\:\\path\\to\\Android\\sdk
```

```
./gradlew :core:test          # unit tests (pure Kotlin, no device needed)
./gradlew :app:assembleDebug  # APK at app/build/outputs/apk/debug/app-debug.apk
```

Install on a phone with USB debugging enabled: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Signing your own release APK

1. Create a key once (keep it safe; losing it means users must uninstall to update):
   `keytool -genkeypair -v -keystore zoomi-release.jks -alias zoomi -keyalg RSA -keysize 4096 -validity 10000`
2. Build: `./gradlew :app:assembleRelease`
3. Sign: `apksigner sign --ks zoomi-release.jks --out zoomi.apk app/build/outputs/apk/release/app-release-unsigned.apk`
   (`apksigner` is in the SDK `build-tools/<version>/` folder; run `zipalign -p 4` first if your build-tools version requires it.)
4. Upload `zoomi.apk` to a GitHub Release. Never commit the `.jks` file.

## Data sources and attribution

| Source | Used for | Licence / terms |
|---|---|---|
| OpenStreetMap contributors | all map data | ODbL 1.0, attribution "© OpenStreetMap contributors" |
| OpenMapTiles schema | vector tile layout | CC-BY 4.0, "© OpenMapTiles" |
| OpenFreeMap (tiles.openfreemap.org) | online tiles and fonts | free public instance, no key; donations fund it |
| MapLibre Native | rendering | BSD 2-Clause |
| NOAA solar equations | sunrise/sunset | public-domain algorithm, implemented in `core/` |
| Valhalla on valhalla1.openstreetmap.de (FOSSGIS e.V.) | car routes, instructions | Valhalla MIT; server terms: max 1 req/s, identifying User-Agent, attribution + fixthemap link |
| Photon on photon.komoot.io (komoot) | search, reverse geocoding | Photon Apache-2.0; public server "fair use, heavy use throttled" |
| Ferrostar (Stadia Maps) | navigation logic, TTS, foreground service | BSD 3-Clause |
| OkHttp | HTTP | Apache-2.0 |

Attribution is visible on the map (bottom-left "© OpenStreetMap") and in Settings → About.

## Privacy policy (current build)

- Zoomi collects **no** personal data, has no analytics, no crash reporting, no ads, no account.
- Location is processed on the device. It leaves the phone only as described below, and only when you use a feature.
- Network traffic (every server also sees your IP address, as with any web request):
  - `tiles.openfreemap.org`: map tiles and fonts (reveals the area you view).
  - Search server (default `photon.komoot.io`): the text you type and your position **rounded to ~1 km** (for ranking).
    Long-press sends the pressed point for reverse geocoding.
  - Routing server (default `valhalla1.openstreetmap.de`): start point (your position, accuracy and heading) and destination
    of each route request, including automatic reroutes.
- Zoomi has no server of its own yet; nothing is stored about you anywhere except on your phone.
- Settings and saved places are stored locally on the device. `allowBackup` is off.

This section will be updated, before release, for every milestone that adds a network feature.

## Known limitations

- Needs internet: no offline maps or offline routing yet (milestone c). If the connection drops mid-trip, guidance
  continues on the current route, but rerouting fails until the connection returns.
- Car only. Bike, walking, public transport and the "no road route / water crossing" dialog come in milestone (d).
- ETAs don't include live traffic (milestone g), so rush-hour estimates will be optimistic.
- Default servers are community-run with strict limits (1 req/s). Fine for personal use and testing; a public release
  should use Zoomi's own routing server (planned on Oracle Always Free at milestone e). FOSSGIS also requires an operator
  contact email on the app's website before public distribution.
- Speed limits and lane arrows only appear where OpenStreetMap has `maxspeed` / `turn:lanes`.
- Voice language follows the phone's system language; quality depends on the installed Android TTS voice.
- Heading: no gyro fusion or dead reckoning yet (milestone c). At start-up you get a dot until you drive.
- Tunnel detection depends on OSM having the road tagged `tunnel=yes` and on the map following you.
- The light sensor on most phones faces the screen side; in a mount it may be shaded. That's why it never switches the theme alone.

## Roadmap

(a) map + location + theme · (b) online routing + turn-by-turn · (c) offline regions, offline routing, heading fix ·
(d) bike/walk/public transport + water crossings · (e) optional account + settings sync · (f) crowd alerts · (g) traffic estimation.

## Licence

GPL-3.0-or-later. See `LICENSE`.
