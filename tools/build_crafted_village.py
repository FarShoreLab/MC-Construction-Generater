"""Author the crafted village building series (batch 1).

Every model is drawn voxel by voxel: footing, shell, windows, doorway, pitched roof,
eaves, chimney, porch and interior fittings. Nothing here is a copy or a stamp, and no
external assets are used.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from preset_kit import (AIR, Grid, awning, build_preset, chimney, door_opening,
                        foundation, pitched_roof, sawtooth_roof, stairs, wall_shell,
                        window_bay, write_preset)

# --------------------------------------------------------------------------------------
# shared symbol legend (only the symbols a model uses are emitted per preset)
# --------------------------------------------------------------------------------------
SYMBOLS = {
    ".": AIR,
    "c": "minecraft:cobblestone",
    "m": "minecraft:mossy_cobblestone",
    "s": "minecraft:stone",
    "b": "minecraft:stone_bricks",
    "n": "minecraft:mossy_stone_bricks",
    "k": "minecraft:cracked_stone_bricks",
    "y": "minecraft:chiseled_stone_bricks",
    "B": "minecraft:bricks",
    "d": "minecraft:deepslate_tiles",
    "T": "minecraft:tuff",
    "q": "minecraft:calcite",
    "o": "minecraft:oak_planks",
    "O": "minecraft:oak_log[axis=y]",
    "P": "minecraft:white_terracotta",
    "v": "minecraft:oak_slab[type=bottom,waterlogged=false]",
    "ov": "minecraft:oak_slab[type=top,waterlogged=false]",
    "w": "minecraft:spruce_planks",
    "W": "minecraft:spruce_log[axis=y]",
    "wv": "minecraft:spruce_slab[type=top,waterlogged=false]",
    "e": "minecraft:dark_oak_planks",
    "E": "minecraft:dark_oak_log[axis=y]",
    "i": "minecraft:birch_planks",
    "a": "minecraft:acacia_planks",
    "u": "minecraft:mud_bricks",
    "uv": "minecraft:mud_brick_slab[type=top,waterlogged=false]",
    "t": "minecraft:terracotta",
    "g": "minecraft:glass_pane[waterlogged=false]",
    "I": "minecraft:iron_bars[waterlogged=false]",
    "f": "minecraft:oak_fence[east=false,north=false,south=false,water=false,west=false]",
    "F_": "minecraft:spruce_fence[east=false,north=false,south=false,water=false,west=false]",
    "h": "minecraft:hay_block[axis=y]",
    "L": "minecraft:lantern[hanging=false,waterlogged=false]",
    "Y": "minecraft:campfire[lit=true,signal_fire=false,waterlogged=false]",
    "X": "minecraft:chest[facing=south,type=single,waterlogged=false]",
    "r": "minecraft:barrel[facing=up,open=false]",
    "R_": "minecraft:barrel[facing=south,open=false]",
    "Fn": "minecraft:furnace[facing=south,lit=false]",
    "Sm": "minecraft:smoker[facing=south,lit=false]",
    "K": "minecraft:crafting_table",
    "Z": "minecraft:stonecutter",
    "A": "minecraft:anvil[facing=south]",
    "N": "minecraft:cauldron[level=3]",
    "H": "minecraft:bookshelf",
    "M": "minecraft:moss_block",
    "V": "minecraft:vine[east=false,north=false,south=false,up=false,west=false]",
    "z": "minecraft:moss_carpet",
    "l": "minecraft:oak_leaves[distance=7,persistent=true,waterlogged=false]",
    "R": "minecraft:flowering_azalea_leaves[distance=7,persistent=true,waterlogged=false]",
    "oT": "minecraft:oak_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]",
    "os": "minecraft:oak_trapdoor[facing=south,half=top,open=false,powered=false,waterlogged=false]",
    "wb": "minecraft:spruce_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]",
    "ws": "minecraft:spruce_trapdoor[facing=south,half=bottom,open=false,powered=false,waterlogged=false]",
    "on": "minecraft:oak_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]",
    "osb": "minecraft:oak_trapdoor[facing=south,half=bottom,open=false,powered=false,waterlogged=false]",
    "we": "minecraft:spruce_trapdoor[facing=east,half=top,open=false,powered=false,waterlogged=false]",
    "ww": "minecraft:spruce_trapdoor[facing=west,half=top,open=false,powered=false,waterlogged=false]",
    "_": "minecraft:gravel",
    "=": "minecraft:sand",
    "`": "minecraft:poppy",
    "'": "minecraft:dandelion",
    ";": "minecraft:cornflower",
    ":": "minecraft:azure_bluet",
    ",": "minecraft:oxeye_daisy",
    "/": "minecraft:short_grass",
    "\\": "minecraft:fern",
    "-": "minecraft:coarse_dirt",
    "+": "minecraft:podzol[snowy=false]",
    "~": "minecraft:lily_pad",
    "?": "minecraft:torch",
}

OUT = Path(__file__).resolve().parents[1] / "core-planner/src/main/resources/presets"
CREATED: list[str] = []


def emit(preset: dict) -> None:
    write_preset(preset, OUT)
    CREATED.append(preset["id"])
    print(f"built {preset['id']}: {preset['sizeX']}x{preset['sizeZ']}x{preset['sizeY']} "
          f"area={sum(r.count('#') for r in preset['footprintMask'])} "
          f"legend={len(preset['palette']) - 1}")


def palette(*symbols: str) -> dict[str, str]:
    out = {".": AIR}
    for symbol in symbols:
        assert symbol in SYMBOLS, f"unknown symbol {symbol!r}"
        out[symbol] = SYMBOLS[symbol]
    return out


# --------------------------------------------------------------------------------------
# 1. thatched cottage — steep thatch, half-timbered plaster, deep porch
# --------------------------------------------------------------------------------------
def thatched_cottage() -> dict:
    """Steep thatch, whitewashed plaster between oak studs, open entry bay and a brick stack."""
    sx, sz, sy = 9, 13, 10
    g = Grid(sx, sy, sz)
    house = {(x, z) for z in range(10) for x in range(sx)}
    apron = {(x, z) for z in range(10, 13) for x in range(1, 8)}
    mask = house | apron
    foundation(g, SYMBOLS["c"], mask)

    # rubble footing with mossy patches, then plaster infill between oak studs
    for x, z in mask:
        g.set(x, 1, z, SYMBOLS["m"] if (x + 2 * z) % 5 == 0 else SYMBOLS["c"])
    g.rect(2, 0, sx - 1, 0, 9, SYMBOLS["P"])
    g.rect(3, 1, sx - 2, 1, 8, SYMBOLS["P"])
    for x, z in ((0, 0), (8, 0), (0, 9), (8, 9)):
        g.columns(1, 3, SYMBOLS["O"], (x, z))
    for x in (2, 6):
        g.columns(1, 2, SYMBOLS["O"], (x, 0), (x, 9))

    # two-high openings: the front door, a back door, and side windows on both gables
    door_opening(g, x=4, z=9, facing="SOUTH", frame=SYMBOLS["O"], door=SYMBOLS["o"],
                 lintel=SYMBOLS["o"], threshold=SYMBOLS["b"])
    door_opening(g, x=4, z=0, facing="NORTH", frame=SYMBOLS["O"], door=SYMBOLS["o"],
                 lintel=SYMBOLS["o"], threshold=SYMBOLS["b"])
    window_bay(g, facing="NORTH", plane=0, y=2, span=(1, 2), frame=SYMBOLS["O"],
               glass=SYMBOLS["g"], sill=SYMBOLS["o"])
    window_bay(g, facing="NORTH", plane=0, y=2, span=(6, 7), frame=SYMBOLS["O"],
               glass=SYMBOLS["g"], sill=SYMBOLS["o"])
    window_bay(g, facing="SOUTH", plane=9, y=2, span=(1, 2), frame=SYMBOLS["O"],
               glass=SYMBOLS["g"], sill=SYMBOLS["o"])
    window_bay(g, facing="SOUTH", plane=9, y=2, span=(6, 7), frame=SYMBOLS["O"],
               glass=SYMBOLS["g"], sill=SYMBOLS["o"])
    window_bay(g, facing="WEST", plane=0, y=2, span=(4, 6), frame=SYMBOLS["O"],
               glass=SYMBOLS["g"], sill=SYMBOLS["o"])
    for x in (1, 2, 6, 7):
        g.set(x, 2, 10, SYMBOLS["oT"])            # shutters folded flat against the wall
        g.set(x, 2, 1, SYMBOLS["on"])
    for x, bloom in ((1, SYMBOLS["`"]), (7, SYMBOLS["'"])):
        g.set(x, 1, 11, SYMBOLS["osb"])           # window box: hinged trapdoor planter
        g.set(x, 2, 11, SYMBOLS["o"])
        g.set(x, 3, 11, bloom)

    # oak bressumer and studs: alternate timber and plaster along the eaves line
    for z in range(1, 9):
        g.set(0, 3, z, SYMBOLS["O"] if z % 2 == 0 else SYMBOLS["P"])
        g.set(8, 3, z, SYMBOLS["O"] if z % 2 == 0 else SYMBOLS["P"])
    for x in range(1, 8):
        if g.get(x, 3, 0) is None:
            g.set(x, 3, 0, SYMBOLS["O"] if x % 2 else SYMBOLS["P"])
        g.set(x, 3, 9, SYMBOLS["O"] if x % 2 else SYMBOLS["P"])

    pitched_roof(g, x0=0, x1=8, z0=0, z1=9, eave_y=4, material=SYMBOLS["h"],
                 slope=SYMBOLS["h"], levels=5, ridge_slab=SYMBOLS["h"],
                 fill=SYMBOLS["o"], cap_ends=True)
    chimney(g, x=2, z0=3, z1=4, y0=6, y1=9, body=SYMBOLS["B"], band=SYMBOLS["b"],
            cap=SYMBOLS["b"], vent=SYMBOLS["Y"])

    # entry bay: flagstone apron under the thatch overhang, braced by two oak posts
    for x in range(1, 8):
        for z in range(10, 13):
            g.set(x, 0, z, SYMBOLS["m"] if (x * 2 + z) % 5 == 0 else SYMBOLS["c"])
    g.columns(1, 2, SYMBOLS["O"], (1, 12), (7, 12))
    for x in range(1, 8):
        if x != 4:                               # keep the entrance bay clear
            g.set(x, 2, 12, stairs(SYMBOLS["o"], "SOUTH"))
        g.set(x, 2, 11, SYMBOLS["o"])
    for x in (1, 2, 6, 7):
        g.set(x, 1, 12, SYMBOLS["f"])            # balustrade either side of the entrance
    for y in (1, 2):
        g.set(4, y, 12, AIR)
    g.set(1, 3, 12, SYMBOLS["L"])
    g.set(7, 3, 12, SYMBOLS["L"])
    g.set(1, 1, 10, SYMBOLS[":"] )
    g.set(7, 1, 10, SYMBOLS[";"])
    for z in (3, 7):
        g.set(0, 4, z, SYMBOLS["V"])
        g.set(8, 4, z, SYMBOLS["V"])

    # interior: hearth, cupboards, a bed and a hanging lantern
    g.set(7, 1, 1, SYMBOLS["Fn"])
    g.set(6, 1, 1, SYMBOLS["K"])
    g.set(1, 1, 1, SYMBOLS["X"])
    g.set(2, 1, 1, SYMBOLS["X"])
    g.set(2, 3, 6, SYMBOLS["f"])
    g.set(2, 2, 6, SYMBOLS["L"])
    return build_preset(
        ident="thatched_cottage", name="茅草农舍", category="residential",
        grid=g, mask=mask,
        palette=palette("c", "m", "b", "o", "O", "P", "g", "h", "f", "L", "Y", "X", "Fn", "K", "V",
                        "`", "'", ";", ":", "oT", "on", "osb"),
        entrance=dict(facing="SOUTH", x=4, z=12),
        entrances=[dict(facing="NORTH", x=4, z=0)],
        tags=["residential", "crafted", "rectangle", "standard", "thatch", "cottage", "porch"],
        size_tier="standard",
        description="9×13 茅草农舍：陡坡茅草屋顶与砖砌烟囱、白灰泥夹橡木梁柱、百叶窗与窗盒、"
                    "屋檐下的石铺门廊；门廊与北侧后门净空均为两格。")


# --------------------------------------------------------------------------------------
# 2. pottery workshop — bottle kiln, tiled roof, open-air kiln yard
# --------------------------------------------------------------------------------------
def pottery_workshop() -> dict:
    """Stone workshop with a mud-brick tiled roof, plus a wood-fired bottle kiln in the yard."""
    sx, sz, sy = 13, 13, 12
    g = Grid(sx, sy, sz)
    building = {(x, z) for z in range(12) for x in range(12)}
    yard = {(12, z) for z in range(8, 12)}
    mask = building | yard
    foundation(g, SYMBOLS["c"], mask)

    for x, z in mask:
        g.set(x, 0, z, SYMBOLS["_"] if (x + z) % 5 == 0 else SYMBOLS["c"])
    wall_shell(g, x0=0, x1=11, z0=0, z1=11, y0=1, y1=3, body=SYMBOLS["b"],
               rubble=SYMBOLS["n"], post=SYMBOLS["O"], speckle=SYMBOLS["k"])
    g.rect(4, 1, 11, 1, 11, SYMBOLS["b"])
    for z in range(1, 11):
        g.set(0, 4, z, SYMBOLS["b"] if z % 3 else SYMBOLS["y"])
        g.set(11, 4, z, SYMBOLS["y"] if z % 3 else SYMBOLS["b"])

    # workshop openings: a wide north light band, a south door and an open kiln-yard arch
    window_bay(g, facing="NORTH", plane=0, y=2, span=(2, 9), frame=SYMBOLS["O"],
               glass=SYMBOLS["g"], sill=SYMBOLS["o"])
    for x in (4, 7):
        g.columns(1, 3, SYMBOLS["O"], (x, 0))
    door_opening(g, x=5, z=11, facing="SOUTH", frame=SYMBOLS["O"], door=SYMBOLS["o"],
                 lintel=SYMBOLS["o"], threshold=SYMBOLS["b"])
    door_opening(g, x=7, z=11, facing="SOUTH", frame=SYMBOLS["O"], door=SYMBOLS["o"],
                 lintel=SYMBOLS["o"], threshold=SYMBOLS["b"])   # loading bay for greenware
    g.air(11, 11, 1, 2, 9, 9)                    # open arch from the shop into the kiln yard
    g.set(11, 3, 9, SYMBOLS["y"])
    for z in (4, 6):                             # unglazed drying bays on the east wall
        g.air(11, 11, 2, 2, z, z)
        g.set(11, 2, z, SYMBOLS["I"])

    # wood-fired bottle kiln in the yard: tapering terracotta stack over a stone chamber
    g.fill(9, 11, 1, 1, 9, 10, SYMBOLS["B"])
    g.fill(10, 11, 2, 4, 9, 10, SYMBOLS["t"])
    g.fill(10, 11, 2, 2, 9, 10, SYMBOLS["B"])
    chimney(g, x=11, z0=11, z1=11, y0=1, y1=8, body=SYMBOLS["t"], band=SYMBOLS["B"],
            cap=SYMBOLS["b"], vent=SYMBOLS["Y"])
    g.set(11, 5, 11, SYMBOLS["t"])
    g.set(12, 1, 9, SYMBOLS["I"])
    g.set(12, 2, 9, SYMBOLS["I"])
    g.set(12, 1, 10, SYMBOLS["I"])
    g.set(12, 2, 10, SYMBOLS["I"])
    g.set(12, 3, 9, SYMBOLS["B"])
    g.set(12, 3, 10, SYMBOLS["B"])
    g.set(11, 1, 10, SYMBOLS["B"])

    # kiln yard: fuel stack, clay bins, water butt and a shelf of greenware
    g.set(12, 1, 11, SYMBOLS["O"])
    g.set(12, 2, 11, SYMBOLS["O"])
    g.set(12, 1, 8, SYMBOLS["h"])
    g.set(12, 1, 9, SYMBOLS["r"])
    g.set(11, 1, 6, SYMBOLS["N"])
    g.set(11, 2, 6, SYMBOLS["N"])
    g.fill(9, 10, 1, 1, 6, 7, SYMBOLS["o"])
    g.fill(9, 10, 2, 2, 6, 7, SYMBOLS["u"])
    g.set(9, 3, 6, SYMBOLS["r"])
    g.set(10, 3, 6, SYMBOLS["N"])
    g.set(11, 3, 3, SYMBOLS["V"])
    g.set(11, 3, 6, SYMBOLS["f"])
    g.set(11, 4, 6, SYMBOLS["L"])

    # tiled roof: mud-brick slopes, glazed monitor ridge and wide eaves
    pitched_roof(g, x0=0, x1=11, z0=0, z1=11, eave_y=4, material=SYMBOLS["u"],
                 slope=SYMBOLS["u"], levels=5, ridge_slab=SYMBOLS["uv"],
                 fill=SYMBOLS["b"], cap_ends=True)
    for x in range(0, 12, 3):
        g.set(x, 6, 5, SYMBOLS["I"])
        g.set(x, 6, 6, SYMBOLS["I"])

    # interior: wheels, benches and stock shelves
    g.set(1, 1, 7, SYMBOLS["K"])
    g.set(2, 1, 7, SYMBOLS["Z"])
    g.set(9, 1, 1, SYMBOLS["Fn"])
    g.set(10, 1, 1, SYMBOLS["r"])
    g.set(1, 1, 1, SYMBOLS["N"])
    g.set(2, 1, 1, SYMBOLS["X"])
    g.set(5, 3, 5, SYMBOLS["f"])
    g.set(5, 2, 5, SYMBOLS["L"])
    return build_preset(
        ident="pottery_workshop", name="陶艺作坊", category="industrial",
        grid=g, mask=mask,
        palette=palette("_", "c", "b", "n", "k", "y", "u", "uv", "t", "B", "I", "o", "O", "g",
                        "f", "L", "Y", "N", "r", "X", "Z", "Fn", "K", "V", ",", "/", "\\"),
        entrance=dict(facing="SOUTH", x=5, z=11),
        entrances=[dict(facing="SOUTH", x=7, z=11)],
        tags=["industrial", "crafted", "rectangle", "standard", "kiln", "pottery", "workshop"],
        size_tier="standard",
        description="13×13 陶艺作坊：石砌厂房、陶瓦坡顶与天窗、木柴瓶形窑、陶土堆场与晾坯棚；"
                    "南门与东侧装窑门净空两格，窑炉烟火朝外。")


# --------------------------------------------------------------------------------------
# 3. carpenter workshop — twin sawtooth roof, open lumber yard
# --------------------------------------------------------------------------------------
def carpenter_workshop() -> dict:
    """Birch-panelled shop under a twin sawtooth roof with a plank lumber yard."""
    sx, sz, sy = 13, 12, 9
    g = Grid(sx, sy, sz)
    building = {(x, z) for z in range(10) for x in range(13)}
    yard = {p for p in ((x, 10) for x in range(13))} | {p for p in ((x, 11) for x in range(13))}
    mask = building | yard
    foundation(g, SYMBOLS["c"], mask)

    for x, z in mask:
        g.set(x, 0, z, SYMBOLS["c"] if (x * 2 + z) % 3 else SYMBOLS["_"])
    wall_shell(g, x0=0, x1=12, z0=0, z1=9, y0=1, y1=3, body=SYMBOLS["i"],
               rubble=SYMBOLS["o"], post=SYMBOLS["O"], speckle=SYMBOLS["o"])
    for x in (4, 8):
        g.columns(1, 3, SYMBOLS["O"], (x, 0))
    for z in (3, 6):
        g.set(0, 3, z, SYMBOLS["O"])
        g.set(12, 3, z, SYMBOLS["O"])

    window_bay(g, facing="NORTH", plane=0, y=2, span=(2, 3), frame=SYMBOLS["O"],
               glass=SYMBOLS["g"], sill=SYMBOLS["o"])
    window_bay(g, facing="NORTH", plane=0, y=2, span=(5, 7), frame=SYMBOLS["O"],
               glass=SYMBOLS["g"], sill=SYMBOLS["o"])
    window_bay(g, facing="NORTH", plane=0, y=2, span=(9, 10), frame=SYMBOLS["O"],
               glass=SYMBOLS["g"], sill=SYMBOLS["o"])
    door_opening(g, x=3, z=9, facing="SOUTH", frame=SYMBOLS["O"], door=SYMBOLS["o"],
                 lintel=SYMBOLS["o"], threshold=SYMBOLS["b"])
    g.air(9, 9, 1, 2, 9, 9)                      # open timber bay for long stock
    g.set(9, 3, 9, SYMBOLS["o"])
    for s in (8, 10):
        g.columns(1, 2, SYMBOLS["O"], (s, 9))
    door_opening(g, x=12, z=3, facing="EAST", frame=SYMBOLS["O"], door=SYMBOLS["o"],
                 lintel=SYMBOLS["o"], threshold=SYMBOLS["b"])
    door_opening(g, x=12, z=6, facing="EAST", frame=SYMBOLS["O"], door=SYMBOLS["o"],
                 lintel=SYMBOLS["o"], threshold=SYMBOLS["b"])
    window_bay(g, facing="EAST", plane=12, y=2, span=(8, 9), frame=SYMBOLS["O"],
               glass=SYMBOLS["g"], sill=SYMBOLS["o"])

    # twin sawtooth roof, north-lit, with an exposed ridge beam
    sawtooth_roof(g, x0=0, x1=12, z0=0, z1=9, y=4, spike=SYMBOLS["o"],
                  ridge=SYMBOLS["O"], purlin=SYMBOLS["o"], glazing=SYMBOLS["g"],
                  teeth=2, axis="x")
    for x in range(0, 13, 3):
        g.set(x, 5, 4, SYMBOLS["O"])

    # lumber yard: stacked logs and plank racks, kept clear of the doors
    for x in (0, 1, 11, 12):
        g.set(x, 10, 1, SYMBOLS["O"])
        g.set(x, 10, 2, SYMBOLS["O"])
    g.set(2, 10, 1, SYMBOLS["h"])
    g.set(2, 11, 1, SYMBOLS["h"])
    for x in (5, 6, 7):
        g.set(x, 10, 1, SYMBOLS["o"])
    g.set(6, 10, 2, SYMBOLS["o"])
    g.set(8, 10, 1, SYMBOLS["r"])
    g.set(9, 10, 1, SYMBOLS["r"])
    g.set(4, 10, 1, SYMBOLS["A"])
    g.set(0, 11, 1, SYMBOLS["f"])
    g.set(0, 11, 2, SYMBOLS["L"])
    g.set(12, 11, 1, SYMBOLS["f"])
    g.set(12, 11, 2, SYMBOLS["L"])

    # interior: benches, plank stacks and a sawdust floor
    g.set(1, 1, 2, SYMBOLS["K"])
    g.set(2, 1, 2, SYMBOLS["Z"])
    g.set(11, 1, 2, SYMBOLS["Fn"])
    g.set(10, 1, 2, SYMBOLS["r"])
    g.set(1, 1, 7, SYMBOLS["X"])
    g.set(2, 1, 7, SYMBOLS["X"])
    g.fill(5, 8, 1, 1, 6, 6, SYMBOLS["o"])
    g.set(6, 2, 6, SYMBOLS["o"])
    g.set(6, 3, 5, SYMBOLS["f"])
    g.set(6, 2, 5, SYMBOLS["L"])
    return build_preset(
        ident="carpenter_workshop", name="木工工坊", category="industrial",
        grid=g, mask=mask,
        palette=palette("_", "c", "b", "o", "O", "i", "g", "f", "L", "X", "r", "Z", "Fn", "K", "A",
                        "h", "/", "\\"),
        entrance=dict(facing="EAST", x=12, z=3),
        entrances=[dict(facing="EAST", x=12, z=6)],
        tags=["industrial", "crafted", "rectangle", "standard", "timber", "workshop", "sawmill"],
        size_tier="standard",
        description="13×12 木工工坊：双跨北向采光锯齿屋顶、白桦板墙与橡木构架、敞口长料间、"
                    "原木与板材堆场；南门与北侧料门净空两格。")


# --------------------------------------------------------------------------------------
# 4. herbalist shop — mossy stone, shop awning, walled herb garden
# --------------------------------------------------------------------------------------
def herbalist_shop() -> dict:
    """Mossy stone shop under a spruce gable, with a shop awning and a walled herb garden."""
    sx, sz, sy = 11, 12, 9
    g = Grid(sx, sy, sz)
    building = {(x, z) for z in range(8) for x in range(8)}
    forecourt = {(x, z) for z in range(8, 12) for x in range(4)}
    garden = {(x, z) for z in range(8, 12) for x in range(4, 11)}
    mask = building | forecourt | garden
    foundation(g, SYMBOLS["c"], mask)

    for x, z in mask:
        g.set(x, 0, z, SYMBOLS["M"] if (x + 2 * z) % 6 == 0 else SYMBOLS["n"])
    wall_shell(g, x0=0, x1=7, z0=0, z1=7, y0=1, y1=3, body=SYMBOLS["w"],
               rubble=SYMBOLS["n"], post=SYMBOLS["W"], speckle=SYMBOLS["n"])
    g.rect(2, 0, 7, 0, 7, SYMBOLS["o"])
    for x in (3, 5):
        g.columns(1, 2, SYMBOLS["W"], (x, 0), (x, 7))

    door_opening(g, x=0, z=4, facing="WEST", frame=SYMBOLS["W"], door=SYMBOLS["w"],
                 lintel=SYMBOLS["o"], threshold=SYMBOLS["b"], light=SYMBOLS["L"])
    door_opening(g, x=7, z=3, facing="EAST", frame=SYMBOLS["W"], door=SYMBOLS["w"],
                 lintel=SYMBOLS["o"], threshold=SYMBOLS["b"])
    window_bay(g, facing="SOUTH", plane=7, y=2, span=(1, 2), frame=SYMBOLS["W"],
               glass=SYMBOLS["g"], sill=SYMBOLS["o"])
    window_bay(g, facing="SOUTH", plane=7, y=2, span=(5, 6), frame=SYMBOLS["W"],
               glass=SYMBOLS["g"], sill=SYMBOLS["o"])
    window_bay(g, facing="NORTH", plane=0, y=2, span=(2, 5), frame=SYMBOLS["W"],
               glass=SYMBOLS["g"], sill=SYMBOLS["o"])
    g.set(1, 2, 8, SYMBOLS["ws"])                # shopfront shutters facing the forecourt
    g.set(6, 2, 8, SYMBOLS["ws"])
    for x in (1, 2, 5, 6):                       # small canopy over the forecourt windows
        g.set(x, 3, 8, stairs(SYMBOLS["o"], "SOUTH"))
    g.set(1, 1, 8, SYMBOLS["F_"])
    g.set(6, 1, 8, SYMBOLS["F_"])
    g.set(4, 1, 9, SYMBOLS["f"])
    g.set(4, 2, 9, SYMBOLS["h"])

    pitched_roof(g, x0=0, x1=7, z0=0, z1=7, eave_y=4, material=SYMBOLS["w"],
                 slope=SYMBOLS["w"], levels=3, ridge_slab=SYMBOLS["wv"],
                 fill=SYMBOLS["o"], cap_ends=True)
    chimney(g, x=6, z0=1, z1=2, y0=5, y1=8, body=SYMBOLS["n"], band=SYMBOLS["M"],
            cap=SYMBOLS["c"], vent=None)

    # walled herb garden south of the shop: boundary wall, gate, raised beds and a butt
    for z in range(8, 12):
        g.set(10, 1, z, SYMBOLS["n"])
        g.set(10, 2, z, SYMBOLS["n"])
    for x in range(4, 11):
        g.set(x, 1, 11, SYMBOLS["n"])
        g.set(x, 2, 11, SYMBOLS["M"] if x % 2 else SYMBOLS["n"])
    g.air(7, 7, 1, 2, 11, 11)                    # garden gate opening
    for x in (5, 6, 8, 9):
        g.set(x, 1, 11, SYMBOLS["n"])
        g.set(x, 2, 11, SYMBOLS["n"])
    for x in (6, 8):
        g.set(x, 3, 11, SYMBOLS["n"])
    g.set(7, 3, 11, SYMBOLS["o"])                # lintel spanning the gate posts
    for x, z in ((6, 8), (9, 8), (6, 10), (9, 10)):
        g.set(x, 1, z, SYMBOLS["o"])
        g.set(x, 2, z, SYMBOLS["M"])
    for x, z, plant in ((6, 8, SYMBOLS["`"]), (9, 8, SYMBOLS["'"]), (6, 10, SYMBOLS[";"]),
                        (9, 10, SYMBOLS[":"])):
        g.set(x, 3, z, plant)
    g.set(7, 1, 9, SYMBOLS["N"])
    g.set(7, 2, 9, SYMBOLS["N"])
    g.set(8, 1, 8, SYMBOLS["r"])
    g.set(5, 1, 10, SYMBOLS["r"])
    g.set(5, 2, 10, SYMBOLS["h"])
    g.set(8, 1, 10, SYMBOLS["F_"])
    g.set(8, 2, 10, SYMBOLS["L"])
    g.set(10, 3, 8, SYMBOLS["V"])

    # interior: brewing bench, herb shelves and a mossy floor
    g.set(1, 1, 1, SYMBOLS["K"])
    g.set(2, 1, 1, SYMBOLS["N"])
    g.set(6, 1, 1, SYMBOLS["H"])
    g.set(5, 1, 1, SYMBOLS["H"])
    g.set(1, 1, 5, SYMBOLS["X"])
    g.set(2, 1, 5, SYMBOLS["r"])
    g.set(4, 1, 3, SYMBOLS["M"])
    g.set(4, 1, 4, SYMBOLS["z"])
    return build_preset(
        ident="herbalist_shop", name="草药铺", category="commercial",
        grid=g, mask=mask,
        palette=palette("c", "b", "n", "o", "O", "w", "W", "a", "g", "f", "F_", "l", "R", "M", "N",
                        "H", "r", "K", "L", "z", "'", "`", ";", ":", "ws", "wv", "/", "\\"),
        entrance=dict(facing="WEST", x=0, z=4),
        entrances=[dict(facing="EAST", x=7, z=3), dict(facing="SOUTH", x=7, z=11)],
        tags=["commercial", "crafted", "rectangle", "standard", "herbalist", "shop", "garden"],
        size_tier="standard",
        description="11×12 草药铺：青苔石基与云杉构架、外挑木棚与门旁提灯、店前百叶窗、"
                    "围墙草药圃与晾药架；店面正门与后院园门净空两格。")


def main() -> int:
    for build in (thatched_cottage, pottery_workshop, carpenter_workshop, herbalist_shop):
        emit(build())
    print(f"batch 1: {len(CREATED)} presets -> {OUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
