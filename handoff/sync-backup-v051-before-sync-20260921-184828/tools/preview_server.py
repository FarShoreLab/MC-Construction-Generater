"""Local-only rehearsal server for the Java settlement planner.

The browser receives static assets from this process and each plan request is
executed by the real Java SimulationApiRunner. No external network is used.
"""

from __future__ import annotations

import argparse
import json
import math
import os
import re
import subprocess
import sys
import threading
import tempfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlencode, parse_qs, urlparse


ROOT = Path(__file__).resolve().parents[1]
PAGE = ROOT / "tools" / "preview.html"
THREE = ROOT / "tools" / "generators" / "three.min.js"
ORBIT = ROOT / "tools" / "generators" / "OrbitControls.js"
CLASSES = ROOT / "core-planner" / "build" / "classes" / "java" / "main"
RESOURCES = ROOT / "core-planner" / "src" / "main" / "resources"
TERRAIN_SCHEMA_PATH = RESOURCES / "terrain-controls.json"
TERRAIN_SCHEMA = json.loads(TERRAIN_SCHEMA_PATH.read_text(encoding="utf-8"))
TERRAIN_SPECS = {spec["key"]: spec for spec in TERRAIN_SCHEMA["controls"]}
TERRAIN_INPUTS = {"width", "depth", "baseElevation", "relief", "terrainType", "terrainSeed"} | set(TERRAIN_SPECS)
JAVA_MAIN = "org.mcsettlement.planner.simulation.SimulationApiRunner"
REQUEST_LOCK = threading.Lock()
MAX_RESPONSE_BYTES = 32 * 1024 * 1024


def find_gson() -> Path | None:
    override = os.environ.get("GSON_JAR")
    if override:
        jar = Path(override).expanduser().resolve()
        if not jar.is_file():
            raise RuntimeError(f"GSON_JAR does not exist: {jar}")
        return jar
    candidates: list[Path] = []
    roots = [ROOT / ".gradle", Path.home() / ".gradle"]
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


def run_java(query: dict[str, str], terrain_only: bool = False) -> dict:
    if not CLASSES.exists():
        raise RuntimeError(f"Java classes not found: {CLASSES}")
    classpath = [str(CLASSES), str(RESOURCES)]
    if GSON is not None:
        classpath.append(str(GSON))
    args = [f"--{key}={value}" for key, value in query.items()]
    if terrain_only:
        args.append("--terrainOnly=true")
    with tempfile.TemporaryFile() as stdout, tempfile.TemporaryFile() as stderr:
        completed = subprocess.run(
            ["java", "-Xmx1g", "-cp", os.pathsep.join(classpath), JAVA_MAIN, *args],
            cwd=ROOT, stdout=stdout, stderr=stderr, timeout=90, check=False,
        )
        size = stdout.tell()
        if size > MAX_RESPONSE_BYTES:
            raise RuntimeError("RESPONSE_BYTE_LIMIT: no partial response was returned")
        if completed.returncode != 0:
            stderr.seek(max(0, stderr.tell() - 4000))
            raise RuntimeError(stderr.read(4000).decode("utf-8", errors="replace") or "Java planner failed")
        stdout.seek(0)
        data = stdout.read(MAX_RESPONSE_BYTES + 1)
    try:
        payload = json.loads(data)
    except json.JSONDecodeError as exc:
        raise RuntimeError(f"Java planner returned invalid JSON: {exc}") from exc
    if not payload.get("ok", False):
        raise RuntimeError("Java planner rejected the request")
    return payload


def query_values(parsed, terrain_only: bool = False) -> dict[str, str]:
    defaults = {
        "layoutMode": "expert", "minBBoxCoverage": "0.25", "minMinorAxisRatio": "0.30",
        "allowBridges": "true", "autoDock": "true", "maxBridgeSpan": "48", "pins": "[]",
        "width": "128",
        "depth": "128",
        "baseElevation": "58",
        "relief": "24",
        "terrainType": "rolling_hills",
        "terrainSeed": "42",
        "planSeed": "42",
        "targetPlots": "7",
        "roadDirections": "8", "diagonalBuildings": "false",
        "presetPalette": "classic", "singlePresetId": "square_cabin",
        "candidateChecks": "24000", "pathExpanded": "180000", "attempts": "3",
        "pathStates": "100000", "gradeRelaxations": "2000000",
        "groundColumns": "40000", "constructionEdits": "500000",
    }
    query = parse_qs(parsed.query, keep_blank_values=True)
    if any(len(values) != 1 or not values[0].strip() for values in query.values()):
        raise ValueError("Each parameter must occur once with a non-empty value")
    if terrain_only and set(query) - TERRAIN_INPUTS:
        raise ValueError("Terrain-only endpoint accepts terrain parameters only")
    selected_type = query.get("terrainType", [defaults["terrainType"]])[0]
    if selected_type not in {t["id"] for t in TERRAIN_SCHEMA["types"]}:
        raise ValueError("Invalid terrainType")
    for key, spec in TERRAIN_SPECS.items():
        value = spec["default"]
        defaults[key] = str(value[selected_type] if isinstance(value, dict) else value)
    for key in defaults:
        values = query.get(key)
        if values:
            defaults[key] = values[-1]
    if set(query) - set(defaults):
        raise ValueError("Unknown request parameter")
    if defaults["terrainType"] not in {"rolling_hills", "mountain", "valley", "plateau"}:
        raise ValueError("Invalid terrainType")
    if defaults["diagonalBuildings"] not in {"true", "false"}:
        raise ValueError("diagonalBuildings must be true or false")
    if defaults["presetPalette"] not in {"classic", "mixed", "compact", "spacious", "single"}:
        raise ValueError("Invalid presetPalette")
    if not re.fullmatch(r"[a-z][a-z0-9_]{0,63}", defaults["singlePresetId"]):
        raise ValueError("Invalid singlePresetId")
    if not (RESOURCES / "presets" / (defaults["singlePresetId"] + ".json")).is_file():
        raise ValueError("Unknown singlePresetId")
    bounds = {"width": (32, 512), "depth": (32, 512), "baseElevation": (50, 75),
              "relief": (4, 36), "targetPlots": (1, 12), "roadDirections": (8, 12),
              "terrainSeed": (-9007199254740991, 9007199254740991),
              "planSeed": (-9007199254740991, 9007199254740991),
              "candidateChecks": (1, 200000), "pathExpanded": (1, 2000000),
              "attempts": (1, 8), "pathStates": (1, 200000), "gradeRelaxations": (1, 10000000),
              "groundColumns": (1, 100000), "constructionEdits": (1, 1000000)}
    for key, (low, high) in bounds.items():
        value = int(defaults[key])
        if not low <= value <= high:
            raise ValueError(f"{key} must be between {low} and {high}")
        defaults[key] = str(value)
    for key, spec in TERRAIN_SPECS.items():
        value = int(defaults[key]) if spec["integer"] else float(defaults[key])
        if not math.isfinite(value) or not spec["min"] <= value <= spec["max"]:
            raise ValueError(f"{key} must be finite and between {spec['min']} and {spec['max']}")
        defaults[key] = str(value)
    if defaults["layoutMode"] not in {"expert", "legacy"}:
        raise ValueError("Invalid layoutMode")
    for key in ["allowBridges", "autoDock"]:
        if defaults[key] not in {"true", "false"}:
            raise ValueError(key + " must be true or false")
    for key, high in [("minBBoxCoverage", .85), ("minMinorAxisRatio", .9)]:
        value=float(defaults[key])
        if not math.isfinite(value) or not 0 <= value <= high:
            raise ValueError(key + " outside finite range")
    if not 4 <= int(defaults["maxBridgeSpan"]) <= 96:
        raise ValueError("maxBridgeSpan must be 4–96")
    if len(defaults["pins"].encode("utf-8")) > 8192:
        raise ValueError("PIN_JSON_LIMIT")
    pins=json.loads(defaults["pins"], object_pairs_hook=unique_object)
    if not isinstance(pins,list) or len(pins)>8:
        raise ValueError("PINS_MUST_BE_ARRAY_MAX_8")
    if defaults["roadDirections"] not in {"8", "12"}:
        raise ValueError("roadDirections must be 8 or 12")
    return defaults


def unique_object(pairs):
    result={}
    for key,value in pairs:
        if key in result: raise ValueError("DUPLICATE_JSON_KEY: " + key)
        result[key]=value
    return result


class Handler(BaseHTTPRequestHandler):
    server_version = "MCSettlementPreview/5.0"

    def do_POST(self) -> None:
        if self.path != "/api/plan":
            self.send_json({"ok":False,"error":"POST supports /api/plan only"},status=404);return
        try:
            if self.headers.get("Transfer-Encoding") or self.headers.get_content_type() != "application/json":
                raise ValueError("A bounded application/json body with Content-Length is required")
            length=int(self.headers.get("Content-Length","0"))
            if not 0 < length <= 16384:
                self.send_json({"ok":False,"error":"REQUEST_BODY_LIMIT_16_KIB"},status=413);return
            self.connection.settimeout(10)
            body=self.rfile.read(length)
            if len(body)!=length: raise ValueError("INCOMPLETE_REQUEST_BODY")
            obj=json.loads(body.decode("utf-8"),object_pairs_hook=unique_object)
            if not isinstance(obj,dict): raise ValueError("JSON object required")
            flat={}
            for key,value in obj.items():
                if key=="pins":
                    if not isinstance(value,list): raise ValueError("pins must be an array")
                    flat[key]=json.dumps(value,separators=(",",":"),ensure_ascii=False)
                elif isinstance(value,bool): flat[key]=str(value).lower()
                elif isinstance(value,(str,int,float)): flat[key]=str(value)
                else: raise ValueError("Scalar parameter required: "+key)
            query=query_values(urlparse("/api/plan?"+urlencode(flat)))
            if not REQUEST_LOCK.acquire(blocking=False):
                self.send_json({"ok":False,"error":"Planner busy; only one request runs at a time"},status=429);return
            try: self.send_json(run_java(query))
            finally: REQUEST_LOCK.release()
        except (ValueError,RuntimeError,subprocess.TimeoutExpired,TimeoutError) as exc:
            self.send_json({"ok":False,"error":str(exc)},status=400)

    def do_GET(self) -> None:
        if len(self.path) > 16384:
            self.send_json({"ok": False, "error": "REQUEST_URL_LIMIT"}, status=414)
            return
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
            if parsed.path == "/api/terrain-controls":
                self.send_file(TERRAIN_SCHEMA_PATH, "application/json; charset=utf-8")
                return
            if parsed.path in {"/api/plan", "/api/terrain"}:
                terrain_only = parsed.path == "/api/terrain"
                query = query_values(parsed, terrain_only=terrain_only)
                if not REQUEST_LOCK.acquire(blocking=False):
                    self.send_json({"ok": False, "error": "Planner busy; only one request runs at a time"}, status=429)
                    return
                try:
                    payload = run_java(query, terrain_only=terrain_only)
                    self.send_json(payload)
                finally:
                    REQUEST_LOCK.release()
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
        if len(data) > MAX_RESPONSE_BYTES:
            data = b'{"ok":false,"error":"RESPONSE_BYTE_LIMIT"}'
            status = 413
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
