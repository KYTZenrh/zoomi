#!/usr/bin/env python3
"""
Package an offline region for Zoomi.

Input:  a PMTiles map file (OpenMapTiles schema, e.g. from Planetiler) and a Valhalla tile
        extract (.tar from valhalla_build_extract), both built from the same OSM extract.
Output: <out>/<id>/{map.pmtiles, routing.tar, region.json} and an entry in <out>/catalog.json.

The bbox is read from the PMTiles header, so it always matches the map data.
Usage:
  python package_region.py --id romania --name Romania --osm-date 2026-11-09 \
      --map romania.pmtiles --routing romania-routing.tar --valhalla-version 3.9.1 \
      --out ./regions [--base-url https://example.org/zoomi/regions]
"""
import argparse, hashlib, json, os, shutil, struct


def pmtiles_bbox(path):
    with open(path, "rb") as f:
        h = f.read(127)
    if h[:7] != b"PMTiles" or h[7] != 3:
        raise SystemExit(f"{path}: not a PMTiles v3 file")
    min_lon, min_lat, max_lon, max_lat = struct.unpack_from("<iiii", h, 102)
    return [round(v / 1e7, 5) for v in (min_lon, min_lat, max_lon, max_lat)]


def sha256(path):
    d = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            d.update(chunk)
    return d.hexdigest()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--id", required=True)
    ap.add_argument("--name", required=True)
    ap.add_argument("--osm-date", required=True)
    ap.add_argument("--map", required=True)
    ap.add_argument("--routing", required=True)
    ap.add_argument("--valhalla-version", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--base-url", default=None, help="public URL of --out (for catalog download links)")
    a = ap.parse_args()

    region_dir = os.path.join(a.out, a.id)
    os.makedirs(region_dir, exist_ok=True)
    files = []
    for src, name in ((a.map, "map.pmtiles"), (a.routing, "routing.tar")):
        dst = os.path.join(region_dir, name)
        if os.path.abspath(src) != os.path.abspath(dst):
            shutil.copyfile(src, dst)
        entry = {"name": name, "size": os.path.getsize(dst), "sha256": sha256(dst)}
        if a.base_url:
            entry["url"] = f"{a.base_url.rstrip('/')}/{a.id}/{name}"
        files.append(entry)

    region = {
        "id": a.id, "name": a.name, "bbox": pmtiles_bbox(os.path.join(region_dir, "map.pmtiles")),
        "osmDate": a.osm_date, "valhallaVersion": a.valhalla_version, "files": files,
    }
    with open(os.path.join(region_dir, "region.json"), "w", encoding="utf-8") as f:
        json.dump(region, f, ensure_ascii=False, indent=1)

    catalog_path = os.path.join(a.out, "catalog.json")
    catalog = {"regions": []}
    if os.path.exists(catalog_path):
        with open(catalog_path, encoding="utf-8") as f:
            catalog = json.load(f)
    catalog["regions"] = [r for r in catalog["regions"] if r.get("id") != a.id] + [region]
    with open(catalog_path, "w", encoding="utf-8") as f:
        json.dump(catalog, f, ensure_ascii=False, indent=1)
    print(json.dumps(region, indent=1))


if __name__ == "__main__":
    main()
