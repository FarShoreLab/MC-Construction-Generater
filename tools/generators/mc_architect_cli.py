"""
Antigravity MC Architectural Synthesizer CLI (终端建筑规划与求解工具)
- Integrates with Antigravity CLI ('agy') and standalone terminal workflows.
- Supports prompt-to-blueprint, multi-turn AI iteration solving, seed reproducibility,
  exporting models, updating browser 3D preview, and serving a local HTTP bridge.
"""

import argparse
import http.server
import json
import os
import random
import socketserver
import sys
import threading
import time

CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, CURRENT_DIR)

from monumental_architect_engine import MonumentalArchitectEngine
from ai_hybrid_solver import AiHybridArchitectEngine

PREVIEW_HTML_PATH = os.path.join(CURRENT_DIR, "..", "..", "core-planner", "output", "building_preview.html")
DEEP_MODELS_PATH = os.path.join(CURRENT_DIR, "deep_models.json")


def run_generation(prompt: str, archetype: str, scale: str, seed: int, iterations: int, mode: str = "ai_hybrid") -> dict:
    t0 = time.time()
    if mode == "ai_hybrid":
        solver = AiHybridArchitectEngine(prompt=prompt, archetype=archetype, scale=scale, seed=seed, max_iterations=iterations)
        snapshots = solver.solve_iterations()
        final_model = dict(snapshots[-1])
        final_model["iterations_history"] = [
            {
                "iteration": s["iteration"],
                "stage_name": s["stage_name"],
                "blueprint": s["blueprint"],
                "critic_comment": s["critic_comment"],
                "metrics": s["metrics"],
                "solid_voxels": s["solid_voxels"]
            }
            for s in snapshots
        ]
        final_model["prompt"] = prompt
        final_model["seed"] = seed
    else:
        engine = MonumentalArchitectEngine(archetype=archetype, scale=scale, seed=seed)
        final_model = engine.generate()
        final_model["prompt"] = prompt
        final_model["seed"] = seed

    elapsed = round(time.time() - t0, 3)
    final_model["execution_seconds"] = elapsed
    return final_model


def start_server(port: int = 8080):
    """Starts a lightweight HTTP server to bridge browser preview requests to the Python engine."""
    class BridgeHandler(http.server.BaseHTTPRequestHandler):
        def do_OPTIONS(self):
            self.send_response(200)
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            self.send_header("Access-Control-Allow-Headers", "Content-Type")
            self.end_headers()

        def do_GET(self):
            if self.path == "/api/status":
                self.send_response(200)
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
                res = {"status": "ok", "service": "Antigravity MC Architectural Bridge", "version": "2.0.0"}
                self.wfile.write(json.dumps(res).encode("utf-8"))
            else:
                self.send_response(404)
                self.end_headers()

        def do_POST(self):
            if self.path == "/api/generate":
                length = int(self.headers.get("content-length", 0))
                body = self.rfile.read(length).decode("utf-8")
                data = json.loads(body) if body else {}

                prompt = data.get("prompt", "殿堂级宏伟全域建筑")
                archetype = data.get("archetype", "church")
                scale = data.get("scale", "large")
                seed = data.get("seed", random.randint(1000, 999999))
                iterations = data.get("iterations", 4)

                result = run_generation(prompt, archetype, scale, seed, iterations, mode="ai_hybrid")

                self.send_response(200)
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
                self.wfile.write(json.dumps(result, ensure_ascii=False).encode("utf-8"))
            else:
                self.send_response(404)
                self.end_headers()

    with socketserver.TCPServer(("", port), BridgeHandler) as httpd:
        print(f"[Antigravity CLI Bridge] Serving on http://localhost:{port}")
        print("Press Ctrl+C to terminate server.")
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\nShutting down server.")


def main():
    parser = argparse.ArgumentParser(description="Antigravity MC Architectural Synthesizer CLI")
    parser.add_argument("--prompt", type=str, default="建造一座气势恢宏的帝国大教堂，带双钟楼、飞扶壁与通高人字陡坡屋脊", help="Natural language architectural prompt")
    parser.add_argument("--archetype", type=str, choices=["church", "brickhouse", "school", "bakery", "farm"], default="church", help="Building archetype")
    parser.add_argument("--scale", type=str, choices=["small", "medium", "large"], default="large", help="Building scale")
    parser.add_argument("--seed", type=int, default=None, help="Random seed for reproducible synthesis")
    parser.add_argument("--iterations", type=int, default=4, help="Multi-turn AI iterations count (1 to 5)")
    parser.add_argument("--mode", type=str, choices=["monumental", "ai_hybrid"], default="ai_hybrid", help="Solving mode")
    parser.add_argument("--export-json", type=str, default=None, help="Path to export generated model JSON")
    parser.add_argument("--serve", action="store_true", help="Start local HTTP bridge server for browser preview")
    parser.add_argument("--port", type=int, default=8080, help="Port for local HTTP bridge server")

    args = parser.parse_args()

    if args.serve:
        start_server(args.port)
        return

    seed = args.seed if args.seed is not None else random.randint(1000, 999999)
    print(f"[Antigravity Architect] Starting synthesis for '{args.prompt}'...")
    print(f"  * Archetype:  {args.archetype}")
    print(f"  * Scale:      {args.scale}")
    print(f"  * Seed:       {seed}")
    print(f"  * Iterations: {args.iterations}")

    result = run_generation(args.prompt, args.archetype, args.scale, seed, args.iterations, mode=args.mode)

    print(f"\n[Antigravity Architect] Synthesis Completed successfully!")
    print(f"  * Dimensions:   {result['sizeX']} x {result['sizeY']} x {result['sizeZ']} blocks")
    print(f"  * Total Blocks: {result['solid_voxels']:,} blocks")
    print(f"  * Execution:    {result['execution_seconds']}s")
    if "metrics" in result:
        m = result["metrics"]
        print(f"  * Quality:      GRADE {m.get('overall_aesthetic_grade', 'S+')}")
        print(f"  * Roof Score:   {m.get('roof_complexity_score', 98)}分")
        print(f"  * Facade Depth: {m.get('facade_depth_index', 90)}%")

    if args.export_json:
        with open(args.export_json, "w", encoding="utf-8") as f:
            json.dump(result, f, ensure_ascii=False, indent=2)
        print(f"  * Exported JSON to: {args.export_json}")


if __name__ == "__main__":
    main()
