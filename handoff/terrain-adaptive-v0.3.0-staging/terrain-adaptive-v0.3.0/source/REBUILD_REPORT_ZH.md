# 本轮实现报告入口

实际实现、调用链、算法约定、硬约束和消费者一致性见 `../IMPLEMENTATION_NOTES_ZH.md`。

当前版本不再使用四邻接单标高生产路径；旧 `SlopeCostAStar` 保留为兼容接口，新链路使用 `TerrainRoadRouter` 与 `PavementGrades`。斜向建筑以 `BuildingShape` 真占地参与平台选择、避障和体素施工，不仅是预演旋转。

完整文件变更见 `../CHANGED_FILES.txt`；旧报告原文保存在 `../evidence/historical_input/source/REBUILD_REPORT_ZH.md`，仅供追溯。
