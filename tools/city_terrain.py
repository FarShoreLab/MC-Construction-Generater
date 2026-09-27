"""Roads and platforms on the existing Java Minecraft terrain, at 1:1 scale.

Natural ground, water, materials and trees come only from TerrainBlockGenerator.
Construction is checked against every original one-block column.
"""
import heapq
import math
import random
from collections import deque


def _box_mean(values, n, radius):
    stride = n + 1
    integral = [0.] * (stride * stride)
    for z in range(n):
        total = 0.
        for x in range(n):
            total += values[z * n + x]
            integral[(z + 1) * stride + x + 1] = integral[z * stride + x + 1] + total
    result = []
    for z in range(n):
        z0, z1 = max(0, z - radius), min(n, z + radius + 1)
        for x in range(n):
            x0, x1 = max(0, x - radius), min(n, x + radius + 1)
            total = (integral[z1 * stride + x1] - integral[z0 * stride + x1]
                     - integral[z1 * stride + x0] + integral[z0 * stride + x0])
            result.append(total / ((x1 - x0) * (z1 - z0)))
    return result


def _square_max(values, n, radius):
    def line_max(line):
        window, out, end = deque(), [], 0
        for center in range(n):
            while end < min(n, center + radius + 1):
                while window and line[window[-1]] <= line[end]:
                    window.pop()
                window.append(end)
                end += 1
            while window[0] < center - radius:
                window.popleft()
            out.append(line[window[0]])
        return out
    horizontal = []
    for z in range(n):
        horizontal.extend(line_max(values[z * n:(z + 1) * n]))
    result = [0.] * len(values)
    for x in range(n):
        for z, value in enumerate(line_max([horizontal[z * n + x] for z in range(n)])):
            result[z * n + x] = value
    return result


def generate_terrain_city(seed, density, layout, terrain_type, max_edit):
    from legacy_city_terrain import terrain_preview
    from city_districts import finalize_districts
    city = terrain_preview(seed, terrain_type)
    field = city["terrain"]
    n, size = field["resolution"], city["size"]
    original, water = field["original"], field["waterMask"]
    water_level = field["waterLevel"]
    rng = random.Random(seed + 7349)
    modified = list(original)
    # Smoothing proposes a buildable roadbed, never a replacement source map.
    reference = _box_mean(_box_mean(original, n, 28), n, 8)
    slope = [0.] * (n * n)
    for z in range(1, n - 1):
        for x in range(1, n - 1):
            i = z * n + x
            gx = max(abs(reference[i + 1] - reference[i]), abs(reference[i] - reference[i - 1]))
            gz = max(abs(reference[i + n] - reference[i]), abs(reference[i] - reference[i - n]))
            slope[i] = math.hypot(gx, gz)
    envelope_slope = _square_max(slope, n, 8)
    envelope_edit = _square_max([abs(a - b) for a, b in zip(reference, original)], n, 8)
    envelope_water = _square_max(water, n, 8)
    stride, gn = 8, (n - 1) // 8 + 1
    elevations = [reference[z * stride * n + x * stride] for z in range(gn) for x in range(gn)]
    viable = set()
    for z in range(2, gn - 2):
        for x in range(2, gn - 2):
            i = z * stride * n + x * stride
            if not envelope_water[i] and envelope_slope[i] <= .105 and envelope_edit[i] <= max_edit:
                viable.add(z * gn + x)
    neighbors = {node: [] for node in viable}
    for node in viable:
        x, z = node % gn, node // gn
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            other = (z + dz) * gn + x + dx
            if other in viable:
                grade = abs(elevations[node] - elevations[other]) / stride
                if grade <= .105:
                    neighbors[node].append((other, stride * (1 + grade * 8)))

    # Search real water-mask gaps for narrow bridges with adequate dry ramps.
    # This supports irregular ponds/valleys; no assumed synthetic river axis.
    bridge_candidates = []
    for axis in (0, 1):
        for fixed in range(3, gn - 3, 2):
            row_nodes = [(fixed * gn + k if axis == 0 else k * gn + fixed) for k in range(2, gn - 2)]
            positions = [k for k, node in enumerate(row_nodes) if node in viable]
            for left, right in zip(positions, positions[1:]):
                if not 1 < right - left <= 18:
                    continue
                a, b = row_nodes[left], row_nodes[right]
                ax, az, bx, bz = a % gn * stride, a // gn * stride, b % gn * stride, b // gn * stride
                length = abs(bx - ax) + abs(bz - az)
                samples = [(round(ax + (bx - ax) * t / length), round(az + (bz - az) * t / length))
                           for t in range(length + 1)]
                wet_positions = [t for t, (x, z) in enumerate(samples) if water[z * n + x]]
                if not wet_positions or wet_positions[-1] - wet_positions[0] > 72:
                    continue
                deck = max(water_level + 2, elevations[a], elevations[b])
                ramp_in, ramp_out = max(1, wet_positions[0] - 6), max(1, length - wet_positions[-1] - 6)
                if (deck - elevations[a]) / ramp_in > .10 or (deck - elevations[b]) / ramp_out > .10:
                    continue
                points, clear = [], True
                for t, (x, z) in enumerate(samples):
                    y = (elevations[a] + (deck - elevations[a]) * t / ramp_in if t < ramp_in
                         else elevations[b] + (deck - elevations[b]) * (length - t) / ramp_out
                         if length - t < ramp_out else deck)
                    if any(original[zz * n + xx] > y + .02 for zz in range(z - 5, z + 6)
                           for xx in range(x - 5, x + 6)):
                        clear = False
                        break
                    points.append(dict(x=x, y=round(y, 5), z=z))
                if clear:
                    bridge_candidates.append((length, a, b, points))
    bridge_paths, chosen_centers = {}, []
    for length, a, b, points in sorted(bridge_candidates):
        center = points[len(points) // 2]
        if any(math.hypot(center["x"] - x, center["z"] - z) < 96 for x, z in chosen_centers):
            continue
        bridge_paths[a, b], bridge_paths[b, a] = points, list(reversed(points))
        neighbors[a].append((b, length * 1.3))
        neighbors[b].append((a, length * 1.3))
        chosen_centers.append((center["x"], center["z"]))
        if len(chosen_centers) == 3:
            break
    unseen, component = set(viable), set()
    while unseen:
        initial = min(unseen)
        reached, pending = {initial}, [initial]
        unseen.remove(initial)
        while pending:
            for other, _ in neighbors[pending.pop()]:
                if other in unseen:
                    unseen.remove(other)
                    reached.add(other)
                    pending.append(other)
        if len(reached) > len(component):
            component = reached
    if not component:
        raise ValueError("No connected road corridor fits this source terrain and earthwork budget")

    def anchor(x, z):
        return min(component, key=lambda node: (node % gn * stride - x) ** 2 + (node // gn * stride - z) ** 2)

    def route(start, goal):
        gx, gz = goal % gn, goal // gn
        frontier, cost, previous = [(0., start)], {start: 0.}, {}
        while frontier:
            _, node = heapq.heappop(frontier)
            if node == goal:
                path = [node]
                while node != start:
                    node = previous[node]
                    path.append(node)
                return path[::-1]
            for other, edge_cost in neighbors[node]:
                candidate = cost[node] + edge_cost
                if other in component and candidate < cost.get(other, math.inf):
                    cost[other], previous[other] = candidate, node
                    estimate = stride * (abs(other % gn - gx) + abs(other // gn - gz))
                    heapq.heappush(frontier, (candidate + estimate, other))
        raise RuntimeError("Connected terrain road graph lost a route")

    coords = [24 + i * (size - 56) / 7 for i in range(8)]
    anchors = [[anchor(x, z) for x in coords] for z in coords]
    roads, road_cells, pavement_cells, used_routes = [], set(), set(), set()
    for row in range(8):
        for col in range(8):
            for dr, dc in ((0, 1), (1, 0)):
                if row + dr == 8 or col + dc == 8:
                    continue
                start, goal = anchors[row][col], anchors[row + dr][col + dc]
                pair = tuple(sorted((start, goal)))
                if start == goal or pair in used_routes:
                    continue
                used_routes.add(pair)
                path = route(start, goal)
                major = (row if dc else col) in (0, 3, 6)
                kind = "arterial" if major else "collector" if (row + col) % 2 else "local"
                width = 12 if major else 8 if kind == "collector" else 6
                points, bridge_segments = [], []
                for a, b in zip(path, path[1:]):
                    bridge = (a, b) in bridge_paths
                    if bridge:
                        segment = bridge_paths[a, b]
                    else:
                        ax, az, bx, bz = a % gn * stride, a // gn * stride, b % gn * stride, b // gn * stride
                        segment = []
                        for t in range(stride + 1):
                            x, z = round(ax + (bx - ax) * t / stride), round(az + (bz - az) * t / stride)
                            segment.append(dict(x=x, y=round(reference[z * n + x], 5), z=z))
                    if not points:
                        points.append(segment[0])
                    for point in segment[1:]:
                        if bridge:
                            bridge_segments.append(len(points) - 1)
                        points.append(point)
                bridge_set = set(bridge_segments)
                for j, (a, b) in enumerate(zip(points, points[1:])):
                    radius = width / 2 + 1
                    for z in range(max(0, math.floor(min(a["z"], b["z"]) - radius)),
                                   min(n, math.ceil(max(a["z"], b["z"]) + radius) + 1)):
                        for x in range(max(0, math.floor(min(a["x"], b["x"]) - radius)),
                                       min(n, math.ceil(max(a["x"], b["x"]) + radius) + 1)):
                            px = max(min(a["x"], b["x"]), min(max(a["x"], b["x"]), x))
                            pz = max(min(a["z"], b["z"]), min(max(a["z"], b["z"]), z))
                            dist = math.hypot(x - px, z - pz)
                            if dist > radius:
                                continue
                            i = z * n + x
                            road_cells.add(i)
                            if dist <= width / 2:
                                pavement_cells.add(i)
                            if j not in bridge_set:
                                if water[i] or abs(reference[i] - original[i]) > max_edit + 1e-8:
                                    raise RuntimeError("Road footprint escaped its checked construction envelope")
                                modified[i] = reference[i]
                roads.append(dict(id=len(roads), kind=kind, width=width, points=points, bridgeSegments=bridge_segments))

    distance = [10000] * (n * n)
    queue = deque(sorted(road_cells))
    for i in road_cells:
        distance[i] = 0
    while queue:
        i = queue.popleft()
        for other in (i - 1 if i % n else -1, i + 1 if i % n < n - 1 else -1, i - n, i + n):
            if 0 <= other < n * n and distance[other] > distance[i] + 1:
                distance[other] = distance[i] + 1
                queue.append(other)
    centers = ({"balanced": [(size * .50, size * .48, 1)],
                "polycentric": [(size * .30, size * .31, 1), (size * .72, size * .65, .95), (size * .31, size * .77, .7)],
                "grid": []}[layout])
    candidates = []
    for z in range(12, size - 28, 8):
        for x in range(12, size - 28, 8):
            choices = (12, 16) if density > .6 else (20, 24, 28)
            candidates.append((x, z, rng.choice(choices), rng.choice(choices)))
    rng.shuffle(candidates)
    candidates.sort(key=lambda p: distance[p[1] * n + p[0]])
    occupied, buildings, rejected = set(), [], 0
    for x, z, w, d in candidates:
        if x + w + 2 >= n or z + d + 2 >= n:
            continue
        cells = [(z + dz) * n + x + dx for dz in range(d + 1) for dx in range(w + 1)]
        if any(i in occupied or i in road_cells or water[i] for i in cells):
            continue
        if min(distance[i] for i in cells) > 22:
            continue
        margin = [(z + dz) * n + x + dx for dz in range(-2, d + 3) for dx in range(-2, w + 3)]
        if any(water[i] for i in margin):
            continue
        heights = sorted(original[i] for i in cells)
        low, high = heights[-1] - max_edit, heights[0] + max_edit
        if low > high or (heights[-1] - heights[0]) / max(w, d) > .4:
            rejected += 1
            continue
        base = round(max(low, min(high, heights[len(heights) // 2])), 5)
        cx, cz = x + w / 2, z + d / 2
        influence = (.45 if not centers else max(s * math.exp(-((cx - px) ** 2 + (cz - pz) ** 2)
                                                              / (2 * (size * .24) ** 2)) for px, pz, s in centers))
        zone = "office" if influence > .7 and rng.random() < .45 else "mixed" if influence > .4 else "residential"
        tower = zone == "office" and density > .5 and w >= 16 and d >= 16
        height = round((14 + density * (18 + influence ** 2 * (110 if tower else 54))) * rng.uniform(.8, 1.18) / 3) * 3
        buildings.append(dict(id=len(buildings), blockId=None, x=x, z=z, w=w, d=d, height=height,
                              baseY=base, zone=zone, style="tower" if tower else "midrise",
                              platform=dict(x=x, z=z, w=w, d=d, baseY=base)))
        occupied.update(margin)
        for i in cells:
            modified[i] = base

    changes = [modified[i] - original[i] for i in range(n * n)]
    grades = [abs(a["y"] - b["y"]) / math.hypot(a["x"] - b["x"], a["z"] - b["z"])
              for road in roads for a, b in zip(road["points"], road["points"][1:])]
    bridge_graph = {}
    for road in roads:
        for j in road["bridgeSegments"]:
            a, b = road["points"][j:j + 2]
            u, v = (a["x"], a["z"]), (b["x"], b["z"])
            bridge_graph.setdefault(u, set()).add(v)
            bridge_graph.setdefault(v, set()).add(u)
    remaining, bridge_count = set(bridge_graph), 0
    while remaining:
        bridge_count += 1
        pending = [remaining.pop()]
        while pending:
            for other in bridge_graph[pending.pop()]:
                if other in remaining:
                    remaining.remove(other)
                    pending.append(other)
    field["modified"] = [round(h, 5) for h in modified]
    field["maxEdit"] = max_edit
    city.update(density=density, layout=layout, roads=roads, buildings=buildings, parks=[], blocks=[],
                centers=[dict(x=x, z=z, strength=s) for x, z, s in centers])
    city["metrics"].update(buildingCount=len(buildings), maxHeight=max((b["height"] for b in buildings), default=0),
        buildingCoverage=round(sum(b["w"] * b["d"] for b in buildings) / size ** 2, 4),
        roadAreaRatio=round(len(pavement_cells) / size ** 2, 4),
        roadReservationRatio=round(len(road_cells) / size ** 2, 4),
        populationEstimate=round(sum(b["w"] * b["d"] * b["height"] / 3 * (.85 if b["zone"] == "residential" else .35)
                                     / 35 for b in buildings if b["zone"] != "office")),
        cutVolume=round(sum(max(0, -change) for change in changes)),
        fillVolume=round(sum(max(0, change) for change in changes)),
        maxEditObserved=round(max(map(abs, changes)), 5), maxRoadGrade=round(max(grades, default=0), 5),
        bridgeCount=bridge_count, rejectedPlatforms=rejected,
        protectedAreaRatio=round(sum(i not in occupied and i not in road_cells for i in range(n * n)) / (n * n), 4))
    city["metrics"]["unchangedTerrainRatio"] = round(sum(abs(change) < 1e-5 for change in changes) / (n * n), 4)
    finalize_districts(city)
    return city
