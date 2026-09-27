"""Geometry checks exercise the generated result, independent of partition logic."""

import unittest

from modern_city import generate_city


def overlaps(a, b, tolerance=.003):
    return (min(a["x"] + a["w"], b["x"] + b["w"]) - max(a["x"], b["x"]) > tolerance
            and min(a["z"] + a["d"], b["z"] + b["d"]) - max(a["z"], b["z"]) > tolerance)


def distance(a, b):
    dx = max(0, a["x"] - b["x"] - b["w"], b["x"] - a["x"] - a["w"])
    dz = max(0, a["z"] - b["z"] - b["d"], b["z"] - a["z"] - a["d"])
    return (dx * dx + dz * dz) ** .5


class ModernCityTests(unittest.TestCase):
    def test_geometry_across_parameters(self):
        for layout in ("balanced", "polycentric", "grid"):
            for seed in (0, 42, 917):
                for density in (.25, .45, .75, 1):
                    with self.subTest(layout=layout, seed=seed, density=density):
                        city = generate_city(seed, density, layout)
                        self.assertGreater(len(city["buildings"]), 200)
                        roads = city["roads"]
                        reached, pending = {0}, [0]
                        while pending:
                            current = pending.pop()
                            for i, road in enumerate(roads):
                                if i not in reached and overlaps(roads[current], road):
                                    reached.add(i)
                                    pending.append(i)
                        self.assertEqual(len(reached), len(roads), "disconnected road network")
                        for park in city["parks"]:
                            self.assertGreater(park["w"], 0)
                            self.assertGreater(park["d"], 0)
                            self.assertFalse(any(overlaps(park, road) for road in roads))
                        buildings = city["buildings"]
                        for i, building in enumerate(buildings):
                            block = city["blocks"][building["blockId"]]
                            self.assertGreater(building["w"], 0)
                            self.assertGreater(building["d"], 0)
                            self.assertGreater(building["height"], 0)
                            for axis, dimension in (("x", "w"), ("z", "d")):
                                self.assertGreaterEqual(building[axis], 0)
                                self.assertLessEqual(building[axis] + building[dimension], city["size"])
                                self.assertGreaterEqual(building[axis], block[axis] - .003)
                                self.assertLessEqual(building[axis] + building[dimension],
                                                     block[axis] + block[dimension] + .003)
                            self.assertFalse(any(overlaps(building, road) for road in roads))
                            self.assertFalse(any(overlaps(building, other) for other in buildings[i + 1:]))
                            self.assertFalse(any(overlaps(building, park) for park in city["parks"]))
                            frontage = min(distance(building, road) for road in roads)
                            self.assertLessEqual(frontage, 20, "building lacks street access")
                            if building["style"] in ("perimeter", "midrise"):
                                self.assertLessEqual(frontage, 5.003)
                        self.assertEqual(city["metrics"]["buildingCount"], len(buildings))

    def test_deterministic_and_distinct(self):
        self.assertEqual(generate_city(), generate_city())
        self.assertNotEqual(generate_city(42)["buildings"], generate_city(43)["buildings"])
        self.assertNotEqual(generate_city(layout="balanced")["buildings"],
                            generate_city(layout="polycentric")["buildings"])
        self.assertEqual(len(generate_city(layout="polycentric")["centers"]), 3)
        self.assertEqual(generate_city(layout="grid")["centers"], [])

    def test_density_and_center_gradient(self):
        low, high = generate_city(density=.25), generate_city(density=1)
        self.assertGreater(high["metrics"]["maxHeight"], low["metrics"]["maxHeight"])
        self.assertGreater(high["metrics"]["buildingCoverage"], low["metrics"]["buildingCoverage"])
        buildings = high["buildings"]
        near, far = [], []
        for b in buildings:
            distance = ((b["x"] + b["w"] / 2 - 360) ** 2
                        + (b["z"] + b["d"] / 2 - 345) ** 2) ** .5
            if distance < 150:
                near.append(b["height"])
            elif distance > 300:
                far.append(b["height"])
        self.assertGreater(sum(near) / len(near), sum(far) / len(far) * 1.5)

    def test_invalid_inputs(self):
        for density in (-1, 0, .24, 1.1, float("nan"), float("inf")):
            with self.assertRaises(ValueError):
                generate_city(density=density)
        with self.assertRaises(ValueError):
            generate_city(layout="unknown")
        for seed in (-1, 2147483648, 1.5, True):
            with self.assertRaises(ValueError):
                generate_city(seed=seed)


if __name__ == "__main__":
    unittest.main()
