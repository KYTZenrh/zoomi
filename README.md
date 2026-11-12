# Zoomi

Free, open-source navigation for Android. No ads, no tracking, no Google Play Services.

**Version 1.0.0.** Car / bike / walk navigation online and offline, public transport, satellite view and
downloadable offline maps for every country in Europe. Accounts, crowd alerts and live traffic are planned
(see Roadmap) and **not in the app yet**. Nothing in the app is a placeholder.

## Install

Download `Zoomi-1.0.0.apk` from the [latest release](https://github.com/KYTZenrh/zoomi/releases/latest), open it on your
phone and allow "install unknown apps" for your browser or file manager when Android asks. Android 10 or newer, ARM phones.

## New in 1.0.0

- **Settings in categories:** Map, Navigation, Offline maps, Advanced, About.
- **Offline maps for any country in Europe:** Settings → Offline maps suggests the country you're in and lists every
  country by continent ("Download all" per continent). Each package has the map and the road network for offline routes,
  built from OpenStreetMap by this repository's GitHub Actions workflow (`.github/workflows/regions.yml`) and hosted as
  files of the [regions release](https://github.com/KYTZenrh/zoomi/releases/tag/regions). More continents can be built
  from the Actions tab (Run workflow → continent). Without using the phone's data: download a country's three files
  (`<id>.region.json`, `<id>-map.pmtiles`, `<id>-routing.tar`, plus `.001`, `.002` … pieces for very big countries)
  from the regions release on a computer, copy them to the phone, then Offline maps → Advanced → Choose files… and
  select them together (several countries at once work too).
- **Dark colours in tunnels and underground:** while navigating, the route itself says where tunnels are, so the map
  turns dark at the portal (the way big navigation apps do it); in underground car parks it turns dark when GPS is
  lost and it's very dark right after driving. Outside navigation, the mapped tunnel under you plus lost GPS or darkness.
- **Position colour:** 7 colours of equal lightness for the arrow and accuracy circle.
- **Route options:** avoid tolls, motorways, ferries. **Speed alert:** a short beep when over the limit (where OSM has it).


## What works now

### Milestone (d): travel modes and water crossings
- **Car, Bike, Walk** (Valhalla `auto` / `bicycle` / `pedestrian`), online and offline, with turn-by-turn and reroute.
  Speed-limit signs only in car mode.
- **Public transport** (online only) from [Transitous](https://transitous.org) (MOTIS 2): departure/arrival times,
  lines, operators, walking legs, transfers; drawn on the map. Itinerary only, no turn-by-turn for transit.
  Shows "timetable" vs "live" per leg. If walking beats transit, it says so.
  Coverage depends on which operators publish open GTFS timetables. In Iași (SCTP) it works with real data;
  see transitous.org/sources for other places. Nothing is invented: no data means "no connection found".
- **Water crossings**: every route is checked with Valhalla `trace_attributes` (online, or on the phone offline).
  The preview lists **ferries first**, then the longest bridges and tunnels (≥ 300 m, with names when OSM has them).
  Zoomi doesn't know ferry schedules or prices and says so. Note: Valhalla doesn't say what a bridge crosses,
  so a listed bridge may span a valley or railway, not water.
- **Unreachable destinations**: if routing fails ("no path") or the route ends more than 2 km (car) / 1 km (bike, walk)
  from the destination, because Valhalla snaps islands or sea points to the nearest road, the card says
  "Can't reach this destination by car" and asks how to travel instead: public transport or walking, plus the
  **nearest airports** to you and to the destination as information only (OSM `aeroway=aerodrome` via Photon).
  There are no flight schedules or prices.

### Milestone (c): offline + heading
- **Offline regions** (Settings → Offline regions): map (PMTiles) + routing network (Valhalla tile extract) per region.
  Download from a catalog URL (resumable, SHA-256 verified) or "Import region folder…" (pick a folder, e.g. in Downloads,
  with Android's folder picker; files are copied and verified). Copying straight into `Android/data/…` doesn't work on
  Android 11+: the app can't read folders created there by other users (found on the S24 Ultra). Inside a region the map always comes
  from the phone. Map labels use fonts bundled in the app.
- **Offline routing / rerouting**: the same request goes to the online server when connected, or to Valhalla running
  on the phone (valhalla-mobile) when offline or when the server is unreachable. Losing signal mid-trip keeps guidance
  running, and reroutes are computed on the phone inside a downloaded region. The preview says "offline route".
- **Heading without a compass**: GNSS course when moving (≥ 2.5 m/s, accurate course); when slow or stopped, the gyroscope's
  rotation around the gravity axis (works with any phone mount) from the last good course. A deadband stops drift while parked.
  The magnetometer is not used at all, because the car's metal and mount magnets make it unreliable.
  If uncertainty grows past 60°, the arrow is replaced by a dot rather than showing a possibly wrong direction.
- **Dead reckoning** in tunnels / GPS dropouts while navigating: the position keeps moving along the route at the last
  speed (max 120 s / 4 km), shown as "GPS lost · position estimated", with a growing accuracy radius so it never
  triggers a false off-route reroute. Instructions keep advancing.
- Status pills: "Offline · using downloaded region" / "Offline · no region here".

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

## Sharper satellite view (optional, your own free key)

The free default imagery (EOxCloudless, Sentinel-2) is ~10 m per pixel and blurry at street level.
No sharp imagery is both keyless and licensed for apps like Zoomi (Google/Bing are off-limits, Esri's keyless
endpoint isn't allowed for non-Esri apps). Two options with **your own** free key (Zoomi ships no key):

**Recommended: MapTiler Satellite** (~1–2 m/px in Romania; 5–50 cm aerial in much of Western Europe)
1. Create a free account at <https://www.maptiler.com> (free plan: personal/non-commercial, no credit card,
   100,000 requests/month, then the service pauses until next month; it is never billed).
2. Copy your API key from the account page.
3. In Zoomi: Settings → Satellite view → *MapTiler Satellite* → paste → *Test & save key*. Zoomi downloads one
   tile to confirm the key works before saving it. The map then shows the MapTiler logo and "© MapTiler" as required.

**Sharper in some cities but may bill: Esri World Imagery**

1. Create a free account at <https://location.arcgis.com> (ArcGIS Location Platform). Check its billing settings:
   the free tier is 2 million basemap tiles per month and usage beyond that is billed.
2. In the developer dashboard, create an API key with the **basemaps** privilege.
3. In Zoomi: Settings → Satellite view → *Esri World Imagery (your own key)* → paste the key → Save key.

The key is stored only on the phone. Zoomi shows Esri's required credits ("Powered by Esri", data sources) on the map.
Esri's terms don't allow exporting this imagery for offline use, so it's online only.

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
   `keytool -genkeypair -keystore zoomi-release.jks -storetype PKCS12 -alias zoomi -keyalg RSA -keysize 4096 -validity 36500`
2. Create `keystore.properties` next to `settings.gradle.kts` (git-ignored) with `storeFile=`, `storePassword=`,
   `keyAlias=zoomi`, `keyPassword=`.
3. Build: `./gradlew :app:assembleRelease` gives a signed `app/build/outputs/apk/release/app-release.apk` (ARM only).
4. Optional private build with your own MapTiler key preset: `-Pzoomi.maptilerKey=...` (don't publish that APK:
   anyone can read the key out of it).

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
| Transitous (MOTIS 2), api.transitous.org | public transport journeys | volunteer-run, FOSS/non-commercial only; requires User-Agent with contact and a link to transitous.org/sources |
| valhalla-mobile (Rallista) 0.6.4 / Valhalla 3.9.1 | on-device routing | MIT |
| Planetiler 0.10.2 (OpenMapTiles profile) | building offline map files | Apache-2.0 (tool); output ODbL |
| Natural Earth | low-zoom map data in offline files | public domain |
| EOxCloudless 2024 (EOX IT Services GmbH), tiles.maps.eox.at | satellite view imagery | CC BY-NC-SA 4.0 for non-commercial use; attribution "EOxCloudless https://cloudless.eox.at by EOX IT Services GmbH (Contains modified Copernicus Sentinel data 2024)" shown on the map |
| Noto Sans glyphs (bundled) | map labels | SIL Open Font License 1.1 (`app/src/main/assets/glyphs/OFL.txt`) |

Attribution is visible on the map (bottom-left "© OpenStreetMap") and in Settings → About.

## Privacy policy (current build)

- Zoomi collects **no** personal data, has no analytics, no crash reporting, no ads, no account.
- Location is processed on the device. It leaves the phone only as described below, and only when you use a feature.
- Network traffic (every server also sees your IP address, as with any web request):
  - `tiles.openfreemap.org`: map tiles and fonts (reveals the area you view).
  - Search server (default `photon.komoot.io`): the text you type and your position **rounded to ~1 km** (for ranking).
    Long-press sends the pressed point for reverse geocoding.
  - Routing server (default `valhalla1.openstreetmap.de`): start point (your position, accuracy and heading) and destination
    of each route request, including automatic reroutes, plus the route shape for the bridge/ferry/tunnel check.
  - `api.transitous.org`: start and destination of public transport searches (only when you pick Transit).
  - Search server: when a destination is unreachable, two airport lookups near you and near the destination.
- Zoomi has no server of its own yet; nothing is stored about you anywhere except on your phone.
- Settings and saved places are stored locally on the device. `allowBackup` is off.

This section will be updated, before release, for every milestone that adds a network feature.

## Known limitations

- Satellite view: the free default (Sentinel-2) is ~10 m per pixel: good for landscapes, blurry for streets and houses,
  unlike Google's sub-metre imagery. Free sharp imagery for apps isn't available without an account/key
  (e.g. Esri World Imagery needs an ArcGIS account; Google/Bing/Mapbox are paid or restricted). Online only.

- Public transport: online only, coverage varies by city/operator, no turn-by-turn, no ticket prices.
- Before a public release, set a contact in the User-Agent (`-Pzoomi.contact=...`): Transitous and FOSSGIS require one.

- Offline maps: Europe is built; other continents only after someone runs the workflow for them. Countries whose
  files are too big for a free GitHub runner may fail to build (reported in the Actions log). Offline routes work
  inside one country at a time (no cross-border offline routing); online routing has no such limit.
- Search needs internet (no offline address search yet).
- Dead reckoning needs a route (navigation active); outside navigation the dot simply stays put during GPS loss.
  It assumes constant speed, so in a long tunnel with traffic the estimate runs ahead or behind until GPS returns.
- Gyro heading drifts a few degrees per minute while turning slowly without GPS; it resets on the next good GPS course.
- The release APK is ARM only; the debug APK (~133 MB) also has x86 libraries for emulators.

- ETAs don't include live traffic (milestone g), so rush-hour estimates will be optimistic.
- Default servers are community-run with strict limits (1 req/s). Fine for personal use and testing; a public release
  should use Zoomi's own routing server (planned on Oracle Always Free at milestone e). FOSSGIS also requires an operator
  contact email on the app's website before public distribution.
- Speed limits and lane arrows only appear where OpenStreetMap has `maxspeed` / `turn:lanes`.
- Voice quality depends on the installed Android speech engine (Google's by default when installed).
- Tunnel colours depend on OSM having the road tagged `tunnel=yes`; underground car parks rely on lost GPS + darkness.
- The light sensor on most phones faces the screen side; in a mount it may be shaded. That's why it never switches the theme alone.

## Roadmap

(a) map + location + theme · (b) online routing + turn-by-turn · (c) offline regions, offline routing, heading fix ·
(d) bike/walk/public transport + water crossings · (e) optional account + settings sync · (f) crowd alerts · (g) traffic estimation.

## Licence

GPL-3.0-or-later. See `LICENSE`.
