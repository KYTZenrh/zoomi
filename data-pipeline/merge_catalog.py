#!/usr/bin/env python3
"""Merges every <id>.region.json in a folder into catalog.json ({"regions": [...]}, sorted by group then name)."""
import glob, json, os, sys

folder, out = sys.argv[1], sys.argv[2]
regions = []
for p in glob.glob(os.path.join(folder, "*.region.json")):
    with open(p, encoding="utf-8") as f:
        regions.append(json.load(f))
regions.sort(key=lambda r: (r.get("group", ""), r["name"]))
with open(out, "w", encoding="utf-8") as f:
    json.dump({"regions": regions}, f, ensure_ascii=False, indent=1)
print(f"{len(regions)} regions")
