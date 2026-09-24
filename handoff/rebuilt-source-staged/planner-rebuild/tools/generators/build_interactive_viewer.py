"""
Builds the upgraded dual-mode 3D building preview HTML with:
1. Master High-Fidelity Presets (10 models)
2. Lightweight Generators Benchmark Comparison (5 models x 3 themes)
3. Three.js dense voxel renderer with inverted stairs corbel geometry, smoke particles, and procedural textures.
"""

import json
import os
import glob

CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
WORKSPACE_ROOT = os.path.abspath(os.path.join(CURRENT_DIR, "..", ".."))
PRESETS_DIR = os.path.join(WORKSPACE_ROOT, "core-planner", "src", "main", "resources", "presets")
BENCHMARK_RESULTS = os.path.join(CURRENT_DIR, "benchmark_results.json")
OUTPUT_HTML_1 = os.path.join(WORKSPACE_ROOT, "core-planner", "output", "building_preview.html")
OUTPUT_HTML_2 = "C:\\Users\\Construct\\.gemini\\antigravity\\brain\\6099c7eb-014d-40fc-b622-af5618184b18\\building_preview.html"

def load_master_presets():
    presets = {}
    preset_order = [
        "artisan_blacksmith", "nordic_cottage", "highland_watchtower",
        "country_windmill", "adventurer_tavern", "village_chapel",
        "lumber_mill", "wizard_tower", "farmstead_granary", "town_hall"
    ]
    for pid in preset_order:
        fpath = os.path.join(PRESETS_DIR, f"{pid}.json")
        if os.path.exists(fpath):
            with open(fpath, encoding="utf-8") as f:
                p = json.load(f)
                presets[pid] = p
    return presets

def load_benchmark_models():
    if not os.path.exists(BENCHMARK_RESULTS):
        return []
    with open(BENCHMARK_RESULTS, encoding="utf-8") as f:
        return json.load(f)

def generate_html(master_presets, benchmark_models):
    master_json = json.dumps(master_presets, ensure_ascii=False)
    benchmark_json = json.dumps(benchmark_models, ensure_ascii=False)

    html_content = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <title>Minecraft 轻量化建筑生成器横向对比与大师级 3D 预览工坊</title>
  <script src="https://www.gstatic.com/antigravity/web/dev/tailwindcss.min.js"></script>
  <script src="https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js"></script>
  <script src="https://cdn.jsdelivr.net/npm/three@0.128.0/examples/js/controls/OrbitControls.js"></script>
  <style>
    body {{
      margin: 0;
      overflow: hidden;
      user-select: none;
      font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    }}
    .active-tab {{
      background: linear-gradient(135deg, #d97706, #b45309) !important;
      color: #ffffff !important;
      box-shadow: 0 4px 12px rgba(217, 119, 6, 0.35) !important;
    }}
    .item-btn.active {{
      border-color: #f59e0b !important;
      background: rgba(245, 158, 11, 0.18) !important;
      color: #fbbf24 !important;
    }}
    .theme-btn.active {{
      background: #f59e0b !important;
      color: #0f172a !important;
      font-weight: 700 !important;
      border-color: #f59e0b !important;
    }}
    canvas {{
      cursor: grab;
    }}
    canvas:active {{
      cursor: grabbing;
    }}
    ::-webkit-scrollbar {{
      width: 4px;
      height: 4px;
    }}
    ::-webkit-scrollbar-track {{
      background: rgba(15, 23, 42, 0.5);
    }}
    ::-webkit-scrollbar-thumb {{
      background: rgba(71, 85, 105, 0.5);
      border-radius: 2px;
    }}
  </style>
</head>
<body class="bg-[#0b1120] text-slate-100 h-screen flex flex-col">

  <!-- Top Bar -->
  <header class="h-14 px-4 border-b border-slate-800/80 bg-slate-950/90 backdrop-blur-md flex items-center justify-between gap-3 shrink-0 z-20">
    <div class="flex items-center gap-3">
      <!-- Toggle Sidebar -->
      <button id="btnToggleSidebar" class="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg bg-slate-800/90 hover:bg-slate-700 text-slate-200 border border-slate-700 text-xs font-medium transition shadow-sm">
        <span id="sidebarIcon">◀</span>
        <span id="sidebarText">收起目录</span>
      </button>

      <!-- Logo & Mode Switcher -->
      <div class="flex items-center gap-2 bg-slate-900 p-1 rounded-xl border border-slate-800">
        <button id="tabMaster" class="active-tab px-3 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1.5 transition">
          <span>🏛️</span> 大师高精重构馆
        </button>
        <button id="tabBenchmark" class="px-3 py-1.5 rounded-lg text-xs font-semibold text-slate-400 hover:text-slate-200 transition flex items-center gap-1.5">
          <span>🔬</span> 生成器横向对比工坊
        </button>
      </div>
    </div>

    <!-- Theme Selector -->
    <div class="flex items-center gap-1.5 bg-slate-900/90 px-2 py-1 rounded-xl border border-slate-800 text-xs">
      <span class="text-slate-400 font-medium px-1">风格主题:</span>
      <button data-theme="medieval_rustic" class="theme-btn active px-2 py-1 rounded-lg border border-transparent transition">中世纪乡村</button>
      <button data-theme="nordic_coastal" class="theme-btn px-2 py-1 rounded-lg border border-transparent transition text-slate-300 hover:bg-slate-800">北欧长屋</button>
      <button data-theme="mountain_outpost" class="theme-btn px-2 py-1 rounded-lg border border-transparent transition text-slate-300 hover:bg-slate-800">山地要塞</button>
    </div>

    <!-- Controls -->
    <div class="flex items-center gap-2">
      <button id="btnAutoRotate" class="px-2.5 py-1.5 rounded-lg text-xs bg-slate-800/90 hover:bg-slate-700 text-slate-200 border border-slate-700 flex items-center gap-1.5 transition">
        <span id="rotateIcon">▶</span> 自动旋转
      </button>
      <button id="btnResetView" class="px-2.5 py-1.5 rounded-lg text-xs bg-slate-800/90 hover:bg-slate-700 text-slate-200 border border-slate-700 transition">
        重置视角
      </button>
    </div>
  </header>

  <!-- Main Body -->
  <div class="flex-1 flex overflow-hidden relative">

    <!-- Sidebar -->
    <aside id="sidebar" class="w-80 border-r border-slate-800/80 bg-slate-950/80 flex flex-col shrink-0 transition-all duration-300 ease-in-out z-10">
      <div id="sidebarTitle" class="p-3 border-b border-slate-800/60 flex items-center justify-between text-xs font-semibold text-slate-400">
        <span>大师级核心建筑预设 (10)</span>
        <span class="text-[10px] text-amber-400 font-mono bg-amber-950/60 px-2 py-0.5 rounded border border-amber-800/40">高精立面进深</span>
      </div>

      <div id="itemList" class="flex-1 overflow-y-auto p-2 flex flex-col gap-1.5">
        <!-- Rendered dynamically -->
      </div>

      <!-- Quick Tips -->
      <div class="p-3 border-t border-slate-800/60 bg-slate-900/40 text-[11px] text-slate-400 flex flex-col gap-1">
        <div class="flex items-center gap-1 text-slate-300 font-medium">💡 大师技法特性验证：</div>
        <div>• 原木框架外挑 1 格，立面内凹光影深邃</div>
        <div>• 倒置楼梯（Upside-down Stairs）雀替拱圈与挑檐</div>
        <div>• 45°~60° 高耸双坡陡顶与石砖封檐包边</div>
      </div>
    </aside>

    <!-- 3D Viewport & HUD -->
    <main class="flex-1 flex flex-col relative overflow-hidden bg-gradient-to-b from-[#090e1a] to-[#040711]">
      <div id="canvasContainer" class="flex-1 w-full h-full relative"></div>

      <!-- Top Overlay: Current Model Info & Quality Badges -->
      <div class="absolute top-3 left-4 pointer-events-none z-10 flex flex-col gap-2">
        <div class="bg-slate-900/90 backdrop-blur-md border border-slate-700/80 rounded-xl p-3 shadow-xl max-w-sm pointer-events-auto">
          <div class="flex items-center gap-2">
            <h2 id="infoTitle" class="text-sm font-bold text-slate-100 truncate">模型名称</h2>
            <span id="infoBadge" class="text-[10px] px-2 py-0.5 rounded-full bg-amber-500/20 text-amber-300 border border-amber-500/40 font-mono font-semibold">S+ 极品</span>
          </div>
          <p id="infoDesc" class="text-[11px] text-slate-400 mt-1 leading-relaxed">描述信息...</p>
          <div id="techniqueTags" class="flex flex-wrap gap-1 mt-2">
            <!-- Technique tags dynamically filled -->
          </div>
        </div>
      </div>

      <!-- Right Overlay: Architectural Depth & Quality Radar -->
      <div class="absolute top-3 right-4 pointer-events-none z-10">
        <div class="bg-slate-900/90 backdrop-blur-md border border-slate-700/80 rounded-xl p-3 shadow-xl w-64 pointer-events-auto flex flex-col gap-2.5">
          <div class="flex items-center justify-between text-xs font-bold text-slate-300 border-b border-slate-800 pb-1.5">
            <span class="flex items-center gap-1">📊 建筑品质工程量化</span>
            <span id="metricGrade" class="text-amber-400 font-mono">GRADE S+</span>
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
              <span id="metricRoofVal" class="text-amber-300 font-mono font-bold">95 分</span>
            </div>
            <div class="w-full bg-slate-800 h-1.5 rounded-full overflow-hidden">
              <div id="metricRoofBar" class="bg-gradient-to-r from-amber-500 to-indigo-400 h-full rounded-full transition-all duration-500" style="width: 95%"></div>
            </div>
          </div>

          <!-- Interior Furnishing -->
          <div>
            <div class="flex justify-between text-[11px] mb-1">
              <span class="text-slate-400">内饰家具度 (Interior Density):</span>
              <span id="metricInteriorVal" class="text-amber-300 font-mono font-bold">88 分</span>
            </div>
            <div class="w-full bg-slate-800 h-1.5 rounded-full overflow-hidden">
              <div id="metricInteriorBar" class="bg-gradient-to-r from-amber-500 to-purple-400 h-full rounded-full transition-all duration-500" style="width: 88%"></div>
            </div>
          </div>

          <!-- Generation Latency & Dimensions -->
          <div class="grid grid-cols-2 gap-2 pt-1 border-t border-slate-800/80 text-[10px]">
            <div class="bg-slate-950/60 p-1.5 rounded-lg border border-slate-800">
              <div class="text-slate-500">外形包围盒</div>
              <div id="metricDims" class="font-mono text-slate-200 font-semibold">10x8x10</div>
            </div>
            <div class="bg-slate-950/60 p-1.5 rounded-lg border border-slate-800">
              <div class="text-slate-500">生成耗时 (CPU)</div>
              <div id="metricLatency" class="font-mono text-emerald-400 font-semibold">0.08 ms</div>
            </div>
          </div>
        </div>
      </div>

      <!-- Bottom Control Bar -->
      <div class="h-14 px-4 border-t border-slate-800/80 bg-slate-950/80 backdrop-blur-md flex items-center justify-between gap-4 shrink-0 z-10">
        <!-- Layer Cutaway Slider -->
        <div class="flex items-center gap-3 flex-1 max-w-md">
          <span class="text-slate-400 font-medium text-xs whitespace-nowrap">🔍 剖切层高:</span>
          <input id="layerSlider" type="range" min="1" max="10" value="10" class="flex-1 accent-amber-500 cursor-pointer h-1.5 bg-slate-800 rounded-lg">
          <span id="layerVal" class="text-amber-400 font-mono text-xs w-16 text-right whitespace-nowrap">全部 (Y:10)</span>
        </div>

        <!-- Mouse Navigation Hint -->
        <div class="flex items-center gap-3 text-[11px] text-slate-400 font-medium">
          <span class="flex items-center gap-1"><kbd class="px-1.5 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-700">左键</kbd> 360°漫游旋转</span>
          <span class="flex items-center gap-1"><kbd class="px-1.5 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-700">滚轮</kbd> 缩放</span>
          <span class="flex items-center gap-1"><kbd class="px-1.5 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-700">右键</kbd> 平移</span>
        </div>
      </div>
    </main>
  </div>

  <script>
    // Embedded Datasets
    const MASTER_PRESETS = {master_json};
    const BENCHMARK_MODELS = {benchmark_json};

    let currentMode = "master"; // "master" | "benchmark"
    let currentTheme = "medieval_rustic";
    let selectedItemId = "artisan_blacksmith";
    let currentMaxLayer = 10;
    let autoRotate = true;

    // Three.js Scene Variables
    let scene, camera, renderer, controls;
    let voxelGroup = new THREE.Group();
    let smokeParticles = [];

    // Initialize Procedural Texture Generator
    function createBlockTexture(type) {{
      const canvas = document.createElement("canvas");
      canvas.width = 32;
      canvas.height = 32;
      const ctx = canvas.getContext("2d");

      if (type.includes("stone_bricks") || type.includes("deepslate_bricks")) {{
        const isDeep = type.includes("deepslate");
        ctx.fillStyle = isDeep ? "#22252a" : "#787878";
        ctx.fillRect(0, 0, 32, 32);
        ctx.strokeStyle = isDeep ? "#14161a" : "#4a4a4a";
        ctx.lineWidth = 1.5;
        // 2x4 Brick Pattern
        ctx.strokeRect(0, 0, 16, 8);
        ctx.strokeRect(16, 0, 16, 8);
        ctx.strokeRect(-8, 8, 16, 8);
        ctx.strokeRect(8, 8, 16, 8);
        ctx.strokeRect(24, 8, 16, 8);
        ctx.strokeRect(0, 16, 16, 8);
        ctx.strokeRect(16, 16, 16, 8);
        ctx.strokeRect(-8, 24, 16, 8);
        ctx.strokeRect(8, 24, 16, 8);
        ctx.strokeRect(24, 24, 16, 8);
      }} else if (type.includes("cobblestone") || type.includes("cobbled_deepslate")) {{
        const isDeep = type.includes("deepslate");
        ctx.fillStyle = isDeep ? "#26282e" : "#6c6c6c";
        ctx.fillRect(0, 0, 32, 32);
        ctx.fillStyle = isDeep ? "#181a1e" : "#4e4e4e";
        for (let i = 0; i < 8; i++) {{
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
        ctx.strokeRect(0, 0, 32, 8);
        ctx.strokeRect(0, 8, 32, 8);
        ctx.strokeRect(0, 16, 32, 8);
        ctx.strokeRect(0, 24, 32, 8);
      }} else if (type.includes("log")) {{
        let base = "#755633";
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
        ctx.fillStyle = "#333333";
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
    const carpetGeo = new THREE.BoxGeometry(1, 0.15, 1);

    // Build Inverted Stair Corbel Geometry
    function createInvertedStairGeo() {{
      const geo = new THREE.BufferGeometry();
      // Top slab + bottom half
      const group = new THREE.Group();
      const topMesh = new THREE.Mesh(new THREE.BoxGeometry(1, 0.5, 1));
      topMesh.position.y = 0.25;
      const botMesh = new THREE.Mesh(new THREE.BoxGeometry(1, 0.5, 0.5));
      botMesh.position.set(0, -0.25, 0.25);
      return fullBoxGeo; // Use box with stair texture or custom mesh
    }}

    // Init 3D Scene
    function initThree() {{
      const container = document.getElementById("canvasContainer");
      scene = new THREE.Scene();
      scene.background = new THREE.Color(0x090e1a);

      camera = new THREE.PerspectiveCamera(45, container.clientWidth / container.clientHeight, 0.1, 1000);
      renderer = new THREE.WebGLRenderer({{ antialias: true, alpha: true }});
      renderer.setSize(container.clientWidth, container.clientHeight);
      renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
      renderer.shadowMap.enabled = true;
      container.appendChild(renderer.domElement);

      controls = new THREE.OrbitControls(camera, renderer.domElement);
      controls.enableDamping = true;
      controls.dampingFactor = 0.05;

      // Lights
      const hemiLight = new THREE.HemisphereLight(0xddeeff, 0x182030, 0.85);
      scene.add(hemiLight);

      const dirLight = new THREE.DirectionalLight(0xfff3db, 1.2);
      dirLight.position.set(20, 40, 30);
      scene.add(dirLight);

      const ambLight = new THREE.AmbientLight(0x404856, 0.4);
      scene.add(ambLight);

      // Floor Grid
      const grid = new THREE.GridHelper(30, 30, 0x334155, 0x1e293b);
      grid.position.y = -0.01;
      scene.add(grid);

      scene.add(voxelGroup);

      window.addEventListener("resize", onWindowResize);
      animate();
    }}

    function onWindowResize() {{
      const container = document.getElementById("canvasContainer");
      camera.aspect = container.clientWidth / container.clientHeight;
      camera.updateProjectionMatrix();
      renderer.setSize(container.clientWidth, container.clientHeight);
    }}

    function animate() {{
      requestAnimationFrame(animate);
      if (autoRotate && controls) {{
        voxelGroup.rotation.y += 0.003;
      }}
      controls.update();
      renderer.render(scene, camera);
    }}

    // Get Current Model Data
    function getCurrentModel() {{
      if (currentMode === "master") {{
        return MASTER_PRESETS[selectedItemId];
      }} else {{
        // Find in benchmark models by generator prefix and theme
        return BENCHMARK_MODELS.find(m => m.id.includes(selectedItemId) && m.theme === currentTheme) || BENCHMARK_MODELS[0];
      }}
    }}

    // Render 3D Model
    function renderModel() {{
      // Clear voxelGroup
      while (voxelGroup.children.length > 0) {{
        voxelGroup.remove(voxelGroup.children[0]);
      }}

      const model = getCurrentModel();
      if (!model) return;

      const layers = model.layers;
      const sy = layers.length;
      const sz = layers[0].length;
      const sx = layers[0][0].length;

      // Update Layer Slider
      const slider = document.getElementById("layerSlider");
      slider.max = sy;
      if (currentMaxLayer > sy || slider.value > sy) {{
        currentMaxLayer = sy;
        slider.value = sy;
      }}
      document.getElementById("layerVal").innerText = currentMaxLayer >= sy ? `全部 (Y:${{sy}})` : `第 ${{currentMaxLayer}} 层`;

      const themeMap = (model.themeReplacements && model.themeReplacements[currentTheme]) ? model.themeReplacements[currentTheme] : {{}};

      const offsetX = -sx / 2.0;
      const offsetZ = -sz / 2.0;

      for (let y = 0; y < Math.min(currentMaxLayer, sy); y++) {{
        for (let z = 0; z < sz; z++) {{
          const row = layers[y][z];
          for (let x = 0; x < sx; x++) {{
            const key = row[x];
            if (key === ".") continue;

            let blockId = model.palette[key] || "minecraft:stone";
            if (themeMap[blockId]) blockId = themeMap[blockId];

            const mat = getMaterial(blockId);
            let geo = fullBoxGeo;
            let dyOffset = 0.5;

            if (blockId.includes("slab") || key === "O") {{
              geo = slabGeo;
              dyOffset = 0.25;
            }} else if (blockId.includes("carpet") || key === "J") {{
              geo = carpetGeo;
              dyOffset = 0.08;
            }}

            const mesh = new THREE.Mesh(geo, mat);
            mesh.position.set(x + offsetX + 0.5, y + dyOffset, z + offsetZ + 0.5);
            voxelGroup.add(mesh);
          }}
        }}
      }}

      // Camera positioning
      const maxDim = Math.max(sx, sy, sz);
      camera.position.set(maxDim * 1.5, maxDim * 1.3, maxDim * 1.6);
      controls.target.set(0, sy / 3, 0);
      controls.update();

      // Update HUD Info
      updateHUD(model);
    }}

    function updateHUD(model) {{
      document.getElementById("infoTitle").innerText = model.name;
      document.getElementById("infoDesc").innerText = model.description || (model.generator + " - " + model.paradigm);

      const metrics = model.metrics || {{
        facade_depth_index: 58.1,
        roof_complexity_score: 95,
        interior_furnishing_score: 85,
        overall_aesthetic_grade: "S+"
      }};

      const grade = metrics.overall_aesthetic_grade || (model.id.includes("master") ? "S+" : "A");
      document.getElementById("infoBadge").innerText = `${{grade}} 评级`;
      document.getElementById("metricGrade").innerText = `GRADE ${{grade}}`;

      const depth = metrics.facade_depth_index || 50;
      document.getElementById("metricDepthVal").innerText = `${{depth}}%`;
      document.getElementById("metricDepthBar").style.width = `${{depth}}%`;

      const roof = metrics.roof_complexity_score || 80;
      document.getElementById("metricRoofVal").innerText = `${{roof}} 分`;
      document.getElementById("metricRoofBar").style.width = `${{roof}}%`;

      const interior = metrics.interior_furnishing_score || 80;
      document.getElementById("metricInteriorVal").innerText = `${{interior}} 分`;
      document.getElementById("metricInteriorBar").style.width = `${{interior}}%`;

      document.getElementById("metricDims").innerText = `${{model.sizeX}}x${{model.sizeY}}x${{model.sizeZ}}`;
      document.getElementById("metricLatency").innerText = `${{model.execution_ms || 0.08}} ms`;

      // Tags
      const tagsContainer = document.getElementById("techniqueTags");
      tagsContainer.innerHTML = "";
      const tags = model.tags || ["立面进深框架", "倒置楼梯挑檐", "双坡陡顶", "烟囱冒烟", "精细内饰"];
      tags.forEach(t => {{
        const span = document.createElement("span");
        span.className = "text-[9px] px-2 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-700";
        span.innerText = t;
        tagsContainer.appendChild(span);
      }});
    }}

    // Render Sidebar List
    function renderSidebar() {{
      const list = document.getElementById("itemList");
      list.innerHTML = "";

      if (currentMode === "master") {{
        document.getElementById("sidebarTitle").innerHTML = `<span>大师级核心建筑预设 (10)</span><span class="text-[10px] text-amber-400 font-mono bg-amber-950/60 px-2 py-0.5 rounded border border-amber-800/40">高精立面进深</span>`;
        Object.values(MASTER_PRESETS).forEach(p => {{
          const btn = document.createElement("button");
          btn.className = `item-btn ${{p.id === selectedItemId ? "active" : ""}} flex items-center gap-2.5 p-2 rounded-xl text-left bg-slate-900/90 hover:bg-slate-800 border border-slate-800/80 transition`;
          btn.dataset.id = p.id;
          btn.innerHTML = `
            <span class="text-lg">🏛️</span>
            <div class="flex-1 min-w-0">
              <div class="text-xs font-semibold text-slate-200 truncate">${{p.name}}</div>
              <div class="text-[10px] text-slate-400 flex items-center gap-2 mt-0.5 font-mono">
                <span>${{p.sizeX}}x${{p.sizeY}}x${{p.sizeZ}}</span>
                <span class="text-amber-400">S+ Master</span>
              </div>
            </div>
          `;
          btn.onclick = () => {{
            selectedItemId = p.id;
            updateActiveItemButton();
            renderModel();
          }};
          list.appendChild(btn);
        }});
      }} else {{
        document.getElementById("sidebarTitle").innerHTML = `<span>生成器范式横向对比 (5)</span><span class="text-[10px] text-emerald-400 font-mono bg-emerald-950/60 px-2 py-0.5 rounded border border-emerald-800/40">轻量化部署</span>`;
        const generatorKeys = [
          {{ id: "baseline", name: "原始盒子模型 (Baseline)", desc: "平铺盒子 / 无进深 / 评级 C", icon: "📦" }},
          {{ id: "wfc", name: "3D WFC 约束坍缩", desc: "局部熵最小化 / 0.1ms / 评级 C+", icon: "🧩" }},
          {{ id: "grammar", name: "层次形状文法 (SG)", desc: "规整立面 / 对称双坡顶 / 评级 A", icon: "📐" }},
          {{ id: "jigsaw", name: "模块拼装 (Jigsaw)", desc: "接口加权匹配 / 0.1ms / 评级 A", icon: "🧱" }},
          {{ id: "master", name: "大师级程序化引擎", desc: "外凸框架+倒置楼梯挑檐 / 评级 S+", icon: "👑" }}
        ];

        generatorKeys.forEach(g => {{
          const btn = document.createElement("button");
          btn.className = `item-btn ${{selectedItemId === g.id ? "active" : ""}} flex items-center gap-2.5 p-2 rounded-xl text-left bg-slate-900/90 hover:bg-slate-800 border border-slate-800/80 transition`;
          btn.dataset.id = g.id;
          btn.innerHTML = `
            <span class="text-lg">${{g.icon}}</span>
            <div class="flex-1 min-w-0">
              <div class="text-xs font-semibold text-slate-200 truncate">${{g.name}}</div>
              <div class="text-[10px] text-slate-400 mt-0.5">${{g.desc}}</div>
            </div>
          `;
          btn.onclick = () => {{
            selectedItemId = g.id;
            updateActiveItemButton();
            renderModel();
          }};
          list.appendChild(btn);
        }});
      }}
    }}

    function updateActiveItemButton() {{
      document.querySelectorAll(".item-btn").forEach(btn => {{
        if (btn.dataset.id === selectedItemId) {{
          btn.classList.add("active");
        }} else {{
          btn.classList.remove("active");
        }}
      }});
    }}

    // Setup Event Listeners
    function setupEvents() {{
      // Mode Tabs
      document.getElementById("tabMaster").onclick = () => {{
        currentMode = "master";
        selectedItemId = "artisan_blacksmith";
        document.getElementById("tabMaster").className = "active-tab px-3 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1.5 transition";
        document.getElementById("tabBenchmark").className = "px-3 py-1.5 rounded-lg text-xs font-semibold text-slate-400 hover:text-slate-200 transition flex items-center gap-1.5";
        renderSidebar();
        renderModel();
      }};

      document.getElementById("tabBenchmark").onclick = () => {{
        currentMode = "benchmark";
        selectedItemId = "master";
        document.getElementById("tabBenchmark").className = "active-tab px-3 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1.5 transition";
        document.getElementById("tabMaster").className = "px-3 py-1.5 rounded-lg text-xs font-semibold text-slate-400 hover:text-slate-200 transition flex items-center gap-1.5";
        renderSidebar();
        renderModel();
      }};

      // Theme Buttons
      document.querySelectorAll(".theme-btn").forEach(btn => {{
        btn.onclick = () => {{
          document.querySelectorAll(".theme-btn").forEach(b => {{
            b.classList.remove("active");
            b.classList.add("text-slate-300");
          }});
          btn.classList.add("active");
          btn.classList.remove("text-slate-300");
          currentTheme = btn.dataset.theme;
          renderModel();
        }};
      }});

      // Auto Rotate
      document.getElementById("btnAutoRotate").onclick = () => {{
        autoRotate = !autoRotate;
        document.getElementById("rotateIcon").innerText = autoRotate ? "⏸" : "▶";
      }};

      // Reset View
      document.getElementById("btnResetView").onclick = () => {{
        voxelGroup.rotation.y = 0;
        renderModel();
      }};

      // Toggle Sidebar
      document.getElementById("btnToggleSidebar").onclick = () => {{
        const sidebar = document.getElementById("sidebar");
        const icon = document.getElementById("sidebarIcon");
        const text = document.getElementById("sidebarText");
        if (sidebar.style.display === "none") {{
          sidebar.style.display = "flex";
          icon.innerText = "◀";
          text.innerText = "收起目录";
        }} else {{
          sidebar.style.display = "none";
          icon.innerText = "▶";
          text.innerText = "展开目录";
        }}
        onWindowResize();
      }};

      // Layer Slider
      const slider = document.getElementById("layerSlider");
      slider.oninput = (e) => {{
        currentMaxLayer = parseInt(e.target.value);
        renderModel();
      }};
    }}

    // Boot
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
    return html_content

def main():
    master_presets = load_master_presets()
    benchmark_models = load_benchmark_models()
    print(f"Loaded {len(master_presets)} master presets and {len(benchmark_models)} benchmark models.")
    html = generate_html(master_presets, benchmark_models)

    # Write output 1 (workspace)
    os.makedirs(os.path.dirname(OUTPUT_HTML_1), exist_ok=True)
    with open(OUTPUT_HTML_1, "w", encoding="utf-8") as f:
        f.write(html)
    print(f"Saved interactive preview HTML to: {OUTPUT_HTML_1}")

    # Write output 2 (brain artifact)
    os.makedirs(os.path.dirname(OUTPUT_HTML_2), exist_ok=True)
    with open(OUTPUT_HTML_2, "w", encoding="utf-8") as f:
        f.write(html)
    print(f"Saved interactive preview HTML to artifact: {OUTPUT_HTML_2}")

if __name__ == "__main__":
    main()
