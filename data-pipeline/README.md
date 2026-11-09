# Offline region data pipeline

Builds one Zoomi offline region (map + routing) from a Geofabrik OSM extract.
Tested on Windows 10 with the versions below (2026-11-09).

| Tool | Version | Source |
|---|---|---|
| Planetiler (OpenMapTiles profile) | 0.10.2 | github.com/onthegomap/planetiler releases (SHA-256 checked) |
| Valhalla tools (pyvalhalla wheel) | 3.9.1 | PyPI `pyvalhalla` (SHA-256 checked); same Valhalla version as the app's valhalla-mobile 0.6.4 |
| JDK | Temurin 21 | adoptium.net |

## Steps (Romania example)

```bash
# 1. OSM extract (check the .md5 published next to it)
curl -O https://download.geofabrik.de/europe/romania-latest.osm.pbf

# 2. Map tiles (downloads Natural Earth, lake centerlines and OSM water polygons into data/sources)
java -Xmx10g -jar planetiler.jar --osm-path=romania-latest.osm.pbf --output=romania.pmtiles --download

# 3. Routing tiles (on Windows put site-packages/pyvalhalla.libs on PATH so the .exe tools find their DLLs)
python valhalla/valhalla_build_config.py --mjolnir-tile-dir tiles --mjolnir-tile-extract romania-routing.tar \
    --mjolnir-admin tiles/admins.sqlite --mjolnir-timezone tiles/timezones.sqlite > valhalla.json
valhalla_build_admins -c valhalla.json romania-latest.osm.pbf
valhalla_build_tiles  -c valhalla.json romania-latest.osm.pbf
python valhalla/valhalla_build_extract.py -c valhalla.json -v

# 4. Package (bbox from the PMTiles header, sizes + SHA-256, region.json + catalog.json)
python package_region.py --id romania --name Romania --osm-date 2026-11-09 \
    --map romania.pmtiles --routing romania-routing.tar --valhalla-version 3.9.1 --out ./regions
```

`empty_water_polygons.py` writes an empty stand-in for the OSM water polygons. Use it **only** when
osmdata.openstreetmap.de is unreachable; the open sea is then missing from the map (lakes and rivers
are unaffected). The first Romania build (2026-11-09) used it because that host timed out.

Timezones (`valhalla_build_timezones`) are not built yet; Valhalla then can't do time-dependent routing.

Licences: the outputs are derived from OpenStreetMap and are distributed under the ODbL 1.0
("© OpenStreetMap contributors"); the vector tile schema is OpenMapTiles (CC-BY 4.0).
