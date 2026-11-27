#!/usr/bin/env python3
"""
Generates app/src/main/java/io/github/dnicotra/ovinfode/country/CountryOutlines.kt: simplified
land outlines that tell the app which country a stop lies in when its id does not say.

Source: Natural Earth 1:10m admin-0 countries and admin-1 provinces (public domain,
https://www.naturalearthdata.com). Each country's polygons are simplified (Douglas-Peucker,
default 400 m) and stored as encoded polylines (precision 1e-5), a few kilobytes per country.

Besides countries (ISO 3166-1 codes such as NL), the regions in REGIONS can be generated
(ISO 3166-2 codes such as BE-VLG, the Flemish Region), from the provinces they consist of.

Usage, from the repository root:
    python3 tools/country_outlines.py NL DE IT LU BE BE-VLG BE-BRU > app/src/main/java/io/github/dnicotra/ovinfode/country/CountryOutlines.kt
Pass --geojson FILE / --admin1 FILE to use downloaded copies instead of fetching them (13 / 40 MB).
"""
import argparse
import json
import math
import sys
import urllib.request

SOURCE = "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_10m_admin_0_countries.geojson"
SOURCE_ADMIN1 = "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_10m_admin_1_states_provinces.geojson"

# Regions made of admin-1 provinces: code -> (country, value of the Natural Earth "region" property).
REGIONS = {
    "BE-VLG": ("BE", "Flemish"),
    "BE-WAL": ("BE", "Walloon"),
    "BE-BRU": ("BE", "Capital Region"),
}
EARTH_RADIUS_M = 6_371_000.0


def to_metres(lon, lat, ref_lat):
    k = math.cos(math.radians(ref_lat))
    return (math.radians(lon) * EARTH_RADIUS_M * k, math.radians(lat) * EARTH_RADIUS_M)


def simplify(ring, tolerance_m):
    """Douglas-Peucker on a closed ring of (lon, lat); returns the kept points (ring stays closed)."""
    ref = sum(p[1] for p in ring) / len(ring)
    xy = [to_metres(lon, lat, ref) for lon, lat in ring]

    def distance(i, a, b):
        (px, py), (ax, ay), (bx, by) = xy[i], xy[a], xy[b]
        dx, dy = bx - ax, by - ay
        if dx == 0 and dy == 0:
            return math.hypot(px - ax, py - ay)
        t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
        return math.hypot(px - (ax + t * dx), py - (ay + t * dy))

    keep = {0, len(ring) - 1}
    stack = [(0, len(ring) - 1)]
    while stack:
        start, end = stack.pop()
        best, index = -1.0, -1
        for i in range(start + 1, end):
            d = distance(i, start, end)
            if d > best:
                best, index = d, i
        if best > tolerance_m:
            keep.add(index)
            stack += [(start, index), (index, end)]
    return [ring[i] for i in sorted(keep)]


def area_km2(ring):
    ref = sum(p[1] for p in ring) / len(ring)
    xy = [to_metres(lon, lat, ref) for lon, lat in ring]
    return abs(sum(x1 * y2 - x2 * y1 for (x1, y1), (x2, y2) in zip(xy, xy[1:] + xy[:1]))) / 2 / 1e6


def encode(points):
    """Google encoded polyline of (lat, lon) points, precision 1e-5."""
    out, last_lat, last_lon = [], 0, 0
    for lat, lon in points:
        lat_e, lon_e = round(lat * 1e5), round(lon * 1e5)
        for delta in (lat_e - last_lat, lon_e - last_lon):
            value = ~(delta << 1) if delta < 0 else delta << 1
            while value >= 0x20:
                out.append(chr((0x20 | (value & 0x1F)) + 63))
                value >>= 5
            out.append(chr(value + 63))
        last_lat, last_lon = lat_e, lon_e
    return "".join(out)


def outer_rings(geometry):
    """Outer rings of a (multi)polygon, without the repeated first point."""
    polygons = geometry["coordinates"] if geometry["type"] == "MultiPolygon" else [geometry["coordinates"]]
    return [p[0][:-1] for p in polygons]


def outlines(features, code, tolerance_m, min_area_km2):
    feature = next((f for f in features if f["properties"].get("ISO_A2_EH") == code), None)
    if feature is None:
        sys.exit(f"no country with ISO code {code}")
    rings = outer_rings(feature["geometry"])
    return simplified(rings, tolerance_m, min_area_km2)


def region_outlines(features, code, tolerance_m, min_area_km2):
    country, region = REGIONS[code]
    provinces = [f for f in features if f["properties"].get("iso_a2") == country and f["properties"].get("region") == region]
    if not provinces:
        sys.exit(f"no provinces for region {code}")
    rings = [ring for f in provinces for ring in outer_rings(f["geometry"])]
    return simplified(rings, tolerance_m, min_area_km2)


def simplified(rings, tolerance_m, min_area_km2):
    rings = [r for r in rings if area_km2(r) >= min_area_km2]
    # Keep the part of the country around its largest polygon (drops e.g. the Caribbean Netherlands).
    main = max(rings, key=area_km2)
    centre = (sum(p[0] for p in main) / len(main), sum(p[1] for p in main) / len(main))
    near = [r for r in rings if math.dist(r[0], centre) < 15]  # degrees, roughly 1500 km
    result = []
    for ring in sorted(near, key=area_km2, reverse=True):
        simple = simplify(ring + [ring[0]], tolerance_m)[:-1]
        if len(simple) >= 3:
            result.append([(lat, lon) for lon, lat in simple])
    return result


def load(path, url):
    if path:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    with urllib.request.urlopen(url) as response:
        return json.load(response)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("codes", nargs="+", help="ISO 3166-1 alpha-2 codes, e.g. NL DE, or regions, e.g. BE-VLG")
    parser.add_argument("--geojson", help="local copy of ne_10m_admin_0_countries.geojson")
    parser.add_argument("--admin1", help="local copy of ne_10m_admin_1_states_provinces.geojson")
    parser.add_argument("--tolerance", type=float, default=400, help="simplification in metres (default 400)")
    parser.add_argument("--min-area", type=float, default=1, help="drop islands smaller than this, km² (default 1)")
    args = parser.parse_args()

    codes = [c.upper() for c in args.codes]
    for code in codes:
        if "-" in code and code not in REGIONS:
            sys.exit(f"unknown region {code}; known: {', '.join(REGIONS)}")
    data = load(args.geojson, SOURCE)
    admin1 = load(args.admin1, SOURCE_ADMIN1) if any("-" in c for c in codes) else None

    print("// Generated by tools/country_outlines.py — do not edit; run the script to add a country.")
    print("// Country borders: Natural Earth 1:10m admin-0 countries and admin-1 provinces (public domain),")
    print(f"// simplified to about {args.tolerance:.0f} m and stored as encoded polylines (precision 1e-5).")
    print()
    print("package io.github.dnicotra.ovinfode.country")
    print()
    print("internal object CountryOutlines {")
    for code in codes:
        if "-" in code:
            polygons = region_outlines(admin1["features"], code, args.tolerance, args.min_area)
        else:
            polygons = outlines(data["features"], code, args.tolerance, args.min_area)
        vertices = sum(len(p) for p in polygons)
        print(f"    /** {len(polygons)} polygons, {vertices} points. */")
        print(f"    val {code.replace('-', '_')}: List<String> = listOf(")
        for polygon in polygons:
            text = encode(polygon)
            lines = [text[i:i + 100] for i in range(0, len(text), 100)]
            print('        """')
            for line in lines:
                print("        " + line)
            print('        """,')
        print("    )")
    print("}")


if __name__ == "__main__":
    main()
