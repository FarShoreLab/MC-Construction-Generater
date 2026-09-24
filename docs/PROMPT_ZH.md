# 交接任务

阅读源码和 `road-height-problem.png`，分批完成以下任务。每批先定位根因、复用现有实现、补回归测试并运行现有离线测试；不做无关重构。

1. 道路默认贴合地形。普通陆地不得因 `allowBridges` 而形成长距离高架或连续同高填方；桥梁只用于确有必要的跨水、沟谷或短跨度障碍。将贴地程度和异常连续架空纳入路径成本及硬约束。
2. 支持先确定建筑选址再反复 roll 道路；手动选址可添加、删除，已固定选址不得被 roll 移动。
3. 完整方案 `REJECTED` 时自动更换 `planSeed` 重试，成功即停止，并有可配置的严格轮数上限；保留每轮失败原因。
4. 加入农田和畜牧地：使用适应地形、具有自然边界的区域生成，不要只是矩形占地。
5. 修复 `buildingRepulsion=0` 仍强制分散的问题，并提供可明确选择的村落、城镇、城市布局模式。城市模式应提高密度、街区连续性和建筑高度/体量层级，而不只是增加建筑数量。
6. 更新预览 UI/API、施工清单和测试，保持确定性、预算上限及前后端职责边界。

## 已定位线索

- `RoadTerrain.upper()` 在专家模式启用桥梁后，会对所有陆地道路放宽到 `MAX_PILE_HEIGHT`；`applyStructure()` 又会把超过普通填方上限的陆地道路标成 `bridge`。这很可能导致截图中的长距离同高架空道路。
- `ExpertSettlementPlanner.regionalAnchor()` 使用 maximin 主动拉开建筑，因此 `buildingRepulsion=0` 只关闭局部排斥分数，不能关闭宏观分散。
- 当前已有 pins、planSeed roll、搜索 attempts 和 REJECTED 状态。应扩展现有流程，不要另写平行系统。

## 独立研究任务（先研究，不改代码）

分析 https://github.com/shiroha-233/RoadWeaver 中可复用的道路网络、寻路代价、曲线平滑、路基贴地、桥隧判定和可视化思路，并与本项目 `TerrainRoadRouter`、`PavementGrades`、`OrganicRoadNetwork` 对照。输出适合本项目的最小算法建议、风险和建议测试，不照搬代码。

用户提供的 YouTube 链接 `https://www.youtube.com/watch?v=PqrKqhkj3gQ` 当前标题为 Unreal Engine 5.8 MCP 教程，疑似贴错；不要把它当作本项目算法依据。

## 验收

1. 跑 `python tools/build_offline.py --test --loopback-tests`，现有测试继续通过。
2. 新增道路离地高度、最长连续陆地架空、固定选址不变、自动重试上限、自然农牧地边界、不同聚落模式密度与高度分布的测试。
3. 跑相关 API 与浏览器验证脚本，并报告命令、结果和剩余限制。
