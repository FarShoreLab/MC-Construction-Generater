# 聚落规划下一阶段实现与验证报告

日期：2026-09-22

## 1. 结论

已按 `PROMPT_ZH.md` 完成这一轮源码实现，核心目标不是新增一套平行规划器，而是在现有 `ExpertSettlementPlanner` / `TerrainRoadRouter` / `PavementGrades` / `OrganicRoadNetwork` / `PlanningIR.groundColumns` 调用链上增量修改。

本轮完成：

- 普通陆地道路恢复“贴地优先”，不再因为 `allowBridges=true` 获得全局 `MAX_PILE_HEIGHT` 高架权限；
- 陆地桥改为短跨度地形例外，并加入连续架空硬上限、禁止连续架空中转弯、离地高度成本和最终审计；
- 新增独立 `siteSeed`，可先锁定自动建筑选址，再滚动 `planSeed` 只重算道路；现有人工 pins 继续是硬约束，可添加/更新/删除；
- 完整方案 `REJECTED` 时自动滚动 `planSeed`，有严格 `maxPlanAttempts` 上限，并保留每轮 seed / status / reasons；
- 新增地形自适应农田、牧场 flood region，不使用矩形作为占地真源；最终施工仍只认 `PlanningIR.groundColumns`；
- 修复 `buildingRepulsion=0` 仍被宏观 maximin 主动拉散的问题；新增 village / town / city 三种布局模式；
- city 模式在相同建筑数量下提高密度与街区连续倾向，并改变自动 preset mix，使平均高度、平均体量和高度层级更高；
- 预览 UI / HTTP 参数 / Java API / 施工清单 / 审计 / 回归测试均已接入。

## 2. RoadWeaver 独立研究与取舍

参考：

- RoadWeaver 官方仓库：<https://github.com/shiroha-233/RoadWeaver>
- 大跨度桥梁连接异常 issue #63：<https://github.com/shiroha-233/RoadWeaver/issues/63>

RoadWeaver 官方 README 把道路问题拆成了几层：地形感知寻路、道路曲线处理、桥隧、挖填/路基、以及道路网络拓扑。其地形寻路会考虑高度、坡度/危险区域、生物群系与地面稳定性；道路可以做 Bézier 平滑；桥隧与普通路基是明确的不同情形；路基仅在道路高于原地形时填充并与周围高度场平滑过渡；网络层还提供多种连接算法。

与本项目对照后，本轮只吸收最小必要思想：

| 层 | 本项目已有能力 | 本轮采用 | 本轮不采用 |
|---|---|---|---|
| 网络拓扑 | `OrganicRoadNetwork` 已有锁定选址、plaza/hub、有限 MST 目标、冗余边、语义连接和原子回退 | 保留现有拓扑，不另建 KNN/Delaunay/RNG 系统 | 不引入另一套网络算法，避免双真源 |
| 寻路 | `TerrainRoadRouter` 已是带高度状态、完整路宽 brush 检查、预算有界的 A* | 增加贴地偏差成本、短陆桥状态、连续架空长度状态、planSeed 道路 tie-break | 不复制 RoadWeaver 寻路实现 |
| 标高 | `PavementGrades` 已承担整条路面的可施工标高求解 | 在求解后增加陆地连续架空硬验证 | 不把标高求解搬回路由器 |
| 曲线 | 现有 8/12/16 方向栅格道路是施工真几何 | 维持现有方向体系 | 不做 Bézier 后处理；否则可能破坏已验证的完整路宽、坡度、障碍和施工列 |
| 桥梁 | 旧逻辑把所有陆地都放宽到 `MAX_PILE_HEIGHT` | 只允许 derived cliff 或局部两侧有 rim 的短沟谷例外；连续跨度有独立上限 | 不让 `allowBridges` 成为全局高架许可 |
| 可视化 | 预览已展示道路/施工/对比 | 增加陆地架空、道路 clearance、农田牧场、retry history | 不做与施工脱离的装饰性 overlay |

RoadWeaver 公开 issue 中也有“大跨度桥梁连接异常”的实际案例。因此本项目把陆地桥的**连续跨度**做成可配置硬约束，而不是只靠软成本。

## 3. 主要实现

### 3.1 道路贴地与短陆桥

涉及：

- `RoadTerrain.java`
- `TerrainRoadRouter.java`
- `PavementGrades.java`
- `ExpertTerrainAudit.java`

根因是旧 `RoadTerrain.upper()`：只要专家模式允许桥梁，所有干燥陆地都允许上抬 `MAX_PILE_HEIGHT`；后续又会把超过普通 fill 上限的列标记成 bridge，于是 A* 可以把高架当成普通可行解。

现在：

1. 普通干燥地面 `upper = surface + roadMaxFill`；
2. 只有 `landBridgeEligible()` 才开放 `MAX_PILE_HEIGHT`：derived cliff 或短半径内具有两侧高 rim 的局部干沟/凹地；
3. A* 状态加入 `landRun`，连续干地架空不得超过 `maxLandBridgeSpan`；连续架空期间禁止转弯；
4. 道路代价增加地形偏离项和超过普通 fill 的 clearance 二次惩罚；
5. `PavementGrades` 和最终 audit 再次验证连续干地 deck；
6. 暴露 `maxRoadClearance`、`meanRoadClearance`、`maxLandAirborneRun` 指标。

水桥继续使用原 `maxBridgeSpan`，陆地短桥使用独立 `maxLandBridgeSpan`，两者不再混用。

### 3.2 锁定选址后滚道路

新增 `ExpertSettings.siteSeed`：

- 未指定：`siteSeed` 跟随 `planSeed`，保持原有“整个方案一起 roll”的语义；
- 指定：建筑候选与宏观选址只使用固定 `siteSeed`，道路仍使用 `planSeed`；
- 当 `siteSeed` 固定后，禁止道路失败触发会移动自动建筑的 site fallback/repair；
- `TerrainRoadRouter` 增加很小的确定性 `roadRollBias(planSeed, x, z, heading)`，使同一选址下不同 planSeed 确实可以产生不同等价道路选择。

预览新增“锁定当前选址 / 解除锁定”。人工 pins 沿用现有硬约束系统，列表删除仍是唯一删除入口，没有新建平行 pin 系统。

### 3.3 REJECTED 自动 retry

`SettlementPlanner.plan()` 在专家模式外包一层严格重试：

- 第 1 轮使用用户请求 seed；
- 仅当完整结果为 `REJECTED` 时继续；
- 每轮 seed `requested + round - 1`；
- `COMPLETE` 或 `INVALID_REQUEST` 立即停止；
- 严格受 `maxPlanAttempts`（1–8）限制；
- 每轮记录 `round / planSeed / status / reasons`；
- 不修改调用方传入的 request；每轮仍受原单次搜索预算限制，最大总成本由 retry 上限显式封顶。

### 3.4 农田与牧场

新增 `TerrainLandUsePlanner.java`：

- 以已有建筑/道路施工列为障碍，并留 2 格 buffer；
- 分别寻找 farmland / pasture 起点；
- 用有界 priority flood 生长；
- 代价包含与聚落中心的距离、坡度/标高变化和 seeded ragged noise；
- farmland 坡度更严格，pasture 更宽松；
- 只接受干地且无障碍；
- 区域必须至少 12 格；
- 若极端情况下恰好形成实心矩形，会确定性挖去一个边界 notch；
- `LandUseArea` 保存真实 cell 集合和边界 cell；
- 对应 `GroundColumn` 的 `targetY == originalY == terrain surface`，因此是地形跟随面，而不是平台化矩形；
- 施工材质：farmland -> `minecraft:farmland[moisture=7]`，pasture -> `minecraft:grass_block`。

它不生成作物生长、动物实体或经营模拟；本轮只解决规划占地和施工地表。

### 3.5 village / town / city 与 `buildingRepulsion=0`

旧 `regionalAnchor()` 的 maximin 会无条件偏向远离已有建筑的位置，所以把局部 `buildingRepulsion` 设为 0 也无法真正关闭宏观分散。

现在宏观 spread 也乘 `buildingRepulsion`：0 时不再有该 maximin 拉散项。硬 `minBBoxCoverage` / `minMinorAxisRatio` 仍是独立的用户约束，不会被 `buildingRepulsion=0` 隐式关闭。

布局模式：

- `village`：较大 spacing reference、最强 spread；自动 preset 倾向较小/较低体量；
- `town`：中间态；
- `city`：较小 spacing reference、较强 centroid continuity、plot spacing 可收紧 1 格；自动 preset 倾向更高/更大体量，并保留高度层级。

目标建筑数不随 mode 增加；因此 city 的差异来自布局密度、连续性和建筑体量分布，而不是“简单多放建筑”。显式 requirements 和 `single` preset 不会被模式偷偷替换。

## 4. UI / API / IR / 施工接口

新增/暴露：

- `settlementMode = village | town | city`
- `siteSeed = follow | integer`
- `maxPlanAttempts = 1..8`
- `maxLandBridgeSpan = 2..32`

`PlanningIR.SitePlanning` 新增：

- requested/effective plan seed
- effective site seed
- retry history
- settlement mode
- max/mean road clearance
- max land airborne run
- farmland/pasture cell count
- mean/max building height
- mean building volume
- building height stddev

`PlanningIR.landUses` 只是区域语义/显示 provenance；真实施工仍来自 `groundColumns`，没有第二套施工真源。

## 5. 新增回归测试

`NextAiPlanningMain` 覆盖 6 类验收：

1. 普通陆地不因 `allowBridges` 获得超 fill 高度；完整平地方案道路 clearance 不超过 fill envelope；短沟谷仍可进入陆桥例外；
2. 连续干地架空长度检测，超过 `maxLandBridgeSpan` 必须拒绝；
3. 相同 `siteSeed`、不同 `planSeed` 下自动建筑 origin / base elevation / preset / facing 不变；
4. 不可满足方案严格在 retry cap 停止，seed 顺序确定，并保留每轮拒绝原因；
5. farmland 和 pasture 都是非矩形、地形跟随、真实边界 cell；
6. village/town/city 的 density reference 有序；city 完整方案平均高度、平均体量、高度标准差均高于 village，并且近邻均值不更疏。

`LandBridgeMain` 的人工沟谷 fixture 也缩短为符合新“短陆桥”政策的 8 格。

## 6. 验证结果

### 6.1 官方验收命令

已实际执行：

```bash
python3 tools/build_offline.py --test --loopback-tests --evidence build/final-verification/official
```

结果：**exit 1，编译前即被依赖检查阻断**：

```text
ERROR: Local Gson 2.10.1 not found. Set GSON_JAR to a real local Gson jar. Nothing was downloaded.
```

这不是测试失败后的伪装“通过”，因此本报告不把官方验收标记为 PASS。项目脚本明确要求真实本地 Gson 2.10.1，并禁止下载依赖；当前执行环境没有该 jar，容器也没有可用外网下载通道。

Maven Central 可确认真实制品存在：
<https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar>

在有 jar 的机器上应运行：

```bash
GSON_JAR=/absolute/path/to/gson-2.10.1.jar \
python3 tools/build_offline.py --test --loopback-tests
```

### 6.2 聚焦诊断测试

为避免因单个外部依赖完全失去源码级验证，另用**仅位于 `/tmp`、不进入交付包**的最小 Gson 编译替身和由真实 preset JSON 转写出的测试 bootstrap 做了诊断运行。它不能替代官方 Gson 验收，只用于验证本轮 Java 逻辑/类型和新回归。

结果：

```text
PASS ordinary_land_stays_within_fill_and_short_ravine_is_exception
PASS continuous_dry_airborne_run_has_hard_cap
PASS locked_site_seed_survives_road_roll
PASS rejected_plan_retries_stop_at_strict_cap
PASS farmland_and_pasture_are_terrain_following_non_rectangles
PASS settlement_modes_change_density_target_and_height_mix
RESULT 6 passed; 0 failed
```

另外：

- core + llm-bridge main sources：test-only Gson signature substitute 下 `javac --release 21` type-check PASS；
- `git diff --check`：PASS；
- `python3 -m py_compile`（preview/API/browser verifier 相关脚本）：PASS；
- `node --check`（`preview.html` 主 inline JS）：PASS；
- 新 API 参数 parser 正/反例：PASS。

### 6.3 API / 浏览器验证脚本

按要求实际尝试了现有 E2E：

```bash
python3 tools/verify_site_expert.py --base http://127.0.0.1:18767
python3 tools/verify_site_expert_browser.py --base http://127.0.0.1:18767
```

由于官方 build 在 Gson 检查处就停止，`core-planner/build/classes/java/main` 没有生成：

- API verifier 第一条真实 plan 请求返回 400：`Java classes not found: .../core-planner/build/classes/java/main`，exit 1；
- browser verifier 能加载页面和 terrain schema，但初始 plan POST 同样返回 400，无法进入 ready state；为避免空等，验证进程在 30 秒上限被终止，exit 124；
- 静态浏览器 JS syntax 与 API 参数边界验证均已单独通过。

因此当前剩余唯一阻断是：**缺少真实 Gson 2.10.1 导致官方 Java backend 无法构建，进而阻断真实 API/浏览器 E2E。**

## 7. 剩余限制 / 风险

1. 陆地短桥 eligibility 使用局部 E-W / N-S 两侧 rim 启发式；它故意保守，不是任意方向峡谷/隧道的完整地貌理解器。斜向狭沟可能选择绕行而不是架桥。
2. 连续陆桥硬上限比旧逻辑严格；某些旧 fixture/地图若依赖长陆地高架会变为 REJECTED 或绕行，这是预期行为。
3. retry 的总工作量上限约为“单次预算 × maxPlanAttempts”；单轮预算没有被放大，但用户把 retry 设置为 8 时总 CPU 时间自然会上升。
4. city 模式只改变**自动 palette**的选择顺序；显式 requirements / single preset 保持精确，不会为了“城市感”擅自改用户指定建筑。
5. 农田/牧场目前是规划与施工表面，不包含作物、围栏、动物实体、田埂道路或生产模拟。
6. 真实 Gson/Fabric/Minecraft runtime 和浏览器 E2E 仍需在具备项目声明依赖的环境中补跑；本环境无法诚实替代这一项。

## 8. 关键改动文件

```text
core-planner/src/main/java/org/mcsettlement/planner/
  ExpertSettings.java
  SettlementPlanner.java
  ExpertSettlementPlanner.java
  TerrainRoadRouter.java
  RoadTerrain.java
  PavementGrades.java
  ExpertTerrainAudit.java
  SiteMetrics.java
  TerrainLandUsePlanner.java        [new]
  BoundedSettlementPlanner.java
  ir/PlanningIR.java
  civil/PlanConstruction.java
  preset/PresetPalette.java
  simulation/SimulatedSettlementPipeline.java
  simulation/SimulationApiRunner.java

core-planner/src/test/java/org/mcsettlement/planner/
  NextAiPlanningMain.java           [new]
  LandBridgeMain.java

tools/
  build_offline.py
  preview_server.py
  preview.html
```

本轮没有复制 RoadWeaver 源码。
