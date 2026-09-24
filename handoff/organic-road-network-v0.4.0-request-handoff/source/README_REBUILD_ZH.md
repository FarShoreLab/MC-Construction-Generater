# 当前源码入口：地形适应 v0.3.0

本目录包含真实核心、桥接和Fabric适配源码。最新交接说明见上一级 `README_HANDOFF_ZH.md`，算法说明见 `IMPLEMENTATION_NOTES_ZH.md`，实测结果见 `VALIDATION_REPORT_ZH.md`。原先四邻接、单标高的文档和证据已移到 `../evidence/historical_input/`。

快速启动：`python tools/build_offline.py` 后运行 `python tools/preview_server.py --port 8766`；也可使用 `tools/start_preview.ps1` / `tools/start_preview.sh`。需要Java21、Python、本机Gson，不自动下载依赖。

默认128地图；道路8/12方向；可选真实45°建筑占地；最大512及矩形尺寸。施工真源为PlanningIR.groundColumns。提供源码与离线验证，不提供未经验证的Fabric二进制。
