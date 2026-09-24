# 最新交接同步与 demo 验证（2026-09-22）

来源：`handoff/settlement-planning-next-ai-20260922-completed.zip`。
同步 23 个新增或变更文件；原文件备份及清单位于 `handoff/sync-backup-next-ai-20260922-122249/`。

已接入：道路贴地及短陆桥约束、固定选址后重掷道路、有限自动重试、地形跟随农田与牧场、村落/城镇/城市模式及 UI/API。

本地额外修正：`NetworkDiversityMain` 的反射测试仍调用六参数状态编码函数，已更新为七参数，并增加 landRun=0/1/32 的状态无冲突检查；37 项路网测试通过。

## 验证

- 使用本地真实 Gson 2.10.1 和 Java 21 构建，新增功能回归 6/6 通过。
- `python tools/verify_site_expert.py --base http://127.0.0.1:18768 --out build/next-ai-api`：全部通过，包括六组地形、确定性、人工 pins、失败原子回退、参数边界和并发限制。
- `python tools/verify_terrain_controls.py --base http://127.0.0.1:18768 --out build/next-ai-terrain-api`：全部通过，包括四类地形、192×96、512×512、水位变化和确定性。
- 通过实际浏览器操作验证默认规划、锁定选址、重新规划和三维施工视图；没有使用模拟 API 响应。未执行完整自动化浏览器测试脚本。
- 默认场景：128×128，terrainSeed=42，planSeed=42，COMPLETE，7/7 栋，农田 77 格，牧场 90 格，道路最大离地 3 格，连续陆地架空 0 格。
- 页面锁定 siteSeed=42 后 roll 到 planSeed=43：COMPLETE，7/7 栋，建筑包围框/轴比/凸包一致，道路中心线由 498.1 格变为 471.4 格；精确选址不变由新增 Java 回归验证。

## Demo

地址：http://127.0.0.1:18768/

重启：`python tools/preview_server.py --port 18768`。
修改源码后先执行 `python tools/build_offline.py`。

未执行 Minecraft 游戏内运行和 Fabric 完整类型编译；Fabric 语法检查不能替代这两项。

## 完整离线测试结果

命令：`python tools/build_offline.py --test --loopback-tests --evidence build/next-ai-verification-final`。

12/13 组通过，命令退出码 1。唯一未通过为 bridge-loopback：Java HttpServer 初始化时出现 `Unable to establish loopback connection` / `SocketException: Invalid argument: connect`，在进入 HTTP 测试前失败。尝试进程级 `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.disable=true` 后仍失败；没有修改系统配置。真实 demo HTTP API 验证已独立通过。

| 测试组 | 退出码 |
|---|---|
| core-21 | 0 |
| adaptive-29 | 0 |
| fuzz-100 | 0 |
| organic-presets-25 | 0 |
| route-richness | 0 |
| network-diversity | 0 |
| site-expert | 0 |
| next-ai-planning | 0 |
| land-bridge | 0 |
| terrain-controls | 0 |
| bridge-diagonal | 0 |
| fabric-syntax-only | 0 |
| bridge-loopback | 1 |

原始日志：`build/next-ai-verification-final/`。
