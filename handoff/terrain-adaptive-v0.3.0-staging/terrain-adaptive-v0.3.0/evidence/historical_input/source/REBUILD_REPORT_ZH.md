# 聚落规划重建交付说明

本文件及 `TEST_RESULTS_ZH.md` 描述本轮实际实现。原包中的 `SOURCE_BUNDLE.txt`、`SOURCE_INDEX.md`、`MANIFEST.json`、`DEPLOYMENT.md` 和原有 `output/` 报告保留为历史输入，**没有重新生成，不能作为新版源码快照、清单或本轮测试证据**。新版以真实 `src/`、`CHANGED_FILES.txt` 和本轮测试日志为准。

这是已完成离线 Java 实现/验证的候选版本，**不是已通过 Fabric 构建与游戏内验收的发布版**。所有此前被撤回的成功结论均不构成本次证据。

## 1. 实际调用链与问题定位

| 源文件 / 方法 | 原实现核实到的问题 | 本轮落点 |
|---|---|---|
| `core-planner/.../SettlementPlanner.plan` | 先固定入口—广场—山脊—回环，再按道路顺序安排地块；需求不能主导尺寸与用途 | 委托 `BoundedSettlementPlanner.plan`，先明确需求和预设再生成候选 |
| `parcel/PlotPlanner` / 原地块循环 | 固定默认尺寸；EXISTING_BUILDING 没有作为完整保护条件；入口不是预设真门口 | 生产路径不再使用旧贪心地块循环；保留公共类并修复显然的保护/地基失败处理 |
| `civil/EarthworkOptimizer.optimizePlotFoundation` | 无可行逐格标高区间时仍有成功形状的结果；偏重挖填平衡 | 显式 feasible/reason，逐格区间求交，区间内中位数最小化绝对挖填；无区间则失败 |
| `pathfinding/SlopeCostAStar.findPath` 与旧施工刷宽 | 对角、高差及 bridge/stair 标签不能证明全宽和最终角色通行；偶数宽度被多铺一列 | 原 A* 留给旧接口/测试基线，生产改为四邻接全宽平整走廊；不支持的桥/爬坡拒绝 |
| `llm-bridge/PlanningIntent` → `fabric-mod/.../TacticalMapScreen` | landmarkPlacement 在 UI 手工映射时丢失；模型原本只获得少量地形信息 | 统一 `toPlanRequest(seed)`；传用途、范围、邻近需求；提供高差、水/保护格、坡度及预设尺寸摘要 |
| `llm-bridge/AiBuildingArchitect` / 预设回退 | 全局提示词可能覆盖各地块用途；后选预设再旋转/裁剪；可能逐栋调用模型 | 锁定 `presetId`、朝向、实际尺寸；新路径直接返回 `PlannedBuilding`，不再逐栋调用模型 |
| `preset/BuildingPreset` 注册内容 | 部分预设在声明门口有实心方块，只有门口坐标不足以保证能进入 | 只修整声明门道：向内一格及向外到边界，地板+两格净空，不重写所有生成器 |
| `fabric-mod/.../WorldConstructor.execute` / 旧 AdaptiveBuildingPlacer | 客户端直接改世界；另行补根、堆楼梯/桥；施工与 IR 不是同一几何 | 统一 `PlanConstruction` 编辑程序；权威 ServerWorld/服务端线程；整份预检后写入 |
| `WorldTerrainScanner.scanRegion` | 旧固定高度窗口及不完整的结构识别 | 使用实际世界高度，保守自然材质白名单；未知/方块实体列保护；施工前重新扫描 |
| `simulation/SimulatedSettlementPipeline` / CLI / 渲染 | 旧仿真另建简化房屋，旧展示总分不能作为质量证据 | 共享编辑程序，输出状态与欠缺；SVG 使用唯一施工列；不再显示固定高分 |

实际新链路：`TacticalMapScreen` → `LlmStrategyManager`（选定一个意图来源，必要时离线回退）→ `PlanningIntent.toPlanRequest(42)` → `SettlementPlanner` → `BoundedSettlementPlanner` → `PlanningIR 0.2` → `PlanConstruction.prepare` → 单机集成服务器的 `WorldConstructor.execute`。仿真也执行同一个编辑程序。GUI 仍只做必要的映射、状态和服务端施工接线。

## 2. 所选算法：有界多起点、需求优先的贪心放置

没有引入联合优化框架、WFC、多智能体或新依赖。选择多起点贪心是为了能用现有预设和 HeightfieldMap 实现完整可施工链，并给每个探索步骤确切上限。它不声称全局最优，也不保证找到所有可行解。

### 最小需求模型

`SettlementPlanner.BuildingRequirement`：id、purpose、count、可选 presetId、min/maxWidth、min/maxDepth、heightLimit、placement、maxWaterDistance、nearPurpose、maxDistance。尺寸全部是 **旋转后的实际外接矩形**，不是允许裁剪的目标尺寸。

用途与预设 category/tags 匹配；四种朝向提前展开。无匹配预设则明确 `NO_PRESET_MATCHES_PURPOSE_SIZE_HEIGHT`。必须靠水的需求只接受干地且地块至水格曼哈顿距离不超过 maxWaterDistance；nearPurpose 为地块中心曼哈顿距离硬限制，先放引用用途，依赖环或引用未放下会产生欠缺理由。中心/高处/低处是软排序，不等于硬指定某个最高点。

旧调用者不传 requirements 时，按数量给 1 栋 government、1 栋 workshop，其余 residential（总量不足时按顺序截断）；旧 defaultWidth/defaultDepth 改作选择完整预设的最小尺寸，不再固定裁剪模板。显式 requirements 的总数是权威值；与 targetPlots 不同时有 warning。

### 候选和搜索

每次尝试选择一个可施工的外围入口（有显式 `[x,y,z]` 时必须保持原位及真实地面 Y），将该次布局的道路和地基统一到入口标高。先做**全宽道路可达性预筛**，其展开也计入预算；候选用 seed 决定的有限算术置换遍历格点和旋转预设，不随机到什么时候算什么时候。

每个需求单元从当前剩余预算获得有限份额，过滤边界、已有建筑、水/悬崖、坡度、地基逐格挖填、地块间距、道路退界、历史私人接入、河岸/邻近硬关系以及预设入口。保留有限 shortlist。逐个尝试四邻接 A*；每个中心点都检查完整正方形道路刷宽，路径不能切入任何已放地块。接入从预设真实门槛沿门朝向穿出到道路，明确为宽 1 的私人行人连接。

单次放置后更新地块/道路/私人入口占用，继续下一需求。不对已放建筑进行无界回溯，不用 LLM 逐个猜坐标。有限次尝试结束后选出最佳已验证布局。地图/seed/需求/预算相同，几何及失败解释确定；时间戳和耗时除外。

### 排序与预算

不混合不同单位成任意加权总分，也没有“高分抵消硬违规”。候选按字典序：placement 偏好（中心为 2 倍曼哈顿距离；ridge/valley 为原地面 Y 的相反/正序；河岸为格距离）→ 绝对挖填体积（格³）→ 至入口曼哈顿距离（格）→ seed 派生确定性 tie-break。不同 layout 依次比较已放需求单元数、覆盖需求组数、全部唯一施工列绝对挖填、公共道路面积。排序表达明确优先级，不是经验权重拟合。

默认 `attempts=3, candidateChecks=24000, pathExpanded=180000, shortlist=8, candidateStride=2`。硬上限：地图每轴 256、总请求 32、道路宽 5、attempts 8、候选 200000、展开 2000000、shortlist 32、stride 16、单维预设 32、挖填/间距/退界参数不超过 16；非法值直接 INVALID_REQUEST。编辑程序最多 1000000 个唯一方块编辑。默认 stride=2 会漏过仅在奇数偏移位置可行的布局；可显式设 1，并仍受候选预算约束。

设候选预算 C、路径预算 E、地图格数 N、最大预设边长 F、退界 S、河岸距离 R、已放建筑数 P、宽 w、shortlist K。候选测试上界约 `O(C * ((F+2S)^2 + F^2 + (F+2R)^2 + P + K log K))`；河岸项只对相应需求执行。路径总展开至多 E，每步检查 `O(w²+P)` 并有优先队列 `log N`；每次 A* 初始化 O(N)，启动次数至多 attempts×请求数×K。最终施工列/编辑程序还受地图尺寸及 100 万编辑上限约束。常数操作也有有限输入界限；这些是操作/数据规模边界，**不是毫秒响应保证**。

## 3. 一致的坐标、入口、地基和 IR

世界 x/z 为整数格；矩形 polygon 两端均包含，宽度 `max-min+1`。所有道路/地基/门槛 Y 都是**顶层实心方块 Y**；角色脚部空气在 Y+1，上方 Y+2 也必须净空。模板原点 Y=baseElevation，y=0 为地板层。道路刷宽偏移 `low=-width/2`、`high=low+width-1`，偶数宽也只铺 width 格。

`groundColumns` 是施工、土方统计和 SVG 的唯一列清单：originalY/targetY/clearToY/kind，不允许重复列。所有列满足各自逐格挖填限制，保护格禁止进入。地基没有无预算的外伸挡墙/深根；模板空地板保留承重板。生产单标高布局直接验证该固定标高可行；公共 EarthworkOptimizer 则独立提供逐格可行区间与中位数解，避免对旧调用者继续报假成功。

`transportNetwork.corridors` 保存原设计全宽中心线路径，nodes/edges 改为**实际铺装和接入格点的四邻接图**，不会出现两条路相交而拓扑不连。图上的 edge.width=1 描述格邻接，不应再拿它当整条主路宽去刷一遍。真实门槛位于图上，entrance.path 保存合法门口接入，connectedEdgeId 必须 incident 于门口。

`PlanConstruction.prepare` 先解析锁定预设、维度/门口/清单并形成去重编辑程序，再由消费者执行。Fabric 还会完整重扫、检查原地形未变、支撑层不是洞、世界高度和所有待改格可替换、全部材料能解析，然后才写方块；不代表事务性回滚或物理引擎验证。旧 IR 默认 UNVALIDATED，缺少新清单时拒绝施工，不猜测/补造。

## 4. 模型调用、用途和消费者边界

核心为 0 模型调用。桥接保留既有意图来源选择和回退，不新增规划模型轮数，新增字段传给真正的 Java 求解器；测试覆盖完整 `Intent → Request`、本机 HTTP prompt/schema、landmark 影响及恶意超范围数值。地形摘要不是逐格全图，也不能让模型证明几何可行；几何验证全部在本地完成。

新锁定预设路径不再向 AiBuildingArchitect 发逐栋模型请求，不让“铁匠铺”等全局关键词覆盖每块地的用途。不调用收费 API，不改任何密钥配置。离线中文关键词回退是有限启发式，**不是完整数量/否定句语义解析器**；精确数量保证以传入的结构化 requirements 为准。历史 roadStyle/paletteTag 仍不是新道路优化自由度，本版统一可靠的石质路/板，不宣称样式字段全部新增生效。已有本地 CLI/远程连接耗时不属于核心搜索次数预算，本轮未验证真实供应商或 agy 程序。

## 5. 状态、兼容性和明确限制

COMPLETE=请求全部满足；PARTIAL=只返回满足全部几何硬约束的子集，并列欠缺；INFEASIBLE=该有限预算、入口和单标高模型下没找到可行建筑，**不等于数学上的无解证明**；INVALID_REQUEST=参数无效并给 warning。消费 PARTIAL 时 GUI 按部分方案显示，不自动宣传完成目标。失败时先读 unmetRequirements.reason；只能由调用者明确改数量、尺寸、预算、入口或选区重新规划，不能静默放宽挖填/保护条件。

当前只支持干地水平整平道路/板式基础。没有跨水桥、爬坡台阶、不同标高分区、坡面建筑、跨高度真正梯级交通；因此陡峭场景可能少建或拒绝，随机和少量候选也可能漏掉可行解。私人入口固定宽 1，不是所有道路都降宽。

单机集成服务器接线已实现，远程多人服务器不允许客户端自行施工；未实现网络请求/权限协议。Minecraft、Fabric 类型解析及游戏内验证均未完成。Scanner 无法区分玩家放置的天然石块和自然石头；自然木/叶也不能可靠识别所有木屋，实际保护范围仍应由使用者和后续游戏测试确认。HeightfieldMap 不含完整地下空洞/悬空结构，Fabric 支撑预检会进一步拒绝不能施工的列；纯高度场通过不代表所有世界物理成立。

预设门道局部修整可能改变原门饰，但不裁剪主体；模板内部其它房间/楼层、家具的可达性未保证。相同几何的所有朝向门槛检查不等于建筑所有内部布局都合格。原 Python 建筑实验、密钥配置及无关 GUI/单体生成器没有整体重写。

**回退方式：**保留原 ZIP 或用 `git apply -R` 回退本次补丁；测试基线仅用于对照，不能直接充当“安全自动降级”的施工回退。重要存档应等正式 Fabric 与游戏验收后使用。

## 6. 使用示例

正常依赖环境：`bash gradlew :core-planner:test :llm-bridge:test :fabric-mod:compileJava --no-daemon`。独立验证入口和本轮阻塞详情见 `TEST_RESULTS_ZH.md`。

```java
var request = new SettlementPlanner.PlanRequest();
request.seed = 42;
var homes = new SettlementPlanner.BuildingRequirement();
homes.id = "homes"; homes.purpose = "residential"; homes.count = 3;
var forge = new SettlementPlanner.BuildingRequirement();
forge.id = "forge"; forge.purpose = "workshop"; forge.count = 1;
forge.minWidth = 7; forge.maxWidth = 20;
forge.minDepth = 7; forge.maxDepth = 20;
request.requirements = new java.util.ArrayList<>(java.util.List.of(homes, forge));
request.targetPlots = 4;
request.searchBudget.candidateChecks = 24000;
request.searchBudget.pathExpanded = 180000;
var plan = SettlementPlanner.plan(heightfield, request);
System.out.println(plan.status);
for (var missing : plan.unmetRequirements)
    System.out.println(missing.requirementId + ": " + missing.reason);
// 只有明确接受 COMPLETE/PARTIAL 后才准备施工；实际世界施工必须走服务器入口。
if ("COMPLETE".equals(plan.status)) {
    var edits = org.mcsettlement.planner.civil.PlanConstruction.prepare(plan, request.settlementStyle);
}
```

## 7. 文件与证据

`planner-rebuilt-source.zip`：完整源码及本说明/测试结果/修改列表，保留项目内原目录结构；`planner-rebuilt.patch`：相对用户原 ZIP 的 unified diff；`planner-rebuilt-test-evidence.zip`：最终原始日志、13×2 场景 IR、CSV、编译版本、基线保真、失败历史和冒烟产物。`package-verification.json` 是独立的真实补丁应用及源码哈希一致性记录。
