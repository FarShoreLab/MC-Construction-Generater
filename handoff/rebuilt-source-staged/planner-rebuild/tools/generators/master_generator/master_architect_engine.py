"""
Master Architectural Procedural Engine (MCSettlement Master V2).
Implements advanced Minecraft building techniques:
- Inset wall bays with protruding log framework (立面进深)
- Upside-down stairs corbel brackets & eaves overhang (倒置楼梯挑檐与雀替)
- High-pitched double-slope gable roof with contrasting trim & dormer (双坡陡顶与老虎窗)
- Authentic brick chimney with hearth & smoke (壁炉烟囱)
- Full functional interior furnishing (精细内饰与家具)
"""

import time
from typing import Dict, List, Any, Optional


class MasterArchitectEngine:
    """Master Procedural Building Synthesizer"""

    def __init__(self, size_x: int = 11, size_z: int = 11):
        self.sx = size_x
        self.sz = size_z

    def generate(self, theme: str = "medieval_rustic") -> Dict[str, Any]:
        start_time = time.perf_counter()

        # Dimensions
        # Width: 11, Length: 11, Height: 11
        sx = self.sx
        sz = self.sz
        sy = 11

        grid = [[["." for _ in range(sz)] for _ in range(sy)] for _ in range(sx)]

        # --- Y=0: Foundation ---
        # Outer border is Cobblestone, center is Oak/Spruce planks floor with carpet borders
        for x in range(sx):
            for z in range(sz):
                if x in [1, sx - 2] or z in [1, sz - 2] or x in [0, sx - 1] or z in [0, sz - 1]:
                    grid[x][0][z] = "C"  # Stone foundation
                else:
                    grid[x][0][z] = "W"  # Wooden floor

        # --- Y=1..3: Ground Floor with Protruding Framing & Inset Walls ---
        for y in range(1, 4):
            # Protruding Frame Posts at outer envelope (X in [1, sx-2], Z in [1, sz-2])
            for cx in [1, 5, sx - 2]:
                for cz in [1, sz - 2]:
                    grid[cx][y][cz] = "L"
            for cz in [1, 5, sz - 2]:
                for cx in [1, sx - 2]:
                    grid[cx][y][cz] = "L"

            # Inset Walls: positioned along x=1..sx-2, z=1..sz-2
            # Front facade (z=1):
            for x in range(2, sx - 2):
                if x == 5:
                    continue
                if x == 3 and y in [1, 2]:
                    # Main entrance door bay
                    grid[x][y][1] = "."
                elif x in [7, 8] and y == 2:
                    # Inset window with glass pane
                    grid[x][y][1] = "G"
                else:
                    grid[x][y][1] = "S" if y == 1 else "W"

            # Back facade (z=sz-2):
            for x in range(2, sx - 2):
                if x == 5:
                    continue
                if x in [3, 7] and y == 2:
                    grid[x][y][sz - 2] = "G"
                else:
                    grid[x][y][sz - 2] = "S" if y == 1 else "W"

            # Left & Right facades
            for z in range(2, sz - 2):
                if z == 5:
                    continue
                if z in [3, 7] and y == 2:
                    grid[1][y][z] = "G"
                    grid[sx - 2][y][z] = "G"
                else:
                    grid[1][y][z] = "S" if y == 1 else "W"
                    grid[sx - 2][y][z] = "S" if y == 1 else "W"

        # Horizontal Tie-beam at Y=4 (Protruding beam header)
        for x in range(1, sx - 1):
            grid[x][4][1] = "L"
            grid[x][4][sz - 2] = "L"
        for z in range(1, sz - 1):
            grid[1][4][z] = "L"
            grid[sx - 2][4][z] = "L"

        # --- Y=4: Corbel Under-eaves (Upside-down stair brackets 'U') ---
        # Along east & west outer line (x=0 and x=sx-1)
        for z in range(sz):
            grid[0][4][z] = "U"  # Inverted corbel supporting outer eave
            grid[sx - 1][4][z] = "U"

        # --- Interior Furnishings (Y=1..3) ---
        # Hearth & Chimney at northeast corner (x=8, z=8)
        for cy in range(1, 10):
            grid[8][cy][8] = "R"  # Brick chimney flue
        grid[8][1][7] = "H"      # Campfire in hearth
        grid[8][1][6] = "I"      # Iron bar spark guard
        grid[8][10][8] = "H"     # Chimney smoke top

        # Furniture & Props
        grid[2][1][8] = "E"      # Bed
        grid[3][1][8] = "M"      # Bookshelf
        grid[2][1][4] = "T"      # Crafting table
        grid[2][1][3] = "X"      # Double chest
        grid[2][1][2] = "F"      # Furnace
        grid[7][1][2] = "B"      # Blast furnace
        grid[8][1][2] = "A"      # Anvil
        grid[8][1][3] = "K"      # Cauldron
        grid[5][3][5] = "P"      # Suspended lantern from ceiling

        # Floor Carpet accents
        grid[4][1][4] = "J"
        grid[5][1][4] = "J"
        grid[4][1][5] = "J"
        grid[5][1][5] = "J"

        # --- Y=5..9: Steep Double-Pitch Gable Roof with Contrasting Trim ---
        # Roof spans along X axis, sloping upwards towards center (x=5 is ridge)
        # Pitch:
        # x=0, 10 -> Y=5 (Eave)
        # x=1, 9  -> Y=6
        # x=2, 8  -> Y=7
        # x=3, 7  -> Y=8
        # x=4, 6  -> Y=9
        # x=5     -> Y=10 (Ridge)
        roof_profile = {
            0: 5, 10: 5,
            1: 6, 9: 6,
            2: 7, 8: 7,
            3: 8, 7: 8,
            4: 9, 6: 9,
            5: 10
        }

        for x, ry in roof_profile.items():
            for z in range(sz):
                is_front_back_trim = (z == 0 or z == sz - 1)
                # Outer trim uses Stone Bricks ('S'), roof surface uses Wood Slabs/Stairs ('O')
                mat = "S" if is_front_back_trim else "O"

                # Invert corbel beneath gable trim
                if is_front_back_trim and ry > 5:
                    grid[x][ry - 1][z] = "U"

                grid[x][ry][z] = mat

                # Infill gable triangles (front z=1, back z=sz-2)
                for fill_y in range(5, ry):
                    if z in [1, sz - 2] and 1 <= x <= sx - 2:
                        grid[x][fill_y][z] = "W"

        # --- Dormer Window (老虎窗) on West Roof Slope (x=2..4, z=5) ---
        grid[2][7][5] = "G"  # Dormer window glass
        grid[2][8][5] = "S"  # Dormer roof arch
        grid[3][8][5] = "O"

        elapsed_ms = (time.perf_counter() - start_time) * 1000.0

        # Layers representation
        layers = []
        for y in range(sy):
            layer_rows = []
            for z in range(sz):
                row = "".join(grid[x][y][z] for x in range(sx))
                layer_rows.append(row)
            layers.append(layer_rows)

        solid_count = sum(1 for y in range(sy) for z in range(sz) for x in range(sx) if grid[x][y][z] != ".")

        palette = {
            ".": "minecraft:air",
            "C": "minecraft:cobblestone",
            "S": "minecraft:stone_bricks",
            "W": "minecraft:oak_planks",
            "L": "minecraft:oak_log[axis=y]",
            "G": "minecraft:glass_pane",
            "U": "minecraft:oak_stairs[half=top,facing=south]",
            "O": "minecraft:oak_slab",
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
            "J": "minecraft:red_carpet"
        }

        theme_replacements = {
            "nordic_coastal": {
                "minecraft:oak_planks": "minecraft:spruce_planks",
                "minecraft:oak_log[axis=y]": "minecraft:spruce_log[axis=y]",
                "minecraft:oak_slab": "minecraft:spruce_slab",
                "minecraft:oak_stairs[half=top,facing=south]": "minecraft:spruce_stairs[half=top,facing=south]",
                "minecraft:red_carpet": "minecraft:blue_carpet"
            },
            "mountain_outpost": {
                "minecraft:cobblestone": "minecraft:cobbled_deepslate",
                "minecraft:stone_bricks": "minecraft:deepslate_bricks",
                "minecraft:oak_planks": "minecraft:dark_oak_planks",
                "minecraft:oak_log[axis=y]": "minecraft:dark_oak_log[axis=y]",
                "minecraft:oak_slab": "minecraft:deepslate_brick_slab",
                "minecraft:oak_stairs[half=top,facing=south]": "minecraft:deepslate_brick_stairs[half=top,facing=south]",
                "minecraft:red_carpet": "minecraft:gray_carpet"
            }
        }

        return {
            "id": f"gen_master_{theme}",
            "name": f"大师级重构建筑 ({theme})",
            "generator": "Master Architectural Procedural Engine",
            "paradigm": "Protruding Framing, Inset Bays, Inverted Corbels & Steep Trim",
            "theme": theme,
            "execution_ms": round(elapsed_ms, 2),
            "sizeX": sx,
            "sizeY": sy,
            "sizeZ": sz,
            "solid_voxels": solid_count,
            "total_volume": sx * sy * sz,
            "layers": layers,
            "palette": palette,
            "themeReplacements": theme_replacements
        }


if __name__ == "__main__":
    eng = MasterArchitectEngine(11, 11)
    res = eng.generate("medieval_rustic")
    print(f"Generated Master structure in {res['execution_ms']}ms: {res['sizeX']}x{res['sizeY']}x{res['sizeZ']} with {res['solid_voxels']} voxels.")
