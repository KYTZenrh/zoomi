# Changelog

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
