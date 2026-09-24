# 当前源码：有机道路与多形状建筑 v0.4.0

启动：`python tools/build_offline.py` 后执行 `python tools/preview_server.py --port 8766`；或使用 `tools/start_preview.ps1` / `tools/start_preview.sh`。需要 Java 21、Python 3、本地真实 Gson；不自动下载依赖。

浏览器默认混合预设组、128×128。API 默认 classic，保留原10个预设作公平对照。新增12个固定尺寸、真实占地掩码建筑。混合、紧凑、大体量、单一指定均可在界面切换；点选预设卡片后点击生成。

当前说明见上一级 `README_ZH.md`、`IMPLEMENTATION_NOTES_ZH.md`、`VALIDATION_REPORT_ZH.md`。施工只读取 PlanningIR 0.4.0 的 groundColumns 和锁定建筑足迹。没有随包提供未经验证的 Fabric 二进制。
