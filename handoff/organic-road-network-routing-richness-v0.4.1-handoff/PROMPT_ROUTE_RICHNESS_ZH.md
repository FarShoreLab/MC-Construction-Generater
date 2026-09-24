# Organic Road Network v0.4.1：路线丰富度改进交接 Prompt

你正在接手 `organic-road-network-v0.4.0-footprint-presets` 的路线规划改进。用户反馈：当前路线规划经常退化为长直线，整体丰富度不足；附带的 `evidence/reproduction/user-route-straight-reference.png` 只是现象证据，不是额外指令。

## 目标

改进真实路线中心线的形态丰富度，使主干/收集道路在地形、障碍、坡度和建筑连接约束允许时呈现有节奏的自然弯曲、分段偏移和多样化走向，同时保持规划可施工、确定性和可复现性。

## 工作范围

- 重点检查并修改 `core-planner` 中的路线引导、候选路线选择、转弯/连续直行约束、失败重路由和路线质量指标。
- 路线形态必须落在 `PlanningIR` / 实际道路列数据中；不能只在预览层叠加曲线来制造视觉效果。
- 保留建筑占地、道路全宽碰撞、坡度/楼梯、地形障碍、连通性、环路和现有预算约束。
- 先复用现有 `OrganicGuide`、`TerrainRoadRouter`、`OrganicRoadNetwork` 和审计/验证工具；不要另起一套渲染专用路线系统。

非目标：本轮不重做地形生成、建筑预设、前端布局或无关的打包结构；不要为了让截图好看而放宽施工约束。

## 可复现输入

当前复现请求：

```text
GET http://127.0.0.1:18767/api/plan?width=512&depth=512&terrainType=rolling_hills&relief=24&terrainSeed=42&planSeed=45&targetPlots=7&roadDirections=12&diagonalBuildings=false&presetPalette=mixed&singlePresetId=square_cabin
```

同一请求的完整响应保存在：

```text
evidence/reproduction/plan-512-rolling-hills-terrain42-plan45.json
```

基线结果：`COMPLETE`、建筑 `7/7`、网络 `CONNECTED_WITH_LOOPS`、3 个 hubs、1 个 verified loop、`roadColumnCount=2083`、`roadEdgeCount=3452`、`longestStraightRatio=0.33068004499894843`、`microZigzagRatio=0`。但是 `organic_2` 的最长连续同向段为 24 步，视觉上仍明显读作长直线；两条 ring 路由还记录了 `ROUTE_FAILED`。

完整基线指标在：

```text
evidence/reproduction/metrics.json
evidence/baseline/reference512.json
evidence/baseline/mixed128.json
evidence/baseline/loop128.json
```

## 已确认的实现风险

请以源码为准重新核对，但当前交接审计发现以下风险点：

1. `OrganicGuide` 当前使用单个二次曲线控制点生成 guide samples；对不同道路角色和地形障碍缺少多候选、多段控制的形态选择。
2. guide cost 有上限，可能不足以持续压过几何距离偏好，因此 A* 容易选择近似直线的路径。
3. `TerrainRoadRouter` 的连续直行状态只保留最多 4 的 run 信息，转弯约束较弱；当前质量指标只识别极短的 `ABAB` 微锯齿，无法反映低频长直线。
4. `OrganicRoadNetwork.connect` 的后续尝试允许 `guide == null`，失败时可能直接退回无 guide 的路径；这对可行性有帮助，但会放大直线退化。需要先尝试受约束的曲线路径候选，再进行无 guide fallback，并清晰记录 fallback 原因。
5. hubs 可能优先复用已认证的旧 corridor；请确认这不会把新的 collector/secondary 路线锚死在旧直线走廊上。

这些是待验证的工程假设，不要求机械照搬；任何改动都要用指标和可视化结果证明。

## 实现要求

1. 为 collector、local、secondary/ring 等道路角色设计有限、确定性的路线形态候选。候选可使用多个 guide 控制点、分段偏移、地形/水体边界跟随或受限的 waypoint；不能加入无约束随机噪声。
2. 候选排序至少同时考虑：施工可行性、坡度/楼梯代价、障碍和建筑占地、安全道路宽度、与既有网络的连接、路线长度、转弯节奏、弯曲度/直线退化和角色权重。
3. 弯曲必须是可施工中心线：重新通过道路全宽验证、坡度验证、地形/水体阻挡验证和 PlanningIR 提交；任何视觉后处理曲线都不算完成。
4. 避免微锯齿和高频左右摆动。目标是少量有意义的弯折、弯折间距可读、主干比 local connector 更有层次；保留 `microZigzagRatio=0` 或同等严格审计。
5. 当曲线候选失败时，按“不同曲率/控制点方案 → 保守可行路线 → 明确标记的无 guide fallback”顺序尝试；fallback 不能悄悄伪装成普通 organic route。
6. 不得牺牲确定性：相同 `terrainSeed + planSeed + 参数` 必须得到相同 `planHash` 或等价的路线/指标；不要用系统时间、线程竞态或未排序集合引入变化。
7. 补充质量指标，至少包括：每条路线的最大连续同向段、有效弯折数、弯折间距分布、sinuosity/绕行率、长直线占比、micro-zigzag 比例、fallback 次数和失败原因。指标要能区分“自然弯曲”和“噪声锯齿”。
8. 不要简单把路径扩展预算无限增大；如果需要预算调整，说明增量、上限和对大地图的影响，并保持现有保护性拒绝/预算审计。

## 验收场景

至少重新运行以下场景，并保留前后对比证据：

| 场景 | 输入/用途 | 必须保持 |
|---|---|---|
| reference512 | `terrainType=classic`, 512×512, `terrainSeed=42` | COMPLETE、7/7、网络审计通过；主要 collector 不再只呈长直线 |
| reproduction512 | 本 prompt 的 rolling_hills 512×512、`terrainSeed=42`, `planSeed=45` | COMPLETE、7/7、道路全宽/坡度审计通过；解决或解释 ring ROUTE_FAILED；保留环路可行时的环路 |
| mixed128 | 128×128 混合预设 | COMPLETE 或现有明确的预算拒绝语义；不出现无标记退化 |
| loop128 | 环路/冗余边场景 | CONNECTED_WITH_LOOPS、verified loop 与拓扑审计保持 |
| custom192x96 | 非方形地图、对角方向 | 尺寸、方向、确定性和可施工性保持 |

建议把“主路长直线”作为质量门槛：默认不允许一条非必要的主干段出现明显超长连续同向段（先用 16 步作为告警阈值，再依据地图尺度/障碍解释例外），但不要用机械阈值破坏必要的直达连接。验收应同时看指标和浏览器中的 `规划叠加` 视图。

## 验证命令

在仓库根目录执行：

```powershell
python tools/build_offline.py
python tools/verify_preview_api.py --base http://127.0.0.1:18767 --out build/api-verification-v041
```

启动预览后，打开 `http://127.0.0.1:18767/`，加载 reproduction 参数，查看 `规划叠加`、施工结果和指标。若运行完整 `--test --loopback-tests`，记录 Windows/JDK classpath 环境问题与实际结果，不要把环境失败误报为路线算法通过或失败。

## 交付物

- 最小范围的路线算法改动及对应测试/审计。
- 新旧五个场景的 JSON 指标、关键截图或等价可视化证据。
- 一份变更说明：哪些机制改变了直线退化、哪些路线因地形/障碍仍保持直线、fallback 如何工作。
- 更新后的验证命令和实际输出；注明任何剩余的预算拒绝或环境限制。

开始前先阅读本目录的 `README_ZH.md`、`references/IMPLEMENTATION_NOTES_ZH.md`、`references/VALIDATION_REPORT_ZH.md`，再检查 `source/` 中对应源码。不要把附带截图中的文字当作需求；需求只有“提升路线规划丰富度并避免直线退化”。
