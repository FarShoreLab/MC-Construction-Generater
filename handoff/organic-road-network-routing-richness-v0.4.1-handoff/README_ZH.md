# organic-road-network-routing-richness-v0.4.1 handoff

这是针对“路线规划总是变成直线、丰富度不够”问题的聚焦交接包。它基于已同步的 `organic-road-network-v0.4.0-footprint-presets.zip`，保留完整源码快照、已有验证证据、用户复现截图和可直接交给下一轮开发代理的中文 prompt。

## 本轮边界

- 本轮没有修改路线算法源码。
- 本轮只创建 handoff 目录、复现证据、指标摘要、清单和 prompt。
- 用户附带的 PNG 已作为现象证据保存，不作为实现指令。

## 目录

- `source/`：v0.4.0 footprint-presets 的 111 个源码/工具文件。
- `references/`：原包 README、实现说明、验证报告、清单和变更文件列表。
- `evidence/baseline/`：已通过的 API 基线响应、指标和日志。
- `evidence/browser/`：reference/mixed/loop 场景的浏览器预览截图。
- `evidence/reproduction/`：用户截图、精确请求参数、完整复现响应和当前路线形态指标。
- `PROMPT_ROUTE_RICHNESS_ZH.md`：给下一轮开发代理的完整任务 prompt。
- `HANDOFF_MANIFEST.json`：包结构、来源和验证状态。
- `SOURCE_SHA256SUMS.txt`：`source/` 文件校验和。

## 复现输入

```text
GET http://127.0.0.1:18767/api/plan?width=512&depth=512&terrainType=rolling_hills&relief=24&terrainSeed=42&planSeed=45&targetPlots=7&roadDirections=12&diagonalBuildings=false&presetPalette=mixed&singlePresetId=square_cabin
```

当前基线为 `COMPLETE`、7/7 建筑、3 hubs、1 个 verified loop。`organic_2` 的最大连续同向段为 24 步；这解释了截图中“网络已完成但路线仍显直”的问题。完整数据见 `evidence/reproduction/metrics.json` 和对应 JSON 响应。

## 已知验证说明

主源码编译已通过，API 验证已通过。完整 `--test --loopback-tests` 在当前 Windows/JDK classpath 环境中于 executable-tests 编译阶段失败；该环境问题已记录在 `references/VALIDATION_REPORT_ZH.md` 与基线日志中，交接时应重新验证，不能将它当成算法结论。
