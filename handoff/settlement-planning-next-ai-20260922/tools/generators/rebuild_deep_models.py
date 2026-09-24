"""
Rebuilds deep_models.json with MonumentalArchitectEngine models:
- 5 Archetypes (church, brickhouse, bakery, school, farm)
- 3 Scales (small, medium, large)
- All with authentic full procedural roofs (high gables, spires, mansards, domes, battlements)
- Monolithic grand mega-structures (up to 25,000+ blocks)
"""

import json
import os
import sys

CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, CURRENT_DIR)

from monumental_architect_engine import MonumentalArchitectEngine

DEEP_MODELS_PATH = os.path.join(CURRENT_DIR, "deep_models.json")


def rebuild_all():
    archetypes = ["church", "brickhouse", "bakery", "school", "farm"]
    scales = ["small", "medium", "large"]

    models = {}
    print("Rebuilding all 15 Monumental Mega-Building models...")
    for arch in archetypes:
        for scale in scales:
            model_id = f"{arch}_{scale}"
            # Seed based on name hash for determinism
            seed = abs(hash(model_id)) % 1000000 + 1000
            engine = MonumentalArchitectEngine(archetype=arch, scale=scale, seed=seed)
            data = engine.generate()
            models[model_id] = data
            print(f"  [+] Generated {data['name']}: {data['sizeX']}x{data['sizeY']}x{data['sizeZ']}, {data['solid_voxels']:,} blocks, Roof Score: {data['metrics']['roof_complexity_score']}")

    with open(DEEP_MODELS_PATH, "w", encoding="utf-8") as f:
        json.dump(models, f, ensure_ascii=False, indent=2)

    print(f"\nSuccessfully wrote {len(models)} models to {DEEP_MODELS_PATH}")


if __name__ == "__main__":
    rebuild_all()
