"""One-off diagnostic: dump mask and column contents for a designed building."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import preset_kit  # noqa: E402
import build_crafted_village as village  # noqa: E402

original = preset_kit.build_preset
captured = {}


def patched(**kwargs):
    captured.update(kwargs)
    return original(**kwargs)


preset_kit.build_preset = patched
village.__dict__["build_preset"] = patched
name = sys.argv[1] if len(sys.argv) > 1 else "herbalist_shop"
try:
    getattr(village, name)()
except Exception as error:
    print("stopped:", error)

grid = captured.get("grid")
mask = captured.get("mask") or set()
if grid is None:
    raise SystemExit("no grid captured")
print(f"{name}: {grid.sx}x{grid.sz}x{grid.sy}")
print("mask rows (z down, x right):")
for z in range(grid.sz):
    print("  ", "".join("#" if (x, z) in mask else "." for x in range(grid.sx)))
for x, z in sorted(mask):
    column = [grid.get(x, y, z) for y in range(4)]
    marks = ["S" if c not in (None, preset_kit.AIR) else ("." if c == preset_kit.AIR else "!") for c in column]
    print(f"  ({x:2d},{z:2d}) y0..3 {'|'.join(marks)}   {column[0]}")
