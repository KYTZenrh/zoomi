#!/usr/bin/env python3
"""
Prints a GitHub Actions matrix (JSON) of Geofabrik extracts to build.
  pick_regions.py <parent> [ids]   parent = continent id (europe, asia, ...); ids = optional comma list
Only direct children of the continent (countries) are used; their sub-regions are skipped.
Russia is its own top level at Geofabrik and is accepted as a parent too.
"""
import json, sys, urllib.request

# Geofabrik bundles of several countries, or a part of a country that also has its own file.
EXCLUDE = {"alps", "britain-and-ireland", "dach", "great-britain", "us"}

parent = sys.argv[1]
only = {x.strip() for x in (sys.argv[2] if len(sys.argv) > 2 else "").split(",") if x.strip()}
req = urllib.request.Request("https://download.geofabrik.de/index-v1-nogeom.json", headers={"User-Agent": "zoomi-region-builder"})
features = [f["properties"] for f in json.load(urllib.request.urlopen(req))["features"]]
if parent == "russia":
    picked = [p for p in features if p["id"] == "russia"]
    group = "europe"
else:
    picked = [p for p in features if p.get("parent") == parent and p["id"] not in EXCLUDE]
    group = parent
if only:
    picked = [p for p in picked if p["id"] in only]
matrix = [{"id": p["id"], "name": p["name"], "group": group, "pbf": p["urls"]["pbf"]} for p in picked if "pbf" in p.get("urls", {})]
print(json.dumps({"include": matrix}))
