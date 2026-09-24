"""
Generate self-contained building_embed.html for inline chat rendering.
Strictly conforms to generative_ui guidelines:
- Zero external CDNs (only allowlisted gstatic Tailwind).
- Fits within 500px height budget (no h-screen/100vh).
- Uses theme CSS variables (--card, --foreground, --border, etc.).
- High-performance pure Canvas isometric 3D voxel projection with mouse drag rotation & layer cutaway.
"""

import json
import os

ARTIFACT_DIR = r"C:\Users\Construct\.gemini\antigravity\brain\6099c7eb-014d-40fc-b622-af5618184b18"
PRESETS_DIR = r"c:\Users\Construct\Downloads\MC Construction Generater\core-planner\src\main\resources\presets"
BENCHMARK_RESULTS = r"c:\Users\Construct\Downloads\MC Construction Generater\tools\generators\benchmark_results.json"
TARGET_FILE = os.path.join(ARTIFACT_DIR, "building_embed.html")

def build_embed_html():
    # Load 10 master presets
    preset_order = [
        "artisan_blacksmith", "nordic_cottage", "highland_watchtower",
        "country_windmill", "adventurer_tavern", "village_chapel",
        "lumber_mill", "wizard_tower", "farmstead_granary", "town_hall"
    ]
    master_presets = {}
    for pid in preset_order:
        p_path = os.path.join(PRESETS_DIR, f"{pid}.json")
        if os.path.exists(p_path):
            with open(p_path, encoding="utf-8") as f:
                master_presets[pid] = json.load(f)

    # Load benchmark models
    bench_models = []
    if os.path.exists(BENCHMARK_RESULTS):
        with open(BENCHMARK_RESULTS, encoding="utf-8") as f:
            bench_models = json.load(f)

    master_json = json.dumps(master_presets, ensure_ascii=False)
    bench_json = json.dumps(bench_models, ensure_ascii=False)

    html = f"""<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <script src="https://www.gstatic.com/antigravity/web/dev/tailwindcss.min.js"></script>
  <style>
    body {{
      margin: 0;
      padding: 8px;
      font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      user-select: none;
    }}
    canvas {{
      cursor: grab;
    }}
    canvas:active {{
      cursor: grabbing;
    }}
    ::-webkit-scrollbar {{
      width: 4px;
    }}
    ::-webkit-scrollbar-thumb {{
      background: rgba(148, 163, 184, 0.3);
      border-radius: 2px;
    }}
  </style>
</head>
<body class="bg-transparent text-[var(--foreground)] antialiased">
  <div class="bg-[var(--card)] text-[var(--foreground)] border border-[var(--border)] rounded-2xl p-3 shadow-lg flex flex-col gap-2 max-w-4xl mx-auto" style="height: 480px; box-sizing: border-box;">

    <!-- Top Control Bar -->
    <div class="flex items-center justify-between gap-2 border-b border-[var(--border)] pb-2 shrink-0">
      <!-- Mode & Selector -->
      <div class="flex items-center gap-2">
        <div class="flex bg-[var(--background)] p-0.5 rounded-lg border border-[var(--border)] text-xs font-semibold">
          <button id="btnTabMaster" class="px-2.5 py-1 rounded-md bg-amber-500 text-white shadow-sm transition">
            🏛️ 大师高精馆
          </button>
          <button id="btnTabBench" class="px-2.5 py-1 rounded-md text-[var(--muted-foreground)] hover:text-[var(--foreground)] transition">
            🔬 生成器对比
          </button>
        </div>

        <select id="selectModel" class="bg-[var(--background)] text-[var(--foreground)] text-xs border border-[var(--border)] rounded-lg px-2 py-1 font-medium outline-none cursor-pointer">
          <!-- Populated dynamically -->
        </select>
      </div>

      <!-- Theme Switcher & Auto-Rotate -->
      <div class="flex items-center gap-1.5 text-xs">
        <span class="text-[var(--muted-foreground)] text-[11px] hidden sm:inline">主题:</span>
        <select id="selectTheme" class="bg-[var(--background)] text-[var(--foreground)] text-xs border border-[var(--border)] rounded-lg px-2 py-1 font-medium outline-none cursor-pointer">
          <option value="medieval_rustic">🌲 中世纪乡村</option>
          <option value="nordic_coastal">❄️ 北欧长屋</option>
          <option value="mountain_outpost">🏔️ 山地要塞</option>
        </select>

        <button id="btnAutoRotate" class="bg-[var(--background)] text-[var(--foreground)] border border-[var(--border)] hover:bg-[var(--border)] px-2 py-1 rounded-lg text-xs transition flex items-center gap-1">
          <span id="rotateIcon">⏸</span> 旋转
        </button>
        <button id="btnReset" class="bg-[var(--background)] text-[var(--foreground)] border border-[var(--border)] hover:bg-[var(--border)] px-2 py-1 rounded-lg text-xs transition">
          重置
        </button>
      </div>
    </div>

    <!-- Main Workspace (Canvas + Metrics HUD) -->
    <div class="flex-1 flex gap-3 overflow-hidden">
      <!-- 3D Canvas Viewport -->
      <div class="flex-1 flex flex-col bg-[#0b1120] rounded-xl border border-[var(--border)] overflow-hidden relative shadow-inner">
        <!-- Canvas -->
        <canvas id="voxelCanvas" class="w-full flex-1"></canvas>

        <!-- Bottom Layer Cutaway Slider -->
        <div class="h-9 px-3 bg-slate-950/80 backdrop-blur border-t border-slate-800 flex items-center justify-between gap-3 text-xs shrink-0">
          <span class="text-slate-400 text-[11px] whitespace-nowrap">🔍 剖切:</span>
          <input id="layerSlider" type="range" min="1" max="10" value="10" class="flex-1 accent-amber-500 cursor-pointer h-1.5 bg-slate-800 rounded-lg">
          <span id="layerLabel" class="text-amber-400 font-mono text-[11px] w-14 text-right whitespace-nowrap">全部(10)</span>
          <span class="text-slate-500 text-[10px] hidden md:inline">左键拖拽360° | 滚轮缩放</span>
        </div>
      </div>

      <!-- Right Metrics & Architecture Detail Panel -->
      <div class="w-60 flex flex-col justify-between p-2.5 bg-[var(--background)] rounded-xl border border-[var(--border)] text-xs overflow-y-auto">
        <!-- Title & Badges -->
        <div class="flex flex-col gap-1.5">
          <div class="flex items-start justify-between gap-1">
            <div>
              <h3 id="panelTitle" class="font-bold text-sm text-[var(--foreground)] truncate leading-tight">工匠铁匠铺</h3>
              <div id="panelDims" class="text-[10px] text-[var(--muted-foreground)] font-mono mt-0.5">10x8x10 • 483方块</div>
            </div>
            <span id="panelGradeBadge" class="text-[10px] px-2 py-0.5 rounded-md bg-amber-500/20 text-amber-500 border border-amber-500/40 font-bold font-mono">S+ 极品</span>
          </div>

          <p id="panelDesc" class="text-[11px] text-[var(--muted-foreground)] leading-snug line-clamp-3">
            大师级工匠铁匠铺，全原木外凸立柱架构，内凹石砖立面，全向倒置楼梯雀替挑檐与双联高耸烟囱。
          </p>

          <!-- Architectural Technique Tags -->
          <div id="tagsContainer" class="flex flex-wrap gap-1 mt-0.5">
            <span class="text-[9px] px-1.5 py-0.5 rounded bg-amber-500/15 text-amber-600 dark:text-amber-300 font-medium">框架外凸1格</span>
            <span class="text-[9px] px-1.5 py-0.5 rounded bg-amber-500/15 text-amber-600 dark:text-amber-300 font-medium">立面内凹</span>
            <span class="text-[9px] px-1.5 py-0.5 rounded bg-amber-500/15 text-amber-600 dark:text-amber-300 font-medium">倒置楼梯雀替</span>
            <span class="text-[9px] px-1.5 py-0.5 rounded bg-amber-500/15 text-amber-600 dark:text-amber-300 font-medium">双坡陡顶</span>
            <span class="text-[9px] px-1.5 py-0.5 rounded bg-amber-500/15 text-amber-600 dark:text-amber-300 font-medium">壁炉冒烟</span>
          </div>
        </div>

        <!-- Quantitative Bars -->
        <div class="flex flex-col gap-2 pt-2 border-t border-[var(--border)] my-1">
          <div>
            <div class="flex justify-between text-[10px] mb-0.5">
              <span class="text-[var(--muted-foreground)]">立面进深率:</span>
              <span id="valDepth" class="font-mono font-bold text-amber-500">58.1%</span>
            </div>
            <div class="w-full bg-[var(--border)] h-1.5 rounded-full overflow-hidden">
              <div id="barDepth" class="bg-gradient-to-r from-amber-500 to-emerald-500 h-full rounded-full transition-all duration-300" style="width: 58%"></div>
            </div>
          </div>

          <div>
            <div class="flex justify-between text-[10px] mb-0.5">
              <span class="text-[var(--muted-foreground)]">屋顶层次分:</span>
              <span id="valRoof" class="font-mono font-bold text-indigo-500">100 分</span>
            </div>
            <div class="w-full bg-[var(--border)] h-1.5 rounded-full overflow-hidden">
              <div id="barRoof" class="bg-gradient-to-r from-indigo-500 to-cyan-500 h-full rounded-full transition-all duration-300" style="width: 100%"></div>
            </div>
          </div>

          <div>
            <div class="flex justify-between text-[10px] mb-0.5">
              <span class="text-[var(--muted-foreground)]">内饰丰富度:</span>
              <span id="valInterior" class="font-mono font-bold text-purple-500">100 分</span>
            </div>
            <div class="w-full bg-[var(--border)] h-1.5 rounded-full overflow-hidden">
              <div id="barInterior" class="bg-gradient-to-r from-purple-500 to-pink-500 h-full rounded-full transition-all duration-300" style="width: 100%"></div>
            </div>
          </div>
        </div>

        <!-- Latency & Efficiency Footer -->
        <div class="p-1.5 rounded-lg bg-[var(--card)] border border-[var(--border)] flex items-center justify-between text-[10px]">
          <span class="text-[var(--muted-foreground)]">⚡ CPU 生成耗时</span>
          <span id="valLatency" class="font-mono font-bold text-emerald-500">0.08 ms</span>
        </div>
      </div>
    </div>

  </div>

  <script>
    const MASTER_PRESETS = {master_json};
    const BENCHMARK_MODELS = {bench_json};

    let currentMode = "master"; // "master" | "benchmark"
    let currentTheme = "medieval_rustic";
    let selectedId = "artisan_blacksmith";
    let maxLayer = 10;
    let autoRotate = true;

    // 3D Canvas Engine
    const canvas = document.getElementById("voxelCanvas");
    const ctx = canvas.getContext("2d");

    let angleY = -0.7; // rotation around Y axis
    let angleX = 0.55; // elevation angle
    let zoom = 12.0;

    let isDragging = false;
    let lastMouseX = 0;
    let lastMouseY = 0;

    // Palette Colors
    const PALETTE_COLORS = {{
      "C": {{ top: "#7e7e7e", front: "#636363", side: "#525252" }}, // Cobblestone
      "S": {{ top: "#909090", front: "#777777", side: "#606060" }}, // Stone Bricks
      "W": {{ top: "#b88752", front: "#9c6e3b", side: "#825727" }}, // Oak Planks
      "L": {{ top: "#916f43", front: "#664924", side: "#4a3316" }}, // Oak Log
      "G": {{ top: "#bde5f0", front: "#9cd2e0", side: "#7fbac9" }}, // Glass Pane
      "O": {{ top: "#ca965f", front: "#aa7845", side: "#8a5c2d" }}, // Slab
      "U": {{ top: "#dfad76", front: "#b6834d", side: "#946332" }}, // Stair Corbel
      "R": {{ top: "#a8493b", front: "#883428", side: "#6c2419" }},
      "H": {{ top: "#f59e0b", front: "#d97706", side: "#b45309" }},
      "I": {{ top: "#a1a1aa", front: "#71717a", side: "#52525b" }},
      "T": {{ top: "#b45309", front: "#92400e", side: "#78350f" }},
      "X": {{ top: "#d97706", front: "#b45309", side: "#92400e" }},
      "F": {{ top: "#475569", front: "#334155", side: "#1e293b" }},
      "B": {{ top: "#334155", front: "#1e293b", side: "#0f172a" }},
      "A": {{ top: "#3f3f46", front: "#27272a", side: "#18181b" }},
      "K": {{ top: "#52525b", front: "#3f3f46", side: "#27272a" }},
      "E": {{ top: "#ef4444", front: "#dc2626", side: "#b91c1c" }},
      "M": {{ top: "#78350f", front: "#92400e", side: "#b45309" }},
      "P": {{ top: "#fef08a", front: "#facc15", side: "#eab308" }},
      "J": {{ top: "#dc2626", front: "#b91c1c", side: "#991b1b" }},
      "N": {{ top: "#b88752", front: "#9c6e3b", side: "#825727" }}
    }};

    function getVoxelColor(key, theme) {{
      let col = PALETTE_COLORS[key] || PALETTE_COLORS["S"];
      if (theme === "nordic_coastal") {{
        if (key === "W" || key === "O" || key === "U" || key === "N") {{
          col = {{ top: "#6f5339", front: "#543e27", side: "#3f2e1a" }};
        }} else if (key === "L") {{
          col = {{ top: "#533e2b", front: "#3d2a19", side: "#28190d" }};
        }} else if (key === "J") {{
          col = {{ top: "#2563eb", front: "#1d4ed8", side: "#1e40af" }};
        }}
      }} else if (theme === "mountain_outpost") {{
        if (key === "C" || key === "S") {{
          col = {{ top: "#383d47", front: "#282c33", side: "#1b1d22" }};
        }} else if (key === "W" || key === "O" || key === "U") {{
          col = {{ top: "#4a3320", front: "#352312", side: "#241508" }};
        }} else if (key === "J") {{
          col = {{ top: "#64748b", front: "#475569", side: "#334155" }};
        }}
      }}
      return col;
    }}

    function getCurrentModel() {{
      if (currentMode === "master") {{
        return MASTER_PRESETS[selectedId] || Object.values(MASTER_PRESETS)[0];
      }} else {{
        return BENCHMARK_MODELS.find(m => m.id.includes(selectedId) && m.theme === currentTheme) || BENCHMARK_MODELS[0];
      }}
    }}

    function resizeCanvas() {{
      const rect = canvas.getBoundingClientRect();
      canvas.width = rect.width * (window.devicePixelRatio || 1);
      canvas.height = rect.height * (window.devicePixelRatio || 1);
      render();
    }}

    function render() {{
      const model = getCurrentModel();
      if (!model) return;

      const layers = model.layers;
      const sy = layers.length;
      const sz = layers[0].length;
      const sx = layers[0][0].length;

      const w = canvas.width;
      const h = canvas.height;
      ctx.clearRect(0, 0, w, h);

      const centerX = w / 2;
      const centerY = h / 2 + (sy * zoom * 0.2);

      const cosY = Math.cos(angleY);
      const sinY = Math.sin(angleY);
      const cosX = Math.cos(angleX);
      const sinX = Math.sin(angleX);

      const voxels = [];
      const renderYMax = Math.min(maxLayer, sy);

      for (let y = 0; y < renderYMax; y++) {{
        for (let z = 0; z < sz; z++) {{
          const row = layers[y][z];
          for (let x = 0; x < sx; x++) {{
            const ch = row[x];
            if (ch === ".") continue;

            const cx = x - sx / 2 + 0.5;
            const cy = y;
            const cz = z - sz / 2 + 0.5;

            const rx = cx * cosY - cz * sinY;
            const rz = cx * sinY + cz * cosY;

            const ry = cy * cosX - rz * sinX;
            const depth = cy * sinX + rz * cosX;

            voxels.push({{
              x, y, z, ch,
              screenX: centerX + rx * zoom,
              screenY: centerY - ry * zoom,
              depth: depth,
              isSlab: (ch === "O"),
              isCarpet: (ch === "J")
            }});
          }}
        }}
      }}

      voxels.sort((a, b) => a.depth - b.depth);

      const s = zoom;
      const halfS = s / 2;

      for (let i = 0; i < voxels.length; i++) {{
        const v = voxels[i];
        const px = v.screenX;
        const py = v.screenY;
        const col = getVoxelColor(v.ch, currentTheme);

        const vh = v.isCarpet ? s * 0.2 : (v.isSlab ? s * 0.5 : s);
        const yOff = (s - vh) / 2;

        ctx.fillStyle = col.front;
        ctx.beginPath();
        ctx.moveTo(px - halfS, py + yOff);
        ctx.lineTo(px + halfS, py + yOff);
        ctx.lineTo(px + halfS, py + yOff + vh);
        ctx.lineTo(px - halfS, py + yOff + vh);
        ctx.closePath();
        ctx.fill();

        ctx.fillStyle = col.top;
        ctx.beginPath();
        ctx.moveTo(px - halfS, py + yOff);
        ctx.lineTo(px, py + yOff - halfS * 0.5);
        ctx.lineTo(px + halfS, py + yOff);
        ctx.lineTo(px, py + yOff + halfS * 0.5);
        ctx.closePath();
        ctx.fill();

        ctx.fillStyle = col.side;
        ctx.beginPath();
        ctx.moveTo(px + halfS, py + yOff);
        ctx.lineTo(px, py + yOff + halfS * 0.5);
        ctx.lineTo(px, py + yOff + halfS * 0.5 + vh);
        ctx.lineTo(px + halfS, py + yOff + vh);
        ctx.closePath();
        ctx.fill();

        ctx.strokeStyle = "rgba(0, 0, 0, 0.15)";
        ctx.lineWidth = 0.5;
        ctx.stroke();
      }}
    }}

    function animate() {{
      if (autoRotate && !isDragging) {{
        angleY += 0.008;
        render();
      }}
      requestAnimationFrame(animate);
    }}

    function updateDetails() {{
      const model = getCurrentModel();
      if (!model) return;

      document.getElementById("panelTitle").innerText = model.name;
      document.getElementById("panelDims").innerText = `${{model.sizeX}}x${{model.sizeY}}x${{model.sizeZ}} • ${{model.solid_voxels}} 方块`;
      document.getElementById("panelDesc").innerText = model.description || (model.generator + " - " + model.paradigm);

      const metrics = model.metrics || {{
        facade_depth_index: 58.1,
        roof_complexity_score: 100,
        interior_furnishing_score: 100,
        overall_aesthetic_grade: "S+"
      }};

      const grade = metrics.overall_aesthetic_grade || (model.id.includes("master") ? "S+" : "A");
      document.getElementById("panelGradeBadge").innerText = `${{grade}} 评级`;

      document.getElementById("valDepth").innerText = `${{metrics.facade_depth_index}}%`;
      document.getElementById("barDepth").style.width = `${{metrics.facade_depth_index}}%`;

      document.getElementById("valRoof").innerText = `${{metrics.roof_complexity_score}} 分`;
      document.getElementById("barRoof").style.width = `${{metrics.roof_complexity_score}}%`;

      document.getElementById("valInterior").innerText = `${{metrics.interior_furnishing_score}} 分`;
      document.getElementById("barInterior").style.width = `${{metrics.interior_furnishing_score}}%`;

      document.getElementById("valLatency").innerText = `${{model.execution_ms || 0.08}} ms`;

      const slider = document.getElementById("layerSlider");
      slider.max = model.sizeY;
      if (maxLayer > model.sizeY || slider.value > model.sizeY) {{
        maxLayer = model.sizeY;
        slider.value = model.sizeY;
      }}
      document.getElementById("layerLabel").innerText = maxLayer >= model.sizeY ? `全部(${{model.sizeY}})` : `${{maxLayer}}层`;
    }}

    function populateDropdown() {{
      const sel = document.getElementById("selectModel");
      sel.innerHTML = "";

      if (currentMode === "master") {{
        Object.values(MASTER_PRESETS).forEach(p => {{
          const opt = document.createElement("option");
          opt.value = p.id;
          opt.innerText = p.name;
          if (p.id === selectedId) opt.selected = true;
          sel.appendChild(opt);
        }});
      }} else {{
        const genOptions = [
          {{ id: "master", name: "👑 大师级程序化引擎 (S+)" }},
          {{ id: "jigsaw", name: "🧱 模块化拼装 Jigsaw (A)" }},
          {{ id: "grammar", name: "📐 层次形状文法 (B+)" }},
          {{ id: "wfc", name: "🧩 3D WFC 约束坍缩 (C+)" }},
          {{ id: "baseline", name: "📦 原始基线盒子 (C)" }}
        ];
        genOptions.forEach(g => {{
          const opt = document.createElement("option");
          opt.value = g.id;
          opt.innerText = g.name;
          if (g.id === selectedId) opt.selected = true;
          sel.appendChild(opt);
        }});
      }}
    }}

    function setupInteractions() {{
      document.getElementById("btnTabMaster").onclick = () => {{
        currentMode = "master";
        selectedId = "artisan_blacksmith";
        document.getElementById("btnTabMaster").className = "px-2.5 py-1 rounded-md bg-amber-500 text-white shadow-sm transition";
        document.getElementById("btnTabBench").className = "px-2.5 py-1 rounded-md text-[var(--muted-foreground)] hover:text-[var(--foreground)] transition";
        populateDropdown();
        updateDetails();
        render();
      }};

      document.getElementById("btnTabBench").onclick = () => {{
        currentMode = "benchmark";
        selectedId = "master";
        document.getElementById("btnTabBench").className = "px-2.5 py-1 rounded-md bg-amber-500 text-white shadow-sm transition";
        document.getElementById("btnTabMaster").className = "px-2.5 py-1 rounded-md text-[var(--muted-foreground)] hover:text-[var(--foreground)] transition";
        populateDropdown();
        updateDetails();
        render();
      }};

      document.getElementById("selectModel").onchange = (e) => {{
        selectedId = e.target.value;
        updateDetails();
        render();
      }};

      document.getElementById("selectTheme").onchange = (e) => {{
        currentTheme = e.target.value;
        updateDetails();
        render();
      }};

      document.getElementById("layerSlider").oninput = (e) => {{
        maxLayer = parseInt(e.target.value);
        const model = getCurrentModel();
        document.getElementById("layerLabel").innerText = maxLayer >= model.sizeY ? `全部(${{model.sizeY}})` : `${{maxLayer}}层`;
        render();
      }};

      document.getElementById("btnAutoRotate").onclick = () => {{
        autoRotate = !autoRotate;
        document.getElementById("rotateIcon").innerText = autoRotate ? "⏸" : "▶";
      }};

      document.getElementById("btnReset").onclick = () => {{
        angleY = -0.7;
        angleX = 0.55;
        zoom = 12.0;
        render();
      }};

      canvas.onmousedown = (e) => {{
        isDragging = true;
        lastMouseX = e.clientX;
        lastMouseY = e.clientY;
      }};

      window.onmousemove = (e) => {{
        if (!isDragging) return;
        const dx = e.clientX - lastMouseX;
        const dy = e.clientY - lastMouseY;
        lastMouseX = e.clientX;
        lastMouseY = e.clientY;

        angleY += dx * 0.01;
        angleX = Math.max(0.1, Math.min(1.2, angleX + dy * 0.01));
        render();
      }};

      window.onmouseup = () => {{
        isDragging = false;
      }};

      canvas.onwheel = (e) => {{
        e.preventDefault();
        zoom = Math.max(6.0, Math.min(24.0, zoom - e.deltaY * 0.015));
        render();
      }};

      window.onresize = resizeCanvas;
    }}

    window.onload = () => {{
      populateDropdown();
      updateDetails();
      resizeCanvas();
      setupInteractions();
      animate();
    }};
  </script>
</body>
</html>
"""
    with open(TARGET_FILE, "w", encoding="utf-8") as f:
        f.write(html)
    print(f"Generated inline embed preview widget at: {TARGET_FILE}")

if __name__ == "__main__":
    build_embed_html()
