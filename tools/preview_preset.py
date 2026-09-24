"""Human-readable preview of authored presets: isometric shading, plan, section.

Colours approximate Minecraft's texture averages; they exist only so a reviewer can
judge silhouette, roof pitch and material contrast before the Java tests run.
"""
from __future__ import annotations

import argparse
from pathlib import Path

from preset_kit import PRESET_DIR, render_elevation, render_iso, render_plan

COLORS = {
    "minecraft:air": "#000000",
    "minecraft:cobblestone": "#7d7d7d",
    "minecraft:mossy_cobblestone": "#6a7a5a",
    "minecraft:stone": "#8f8f8f",
    "minecraft:stone_bricks": "#7c7c7c",
    "minecraft:mossy_stone_bricks": "#6f7d63",
    "minecraft:cracked_stone_bricks": "#6e6e6e",
    "minecraft:chiseled_stone_bricks": "#828282",
    "minecraft:stone_brick_stairs": "#7c7c7c",
    "minecraft:stone_brick_slab": "#828282",
    "minecraft:deepslate_tiles": "#4a4a52",
    "minecraft:deepslate_brick_stairs": "#4a4a52",
    "minecraft:deepslate_brick_slab": "#4a4a52",
    "minecraft:oak_planks": "#b4915c",
    "minecraft:oak_log": "#6b5433",
    "minecraft:oak_stairs": "#b4915c",
    "minecraft:oak_slab": "#b4915c",
    "minecraft:oak_fence": "#b4915c",
    "minecraft:oak_trapdoor": "#a5824e",
    "minecraft:stripped_oak_log": "#c1a06a",
    "minecraft:spruce_planks": "#7a5a38",
    "minecraft:spruce_log": "#4b3823",
    "minecraft:dark_oak_planks": "#513a1f",
    "minecraft:dark_oak_log": "#3b2b16",
    "minecraft:birch_planks": "#d7c99a",
    "minecraft:acacia_planks": "#b4601f",
    "minecraft:jungle_planks": "#b1794f",
    "minecraft:white_terracotta": "#d6cfc0",
    "minecraft:terracotta": "#9a5b43",
    "minecraft:bricks": "#96574a",
    "minecraft:mud_bricks": "#8c6b52",
    "minecraft:glass_pane": "#c8e8ef",
    "minecraft:glass": "#c8e8ef",
    "minecraft:white_stained_glass_pane": "#e8f4f7",
    "minecraft:iron_bars": "#a8adb2",
    "minecraft:iron_block": "#d8d8d8",
    "minecraft:cauldron": "#4a4a4a",
    "minecraft:bookshelf": "#8f7440",
    "minecraft:barrel": "#8a6a3c",
    "minecraft:chest": "#8a6a3c",
    "minecraft:lantern": "#f2c14e",
    "minecraft:soul_lantern": "#5fd0c8",
    "minecraft:campfire": "#8a5a2b",
    "minecraft:hay_block": "#c9a227",
    "minecraft:composter": "#8a6a3c",
    "minecraft:furnace": "#6e6e6e",
    "minecraft:blast_furnace": "#5c5c5c",
    "minecraft:smoker": "#7a6a52",
    "minecraft:crafting_table": "#a1793f",
    "minecraft:cartography_table": "#7a5c33",
    "minecraft:lectern": "#a1793f",
    "minecraft:anvil": "#4a4a4a",
    "minecraft:grindstone": "#7d7d7d",
    "minecraft:stonecutter": "#8a8a8a",
    "minecraft:loom": "#a08a6a",
    "minecraft:brewing_stand": "#7a6a52",
    "minecraft:carpet": "#b03a3a",
    "minecraft:red_carpet": "#b03a3a",
    "minecraft:brown_carpet": "#6b4a2b",
    "minecraft:white_carpet": "#e6e6e6",
    "minecraft:grass_block": "#5d8c37",
    "minecraft:moss_block": "#5a7a34",
    "minecraft:moss_carpet": "#5a7a34",
    "minecraft:azalea_leaves": "#4f7a35",
    "minecraft:flowering_azalea_leaves": "#8a6a8f",
    "minecraft:oak_leaves": "#3f7a2a",
    "minecraft:spruce_leaves": "#2f4f2a",
    "minecraft:vine": "#3a6b28",
    "minecraft:glow_lichen": "#8a9c86",
    "minecraft:cave_vines": "#4a7a2a",
    "minecraft:sweet_berry_bush": "#3f6b2f",
    "minecraft:lily_pad": "#3f7a2a",
    "minecraft:dirt": "#79553a",
    "minecraft:coarse_dirt": "#7a5c40",
    "minecraft:rooted_dirt": "#8a6a4a",
    "minecraft:podzol": "#5c3f21",
    "minecraft:gravel": "#8f8d8a",
    "minecraft:sand": "#dbd0a0",
    "minecraft:sandstone": "#d8cd9a",
    "minecraft:red_sand": "#b35a1f",
    "minecraft:clay": "#a0a4b0",
    "minecraft:snow_block": "#f0f6f8",
    "minecraft:ice": "#a8c8f0",
    "minecraft:water": "#3a62c8",
    "minecraft:dirt_path": "#a1854a",
    "minecraft:farmland": "#6b4a2a",
    "minecraft:wheat": "#c9b04a",
    "minecraft:potatoes": "#4a7a2a",
    "minecraft:carrots": "#d2761f",
    "minecraft:beetroots": "#8a3a3a",
    "minecraft:hanging_roots": "#a07a4a",
    "minecraft:big_dripleaf": "#5a8a3a",
    "minecraft:mangrove_roots": "#6a4a2a",
    "minecraft:muddy_mangrove_roots": "#5c4630",
    "minecraft:sea_lantern": "#c8e0d8",
    "minecraft:prismarine": "#5f9e8f",
    "minecraft:dark_prismarine": "#2f5c4a",
    "minecraft:prismarine_bricks": "#6aa898",
    "minecraft:tuff": "#6a6a60",
    "minecraft:calcite": "#dfdeda",
    "minecraft:dripstone_block": "#8a6f60",
    "minecraft:smooth_basalt": "#4a4a50",
    "minecraft:basalt": "#4a4a50",
    "minecraft:blackstone": "#2f2a2e",
    "minecraft:polished_blackstone_bricks": "#33303a",
    "minecraft:nether_bricks": "#2f1a1e",
    "minecraft:quartz_block": "#e8e2d8",
    "minecraft:smooth_quartz": "#e8e2d8",
    "minecraft:copper_block": "#c07a4a",
    "minecraft:oxidized_copper": "#4f9a7a",
    "minecraft:weathered_copper": "#6a9a70",
    "minecraft:cut_copper": "#c07a4a",
    "minecraft:exposed_copper": "#a08a6a",
    "minecraft:waxed_copper_block": "#c07a4a",
    "minecraft:bamboo_planks": "#c8b04a",
    "minecraft:bamboo_block": "#7a8a3a",
    "minecraft:stripped_bamboo_block": "#c0b060",
    "minecraft:white_wool": "#e9ecec",
    "minecraft:brown_wool": "#724728",
    "minecraft:red_wool": "#a02722",
    "minecraft:yellow_wool": "#f0c23a",
    "minecraft:black_wool": "#1d1c21",
    "minecraft:gray_wool": "#3e4447",
    "minecraft:torch": "#f2c14e",
    "minecraft:wall_torch": "#f2c14e",
    "minecraft:flower_pot": "#a05a3a",
    "minecraft:poppy": "#c03a2a",
    "minecraft:dandelion": "#e8d02a",
    "minecraft:blue_orchid": "#2a9ad0",
    "minecraft:cornflower": "#3a5ac0",
    "minecraft:azure_bluet": "#e8e8d8",
    "minecraft:oxeye_daisy": "#e8e8e0",
    "minecraft:allium": "#b06ac0",
    "minecraft:lilac": "#c0a0c8",
    "minecraft:peony": "#d8a0b0",
    "minecraft:rose_bush": "#a02a2a",
    "minecraft:sunflower": "#f0c020",
    "minecraft:short_grass": "#5d8c37",
    "minecraft:fern": "#4f7a30",
    "minecraft:large_fern": "#4f7a30",
    "minecraft:dead_bush": "#8a6a3a",
    "minecraft:sugar_cane": "#8ac06a",
    "minecraft:cactus": "#4a7a34",
    "minecraft:melon": "#8aa02a",
    "minecraft:pumpkin": "#d07a1f",
    "minecraft:carved_pumpkin": "#d07a1f",
    "minecraft:jack_o_lantern": "#e08a2a",
    "minecraft:beehive": "#c8a04a",
    "minecraft:bell": "#e0c060",
    "minecraft:chain": "#5a5a60",
    "minecraft:ladder": "#a5824e",
    "minecraft:scaffolding": "#c8a860",
    "minecraft:blackstone_slab": "#2f2a2e",
    "minecraft:spruce_trapdoor": "#6b4a2a",
    "minecraft:dark_oak_trapdoor": "#3b2b16",
    "minecraft:iron_trapdoor": "#c0c0c0",
    "minecraft:acacia_trapdoor": "#a05a20",
    "minecraft:oak_door": "#a5824e",
    "minecraft:spruce_door": "#6b4a2a",
    "minecraft:dark_oak_door": "#4a3520",
    "minecraft:birch_door": "#d0c090",
    "minecraft:iron_door": "#c8c8c8",
    "minecraft:oak_sign": "#b4915c",
    "minecraft:lily_of_the_valley": "#e8f0e8",
    "minecraft:white_tulip": "#e8ece0",
    "minecraft:red_tulip": "#c03a2a",
    "minecraft:orange_tulip": "#e07820",
    "minecraft:pink_tulip": "#e8a8c0",
    "minecraft:sea_pickle": "#6a9a5a",
    "minecraft:turtle_egg": "#e8e4c8",
    "minecraft:cod": "#a08a6a",
    "minecraft:salmon": "#c06a5a",
    "minecraft:dried_kelp_block": "#3a3a2a",
}


LABELS = {
    "air": " ", "cobblestone": "c", "mossy_cobblestone": "m", "stone": "s",
    "stone_bricks": "b", "mossy_stone_bricks": "n", "cracked_stone_bricks": "k",
    "chiseled_stone_bricks": "y", "bricks": "B", "deepslate_tiles": "d", "tuff": "T",
    "calcite": "q", "terracotta": "t", "white_terracotta": "P", "mud_bricks": "u",
    "oak_planks": "o", "spruce_planks": "w", "dark_oak_planks": "e", "birch_planks": "i",
    "acacia_planks": "a", "jungle_planks": "j", "oak_log": "O", "spruce_log": "W",
    "dark_oak_log": "E", "stripped_oak_log": "p", "glass_pane": "g",
    "white_stained_glass_pane": "G", "iron_bars": "I", "oak_fence": "f",
    "spruce_fence": "F", "hay_block": "h", "lantern": "L", "campfire": "Y",
    "glowstone": "*", "chest": "X", "barrel": "r", "furnace": "F", "smoker": "S",
    "crafting_table": "K", "stonecutter": "Z", "anvil": "A", "cauldron": "N",
    "bookshelf": "H", "moss_block": "M", "vine": "V", "moss_carpet": "z",
    "oak_leaves": "l", "flowering_azalea_leaves": "R", "gravel": "_", "sand": "=",
    "poppy": "`", "dandelion": "'", "cornflower": ";", "azure_bluet": ":",
    "oxeye_daisy": ",", "short_grass": "/", "fern": "\\", "coarse_dirt": "-",
    "podzol": "+", "lily_pad": "~", "torch": "?", "dirt": ")", "grass_block": "(",
}


def glyph_for(block: str) -> str:
    base = block.split("[")[0].removeprefix("minecraft:")
    if "trapdoor" in base:
        return "T"
    if base.endswith("_stairs"):
        return "/" if "facing=north" in block or "facing=west" in block else "\\"
    if base.endswith("_slab"):
        return "-"
    return LABELS.get(base, "?")


def view(preset: dict, title: str, iso_width: int) -> None:
    print("=" * 78)
    print(f"{title}  [{preset['id']}]  {preset['sizeX']}x{preset['sizeZ']}x{preset['sizeY']}"
          f"  door {preset['entrance']['facing']} @({preset['entrance']['x']},{preset['entrance']['z']})"
          f"  area={sum(row.count('#') for row in preset['footprintMask'])}")
    print(f"  {preset['description']}")
    print("-- isometric (north-west lit) " + "-" * 40)
    for line in render_iso(preset, COLORS, width=iso_width):
        print("  " + line)
    roof = tuple(block for block in preset["palette"].values()
                 if "stairs" in block or "slab" in block or block.startswith("minecraft:hay")
                 or block.startswith("minecraft:mud_brick"))
    print("-- south elevation (roof stripped) " + "-" * 35)
    for line in render_elevation(preset, axis="x", drop=roof, glyph=glyph_for):
        print("  " + line)
    print("-- east elevation (roof stripped) " + "-" * 36)
    for line in render_elevation(preset, axis="z", drop=roof, glyph=glyph_for):
        print("  " + line)
    print("-- plan (top-down) " + "-" * 51)
    for line in render_plan(preset, COLORS, glyph=glyph_for):
        print("  " + line)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("ids", nargs="*")
    ap.add_argument("--dir", type=Path, default=PRESET_DIR)
    ap.add_argument("--width", type=int, default=110)
    args = ap.parse_args()
    ids = args.ids or sorted(p.stem for p in args.dir.glob("*.json"))
    for ident in ids:
        path = args.dir / f"{ident}.json"
        view(__import__("json").loads(path.read_text(encoding="utf-8")), path.stem, args.width)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
