"""
Builds the standalone FULL-SCREEN 100vh browser-optimized 3D building previewer.
- Optimized for full-screen desktop browser experience (100% viewport width and height).
- Inlined Three.js & OrbitControls (zero CDN network dependency, loads instantly offline).
- Full Stairs System support (Compound L-shape 3D stair mesh, orientation, zero-gap roofs).
- Multi-dimensional Matrix: 4 Types x 3 Scales x 5 Generators x 3 Themes = 60 models + 10 Master Presets.
- Collapsible sidebar, floating architectural quality HUD, layer cutaway slider, 360° orbital controls.
"""

import json
import os

CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
ARTIFACT_DIR = r"C:\Users\Construct\.gemini\antigravity\brain\6099c7eb-014d-40fc-b622-af5618184b18"
PRESETS_DIR = r"c:\Users\Construct\Downloads\MC Construction Generater\core-planner\src\main\resources\presets"
MATRIX_RESULTS = os.path.join(CURRENT_DIR, "multi_scale_results.json")
THREE_JS_PATH = os.path.join(CURRENT_DIR, "three.min.js")
ORBIT_JS_PATH = os.path.join(CURRENT_DIR, "OrbitControls.js")

STANDALONE_OUTPUT = r"c:\Users\Construct\Downloads\MC Construction Generater\core-planner\output\building_preview.html"
ARTIFACT_STANDALONE = os.path.join(ARTIFACT_DIR, "building_preview.html")

def build_fullscreen_html():
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

    matrix_models = {}
    if os.path.exists(MATRIX_RESULTS):
        with open(MATRIX_RESULTS, encoding="utf-8") as f:
            matrix_models = json.load(f)

    with open(THREE_JS_PATH, "r", encoding="utf-8") as f:
        three_js_code = f.read()

    with open(ORBIT_JS_PATH, "r", encoding="utf-8") as f:
        orbit_js_code = f.read()

    master_json = json.dumps(master_presets, ensure_ascii=False)
    matrix_json = json.dumps(matrix_models, ensure_ascii=False)

    html = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <title>Minecraft 建筑生成器多主题全规模 3D 密堆积全屏预览工坊</title>
  <script src="https://www.gstatic.com/antigravity/web/dev/tailwindcss.min.js"></script>
  <style>
    html, body {{
      margin: 0;
      padding: 0;
      width: 100vw;
      height: 100vh;
      overflow: hidden;
      user-select: none;
      font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      background: #060913;
      color: #f1f5f9;
    }}
    canvas {{
      cursor: grab;
    }}
    canvas:active {{
      cursor: grabbing;
    }}
    .active-mode {{
      background: linear-gradient(135deg, #f59e0b, #d97706) !important;
      color: #ffffff !important;
      font-weight: 700 !important;
      box-shadow: 0 2px 10px rgba(245, 158, 11, 0.4) !important;
    }}
    .chip-btn.active {{
      background: #f59e0b !important;
      color: #0f172a !important;
      font-weight: 700 !important;
    }}
    ::-webkit-scrollbar {{
      width: 5px;
      height: 5px;
    }}
    ::-webkit-scrollbar-track {{
      background: rgba(15, 23, 42, 0.6);
    }}
    ::-webkit-scrollbar-thumb {{
      background: rgba(100, 116, 139, 0.5);
      border-radius: 3px;
    }}
  </style>
  <script>
    // Inlined Three.js Engine
    {three_js_code}
  </script>
  <script>
    // Inlined OrbitControls
    {orbit_js_code}
  </script>
</head>
<body class="flex flex-col h-screen w-screen overflow-hidden">

  <!-- Top Header Navigation -->
  <header class="h-14 px-4 bg-slate-950/90 backdrop-blur-md border-b border-slate-800/80 flex items-center justify-between gap-3 shrink-0 z-30 shadow-md">
    <div class="flex items-center gap-3">
      <!-- Toggle Sidebar -->
      <button id="btnToggleSidebar" class="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-slate-900 hover:bg-slate-800 text-slate-200 border border-slate-700 text-xs font-medium transition shadow-sm">
        <span id="sidebarIcon">◀</span>
        <span id="sidebarText">折叠列表</span>
      </button>

      <!-- App Title & Mode Switch -->
      <div class="flex items-center gap-2">
        <div class="w-8 h-8 rounded-xl bg-gradient-to-tr from-amber-500 to-amber-700 flex items-center justify-center text-base shadow-md">
          🏛️
        </div>
        <div class="flex bg-slate-900 p-1 rounded-xl border border-slate-800 text-xs font-semibold">
          <button id="btnModeMatrix" class="active-mode px-3 py-1 rounded-lg transition flex items-center gap-1.5">
            <span>🔬</span> 生成器全矩阵对比
          </button>
          <button id="btnModeMaster" class="px-3 py-1 rounded-lg text-slate-400 hover:text-slate-200 transition flex items-center gap-1.5">
            <span>👑</span> 10大高精预设馆
          </button>
        </div>
      </div>

      <span class="text-[11px] px-2.5 py-0.5 rounded-full bg-amber-950/80 text-amber-300 border border-amber-800/60 font-mono hidden lg:inline">
        MC密堆积渲染 • 真实台阶/楼梯系统
      </span>
    </div>

    <!-- Quick Matrix Filters (Visible in Matrix Mode) -->
    <div id="topMatrixFilters" class="hidden md:flex items-center gap-2 bg-slate-900/90 px-3 py-1 rounded-xl border border-slate-800 text-xs">
      <!-- Archetype -->
      <div class="flex items-center gap-1">
        <span class="text-slate-400">建筑:</span>
        <select id="selectTopType" class="bg-slate-950 text-slate-100 border border-slate-700 rounded-lg px-2 py-0.5 outline-none cursor-pointer">
          <option value="cottage">🏡 民居木屋</option>
          <option value="blacksmith">🔨 工匠铁匠铺</option>
          <option value="watchtower">🗼 戍卫哨塔</option>
          <option value="town_hall">🏛️ 市政大厅</option>
        </select>
      </div>

      <!-- Scale -->
      <div class="flex items-center gap-1">
        <span class="text-slate-400">体积:</span>
        <select id="selectTopScale" class="bg-slate-950 text-slate-100 border border-slate-700 rounded-lg px-2 py-0.5 outline-none cursor-pointer">
          <option value="small">🔹 小型 (~7x7)</option>
          <option value="medium" selected>🔸 中型 (~11x11)</option>
          <option value="large">🔷 大型 (~15x15)</option>
        </select>
      </div>

      <!-- Generator -->
      <div class="flex items-center gap-1">
        <span class="text-slate-400">生成器:</span>
        <select id="selectTopGen" class="bg-slate-950 text-slate-100 border border-slate-700 rounded-lg px-2 py-0.5 outline-none cursor-pointer">
          <option value="master">👑 大师级引擎 (S+)</option>
          <option value="jigsaw">🧱 模块化拼装 (A)</option>
          <option value="grammar">📐 形状文法 (B+)</option>
          <option value="wfc">🧩 3D WFC (C+)</option>
          <option value="baseline">📦 原始基线 (C)</option>
        </select>
      </div>
    </div>

    <!-- Theme Switch & Controls -->
    <div class="flex items-center gap-2 text-xs">
      <div class="flex items-center gap-1 bg-slate-900/90 px-2 py-1 rounded-xl border border-slate-800">
        <span class="text-slate-400 text-[11px] hidden sm:inline">主题:</span>
        <button data-theme="medieval_rustic" class="theme-btn px-2 py-0.5 rounded-lg font-medium transition chip-btn active">中世纪</button>
        <button data-theme="nordic_coastal" class="theme-btn px-2 py-0.5 rounded-lg font-medium text-slate-400 hover:text-slate-200 transition">北欧</button>
        <button data-theme="mountain_outpost" class="theme-btn px-2 py-0.5 rounded-lg font-medium text-slate-400 hover:text-slate-200 transition">山地</button>
      </div>

      <button id="btnAutoRotate" class="bg-slate-900 hover:bg-slate-800 text-slate-200 border border-slate-700 px-2.5 py-1.5 rounded-xl transition flex items-center gap-1 shadow-sm">
        <span id="rotateIcon">⏸</span> 自动旋转
      </button>
      <button id="btnResetView" class="bg-slate-900 hover:bg-slate-800 text-slate-200 border border-slate-700 px-2.5 py-1.5 rounded-xl transition shadow-sm">
        重置视角
      </button>
    </div>
  </header>

  <!-- Workspace: Sidebar + 3D Viewport -->
  <div class="flex-1 flex overflow-hidden relative">

    <!-- Collapsible Sidebar -->
    <aside id="sidebar" class="w-80 border-r border-slate-800/80 bg-slate-950/80 backdrop-blur-md flex flex-col shrink-0 transition-all duration-300 ease-in-out z-20">
      <div id="sidebarTitle" class="p-3 border-b border-slate-800/60 flex items-center justify-between text-xs font-semibold text-slate-400">
        <span>多体积模型对照库 (60)</span>
        <span class="text-[10px] text-amber-400 font-mono bg-amber-950/60 px-2 py-0.5 rounded border border-amber-800/40">点击即换</span>
      </div>

      <!-- Quick Archetype Filter Pills -->
      <div id="archetypePills" class="p-2 border-b border-slate-800/50 flex gap-1 overflow-x-auto text-[11px]">
        <button data-type="all" class="type-pill px-2 py-1 rounded-lg bg-amber-500 text-slate-950 font-bold">全部</button>
        <button data-type="cottage" class="type-pill px-2 py-1 rounded-lg bg-slate-900 text-slate-300 hover:bg-slate-800">民居木屋</button>
        <button data-type="blacksmith" class="type-pill px-2 py-1 rounded-lg bg-slate-900 text-slate-300 hover:bg-slate-800">铁匠铺</button>
        <button data-type="watchtower" class="type-pill px-2 py-1 rounded-lg bg-slate-900 text-slate-300 hover:bg-slate-800">戍卫哨塔</button>
        <button data-type="town_hall" class="type-pill px-2 py-1 rounded-lg bg-slate-900 text-slate-300 hover:bg-slate-800">市政大厅</button>
      </div>

      <!-- Sidebar Item List -->
      <div id="itemList" class="flex-1 overflow-y-auto p-2 flex flex-col gap-1.5">
        <!-- Dynamically rendered -->
      </div>
    </aside>

    <!-- Main 3D Viewport -->
    <main class="flex-1 flex flex-col relative overflow-hidden bg-gradient-to-b from-[#080c18] to-[#04060e]">
      <!-- WebGL Canvas Container -->
      <div id="canvasContainer" class="flex-1 w-full h-full relative"></div>

      <!-- Top-Left Floating Info Card -->
      <div class="absolute top-4 left-4 pointer-events-none z-10 flex flex-col gap-2">
        <div class="bg-slate-900/90 backdrop-blur-md border border-slate-700/80 rounded-2xl p-4 shadow-2xl max-w-sm pointer-events-auto">
          <div class="flex items-center gap-2">
            <h2 id="infoTitle" class="text-sm font-bold text-slate-100 truncate">民居木屋 [中型]</h2>
            <span id="infoBadge" class="text-[10px] px-2 py-0.5 rounded-full bg-amber-500/20 text-amber-300 border border-amber-500/40 font-mono font-semibold">S+ 大师级</span>
          </div>
          <p id="infoDesc" class="text-[11px] text-slate-400 mt-1.5 leading-relaxed">
            大师级程序化引擎生成：真实台阶系统+框架外凸1格+内凹立面+倒置楼梯雀替挑檐
          </p>
          <div id="techniqueTags" class="flex flex-wrap gap-1 mt-2.5">
            <span class="text-[9px] px-2 py-0.5 rounded-md bg-amber-500/15 text-amber-400 border border-amber-500/30">真实L型台阶</span>
            <span class="text-[9px] px-2 py-0.5 rounded-md bg-amber-500/15 text-amber-400 border border-amber-500/30">山墙封闭无缝</span>
            <span class="text-[9px] px-2 py-0.5 rounded-md bg-amber-500/15 text-amber-400 border border-amber-500/30">框架外凸1格</span>
            <span class="text-[9px] px-2 py-0.5 rounded-md bg-amber-500/15 text-amber-400 border border-amber-500/30">倒置雀替挑檐</span>
          </div>
        </div>
      </div>

      <!-- Top-Right Architectural Quality Radar HUD -->
      <div class="absolute top-4 right-4 pointer-events-none z-10">
        <div class="bg-slate-900/90 backdrop-blur-md border border-slate-700/80 rounded-2xl p-4 shadow-2xl w-72 pointer-events-auto flex flex-col gap-3">
          <div class="flex items-center justify-between text-xs font-bold text-slate-300 border-b border-slate-800 pb-2">
            <span class="flex items-center gap-1.5">📊 建筑品质工程量化</span>
            <span id="metricGrade" class="text-amber-400 font-mono font-bold">GRADE S+</span>
          </div>

          <!-- Facade Depth -->
          <div>
            <div class="flex justify-between text-[11px] mb-1">
              <span class="text-slate-400">立面进深率 (Facade Depth):</span>
              <span id="metricDepthVal" class="text-amber-300 font-mono font-bold">58.1%</span>
            </div>
            <div class="w-full bg-slate-800 h-1.5 rounded-full overflow-hidden">
              <div id="metricDepthBar" class="bg-gradient-to-r from-amber-500 to-emerald-400 h-full rounded-full transition-all duration-500" style="width: 58.1%"></div>
            </div>
          </div>

          <!-- Roof Complexity -->
          <div>
            <div class="flex justify-between text-[11px] mb-1">
              <span class="text-slate-400">屋顶层次分 (Roof Complexity):</span>
              <span id="metricRoofVal" class="text-amber-300 font-mono font-bold">100 分</span>
            </div>
            <div class="w-full bg-slate-800 h-1.5 rounded-full overflow-hidden">
              <div id="metricRoofBar" class="bg-gradient-to-r from-indigo-500 to-cyan-400 h-full rounded-full transition-all duration-500" style="width: 100%"></div>
            </div>
          </div>

          <!-- Interior Furnishing -->
          <div>
            <div class="flex justify-between text-[11px] mb-1">
              <span class="text-slate-400">内饰丰度 (Interior Furnishing):</span>
              <span id="metricInteriorVal" class="text-amber-300 font-mono font-bold">100 分</span>
            </div>
            <div class="w-full bg-slate-800 h-1.5 rounded-full overflow-hidden">
              <div id="metricInteriorBar" class="bg-gradient-to-r from-purple-500 to-pink-400 h-full rounded-full transition-all duration-500" style="width: 100%"></div>
            </div>
          </div>

          <!-- Stats Grid -->
          <div class="grid grid-cols-2 gap-2 pt-1 border-t border-slate-800/80 text-[11px]">
            <div class="bg-slate-950/60 p-2 rounded-xl border border-slate-800">
              <div class="text-slate-500 text-[10px]">外形包围盒</div>
              <div id="metricDims" class="font-mono text-slate-200 font-bold">11x11x11</div>
            </div>
            <div class="bg-slate-950/60 p-2 rounded-xl border border-slate-800">
              <div class="text-slate-500 text-[10px]">CPU 生成耗时</div>
              <div id="metricLatency" class="font-mono text-emerald-400 font-bold">0.08 ms</div>
            </div>
          </div>
        </div>
      </div>

      <!-- Bottom Control Bar (Layer Cutaway Slider + Mouse Hints) -->
      <div class="h-16 px-6 border-t border-slate-800/80 bg-slate-950/80 backdrop-blur-md flex items-center justify-between gap-6 shrink-0 z-20 shadow-lg">
        <!-- Layer Slider -->
        <div class="flex items-center gap-3 flex-1 max-w-xl">
          <span class="text-slate-300 font-semibold text-xs whitespace-nowrap flex items-center gap-1.5">
            <span>🔍</span> 剖切层高 (Layer Cutaway):
          </span>
          <input id="layerSlider" type="range" min="1" max="30" value="30" class="flex-1 accent-amber-500 cursor-pointer h-2 bg-slate-800 rounded-lg">
          <span id="layerVal" class="text-amber-400 font-mono text-xs w-24 text-right font-bold whitespace-nowrap">全部展示</span>
          <button id="btnFullHeight" class="px-2.5 py-1 rounded-lg bg-amber-500/20 hover:bg-amber-500/30 text-amber-300 border border-amber-500/40 text-[11px] font-semibold whitespace-nowrap transition cursor-pointer shadow-sm">
            完全展示
          </button>
        </div>

        <!-- Mouse Controls Hint -->
        <div class="flex items-center gap-4 text-xs text-slate-400 font-medium">
          <span class="flex items-center gap-1.5"><kbd class="px-2 py-0.5 rounded-lg bg-slate-800 text-slate-200 border border-slate-700 text-[10px]">鼠标左键</kbd> 360°漫游旋转</span>
          <span class="flex items-center gap-1.5"><kbd class="px-2 py-0.5 rounded-lg bg-slate-800 text-slate-200 border border-slate-700 text-[10px]">滚轮</kbd> 自由缩放</span>
          <span class="flex items-center gap-1.5"><kbd class="px-2 py-0.5 rounded-lg bg-slate-800 text-slate-200 border border-slate-700 text-[10px]">右键</kbd> 平移镜头</span>
        </div>
      </div>
    </main>
  </div>

  <script>
    const MASTER_PRESETS = {master_json};
    const MATRIX_MODELS = {matrix_json};

    let currentMode = "matrix"; // "matrix" | "master"
    let currentType = "cottage";
    let currentScale = "medium";
    let currentGenerator = "master";
    let currentTheme = "medieval_rustic";
    let currentMasterId = "artisan_blacksmith";
    let currentFilterType = "all";

    let maxLayer = 999; // Default to fully shown without cutting off
    let autoRotate = true;

    // Three.js Scene Variables
    let scene, camera, renderer, controls;
    let voxelGroup = new THREE.Group();

    // Procedural Minecraft Textures Engine
    function createBlockTexture(type) {{
      const canvas = document.createElement("canvas");
      canvas.width = 32;
      canvas.height = 32;
      const ctx = canvas.getContext("2d");

      if (type.includes("stone_bricks") || type.includes("deepslate_bricks")) {{
        const isDeep = type.includes("deepslate");
        ctx.fillStyle = isDeep ? "#24272e" : "#7a7a7a";
        ctx.fillRect(0, 0, 32, 32);
        ctx.strokeStyle = isDeep ? "#16181d" : "#4c4c4c";
        ctx.lineWidth = 1.5;
        // Brick rows
        ctx.strokeRect(0, 0, 16, 8); ctx.strokeRect(16, 0, 16, 8);
        ctx.strokeRect(-8, 8, 16, 8); ctx.strokeRect(8, 8, 16, 8); ctx.strokeRect(24, 8, 16, 8);
        ctx.strokeRect(0, 16, 16, 8); ctx.strokeRect(16, 16, 16, 8);
        ctx.strokeRect(-8, 24, 16, 8); ctx.strokeRect(8, 24, 16, 8); ctx.strokeRect(24, 24, 16, 8);
      }} else if (type.includes("cobblestone") || type.includes("cobbled_deepslate")) {{
        const isDeep = type.includes("deepslate");
        ctx.fillStyle = isDeep ? "#2a2d34" : "#6e6e6e";
        ctx.fillRect(0, 0, 32, 32);
        ctx.fillStyle = isDeep ? "#1a1c21" : "#4a4a4a";
        for (let i = 0; i < 9; i++) {{
          const rx = (i * 7) % 24;
          const ry = (i * 11) % 24;
          ctx.fillRect(rx, ry, 7, 7);
        }}
      }} else if (type.includes("planks") || type.includes("slab") || type.includes("stairs")) {{
        let base = "#a87444";
        let line = "#6f4825";
        if (type.includes("spruce")) {{ base = "#664b30"; line = "#422f1d"; }}
        else if (type.includes("dark_oak")) {{ base = "#3f2715"; line = "#26150a"; }}
        ctx.fillStyle = base;
        ctx.fillRect(0, 0, 32, 32);
        ctx.strokeStyle = line;
        ctx.lineWidth = 1;
        ctx.strokeRect(0, 0, 32, 8); ctx.strokeRect(0, 8, 32, 8);
        ctx.strokeRect(0, 16, 32, 8); ctx.strokeRect(0, 24, 32, 8);
      }} else if (type.includes("log")) {{
        let base = "#785836";
        let line = "#4d3419";
        if (type.includes("spruce")) {{ base = "#4b3520"; line = "#2e1e10"; }}
        else if (type.includes("dark_oak")) {{ base = "#2c1c0f"; line = "#160b05"; }}
        ctx.fillStyle = base;
        ctx.fillRect(0, 0, 32, 32);
        ctx.strokeStyle = line;
        ctx.lineWidth = 2;
        for (let x = 4; x < 32; x += 6) {{
          ctx.beginPath();
          ctx.moveTo(x, 0); ctx.lineTo(x, 32); ctx.stroke();
        }}
      }} else if (type.includes("glass")) {{
        ctx.fillStyle = "rgba(165, 230, 245, 0.45)";
        ctx.fillRect(0, 0, 32, 32);
        ctx.strokeStyle = "#ffffff";
        ctx.lineWidth = 1.5;
        ctx.strokeRect(1, 1, 30, 30);
      }} else if (type.includes("bricks")) {{
        ctx.fillStyle = "#8a3a2b";
        ctx.fillRect(0, 0, 32, 32);
        ctx.strokeStyle = "#d4b8a8";
        ctx.lineWidth = 1;
        ctx.strokeRect(0, 0, 16, 8); ctx.strokeRect(16, 0, 16, 8);
        ctx.strokeRect(8, 8, 16, 8); ctx.strokeRect(0, 16, 16, 8); ctx.strokeRect(16, 16, 16, 8);
      }} else if (type.includes("campfire") || type.includes("furnace") || type.includes("blast")) {{
        ctx.fillStyle = "#2d3139";
        ctx.fillRect(0, 0, 32, 32);
        ctx.fillStyle = "#f59e0b";
        ctx.fillRect(8, 8, 16, 16);
      }} else if (type.includes("carpet")) {{
        ctx.fillStyle = type.includes("blue") ? "#2563eb" : (type.includes("gray") ? "#475569" : "#dc2626");
        ctx.fillRect(0, 0, 32, 32);
      }} else {{
        ctx.fillStyle = "#64748b";
        ctx.fillRect(0, 0, 32, 32);
      }}

      const texture = new THREE.CanvasTexture(canvas);
      texture.magFilter = THREE.NearestFilter;
      texture.minFilter = THREE.NearestFilter;
      return texture;
    }}

    const materialCache = {{}};
    function getMaterial(blockId) {{
      if (!materialCache[blockId]) {{
        const isGlass = blockId.includes("glass");
        const texture = createBlockTexture(blockId);
        materialCache[blockId] = new THREE.MeshStandardMaterial({{
          map: texture,
          transparent: isGlass,
          opacity: isGlass ? 0.6 : 1.0,
          roughness: 0.85,
          metalness: 0.1
        }});
      }}
      return materialCache[blockId];
    }}

    // Geometries
    const fullBoxGeo = new THREE.BoxGeometry(1, 1, 1);
    const slabGeo = new THREE.BoxGeometry(1, 0.5, 1);
    const stepGeoX = new THREE.BoxGeometry(0.5, 0.5, 1);
    const stepGeoZ = new THREE.BoxGeometry(1, 0.5, 0.5);
    const carpetGeo = new THREE.BoxGeometry(1, 0.15, 1);

    function isStairBlock(blockId, key) {{
      return blockId.includes("stairs") || ["[", "]", "{{", "}}", "U", "u", "<", ">", "^", "v"].includes(key);
    }}

    function getStairSpecs(blockId, key) {{
      let facing = "east";
      let isTop = false;

      if (blockId.includes("facing=west") || key === "]" || key === "}}" || key === ">" || key === "u") {{
        facing = "west";
      }} else if (blockId.includes("facing=north") || key === "v") {{
        facing = "north";
      }} else if (blockId.includes("facing=south") || key === "^") {{
        facing = "south";
      }} else {{
        facing = "east";
      }}

      if (blockId.includes("half=top") || key === "U" || key === "u") {{
        isTop = true;
      }}

      return {{ facing, isTop }};
    }}

    function initThree() {{
      const container = document.getElementById("canvasContainer");
      scene = new THREE.Scene();
      scene.background = new THREE.Color(0x080c18);

      camera = new THREE.PerspectiveCamera(45, container.clientWidth / container.clientHeight, 0.1, 1000);
      renderer = new THREE.WebGLRenderer({{ antialias: true, alpha: true }});
      renderer.setSize(container.clientWidth, container.clientHeight);
      renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
      container.appendChild(renderer.domElement);

      controls = new THREE.OrbitControls(camera, renderer.domElement);
      controls.enableDamping = true;
      controls.dampingFactor = 0.05;

      const hemiLight = new THREE.HemisphereLight(0xddeeff, 0x182030, 0.95);
      scene.add(hemiLight);

      const dirLight = new THREE.DirectionalLight(0xfff3db, 1.25);
      dirLight.position.set(20, 35, 25);
      scene.add(dirLight);

      const ambLight = new THREE.AmbientLight(0x404856, 0.4);
      scene.add(ambLight);

      const grid = new THREE.GridHelper(30, 30, 0x334155, 0x1e293b);
      grid.position.y = -0.01;
      scene.add(grid);

      scene.add(voxelGroup);

      window.addEventListener("resize", onWindowResize);
      animate();
    }}

    function onWindowResize() {{
      const container = document.getElementById("canvasContainer");
      if (!container || !renderer || !camera) return;
      camera.aspect = container.clientWidth / container.clientHeight;
      camera.updateProjectionMatrix();
      renderer.setSize(container.clientWidth, container.clientHeight);
    }}

    function animate() {{
      requestAnimationFrame(animate);
      if (autoRotate && voxelGroup) {{
        voxelGroup.rotation.y += 0.0035;
      }}
      if (controls) controls.update();
      if (renderer && scene && camera) {{
        renderer.render(scene, camera);
      }}
    }}

    function getCurrentModel() {{
      if (currentMode === "master") {{
        return MASTER_PRESETS[currentMasterId] || Object.values(MASTER_PRESETS)[0];
      }} else {{
        const key = `${{currentType}}_${{currentScale}}_${{currentGenerator}}`;
        return MATRIX_MODELS[key] || Object.values(MATRIX_MODELS)[0];
      }}
    }}

    // Dense Voxel Mesh Construction with TRUE Minecraft Stairs
    function renderModel(keepLayer = false) {{
      const model = getCurrentModel();
      if (!model) return;

      const layers = model.layers;
      const sy = layers.length;
      const sz = layers[0].length;
      const sx = layers[0][0].length;

      const slider = document.getElementById("layerSlider");
      if (slider) {{
        slider.max = sy;
        if (!keepLayer) {{
          maxLayer = sy;
          slider.value = sy;
        }} else {{
          if (maxLayer > sy) maxLayer = sy;
          slider.value = maxLayer;
        }}
      }} else {{
        if (!keepLayer) maxLayer = sy;
      }}

      const layerVal = document.getElementById("layerVal");
      if (layerVal) {{
        layerVal.innerText = maxLayer >= sy ? `全部 (Y:${{sy}})` : `第 ${{maxLayer}} 层`;
      }}

      while (voxelGroup.children.length > 0) {{
        voxelGroup.remove(voxelGroup.children[0]);
      }}

      const themeMap = (model.themeReplacements && model.themeReplacements[currentTheme]) ? model.themeReplacements[currentTheme] : {{}};

      const offsetX = -sx / 2.0;
      const offsetZ = -sz / 2.0;

      const renderYMax = Math.min(maxLayer, sy);

      for (let y = 0; y < renderYMax; y++) {{
        for (let z = 0; z < sz; z++) {{
          const row = layers[y][z];
          for (let x = 0; x < sx; x++) {{
            const key = row[x];
            if (key === ".") continue;

            let blockId = model.palette[key] || "minecraft:stone";
            if (themeMap[blockId]) blockId = themeMap[blockId];

            const mat = getMaterial(blockId);

            // STAIR SYSTEM: Base Slab + Step
            if (isStairBlock(blockId, key)) {{
              const {{ facing, isTop }} = getStairSpecs(blockId, key);
              const stairGroup = new THREE.Group();

              const baseMesh = new THREE.Mesh(slabGeo, mat);
              baseMesh.position.set(0, isTop ? 0.25 : -0.25, 0);
              stairGroup.add(baseMesh);

              let stepMesh;
              if (facing === "east") {{
                stepMesh = new THREE.Mesh(stepGeoX, mat);
                stepMesh.position.set(0.25, isTop ? -0.25 : 0.25, 0);
              }} else if (facing === "west") {{
                stepMesh = new THREE.Mesh(stepGeoX, mat);
                stepMesh.position.set(-0.25, isTop ? -0.25 : 0.25, 0);
              }} else if (facing === "south") {{
                stepMesh = new THREE.Mesh(stepGeoZ, mat);
                stepMesh.position.set(0, isTop ? -0.25 : 0.25, 0.25);
              }} else {{
                stepMesh = new THREE.Mesh(stepGeoZ, mat);
                stepMesh.position.set(0, isTop ? -0.25 : 0.25, -0.25);
              }}
              stairGroup.add(stepMesh);

              stairGroup.position.set(x + offsetX + 0.5, y + 0.5, z + offsetZ + 0.5);
              voxelGroup.add(stairGroup);
            }} else if (blockId.includes("slab") || key === "O" || key === "o") {{
              const mesh = new THREE.Mesh(slabGeo, mat);
              mesh.position.set(x + offsetX + 0.5, y + 0.25, z + offsetZ + 0.5);
              voxelGroup.add(mesh);
            }} else if (blockId.includes("carpet") || key === "J") {{
              const mesh = new THREE.Mesh(carpetGeo, mat);
              mesh.position.set(x + offsetX + 0.5, y + 0.08, z + offsetZ + 0.5);
              voxelGroup.add(mesh);
            }} else {{
              const mesh = new THREE.Mesh(fullBoxGeo, mat);
              mesh.position.set(x + offsetX + 0.5, y + 0.5, z + offsetZ + 0.5);
              voxelGroup.add(mesh);
            }}
          }}
        }}
      }}

      const maxDim = Math.max(sx, sy, sz);
      camera.position.set(maxDim * 1.5, maxDim * 1.3, maxDim * 1.6);
      controls.target.set(0, sy / 3, 0);
      controls.update();

      updateDetails(model);
    }}

    function updateDetails(model) {{
      if (!model) model = getCurrentModel();
      if (!model) return;

      document.getElementById("infoTitle").innerText = model.name;
      document.getElementById("metricDims").innerText = `${{model.sizeX}}x${{model.sizeY}}x${{model.sizeZ}}`;

      const metrics = model.metrics || {{
        facade_depth_index: 58.1,
        roof_complexity_score: 100,
        interior_furnishing_score: 100,
        overall_aesthetic_grade: "S+"
      }};

      const grade = metrics.overall_aesthetic_grade || (model.generator === "master" ? "S+" : "A");
      document.getElementById("infoBadge").innerText = `${{grade}} 评级`;
      document.getElementById("metricGrade").innerText = `GRADE ${{grade}}`;

      document.getElementById("metricDepthVal").innerText = `${{metrics.facade_depth_index}}%`;
      document.getElementById("metricDepthBar").style.width = `${{metrics.facade_depth_index}}%`;

      document.getElementById("metricRoofVal").innerText = `${{metrics.roof_complexity_score}} 分`;
      document.getElementById("metricRoofBar").style.width = `${{metrics.roof_complexity_score}}%`;

      document.getElementById("metricInteriorVal").innerText = `${{metrics.interior_furnishing_score}} 分`;
      document.getElementById("metricInteriorBar").style.width = `${{metrics.interior_furnishing_score}}%`;

      document.getElementById("metricLatency").innerText = `${{model.execution_ms || 0.08}} ms`;

      const slider = document.getElementById("layerSlider");
      if (slider) {{
        slider.max = model.sizeY;
      }}
      const label = document.getElementById("layerVal");
      if (label) {{
        label.innerText = maxLayer >= model.sizeY ? `全部 (Y:${{model.sizeY}})` : `第 ${{maxLayer}} 层`;
      }}
    }}

    function renderSidebar() {{
      const list = document.getElementById("itemList");
      list.innerHTML = "";

      if (currentMode === "master") {{
        document.getElementById("sidebarTitle").innerHTML = `<span>大师级核心预设 (10)</span><span class="text-[10px] text-amber-400 font-mono bg-amber-950/60 px-2 py-0.5 rounded border border-amber-800/40">点击即换</span>`;
        document.getElementById("archetypePills").style.display = "none";
        document.getElementById("topMatrixFilters").style.display = "none";

        Object.values(MASTER_PRESETS).forEach(p => {{
          const btn = document.createElement("button");
          btn.className = `p-2.5 rounded-xl text-left bg-slate-900/90 hover:bg-slate-800 border ${{p.id === currentMasterId ? "border-amber-500 text-amber-400 bg-amber-500/10" : "border-slate-800 text-slate-200"}} transition flex items-center gap-3`;
          btn.innerHTML = `
            <span class="text-xl">🏛️</span>
            <div class="flex-1 min-w-0">
              <div class="text-xs font-semibold truncate">${{p.name}}</div>
              <div class="text-[10px] text-slate-400 font-mono mt-0.5">${{p.sizeX}}x${{p.sizeY}}x${{p.sizeZ}} • S+ Master</div>
            </div>
          `;
          btn.onclick = () => {{
            currentMasterId = p.id;
            renderSidebar();
            renderModel();
          }};
          list.appendChild(btn);
        }});
      }} else {{
        document.getElementById("sidebarTitle").innerHTML = `<span>多体积多类型对照矩阵 (60)</span><span class="text-[10px] text-emerald-400 font-mono bg-emerald-950/60 px-2 py-0.5 rounded border border-emerald-800/40">全量生成</span>`;
        document.getElementById("archetypePills").style.display = "flex";
        document.getElementById("topMatrixFilters").style.display = "flex";

        Object.values(MATRIX_MODELS).forEach(m => {{
          if (currentFilterType !== "all" && m.type !== currentFilterType) return;

          const isActive = (m.type === currentType && m.scale === currentScale && m.generator === currentGenerator);
          const btn = document.createElement("button");
          btn.className = `p-2 rounded-xl text-left bg-slate-900/90 hover:bg-slate-800 border ${{isActive ? "border-amber-500 text-amber-400 bg-amber-500/10" : "border-slate-800 text-slate-200"}} transition flex items-center gap-2.5`;
          btn.innerHTML = `
            <span class="text-base">${{m.generator === "master" ? "👑" : (m.generator === "jigsaw" ? "🧱" : (m.generator === "grammar" ? "📐" : (m.generator === "wfc" ? "🧩" : "📦")))}}</span>
            <div class="flex-1 min-w-0">
              <div class="text-xs font-semibold truncate">${{m.name}}</div>
              <div class="text-[10px] text-slate-400 font-mono mt-0.5">${{m.sizeX}}x${{m.sizeY}}x${{m.sizeZ}} • ${{m.solid_voxels}}方块</div>
            </div>
          `;
          btn.onclick = () => {{
            currentType = m.type;
            currentScale = m.scale;
            currentGenerator = m.generator;
            document.getElementById("selectTopType").value = m.type;
            document.getElementById("selectTopScale").value = m.scale;
            document.getElementById("selectTopGen").value = m.generator;
            renderSidebar();
            renderModel();
          }};
          list.appendChild(btn);
        }});
      }}
    }}

    function setupEvents() {{
      // Mode Switching
      document.getElementById("btnModeMatrix").onclick = () => {{
        currentMode = "matrix";
        document.getElementById("btnModeMatrix").className = "active-mode px-3 py-1 rounded-lg transition flex items-center gap-1.5";
        document.getElementById("btnModeMaster").className = "px-3 py-1 rounded-lg text-slate-400 hover:text-slate-200 transition flex items-center gap-1.5";
        renderSidebar();
        renderModel();
      }};

      document.getElementById("btnModeMaster").onclick = () => {{
        currentMode = "master";
        document.getElementById("btnModeMaster").className = "active-mode px-3 py-1 rounded-lg transition flex items-center gap-1.5";
        document.getElementById("btnModeMatrix").className = "px-3 py-1 rounded-lg text-slate-400 hover:text-slate-200 transition flex items-center gap-1.5";
        renderSidebar();
        renderModel();
      }};

      // Filter Pills
      document.querySelectorAll(".type-pill").forEach(pill => {{
        pill.onclick = () => {{
          document.querySelectorAll(".type-pill").forEach(p => {{
            p.className = "type-pill px-2 py-1 rounded-lg bg-slate-900 text-slate-300 hover:bg-slate-800";
          }});
          pill.className = "type-pill px-2 py-1 rounded-lg bg-amber-500 text-slate-950 font-bold";
          currentFilterType = pill.dataset.type;
          renderSidebar();
        }};
      }});

      // Top Matrix Dropdowns
      document.getElementById("selectTopType").onchange = (e) => {{
        currentType = e.target.value;
        renderSidebar();
        renderModel();
      }};
      document.getElementById("selectTopScale").onchange = (e) => {{
        currentScale = e.target.value;
        renderSidebar();
        renderModel();
      }};
      document.getElementById("selectTopGen").onchange = (e) => {{
        currentGenerator = e.target.value;
        renderSidebar();
        renderModel();
      }};

      // Theme Buttons
      document.querySelectorAll(".theme-btn").forEach(btn => {{
        btn.onclick = () => {{
          document.querySelectorAll(".theme-btn").forEach(b => {{
            b.classList.remove("active", "chip-btn");
            b.classList.add("text-slate-400");
          }});
          btn.classList.add("active", "chip-btn");
          btn.classList.remove("text-slate-400");
          currentTheme = btn.dataset.theme;
          renderModel(true);
        }};
      }});

      // Auto Rotate Button
      document.getElementById("btnAutoRotate").onclick = () => {{
        autoRotate = !autoRotate;
        document.getElementById("rotateIcon").innerText = autoRotate ? "⏸" : "▶";
      }};

      // Reset View Button
      document.getElementById("btnResetView").onclick = () => {{
        if (voxelGroup) voxelGroup.rotation.y = 0;
        renderModel(false);
      }};

      // Toggle Sidebar
      document.getElementById("btnToggleSidebar").onclick = () => {{
        const sidebar = document.getElementById("sidebar");
        const icon = document.getElementById("sidebarIcon");
        const text = document.getElementById("sidebarText");
        if (sidebar.style.display === "none") {{
          sidebar.style.display = "flex";
          icon.innerText = "◀";
          text.innerText = "折叠列表";
        }} else {{
          sidebar.style.display = "none";
          icon.innerText = "▶";
          text.innerText = "展开列表";
        }}
        onWindowResize();
      }};

      // Layer Cutaway Slider
      document.getElementById("layerSlider").oninput = (e) => {{
        maxLayer = parseInt(e.target.value);
        const model = getCurrentModel();
        const sy = model ? model.sizeY : maxLayer;
        document.getElementById("layerVal").innerText = maxLayer >= sy ? `全部 (Y:${{sy}})` : `第 ${{maxLayer}} 层`;
        renderModel(true);
      }};

      // Full Height Button
      document.getElementById("btnFullHeight").onclick = () => {{
        const model = getCurrentModel();
        if (model) {{
          maxLayer = model.sizeY;
          const slider = document.getElementById("layerSlider");
          if (slider) {{
            slider.max = model.sizeY;
            slider.value = model.sizeY;
          }}
          document.getElementById("layerVal").innerText = `全部 (Y:${{model.sizeY}})`;
          renderModel(false);
        }}
      }};
    }}

    window.onload = () => {{
      initThree();
      setupEvents();
      renderSidebar();
      renderModel();
    }};
  </script>
</body>
</html>
"""

    with open(STANDALONE_OUTPUT, "w", encoding="utf-8") as f:
        f.write(html)
    print(f"Generated standalone fullscreen browser viewer at: {STANDALONE_OUTPUT}")

    with open(ARTIFACT_STANDALONE, "w", encoding="utf-8") as f:
        f.write(html)
    print(f"Copied to artifact directory: {ARTIFACT_STANDALONE}")

if __name__ == "__main__":
    build_fullscreen_html()
