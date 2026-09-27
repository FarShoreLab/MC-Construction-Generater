# 当前工程接手指南

## 源码入口

以根目录下的 `core-planner/`、`llm-bridge/`、`fabric-mod/` 和 `tools/` 为当前源码。不要在 `handoff/` 的历史源码副本中开发。当前使用说明优先阅读根目录 README、本文、`TWO_STAGE_DEMO_20260926_ZH.md` 和 `TERRAIN_FARMLAND_20260924_ZH.md`；`README_REBUILD_ZH.md` 的 v0.4.0 内容是早期记录。

调用路径：`tools/preview.html` → `tools/preview_server.py` → `SimulationApiRunner` → 聚落规划器 → `PlanningIR` → `PlanConstruction`。预览是本地模拟，不是 Minecraft 实机验收。Fabric 模块仍需单独完成依赖构建和客户端验收。

现代城市链路：`tools/modern_city.html` → `/api/modern-city` → `tools/modern_city.py`。丘陵/河谷通过 `legacy_city_terrain.py` 读取原 Java 地形，`city_terrain.py` 与 `city_districts.py` 负责城市几何；保留平地对照。它不使用 Java 聚落规划器或 Minecraft 施工器；说明见 `MODERN_CITY_DEMO_ZH.md`、`MODERN_CITY_TERRAIN_ZH.md`，研究依据见 `MODERN_CITY_RESEARCH_ZH.md`。

## 环境与验证

需要 Python 3、Java/Javac 21 和 Gson 2.10.1。离线构建只读取本地依赖，不自动下载；可用环境变量 `GSON_JAR` 指向已安装的真实 JAR，或使用 Gradle 缓存。首次联网准备依赖可用根目录 Gradle Wrapper；Fabric 的依赖要求见 `DEPLOYMENT.md`。

在项目根目录运行：

1. `python tools/build_offline.py --test` → Verify: 主源码及可执行测试编译通过，所有测试输出 PASS；证据在 `build/verification/`。
2. `python tools/verify_two_stage.py` → Verify: 选址不施工、确认后精确连路、无效位置拒绝及建筑约束保留通过。
3. `python tools/verify_site_orientations.py` → Verify: 四向和 45° 占地、入口与实际放置一致。
4. `python tools/preview_server.py --port 8766` → Verify: 打开 http://127.0.0.1:8766/ ，生成选址、调整一栋、确认生成道路。
5. `python tools/test_modern_city.py`，服务运行后执行 `python tools/verify_modern_city_api.py --port 8766` → Verify: 几何约束与三种布局 API 检查通过；打开 `/modern-city` 检查阶段切换、俯视、密度和 JSON 导出。
6. `python tools/test_city_terrain.py -v` → Verify: 原 Java 地形来源一致、道路/建筑几何和挖填约束通过。丘陵/河谷需要先完成 Java 编译和 Gson 准备。

若缺少 Java 或 Gson，先修复环境再重试。编译或回归失败时查看对应日志，不用旧生成结果代替本次验证。完整 Fabric 类型检查和 Minecraft 运行不包含在离线验证中。

2026-09-27 验证：主源码、可执行测试编译及全部 16 组离线回归通过；两步 API 检查和八种朝向/45° 占地检查通过。精简后从 Git 索引导出干净副本 `build/clean-handoff/`，重新检查预设编译与独立打包、两步 API、朝向、现代城市几何/API 及 HTTP 地形控制，均通过。现代城市的浏览器验收记录见其说明；本次整理未重跑浏览器手工验收、可选 loopback fixture 或 Minecraft 客户端验收。首次沙箱运行因 Gson 缓存读取权限失败，获准在沙箱外重跑后通过。

城市地形增量发布验证：从提交候选导出 `build/city-release-check/`，Java 主源码编译、4 项平地城市测试、5 项地形城市测试、城市 HTTP API 和原两步规划 API 均通过；其中地形测试直接核对旧 Java 来源及导出几何。此次未重复执行浏览器或 Minecraft 客户端验收。

## 当前包含的功能

此次整理将以下原先未提交的功能、测试和文档作为完整版本发布。接手时运行 `git status` 和 `git log -1` 确认实际版本，不用历史报告推断当前状态。

| 改动组 | 主要文件 | 验证入口 |
| --- | --- | --- |
| 新地形、起伏范围、农田、梯田、吊桥 | `TerrainBlockGenerator`、`TerrainParameters`、`TerrainLandUsePlanner`、`SuspensionBridges`、道路/施工相关类、`terrain-controls.json` | `TerrainExpansionMain`、`LandBridgeMain`、`verify_terrain_controls.py` |
| 两步选址与道路、确认建筑约束 | `ExpertSettings`、`ExpertSettlementPlanner`、`ExpertTerrainAudit`、`PlanningIR`、模拟 API/管线、`TwoStagePlanningMain` | `verify_two_stage.py`、离线回归 |
| 编辑反馈、占地呼吸、朝向与入口预览 | `tools/preview.html`、`tools/preview_server.py`、`SimulationApiRunner` | `verify_site_orientations.py`、浏览器手工验证 |
| 构建与说明 | `tools/build_offline.py`、新增功能文档、README/文档索引 | 按以上顺序执行 |

这几组功能共享 API 和核心规划文件，发布时将完整功能、测试和文档一起提交。

## 本地归档与云端精简

历史交付包、源码快照、旧导出结果及预设包中的重复源码/验证记录移至本地 `.local-archive/20260927/`，按原相对路径存放。移动前后逐文件 SHA256 校验，清单位于该目录的 `manifest.json`。本地文件未删除，该目录被 Git 忽略，不是新克隆用户的运行依赖。

`handoff/building-preset-standard-v1/` 保留作者规范、Schema、编译器和两个测试依赖 JSON 样例。运行 `python tools/package_preset_handoff.py` 从当前源码重新生成独立交接 ZIP；暂存文件在 `build/preset-handoff/`，ZIP 位于 `handoff/building-preset-standard-v1.zip`，两者均被忽略。打包不依赖本机历史验证证据。

历史目录 `handoff/settlement-planning-next-ai-20260922/` 含有本地修改的旧子模块，为保留 Git 元数据原位保留并忽略；已从云端当前树与 `.gitmodules` 移除。其原有图片和结构数据删除未提交。当前唯一第三方子模块入口为 `tools/generators/mgaia-wfc`。

此次通过正常提交移出历史产物，不改写 Git 历史。清理前的完整版本可在提交 `325cbe74ca78db772e71740493ac115f8bff16ce` 查看；例如 `git show 325cbe7:handoff/building-preset-standard-v1/manifest.json`。需要旧文件时可从本地归档复制，或在独立克隆中检出旧提交，避免覆盖当前源码。

根目录 `README_REBUILD_ZH.md` 和文档索引的历史部分只用于追溯，引用的历史包不再随当前克隆提供。`ROADWEAVER_EVALUATION_20260927_ZH.md` 为研究记录，不代表已接入相应算法。早期 `tools/generators/` 脚本部分依赖本机路径与可选第三方环境，当前接手首先验证 Java + Python Demo 链路。
