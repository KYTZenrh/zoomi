# Changelog

## [Unreleased] 0.3.0-c, milestone (c)

### Added
- Offline regions: install from a catalog (resumable download, SHA-256 check) or import a folder via the system folder
  picker (copy + SHA-256 check); delete.
- Map uses the local PMTiles file inside a downloaded region; label glyphs bundled in the app (Noto Sans, OFL).
- Hybrid routing: online Valhalla when connected, on-device Valhalla (valhalla-mobile 0.6.4 / Valhalla 3.9.1) when offline or
  the server is unreachable. Same request format and parser for both; used for reroutes too.
- Heading fusion: GNSS course + gyroscope yaw around gravity (mount-independent), no magnetometer, uncertainty-based withdrawal.
- Dead reckoning along the route during GPS loss while navigating (time/distance capped, accuracy grows).
- Offline / estimated-position status pills; "offline route" note in the route preview.
- `data-pipeline/`: build + package scripts and instructions (Planetiler 0.10.2, Valhalla 3.9.1).
- Unit tests: heading fusion, yaw-rate projection, dead reckoning, polyline projection, region manifests (62 total).

### Fixed (found on device)
- adb-pushed region folders in Android/data were unreadable by the app (Android 11+ storage rules): replaced with folder import.
- Network monitor reported "online" in airplane mode (re-read activeNetwork in onLost); now tracks validated networks per callback.

## [Unreleased] 0.2.0-b, milestone (b)

### Added
- Search with autocomplete via Photon (debounced, min 3 letters, 1 req/s), place-type labels, reverse geocoding on long-press.
- Online car routing via Valhalla (FOSSGIS server by default) with up to 2 alternatives, selectable on the map or as chips.
- Turn-by-turn navigation with Ferrostar 0.59.0: maneuver banner, lanes, Android TTS voice, OSM speed limits, current speed,
  automatic off-route rerouting, arrival screen, keep-screen-on, location foreground service with notification.
- Saved places (Home, Work, favourites), stored locally.
- Share ETA (destination + arrival time only).
- Settings: editable routing / search server URLs. About: new credits and "report a map error" (fixthemap) link.
- Shared HTTP client: identifying User-Agent, 1 req/s limiter for public community servers.
- 21 new unit tests (formatting, locale mapping, rate limiter, Photon parsing, saved places, ETA text, maneuver icons); 42 total.

### Changed
- Core library desugaring enabled (required by Ferrostar). Debug APK is now ~99 MB (all CPU architectures).

## [Unreleased] 0.1.0-a, milestone (a)

### Added
- Project setup: Kotlin, Jetpack Compose, MapLibre Native Android 13.7.1, minSdk 29, targetSdk 36, compileSdk 37 (required by core-ktx 1.19.1), GPL-3.0-or-later.
- `core` module (pure Kotlin): `SolarCalculator`, `AutoThemeController` (hysteresis + dwell), `TunnelDetector`, `CourseTracker`.
- Zoomi map style (light/dark) over OpenFreeMap / OpenMapTiles vector tiles.
- GPS-only location, follow camera, heading-up / north-up, own position marker.
- Light / Dark / Auto theme with a live "why" diagnostics panel; tunnel-aware Auto mode.
- About screen with OpenStreetMap and library attributions; persistent "© OpenStreetMap" on the map.
- 25 unit tests for the theme and course logic.
- Launcher icon: a "Z" whose diagonal is a road, with an amber navigation chevron (`docs/brand/zoomi-logo.svg`).

### Fixed (found on Galaxy S24 Ultra, Android 14)
- Auto theme got stuck in "tunnel" mode indoors: a mapped underground passage under the position plus
  indoor light (29 lux) triggered entry, and nothing allowed exit while the map wasn't following.
  Tunnel entry now requires recent vehicle motion (>= 4 m/s within 15 s), and 8 s of accurate GPS
  ends tunnel mode unless the map positively shows a tunnel. Tunnel search box reduced to 6 dp.
- MapLibre's own attribution button overlapped the "© OpenStreetMap" label; it is now hidden and
  the label opens the full credits screen.
