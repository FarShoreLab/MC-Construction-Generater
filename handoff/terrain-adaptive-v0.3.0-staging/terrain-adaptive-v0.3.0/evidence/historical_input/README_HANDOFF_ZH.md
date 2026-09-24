# 地形适应聚落规划器浏览器端交接

本包是当前可复现源码基线，用于在浏览器端继续开发“多标高、随坡、斜向道路、大地图”版本。它不是已完成该目标的发布包。

## 从哪里开始

1. 将整个压缩包提交给浏览器端会话。
2. 同时发送 `PROMPT_BROWSER_ZH.md` 的完整内容。
3. 要求接手者先阅读 `CHANGED_FILES.txt` 和 `evidence/`，再检查 `source/core-planner/.../BoundedSettlementPlanner.java`。

## 当前可以复现的能力

- 独立 terrainSeed / planSeed。
- 丘陵、山地、谷地、台地四种确定性地形。
- 同一地形反复规划、方案比较、原始/规划/施工三种视图。
- 统一 `PlanningIR 0.2` 与 `PlanConstruction` 施工清单。
- 当前证据：21 项核心回归通过；100 个确定性随机不变量场景通过。

## 当前不具备的能力

- 核心道路仍是四邻接、单标高。
- 建筑平台仍继承入口标高。
- 浏览器页面固定请求 96×96；API 最大 128×128。
- 未完成 Fabric 全量构建和 Minecraft 游戏内验收。

## 本地预演

Windows PowerShell：

```powershell
.\tools\start_preview.ps1 --port 8766
```

打开 `http://127.0.0.1:8766/`。脚本使用 Java 21、本机 Python 和本地缓存的 Gson 2.10.1，不访问外网。

## 证据说明

- `evidence/interactive-core-tests.log`：21 项核心回归。
- `evidence/fuzz.log`：100 个随机不变量用例。
- `evidence/interactive-api-check.json`：同地形 roll 与种子复现。
- `evidence/interactive-terrain-check.json`：三类扩展地形响应。
- `evidence/interactive-height-check.json`：高标高、大起伏预演边界验证。
- `evidence/benchmark.csv`：新版与历史基线的场景对照，仅用于定位，不代表未来 V2 性能。

## 已知运行限制

当前主机的 Java/Gradle daemon 和 Java HTTP 测试会遇到 loopback/UnixDomainSockets 异常。因此交互预演使用 Python HTTP 服务调用 Java 子进程；核心回归使用直接 `javac/java`。这属于当前环境限制，不应通过关闭安全设置绕过。
