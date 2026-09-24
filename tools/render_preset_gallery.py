"""Render authored presets to PNG so their silhouettes can actually be reviewed.

Builds a self-contained three.js gallery over the bundled `tools/generators/three.min.js`
and screenshots it with headless Chrome at two camera angles per preset. Chrome is driven
with --screenshot, which needs no extra tooling; nothing is installed or downloaded.
"""
from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from preset_kit import PRESET_DIR  # noqa: E402
from preview_preset import COLORS  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
THREE = ROOT / "tools/generators/three.min.js"
CHROME_CANDIDATES = [
    Path(os.environ.get("PROGRAMFILES", "")) / "Google/Chrome/Application/chrome.exe",
    Path(os.environ.get("PROGRAMFILES(X86)", "")) / "Google/Chrome/Application/chrome.exe",
    Path(os.environ.get("LOCALAPPDATA", "")) / "Google/Chrome/Application/chrome.exe",
    Path(os.environ.get("PROGRAMFILES", "")) / "Microsoft/Edge/Application/msedge.exe",
    Path(os.environ.get("PROGRAMFILES(X86)", "")) / "Microsoft/Edge/Application/msedge.exe",
]

# roof material families, used by the geometry audit rather than the renderer
ROOF_PREFIXES = ("minecraft:hay_block", "minecraft:oak_stairs", "minecraft:spruce_stairs",
                 "minecraft:dark_oak_stairs", "minecraft:mud_brick_stairs",
                 "minecraft:birch_stairs", "minecraft:acacia_stairs",
                 "minecraft:deepslate_brick_stairs", "minecraft:bamboo_stairs",
                 "minecraft:oak_slab", "minecraft:spruce_slab", "minecraft:dark_oak_slab",
                 "minecraft:mud_brick_slab", "minecraft:birch_slab", "minecraft:acacia_slab")


def preset_files(ids: list[str]) -> list[Path]:
    files = sorted(PRESET_DIR.glob("*.json"))
    if ids:
        wanted = set(ids)
        files = [f for f in files if f.stem in wanted]
    return [f for f in files if not json.loads(f.read_text(encoding="utf-8")).get("archived")]


def audit(preset: dict) -> list[str]:
    """Structural sanity: no floating solids, an enclosed interior, windows on the shell."""
    sx, sy, sz = preset["sizeX"], preset["sizeY"], preset["sizeZ"]
    palette, layers = preset["palette"], preset["layers"]
    mask = preset.get("footprintMask") or ["#" * sx for _ in range(sz)]
    issues: list[str] = []

    def block(x: int, y: int, z: int) -> str:
        if not (0 <= x < sx and 0 <= y < sy and 0 <= z < sz):
            return "minecraft:air"
        return palette.get(layers[y][z][x], "minecraft:air")

    if not any("glass" in b for b in palette.values()):
        issues.append("no glazing in the legend")
    if not any(b.startswith(ROOF_PREFIXES) for b in palette.values()):
        issues.append("no pitched roof material in the legend")

    # the floor (y=0) must cover the whole reservation, and every wall must reach the roof
    for z, row in enumerate(mask):
        for x, cell in enumerate(row):
            if cell == "#" and block(x, 0, z) == "minecraft:air":
                issues.append(f"hole in the foundation at ({x},{z})")
                break
    if preset["entrance"]["facing"] not in ("NORTH", "SOUTH", "EAST", "WEST"):
        issues.append("invalid entrance facing")

    # a solid block with no support below and nothing beside it is a modelling slip
    neighbours = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
    for z in range(sz):
        for x in range(sx):
            if mask[z][x] != "#":
                continue
            for y in range(1, sy):
                if block(x, y, z) == "minecraft:air":
                    continue
                if all(block(x + dx, y + dy, z + dz) == "minecraft:air" for dx, dy, dz in neighbours):
                    issues.append(f"floating block at ({x},{y},{z}) = {block(x, y, z)}")
                    break
    return issues[:8]


def gallery_html(presets: list[dict], colors: dict[str, str], three_source: str) -> str:
    payload = json.dumps(presets, ensure_ascii=False)
    palette = json.dumps(colors, ensure_ascii=False)
    return f"""<!doctype html>
<html><head><meta charset="utf-8"><title>preset gallery</title>
<style>html,body{{margin:0;background:#0b1420}}canvas{{display:block}}</style></head>
<body><script>{three_source}</script>
<script>
const PRESETS = {payload};
const COLORS = {palette};
const FALLBACK = "#8a8a8a";
const params = new URLSearchParams(location.search);
const index = Number(params.get("i") || 0);
const yaw = Number(params.get("yaw") || 34);
const preset = PRESETS[index];
function hex(id) {{
  const key = id.split("[")[0];
  return COLORS[id] || COLORS[key] || FALLBACK;
}}
const scene = new THREE.Scene();
scene.background = new THREE.Color(0x0b1420);
const size = Math.max(preset.sizeX, preset.sizeZ, preset.sizeY);
const camera = new THREE.PerspectiveCamera(32, innerWidth / innerHeight, 0.1, 1000);
const target = new THREE.Vector3(preset.sizeX / 2, preset.sizeY / 2, preset.sizeZ / 2);
const radius = size * 2.1;
const a = yaw * Math.PI / 180;
camera.position.set(target.x + radius * Math.cos(a), target.y + radius * 0.72, target.z + radius * Math.sin(a));
camera.lookAt(target);
scene.add(new THREE.HemisphereLight(0xdfe9ff, 0x2a3242, 0.85));
const sun = new THREE.DirectionalLight(0xfff3e0, 0.85);
sun.position.set(-size, size * 1.6, -size * 0.6);
scene.add(sun);
const fill = new THREE.DirectionalLight(0x9fc4ff, 0.35);
fill.position.set(size, size * 0.5, size);
scene.add(fill);
const materials = new Map();
function materialFor(id) {{
  if (!materials.has(id)) {{
    materials.set(id, new THREE.MeshLambertMaterial({{ color: new THREE.Color(hex(id)) }}));
  }}
  return materials.get(id);
}}
const box = new THREE.BoxGeometry(1, 1, 1);
const groups = new Map();
for (let y = 0; y < preset.sizeY; y++) {{
  for (let z = 0; z < preset.sizeZ; z++) {{
    const row = preset.layers[y][z];
    for (let x = 0; x < preset.sizeX; x++) {{
      const id = preset.palette[row[x]];
      if (!id || id === "minecraft:air") continue;
      if (!groups.has(id)) groups.set(id, []);
      groups.get(id).push([x + 0.5, y + 0.5, z + 0.5]);
    }}
  }}
}}
for (const [id, cells] of groups) {{
  const mesh = new THREE.InstancedMesh(box, materialFor(id), cells.length);
  const matrix = new THREE.Matrix4();
  cells.forEach((c, i) => {{ matrix.setPosition(c[0], c[1], c[2]); mesh.setMatrixAt(i, matrix); }});
  mesh.instanceMatrix.needsUpdate = true;
  scene.add(mesh);
}}
const ground = new THREE.Mesh(
  new THREE.BoxGeometry(preset.sizeX + 6, 0.4, preset.sizeZ + 6),
  new THREE.MeshLambertMaterial({{ color: 0x24313f }}));
ground.position.set(preset.sizeX / 2, -0.2, preset.sizeZ / 2);
scene.add(ground);
const renderer = new THREE.WebGLRenderer({{ antialias: true }});
renderer.setSize(innerWidth, innerHeight);
renderer.setPixelRatio(1);
document.body.appendChild(renderer.domElement);
renderer.render(scene, camera);
window.__rendered = true;
</script></body></html>"""


def find_chrome() -> Path | None:
    for candidate in CHROME_CANDIDATES:
        if candidate and candidate.is_file():
            return candidate
    return None


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("ids", nargs="*")
    ap.add_argument("--out", type=Path, default=ROOT / "build/preset-render")
    ap.add_argument("--size", default="620,520")
    ap.add_argument("--views", default="34,214", help="comma separated yaw angles")
    ap.add_argument("--audit-only", action="store_true")
    args = ap.parse_args()

    files = preset_files(args.ids)
    presets = [json.loads(f.read_text(encoding="utf-8")) for f in files]
    if not presets:
        print("no presets selected")
        return 1

    failures = 0
    for preset in presets:
        issues = audit(preset)
        status = "PASS" if not issues else "FAIL"
        if issues:
            failures += 1
        print(f"{status} audit {preset['id']}: "
              f"{preset['sizeX']}x{preset['sizeZ']}x{preset['sizeY']} "
              f"blocks={sum(1 for y in preset['layers'] for z in y for c in z if preset['palette'].get(c, 'minecraft:air') != 'minecraft:air')}")
        for issue in issues:
            print("     -", issue)
    if args.audit_only:
        return 1 if failures else 0

    chrome = find_chrome()
    if chrome is None:
        print("no Chrome/Edge found; ran the geometry audit only")
        return 1 if failures else 0

    args.out.mkdir(parents=True, exist_ok=True)
    html = args.out / "gallery.html"
    html.write_text(gallery_html(presets, COLORS, THREE.read_text(encoding="utf-8")), encoding="utf-8")
    yaws = [v.strip() for v in args.views.split(",") if v.strip()]
    for index, preset in enumerate(presets):
        for yaw in yaws:
            shot = args.out / f"{preset['id']}-y{yaw}.png"
            command = [str(chrome), "--headless=new", "--disable-gpu", "--hide-scrollbars",
                       f"--window-size={args.size}", "--virtual-time-budget=4000",
                       f"--screenshot={shot}", f"file:///{html.as_posix()}?i={index}&yaw={yaw}"]
            result = subprocess.run(command, capture_output=True, timeout=180)
            if result.returncode or not shot.is_file():
                print(f"FAIL render {preset['id']} yaw={yaw}: {result.returncode} "
                      f"{result.stderr.decode('utf-8', 'replace')[-300:]}")
                failures += 1
            else:
                print(f"rendered {shot.relative_to(ROOT)}")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
