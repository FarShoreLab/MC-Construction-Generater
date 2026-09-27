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
            response = urlopen(base + path, timeout=15)
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
        path = f"/api/modern-city?seed=42&density=0.75&layout={layout}"
        status, body = fetch(path)
        assert status == 200, body
        data = json.loads(body)
        assert data["buildings"] and data["roads"] and data["blocks"]
        assert fetch(path)[1] == body, "Same request must reproduce identical geometry"
        samples[layout] = data["metrics"]
    for query in ("density=nan", "density=inf", "density=-1", "density=2", "layout=unknown", "seed=abc", "seed=1&seed=2", "extra=1", "density="):
        status, body = fetch("/api/modern-city?" + query)
        assert status == 400 and json.loads(body)["ok"] is False, (query, status)
    destination = Path(__file__).resolve().parents[1] / "build/verification/modern-city"
    destination.mkdir(parents=True, exist_ok=True)
    (destination / "api-results.json").write_text(json.dumps({"status": "PASS", "samples": samples}, ensure_ascii=False, indent=2), encoding="utf-8")
    print("PASS: city assets, legacy landing/schema, three layouts, repeatability, nine invalid requests")
    print(json.dumps(samples, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
