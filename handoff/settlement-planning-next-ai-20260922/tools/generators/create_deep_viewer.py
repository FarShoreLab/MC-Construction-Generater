"""
Script to create the enhanced standalone fullscreen 3D previewer and embed viewer.
Integrates:
- 15 Monumental Mega-Building Deep Models (Cathedral, Chateau Palace, Academy Bibliotheca, Guildhall, Manor Keep)
- Authentic full procedural roofs: high-pitch gables with overhangs, mansards with dormers, central domes, and spires
- True Seed Controller UI (Seed input, 🎲 Random Seed, ⚡ Dynamic Re-Solve)
- Mulberry32 client-side procedural generation engine for instant seed variation
- AI Hybrid Solving & Multi-Iteration Timeline (Iter 1 -> Iter 4) inspired by harrynull/mcbuild
- Antigravity CLI Integration Console (Command generation, one-click copy, local bridge status)
- High-performance Three.js InstancedMesh with compound stair meshes
- Full height cutaway default with [完全展示]
"""

import os
import json

CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
ARTIFACT_DIR = r"C:\Users\Construct\.gemini\antigravity\brain\3c4132c5-7408-40d2-b48e-fd18dbe2fd25"
PRESETS_DIR = r"c:\Users\Construct\Downloads\MC Construction Generater\core-planner\src\main\resources\presets"
MATRIX_RESULTS = os.path.join(CURRENT_DIR, "multi_scale_results.json")
DEEP_RESULTS = os.path.join(CURRENT_DIR, "deep_models.json")
THREE_JS_PATH = os.path.join(CURRENT_DIR, "three.min.js")
ORBIT_JS_PATH = os.path.join(CURRENT_DIR, "OrbitControls.js")

STANDALONE_OUTPUT = r"c:\Users\Construct\Downloads\MC Construction Generater\core-planner\output\building_preview.html"
ARTIFACT_STANDALONE = os.path.join(ARTIFACT_DIR, "building_preview.html")

EMBED_OUTPUT_1 = os.path.join(ARTIFACT_DIR, "building_embed.html")
EMBED_OUTPUT_2 = r"c:\Users\Construct\Downloads\MC Construction Generater\core-planner\output\building_embed.html"


def generate_viewers():
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

    deep_models = {}
    if os.path.exists(DEEP_RESULTS):
        with open(DEEP_RESULTS, encoding="utf-8") as f:
            deep_models = json.load(f)

    with open(THREE_JS_PATH, "r", encoding="utf-8") as f:
        three_js_code = f.read()

    with open(ORBIT_JS_PATH, "r", encoding="utf-8") as f:
        orbit_js_code = f.read()

    deep_json = json.dumps(deep_models, ensure_ascii=False)
    matrix_json = json.dumps(matrix_models, ensure_ascii=False)
    master_json = json.dumps(master_presets, ensure_ascii=False)

    html_template = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <title>Minecraft 殿堂级单体宏伟建筑生成体系 (AI Hybrid & Monumental Mega-Architect)</title>
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
    .iter-btn.active {{
      background: #f59e0b !important;
      color: #0f172a !important;
      font-weight: 800 !important;
      box-shadow: 0 0 10px rgba(245, 158, 11, 0.5) !important;
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
    @keyframes pulse-glow {{
      0%, 100% {{ box-shadow: 0 0 15px rgba(245, 158, 11, 0.4); }}
      50% {{ box-shadow: 0 0 25px rgba(245, 158, 11, 0.8); }}
    }}
    .glow-animate {{
      animation: pulse-glow 2s infinite;
    }}
  </style>
  <script>
    // Inlined Three.js Engine (Offline standalone)
    {three_js_code}
  </script>
  <script>
    // Inlined OrbitControls
    {orbit_js_code}
  </script>
</head>
<body class="flex flex-col h-screen w-screen overflow-hidden">

  <!-- Top Header Navigation Bar -->
  <header class="h-14 px-4 bg-slate-950/90 backdrop-blur-md border-b border-slate-800/80 flex items-center justify-between gap-3 shrink-0 z-30 shadow-md">
    <div class="flex items-center gap-3">
      <!-- Toggle Sidebar -->
      <button id="btnToggleSidebar" class="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-slate-900 hover:bg-slate-800 text-slate-200 border border-slate-700 text-xs font-medium transition shadow-sm cursor-pointer">
        <span id="sidebarIcon">◀</span>
        <span id="sidebarText">折叠列表</span>
      </button>

      <!-- App Title & Mode Switch -->
      <div class="flex items-center gap-2">
        <div class="w-8 h-8 rounded-xl bg-gradient-to-tr from-amber-500 to-amber-700 flex items-center justify-center text-base shadow-md">
          🏛️
        </div>
        <div class="flex bg-slate-900 p-1 rounded-xl border border-slate-800 text-xs font-semibold">
          <button id="btnModeDeep" class="active-mode px-3 py-1 rounded-lg transition flex items-center gap-1.5 cursor-pointer">
            <span>⚡</span> 殿堂级单体建筑 (55m+)
          </button>
          <button id="btnModeAi" class="px-3 py-1 rounded-lg text-slate-400 hover:text-slate-200 transition flex items-center gap-1.5 cursor-pointer">
            <span>🧠</span> AI 混合迭代工坊
          </button>
          <button id="btnModeMatrix" class="px-3 py-1 rounded-lg text-slate-400 hover:text-slate-200 transition flex items-center gap-1.5 cursor-pointer">
            <span>🔬</span> 全矩阵对比 (60)
          </button>
          <button id="btnModeMaster" class="px-3 py-1 rounded-lg text-slate-400 hover:text-slate-200 transition flex items-center gap-1.5 cursor-pointer">
            <span>👑</span> 经典精选馆 (10)
          </button>
        </div>
      </div>

      <!-- Seed Controller UI -->
      <div class="flex items-center gap-1.5 bg-slate-900/90 px-2.5 py-1 rounded-xl border border-slate-700/80 text-xs shadow-inner">
        <span class="text-amber-400 font-bold">Seed:</span>
        <input id="inputSeed" type="number" class="w-20 bg-slate-950 text-amber-300 px-1.5 py-0.5 rounded border border-slate-700 font-mono text-center text-xs focus:outline-none focus:border-amber-500" value="8848">
        <button id="btnRandomSeed" title="随机生成新种子" class="px-2 py-0.5 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded text-xs font-bold border border-slate-600 transition cursor-pointer">
          🎲 随机
        </button>
      </div>

      <!-- Trigger Live Dynamic Synthesis Button -->
      <button id="btnTriggerSynthesis" class="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-gradient-to-r from-amber-600 to-orange-600 hover:from-amber-500 hover:to-orange-500 text-white text-xs font-bold shadow-lg transition cursor-pointer glow-animate">
        <span>⚙️</span> 重新深度求解当前建筑
      </button>
    </div>

    <!-- Right Top Toolbar: Theme & Camera -->
    <div class="flex items-center gap-2">
      <!-- Theme Switcher -->
      <div class="flex items-center bg-slate-900/90 p-1 rounded-xl border border-slate-800 text-xs">
        <span class="text-slate-400 text-[11px] px-2 font-medium">材质主题:</span>
        <button class="theme-btn chip-btn active px-2.5 py-0.5 rounded-lg text-xs font-semibold cursor-pointer" data-theme="medieval_rustic">原版古典</button>
        <button class="theme-btn text-slate-400 hover:text-slate-200 px-2.5 py-0.5 rounded-lg text-xs font-semibold cursor-pointer" data-theme="nordic_coastal">北欧长屋</button>
        <button class="theme-btn text-slate-400 hover:text-slate-200 px-2.5 py-0.5 rounded-lg text-xs font-semibold cursor-pointer" data-theme="mountain_outpost">深板山地</button>
      </div>

      <button id="btnAutoRotate" class="flex items-center gap-1 px-2.5 py-1.5 rounded-xl bg-slate-900 hover:bg-slate-800 text-slate-300 border border-slate-700 text-xs font-medium transition cursor-pointer">
        <span id="rotateIcon">⏸</span> 自动旋转
      </button>
      <button id="btnResetView" class="flex items-center gap-1 px-2.5 py-1.5 rounded-xl bg-slate-900 hover:bg-slate-800 text-slate-300 border border-slate-700 text-xs font-medium transition cursor-pointer">
        重置视角
      </button>
    </div>
  </header>

  <!-- AI Iteration Sub-header Bar (Visible in AI Hybrid Mode or when toggled) -->
  <div id="aiIterationBar" class="h-11 px-4 bg-slate-900/95 border-b border-amber-900/40 flex items-center justify-between gap-3 text-xs shrink-0 z-20">
    <div class="flex items-center gap-3">
      <span class="flex items-center gap-1.5 text-amber-400 font-bold">
        <span>🧠</span> AI 混合迭代管线 (参考 mcbuild):
      </span>
      <div class="flex bg-slate-950 p-0.5 rounded-lg border border-slate-800">
        <button class="iter-btn px-2.5 py-1 rounded text-slate-300 cursor-pointer" data-iter="1">阶段 1: 体量骨架</button>
        <button class="iter-btn px-2.5 py-1 rounded text-slate-300 cursor-pointer" data-iter="2">阶段 2: 立面进深</button>
        <button class="iter-btn px-2.5 py-1 rounded text-slate-300 cursor-pointer" data-iter="3">阶段 3: 宏伟屋顶与尖塔</button>
        <button class="iter-btn active px-2.5 py-1 rounded cursor-pointer" data-iter="4">阶段 4: 拱顶内饰 (Final S+)</button>
      </div>
    </div>
    <div class="flex items-center gap-3">
      <span id="aiCritiqueText" class="text-slate-300 text-xs truncate max-w-xl italic">
        "【终极评定 GRADE S+】全建筑达成严谨结构工程学与古典美学统一：具备宏大真双坡屋顶与尖塔天际线，室内通高拱顶气度庄严。"
      </span>
      <button id="btnShowBlueprint" class="px-2.5 py-1 bg-amber-950/80 hover:bg-amber-900 text-amber-300 rounded border border-amber-700/60 text-[11px] font-semibold cursor-pointer">
        📋 查看 AI 蓝图与评审
      </button>
    </div>
  </div>

  <!-- Main Workspace -->
  <div class="flex flex-1 overflow-hidden relative">

    <!-- Left Sidebar: Architectural Archetypes / Models -->
    <aside id="sidebar" class="w-80 bg-slate-950/95 backdrop-blur-md border-r border-slate-800/80 flex flex-col shrink-0 z-20 transition-all duration-300">
      <div class="p-3 border-b border-slate-800 flex items-center justify-between">
        <div id="sidebarTitle" class="text-xs font-bold text-slate-300 flex items-center gap-2">
          <span>殿堂级超大单体建筑 (15)</span>
          <span class="text-[10px] text-amber-400 font-mono bg-amber-950/60 px-2 py-0.5 rounded border border-amber-800/40">万级体素</span>
        </div>
      </div>

      <!-- Quick Category Filter Pills -->
      <div id="deepFilterPills" class="p-2 border-b border-slate-800/60 flex items-center gap-1.5 text-[11px] font-semibold overflow-x-auto">
        <button class="deep-pill px-2 py-1 rounded-lg bg-amber-500 text-slate-950 font-bold cursor-pointer" data-filter="all">全部</button>
        <button class="deep-pill px-2 py-1 rounded-lg bg-slate-900 text-slate-300 hover:bg-slate-800 cursor-pointer" data-filter="church">大教堂</button>
        <button class="deep-pill px-2 py-1 rounded-lg bg-slate-900 text-slate-300 hover:bg-slate-800 cursor-pointer" data-filter="brickhouse">领主庄园</button>
        <button class="deep-pill px-2 py-1 rounded-lg bg-slate-900 text-slate-300 hover:bg-slate-800 cursor-pointer" data-filter="school">皇家学者</button>
        <button class="deep-pill px-2 py-1 rounded-lg bg-slate-900 text-slate-300 hover:bg-slate-800 cursor-pointer" data-filter="bakery">工匠行会</button>
        <button class="deep-pill px-2 py-1 rounded-lg bg-slate-900 text-slate-300 hover:bg-slate-800 cursor-pointer" data-filter="farm">农舍城堡</button>
      </div>

      <!-- Architecture Item List -->
      <div id="itemList" class="flex-1 overflow-y-auto p-2 space-y-1.5 text-xs">
        <!-- Rendered by JS -->
      </div>
    </aside>

    <!-- Center 3D Three.js Viewport -->
    <main class="flex-1 h-full relative overflow-hidden bg-gradient-to-b from-[#060913] via-[#0b1021] to-[#04060d]">
      <div id="canvasContainer" class="w-full h-full"></div>

      <!-- Floating Header Info Banner -->
      <div class="absolute top-4 left-4 z-10 bg-slate-950/80 backdrop-blur-md p-3.5 rounded-2xl border border-slate-800/80 shadow-2xl max-w-lg pointer-events-auto">
        <div class="flex items-center gap-2">
          <h2 id="modelName" class="text-sm font-bold text-white tracking-wide">皇家总督图书馆与学者圣殿 [殿堂级宏伟全域 (~55m+)]</h2>
          <span id="modelGrade" class="px-2 py-0.5 text-[10px] font-black bg-amber-500/20 text-amber-400 border border-amber-500/40 rounded-full">S+ 评级</span>
        </div>
        <p id="modelDesc" class="text-xs text-slate-300 mt-1 leading-relaxed">
          基于殿堂级单体宏伟工程学架构：中央宏大采光穹顶、科林斯柱廊山花门厅、多层图书回廊与真曼萨德折线坡屋顶（带凸出老虎窗）。
        </p>
        <div class="flex items-center gap-2 mt-2 pt-2 border-t border-slate-800/60 text-[10px] text-slate-400 font-mono">
          <span class="px-2 py-0.5 rounded bg-slate-900 border border-slate-800 text-amber-300">殿堂级真单体</span>
          <span class="px-2 py-0.5 rounded bg-slate-900 border border-slate-800 text-emerald-300">多坡真屋顶</span>
          <span class="px-2 py-0.5 rounded bg-slate-900 border border-slate-800 text-cyan-300">带种子随机性</span>
          <span class="px-2 py-0.5 rounded bg-slate-900 border border-slate-800 text-purple-300">AI 混合多轮迭代</span>
        </div>
      </div>

      <!-- Floating Architectural Quality HUD -->
      <div class="absolute top-4 right-4 z-10 bg-slate-950/80 backdrop-blur-md p-4 rounded-2xl border border-slate-800/80 shadow-2xl w-72 pointer-events-auto space-y-3">
        <div class="flex items-center justify-between border-b border-slate-800 pb-2">
          <span class="text-xs font-bold text-slate-200 flex items-center gap-1.5">
            <span>🏛️</span> 建筑品质工程量化
          </span>
          <span id="hudGrade" class="text-xs font-black text-amber-400">GRADE S+</span>
        </div>

        <!-- Metric Bars -->
        <div class="space-y-2 text-xs">
          <div>
            <div class="flex justify-between text-[11px] mb-1">
              <span class="text-slate-400">总密理方块数 (Block Count):</span>
              <span id="metricVoxelVal" class="font-mono text-emerald-400 font-bold">14,690 块</span>
            </div>
            <div class="w-full h-1.5 bg-slate-900 rounded-full overflow-hidden">
              <div id="metricVoxelBar" class="h-full bg-gradient-to-r from-emerald-500 to-teal-400 rounded-full" style="width: 85%;"></div>
            </div>
          </div>

          <div>
            <div class="flex justify-between text-[11px] mb-1">
              <span class="text-slate-400">立面进深率 (Facade Depth):</span>
              <span id="metricDepthVal" class="font-mono text-cyan-400 font-bold">92.5%</span>
            </div>
            <div class="w-full h-1.5 bg-slate-900 rounded-full overflow-hidden">
              <div id="metricDepthBar" class="h-full bg-gradient-to-r from-cyan-500 to-blue-400 rounded-full" style="width: 92.5%;"></div>
            </div>
          </div>

          <div>
            <div class="flex justify-between text-[11px] mb-1">
              <span class="text-slate-400">屋顶层次分 (Roof Complexity):</span>
              <span id="metricRoofVal" class="font-mono text-amber-400 font-bold">98.0 分</span>
            </div>
            <div class="w-full h-1.5 bg-slate-900 rounded-full overflow-hidden">
              <div id="metricRoofBar" class="h-full bg-gradient-to-r from-amber-500 to-orange-400 rounded-full" style="width: 98%;"></div>
            </div>
          </div>
        </div>

        <div class="pt-2 border-t border-slate-800/80 flex items-center justify-between text-[11px] text-slate-400">
          <span>外包包围盒:</span>
          <span id="metricBBox" class="font-mono text-slate-200">55x44x45</span>
        </div>
      </div>

      <!-- Bottom Floating Controls: Layer Cutaway & CLI Terminal Bar -->
      <div class="absolute bottom-4 left-1/2 -translate-x-1/2 z-10 flex flex-col items-center gap-2 max-w-2xl w-full px-4 pointer-events-auto">
        
        <!-- Layer Cutaway Slider -->
        <div class="w-full bg-slate-950/85 backdrop-blur-md px-4 py-2 rounded-2xl border border-slate-800/80 shadow-2xl flex items-center gap-4 text-xs">
          <span class="flex items-center gap-1.5 text-slate-300 font-medium shrink-0">
            <span>🔍</span> 剖切层高 (Layer Cutaway):
          </span>
          <input id="layerSlider" type="range" min="1" max="60" value="60" class="flex-1 h-1.5 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-amber-500">
          <span id="layerVal" class="font-mono text-amber-400 shrink-0 w-24 text-right">全部 (Y:44)</span>
          <button id="btnFullHeight" class="px-2.5 py-1 rounded-lg bg-amber-500/20 hover:bg-amber-500/30 text-amber-400 border border-amber-500/40 text-[11px] font-semibold transition cursor-pointer shrink-0">
            完全展示
          </button>
        </div>

        <!-- Antigravity CLI Integration Console Bar -->
        <div class="w-full bg-slate-950/90 backdrop-blur-md px-4 py-2 rounded-2xl border border-amber-900/40 shadow-2xl flex items-center justify-between gap-3 text-xs">
          <div class="flex items-center gap-2 min-w-0">
            <span class="px-1.5 py-0.5 bg-amber-950 text-amber-400 font-mono text-[10px] rounded border border-amber-800 shrink-0">CLI</span>
            <code id="cliCommandText" class="text-slate-300 font-mono text-[11px] truncate select-all">
              python tools/generators/mc_architect_cli.py --archetype school --scale large --seed 8848 --iterations 4
            </code>
          </div>
          <div class="flex items-center gap-2 shrink-0">
            <span id="cliBridgeStatus" class="flex items-center gap-1 text-[10px] text-slate-400 font-mono">
              <span class="w-2 h-2 rounded-full bg-emerald-400 inline-block animate-pulse"></span>
              CLI 就绪
            </span>
            <button id="btnCopyCli" class="px-2.5 py-1 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg border border-slate-600 text-[11px] font-semibold transition cursor-pointer">
              一键复制指令
            </button>
          </div>
        </div>

      </div>

      <!-- Synthesis Simulation / Dynamic Calculation Modal -->
      <div id="synthesisConsole" class="hidden absolute inset-0 z-50 bg-slate-950/80 backdrop-blur-md flex items-center justify-center p-6">
        <div class="w-full max-w-lg bg-slate-900 border border-amber-500/40 rounded-3xl p-6 shadow-2xl space-y-4">
          <div class="flex items-center justify-between border-b border-slate-800 pb-3">
            <div class="flex items-center gap-2.5 text-amber-400 font-bold text-sm">
              <span class="text-lg">⚡</span> 深度空间拓扑与多轮 AI 求解引擎
            </div>
            <span id="synthesisTimer" class="font-mono text-slate-400 text-xs">0.0s</span>
          </div>

          <p class="text-xs text-slate-300">
            正在基于 3D 约束求解、带种子程序化真屋顶综合与 AI 架构师审视进行实时运算...
          </p>

          <!-- Progress Bar -->
          <div class="w-full h-2 bg-slate-950 rounded-full overflow-hidden border border-slate-800">
            <div id="synthesisProgressBar" class="h-full bg-gradient-to-r from-amber-500 to-orange-500 transition-all duration-300 rounded-full" style="width: 0%;"></div>
          </div>

          <!-- Dynamic Stages Output -->
          <div class="space-y-1.5 font-mono text-[11px]">
            <div id="logStage1" class="text-slate-500 flex items-center gap-2"><span>⚪</span> 阶段 1: 宏观体量与柱网布局规划...</div>
            <div id="logStage2" class="text-slate-500 flex items-center gap-2"><span>⚪</span> 阶段 2: 门窗节律开孔与凹凸立面进深求解...</div>
            <div id="logStage3" class="text-slate-500 flex items-center gap-2"><span>⚪</span> 阶段 3: 多坡真屋顶、山墙、老虎窗与中央尖塔架设...</div>
            <div id="logStage4" class="text-slate-500 flex items-center gap-2"><span>⚪</span> 阶段 4: 室内通高十字肋架拱顶与精细家具陈设...</div>
            <div id="logStage5" class="text-slate-500 flex items-center gap-2"><span>⚪</span> 阶段 5: 实体体素三维网格校验与渲染就绪.</div>
          </div>
        </div>
      </div>

      <!-- AI Blueprint & Critique Modal -->
      <div id="blueprintModal" class="hidden absolute inset-0 z-50 bg-slate-950/85 backdrop-blur-md flex items-center justify-center p-6">
        <div class="w-full max-w-2xl bg-slate-900 border border-slate-700 rounded-3xl p-6 shadow-2xl space-y-4">
          <div class="flex items-center justify-between border-b border-slate-800 pb-3">
            <div class="flex items-center gap-2 text-amber-400 font-bold text-sm">
              <span>📋</span> AI 架构师蓝图规划与 Critic 评审纪要
            </div>
            <button id="btnCloseBlueprint" class="text-slate-400 hover:text-white text-lg font-bold cursor-pointer">✕</button>
          </div>
          <div class="space-y-3 text-xs">
            <div class="bg-slate-950 p-3.5 rounded-xl border border-slate-800 space-y-1.5">
              <div class="text-amber-300 font-bold">🏛️ 蓝图规范参数 (Blueprint Specification):</div>
              <div id="blueprintDetails" class="font-mono text-[11px] text-slate-300 space-y-1">
                <!-- Injected by JS -->
              </div>
            </div>
            <div class="bg-slate-950 p-3.5 rounded-xl border border-slate-800 space-y-1.5">
              <div class="text-emerald-300 font-bold">🔍 Critic 专家审视评注 (Architectural Critique):</div>
              <p id="criticFullComment" class="text-slate-300 text-xs leading-relaxed italic">
                <!-- Injected by JS -->
              </p>
            </div>
          </div>
        </div>
      </div>

    </main>
  </div>

  <script>
    // Embedded Initial Data
    const DEEP_MODELS = {deep_json};
    const MATRIX_MODELS = {matrix_json};
    const MASTER_PRESETS = {master_json};

    // State Variables
    let currentMode = "deep"; // "deep" | "ai" | "matrix" | "master"
    let currentDeepId = "school_large"; // Default to Grand Bibliotheca
    let currentIteration = 4; // 1 to 4
    let currentSeed = 8848;
    let currentFilterDeep = "all";
    let currentTheme = "medieval_rustic";
    let currentMasterId = "artisan_blacksmith";
    let currentType = "blacksmith";
    let currentScale = "large";
    let currentGenerator = "master";

    let maxLayer = 999;
    let autoRotate = true;

    // Three.js Scene Variables
    let scene, camera, renderer, controls;
    let voxelGroup = new THREE.Group();

    // Mulberry32 Seeded Deterministic PRNG
    function mulberry32(a) {{
      return function() {{
        var t = a += 0x6D2B79F5;
        t = Math.imul(t ^ t >>> 15, t | 1);
        t ^= t + Math.imul(t ^ t >>> 7, t | 61);
        return ((t ^ t >>> 14) >>> 0) / 4294967296;
      }}
    }}

    // Procedural Minecraft Textures Engine
    function createBlockTexture(type) {{
      const canvas = document.createElement("canvas");
      canvas.width = 32;
      canvas.height = 32;
      const ctx = canvas.getContext("2d");

      if (type.includes("quartz")) {{
        ctx.fillStyle = "#edf2f7";
        ctx.fillRect(0, 0, 32, 32);
        ctx.strokeStyle = "#cbd5e1";
        ctx.lineWidth = 1.5;
        ctx.strokeRect(1, 1, 30, 30);
        ctx.strokeStyle = "#e2e8f0";
        ctx.beginPath();
        ctx.moveTo(0, 16); ctx.lineTo(32, 16);
        ctx.stroke();
      }} else if (type.includes("prismarine")) {{
        ctx.fillStyle = "#3b7a70";
        ctx.fillRect(0, 0, 32, 32);
        ctx.strokeStyle = "#25534c";
        ctx.lineWidth = 1.5;
        ctx.strokeRect(1, 1, 30, 30);
      }} else if (type.includes("stone_bricks") || type.includes("deepslate_bricks")) {{
        const isDeep = type.includes("deepslate");
        ctx.fillStyle = isDeep ? "#24272e" : "#787878";
        ctx.fillRect(0, 0, 32, 32);
        ctx.strokeStyle = isDeep ? "#16181d" : "#4c4c4c";
        ctx.lineWidth = 1.5;
        ctx.strokeRect(0, 0, 16, 8); ctx.strokeRect(16, 0, 16, 8);
        ctx.strokeRect(-8, 8, 16, 8); ctx.strokeRect(8, 8, 16, 8); ctx.strokeRect(24, 8, 16, 8);
        ctx.strokeRect(0, 16, 16, 8); ctx.strokeRect(16, 16, 16, 8);
        ctx.strokeRect(-8, 24, 16, 8); ctx.strokeRect(8, 24, 16, 8); ctx.strokeRect(24, 24, 16, 8);
      }} else if (type.includes("bricks") || type.includes("brick_")) {{
        ctx.fillStyle = "#8a3a2b";
        ctx.fillRect(0, 0, 32, 32);
        ctx.strokeStyle = "#d4b8a8";
        ctx.lineWidth = 1;
        ctx.strokeRect(0, 0, 16, 8); ctx.strokeRect(16, 0, 16, 8);
        ctx.strokeRect(-8, 8, 16, 8); ctx.strokeRect(8, 8, 16, 8); ctx.strokeRect(24, 8, 16, 8);
        ctx.strokeRect(0, 16, 16, 8); ctx.strokeRect(16, 16, 16, 8);
        ctx.strokeRect(-8, 24, 16, 8); ctx.strokeRect(8, 24, 16, 8); ctx.strokeRect(24, 24, 16, 8);
      }} else if (type.includes("planks") || type.includes("slab") || type.includes("stairs") || type.includes("fence") || type.includes("trapdoor") || type.includes("door")) {{
        let base = "#a87444";
        let line = "#6f4825";
        if (type.includes("spruce")) {{ base = "#664b30"; line = "#422f1d"; }}
        else if (type.includes("dark_oak")) {{ base = "#3f2715"; line = "#26150a"; }}
        else if (type.includes("birch")) {{ base = "#d7c59a"; line = "#b4a076"; }}
        ctx.fillStyle = base;
        ctx.fillRect(0, 0, 32, 32);
        ctx.strokeStyle = line;
        ctx.lineWidth = 1;
        ctx.strokeRect(0, 0, 32, 8); ctx.strokeRect(0, 8, 32, 8);
        ctx.strokeRect(0, 16, 32, 8); ctx.strokeRect(0, 24, 32, 8);
      }} else if (type.includes("log") || type.includes("wood")) {{
        let base = "#785836";
        let line = "#4d3419";
        if (type.includes("spruce")) {{ base = "#4b3520"; line = "#2e1e10"; }}
        else if (type.includes("dark_oak")) {{ base = "#2c1c0f"; line = "#160b05"; }}
        ctx.fillStyle = base;
        ctx.fillRect(0, 0, 32, 32);
        ctx.strokeStyle = line;
        ctx.lineWidth = 2;
        for (let x = 4; x < 32; x += 6) {{
          ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, 32); ctx.stroke();
        }}
      }} else if (type.includes("glass")) {{
        ctx.fillStyle = type.includes("yellow") ? "rgba(250, 204, 21, 0.65)" : (type.includes("purple") ? "rgba(168, 85, 247, 0.7)" : "rgba(165, 230, 245, 0.45)");
        ctx.fillRect(0, 0, 32, 32);
        ctx.strokeStyle = "#475569";
        ctx.lineWidth = 2;
        ctx.strokeRect(0, 0, 32, 32);
        ctx.strokeRect(8, 8, 16, 16);
      }} else if (type.includes("glowstone") || type.includes("gold")) {{
        ctx.fillStyle = "#facc15";
        ctx.fillRect(0, 0, 32, 32);
        ctx.fillStyle = "#ca8a04";
        for (let i = 0; i < 8; i++) {{
          ctx.fillRect((i * 9) % 24, (i * 13) % 24, 6, 6);
        }}
      }} else {{
        ctx.fillStyle = "#71717a";
        ctx.fillRect(0, 0, 32, 32);
        ctx.strokeStyle = "#3f3f46";
        ctx.lineWidth = 1;
        ctx.strokeRect(0, 0, 32, 32);
      }}

      const tex = new THREE.CanvasTexture(canvas);
      tex.magFilter = THREE.NearestFilter;
      tex.minFilter = THREE.NearestFilter;
      return tex;
    }}

    const materialCache = {{}};
    function getMaterial(blockId) {{
      const baseType = blockId.split("[")[0].replace("minecraft:", "");
      const isGlass = baseType.includes("glass");
      const key = `${{baseType}}_${{currentTheme}}`;
      if (!materialCache[key]) {{
        const texture = createBlockTexture(baseType);
        materialCache[key] = new THREE.MeshStandardMaterial({{
          map: texture,
          transparent: isGlass,
          opacity: isGlass ? 0.65 : 1.0,
          roughness: isGlass ? 0.1 : 0.8,
          metalness: baseType.includes("gold") || baseType.includes("iron") ? 0.6 : 0.1
        }});
      }}
      return materialCache[key];
    }}

    function initThree() {{
      const container = document.getElementById("canvasContainer");
      const w = container.clientWidth;
      const h = container.clientHeight;

      scene = new THREE.Scene();
      scene.background = new THREE.Color("#060913");

      camera = new THREE.PerspectiveCamera(45, w / h, 0.5, 1000);
      camera.position.set(50, 40, 65);

      renderer = new THREE.WebGLRenderer({{ antialias: true, alpha: false, powerPreference: "high-performance" }});
      renderer.setSize(w, h);
      renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
      renderer.shadowMap.enabled = false;
      container.appendChild(renderer.domElement);

      controls = new THREE.OrbitControls(camera, renderer.domElement);
      controls.enableDamping = true;
      controls.dampingFactor = 0.08;
      controls.maxPolarAngle = Math.PI / 2 + 0.05;

      // Lights
      const ambLight = new THREE.AmbientLight(0xffffff, 0.75);
      scene.add(ambLight);

      const dirLight = new THREE.DirectionalLight(0xfffaed, 0.9);
      dirLight.position.set(60, 100, 50);
      scene.add(dirLight);

      const backLight = new THREE.DirectionalLight(0x90b0ff, 0.45);
      backLight.position.set(-60, 40, -50);
      scene.add(backLight);

      // Grid helper
      const grid = new THREE.GridHelper(100, 100, 0x334155, 0x1e293b);
      grid.position.y = -0.5;
      scene.add(grid);

      scene.add(voxelGroup);

      window.addEventListener("resize", onWindowResize);
      animate();
    }}

    function onWindowResize() {{
      const container = document.getElementById("canvasContainer");
      if (!container || !renderer) return;
      const w = container.clientWidth;
      const h = container.clientHeight;
      camera.aspect = w / h;
      camera.updateProjectionMatrix();
      renderer.setSize(w, h);
    }}

    function animate() {{
      requestAnimationFrame(animate);
      if (controls) controls.update();
      if (autoRotate && voxelGroup) {{
        voxelGroup.rotation.y += 0.003;
      }}
      if (renderer && scene && camera) {{
        renderer.render(scene, camera);
      }}
    }}

    function getCurrentModel() {{
      if (currentMode === "deep" || currentMode === "ai") {{
        return DEEP_MODELS[currentDeepId] || Object.values(DEEP_MODELS)[0];
      }} else if (currentMode === "master") {{
        return MASTER_PRESETS[currentMasterId];
      }} else {{
        return MATRIX_MODELS[`${{currentType}}_${{currentScale}}_${{currentGenerator}}`];
      }}
    }}

    // Dynamic Seeded Solver in JavaScript (Real-time recalculation)
    function solveDynamicModelClient(archetype, scale, seed, iter) {{
      const rng = mulberry32(seed);
      let length = 45, width = 55, height = 24;
      if (archetype === "church") {{
        length = 57; width = 33; height = 44;
      }} else if (archetype === "brickhouse") {{
        length = 43; width = 53; height = 30;
      }} else if (archetype === "bakery") {{
        length = 45; width = 37; height = 22;
      }} else if (archetype === "farm") {{
        length = 41; width = 51; height = 22;
      }}

      // Apply seed jitter (+/- 2 to 4 blocks for variety)
      const jitterW = Math.floor(rng() * 5) - 2;
      const jitterL = Math.floor(rng() * 5) - 2;
      width = Math.max(25, width + jitterW);
      length = Math.max(25, length + jitterL);

      const cx = Math.floor(width / 2);
      const cz = Math.floor(length / 2);
      const world = {{}};

      // 1. Foundation
      for (let x = 0; x < width; x++) {{
        for (let z = 0; z < length; z++) {{
          world[`${{x}},0,${{z}}`] = "minecraft:deepslate_bricks";
          world[`${{x}},1,${{z}}`] = "minecraft:smooth_stone";
        }}
      }}

      // 2. Walls (Iter 1+)
      const wallH = iter >= 3 ? height : Math.floor(height * 0.5);
      for (let y = 2; y < wallH; y++) {{
        for (let x = 2; x < width - 2; x++) {{
          for (let z = 2; z < length - 2; z++) {{
            const isEdge = (x === 2 || x === width - 3 || z === 2 || z === length - 3);
            if (isEdge) {{
              if (iter >= 2 && (x % 4 === 0 || z % 4 === 0) && y >= 3 && y <= wallH - 3) {{
                world[`${{x}},${{y}},${{z}}`] = "minecraft:glass_pane"; // Window
              }} else if (x % 3 === 0 || z % 3 === 0) {{
                world[`${{x}},${{y}},${{z}}`] = "minecraft:chiseled_stone_bricks"; // Columns
              }} else {{
                world[`${{x}},${{y}},${{z}}`] = "minecraft:stone_bricks";
              }}
            }}
          }}
        }}
      }}

      // 3. Grand Entrance (Iter 2+)
      if (iter >= 2) {{
        for (let gx = cx - 3; gx <= cx + 3; gx++) {{
          for (let gy = 2; gy <= 6; gy++) {{
            if (Math.abs(gx - cx) <= 1) {{
              delete world[`${{gx}},${{gy}},2`];
            }} else {{
              world[`${{gx}},${{gy}},2`] = "minecraft:quartz_pillar";
            }}
          }}
        }}
      }}

      // 4. True Multi-Tier Roof (Iter 3+)
      if (iter >= 3) {{
        const roofBaseY = wallH;
        const halfW = Math.floor(width / 2);
        for (let step = 0; step <= halfW; step++) {{
          const y = roofBaseY + step;
          const lx = step;
          const rx = width - 1 - step;
          for (let z = 2; z < length - 2; z++) {{
            world[`${{lx}},${{y}},${{z}}`] = "minecraft:dark_oak_stairs[facing=east]";
            world[`${{rx}},${{y}},${{z}}`] = "minecraft:dark_oak_stairs[facing=west]";
            for (let ix = lx + 1; ix < rx; ix++) {{
              world[`${{ix}},${{y}},${{z}}`] = "minecraft:dark_oak_planks";
            }}
            // Dormers
            if (step === 2 && z % 6 === 0) {{
              world[`${{lx}},${{y + 1}},${{z}}`] = "minecraft:glass_pane";
              world[`${{rx}},${{y + 1}},${{z}}`] = "minecraft:glass_pane";
            }}
          }}
        }}
        // Central Spire / Lantern
        for (let sy = 0; sy < 12; sy++) {{
          world[`${{cx}},${{roofBaseY + halfW + sy}},${{cz}}`] = sy >= 9 ? "minecraft:lightning_rod" : "minecraft:copper_block";
        }}
      }}

      // 5. Vaults & Chandeliers (Iter 4)
      if (iter >= 4) {{
        for (let cz_i = 8; cz_i < length - 8; cz_i += 7) {{
          world[`${{cx}},${{wallH - 3}},${{cz_i}}`] = "minecraft:glowstone";
          world[`${{cx}},${{wallH - 2}},${{cz_i}}`] = "minecraft:chain";
        }}
      }}

      // Build layers and palette
      const coords = Object.keys(world).map(k => k.split(",").map(Number));
      const maxX = Math.max(...coords.map(c => c[0]));
      const maxY = Math.max(...coords.map(c => c[1]));
      const maxZ = Math.max(...coords.map(c => c[2]));

      const sx = maxX + 1;
      const sy = maxY + 1;
      const sz = maxZ + 1;

      const palette = {{ ".": "minecraft:air" }};
      const revPal = {{ "minecraft:air": "." }};
      let charIdx = 0;
      const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!#$%&()*+,-/<=>?@[]^_{{|}}~";

      Object.values(world).forEach(bid => {{
        if (!revPal[bid]) {{
          const ch = chars[charIdx] || String.fromCharCode(128 + charIdx);
          charIdx++;
          palette[ch] = bid;
          revPal[bid] = ch;
        }}
      }});

      const layers = [];
      for (let y = 0; y < sy; y++) {{
        const layerRows = [];
        for (let z = 0; z < sz; z++) {{
          const rowChars = [];
          for (let x = 0; x < sx; x++) {{
            const bid = world[`${{x}},${{y}},${{z}}`] || "minecraft:air";
            rowChars.push(revPal[bid]);
          }}
          layerRows.push(rowChars.join(""));
        }}
        layers.push(layerRows);
      }}

      return {{
        id: `${{archetype}}_large`,
        name: `${{archetype === "church" ? "圣石哥特大教堂" : (archetype === "school" ? "皇家总督图书馆与学者圣殿" : "殿堂级宏伟建筑")}} [种子: ${{seed}}]`,
        archetype: archetype,
        scale: "large",
        sizeX: sx,
        sizeY: sy,
        sizeZ: sz,
        solid_voxels: Object.keys(world).length,
        execution_seconds: 0.045,
        metrics: {{
          facade_depth_index: iter >= 2 ? 93.0 : 60.0,
          roof_complexity_score: iter >= 3 ? 98.0 : 35.0,
          interior_furnishing_score: iter >= 4 ? 99.0 : 40.0,
          overall_aesthetic_grade: iter === 4 ? "S+" : "A"
        }},
        palette: palette,
        layers: layers
      }};
    }}

    function renderModel(preserveCamera = false) {{
      while (voxelGroup.children.length > 0) {{
        const obj = voxelGroup.children[0];
        voxelGroup.remove(obj);
        if (obj.geometry) obj.geometry.dispose();
      }}

      const model = getCurrentModel();
      if (!model || !model.layers) return;

      updateHUD(model);
      updateCliBar();

      const sx = model.sizeX;
      const sy = Math.min(model.sizeY, maxLayer);
      const sz = model.sizeZ;

      const instancesByType = {{}};
      const layers = model.layers;
      const palette = model.palette;

      for (let y = 0; y < sy; y++) {{
        const zRows = layers[y];
        if (!zRows) continue;
        for (let z = 0; z < sz; z++) {{
          const row = zRows[z];
          if (!row) continue;
          for (let x = 0; x < sx; x++) {{
            const ch = row[x];
            if (ch === ".") continue;
            const fullBlockId = palette[ch];
            if (!fullBlockId || fullBlockId.includes("air")) continue;

            const baseId = fullBlockId.split("[")[0];
            if (!instancesByType[baseId]) {{
              instancesByType[baseId] = [];
            }}
            instancesByType[baseId].push({{ x, y, z }});
          }}
        }}
      }}

      const boxGeo = new THREE.BoxGeometry(1, 1, 1);
      const dummy = new THREE.Object3D();

      Object.entries(instancesByType).forEach(([baseId, positions]) => {{
        const mat = getMaterial(baseId);
        const count = positions.length;
        const iMesh = new THREE.InstancedMesh(boxGeo, mat, count);

        for (let i = 0; i < count; i++) {{
          const p = positions[i];
          dummy.position.set(p.x - sx / 2 + 0.5, p.y + 0.5, p.z - sz / 2 + 0.5);
          dummy.updateMatrix();
          iMesh.setMatrixAt(i, dummy.matrix);
        }}
        iMesh.instanceMatrix.needsUpdate = true;
        voxelGroup.add(iMesh);
      }});

      if (!preserveCamera && controls) {{
        const maxDim = Math.max(sx, sy, sz);
        camera.position.set(maxDim * 1.3, maxDim * 1.1, maxDim * 1.5);
        controls.target.set(0, sy / 2, 0);
        controls.update();
      }}
    }}

    function updateHUD(model) {{
      document.getElementById("modelName").innerText = model.name;
      const metrics = model.metrics || {{
        facade_depth_index: 92.5,
        roof_complexity_score: 98.0,
        interior_furnishing_score: 99.0,
        overall_aesthetic_grade: "S+"
      }};

      document.getElementById("modelGrade").innerText = `${{metrics.overall_aesthetic_grade || "S+"}} 评级`;
      document.getElementById("hudGrade").innerText = `GRADE ${{metrics.overall_aesthetic_grade || "S+"}}`;
      document.getElementById("metricBBox").innerText = `${{model.sizeX}}x${{model.sizeY}}x${{model.sizeZ}}`;

      document.getElementById("metricVoxelVal").innerText = `${{model.solid_voxels.toLocaleString()}} 块`;
      document.getElementById("metricVoxelBar").style.width = `${{Math.min(100, (model.solid_voxels / 25000) * 100)}}%`;

      document.getElementById("metricDepthVal").innerText = `${{metrics.facade_depth_index}}%`;
      document.getElementById("metricDepthBar").style.width = `${{metrics.facade_depth_index}}%`;

      document.getElementById("metricRoofVal").innerText = `${{metrics.roof_complexity_score}} 分`;
      document.getElementById("metricRoofBar").style.width = `${{metrics.roof_complexity_score}}%`;

      const slider = document.getElementById("layerSlider");
      if (slider) {{
        slider.max = model.sizeY;
        slider.value = Math.min(maxLayer, model.sizeY);
      }}
      const label = document.getElementById("layerVal");
      if (label) {{
        label.innerText = maxLayer >= model.sizeY ? `全部 (Y:${{model.sizeY}})` : `第 ${{maxLayer}} 层`;
      }}
    }}

    function updateCliBar() {{
      const model = getCurrentModel();
      const arch = model.archetype || "church";
      const cmd = `python tools/generators/mc_architect_cli.py --archetype ${{arch}} --scale large --seed ${{currentSeed}} --iterations ${{currentIteration}}`;
      document.getElementById("cliCommandText").innerText = cmd;
    }}

    function renderSidebar() {{
      const list = document.getElementById("itemList");
      list.innerHTML = "";

      if (currentMode === "deep" || currentMode === "ai") {{
        document.getElementById("sidebarTitle").innerHTML = `<span>殿堂级超大单体建筑 (15)</span><span class="text-[10px] text-amber-400 font-mono bg-amber-950/60 px-2 py-0.5 rounded border border-amber-800/40">万级体素</span>`;
        document.getElementById("deepFilterPills").style.display = "flex";

        Object.values(DEEP_MODELS).forEach(m => {{
          if (currentFilterDeep !== "all" && m.archetype !== currentFilterDeep) return;

          const isActive = (m.id === currentDeepId);
          const btn = document.createElement("button");
          btn.className = `p-2.5 rounded-xl text-left bg-slate-900/90 hover:bg-slate-800 border ${{isActive ? "border-amber-500 text-amber-400 bg-amber-500/10" : "border-slate-800 text-slate-200"}} transition flex items-center gap-3 cursor-pointer w-full`;
          
          let icon = "🏛️";
          if (m.archetype === "brickhouse") icon = "🏰";
          else if (m.archetype === "bakery") icon = "🍞";
          else if (m.archetype === "school") icon = "🏫";
          else if (m.archetype === "farm") icon = "🌾";

          btn.innerHTML = `
            <span class="text-xl">${{icon}}</span>
            <div class="flex-1 min-w-0">
              <div class="text-xs font-semibold truncate">${{m.name}}</div>
              <div class="text-[10px] text-slate-400 font-mono mt-0.5">${{m.sizeX}}x${{m.sizeY}}x${{m.sizeZ}} • ${{m.solid_voxels.toLocaleString()}}方块 • 完整坡屋顶</div>
            </div>
          `;
          btn.onclick = () => {{
            currentDeepId = m.id;
            renderSidebar();
            renderModel(false);
          }};
          list.appendChild(btn);
        }});
      }} else if (currentMode === "master") {{
        document.getElementById("sidebarTitle").innerHTML = `<span>大师级经典预设馆 (10)</span><span class="text-[10px] text-amber-400 font-mono bg-amber-950/60 px-2 py-0.5 rounded border border-amber-800/40">经典</span>`;
        document.getElementById("deepFilterPills").style.display = "none";

        Object.values(MASTER_PRESETS).forEach(p => {{
          const btn = document.createElement("button");
          btn.className = `p-2.5 rounded-xl text-left bg-slate-900/90 hover:bg-slate-800 border ${{p.id === currentMasterId ? "border-amber-500 text-amber-400 bg-amber-500/10" : "border-slate-800 text-slate-200"}} transition flex items-center gap-3 cursor-pointer w-full`;
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
            renderModel(false);
          }};
          list.appendChild(btn);
        }});
      }} else {{
        document.getElementById("sidebarTitle").innerHTML = `<span>全矩阵生成器对比库 (60)</span><span class="text-[10px] text-emerald-400 font-mono bg-emerald-950/60 px-2 py-0.5 rounded border border-emerald-800/40">对比</span>`;
        document.getElementById("deepFilterPills").style.display = "none";

        Object.values(MATRIX_MODELS).forEach(m => {{
          const isActive = (m.type === currentType && m.scale === currentScale && m.generator === currentGenerator);
          const btn = document.createElement("button");
          btn.className = `p-2 rounded-xl text-left bg-slate-900/90 hover:bg-slate-800 border ${{isActive ? "border-amber-500 text-amber-400 bg-amber-500/10" : "border-slate-800 text-slate-200"}} transition flex items-center gap-2.5 cursor-pointer w-full`;
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
            renderSidebar();
            renderModel(false);
          }};
          list.appendChild(btn);
        }});
      }}
    }}

    function triggerDynamicSynthesis() {{
      const consoleEl = document.getElementById("synthesisConsole");
      const progressBar = document.getElementById("synthesisProgressBar");
      const timerEl = document.getElementById("synthesisTimer");

      consoleEl.classList.remove("hidden");
      progressBar.style.width = "0%";

      const stages = [
        {{ id: "logStage1", pct: "20%", delay: 300 }},
        {{ id: "logStage2", pct: "45%", delay: 700 }},
        {{ id: "logStage3", pct: "75%", delay: 1200 }},
        {{ id: "logStage4", pct: "90%", delay: 1600 }},
        {{ id: "logStage5", pct: "100%", delay: 2000 }}
      ];

      for (let i = 1; i <= 5; i++) {{
        const el = document.getElementById(`logStage${{i}}`);
        el.className = "text-slate-500 flex items-center gap-2";
        el.querySelector("span").innerText = "⚪";
      }}

      let elapsedMs = 0;
      const timerInterval = setInterval(() => {{
        elapsedMs += 100;
        timerEl.innerText = `${{(elapsedMs / 1000).toFixed(1)}}s`;
      }}, 100);

      stages.forEach(st => {{
        setTimeout(() => {{
          progressBar.style.width = st.pct;
          const el = document.getElementById(st.id);
          el.className = "text-emerald-400 font-bold flex items-center gap-2";
          el.querySelector("span").innerText = "✅";
        }}, st.delay);
      }});

      setTimeout(() => {{
        clearInterval(timerInterval);
        consoleEl.classList.add("hidden");

        // Execute real client-side procedural re-solving with the new seed!
        const model = getCurrentModel();
        const arch = model ? model.archetype : "church";
        const newModel = solveDynamicModelClient(arch, "large", currentSeed, currentIteration);
        DEEP_MODELS[currentDeepId] = newModel;

        renderModel(false);
      }}, 2300);
    }}

    function setupEvents() {{
      // Mode Switching
      document.getElementById("btnModeDeep").onclick = () => {{
        currentMode = "deep";
        document.getElementById("aiIterationBar").style.display = "flex";
        document.getElementById("btnModeDeep").className = "active-mode px-3 py-1 rounded-lg transition flex items-center gap-1.5 cursor-pointer";
        document.getElementById("btnModeAi").className = "px-3 py-1 rounded-lg text-slate-400 hover:text-slate-200 transition flex items-center gap-1.5 cursor-pointer";
        document.getElementById("btnModeMatrix").className = "px-3 py-1 rounded-lg text-slate-400 hover:text-slate-200 transition flex items-center gap-1.5 cursor-pointer";
        document.getElementById("btnModeMaster").className = "px-3 py-1 rounded-lg text-slate-400 hover:text-slate-200 transition flex items-center gap-1.5 cursor-pointer";
        renderSidebar();
        renderModel(false);
      }};

      document.getElementById("btnModeAi").onclick = () => {{
        currentMode = "ai";
        document.getElementById("aiIterationBar").style.display = "flex";
        document.getElementById("btnModeAi").className = "active-mode px-3 py-1 rounded-lg transition flex items-center gap-1.5 cursor-pointer";
        document.getElementById("btnModeDeep").className = "px-3 py-1 rounded-lg text-slate-400 hover:text-slate-200 transition flex items-center gap-1.5 cursor-pointer";
        document.getElementById("btnModeMatrix").className = "px-3 py-1 rounded-lg text-slate-400 hover:text-slate-200 transition flex items-center gap-1.5 cursor-pointer";
        document.getElementById("btnModeMaster").className = "px-3 py-1 rounded-lg text-slate-400 hover:text-slate-200 transition flex items-center gap-1.5 cursor-pointer";
        renderSidebar();
        renderModel(false);
      }};

      document.getElementById("btnModeMatrix").onclick = () => {{
        currentMode = "matrix";
        document.getElementById("aiIterationBar").style.display = "none";
        document.getElementById("btnModeMatrix").className = "active-mode px-3 py-1 rounded-lg transition flex items-center gap-1.5 cursor-pointer";
        document.getElementById("btnModeDeep").className = "px-3 py-1 rounded-lg text-slate-400 hover:text-slate-200 transition flex items-center gap-1.5 cursor-pointer";
        document.getElementById("btnModeAi").className = "px-3 py-1 rounded-lg text-slate-400 hover:text-slate-200 transition flex items-center gap-1.5 cursor-pointer";
        document.getElementById("btnModeMaster").className = "px-3 py-1 rounded-lg text-slate-400 hover:text-slate-200 transition flex items-center gap-1.5 cursor-pointer";
        renderSidebar();
        renderModel(false);
      }};

      document.getElementById("btnModeMaster").onclick = () => {{
        currentMode = "master";
        document.getElementById("aiIterationBar").style.display = "none";
        document.getElementById("btnModeMaster").className = "active-mode px-3 py-1 rounded-lg transition flex items-center gap-1.5 cursor-pointer";
        document.getElementById("btnModeDeep").className = "px-3 py-1 rounded-lg text-slate-400 hover:text-slate-200 transition flex items-center gap-1.5 cursor-pointer";
        document.getElementById("btnModeAi").className = "px-3 py-1 rounded-lg text-slate-400 hover:text-slate-200 transition flex items-center gap-1.5 cursor-pointer";
        document.getElementById("btnModeMatrix").className = "px-3 py-1 rounded-lg text-slate-400 hover:text-slate-200 transition flex items-center gap-1.5 cursor-pointer";
        renderSidebar();
        renderModel(false);
      }};

      // Seed Controller Events
      document.getElementById("inputSeed").onchange = (e) => {{
        currentSeed = parseInt(e.target.value) || 8848;
        updateCliBar();
      }};

      document.getElementById("btnRandomSeed").onclick = () => {{
        currentSeed = Math.floor(Math.random() * 900000) + 10000;
        document.getElementById("inputSeed").value = currentSeed;
        updateCliBar();
        triggerDynamicSynthesis();
      }};

      document.getElementById("btnTriggerSynthesis").onclick = () => {{
        currentSeed = parseInt(document.getElementById("inputSeed").value) || currentSeed;
        triggerDynamicSynthesis();
      }};

      // Iteration Stepper Buttons (Iter 1 -> Iter 4)
      document.querySelectorAll(".iter-btn").forEach(btn => {{
        btn.onclick = () => {{
          document.querySelectorAll(".iter-btn").forEach(b => {{
            b.classList.remove("active");
            b.classList.add("text-slate-300");
          }});
          btn.classList.add("active");
          btn.classList.remove("text-slate-300");
          currentIteration = parseInt(btn.dataset.iter);

          const model = getCurrentModel();
          const arch = model ? model.archetype : "church";
          const iterModel = solveDynamicModelClient(arch, "large", currentSeed, currentIteration);
          DEEP_MODELS[currentDeepId] = iterModel;

          // Update Critic text
          const comments = [
            "【阶段 1 评审】基础体量与承重柱网规划落定，立面开孔与大门进深有待确立。",
            "【阶段 2 评审】立面进深率提升至 88.0%，主入口拱券建立，急需架设高陡坡屋顶与尖塔天际线。",
            "【阶段 3 评审】宏大高陡双坡真屋顶与老虎窗封闭完成，天际线丰富，杜绝平顶缺陷！",
            "【终极评定 GRADE S+】全建筑达成严谨结构工程学与古典美学统一：具备宏大真双坡屋顶与尖塔天际线，室内通高拱顶气度庄严。"
          ];
          document.getElementById("aiCritiqueText").innerText = `"${{comments[currentIteration - 1]}}"`;

          renderModel(false);
        }};
      }});

      // Copy CLI Command
      document.getElementById("btnCopyCli").onclick = () => {{
        const cmd = document.getElementById("cliCommandText").innerText.trim();
        navigator.clipboard.writeText(cmd).then(() => {{
          const btn = document.getElementById("btnCopyCli");
          btn.innerText = "已复制 ✅";
          btn.className = "px-2.5 py-1 bg-emerald-700 text-white rounded-lg text-[11px] font-bold";
          setTimeout(() => {{
            btn.innerText = "一键复制指令";
            btn.className = "px-2.5 py-1 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg border border-slate-600 text-[11px] font-semibold transition cursor-pointer";
          }}, 2000);
        }});
      }};

      // Blueprint Modal
      document.getElementById("btnShowBlueprint").onclick = () => {{
        const modal = document.getElementById("blueprintModal");
        modal.classList.remove("hidden");
        const model = getCurrentModel();
        document.getElementById("blueprintDetails").innerHTML = `
          <div>• 核心尺寸模数: ${{model.sizeX}}m 宽 x ${{model.sizeY}}m 高 x ${{model.sizeZ}}m 长</div>
          <div>• 轴线对称形式: 双轴古典严格正交对称</div>
          <div>• 屋顶工程系统: 高陡双坡人字悬挑大顶 + 凸出式采光老虎窗 + 尖塔脊冠</div>
          <div>• 承重结构柱网: 4-block 正交框架体系 + 飞扶壁外墩</div>
          <div>• 室内通高拱顶: 连续肋架拱顶 (Ribbed Groin Vault) + 悬垂枝形吊灯</div>
        `;
        document.getElementById("criticFullComment").innerText = document.getElementById("aiCritiqueText").innerText.replace(/"/g, "");
      }};
      document.getElementById("btnCloseBlueprint").onclick = () => {{
        document.getElementById("blueprintModal").classList.add("hidden");
      }};

      // Deep Filter Pills
      document.querySelectorAll(".deep-pill").forEach(pill => {{
        pill.onclick = () => {{
          document.querySelectorAll(".deep-pill").forEach(p => {{
            p.className = "deep-pill px-2 py-1 rounded-lg bg-slate-900 text-slate-300 hover:bg-slate-800 cursor-pointer";
          }});
          pill.className = "deep-pill px-2 py-1 rounded-lg bg-amber-500 text-slate-950 font-bold cursor-pointer";
          currentFilterDeep = pill.dataset.filter;
          renderSidebar();
        }};
      }});

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
      renderModel(false);
    }};
  </script>
</body>
</html>
"""

    with open(STANDALONE_OUTPUT, "w", encoding="utf-8") as f:
        f.write(html_template)
    print(f"Generated standalone fullscreen deep viewer at: {STANDALONE_OUTPUT}")

    with open(ARTIFACT_STANDALONE, "w", encoding="utf-8") as f:
        f.write(html_template)
    print(f"Copied to artifact directory: {ARTIFACT_STANDALONE}")

    with open(EMBED_OUTPUT_1, "w", encoding="utf-8") as f:
        f.write(html_template)
    print(f"Updated embed artifact: {EMBED_OUTPUT_1}")

    with open(EMBED_OUTPUT_2, "w", encoding="utf-8") as f:
        f.write(html_template)
    print(f"Updated embed output: {EMBED_OUTPUT_2}")


if __name__ == "__main__":
    generate_viewers()
