"""Exercise the city demo through the running local HTTP server."""
import argparse
import json
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import urlopen


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8766)
    args = parser.parse_args()
    base = f"http://127.0.0.1:{args.port}"

    def fetch(path):
        try:
            response = urlopen(base + path, timeout=90)
        except HTTPError as error:
            response = error
        with response:
            body = response.read()
            assert len(body) == int(response.headers["Content-Length"])
            return response.status, body

    for path in ("/modern-city", "/vendor/three.min.js", "/vendor/OrbitControls.js", "/", "/api/terrain-controls"):
        status, body = fetch(path)
        assert status == 200 and body, (path, status)
    samples = {}
    for layout in ("balanced", "polycentric", "grid"):
        path = f"/api/modern-city?seed=42&density=0.75&layout={layout}&terrain=flat"
        status, body = fetch(path)
        assert status == 200, body
        data = json.loads(body)
        assert data["buildings"] and data["roads"] and data["blocks"]
        assert fetch(path)[1] == body, "Same request must reproduce identical geometry"
        samples[layout] = data["metrics"]
    for terrain in ("hills", "river"):
        path = f"/api/modern-city?seed=42&terrain={terrain}&maxEdit=6"
        status, body = fetch(path)
        assert status == 200, body
        data = json.loads(body)
        field = data["terrain"]
        assert field["type"] == terrain
        assert len(field["original"]) == len(field["modified"]) == field["resolution"] ** 2
        assert max(field["original"]) - min(field["original"]) > 8
        assert field["original"] == [h + 1 for h in field["mcLayer"]["heights"]]
        assert max(abs(a-b) for a,b in zip(field["original"], field["modified"])) <= 6.001
        assert data["buildings"] and all("baseY" in b for b in data["buildings"])
        assert fetch(path)[1] == body
        samples[terrain] = data["metrics"]
    status, body = fetch("/api/modern-city")
    assert status == 200 and json.loads(body)["terrain"]["type"] == "hills"
    status, body = fetch("/api/modern-city-terrain?seed=42&terrain=hills")
    unplanned = json.loads(body)
    assert status == 200 and not unplanned["roads"] and not unplanned["buildings"]
    assert unplanned["terrain"]["original"] == unplanned["terrain"]["modified"]
    for query in ("density=nan", "density=inf", "density=-1", "density=2", "layout=unknown", "seed=abc", "seed=1&seed=2", "extra=1", "density=", "terrain=unknown", "maxEdit=nan", "maxEdit=1", "maxEdit=11"):
        status, body = fetch("/api/modern-city?" + query)
        assert status == 400 and json.loads(body)["ok"] is False, (query, status)
    destination = Path(__file__).resolve().parents[1] / "build/verification/modern-city"
    destination.mkdir(parents=True, exist_ok=True)
    (destination / "api-results.json").write_text(json.dumps({"status": "PASS", "samples": samples}, ensure_ascii=False, indent=2), encoding="utf-8")
    print("PASS: assets, legacy landing/schema, three flat layouts, hills/river terrain, repeatability, thirteen invalid requests")
    print(json.dumps(samples, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
