# 文档索引

[返回项目入口](../README.md)

归档文档保留原始内容。文中的命令仍在项目根目录运行；`tools/`、`build/`、`handoff/` 等工程路径仍以项目根目录为基准。同目录文档名及截图名可直接在本目录找到。

部分历史说明引用原交付包中的 `source/`、`evidence/`、上一级报告或校验清单，这些引用保留原交付语境，不代表当前目录存在相应文件。历史测试记录不代表本次整理重新执行了业务测试。

## 功能与使用

- [部署边界与本地使用](DEPLOYMENT.md)
- [预设控制（2026-09-22）](PRESET_CONTROLS_20260922_ZH.md)
- [建筑表面（2026-09-22）](BUILDING_SURFACES_20260922_ZH.md)
- [路网多样性 v0.5.1](NETWORK_DIVERSITY_V051_ZH.md)
- [专家规划 v0.5.0](EXPERT_PLANNING_V050_ZH.md)
- [地形控制 v0.4.2](TERRAIN_CONTROLS_V042_ZH.md)
- [道路丰富度 v0.4.1](ROUTE_RICHNESS_V041_ZH.md)

## 实现与历史记录

- [Demo 同步记录（2026-09-22）](DEMO_SYNC_20260922_ZH.md)
- [AI 实现记录](NEXT_AI_IMPLEMENTATION_ZH.md)
- [原始任务说明](PROMPT_ZH.md)
- [道路高度问题截图](road-height-problem.png)
- [早期重建说明](../README_REBUILD_ZH.md)
- [早期实现报告](REBUILD_REPORT_ZH.md)
- [早期验证结果](TEST_RESULTS_ZH.md)
- [历史变更清单](CHANGED_FILES.txt)

## 整理记录（2026-09-24）

上列 12 份 Markdown 文档、`CHANGED_FILES.txt` 和 `road-height-problem.png` 从项目根目录移入 `docs/`，文件名和内容保持不变。根目录 `README_REBUILD_ZH.md` 保留并更新预设说明链接；`tools/package_preset_handoff.py` 改为从 `docs/` 读取预设说明，交接包内部文件名保持不变。

如需撤销归档，可将这 14 个文件移回项目根目录，并恢复上述两个文件中的文档引用路径。
