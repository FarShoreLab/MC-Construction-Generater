"""One-off trace helper: print the authoring call chain for a coordinate."""
import sys
import traceback
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import preset_kit  # noqa: E402
import build_crafted_village as village  # noqa: E402

X, Y, Z = (int(v) for v in sys.argv[1:4])
builder = sys.argv[4] if len(sys.argv) > 4 else "herbalist_shop"
original = preset_kit.Grid.set
seen = []


def patched(self, x, y, z, block):
    if (x, y, z) == (X, Y, Z):
        chain = []
        for frame in traceback.extract_stack()[:-1]:
            if "build_crafted_village" in frame.filename or "preset_kit" in frame.filename:
                chain.append(f"{Path(frame.filename).name}:{frame.lineno}:{frame.name}")
        seen.append((block, " <- ".join(chain[-6:])))
    return original(self, x, y, z, block)


preset_kit.Grid.set = patched
try:
    getattr(village, builder)()
except Exception as error:  # the failure we are diagnosing
    print("stopped:", error)
for block, chain in seen:
    print(f"\n({X},{Y},{Z}) <- {block}\n    {chain}")
