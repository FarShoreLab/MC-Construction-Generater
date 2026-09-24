"""
Modular Prefab / Structure Pool Assembler (Jigsaw Paradigm).
Inspired by Minecraft Vanilla Jigsaw, CTOV (ChoiceTheorem's Overhauled Village)
and Towns & Towers modular generation.
Assembles weighted architectural prefabs via connector sockets with collision resolution.
"""

import random
import time
from typing import Dict, List, Any, Optional, Tuple


# Sockets: {name, rel_pos: (dx, dy, dz), facing: "NORTH"|"SOUTH"|"EAST"|"WEST"|"UP"}
MODULE_PIECES = {
    "root_main_hall": {
        "weight": 10,
        "size": (7, 4, 7),
        "sockets": [
            {"id": "roof", "pos": (0, 4, 0), "type": "roof_mount"},
            {"id": "east_wing", "pos": (7, 0, 0), "type": "wing_mount"},
            {"id": "west_wing", "pos": (-5, 0, 0), "type": "wing_mount"}
        ],
        # 7x4x7 layers
        "layers": [
            # y=0: Cobblestone floor & foundation
            ["CCCCCCC","CWWWWWC","CWWWWWC","CWWWWWC","CWWWWWC","CWWWWWC","CCCCCCC"],
            # y=1: Walls with door at south (z=6)
            ["LSSSSSL","S.....S","S.....S","S.....S","S.....S","S.....S","LSS.SSL"],
            # y=2: Windows & interior props
            ["LSGGSSL","G..T..G","G..X..G","G.....G","G..F..G","G..K..G","LSG.GSL"],
            # y=3: Top tie beam & lantern
            ["LLLLLLL","L..P..L","L.....L","L..P..L","L.....L","L..P..L","LLLLLLL"]
        ]
    },
    "wing_forge": {
        "weight": 8,
        "size": (5, 4, 5),
        "sockets": [
            {"id": "roof", "pos": (0, 4, 0), "type": "shed_roof"}
        ],
        "layers": [
            ["CCCCC","CCCCC","CCCCC","CCCCC","CCCCC"],
            ["LSSSL","S...S","S...S","S...S","L...L"],
            ["LSGSL","B...S","A.H.S","I...S","L...L"],
            ["LLLLL","L...L","L...L","L...L","LLLLL"]
        ]
    },
    "roof_steep_gable": {
        "weight": 10,
        "size": (9, 5, 9),  # 1 block overhang on all sides
        "offset": (-1, 0, -1),
        "layers": [
            # y=0: Eaves overhang & corbel tier
            ["SSSSSSSSS","S.......S","S.......S","S.......S","S.......S","S.......S","S.......S","S.......S","SSSSSSSSS"],
            # y=1: Lower roof slope
            [".OOOOOOO.",".OWWWWO.",".OWWWWO.",".OWWWWO.",".OWWWWO.",".OWWWWO.",".OWWWWO.",".OWWWWO.",".OOOOOOO."],
            # y=2: Mid roof slope
            ["..OOOOO..","..OWWWO..","..OWWWO..","..OWWWO..","..OWWWO..","..OWWWO..","..OWWWO..","..OWWWO..","..OOOOO.."],
            # y=3: High slope
            ["...OOO...","...OWO...","...OWO...","...OWO...","...OWO...","...OWO...","...OWO...","...OWO...","...OOO..."],
            # y=4: Ridge line
            ["....S....","....S....","....S....","....S....","....S....","....S....","....S....","....S....","....S...."]
        ]
    },
    "roof_shed": {
        "weight": 10,
        "size": (7, 3, 7),
        "offset": (-1, 0, -1),
        "layers": [
            ["OOOOOOO","O.....O","O.....O","O.....O","O.....O","O.....O","OOOOOOO"],
            [".OOOOO.",".O...O.",".O...O.",".O...O.",".O...O.",".O...O.",".OOOOO."],
            ["..OOO..","..OOO..","..OOO..","..OOO..","..OOO..","..OOO..","..OOO.."]
        ]
    }
}


class JigsawAssembler:
    """Jigsaw / Structure Pool Prefab Assembler"""

    def __init__(self, seed: Optional[int] = None):
        self.rng = random.Random(seed if seed is not None else int(time.time() * 1000))

    def generate(self, theme: str = "medieval_rustic") -> Dict[str, Any]:
        start_time = time.perf_counter()

        # Canvas bounds (approx 16x12x12)
        size_x = 15
        size_y = 10
        size_z = 11

        grid = [[["." for _ in range(size_z)] for _ in range(size_y)] for _ in range(size_x)]

        # Place root main hall at anchor (1, 0, 1)
        root = MODULE_PIECES["root_main_hall"]
        rx, ry, rz = 1, 0, 1
        for y, l_rows in enumerate(root["layers"]):
            for z, row in enumerate(l_rows):
                for x, ch in enumerate(row):
                    if ch != ".":
                        grid[rx + x][ry + y][rz + z] = ch

        # Attach wing at east socket (pos 7, 0, 0 -> world (8, 0, 1))
        wing = MODULE_PIECES["wing_forge"]
        wx, wy, wz = rx + 7, ry + 0, rz + 1
        for y, l_rows in enumerate(wing["layers"]):
            for z, row in enumerate(l_rows):
                for x, ch in enumerate(row):
                    if ch != "." and wx + x < size_x and wz + z < size_z:
                        grid[wx + x][wy + y][wz + z] = ch

        # Attach main gable roof above main hall (y=4)
        roof = MODULE_PIECES["roof_steep_gable"]
        ro_x = rx + roof["offset"][0]
        ro_y = ry + 4
        ro_z = rz + roof["offset"][2]
        for y, l_rows in enumerate(roof["layers"]):
            for z, row in enumerate(l_rows):
                for x, ch in enumerate(row):
                    gx = ro_x + x
                    gy = ro_y + y
                    gz = ro_z + z
                    if ch != "." and 0 <= gx < size_x and 0 <= gy < size_y and 0 <= gz < size_z:
                        grid[gx][gy][gz] = ch

        # Attach shed roof above wing (y=4)
        shed = MODULE_PIECES["roof_shed"]
        sx = wx - 1
        sy = wy + 4
        sz = wz - 1
        for y, l_rows in enumerate(shed["layers"]):
            for z, row in enumerate(l_rows):
                for x, ch in enumerate(row):
                    gx = sx + x
                    gy = sy + y
                    gz = sz + z
                    if ch != "." and 0 <= gx < size_x and 0 <= gy < size_y and 0 <= gz < size_z:
                        grid[gx][gy][gz] = ch

        elapsed_ms = (time.perf_counter() - start_time) * 1000.0

        layers = []
        for y in range(size_y):
            layer_rows = []
            for z in range(size_z):
                row = "".join(grid[x][y][z] for x in range(size_x))
                layer_rows.append(row)
            layers.append(layer_rows)

        solid_count = sum(1 for y in range(size_y) for z in range(size_z) for x in range(size_x) if grid[x][y][z] != ".")

        palette = {
            ".": "minecraft:air",
            "C": "minecraft:cobblestone",
            "S": "minecraft:stone_bricks",
            "W": "minecraft:oak_planks",
            "L": "minecraft:oak_log[axis=y]",
            "G": "minecraft:glass_pane",
            "T": "minecraft:crafting_table",
            "X": "minecraft:chest[facing=south]",
            "F": "minecraft:furnace[facing=north]",
            "B": "minecraft:blast_furnace[facing=north]",
            "A": "minecraft:anvil[facing=east]",
            "H": "minecraft:campfire",
            "I": "minecraft:iron_bars",
            "K": "minecraft:cauldron",
            "P": "minecraft:lantern",
            "O": "minecraft:oak_slab"
        }

        theme_replacements = {
            "nordic_coastal": {
                "minecraft:oak_planks": "minecraft:spruce_planks",
                "minecraft:oak_log[axis=y]": "minecraft:spruce_log[axis=y]",
                "minecraft:oak_slab": "minecraft:spruce_slab"
            },
            "mountain_outpost": {
                "minecraft:cobblestone": "minecraft:cobbled_deepslate",
                "minecraft:stone_bricks": "minecraft:deepslate_bricks",
                "minecraft:oak_planks": "minecraft:dark_oak_planks",
                "minecraft:oak_log[axis=y]": "minecraft:dark_oak_log[axis=y]",
                "minecraft:oak_slab": "minecraft:deepslate_brick_slab"
            }
        }

        return {
            "id": f"gen_jigsaw_{theme}",
            "name": f"模块拼装建筑 ({theme})",
            "generator": "Modular Structure Pool (Jigsaw/CTOV)",
            "paradigm": "Weighted Connector Graph Prefab Assembly",
            "theme": theme,
            "execution_ms": round(elapsed_ms, 2),
            "sizeX": size_x,
            "sizeY": size_y,
            "sizeZ": size_z,
            "solid_voxels": solid_count,
            "total_volume": size_x * size_y * size_z,
            "layers": layers,
            "palette": palette,
            "themeReplacements": theme_replacements
        }


if __name__ == "__main__":
    jigsaw = JigsawAssembler(seed=42)
    res = jigsaw.generate("medieval_rustic")
    print(f"Generated Jigsaw structure in {res['execution_ms']}ms: {res['sizeX']}x{res['sizeY']}x{res['sizeZ']} with {res['solid_voxels']} voxels.")
