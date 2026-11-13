#!/usr/bin/env python3
"""
Builds app/src/main/assets/dictate/places.tsv, the offline place book behind MaPlaces (Marko, 13.11.2026: "if I start to
type street, then it just search in the database of all the streets ... If I type the name of the city, it understands").

Sources (open data, downloaded once by hand, nothing is fetched by the phone):
  OpenStreetMap Croatia   https://osm.download.movisda.io/admin/HR-latest.osm.pbf  (ODbL, (c) OpenStreetMap contributors)
  GeoNames HR postcodes   https://download.geonames.org/export/zip/HR.zip          (CC BY 4.0)
  GeoNames HR places      https://download.geonames.org/export/dump/HR.zip         (CC BY 4.0)
  GeoNames world cities   https://download.geonames.org/export/dump/cities15000.zip (CC BY 4.0)

    pip install osmium
    python3 tools/places/build_places.py DIR      # DIR holds HR.osm.pbf, hrzip/HR.txt, hrdump/HR.txt, c15/cities15000.txt

Rows (tab separated):  s  street  city[,city…]  weight        a street, the towns it has addresses in, most first
                       c  place   postcode      weight        a Croatian town or village with its post code
                       w  city    country       population    a world city (15 000 people or more)
"""
import collections, os, sys

import osmium

d = sys.argv[1]
out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(__file__), "../../app/src/main/assets/dictate/places.tsv")

pairs = collections.Counter()
codes = collections.defaultdict(collections.Counter)   # town -> post codes on its addresses
roads = collections.Counter()
osm_place = {}


class H(osmium.SimpleHandler):
    def addr(self, t):
        s = t.get("addr:street")
        c = t.get("addr:city") or t.get("addr:place")
        if s and c:
            pairs[(s.strip(), c.strip())] += 1
        pc = t.get("addr:postcode")
        if pc and c and pc.strip().isdigit():
            codes[c.strip()][pc.strip()] += 1

    def node(self, n):
        self.addr(n.tags)
        p = n.tags.get("place")
        if p in ("city", "town", "village", "hamlet", "suburb") and n.tags.get("name"):
            osm_place[n.tags["name"].strip()] = p

    def way(self, w):
        self.addr(w.tags)
        h = w.tags.get("highway")
        if h and w.tags.get("name") and h not in ("motorway", "trunk", "motorway_link", "trunk_link", "proposed", "construction"):
            roads[w.tags["name"].strip()] += 1


H().apply_file(os.path.join(d, "HR.osm.pbf"))

clean = lambda s: s.replace("\t", " ").replace("\n", " ").strip()
rows = []

# Streets: the towns each one has house numbers in; roads with a name but no address join with no town.
towns = collections.defaultdict(collections.Counter)
for (s, c), n in pairs.items():
    towns[s][c] += n
for s, cs in towns.items():
    total = sum(cs.values()) + roads.get(s, 0)
    rows.append(("s", s, ",".join(c for c, _ in cs.most_common(6)), total))
for s, n in roads.items():
    if s not in towns and len(s) >= 3 and any(ch.isalpha() for ch in s):
        rows.append(("s", s, "", n))

# Croatian places with post codes (GeoNames zip: country, code, place, county, ...).
pop = {}
for line in open(os.path.join(d, "hrdump/HR.txt"), encoding="utf-8"):
    f = line.rstrip("\n").split("\t")
    if len(f) > 14 and f[6] == "P":
        pop[f[1]] = max(pop.get(f[1], 0), int(f[14] or 0))
weight = {"city": 100000, "town": 10000, "village": 500, "hamlet": 50, "suburb": 2000}
seen = set()
for line in open(os.path.join(d, "hrzip/HR.txt"), encoding="utf-8"):
    f = line.rstrip("\n").split("\t")
    name, code = clean(f[2]), f[1]
    if (name, code) in seen:
        continue
    seen.add((name, code))
    rows.append(("c", name, code, max(pop.get(name, 0), weight.get(osm_place.get(name, ""), 1))))

# Towns OpenStreetMap knows that the post code list splits up or lacks ("Zagreb" there is only its districts).
have = {r[1] for r in rows if r[0] == "c"}
for name, kind in osm_place.items():
    if name not in have and kind in ("city", "town", "village"):
        code = codes[name].most_common(1)[0][0] if codes.get(name) else ""
        rows.append(("c", clean(name), code, max(pop.get(name, 0), weight[kind])))

for line in open(os.path.join(d, "c15/cities15000.txt"), encoding="utf-8"):
    f = line.rstrip("\n").split("\t")
    rows.append(("w", clean(f[1]), f[8], int(f[14] or 0)))

rows.sort(key=lambda r: (r[0], -r[3], r[1]))
with open(out, "w", encoding="utf-8") as o:
    o.write("# MaPlaces: (c) OpenStreetMap contributors (ODbL); GeoNames (CC BY 4.0). Built by tools/places/build_places.py\n")
    for r in rows:
        o.write("\t".join(map(str, r)) + "\n")
print(out, len(rows), collections.Counter(r[0] for r in rows))
