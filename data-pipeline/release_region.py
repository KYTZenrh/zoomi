#!/usr/bin/env python3
"""
Turns one built region (map.pmtiles + routing.tar) into flat release assets for the region catalog:

  <id>-map.pmtiles            (or <id>-map.pmtiles.001, .002 ... when larger than --split-mb)
  <id>-routing.tar            (same)
  <id>.region.json            (manifest with download URLs, sizes and SHA-256 of the whole file)

GitHub release assets must be < 2 GiB each, so big files are split; the app downloads the parts
in order into one file and checks the SHA-256 of the result. Files are MOVED into --out to save disk.
"""
import argparse, hashlib, json, os, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from package_region import pmtiles_bbox  # noqa: E402


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 22), b""):
            h.update(chunk)
    return h.hexdigest()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--id", required=True)
    ap.add_argument("--name", required=True)
    ap.add_argument("--group", required=True, help="continent, e.g. europe")
    ap.add_argument("--osm-date", required=True)
    ap.add_argument("--map", required=True)
    ap.add_argument("--routing", required=True)
    ap.add_argument("--valhalla-version", required=True)
    ap.add_argument("--base-url", required=True, help="URL prefix of the release assets")
    ap.add_argument("--out", required=True)
    ap.add_argument("--split-mb", type=int, default=1900)
    a = ap.parse_args()

    os.makedirs(a.out, exist_ok=True)
    bbox = pmtiles_bbox(a.map)
    limit = a.split_mb * 1024 * 1024
    files = []
    for src, name in ((a.map, "map.pmtiles"), (a.routing, "routing.tar")):
        size = os.path.getsize(src)
        digest = sha256(src)
        asset = f"{a.id}-{name}"
        entry = {"name": name, "size": size, "sha256": digest}
        if size <= limit:
            os.replace(src, os.path.join(a.out, asset))
            entry["url"] = f"{a.base_url}/{asset}"
        else:
            parts = []
            with open(src, "rb") as f:
                i = 1
                while True:
                    part = f"{asset}.{i:03d}"
                    written = 0
                    with open(os.path.join(a.out, part), "wb") as out:
                        while written < limit:
                            chunk = f.read(min(1 << 22, limit - written))
                            if not chunk:
                                break
                            out.write(chunk)
                            written += len(chunk)
                    if written == 0:
                        os.remove(os.path.join(a.out, part))
                        break
                    parts.append({"url": f"{a.base_url}/{part}", "size": written})
                    i += 1
            os.remove(src)
            entry["parts"] = parts
        files.append(entry)

    region = {"id": a.id, "name": a.name, "group": a.group, "bbox": bbox, "osmDate": a.osm_date,
              "valhallaVersion": a.valhalla_version, "files": files}
    with open(os.path.join(a.out, f"{a.id}.region.json"), "w", encoding="utf-8") as f:
        json.dump(region, f, ensure_ascii=False, indent=1)
    print(json.dumps(region, indent=1))


if __name__ == "__main__":
    main()
