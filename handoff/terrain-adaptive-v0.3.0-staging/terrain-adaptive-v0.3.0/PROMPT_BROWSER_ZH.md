# 下一轮继续开发 Prompt（本轮能力已落地）

先完整阅读 `README_HANDOFF_ZH.md`、`IMPLEMENTATION_NOTES_ZH.md`、`VALIDATION_REPORT_ZH.md`、`CHANGED_FILES.txt`、真实 `source/` 与 `evidence/current/`。历史材料另存于 `evidence/historical_input/`，不得用历史通过结论代替当前复现。

当前已实现 PlanningIR 0.3.0、8/12方向高度状态路由、全宽斜向足迹、实际圆石台阶、逐栋平台标高、45°栅格建筑开关，以及默认128/最大512/矩形浏览器地图。不要退回「给斜路画颜色」或「给高度状态打标签但施工仍统一高度」的实现。

当前权威调用链：浏览器 → Python本地服务 → Java SimulationApiRunner → SimulatedSettlementPipeline → SettlementPlanner → BoundedSettlementPlanner/TerrainRoadRouter/PavementGrades → PlanningIR.groundColumns → PlanConstruction → 仿真或Fabric世界写入。建筑网格由 BuildingShape/PlannedBuilding 共用。

下一轮优先处理实测退化，而不是扩大系统框架：

1. 复现 `slope_x_div_8`：同4栋锁定10×10住宅，单标高版4/4，新版3/4。检查候选份额、已提交道路标高锁定、楼梯朝向和宽路求解的拒绝原因，不准减少需求、缩小预设、放宽保护或挖填上限来「改善」。
2. 提高复杂山地可行率。当前逐栋贪心、最多6次抵达目标后的铺装验证、固定旧路标高都不保证完备；先给出可复现失败和预算内的具体方案，再改。
3. 在具有声明Gson2.10.1和Fabric依赖缓存的环境运行Gradle/JUnit/Fabric类型编译，并真正启动Minecraft验证连续台阶、转角、斜向门口、重扫描保护区与撤销策略。当前只有Fabric语法检查，不能继承为已编译。
4. 在可用WebGL浏览器进行原生本地HTTP导航与512地图GPU性能、内存验证。当前截图是2D回退，CPU3D几何生成不等于GPU验收。
5. 精细化45°预设栅格化质量。目前是真占地、真施工，但非无损任意角度建筑旋转；不要把外接框空角填地基或裁剪预设来掩饰问题。

保持全套21+29测试及100随机不变量测试；对照必须锁定数量、用途、预设、footprint面积和预算，报告PARTIAL及失败样本。保持搜索/响应体/施工编辑显式硬上限；`groundColumns` 是施工唯一真源。不得引入联网依赖、修改密钥或调用收费模型。交付源码、unified diff、可复现证据、性能原始记录和已知限制。
