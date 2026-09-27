"""Extract actual bounded street faces and reserve terrain-following public parks."""
import math
from collections import Counter


def contains(polygon, x, z):
    inside = False
    for a, b in zip(polygon, polygon[1:] + polygon[:1]):
        if (a["z"] > z) != (b["z"] > z):
            crossing = a["x"] + (z - a["z"]) * (b["x"] - a["x"]) / (b["z"] - a["z"])
            if x < crossing:
                inside = not inside
    return inside


def street_faces(roads, step):
    graph = {}
    for road in roads:
        for a, b in zip(road["points"], road["points"][1:]):
            count = max(1, round(math.hypot(b["x"] - a["x"], b["z"] - a["z"]) / step))
            points = [(round(a["x"] + (b["x"] - a["x"]) * j / count, 4),
                       round(a["z"] + (b["z"] - a["z"]) * j / count, 4)) for j in range(count + 1)]
            for u, v in zip(points, points[1:]):
                graph.setdefault(u, set()).add(v)
                graph.setdefault(v, set()).add(u)
    order = {u: sorted(vs, key=lambda v: math.atan2(v[1] - u[1], v[0] - u[0])) for u, vs in graph.items()}
    visited, faces = set(), []
    for origin in sorted(graph):
        for target in order[origin]:
            if (origin, target) in visited:
                continue
            u, v, polygon = origin, target, []
            while (u, v) not in visited:
                visited.add((u, v))
                polygon.append(u)
                around = order[v]
                u, v = v, around[(around.index(u) - 1) % len(around)]
            area = sum(a[0] * b[1] - b[0] * a[1] for a, b in zip(polygon, polygon[1:] + polygon[:1])) / 2
            # Negative winding is the unbounded outside face; tiny islands are
            # junction geometry, not buildable city blocks.
            if area < 1200:
                continue
            faces.append([dict(x=x, z=z) for x, z in polygon])
    return faces


def finalize_districts(city):
    field = city["terrain"]
    step, n = field["step"], field["resolution"]
    blocks = []
    for polygon in street_faces(city["roads"], step):
        xs, zs = [p["x"] for p in polygon], [p["z"] for p in polygon]
        x, z, w, d = min(xs), min(zs), max(xs) - min(xs), max(zs) - min(zs)
        blocks.append(dict(id=len(blocks), x=x, z=z, w=w, d=d, polygon=polygon,
                           zone="park", baseY=field["modified"][round(z / step) * n + round(x / step)]))
    zone_counts = [Counter() for _ in blocks]
    for building in city["buildings"]:
        cx, cz = building["x"] + building["w"] / 2, building["z"] + building["d"] / 2
        building["blockId"] = None  # Perimeter frontage can lie outside the enclosed network.
        for block in blocks:
            if block["x"] <= cx <= block["x"] + block["w"] and block["z"] <= cz <= block["z"] + block["d"] and contains(block["polygon"], cx, cz):
                building["blockId"] = block["id"]
                zone_counts[block["id"]][building["zone"]] += 1
                break
    for block, counts in zip(blocks, zone_counts):
        if counts:
            block["zone"] = counts.most_common(1)[0][0]

    blocked = set(i for i, wet in enumerate(field["waterMask"]) if wet)

    def reserve(x, z, w, d, margin):
        for iz in range(max(0, math.floor((z - margin) / step)), min(n, math.ceil((z + d + margin) / step) + 1)):
            for ix in range(max(0, math.floor((x - margin) / step)), min(n, math.ceil((x + w + margin) / step) + 1)):
                blocked.add(iz * n + ix)

    for building in city["buildings"]:
        p = building.get("platform", building)
        reserve(p["x"], p["z"], p["w"], p["d"], 2)
    for road in city["roads"]:
        for a, b in zip(road["points"], road["points"][1:]):
            reserve(min(a["x"], b["x"]), min(a["z"], b["z"]), abs(b["x"] - a["x"]), abs(b["z"] - a["z"]), road["width"] / 2 + 2)
    candidates = []
    for z in range(24, city["size"] - 48, 12):
        for x in range(24, city["size"] - 48, 12):
            count = math.ceil(28 / step) + 1
            cells = [(z // step + dz) * n + x // step + dx for dz in range(count) for dx in range(count)]
            if any(i in blocked for i in cells):
                continue
            heights = [field["modified"][i] for i in cells]
            relief = max(heights) - min(heights)
            if relief <= 4:
                candidates.append((x, z, relief, heights[0]))
    parks = []
    for target_x, target_z in ((150, 340), (600, 265), (260, 610)):
        tx, tz = target_x * city["size"] / 720, target_z * city["size"] / 720
        choices = [p for p in candidates if all(math.hypot(p[0] - q["x"], p[1] - q["z"]) > 90 for q in parks)]
        if choices:
            x, z, _, y = min(choices, key=lambda p: (p[0] - tx) ** 2 + (p[1] - tz) ** 2 + p[2] * 100)
            parks.append(dict(x=x, z=z, w=28, d=28, baseY=y, kind="park"))
    city["blocks"], city["parks"] = blocks, parks
    city["metrics"]["blockCount"] = len(blocks)
    city["metrics"]["parkAreaRatio"] = round(sum(p["w"] * p["d"] for p in parks) / city["size"] ** 2, 4)
