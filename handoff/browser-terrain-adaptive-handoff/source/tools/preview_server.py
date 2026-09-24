"""Local-only rehearsal server for the Java settlement planner.

The browser receives static assets from this process and each plan request is
executed by the real Java SimulationApiRunner. No external network is used.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse


ROOT = Path(__file__).resolve().parents[1]
PAGE = ROOT / "tools" / "preview.html"
THREE = ROOT / "tools" / "generators" / "three.min.js"
ORBIT = ROOT / "tools" / "generators" / "OrbitControls.js"
CLASSES = ROOT / "core-planner" / "build" / "classes" / "java" / "main"
RESOURCES = ROOT / "core-planner" / "src" / "main" / "resources"
JAVA_MAIN = "org.mcsettlement.planner.simulation.SimulationApiRunner"
REQUEST_LOCK = threading.Lock()


def find_gson() -> Path | None:
    candidates: list[Path] = []
    roots = [ROOT / ".gradle", Path.home() / ".gradle", Path("C:/Users/Construct/.gradle")]
    for base in roots:
        pattern = base / "caches" / "modules-2" / "files-2.1" / "com.google.code.gson" / "gson"
        if pattern.exists():
            candidates.extend(pattern.glob("*/*/gson-*.jar"))
    exact = [path for path in candidates if path.is_file() and path.name == "gson-2.10.1.jar"]
    if exact:
        return exact[0]
    jars = [path for path in candidates if path.is_file() and "sources" not in path.name and "javadoc" not in path.name]
    return jars[0] if jars else None


GSON = find_gson()


def run_java(query: dict[str, str]) -> dict:
    if not CLASSES.exists():
        raise RuntimeError(f"Java classes not found: {CLASSES}")
    classpath = [str(CLASSES), str(RESOURCES)]
    if GSON is not None:
        classpath.append(str(GSON))
    args = [
        f"--width={query['width']}",
        f"--depth={query['depth']}",
        f"--baseElevation={query['baseElevation']}",
        f"--relief={query['relief']}",
        f"--terrainType={query['terrainType']}",
        f"--terrainSeed={query['terrainSeed']}",
        f"--planSeed={query['planSeed']}",
        f"--targetPlots={query['targetPlots']}",
    ]
    completed = subprocess.run(
        ["java", "-cp", os.pathsep.join(classpath), JAVA_MAIN, *args],
        cwd=ROOT,
        capture_output=True,
        text=True,
        encoding="utf-8",
        timeout=90,
        check=False,
    )
    if completed.returncode != 0:
        detail = completed.stderr.strip() or completed.stdout.strip() or "Java planner failed"
        raise RuntimeError(detail[-2000:])
    try:
        payload = json.loads(completed.stdout)
    except json.JSONDecodeError as exc:
        raise RuntimeError(f"Java planner returned invalid JSON: {exc}") from exc
    if not payload.get("ok", False):
        raise RuntimeError("Java planner rejected the request")
    return payload


def query_values(parsed) -> dict[str, str]:
    defaults = {
        "width": "96",
        "depth": "96",
        "baseElevation": "58",
        "relief": "24",
        "terrainType": "rolling_hills",
        "terrainSeed": "42",
        "planSeed": "42",
        "targetPlots": "7",
    }
    query = parse_qs(parsed.query, keep_blank_values=False)
    for key in defaults:
        values = query.get(key)
        if values:
            defaults[key] = values[-1]
    if not re.fullmatch(r"[A-Za-z0-9_]+", defaults["terrainType"]):
        raise ValueError("terrainType contains unsupported characters")
    integer_keys = ("width", "depth", "baseElevation", "relief", "terrainSeed", "planSeed", "targetPlots")
    for key in integer_keys:
        int(defaults[key])
    return defaults


class Handler(BaseHTTPRequestHandler):
    server_version = "MCSettlementPreview/1.0"

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        try:
            if parsed.path in ("/", "/index.html", "/simulation_3d_viewer.html"):
                self.send_file(PAGE, "text/html; charset=utf-8")
                return
            if parsed.path == "/vendor/three.min.js":
                self.send_file(THREE, "text/javascript; charset=utf-8")
                return
            if parsed.path == "/vendor/OrbitControls.js":
                self.send_file(ORBIT, "text/javascript; charset=utf-8")
                return
            if parsed.path == "/api/plan":
                query = query_values(parsed)
                with REQUEST_LOCK:
                    payload = run_java(query)
                self.send_json(payload)
                return
            self.send_error(404, "Not found")
        except (ValueError, RuntimeError, subprocess.TimeoutExpired) as exc:
            self.send_json({"ok": False, "error": str(exc)}, status=400)

    def send_file(self, path: Path, content_type: str) -> None:
        if not path.is_file():
            self.send_error(404, "Asset not found")
            return
        data = path.read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        self.wfile.write(data)

    def send_json(self, payload: dict, status: int = 200) -> None:
        data = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, fmt: str, *args) -> None:
        sys.stderr.write(f"[preview] {self.address_string()} - {fmt % args}\n")


def main() -> None:
    parser = argparse.ArgumentParser(description="Serve the local Java settlement rehearsal")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8766)
    args = parser.parse_args()
    if args.host != "127.0.0.1":
        raise SystemExit("The preview server is intentionally limited to 127.0.0.1")
    print(f"MC settlement preview: http://{args.host}:{args.port}/", flush=True)
    with ThreadingHTTPServer((args.host, args.port), Handler) as server:
        server.serve_forever()


if __name__ == "__main__":
    main()
