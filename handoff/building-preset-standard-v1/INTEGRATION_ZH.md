# 工程集成与当前 handoff

## 当前完成的接口

- 活跃内置预设 34 个；`estate_48/64/96` 的 JSON 为 archived=true，保留旧方案还原使用。
- `BuildingPresetRegistry.getAllPresets/getPresetsByCategory/resolveBestPreset` 不返回封存项；getPreset(id) 仍能读取。
- `presetPalette=custom` 启用独立列表，`buildingSelection=[{"presetId":"meadow_hut","enabled":true,"count":2}, ...]`。
- 启用项数量和必须等于 targetPlots，范围 1–64。关闭项不生成。与人工 building pins 混用会明确拒绝，避免覆写数量语义；dock pins 可保留。
- 列表模式需要 `layoutMode=expert`。过大占地或不满足地图约束时拒绝整个方案，不静默减数或换小建筑。
- `sitePreference=clearings` 是浏览器和 HTTP 默认：提高整地/坡度/清林成本，偏好适度离岸且可接路的位置。`balanced` 保留对照模式；Java 直接构造 ExpertSettings 的默认仍为 balanced。
- 选址是带覆盖率、间距、坡度、预算和道路约束的偏好，不保证每个种子都贴水。房屋占地仍须干燥，码头使用原有独立逻辑。
- `BuildingPreset.entrances` 保存候选，空列表回退单 entrance；IR 的 `builder.entranceIndex`、`sourceFacing` 和 `diagonal45` 锁定被选变体。读取旧 IR 时入口索引默认 0。
- `components` 在运行时是全局 mask 元数据，几何来自已编译的 layers。施工、旋转和道路接驳均使用同一 BuildingShape。

## 将新预设接入完整工程

1. 将作者配置留在合适的资产源目录，用编译器输出 `core-planner/src/main/resources/presets/<id>.json`。
2. 在 `BuildingPresetRegistry.EXTRA_PRESET_IDS` 注册新 ID。这样目录和独立开关列表会自动出现。
3. 如需加入自动组合，再更新 `PresetPalette` 对应列表；不要为单个预设修改选址算法。
4. 运行 `python tools/build_offline.py --test --evidence build/preset-verification`（Java 21、本地 Gson 2.10.1）。新增注册数量后同步合理的目录总数断言。
5. 运行 `python tools/preview_server.py --port 18771`，从单预设和平地开始测试，再使用 custom 列表测试混合数量。

包中 `source-reference/` 是本次交接时的数据模型、旋转、注册和规划接口快照，供理解集成点；不是完整项目，也不要盲目覆盖后续修改的工程。可直接独立运行的是作者配置与 Python 编译器。

## 主要文件

| 完整项目相对路径 | 职责 |
| --- | --- |
| core-planner/src/main/java/org/mcsettlement/planner/preset/BuildingPreset.java | 运行时 JSON 字段、mask 校验、四向旋转 |
| .../preset/BuildingShape.java | 实际占地与 45° 重采样 |
| .../preset/PlannedBuilding.java | 按已保存的入口与旋转还原模型 |
| .../preset/BuildingPresetRegistry.java | 内置注册与封存过滤 |
| .../preset/PresetPalette.java | 自动组合、地图容量、独立清单 |
| .../ExpertSettings.java | 清单解析、选址偏好、人工点 |
| .../ExpertSettlementPlanner.java | 候选位置、入口变体与环境评分 |
| .../civil/PlanConstruction.java | 唯一施工清单出口 |
| tools/preview.html、tools/preview_server.py | demo UI 与 HTTP 参数转发 |

## 已知边界

语义组件没有各自独立标高、接路索引或独立旋转；先由作者编译为一个整体预设。多入口不表示多条自动接路。不主动复活大型组团。新示例没有注册为默认第 35 个预设。游戏内 Fabric 完整类型检查和 Minecraft 运行验收需对应开发环境，本包不以浏览器模拟替代游戏验收。
