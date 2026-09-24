"""
Hierarchical Shape Grammar Procedural Building Generator.
Uses parametric split grammar rules: Footprint -> Framing + Bays + Roof Truss.
Produces structured facades, recessed windows, protruding corner columns,
and dual-pitch roofs with overhang eaves.
"""

import random
import time
from typing import Dict, List, Any, Optional


class ShapeGrammarGenerator:
    """Hierarchical Shape Grammar Building Generator"""

    def __init__(self, width: int = 9, depth: int = 9, height_per_floor: int = 3, floors: int = 2, seed: Optional[int] = None):
        self.w = max(7, width if width % 2 == 1 else width + 1)
        self.d = max(7, depth if depth % 2 == 1 else depth + 1)
        self.h_floor = height_per_floor
        self.floors = floors
        self.rng = random.Random(seed if seed is not None else int(time.time() * 1000))

    def generate(self, theme: str = "medieval_rustic") -> Dict[str, Any]:
        start_time = time.perf_counter()

        roof_h = (self.w // 2) + 2
        total_h = 1 + (self.floors * self.h_floor) + roof_h
        size_x = self.w + 2  # 1 block eave on each side
        size_z = self.d + 2
        size_y = total_h

        # 3D grid
        grid = [[["." for _ in range(size_z)] for _ in range(size_y)] for _ in range(size_x)]

        # Offsets for building body within the padded bounding box (due to eaves)
        ox = 1
        oz = 1

        # RULE 1: Foundation (Y=0)
        for x in range(self.w):
            for z in range(self.d):
                is_edge = (x == 0 or x == self.w - 1 or z == 0 or z == self.d - 1)
                grid[ox + x][0][oz + z] = "C" if is_edge else "S"

        # RULE 2: Floor Levels & Framing (Y=1 .. floors*h_floor)
        for fl in range(self.floors):
            base_y = 1 + fl * self.h_floor

            # Floor separation slab/planks if upper floor
            if fl > 0:
                for x in range(1, self.w - 1):
                    for z in range(1, self.d - 1):
                        grid[ox + x][base_y][oz + z] = "W"

            for dy in range(self.h_floor):
                cur_y = base_y + dy

                # Corner columns (Protruding log framing)
                for cx in [0, self.w - 1]:
                    for cz in [0, self.d - 1]:
                        grid[ox + cx][cur_y][oz + cz] = "L"

                # Mid-wall support columns if wide
                mid_x = self.w // 2
                mid_z = self.d // 2
                for cz in [0, self.d - 1]:
                    grid[ox + mid_x][cur_y][oz + cz] = "L"
                for cx in [0, self.w - 1]:
                    grid[ox + cx][cur_y][oz + mid_z] = "L"

                # Subdivide Wall Bays (Recessed facade infill)
                # Front Wall (Z = 0)
                for x in range(1, self.w - 1):
                    if x == mid_x:
                        continue
                    if dy == 0 and fl == 0 and (x == mid_x - 1 or x == mid_x + 1):
                        # Door opening
                        grid[ox + x][cur_y][oz + 0] = "."
                    elif dy == 1 and x in [mid_x - 2, mid_x + 2, 2, self.w - 3]:
                        # Window bay with glass
                        grid[ox + x][cur_y][oz + 0] = "G"
                    else:
                        grid[ox + x][cur_y][oz + 0] = "W"

                # Back Wall (Z = d-1)
                for x in range(1, self.w - 1):
                    if x == mid_x:
                        continue
                    if dy == 1 and (x % 2 == 0):
                        grid[ox + x][cur_y][oz + self.d - 1] = "G"
                    else:
                        grid[ox + x][cur_y][oz + self.d - 1] = "W"

                # Left Wall (X = 0) & Right Wall (X = w-1)
                for z in range(1, self.d - 1):
                    if z == mid_z:
                        continue
                    if dy == 1 and (z % 2 == 0):
                        grid[ox + 0][cur_y][oz + z] = "G"
                        grid[ox + self.w - 1][cur_y][oz + z] = "G"
                    else:
                        grid[ox + 0][cur_y][oz + z] = "W"
                        grid[ox + self.w - 1][cur_y][oz + z] = "W"

            # Horizontal tie beam at top of each floor
            top_y = base_y + self.h_floor - 1
            for x in range(self.w):
                grid[ox + x][top_y][oz + 0] = "L"
                grid[ox + x][top_y][oz + self.d - 1] = "L"
            for z in range(self.d):
                grid[ox + 0][top_y][oz + z] = "L"
                grid[ox + self.w - 1][top_y][oz + z] = "L"

        # Interior props on ground floor
        grid[ox + self.w - 2][1][oz + self.d - 2] = "T"
        grid[ox + self.w - 3][1][oz + self.d - 2] = "X"
        grid[ox + 2][1][oz + self.d - 2] = "F"
        grid[ox + mid_x][self.h_floor][oz + mid_z] = "P"  # Hanging lantern

        # RULE 3: Roof Structure (Dual-Pitch Steep Gable with Overhang & Corbels)
        roof_base_y = 1 + self.floors * self.h_floor

        # Corbels beneath eaves (Upside-down stairs / support blocks)
        for z in range(size_z):
            grid[ox - 1][roof_base_y - 1][z] = "S"  # West corbel
            grid[ox + self.w][roof_base_y - 1][z] = "S"  # East corbel

        # Sloped Gable Roof
        half_w = self.w // 2
        for step in range(half_w + 1):
            cur_roof_y = roof_base_y + step
            left_x = ox - 1 + step
            right_x = ox + self.w - step

            for z in range(size_z):
                # Eaves trim using stone/contrast material at front and back gables
                is_gable_trim = (z == 0 or z == size_z - 1)
                mat = "S" if is_gable_trim else "O"

                if left_x <= right_x:
                    grid[left_x][cur_roof_y][z] = mat
                    grid[right_x][cur_roof_y][z] = mat

            # Infill triangle gables
            if step > 0:
                for z in [oz, oz + self.d - 1]:
                    for fill_x in range(left_x + 1, right_x):
                        grid[fill_x][cur_roof_y - 1][z] = "W"

        elapsed_ms = (time.perf_counter() - start_time) * 1000.0

        # Layers representation
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
            "id": f"gen_grammar_{theme}",
            "name": f"层次形状文法建筑 ({theme})",
            "generator": "Hierarchical Shape Grammar (SG)",
            "paradigm": "Grammar Production Rules & Facade Subdivision",
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
    sg = ShapeGrammarGenerator(9, 9, 3, 2, seed=42)
    res = sg.generate("medieval_rustic")
    print(f"Generated Grammar structure in {res['execution_ms']}ms: {res['sizeX']}x{res['sizeY']}x{res['sizeZ']} with {res['solid_voxels']} voxels.")
