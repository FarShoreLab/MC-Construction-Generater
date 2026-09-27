"""Use the settlement demo's actual Java terrain, including MC materials and trees."""
from functools import lru_cache


@lru_cache(maxsize=4)
def load_legacy_terrain(seed, terrain):
    if type(seed) is not int or not 0 <= seed <= 2147483647:
        raise ValueError("seed must be an integer between 0 and 2147483647")
    if terrain not in ("hills", "river"):
        raise ValueError("terrain must be hills or river")
    # Import lazily: the preview server also exposes the city route. Generation
    # happens only after both modules have finished initialising.
    from preview_server import run_java
    return run_java({"width": "512", "depth": "512", "baseElevation": "58",
                     "relief": "24", "terrainType": "rolling_hills" if terrain == "hills" else "valley",
                     "terrainSeed": str(seed)}, terrain_only=True)


def terrain_preview(seed=42, terrain="hills"):
    if terrain == "flat":
        if type(seed) is not int or not 0 <= seed <= 2147483647:
            raise ValueError("seed must be an integer between 0 and 2147483647")
        return dict(seed=seed, size=720, roads=[], blocks=[], buildings=[], parks=[], centers=[],
                    metrics=dict(buildingCount=0, blockCount=0, maxHeight=0, unchangedTerrainRatio=1))
    source = load_legacy_terrain(seed, terrain)
    layer, config = source["original"], source["config"]
    heights = [h + 1 for h in layer["heights"]]
    field = dict(type=terrain, sourceType=config["terrainType"], resolution=layer["sizeX"], step=1,
                 original=heights, modified=heights[:], waterLevel=config["waterLevel"] + 1,
                 waterMask=[int(w >= 0) for w in layer["waters"]],
                 mcLayer=layer, sourceConfig=config)
    return dict(seed=seed, size=layer["sizeX"], terrain=field,
                roads=[], blocks=[], buildings=[], parks=[], centers=[],
                metrics=dict(buildingCount=0, blockCount=0, maxHeight=0,
                             cutVolume=0, fillVolume=0, unchangedTerrainRatio=1))
