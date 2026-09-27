"""Independent checks of exported road geometry and original/modified terrain."""

import math
import unittest

from modern_city import generate_city


def height_at(terrain, field, x, z):
    """Bilinear sampling from the exported row-major grid (no planner helpers)."""
    n, step = terrain["resolution"], terrain["step"]
    gx, gz = x / step, z / step
    ix, iz = min(n - 2, int(gx)), min(n - 2, int(gz))
    tx, tz = gx - ix, gz - iz
    values = terrain[field]
    a, b = values[iz * n + ix], values[iz * n + ix + 1]
    c, d = values[(iz + 1) * n + ix], values[(iz + 1) * n + ix + 1]
    return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz


def water_at(terrain, x, z):
    n, step = terrain["resolution"], terrain["step"]
    ix, iz = min(n - 1, int(x / step)), min(n - 1, int(z / step))
    return bool(terrain["waterMask"][iz * n + ix])


def footprint_samples(rect, step):
    xs = {rect["x"], rect["x"] + rect["w"], rect["x"] + rect["w"] / 2}
    zs = {rect["z"], rect["z"] + rect["d"], rect["z"] + rect["d"] / 2}
    xs.update(i * step for i in range(math.ceil(rect["x"] / step),
                                      math.floor((rect["x"] + rect["w"]) / step) + 1))
    zs.update(i * step for i in range(math.ceil(rect["z"] / step),
                                      math.floor((rect["z"] + rect["d"]) / step) + 1))
    return [(x, z) for x in xs for z in zs]


def overlaps(a, b):
    return (min(a["x"] + a["w"], b["x"] + b["w"]) > max(a["x"], b["x"]) + .001
            and min(a["z"] + a["d"], b["z"] + b["d"]) > max(a["z"], b["z"]) + .001)


def point_segment_distance(p, a, b):
    dx, dz = b[0] - a[0], b[1] - a[1]
    length2 = dx * dx + dz * dz
    t = max(0, min(1, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dz) / length2)) if length2 else 0
    return math.hypot(p[0] - a[0] - t * dx, p[1] - a[1] - t * dz)


def segment_rect_distance(a, b, rect):
    x0, z0 = rect["x"], rect["z"]
    x1, z1 = x0 + rect["w"], z0 + rect["d"]
    lower, upper = 0, 1
    for position, direction, lo, hi in ((a[0], b[0] - a[0], x0, x1),
                                        (a[1], b[1] - a[1], z0, z1)):
        if abs(direction) < 1e-10:
            if not lo <= position <= hi:
                lower, upper = 1, 0
                break
        else:
            near, far = sorted(((lo - position) / direction, (hi - position) / direction))
            lower, upper = max(lower, near), min(upper, far)
    if lower <= upper:
        return 0
    corners = [(x0, z0), (x1, z0), (x1, z1), (x0, z1)]
    point_rect = lambda p: math.hypot(max(x0 - p[0], 0, p[0] - x1),
                                     max(z0 - p[1], 0, p[1] - z1))
    return min(point_rect(a), point_rect(b),
               *(point_segment_distance(p, a, b) for p in corners))


def road_xy(city):
    return [[(point["x"], point["z"]) for point in road["points"]]
            for road in city["roads"]]


def inside_polygon(polygon, x, z):
    winding = 0
    for a, b in zip(polygon, polygon[1:] + polygon[:1]):
        side = (b["x"] - a["x"]) * (z - a["z"]) - (x - a["x"]) * (b["z"] - a["z"])
        if a["z"] <= z < b["z"] and side > 0:
            winding += 1
        elif b["z"] <= z < a["z"] and side < 0:
            winding -= 1
    return winding != 0


class CityTerrainTests(unittest.TestCase):
    """Assertions deliberately derive evidence rather than trusting metrics."""

    @classmethod
    def setUpClass(cls):
        cls.samples = {}
        for terrain in ("hills", "river"):
            for seed, density, max_edit in ((0, .25, 2), (42, .75, 6), (917, 1, 10)):
                cls.samples[(terrain, seed, density, max_edit)] = generate_city(
                    seed, density, "balanced", terrain=terrain, max_edit=max_edit)

    def test_geometry_across_seeds_density_and_earthwork(self):
        for parameters, city in self.samples.items():
            with self.subTest(parameters=parameters):
                self.check_city(city, parameters[3])

    def test_exact_legacy_java_terrain_source(self):
        from preview_server import run_java

        for parameters in (("hills", 42, .75, 6), ("river", 42, .75, 6), ("hills", 0, .25, 2)):
            with self.subTest(parameters=parameters):
                city = self.samples[parameters]
                terrain = city["terrain"]
                source = terrain["sourceConfig"]
                query = {key: str(source[key]) for key in
                         ("width", "depth", "baseElevation", "relief", "terrainType", "terrainSeed")}
                query.update({key: str(value) for key, value in source["terrainParameters"].items()})
                expected = run_java(query, terrain_only=True)
                self.assertEqual(source, expected["config"], "source config/hash differs from legacy Java")
                layer = terrain["mcLayer"]
                self.assertEqual(set(layer), set(expected["original"]))
                for key, value in expected["original"].items():
                    self.assertTrue(layer[key] == value, f"legacy original field {key} differs")
                self.assertEqual(layer["sizeX"], 512)
                self.assertEqual(layer["sizeZ"], 512)
                self.assertEqual(terrain["step"], 1)
                self.assertTrue(terrain["original"] == [h + 1 for h in layer["heights"]],
                                "planner original heights are not exact MC block top faces")
                self.assertTrue(terrain["waterMask"] == [int(w >= 0) for w in layer["waters"]],
                                "planner water mask differs from legacy MC water columns")
                self.assertGreater(sum(v[3] in (5, 6) for v in layer["voxels"]), 0,
                                   "legacy trees were discarded")

    def test_terrain_changes_horizontal_plan(self):
        hills = self.samples[("hills", 42, .75, 6)]
        river = self.samples[("river", 42, .75, 6)]
        self.assertNotEqual(road_xy(hills), road_xy(river), "terrain changes Y only")
        footprints = lambda city: [(b["x"], b["z"], b["w"], b["d"]) for b in city["buildings"]]
        self.assertNotEqual(footprints(hills), footprints(river), "terrain did not affect building sites")
        self.assertGreater(sum(river["terrain"]["waterMask"]), 0, "river scenario has no water")
        self.assertLess(sum(river["terrain"]["waterMask"]), len(river["terrain"]["waterMask"]) * .4)
        self.assertGreater(max(river["terrain"]["original"]), river["terrain"]["waterLevel"] + 8)
        for city in (hills, river):
            self.assertGreater(len(city["buildings"]), 150, "default must remain a dense city")
            changed = [abs(a - b) for a, b in zip(city["terrain"]["original"], city["terrain"]["modified"])]
            self.assertGreater(sum(delta > .01 for delta in changed), 0, "no local grading occurred")
            self.assertLess(sum(delta > .01 for delta in changed), len(changed) * .5,
                            "majority of natural terrain was replaced")
        # The legacy valley may be a broad lake basin. A bridge is optional;
        # forcing one would manufacture a different source terrain to pass.

    def test_reproducibility_and_earthwork_extremes(self):
        baseline = self.samples[("hills", 42, .75, 6)]
        self.assertEqual(baseline, generate_city(42, .75, "balanced", terrain="hills", max_edit=6))
        extremes = []
        for max_edit in (2, 10):
            with self.subTest(max_edit=max_edit):
                city = generate_city(42, .75, "polycentric", terrain="river", max_edit=max_edit)
                extremes.append(city)
                self.check_city(city, max_edit)
        self.assertNotEqual(extremes[0]["terrain"]["modified"], extremes[1]["terrain"]["modified"],
                            "earthwork control has no geometric effect")

    def test_invalid_terrain_controls(self):
        for terrain in ("unknown", "", None):
            with self.assertRaises(ValueError):
                generate_city(terrain=terrain)
        for max_edit in (0, 1.9, 10.1, float("nan"), float("inf")):
            with self.assertRaises(ValueError):
                generate_city(terrain="hills", max_edit=max_edit)

    def check_city(self, city, max_edit):
        terrain = city["terrain"]
        n, step = terrain["resolution"], terrain["step"]
        original, modified, wet = terrain["original"], terrain["modified"], terrain["waterMask"]
        self.assertEqual(len(original), n * n)
        self.assertEqual(len(modified), len(original))
        self.assertEqual(len(wet), len(original))
        self.assertAlmostEqual(n * step, city["size"])
        self.assertTrue(all(math.isfinite(v) for v in original + modified))
        self.assertGreater(max(original) - min(original), 8, "natural terrain has no meaningful relief")
        self.assertLessEqual(max(abs(a - b) for a, b in zip(original, modified)), max_edit + .002)
        for i, water in enumerate(wet):
            if water:
                self.assertAlmostEqual(original[i], modified[i], delta=.001,
                                       msg="river bed modified by earthwork")

        graph, elevations, edges, segments = {}, {}, set(), []
        bridge_graph, surface_nodes = {}, set()
        max_grade, curved = 0, 0
        for road in city["roads"]:
            points = road["points"]
            self.assertGreaterEqual(len(points), 2)
            self.assertGreater(road["width"], 0)
            path_length = 0
            for p in points:
                self.assertTrue(all(math.isfinite(p[k]) for k in ("x", "y", "z")))
                self.assertTrue(0 <= p["x"] <= city["size"] and 0 <= p["z"] <= city["size"])
                key = (round(p["x"], 3), round(p["z"], 3))
                if key in elevations:
                    self.assertAlmostEqual(elevations[key], p["y"], delta=.002,
                                           msg="shared road junction has incompatible elevations")
                elevations[key] = p["y"]
                graph.setdefault(key, set())
            bridge_segments = set(road["bridgeSegments"])
            self.assertTrue(all(type(i) is int and 0 <= i < len(points) - 1 for i in bridge_segments))
            for segment_index, (a, b) in enumerate(zip(points, points[1:])):
                pa, pb = (a["x"], a["z"]), (b["x"], b["z"])
                distance = math.dist(pa, pb)
                self.assertGreater(distance, 0, "zero-length road segment")
                path_length += distance
                max_grade = max(max_grade, abs(b["y"] - a["y"]) / distance)
                if segment_index not in bridge_segments:
                    for p in (a, b):
                        self.assertAlmostEqual(height_at(terrain, "modified", p["x"], p["z"]),
                                               p["y"], delta=.003,
                                               msg="surface road center is detached from terrain")
                    x, z = (a["x"] + b["x"]) / 2, (a["z"] + b["z"]) / 2
                    self.assertAlmostEqual(height_at(terrain, "modified", x, z),
                                           (a["y"] + b["y"]) / 2, delta=.003,
                                           msg="surface road midpoint is detached from terrain")
                    self.assertFalse(water_at(terrain, x, z), "unbridged road crosses natural water")
                    dx, dz = (b["x"] - a["x"]) / distance, (b["z"] - a["z"]) / distance
                    half = road["width"] / 2
                    section = [height_at(terrain, "modified", x - dz * offset, z + dx * offset)
                               for offset in (-half, -half / 2, 0, half / 2, half)]
                    for left, right in zip(section, section[1:]):
                        self.assertLessEqual(abs(right - left) / (half / 2), .1202,
                                             f"road {road['id']} segment {segment_index} exceeds 12% local crossfall")
                ka, kb = tuple(round(v, 3) for v in pa), tuple(round(v, 3) for v in pb)
                graph[ka].add(kb)
                graph[kb].add(ka)
                edges.add(tuple(sorted((ka, kb))))
                segments.append((pa, pb, road["width"]))
                if segment_index in bridge_segments:
                    bridge_graph.setdefault(ka, set()).add(kb)
                    bridge_graph.setdefault(kb, set()).add(ka)
                    if water_at(terrain, (a["x"] + b["x"]) / 2, (a["z"] + b["z"]) / 2):
                        self.assertGreater(min(a["y"], b["y"]), terrain["waterLevel"] + 1,
                                           "bridge has no useful water clearance")
                else:
                    surface_nodes.update((ka, kb))
            chord = math.hypot(points[-1]["x"] - points[0]["x"], points[-1]["z"] - points[0]["z"])
            curved += path_length > chord * 1.01
        self.assertLessEqual(max_grade, .1202, "exported road exceeds 12% longitudinal grade")
        self.assertTrue(graph, "no road graph")
        pending, reached = [next(iter(graph))], set()
        while pending:
            current = pending.pop()
            if current not in reached:
                reached.add(current)
                pending.extend(graph[current] - reached)
        self.assertEqual(len(reached), len(graph), "disconnected exported road graph")
        self.assertGreater(len(edges) - len(graph) + 1, 0, "road graph has no city block cycle")
        for endpoint, adjacent in bridge_graph.items():
            if len(adjacent) == 1:
                self.assertIn(endpoint, surface_nodes, "bridge terminates without a landed surface approach")
                self.assertAlmostEqual(height_at(terrain, "modified", *endpoint), elevations[endpoint],
                                       delta=.003, msg="bridge abutment floats above its terrain approach")
        if city["seed"] == 42 and city["density"] == .75:
            self.assertGreaterEqual(len(edges) - len(graph) + 1, 4, "default lacks multiple city block cycles")

        blocks = {block["id"]: block for block in city["blocks"]}
        self.assertTrue(blocks, "no real bounded street blocks")
        for block in blocks.values():
            polygon = block["polygon"]
            self.assertGreaterEqual(len(polygon), 3)
            area = sum(a["x"] * b["z"] - b["x"] * a["z"]
                       for a, b in zip(polygon, polygon[1:] + polygon[:1])) / 2
            self.assertGreater(area, 0, "block is zero-area or unbounded outside face")
            for a, b in zip(polygon, polygon[1:] + polygon[:1]):
                ka, kb = (round(a["x"], 3), round(a["z"], 3)), (round(b["x"], 3), round(b["z"], 3))
                self.assertIn(ka, graph, "block vertex does not come from the actual street network")
                self.assertIn(tuple(sorted((ka, kb))), edges, "block boundary does not follow an actual road")

        buildings = city["buildings"]
        self.assertTrue(buildings, "no buildings survive terrain constraints")
        for i, building in enumerate(buildings):
            self.assertGreater(building["w"], 0)
            self.assertGreater(building["d"], 0)
            self.assertGreater(building["height"], 0)
            self.assertTrue(0 <= building["x"] < building["x"] + building["w"] <= city["size"])
            self.assertTrue(0 <= building["z"] < building["z"] + building["d"] <= city["size"])
            for x, z in footprint_samples(building, step):
                self.assertAlmostEqual(height_at(terrain, "modified", x, z), building["baseY"],
                                       delta=.003, msg=f"building {building['id']} floats or penetrates its platform")
                self.assertFalse(water_at(terrain, x, z), "building occupies natural water")
            self.assertFalse(any(overlaps(building, other) for other in buildings[i + 1:]),
                             "buildings overlap")
            if building["blockId"] is not None:
                self.assertIn(building["blockId"], blocks)
                polygon = blocks[building["blockId"]]["polygon"]
                # All block boundaries are road edges checked against the full
                # rectangle below; inside corners plus no boundary crossing
                # establish containment without resampling a long polygon.
                for x, z in ((building["x"] + dx, building["z"] + dz)
                             for dx in (0, building["w"]) for dz in (0, building["d"])):
                    self.assertTrue(inside_polygon(polygon, x, z), "building assigned to unrelated block")
            for a, b, width in segments:
                self.assertGreaterEqual(segment_rect_distance(a, b, building), width / 2 - .003,
                                        "building overlaps full road width")
        self.assertIsInstance(city["parks"], list)
        for park in city["parks"]:
            self.assertGreater(park["w"], 0)
            self.assertGreater(park["d"], 0)
            for x, z in footprint_samples(park, step):
                self.assertFalse(water_at(terrain, x, z), "park footprint occupies water")
            self.assertFalse(any(overlaps(park, building) for building in buildings), "park overlaps building")
            for a, b, width in segments:
                self.assertGreaterEqual(segment_rect_distance(a, b, park), width / 2 - .003,
                                        "park footprint overlaps actual road")
        self.assertGreater(curved, 0, "roads only raise the old straight grid in Y")


if __name__ == "__main__":
    unittest.main()
