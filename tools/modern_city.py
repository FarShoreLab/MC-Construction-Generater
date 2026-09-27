"""Deterministic road-first modern city study, in metre-sized world units.

Road hierarchy reserves the public network first; district demand then chooses
block uses and building types. The JSON geometry is independent of the viewer.
"""

from __future__ import annotations

import math
import random


SIZE = 720


def _rect(x, z, w, d, **attributes):
    return dict(x=round(x, 3), z=round(z, 3), w=round(w, 3),
                d=round(d, 3), **attributes)


def _street_axis(rng, regular):
    """Major avenues each contain one narrower, slightly offset cross street."""
    majors = [0] + [180 * i + (0 if regular else rng.uniform(-22, 22))
                    for i in range(1, 4)] + [SIZE]
    bands = []
    for i, center in enumerate(majors):
        width = 20 if i in (0, 4) else (24 if i == 2 else 18)
        bands.append((max(0, center - width / 2),
                      min(SIZE, center + width / 2), "arterial"))
        if i < 4:
            t = .5 if regular else rng.uniform(.42, .58)
            cross = center + (majors[i + 1] - center) * t
            kind = "collector" if i in (1, 2) else "local"
            width = 11 if kind == "collector" else 8
            bands.append((cross - width / 2, cross + width / 2, kind))
    return sorted(bands)


def _segments(start, length, target, gap, rng):
    """Divide an edge into viable plots, preserving a gap between neighbours."""
    count = max(1, int((length + gap) / (target + gap)))
    usable = length - (count - 1) * gap
    weights = [rng.uniform(.85, 1.15) for _ in range(count)]
    total = sum(weights)
    cursor = start
    for weight in weights:
        size = usable * weight / total
        yield cursor, size
        cursor += size + gap


def generate_city(seed=42, density=.75, layout="balanced", terrain="flat", max_edit=6):
    """Return one reproducible city; density ranges from .25 to 1.

    populationEstimate assumes 35 m² per resident and residential floor shares
    of 85% for residential blocks and 35% for mixed blocks; it is illustrative.
    """
    if type(seed) is not int or not 0 <= seed <= 2147483647:
        raise ValueError("seed must be an integer between 0 and 2147483647")
    if layout not in ("balanced", "polycentric", "grid"):
        raise ValueError("layout must be balanced, polycentric, or grid")
    density = float(density)
    if not math.isfinite(density) or not .25 <= density <= 1:
        raise ValueError("density must be between 0.25 and 1")
    if terrain not in ("flat", "hills", "river"):
        raise ValueError("terrain must be flat, hills, or river")
    if not math.isfinite(max_edit) or not 2 <= max_edit <= 10:
        raise ValueError("max_edit must be between 2 and 10")
    if terrain != "flat":
        from city_terrain import generate_terrain_city
        return generate_terrain_city(seed, density, layout, terrain, max_edit)
    rng = random.Random(seed)
    centers = ({"balanced": [(360, 345, 1)],
                "polycentric": [(215, 225, 1), (520, 470, .95), (225, 555, .7)],
                "grid": []}[layout])
    xroads = _street_axis(rng, layout == "grid")
    zroads = _street_axis(rng, layout == "grid")
    roads = [_rect(a, 0, b - a, SIZE, kind=kind) for a, b, kind in xroads]
    roads += [_rect(0, a, SIZE, b - a, kind=kind) for a, b, kind in zroads]
    blocks, buildings, parks = [], [], []

    def demand(x, z):
        if layout == "grid":
            return .5
        radius = 175 if layout == "polycentric" else 210
        return max(strength * math.exp(-((x - cx) ** 2 + (z - cz) ** 2)
                                      / (2 * radius ** 2))
                   for cx, cz, strength in centers)

    def add_building(block, x, z, w, d, style, influence):
        base = 9 + density * 19 + influence ** 2 * density * 68
        if style == "tower":
            base = 28 + density * (35 + influence ** 2 * 115)
        elif style == "slab":
            base *= .68
        elif style == "civic":
            base = 12 + density * 10
        height = round(max(9, base * rng.uniform(.78, 1.2)) / 3) * 3
        buildings.append(_rect(x, z, w, d, id=len(buildings),
                               blockId=block["id"], height=height,
                               zone=block["zone"], style=style))

    for iz, (_, zstart, _) in enumerate(zroads[:-1]):
        zend = zroads[iz + 1][0]
        for ix, (_, xstart, _) in enumerate(xroads[:-1]):
            xend = xroads[ix + 1][0]
            w, d = xend - xstart, zend - zstart
            cx, cz = (xstart + xend) / 2, (zstart + zend) / 2
            influence = demand(cx, cz)
            # A civic square beside the central avenue and distributed parks
            # are explicit reservations, so dense settings retain public land.
            park = (ix, iz) in ((2, 3), (5, 1), (6, 6))
            if not park:
                park = rng.random() < .045 + (1 - influence) * .035
            if park:
                zone = "park"
            elif (ix, iz) == (4, 3):
                zone = "civic"
            elif influence > .65 and rng.random() < .58:
                zone = "office"
            elif influence > .38 or rng.random() < .2:
                zone = "mixed"
            else:
                zone = "residential"
            block = _rect(xstart, zstart, w, d, id=len(blocks), zone=zone)
            blocks.append(block)
            setback = 3.5 if zone in ("mixed", "office") else 5
            x, z, w, d = xstart + setback, zstart + setback, w - 2 * setback, d - 2 * setback
            if zone == "park":
                parks.append(_rect(x, z, w, d, kind="park"))
                continue
            if zone == "civic":
                add_building(block, x + w * .13, z + d * .25,
                             w * .74, d * .45, "civic", influence)
                continue
            tower_block = zone == "office" and influence > .68 and density > .45
            if tower_block:
                # Towers are placed on separated plots around a shared square.
                gap = 9
                for row in range(2):
                    for col in range(2):
                        pw, pd = (w - gap) / 2, (d - gap) / 2
                        bw, bd = pw * rng.uniform(.7, .9), pd * rng.uniform(.7, .9)
                        add_building(block, x + col * (pw + gap) + (pw - bw) / 2,
                                     z + row * (pd + gap) + (pd - bd) / 2,
                                     bw, bd, "tower", influence)
                continue
            if zone == "residential" and rng.random() < .55:
                # Parallel apartment slabs retain open gaps between each row.
                count = 2 if density < .5 else 3
                gap = 8 + (1 - density) * 5
                slab_depth = min(15, (d - (count - 1) * gap) / count)
                occupied = count * slab_depth + (count - 1) * gap
                for row in range(count):
                    add_building(block, x + 2, z + (d - occupied) / 2 + row * (slab_depth + gap),
                                 w - 4, slab_depth, "slab", influence)
                continue
            # Street walls form a complete perimeter while the central courtyard
            # remains open. Side plots exclude the north and south corner plots.
            depth = min(rng.uniform(13, 17), min(w, d) * .245)
            gap = 2.5 + (1 - density) * 4
            target = 17 + (1 - density) * 12
            for edge_z in (z, z + d - depth):
                for bx, bw in _segments(x, w, target, gap, rng):
                    add_building(block, bx, edge_z, bw, depth, "perimeter", influence)
            inner = d - 2 * (depth + gap)
            for edge_x in (x, x + w - depth):
                for bz, bd in _segments(z + depth + gap, inner, target, gap, rng):
                    add_building(block, edge_x, bz, depth, bd, "midrise", influence)
            parks.append(_rect(x + depth + 3, z + depth + 3,
                               w - 2 * depth - 6, d - 2 * depth - 6,
                               kind="courtyard"))

    xarea = sum(b - a for a, b, _ in xroads) * SIZE
    zarea = sum(b - a for a, b, _ in zroads) * SIZE
    road_area = xarea + zarea - xarea * zarea / SIZE ** 2
    metrics = dict(buildingCount=len(buildings), blockCount=len(blocks),
                   roadAreaRatio=round(road_area / SIZE ** 2, 4),
                   buildingCoverage=round(sum(b["w"] * b["d"] for b in buildings) / SIZE ** 2, 4),
                   populationEstimate=round(sum(b["w"] * b["d"] * (b["height"] / 3)
                                                * (.85 if b["zone"] == "residential" else .35)
                                                / 35 for b in buildings
                                                if b["zone"] in ("residential", "mixed"))),
                   maxHeight=max(b["height"] for b in buildings),
                   parkAreaRatio=round(sum(p["w"] * p["d"] for p in parks) / SIZE ** 2, 4))
    return dict(seed=seed, density=density, layout=layout, size=SIZE, roads=roads,
                blocks=blocks, buildings=buildings, parks=parks,
                centers=[dict(x=x, z=z, strength=s) for x, z, s in centers], metrics=metrics)
