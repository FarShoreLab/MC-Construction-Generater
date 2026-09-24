"""Static authoring checks for the crafted-preset builders (run before compiling).

Catches the two mistakes that are easy to make by hand:
  * referencing a palette symbol that was never declared
  * feeding an already-propertied block into a composite that adds blockstate
"""
from __future__ import annotations

import ast
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

BUILDERS = ["build_crafted_village.py", "build_crafted_service.py"]
TOOLS = Path(__file__).resolve().parent

# composites that append blockstate properties and therefore need a base block
BASE_ONLY = {
    "stairs": {1: "block"},
    "slab": {1: "block"},
    "trapdoor": {1: "block"},
    "window_bay": {"frame": "block", "glass": "block", "sill": "block", "shutter": "block"},
    "pitched_roof": {"material": "block", "slope": "block", "ridge_slab": "block", "fill": "block"},
    "shed_roof": {"material": "block", "slope": "block"},
    "awning": {"canopy": "block", "edge": "block"},
    "door_opening": {"frame": "block", "door": "block", "lintel": "block", "light": "block", "threshold": "block"},
    "chimney": {"body": "block", "band": "block", "cap": "block", "vent": "block"},
    "flower_box": {"wood": "block", "soil": "block", "bloom": "block"},
    "roof_lantern": {"support": "block", "lamp": "block"},
}


def check_undeclared(source: str, name: str) -> list[str]:
    tree = ast.parse(source)
    symbol_assign = next(
        (n for n in tree.body if isinstance(n, ast.Assign) and getattr(n.targets[0], "id", "") == "SYMBOLS"),
        None,
    )
    if symbol_assign is None:
        return [f"{name}: no SYMBOLS table found"]
    ns: dict = {"AIR": "minecraft:air"}
    exec(compile(ast.Module(body=[symbol_assign], type_ignores=[]), "<symbols>", "exec"), ns)
    declared = set(ns["SYMBOLS"])
    used = set(re.findall(r'SYMBOLS\["(.+?)"\]', source))
    problems = [f"{name}: SYMBOLS[{k!r}] is never declared" for k in sorted(used - declared)]
    unused = sorted(declared - used)
    if unused:
        print(f"  note {name}: {len(unused)} declared but unused symbols: {unused}")
    return problems


def check_base_blocks(source: str, name: str) -> list[str]:
    tree = ast.parse(source)
    symbol_assign = next(
        (n for n in tree.body if isinstance(n, ast.Assign) and getattr(n.targets[0], "id", "") == "SYMBOLS"),
        None,
    )
    ns: dict = {"AIR": "minecraft:air"}
    exec(compile(ast.Module(body=[symbol_assign], type_ignores=[]), "<symbols>", "exec"), ns)
    symbols = ns["SYMBOLS"]
    problems = []
    for node in ast.walk(tree):
        if not isinstance(node, ast.Call) or not isinstance(node.func, ast.Name):
            continue
        spec = BASE_ONLY.get(node.func.id)
        if spec is None:
            continue
        for index, arg in enumerate(node.args):
            if index not in spec:
                continue
            if isinstance(arg, ast.Subscript) and isinstance(arg.slice, ast.Constant):
                key = ast.literal_eval(arg.slice)
                if key == ".":
                    continue
                if "[" in symbols.get(key, ""):
                    problems.append(
                        f"{name}: {node.func.id}() arg {index} passes SYMBOLS[{key!r}] = "
                        f"{symbols[key]!r}, which already carries blockstate")
    return problems


def main() -> int:
    problems: list[str] = []
    for builder in BUILDERS:
        path = TOOLS / builder
        if not path.is_file():
            continue
        source = path.read_text(encoding="utf-8")
        print(f"checking {builder}")
        problems += check_undeclared(source, builder)
        problems += check_base_blocks(source, builder)
    for problem in problems:
        print("FAIL " + problem)
    if problems:
        return 1
    print("PASS static preset authoring checks")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
