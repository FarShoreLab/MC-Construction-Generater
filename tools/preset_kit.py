"""Hand-authoring toolkit for Minecraft building presets.

Designs are written as explicit voxel primitives (footings, walls, openings, eaves,
pitched roofs, chimneys, porches, interiors) on a dense `[x][y][z]` grid, then emitted
as the native BuildingPreset JSON the Java planner consumes.

Conventions inherited from the runtime (`BuildingPreset.validateFootprint`):
  * origin is the north-west corner; +X east, +Z south, +Y up
  * y=0 is the foundation top, players stand from y=1, clear headroom is y=1..2
  * every cell of the footprint mask must resolve to a palette symbol
  * every cell outside the mask must be `minecraft:air`
  * the mask must be four-connected and the entrance must face out of the mask
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

AIR = "minecraft:air"
ROOT = Path(__file__).resolve().parents[1]
PRESET_DIR = ROOT / "core-planner/src/main/resources/presets"

DIRS = {"NORTH": (0, -1), "EAST": (1, 0), "SOUTH": (0, 1), "WEST": (-1, 0)}
OPPOSITE = {"NORTH": "SOUTH", "SOUTH": "NORTH", "EAST": "WEST", "WEST": "EAST"}
RUNS_ALONG = {"NORTH": "x", "SOUTH": "x", "EAST": "z", "WEST": "z"}


def _merge(block: str, props: str) -> str:
    """Append blockstate properties, replacing any the block already declares."""
    base, _, existing = block.partition("[")
    existing = existing.rstrip("]")
    directional = {"facing", "axis", "half"}
    if existing and any(pair.split("=")[0] not in directional for pair in existing.split(",")):
        raise ValueError(
            f"{block!r} is not a bare base block; composites that add blockstate need the plain block name")
    kept = []
    for pair in existing.split(",") if existing else []:
        if pair and pair.split("=")[0] not in {p.split("=")[0] for p in props.split(",")}:
            kept.append(pair)
    merged = ",".join(filter(None, kept + [props]))
    return f"{base}[{merged}]"


def stairs(block: str, facing: str, half: str = "bottom") -> str:
    """Blockstate for a stair whose raised back points opposite to `facing`."""
    return _merge(block, f"facing={facing.lower()},half={half},shape=straight,waterlogged=false")


def slab(block: str, kind: str = "bottom") -> str:
    return _merge(block, f"type={kind},waterlogged=false")


def trapdoor(block: str, facing: str, half: str = "top", open_: bool = False) -> str:
    return _merge(block, f"facing={facing.lower()},half={half},open={'true' if open_ else 'false'},"
                         f"powered=false,waterlogged=false")


def pane(block: str = "minecraft:glass_pane") -> str:
    return _merge(block, "waterlogged=false")


def log(block: str, axis: str = "y") -> str:
    return _merge(block, f"axis={axis}")


class Grid:
    """Dense voxel grid; untouched cells stay None so `validate` can flag escapes."""

    def __init__(self, size_x: int, size_y: int, size_z: int):
        self.sx, self.sy, self.sz = size_x, size_y, size_z
        self.cells: list[list[list[str | None]]] = [
            [[None for _ in range(size_z)] for _ in range(size_y)] for _ in range(size_x)
        ]

    def set(self, x: int, y: int, z: int, block: str | None) -> None:
        if 0 <= x < self.sx and 0 <= y < self.sy and 0 <= z < self.sz:
            self.cells[x][y][z] = block

    def get(self, x: int, y: int, z: int) -> str | None:
        if 0 <= x < self.sx and 0 <= y < self.sy and 0 <= z < self.sz:
            return self.cells[x][y][z]
        return None

    def fill(self, x0: int, x1: int, y0: int, y1: int, z0: int, z1: int, block: str) -> None:
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, block)

    def air(self, x0: int, x1: int, y0: int, y1: int, z0: int, z1: int) -> None:
        self.fill(x0, x1, y0, y1, z0, z1, AIR)

    def layer(self, y: int, x0: int, x1: int, z0: int, z1: int, block: str) -> None:
        self.fill(x0, x1, y, y, z0, z1, block)

    def rect(self, y: int, x0: int, x1: int, z0: int, z1: int, block: str) -> None:
        """Hollow one-high rectangle: perimeter only."""
        for x in range(x0, x1 + 1):
            self.set(x, y, z0, block)
            self.set(x, y, z1, block)
        for z in range(z0, z1 + 1):
            self.set(x0, y, z, block)
            self.set(x1, y, z, block)

    def columns(self, y0: int, y1: int, block: str, *points: tuple[int, int]) -> None:
        for x, z in points:
            for y in range(y0, y1 + 1):
                self.set(x, y, z, block)

    def scatter(self, x0: int, x1: int, y: int, z0: int, z1: int, primary: str, secondary: str,
                period: int = 4, phase: int = 1) -> None:
        """Deterministic speckle so large flat surfaces read as textured masonry."""
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                self.set(x, y, z, secondary if (x * 3 + z * 5 + phase) % period == 0 else primary)


# --------------------------------------------------------------------------------------
# composite structures
# --------------------------------------------------------------------------------------

def foundation(g: Grid, block: str, mask: set[tuple[int, int]]) -> None:
    for x, z in mask:
        g.set(x, 0, z, block)


def wall_ring(x0: int, x1: int, z0: int, z1: int) -> list[tuple[int, int]]:
    """Four-connected perimeter cells of an inclusive rectangle, in order."""
    cells = [(x, z0) for x in range(x0, x1 + 1)]
    cells += [(x1, z) for z in range(z0 + 1, z1 + 1)]
    cells += [(x, z1) for x in range(x1 - 1, x0 - 1, -1)]
    cells += [(x0, z) for z in range(z1 - 1, z0, -1)]
    return cells


def wall_shell(g: Grid, *, x0: int, x1: int, z0: int, z1: int, y0: int, y1: int,
               body: str, rubble: str, post: str, body_top: int = 0,
               speckle: str | None = None) -> list[tuple[int, int]]:
    """Stone footing, optional rubble course, then a body course up to the eaves.

    Returns the perimeter cells so callers can dress windows and doorways afterwards.
    `body_top` is the first y of the body (defaults to y0+1). `post` fills the corners.
    """
    body_top = body_top or y0 + 1
    ring = wall_ring(x0, x1, z0, z1)
    for x, z in ring:
        g.set(x, y0, z, rubble)
    for x, z in ring:
        for y in range(body_top, y1 + 1):
            g.set(x, y, z, body)
            if speckle and (x * 3 + z * 5 + y) % 7 == 0:
                g.set(x, y, z, speckle)
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        g.columns(body_top, y1, post, (x, z))
    return ring


def pitched_roof(g: Grid, *, x0: int, x1: int, z0: int, z1: int, eave_y: int,
                 material: str, slope: str, levels: int, ridge_slab: str | None = None,
                 fill: str | None = None, cap_ends: bool = True) -> int:
    """Gable roof ridged along X, rising from z0 (NORTH eave) and z1 (SOUTH eave).

    `slope` is the stair block; `fill` backs the underside of every stair so no seam
    shows through the pitch. Returns the y of the ridge cap.
    """
    ridge = (z0 + z1) // 2
    for d in range(levels):
        y = eave_y + d
        nz, sz = z0 + d, z1 - d
        if nz > ridge or sz < ridge:
            break
        g.layer(y, x0, x1, nz, nz, stairs(slope, "NORTH"))
        g.layer(y, x0, x1, sz, sz, stairs(slope, "SOUTH"))
        if nz < ridge:
            g.layer(y, x0, x1, nz + 1, ridge - 1, fill or material)
        if sz > ridge:
            g.layer(y, x0, x1, ridge + 1, sz - 1, fill or material)
    cap = eave_y + max(0, (z1 - z0) // 2 - 1)
    if ridge_slab:
        g.layer(cap + 1, x0, x1, ridge, ridge, ridge_slab)
    if cap_ends:
        gable_ends(g, x0=x0, x1=x1, z0=z0, z1=z1, eave_y=eave_y, wall=fill or material)
    return cap + 1 if ridge_slab else cap


def gable_ends(g: Grid, *, x0: int, x1: int, z0: int, z1: int, eave_y: int, wall: str) -> None:
    """Fill the triangular end walls at x0 and x1 so the attic is not see-through."""
    ridge = (z0 + z1) // 2
    for d in range(1, ridge - z0 + 1):
        y = eave_y + d
        nz, sz = z0 + d, z1 - d
        if nz > sz:
            break
        for x in (x0, x1):
            for z in range(nz, sz + 1):
                if g.get(x, y, z) is None:
                    g.set(x, y, z, wall)


def shed_roof(g: Grid, *, x0: int, x1: int, z0: int, z1: int, y: int, material: str,
              slope: str, facing: str) -> None:
    g.layer(y, x0, x1, z0, z1, material)
    edge = z1 if facing == "SOUTH" else z0
    g.layer(y, x0, x1, edge, edge, stairs(slope, facing))


def sawtooth_roof(g: Grid, *, x0: int, x1: int, z0: int, z1: int, y: int, spike: str,
                  ridge: str, purlin: str, glazing: str, teeth: int = 2,
                  axis: str = "z") -> int:
    """North-lit sawtooth roof: each span climbs a full level to a glazed monitor.

    `ridge` is the row the climb starts from, `spike` the row it tops out on, `purlin`
    the flat deck, and `glazing` the monitor at the drop. Returns the top row's y.
    """
    if axis == "z":
        lo, hi = z0, z1
        bounds = [lo + (hi - lo) * k // teeth for k in range(teeth + 1)]
        bounds[-1] = hi + 1
        for x in range(x0, x1 + 1):
            for k in range(teeth):
                start, stop = bounds[k], bounds[k + 1]
                if start >= stop:
                    continue
                g.set(x, y, start, ridge)
                for v in range(start + 1, stop - 1):
                    g.set(x, y + 1, v, spike)
                g.set(x, y, stop - 1, glazing if k == teeth - 1 else purlin)
    else:
        lo, hi = x0, x1
        bounds = [lo + (hi - lo) * k // teeth for k in range(teeth + 1)]
        bounds[-1] = hi + 1
        for z in range(z0, z1 + 1):
            for k in range(teeth):
                start, stop = bounds[k], bounds[k + 1]
                if start >= stop:
                    continue
                g.set(start, y, z, ridge)
                for v in range(start + 1, stop - 1):
                    g.set(v, y + 1, z, spike)
                g.set(stop - 1, y, z, glazing if k == teeth - 1 else purlin)
    return y + 1


def window_bay(g: Grid, *, facing: str, plane: int, y: int, span: tuple[int, int],
               frame: str, glass: str, sill: str) -> None:
    """Frame, glass and a projecting sill for a window cut into a flat wall plane.

    `frame` and `sill` must be bare base blocks. Shutters and window boxes are added
    by the caller so their direction and contents stay explicit.
    """
    a, b = span
    run = range(a, b + 1)
    for t in run:
        x, z = (t, plane) if RUNS_ALONG[facing] == "x" else (plane, t)
        g.set(x, y, z, glass)
    for t in (a - 1, b + 1):
        x, z = (t, plane) if RUNS_ALONG[facing] == "x" else (plane, t)
        g.set(x, y, z, frame)
    for t in range(a - 1, b + 2):
        x, z = (t, plane) if RUNS_ALONG[facing] == "x" else (plane, t)
        g.set(x, y - 1, z, stairs(sill, facing, half="bottom"))
    for t in (a - 1, b + 1):
        x, z = (t, plane) if RUNS_ALONG[facing] == "x" else (plane, t)
        if g.get(x, y + 1, z) is None:
            g.set(x, y + 1, z, frame)


def awning(g: Grid, *, facing: str, plane: int, span: tuple[int, int], y: int,
           canopy: str, edge: str, depth: int = 2) -> None:
    """Sloped shop awning projecting outward, with a contrasting valance on the lip."""
    a, b = span
    if RUNS_ALONG[facing] != "x":
        raise ValueError("awning currently authored for north/south facades")
    out = DIRS[facing][1]
    height = y
    for step in range(depth):
        z = plane + out * (step + 1)
        g.layer(height, a, b, z, z, canopy if step < depth - 1 else edge)
        height -= 1
    for t in (a, b):
        g.set(t, y - depth + 1, plane + out * depth, canopy)


def door_opening(g: Grid, *, x: int, z: int, facing: str, frame: str, door: str,
                 lintel: str, light: str | None = None, threshold: str) -> None:
    """Two-high doorway with a flanking frame, carved open above the threshold."""
    g.air(x, x, 1, 2, z, z)
    g.set(x, 0, z, threshold)
    outward = DIRS[facing]
    g.set(x, 3, z, lintel)
    side = DIRS["EAST"] if RUNS_ALONG[facing] == "x" else DIRS["SOUTH"]
    for s in (-1, 1):
        g.set(x + side[0] * s, 1, z + side[1] * s, frame)
        g.set(x + side[0] * s, 2, z + side[1] * s, frame)
    if light:
        g.set(x + outward[0], 3, z + outward[1], light)


def chimney(g: Grid, *, x: int, z0: int, z1: int, y0: int, y1: int, body: str,
            band: str, cap: str, vent: str | None = None) -> None:
    g.fill(x, x, y0, y1, z0, z1, body)
    g.layer(y1 - 1, x, x, z0, z1, band)
    g.layer(y1, x, x, z0, z1, cap)
    if vent:
        g.set(x, y1 + 1, z0, vent)


def flower_box(g: Grid, *, facing: str, plane: int, y: int, span: tuple[int, int],
               wood: str, soil: str, bloom: str) -> None:
    a, b = span
    outward = DIRS[facing]
    for t in range(a, b + 1):
        if RUNS_ALONG[facing] == "x":
            g.set(t, y, plane, wood)
            g.set(t, y + 1, plane + outward[1], soil)
            g.set(t, y + 2, plane + outward[1], bloom)
        else:
            g.set(plane, y, t, wood)
            g.set(plane + outward[0], y + 1, t, soil)
            g.set(plane + outward[0], y + 2, t, bloom)


def roof_lantern(g: Grid, x: int, y: int, z: int, support: str, lamp: str) -> None:
    g.set(x, y, z, support)
    g.set(x, y + 1, z, lamp)


# --------------------------------------------------------------------------------------
# output
# --------------------------------------------------------------------------------------

def footprint_mask(size_x: int, size_z: int, mask: set[tuple[int, int]]) -> list[str]:
    return ["".join("#" if (x, z) in mask else "." for x in range(size_x)) for z in range(size_z)]


def connected(mask: set[tuple[int, int]]) -> bool:
    if not mask:
        return False
    seen = {next(iter(mask))}
    queue = list(seen)
    while queue:
        x, z = queue.pop()
        for dx, dz in DIRS.values():
            cell = (x + dx, z + dz)
            if cell in mask and cell not in seen:
                seen.add(cell)
                queue.append(cell)
    return seen == mask


def build_preset(*, ident: str, name: str, category: str, grid: Grid, mask: set[tuple[int, int]],
                 palette: dict[str, str], entrance: dict, entrances: list[dict] | None = None,
                 tags: list[str] | None = None, size_tier: str = "standard",
                 shape: str = "rectangle", description: str = "") -> dict:
    """Freeze a designed grid into a native preset dict and validate every runtime rule.

    The legend is canonicalised: every distinct block string gets exactly one symbol.
    Blocks already covered by `palette` keep their authored symbol; anything else is
    interned automatically, so a design can never emit an undeclared block.
    """
    sx, sy, sz = grid.sx, grid.sy, grid.sz
    assert 1 <= sx <= 96 and 1 <= sz <= 96 and 3 <= sy <= 48, f"{ident}: envelope out of range"
    assert AIR in palette.values(), f"{ident}: palette needs an air symbol"
    assert connected(mask), f"{ident}: footprint mask is not four-connected"

    legend = dict(palette)
    taken = set(legend)
    reverse = {block: sym for sym, block in legend.items()}
    alphabet = [chr(c) for c in range(ord("A"), ord("Z") + 1)] + \
               [chr(c) for c in range(ord("a"), ord("z") + 1)] + \
               [chr(c) for c in range(ord("0"), ord("9") + 1)] + \
               list("!$%&*+-/:;<=>?@^_~|'`")

    def symbol_for(block: str) -> str:
        known = reverse.get(block)
        if known is not None:
            return known
        for candidate in alphabet:
            if candidate not in taken:
                taken.add(candidate)
                legend[candidate] = block
                reverse[block] = candidate
                return candidate
        raise ValueError(f"{ident}: legend exhausted")

    layers: list[list[str]] = []
    for y in range(sy):
        rows = []
        for z in range(sz):
            row = []
            for x in range(sx):
                cell = grid.get(x, y, z)
                if (x, z) in mask:
                    row.append(symbol_for(cell or AIR))
                else:
                    assert cell in (None, AIR), f"{ident}: voxel outside mask at y={y} z={z} x={x} ({cell})"
                    row.append(symbol_for(AIR))
            rows.append("".join(row))
        layers.append(rows)

    for door in [entrance] + list(entrances or []):
        key = (door["x"], door["z"])
        assert key in mask, f"{ident}: entrance {key} outside mask"
        dx, dz = DIRS[door["facing"]]
        assert (door["x"] + dx, door["z"] + dz) not in mask, \
            f"{ident}: entrance {key} facing {door['facing']} opens into reserved cell " \
            f"{(door['x'] + dx, door['z'] + dz)}"
        floor = grid.get(door["x"], 0, door["z"])
        assert floor not in (None, AIR), f"{ident}: entrance {key} has no floor ({floor})"
        upper = (grid.get(door["x"], 1, door["z"]), grid.get(door["x"], 2, door["z"]))
        assert upper == (AIR, AIR), f"{ident}: entrance {key} needs two clear blocks, found {upper}"

    entries = [entrance] + list(entrances or [])
    return {
        "id": ident,
        "name": name,
        "category": category,
        "tags": tags or [category, shape, size_tier, "crafted"],
        "styles": ["medieval_rustic"],
        "author": "MCSettlement",
        "description": description,
        "footprintShape": shape,
        "sizeTier": size_tier,
        "footprintMask": footprint_mask(sx, sz, mask),
        "sizeX": sx,
        "sizeZ": sz,
        "sizeY": sy,
        "entrance": dict(entries[0]),
        "entrances": [dict(e) for e in entries],
        "components": [{
            "id": "main",
            "role": "main",
            "mask": footprint_mask(sx, sz, mask),
        }],
        "palette": legend,
        "themeReplacements": {},
        "layers": layers,
    }


def write_preset(preset: dict, out_dir: Path = PRESET_DIR) -> Path:
    path = out_dir / f"{preset['id']}.json"
    path.write_text(json.dumps(preset, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return path


# --------------------------------------------------------------------------------------
# preview
# --------------------------------------------------------------------------------------

SOLID_FALLBACK = "#8a8a8a"


def _shade(hex_color: str, factor: float) -> tuple[int, int, int]:
    hex_color = hex_color.lstrip("#")[:6]
    r, g, b = (int(hex_color[i:i + 2], 16) for i in (0, 2, 4))
    return tuple(max(0, min(255, int(c * factor))) for c in (r, g, b))


def render_iso(preset: dict, colors: dict[str, str], width: int = 110,
               pitch: float = 0.58, yaw: float = 38.0) -> list[str]:
    """Painter's-algorithm isometric render into a character grid.

    Text cells are about twice as tall as wide, so the vertical projection is
    compressed and two columns are emitted per character.
    """
    sx, sy, sz = preset["sizeX"], preset["sizeY"], preset["sizeZ"]
    palette, layers = preset["palette"], preset["layers"]
    cy, syaw = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def project(x: float, y: float, z: float) -> tuple[float, float]:
        return x * cy - z * syaw, (x * syaw + z * cy) * sp - y * cp

    def block_at(x: int, y: int, z: int) -> str:
        if not (0 <= x < sx and 0 <= y < sy and 0 <= z < sz):
            return AIR
        return palette.get(layers[y][z][x], AIR)

    faces: list[tuple[float, list[tuple[float, float]], tuple[int, int, int]]] = []
    for x in range(sx):
        for y in range(sy):
            for z in range(sz):
                block = block_at(x, y, z)
                if block == AIR:
                    continue
                base = colors.get(block) or colors.get(block.split("[")[0], SOLID_FALLBACK)
                airy = "[facing=" in block and "half=bottom" in block
                half = "[type=bottom" in block or airy
                for axis, visible in (
                    ("top", block_at(x, y + 1, z) == AIR),
                    ("west", block_at(x - 1, y, z) == AIR),
                    ("north", block_at(x, y, z - 1) == AIR),
                    ("east", block_at(x + 1, y, z) == AIR),
                    ("south", block_at(x, y, z + 1) == AIR),
                ):
                    if not visible:
                        continue
                    if axis == "top":
                        quad = [(x, y + 1, z), (x + 1, y + 1, z), (x + 1, y + 1, z + 1), (x, y + 1, z + 1)]
                        light = 1.0
                    elif axis == "west":
                        quad = [(x, y, z), (x, y, z + 1), (x, y + 1, z + 1), (x, y + 1, z)]
                        light = 0.60
                    elif axis == "north":
                        quad = [(x, y, z), (x + 1, y, z), (x + 1, y + 1, z), (x, y + 1, z)]
                        light = 1.10
                    elif axis == "east":
                        quad = [(x + 1, y, z), (x + 1, y, z + 1), (x + 1, y + 1, z + 1), (x + 1, y + 1, z)]
                        light = 0.78
                    else:
                        quad = [(x, y, z + 1), (x + 1, y, z + 1), (x + 1, y + 1, z + 1), (x, y + 1, z + 1)]
                        light = 0.92
                    if half and axis != "top":
                        quad = [(qx, qy - 0.5 if qy == y + 1 else qy, qz) for qx, qy, qz in quad]
                    pts = [project(*c) for c in quad]
                    faces.append((sum(p[1] for p in pts) / 4 + sum(p[0] for p in pts) / 500, pts, _shade(base, light)))
    if not faces:
        return ["(empty)"]
    lo_x = min(p[0] for f in faces for p in f[1])
    hi_x = max(p[0] for f in faces for p in f[1])
    lo_y = min(p[1] for f in faces for p in f[1])
    hi_y = max(p[1] for f in faces for p in f[1])
    cols = max(8, min(width, int((hi_x - lo_x) * 2) + 3))
    rows = max(4, int(hi_y - lo_y) + 2)
    canvas = [[" "] * cols for _ in range(rows)]
    shades = " .:-=+*#%@"
    for _, pts, color in sorted(faces, key=lambda f: f[0]):
        px = [((p[0] - lo_x) * 2 + 1.0, (hi_y - p[1]) + 0.5) for p in pts]
        min_c = max(0, int(min(p[0] for p in px)))
        max_c = min(cols - 1, int(max(p[0] for p in px)) + 1)
        min_r = max(0, int(min(p[1] for p in px)))
        max_r = min(rows - 1, int(max(p[1] for p in px)) + 1)
        lum = (0.299 * color[0] + 0.587 * color[1] + 0.114 * color[2]) / 255
        glyph = shades[-1] if lum > 0.85 else shades[min(len(shades) - 1, int(lum * (len(shades) - 1) / 0.85))]
        for r in range(min_r, max_r + 1):
            for c in range(min_c, max_c + 1):
                if _inside(px, c + 0.5, r + 0.5):
                    canvas[r][c] = glyph
    return ["".join(row).rstrip() for row in canvas]


def _inside(poly: list[tuple[float, float]], px: float, py: float) -> bool:
    inside = False
    n = len(poly)
    for i in range(n):
        x0, y0 = poly[i]
        x1, y1 = poly[(i + 1) % n]
        if (y0 > py) != (y1 > py):
            x_at = x0 + (py - y0) * (x1 - x0) / (y1 - y0)
            if px < x_at:
                inside = not inside
    return inside


def render_plan(preset: dict, colors: dict[str, str], glyph=None) -> list[str]:
    """Top-down footprint map: surface block of each column."""
    sx, sz = preset["sizeX"], preset["sizeZ"]
    palette, layers = preset["palette"], preset["layers"]
    out = []
    for z in range(sz):
        row = []
        for x in range(sx):
            top = " "
            for y in range(len(layers) - 1, -1, -1):
                block = palette.get(layers[y][z][x], AIR)
                if block != AIR:
                    top = glyph(block) if glyph else block
                    break
            row.append(top)
        out.append("".join(row))
    return out


def render_elevation(preset: dict, axis: str = "x", index: int | None = None,
                     drop: tuple[str, ...] = (), glyph=None) -> list[str]:
    """Vertical section, drawn from the north/west edge so rooflines are readable.

    `drop` lists block prefixes to treat as air, which is how a reviewer looks at the
    walls and openings underneath a pitched roof.
    """
    sx, sy, sz = preset["sizeX"], preset["sizeY"], preset["sizeZ"]
    palette, layers = preset["palette"], preset["layers"]
    glyphs = {block: (glyph(block) if glyph else symbol) for symbol, block in palette.items()}
    glyphs[AIR] = " "

    def cell(y: int, a: int, b: int) -> str:
        block = palette.get(layers[y][b][a] if axis == "x" else layers[y][a][b], AIR)
        if any(block.startswith(prefix) for prefix in drop):
            block = AIR
        return glyphs.get(block, "?")

    out = []
    if axis == "x":
        index = sx // 2 if index is None else index
        for y in range(sy - 1, -1, -1):
            out.append("".join(cell(y, index, z) for z in range(sz)))
    else:
        index = sz // 2 if index is None else index
        for y in range(sy - 1, -1, -1):
            out.append("".join(cell(y, x, index) for x in range(sx)))
    return out


if __name__ == "__main__":
    print(__doc__)
