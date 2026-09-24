"""
Master Preset Rebuilder.
Reconstructs all 10 core presets with master architectural techniques:
- Protruding log framework & inset walls (立面进深)
- Inverted stairs corbels & eaves overhang (倒置楼梯挑檐与雀替)
- Steep double-slope roofs with contrasting trim (双坡陡顶与山墙封边)
- Rich interior detailing & functional furniture (精细内饰与家具)
"""

import json
import os

PRESETS_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", "core-planner", "src", "main", "resources", "presets"))

# Universal palette for master presets
COMMON_PALETTE = {
    ".": "minecraft:air",
    "C": "minecraft:cobblestone",
    "S": "minecraft:stone_bricks",
    "W": "minecraft:oak_planks",
    "L": "minecraft:oak_log[axis=y]",
    "G": "minecraft:glass_pane",
    "U": "minecraft:oak_stairs[half=top,facing=east]",
    "u": "minecraft:oak_stairs[half=top,facing=west]",
    "[": "minecraft:oak_stairs[facing=east,half=bottom]",
    "]": "minecraft:oak_stairs[facing=west,half=bottom]",
    "{": "minecraft:stone_brick_stairs[facing=east,half=bottom]",
    "}": "minecraft:stone_brick_stairs[facing=west,half=bottom]",
    "O": "minecraft:oak_slab",
    "o": "minecraft:stone_brick_slab",
    "R": "minecraft:bricks",
    "H": "minecraft:campfire",
    "I": "minecraft:iron_bars",
    "T": "minecraft:crafting_table",
    "X": "minecraft:chest[facing=south]",
    "F": "minecraft:furnace[facing=north]",
    "B": "minecraft:blast_furnace[facing=north]",
    "A": "minecraft:anvil[facing=east]",
    "K": "minecraft:cauldron",
    "E": "minecraft:red_bed",
    "M": "minecraft:bookshelf",
    "P": "minecraft:lantern",
    "J": "minecraft:red_carpet",
    "N": "minecraft:oak_fence",
    "D": "minecraft:dark_oak_planks"
}

THEME_REPLACEMENTS = {
    "nordic_coastal": {
        "minecraft:oak_planks": "minecraft:spruce_planks",
        "minecraft:oak_log[axis=y]": "minecraft:spruce_log[axis=y]",
        "minecraft:oak_slab": "minecraft:spruce_slab",
        "minecraft:oak_stairs[half=top,facing=east]": "minecraft:spruce_stairs[half=top,facing=east]",
        "minecraft:oak_stairs[half=top,facing=west]": "minecraft:spruce_stairs[half=top,facing=west]",
        "minecraft:oak_stairs[facing=east,half=bottom]": "minecraft:spruce_stairs[facing=east,half=bottom]",
        "minecraft:oak_stairs[facing=west,half=bottom]": "minecraft:spruce_stairs[facing=west,half=bottom]",
        "minecraft:oak_fence": "minecraft:spruce_fence",
        "minecraft:red_carpet": "minecraft:blue_carpet"
    },
    "mountain_outpost": {
        "minecraft:cobblestone": "minecraft:cobbled_deepslate",
        "minecraft:stone_bricks": "minecraft:deepslate_bricks",
        "minecraft:oak_planks": "minecraft:dark_oak_planks",
        "minecraft:oak_log[axis=y]": "minecraft:dark_oak_log[axis=y]",
        "minecraft:oak_slab": "minecraft:deepslate_brick_slab",
        "minecraft:oak_stairs[half=top,facing=east]": "minecraft:deepslate_brick_stairs[half=top,facing=east]",
        "minecraft:oak_stairs[half=top,facing=west]": "minecraft:deepslate_brick_stairs[half=top,facing=west]",
        "minecraft:oak_stairs[facing=east,half=bottom]": "minecraft:deepslate_brick_stairs[facing=east,half=bottom]",
        "minecraft:oak_stairs[facing=west,half=bottom]": "minecraft:deepslate_brick_stairs[facing=west,half=bottom]",
        "minecraft:oak_fence": "minecraft:dark_oak_fence",
        "minecraft:red_carpet": "minecraft:gray_carpet"
    }
}


def build_blacksmith():
    # 10x8x10 Artisan Blacksmith with open forge, corbel eaves, double chimney
    sx, sy, sz = 10, 8, 10
    layers = [
        # Y=0 Foundation
        [
            "CCCCCCCCCC",
            "CCCCCCCCCC",
            "CCCCCCCCCC",
            "CCCCCCCCCC",
            "CCCCCCCCCC",
            "CCCCCCCCCC",
            "CCCCCCCCCC",
            "CCCCCCCCCC",
            "CCCCCCCCCC",
            "CCCCCCCCCC"
        ],
        # Y=1 Base Walls & Forge Interior (X=left workshop, X=right forge shed)
        [
            "LSSSSSSL..",
            "S......S..",
            "S......S..",
            "S......S..",
            "LSSSSSSL..",
            "L......L..",
            "I..A...I.L",
            "I......I.L",
            "LSSSSSSL.L",
            ".........."
        ],
        # Y=2 Recessed Windows & Equipment
        [
            "LSGGGGSL..",
            "G..RR..G..",
            "G..RR..G..",
            "X..FB..G..",
            "LSGGGGSL..",
            "L......L..",
            "I..K...I.L",
            "I......I.L",
            "L......L.L",
            ".........."
        ],
        # Y=3 Tie Beam & Wall Infill
        [
            "LSSSSSSL..",
            "S..RR..S..",
            "S..RR..S..",
            "S......S..",
            "LSSSSSSL..",
            "L......L..",
            "N......N.L",
            "N......N.L",
            "LLLLLLLL.L",
            ".........."
        ],
        # Y=4 Corbels & Eaves Overhang (U = inverted stairs)
        [
            "UUUUUUUU..",
            "U..RR..U..",
            "U..RR..U..",
            "U......U..",
            "UUUUUUUU..",
            "U......U..",
            "U......U..",
            "U......U..",
            "UUUUUUUU..",
            ".........."
        ],
        # Y=5 Gable Roof Slope & Forge Shed Roof
        [
            ".OOOOOO...",
            ".OWRROW...",
            ".OWRROW...",
            ".OWWWWO...",
            ".OOOOOO...",
            ".OOOOOO...",
            ".OOOOOO...",
            ".OOOOOO...",
            ".OOOOOO...",
            ".........."
        ],
        # Y=6 Upper Roof Slope & Chimney
        [
            "..OOOO....",
            "..ORRO....",
            "..ORRO....",
            "..OOOO....",
            "..........",
            "..........",
            "..........",
            "..........",
            "..........",
            ".........."
        ],
        # Y=7 Chimney Smoke & Ridge
        [
            "...SS.....",
            "...RRO....",
            "...RHO....",
            "...SS.....",
            "..........",
            "..........",
            "..........",
            "..........",
            "..........",
            ".........."
        ]
    ]
    return {
        "id": "artisan_blacksmith",
        "name": "工匠铁匠铺 (Artisan Blacksmith Pro)",
        "category": "workshop",
        "tags": ["workshop", "crafting", "blacksmith", "forge", "master"],
        "styles": ["medieval_rustic", "mountain_outpost", "nordic_coastal"],
        "author": "MCSettlement Master",
        "description": "大师级工匠铁匠铺，全原木外凸立柱架构，内凹石砖立面，全向倒置楼梯雀替挑檐，高耸双联砖石烟囱与半开放式外挑工棚",
        "sizeX": sx,
        "sizeY": sy,
        "sizeZ": sz,
        "entrance": {"facing": "SOUTH", "x": 5, "z": 8},
        "palette": COMMON_PALETTE,
        "themeReplacements": THEME_REPLACEMENTS,
        "layers": layers
    }


def build_nordic_cottage():
    # 10x10x10 Nordic Timber Longhouse with steep 60° gable roof, inverted corbels, dormer
    sx, sy, sz = 10, 10, 10
    layers = [
        # Y=0 Foundation
        [
            "CCCCCCCCCC",
            "CWWWWWWWWC",
            "CWWWWWWWWC",
            "CWWWWWWWWC",
            "CWWWWWWWWC",
            "CWWWWWWWWC",
            "CWWWWWWWWC",
            "CWWWWWWWWC",
            "CWWWWWWWWC",
            "CCCCCCCCCC"
        ],
        # Y=1 Ground Floor with Protruding Logs
        [
            "LCCCCCCCCL",
            "C...R....C",
            "C...R....C",
            "C........C",
            "C........C",
            "C........C",
            "C........C",
            "C........C",
            "C...JJ...C",
            "LCC....CCL"
        ],
        # Y=2 Inset Wood Walls & Windows
        [
            "LWWWWWWWWL",
            "W...R....W",
            "G...R....G",
            "W...JJ...W",
            "G...JJ...G",
            "W........W",
            "G...E....G",
            "W...M....W",
            "W........W",
            "LWW....WWL"
        ],
        # Y=3 Tie Beam & Interior props
        [
            "LWWWWWWWWL",
            "W..RR....W",
            "W..RR....W",
            "W..TF....W",
            "W..X.....W",
            "W........W",
            "W...P....W",
            "W........W",
            "W........W",
            "LWWWWWWWWL"
        ],
        # Y=4 Floor 2 Overhang / Tie Beam with Outer Corbels
        [
            "ULLLLLLLLU",
            "LWWWWWWWWL",
            "LWWWWWWWWL",
            "LWWWWWWWWL",
            "LWWWWWWWWL",
            "LWWWWWWWWL",
            "LWWWWWWWWL",
            "LWWWWWWWWL",
            "LWWWWWWWWL",
            "ULLLLLLLLU"
        ],
        # Y=5 Eaves Overhang with Inverted Stairs & Dormer base
        [
            "UUUUUUUUUU",
            "U..RR....U",
            "U..RR....U",
            "U........U",
            "U...G....U",
            "U...G....U",
            "U........U",
            "U........U",
            "U........U",
            "UUUUUUUUUU"
        ],
        # Y=6 Steep Gable Slope Tier 1
        [
            ".OOOOOOOO.",
            ".OWRROWW.",
            ".OWRROWW.",
            ".OWWWWWW.",
            ".OWGWWWW.",
            ".OWWWWWW.",
            ".OWWWWWW.",
            ".OWWWWWW.",
            ".OWWWWWW.",
            ".OOOOOOOO."
        ],
        # Y=7 Steep Gable Slope Tier 2
        [
            "..OOOOOO..",
            "..OWRROW..",
            "..OWRROW..",
            "..OWWWWO..",
            "..OWWWWO..",
            "..OWWWWO..",
            "..OWWWWO..",
            "..OWWWWO..",
            "..OWWWWO..",
            "..OOOOOO.."
        ],
        # Y=8 Steep Gable Slope Tier 3
        [
            "...OOOO...",
            "...ORRO...",
            "...ORRO...",
            "...OWWO...",
            "...OWWO...",
            "...OWWO...",
            "...OWWO...",
            "...OWWO...",
            "...OWWO...",
            "...OOOO..."
        ],
        # Y=9 Ridge Line with Contrasting Stone Trim & Chimney Smoke
        [
            "....SS....",
            "....RR....",
            "....RH....",
            "....SS....",
            "....SS....",
            "....SS....",
            "....SS....",
            "....SS....",
            "....SS....",
            "....SS...."
        ]
    ]
    return {
        "id": "nordic_cottage",
        "name": "北欧斜顶民居 (Nordic Timber Cottage Pro)",
        "category": "residential",
        "tags": ["residential", "nordic", "cozy", "house", "master"],
        "styles": ["nordic_coastal", "medieval_rustic", "mountain_outpost"],
        "author": "MCSettlement Master",
        "description": "大师级北欧人字斜顶木屋，双层外挑立体架构，外露原木立柱与内凹木板立面，全向倒置楼梯雀替挑檐，高耸陡坡屋顶与烟囱冒烟效果",
        "sizeX": sx,
        "sizeY": sy,
        "sizeZ": sz,
        "entrance": {"facing": "SOUTH", "x": 4, "z": 9},
        "palette": COMMON_PALETTE,
        "themeReplacements": THEME_REPLACEMENTS,
        "layers": layers
    }


def build_watchtower():
    # 8x14x8 Highland Deepslate Watchtower with corbel machicolations, battlements, beacon
    sx, sy, sz = 8, 14, 8
    layers = []
    # Y=0 Foundation
    layers.append(["CCCCCCCC","CCCCCCCC","CCCCCCCC","CCCCCCCC","CCCCCCCC","CCCCCCCC","CCCCCCCC","CCCCCCCC"])
    # Y=1..3 Base Shaft
    for y in range(3):
        layers.append([
            "LSSSSSSL",
            "S......S",
            "S......S",
            "S......S",
            "S......S",
            "S......S",
            "S......S",
            "LS....SL" if y < 2 else "LSSSSSSL"
        ])
    # Y=4 Mid Belt Tie Beam
    layers.append(["LLLLLLLL","LSSSSSSL","LS....SL","LS....SL","LS....SL","LS....SL","LSSSSSSL","LLLLLLLL"])
    # Y=5..7 Shaft Mid Tier with Arrow Slits
    for y in range(3):
        layers.append([
            "LSSSSSSL",
            "S......S",
            "S.G..G.S" if y == 1 else "S......S",
            "S......S",
            "S......S",
            "S.G..G.S" if y == 1 else "S......S",
            "S......S",
            "LSSSSSSL"
        ])
    # Y=8 Tie Beam
    layers.append(["LLLLLLLL","LSSSSSSL","LS....SL","LS....SL","LS....SL","LS....SL","LSSSSSSL","LLLLLLLL"])
    # Y=9 Overhang Corbel Tier (Machicolations with Inverted Stairs)
    layers.append(["UUUUUUUU","USSSSSSU","US....SU","US....SU","US....SU","US....SU","USSSSSSU","UUUUUUUU"])
    # Y=10 Overhang Battlement Floor
    layers.append(["SSSSSSSS","S......S","S......S","S......S","S......S","S......S","S......S","SSSSSSSS"])
    # Y=11 Parapet & Arrow Ports
    layers.append(["S.S..S.S",".      .","S      S",".      .",".      .","S      S",".      .","S.S..S.S"])
    # Y=12 Roof Platform & Roof Trusses
    layers.append(["N......N",".OOOOOO.",".OWWWO.",".OWHWO.",".OWWWO.",".OWWWO.",".OOOOOO.","N......N"])
    # Y=13 Tower Spire & Beacon
    layers.append(["........","..OOOO..","..ORRO..","..ORHO..","..OOOO..","........","........","........"])

    return {
        "id": "highland_watchtower",
        "name": "高地戍卫哨塔 (Highland Watchtower Pro)",
        "category": "military",
        "tags": ["military", "defense", "tower", "watchtower", "master"],
        "styles": ["mountain_outpost", "medieval_rustic", "nordic_coastal"],
        "author": "MCSettlement Master",
        "description": "大师级高地防御哨塔，深板岩圆石厚重基座，九层倒置楼梯外挑城堞落石孔（Machicolations），顶部配备瞭望箭台与信标烽火台",
        "sizeX": sx,
        "sizeY": sy,
        "sizeZ": sz,
        "entrance": {"facing": "SOUTH", "x": 3, "z": 7},
        "palette": COMMON_PALETTE,
        "themeReplacements": THEME_REPLACEMENTS,
        "layers": layers
    }


def build_windmill():
    # 9x13x9 Country Windmill with octagonal tapered stone base, rotating blades, grinding mill
    sx, sy, sz = 9, 13, 9
    layers = []
    # Y=0 Foundation
    layers.append(["CCCCCCCCC","CCCCCCCCC","CCCCCCCCC","CCCCCCCCC","CCCCCCCCC","CCCCCCCCC","CCCCCCCCC","CCCCCCCCC","CCCCCCCCC"])
    # Y=1 Base Floor
    layers.append([".LSSSSL.",".S.....S.","LS.....SL","S.......S","S.......S","S.......S","LS.....SL",".S.....S.",".LS...SL."])
    # Y=2 Inset Windows & Flour Grinding Stone
    layers.append([".LSSSSL.",".G..F..G.","LS..K..SL","G.......G","G...A...G","G.......G","LS..T..SL",".G.....G.",".LS...SL."])
    # Y=3 Tapering Belt Beam
    layers.append([".LLLLLL.","LSS..SSL","S......S","S......S","S......S","S......S","S......S","LSS..SSL",".LLLLLL."])
    # Y=4 Upper Floor Base with Corbels
    layers.append(["UUUUUUUUU","USSSSSSSU","US.....SU","US.....SU","US.....SU","US.....SU","US.....SU","USSSSSSSU","UUUUUUUUU"])
    # Y=5 Upper Loft
    layers.append([".LWWWWL.",".W.....W.","LW.....WL","W...X...W","W...P...W","W.......W","LW.....WL",".W.....W.",".LWWWWL."])
    # Y=6 Upper Windows & Sail Hub
    layers.append([".LWWWWL.",".G.....G.","LG.....GL","W.......W","W...L...W","W.......W","LG.....GL",".G.....G.",".LWWWWL."])
    # Y=7 Eaves Corbel
    layers.append(["UUUUUUUUU","UWWWWWWWU","UW.....WU","UW.....WU","UW.....WU","UW.....WU","UW.....WU","UWWWWWWWU","UUUUUUUUU"])
    # Y=8 Roof Slope 1
    layers.append([".OOOOOOO.",".OWWWWO.",".OWWWWO.",".OWWWWO.",".OWWWWO.",".OWWWWO.",".OWWWWO.",".OWWWWO.",".OOOOOOO."])
    # Y=9 Roof Slope 2
    layers.append(["..OOOOO..","..OWWWO..","..OWWWO..","..OWWWO..","..OWWWO..","..OWWWO..","..OWWWO..","..OWWWO..","..OOOOO.."])
    # Y=10 Roof Ridge & Sail Spindle
    layers.append(["...OOO...","...OWO...","...OWO...","...OWO...","...OWO...","...OWO...","...OWO...","...OWO...","...OOO..."])
    # Y=11 Windmill Sail Spindle
    layers.append(["....S....","....S....","....L....","....S....","....S....","....S....","....S....","....S....","....S...."])
    # Y=12 Windmill Sail Cross Blades
    layers.append(["....N....","....N....","NNNNLNNNN","....N....","....N....","....N....","....N....","....N....","....N...."])

    return {
        "id": "country_windmill",
        "name": "乡村风车磨坊 (Country Windmill Pro)",
        "category": "agriculture",
        "tags": ["agriculture", "food", "windmill", "mill", "master"],
        "styles": ["medieval_rustic", "nordic_coastal", "mountain_outpost"],
        "author": "MCSettlement Master",
        "description": "大师级乡村八角收分风车磨坊，圆石与石砖收分基座，上层悬挑木构磨粉阁楼，外挑倒置楼梯挑檐与十字巨型旋转风帆",
        "sizeX": sx,
        "sizeY": sy,
        "sizeZ": sz,
        "entrance": {"facing": "SOUTH", "x": 4, "z": 8},
        "palette": COMMON_PALETTE,
        "themeReplacements": THEME_REPLACEMENTS,
        "layers": layers
    }


def build_tavern():
    # 12x11x11 Adventurer Tavern with banquet hall, guest rooms, bar counter, dormers
    sx, sy, sz = 12, 11, 12
    layers = []
    # Y=0 Foundation
    layers.append(["CCCCCCCCCCCC"] * 12)
    # Y=1 Ground Floor: Bar Counter, Tables & Fireplace
    layers.append([
        "LSSSSSSSSSSL",
        "S....R.....S",
        "S....R.....S",
        "S....R.....S",
        "S..WWWWW...S",
        "S..W...W...S",
        "S..W.T.W...S",
        "S..W...W...S",
        "S..........S",
        "S...JJ.....S",
        "LS........SL",
        "............"
    ])
    # Y=2 Windows & Interior Props
    layers.append([
        "LSGGSSSSGGSL",
        "G....R.....G",
        "G....R.....G",
        "G....R.....G",
        "G..X.K.F...G",
        "W..........G",
        "W..........G",
        "W..........G",
        "G..........G",
        "G...JJ.....G",
        "LS........SL",
        "............"
    ])
    # Y=3 Tie Beams
    layers.append([
        "LLLLLLLLLLLL",
        "LSSSSSSSSSSL",
        "LSSSSSSSSSSL",
        "LSSSSSSSSSSL",
        "LSSSSSSSSSSL",
        "LSSSSSSSSSSL",
        "LSSSSSSSSSSL",
        "LSSSSSSSSSSL",
        "LSSSSSSSSSSL",
        "LSSSSSSSSSSL",
        "LLLLLLLLLLLL",
        "............"
    ])
    # Y=4 Second Floor Overhang with Corbels
    layers.append([
        "UUUUUUUUUUUU",
        "UWWWWWWWWWWU",
        "UWWWWWWWWWWU",
        "UWWWWWWWWWWU",
        "UWWWWWWWWWWU",
        "UWWWWWWWWWWU",
        "UWWWWWWWWWWU",
        "UWWWWWWWWWWU",
        "UWWWWWWWWWWU",
        "UWWWWWWWWWWU",
        "UUUUUUUUUUUU",
        "............"
    ])
    # Y=5 Upper Guest Rooms with Beds & Bookshelves
    layers.append([
        "LWWWWWWWWWWL",
        "W..E.W.E...W",
        "W..M.W.M...W",
        "W....W.....W",
        "WWWWWWWWWWWW",
        "W....W.....W",
        "W..E.W.E...W",
        "W..M.W.M...W",
        "W....W.....W",
        "W....W.....W",
        "LWWWWWWWWWWL",
        "............"
    ])
    # Y=6 Upper Windows & Dormer Bases
    layers.append([
        "LGGWWWWWWGGL",
        "G..........G",
        "G..........G",
        "G..........G",
        "W..........W",
        "W..........W",
        "G..........G",
        "G..........G",
        "G..........G",
        "G..........G",
        "LGGWWWWWWGGL",
        "............"
    ])
    # Y=7 Upper Corbels
    layers.append(["UUUUUUUUUUUU"] * 11 + ["............"])
    # Y=8 Roof Slope 1
    layers.append([
        ".OOOOOOOOOO.",
        ".OWWWWWWWWO.",
        ".OWWWWWWWWO.",
        ".OWWWWWWWWO.",
        ".OWWWWWWWWO.",
        ".OWWWWWWWWO.",
        ".OWWWWWWWWO.",
        ".OWWWWWWWWO.",
        ".OWWWWWWWWO.",
        ".OWWWWWWWWO.",
        ".OOOOOOOOOO.",
        "............"
    ])
    # Y=9 Roof Slope 2
    layers.append([
        "..OOOOOOOO..",
        "..OWWWWWWO..",
        "..OWWWWWWO..",
        "..OWWWWWWO..",
        "..OWWWWWWO..",
        "..OWWWWWWO..",
        "..OWWWWWWO..",
        "..OWWWWWWO..",
        "..OWWWWWWO..",
        "..OWWWWWWO..",
        "..OOOOOOOO..",
        "............"
    ])
    # Y=10 Steep Ridge & Chimney
    layers.append([
        "...SSSSSS...",
        "...SWWWS...",
        "...SWRHS...",
        "...SWWWS...",
        "...SWWWS...",
        "...SWWWS...",
        "...SWWWS...",
        "...SWWWS...",
        "...SWWWS...",
        "...SWWWS...",
        "...SSSSSS...",
        "............"
    ])

    return {
        "id": "adventurer_tavern",
        "name": "冒险者酒馆 (Adventurer Tavern Pro)",
        "category": "commercial",
        "tags": ["tavern", "inn", "commercial", "social", "master"],
        "styles": ["medieval_rustic", "nordic_coastal", "mountain_outpost"],
        "author": "MCSettlement Master",
        "description": "大师级双层外挑大酒馆，首层设环形吧台、餐饮长桌与巨型砖石壁炉，二层悬挑客房、书架床榻与侧向老虎窗，全外露原木架构与倒置楼梯挑檐",
        "sizeX": sx,
        "sizeY": sy,
        "sizeZ": sz,
        "entrance": {"facing": "SOUTH", "x": 5, "z": 10},
        "palette": COMMON_PALETTE,
        "themeReplacements": THEME_REPLACEMENTS,
        "layers": layers
    }


def build_chapel():
    # 9x12x13 Village Chapel with Gothic high arches, rose stained glass window, steeple bell spire
    sx, sy, sz = 9, 12, 13
    layers = []
    # Y=0 Foundation
    layers.append(["CCCCCCCCC"] * sz)
    # Y=1 Floor & Pews (Chairs/Stairs)
    layers.append([
        "LSSSSL..",
        "S....S..",
        "S....S..",
        "S....S..",
        "S.UU.S..",
        "S....S..",
        "S.UU.S..",
        "S....S..",
        "S.UU.S..",
        "S....S..",
        "S.UU.S..",
        "LSSSSSL.",
        "........"
    ])
    # Y=2 Inset Stained Glass Windows & Altar
    layers.append([
        "LSGGSL..",
        "G.T..G..",
        "G....G..",
        "G....G..",
        "G....G..",
        "G....G..",
        "G....G..",
        "G....G..",
        "G....G..",
        "G....G..",
        "G....G..",
        "LS..SL..",
        "........"
    ])
    # Y=3 Gothic Arch Tops
    layers.append([
        "LSSSSL..",
        "S....S..",
        "S....S..",
        "S....S..",
        "S....S..",
        "S....S..",
        "S....S..",
        "S....S..",
        "S....S..",
        "S....S..",
        "S....S..",
        "LSSSSSL.",
        "........"
    ])
    # Y=4 Tie Beams
    layers.append([
        "LLLLLL..",
        "LSSSSL..",
        "LSSSSL..",
        "LSSSSL..",
        "LSSSSL..",
        "LSSSSL..",
        "LSSSSL..",
        "LSSSSL..",
        "LSSSSL..",
        "LSSSSL..",
        "LSSSSL..",
        "LLLLLL..",
        "........"
    ])
    # Y=5 Inverted Corbels & Eaves
    layers.append(["UUUUUUUUU"] * 12 + ["........."])
    # Y=6 Steep Gable Slope 1
    layers.append([
        ".OOOOOOO.",
        ".OWWWWO.",
        ".OWWWWO.",
        ".OWWWWO.",
        ".OWWWWO.",
        ".OWWWWO.",
        ".OWWWWO.",
        ".OWWWWO.",
        ".OWWWWO.",
        ".OWWWWO.",
        ".OWWWWO.",
        ".OOOOOOO.",
        "........."
    ])
    # Y=7 Steep Gable Slope 2
    layers.append([
        "..OOOOO..",
        "..OWWWO..",
        "..OWWWO..",
        "..OWWWO..",
        "..OWWWO..",
        "..OWWWO..",
        "..OWWWO..",
        "..OWWWO..",
        "..OWWWO..",
        "..OWWWO..",
        "..OWWWO..",
        "..OOOOO..",
        "........."
    ])
    # Y=8 Ridge & Bell Spire Base
    layers.append([
        "...OOO...",
        "...OSS...",
        "...OSS...",
        "...OWO...",
        "...OWO...",
        "...OWO...",
        "...OWO...",
        "...OWO...",
        "...OWO...",
        "...OWO...",
        "...OSS...",
        "...OOO...",
        "........."
    ])
    # Y=9 Bell Tower Shaft
    layers.append([
        ".........",
        "...LSL...",
        "...SPS...",
        ".........",
        ".........",
        ".........",
        ".........",
        ".........",
        ".........",
        ".........",
        ".........",
        ".........",
        "........."
    ])
    # Y=10 Bell Tower Corbels
    layers.append([
        ".........",
        "..UUUUU..",
        "..U...U..",
        ".........",
        ".........",
        ".........",
        ".........",
        ".........",
        ".........",
        ".........",
        ".........",
        ".........",
        "........."
    ])
    # Y=11 Spire
    layers.append([
        ".........",
        "...SSS...",
        "...SSS...",
        ".........",
        ".........",
        ".........",
        ".........",
        ".........",
        ".........",
        ".........",
        ".........",
        ".........",
        "........."
    ])

    return {
        "id": "village_chapel",
        "name": "村落礼拜堂 (Village Chapel Pro)",
        "category": "culture",
        "tags": ["culture", "religion", "church", "chapel", "master"],
        "styles": ["medieval_rustic", "mountain_outpost", "nordic_coastal"],
        "author": "MCSettlement Master",
        "description": "大师级哥特式村落礼拜堂，高耸尖拱窗、深邃祈祷长椅动线、圣坛讲台，全向倒置楼梯拱圈与挑檐，前端耸立神圣敲钟尖塔",
        "sizeX": sx,
        "sizeY": sy,
        "sizeZ": sz,
        "entrance": {"facing": "SOUTH", "x": 4, "z": 11},
        "palette": COMMON_PALETTE,
        "themeReplacements": THEME_REPLACEMENTS,
        "layers": layers
    }


def build_lumber_mill():
    # 10x8x10 Lumber Mill with open timber framing, waterwheel shaft, crane, log piles
    sx, sy, sz = 10, 8, 10
    layers = []
    layers.append(["CCCCCCCCCC"] * sz)
    layers.append([
        "LSSSSSSL..",
        "S......S..",
        "S......S.L",
        "S......S.L",
        "LSSSSSSL.L",
        "L......L.L",
        "L......L.L",
        "L......L.L",
        "LLLLLLLL..",
        ".........."
    ])
    layers.append([
        "LSGGGGSL..",
        "G..T...G..",
        "G..X...G.L",
        "G..F...G.L",
        "LSGGGGSL.L",
        "L.LLL..L.L",
        "L.LLL..L.L",
        "L......L.L",
        "LLLLLLLL..",
        ".........."
    ])
    layers.append([
        "LSSSSSSL..",
        "S......S..",
        "S......S.L",
        "S......S.L",
        "LSSSSSSL.L",
        "L......L.L",
        "L......L.L",
        "L......L.L",
        "LLLLLLLL..",
        ".........."
    ])
    layers.append(["UUUUUUUUUU"] * sz)
    layers.append([
        ".OOOOOOOO.",
        ".OWWWWWWO.",
        ".OWWWWWWO.",
        ".OWWWWWWO.",
        ".OWWWWWWO.",
        ".OWWWWWWO.",
        ".OWWWWWWO.",
        ".OWWWWWWO.",
        ".OOOOOOOO.",
        ".........."
    ])
    layers.append([
        "..OOOOOO..",
        "..OWWWWO..",
        "..OWWWWO..",
        "..OWWWWO..",
        "..OWWWWO..",
        "..OWWWWO..",
        "..OWWWWO..",
        "..OWWWWO..",
        "..OOOOOO..",
        ".........."
    ])
    layers.append([
        "...SSSS...",
        "...SWWS...",
        "...SWWS...",
        "...SWWS...",
        "...SWWS...",
        "...SWWS...",
        "...SWWS...",
        "...SWWS...",
        "...SSSS...",
        ".........."
    ])

    return {
        "id": "lumber_mill",
        "name": "林场伐木工坊 (Lumber Mill Pro)",
        "category": "industry",
        "tags": ["industry", "wood", "mill", "forestry", "master"],
        "styles": ["medieval_rustic", "nordic_coastal", "mountain_outpost"],
        "author": "MCSettlement Master",
        "description": "大师级开放式水力伐木工坊，大跨度原木外挑桁架，堆料区、锯木加工台与外挑倒置楼梯大斜顶",
        "sizeX": sx,
        "sizeY": sy,
        "sizeZ": sz,
        "entrance": {"facing": "SOUTH", "x": 4, "z": 8},
        "palette": COMMON_PALETTE,
        "themeReplacements": THEME_REPLACEMENTS,
        "layers": layers
    }


def build_wizard_tower():
    # 9x15x9 Wizard Tower with floating balcony platforms, inverted stone corbels, observatory dome
    sx, sy, sz = 9, 15, 9
    layers = []
    layers.append(["CCCCCCCCC"] * sz)
    for y in range(4):
        layers.append([
            ".LSSSSL.",
            "LS....SL",
            "S......S",
            "S......S",
            "S......S",
            "S......S",
            "LS....SL",
            ".LSSSSL.",
            "........."
        ])
    layers.append(["UUUUUUUUU"] * sz)
    for y in range(3):
        layers.append([
            ".LWWWWL.",
            "LW....WL",
            "W..M...W",
            "W..T...W",
            "W..P...W",
            "W......W",
            "LW....WL",
            ".LWWWWL.",
            "........."
        ])
    layers.append(["UUUUUUUUU"] * sz)
    for y in range(3):
        layers.append([
            "SSSSSSSSS",
            "S.......S",
            "S..G.G..S",
            "S.......S",
            "S...H...S",
            "S.......S",
            "S..G.G..S",
            "S.......S",
            "SSSSSSSSS"
        ])
    layers.append(["UUUUUUUUU"] * sz)
    layers.append([
        ".OOOOOOO.",
        ".OWWWWO.",
        ".OWWWWO.",
        ".OWWWWO.",
        ".OWWWWO.",
        ".OWWWWO.",
        ".OWWWWO.",
        ".OOOOOOO.",
        "........."
    ])
    layers.append([
        "..OOOOO..",
        "..ORRO..",
        "..ORHO..",
        "..ORRO..",
        "..OOOOO..",
        ".........",
        ".........",
        ".........",
        "........."
    ])
    layers.append([
        "...SSS...",
        "...SPS...",
        "...SSS...",
        ".........",
        ".........",
        ".........",
        ".........",
        ".........",
        "........."
    ])

    return {
        "id": "wizard_tower",
        "name": "法师秘术高塔 (Wizard Tower Pro)",
        "category": "mystic",
        "tags": ["magic", "tower", "alchemy", "enchanting", "master"],
        "styles": ["mountain_outpost", "medieval_rustic", "nordic_coastal"],
        "author": "MCSettlement Master",
        "description": "大师级法师秘塔，多层悬挑外露倒置石砖雀替，环形藏书阁、附魔台与顶层星象观测露台",
        "sizeX": sx,
        "sizeY": sy,
        "sizeZ": sz,
        "entrance": {"facing": "SOUTH", "x": 4, "z": 7},
        "palette": COMMON_PALETTE,
        "themeReplacements": THEME_REPLACEMENTS,
        "layers": layers
    }


def build_granary():
    # 8x9x8 Stilt Farmstead Granary with raised log stilts, rodent-proof corbels, double steep roof
    sx, sy, sz = 8, 9, 8
    layers = []
    layers.append(["CCCCCCCC"] * sz)
    # Stilt layer
    layers.append(["L......L",".      .","L      L",".      .",".      .","L      L",".      .","L......L"])
    # Rodent-proof corbel tier
    layers.append(["UUUUUUUU","U......U","U......U","U......U","U......U","U......U","U......U","UUUUUUUU"])
    # Storage box walls
    layers.append(["LWWWWWWL","W......W","W..X...W","W..X...W","W......W","W..X...W","W......W","LWW..WWL"])
    layers.append(["LWWWWWWL","W......W","W..X...W","W..X...W","W......W","W..X...W","W......W","LWW..WWL"])
    layers.append(["LLLLLLLL"] * sz)
    layers.append(["UUUUUUUU"] * sz)
    layers.append([".OOOOOO.",".OWWWWO.",".OWWWWO.",".OWWWWO.",".OWWWWO.",".OWWWWO.",".OWWWWO.",".OOOOOO."])
    layers.append(["..SSSS..","..SWWS..","..SWWS..","..SWWS..","..SWWS..","..SWWS..","..SWWS..","..SSSS.."])

    return {
        "id": "farmstead_granary",
        "name": "高架防潮粮仓 (Farmstead Granary Pro)",
        "category": "agriculture",
        "tags": ["agriculture", "storage", "food", "granary", "master"],
        "styles": ["medieval_rustic", "nordic_coastal", "mountain_outpost"],
        "author": "MCSettlement Master",
        "description": "大师级高架防潮粮仓，下层原木立柱架空防潮，倒置楼梯防鼠挑檐，密闭双层储粮箱与透气斜顶",
        "sizeX": sx,
        "sizeY": sy,
        "sizeZ": sz,
        "entrance": {"facing": "SOUTH", "x": 3, "z": 7},
        "palette": COMMON_PALETTE,
        "themeReplacements": THEME_REPLACEMENTS,
        "layers": layers
    }


def build_town_hall():
    # 12x12x12 Grand Town Hall with portico columns, conference hall, bell tower, cross-gable roof
    sx, sy, sz = 12, 12, 12
    layers = []
    layers.append(["CCCCCCCCCCCC"] * sz)
    layers.append([
        "LSSSSSSSSSSL",
        "S..........S",
        "S..WWWWWW..S",
        "S..W....W..S",
        "S..W....W..S",
        "S..WWWWWW..S",
        "S..........S",
        "S..........S",
        "LSSSSSSSSSSL",
        "L..L....L..L",
        "L..L....L..L",
        "............"
    ])
    layers.append([
        "LSGGSSSSGGSL",
        "G..........G",
        "G..T.P..T..G",
        "G..........G",
        "G..........G",
        "G..........G",
        "G..........G",
        "G...JJ.....G",
        "LS........SL",
        "L..L....L..L",
        "L..L....L..L",
        "............"
    ])
    layers.append(["LLLLLLLLLLLL"] * sz)
    layers.append(["UUUUUUUUUUUU"] * sz)
    layers.append([
        "LWWWWWWWWWWL",
        "W..........W",
        "W..M....M..W",
        "W..........W",
        "W..........W",
        "W..X....X..W",
        "W..........W",
        "W..........W",
        "LWWWWWWWWWWL",
        "............",
        "............",
        "............"
    ])
    layers.append(["UUUUUUUUUUUU"] * sz)
    layers.append([
        ".OOOOOOOOOO.",
        ".OWWWWWWWWO.",
        ".OWWWWWWWWO.",
        ".OWWWWWWWWO.",
        ".OWWWWWWWWO.",
        ".OWWWWWWWWO.",
        ".OWWWWWWWWO.",
        ".OWWWWWWWWO.",
        ".OOOOOOOOOO.",
        "............",
        "............",
        "............"
    ])
    layers.append([
        "..OOOOOOOO..",
        "..OWWWWWWO..",
        "..OWWWWWWO..",
        "..OWWWWWWO..",
        "..OWWWWWWO..",
        "..OWWWWWWO..",
        "..OWWWWWWO..",
        "..OWWWWWWO..",
        "..OOOOOOOO..",
        "............",
        "............",
        "............"
    ])
    layers.append([
        "...OOOOOO...",
        "...OWWWWO...",
        "...OWWWWO...",
        "...OWWWWO...",
        "...OWWWWO...",
        "...OWWWWO...",
        "...OWWWWO...",
        "...OWWWWO...",
        "...OOOOOO...",
        "............",
        "............",
        "............"
    ])
    layers.append([
        "....SSSS....",
        "....SWWS....",
        "....SWWS....",
        "....SWWS....",
        "....SWWS....",
        "....SWWS....",
        "....SWWS....",
        "....SWWS....",
        "....SSSS....",
        "............",
        "............",
        "............"
    ])
    layers.append([
        "....LSSL....",
        "....S..S....",
        "....S..S....",
        "....LSSL....",
        "............",
        "............",
        "............",
        "............",
        "............",
        "............",
        "............",
        "............"
    ])
    layers.append([
        "....SSSS....",
        "....SPS....",
        "....SSSS....",
        "............",
        "............",
        "............",
        "............",
        "............",
        "............",
        "............",
        "............",
        "............"
    ])

    return {
        "id": "town_hall",
        "name": "城镇市政大厅 (Town Hall Pro)",
        "category": "government",
        "tags": ["government", "civic", "hall", "town_hall", "master"],
        "styles": ["medieval_rustic", "mountain_outpost", "nordic_coastal"],
        "author": "MCSettlement Master",
        "description": "大师级宏伟城镇市政大厅，气派原木立柱门廊，会议长桌与双层挑高议事厅，全向倒置楼梯挑檐，高耸双坡陡顶与中央报时钟楼",
        "sizeX": sx,
        "sizeY": sy,
        "sizeZ": sz,
        "entrance": {"facing": "SOUTH", "x": 5, "z": 10},
        "palette": COMMON_PALETTE,
        "themeReplacements": THEME_REPLACEMENTS,
        "layers": layers
    }


def normalize_preset(p):
    sx = p["sizeX"]
    sz = p["sizeZ"]
    layers = p["layers"]
    p["sizeY"] = len(layers)
    norm_layers = []
    for layer in layers:
        norm_layer = []
        for z in range(sz):
            if z < len(layer):
                row = layer[z]
                if len(row) < sx:
                    row = row + "." * (sx - len(row))
                elif len(row) > sx:
                    row = row[:sx]
            else:
                row = "." * sx
            norm_layer.append(row)
        norm_layers.append(norm_layer)
    p["layers"] = norm_layers

    # For nordic_cottage specific palette
    if p["id"] == "nordic_cottage":
        p["palette"] = dict(COMMON_PALETTE)
        p["palette"]["W"] = "minecraft:spruce_planks"
        p["palette"]["L"] = "minecraft:dark_oak_log[axis=y]"
        p["palette"]["O"] = "minecraft:spruce_slab"
        p["themeReplacements"] = {
            "medieval_rustic": {
                "minecraft:spruce_planks": "minecraft:oak_planks",
                "minecraft:dark_oak_log[axis=y]": "minecraft:oak_log[axis=y]",
                "minecraft:spruce_slab": "minecraft:oak_slab"
            },
            "mountain_outpost": {
                "minecraft:cobblestone": "minecraft:cobbled_deepslate",
                "minecraft:spruce_planks": "minecraft:dark_oak_planks",
                "minecraft:dark_oak_log[axis=y]": "minecraft:deepslate_bricks"
            }
        }
    return p


def main():
    builders = [
        build_blacksmith,
        build_nordic_cottage,
        build_watchtower,
        build_windmill,
        build_tavern,
        build_chapel,
        build_lumber_mill,
        build_wizard_tower,
        build_granary,
        build_town_hall
    ]

    print(f"Rebuilding {len(builders)} master presets into: {PRESETS_DIR}")
    for fn in builders:
        p = fn()
        p = normalize_preset(p)
        out_path = os.path.join(PRESETS_DIR, f"{p['id']}.json")
        with open(out_path, "w", encoding="utf-8") as f:
            json.dump(p, f, indent=2, ensure_ascii=False)
        print(f"  [OK] Reconstructed & Normalized: {p['id']} ({p['sizeX']}x{p['sizeY']}x{p['sizeZ']}) -> {out_path}")

if __name__ == "__main__":
    main()
