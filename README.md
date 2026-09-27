# MC Construction Generater

Minecraft 地形自适应聚落规划工程。当前 Demo 支持先生成建筑选址、逐栋调整，再确认生成道路；包含新增地形、农田、梯田与吊桥。本地浏览器预览不代表 Minecraft 客户端验收完成。

## 开始使用

需要 Python 3、Java/Javac 21、Gson 2.10.1。核心预览不依赖 MGAIA 子模块，可直接克隆运行：

```powershell
git clone https://github.com/FarShoreLab/MC-Construction-Generater.git
cd MC-Construction-Generater
# 已有 Gradle 缓存时可省略 GSON_JAR；否则指向已安装的真实 JAR。
$env:GSON_JAR = 'C:\dependencies\gson-2.10.1.jar'
python tools/build_offline.py --test
python tools/preview_server.py --port 8766
```

打开 http://127.0.0.1:8766/ 。离线构建不下载依赖；无本地 Gson 时，可先运行 `./gradlew :core-planner:classes`（Windows 使用 `./gradlew.bat`）准备依赖，需要联网。

现代城市体量 Demo 入口为 http://127.0.0.1:8766/modern-city ，先调用原城镇 Java 生成器展示完整 MC 方块地形，再按道路 → 街区 → 建筑的顺序规划。支持原始丘陵、谷地、平地对照与有限挖填，以及单中心、多中心和均衡布局。城市规划为 Python 几何预演，尚未接入 Minecraft 施工。详见[自然地形城市说明](docs/MODERN_CITY_TERRAIN_ZH.md)。

- [接手指南：模块、验证、已知边界](docs/HANDOVER_ZH.md)
- [两步 Demo 操作与接口](docs/TWO_STAGE_DEMO_20260926_ZH.md)
- [现代城市 Demo 与验收](docs/MODERN_CITY_DEMO_ZH.md)
- [地形、农田与吊桥](docs/TERRAIN_FARMLAND_20260924_ZH.md)
- [建筑预设作者标准](handoff/building-preset-standard-v1/README_ZH.md)
- [文档索引](docs/README.md)

## 目录与开发边界

| 路径 | 用途 |
| --- | --- |
| `core-planner/` | Java 规划、IR、施工与模拟，含回归测试 |
| `llm-bridge/` | 可选 LLM 桥接；当前 Demo 不调用 LLM |
| `fabric-mod/` | Minecraft 1.20.1 / Fabric 集成；需另做完整构建和游戏验收 |
| `tools/` | 离线构建、预览服务、验证、预设编译及打包 |
| `docs/` | 当前功能说明、接手指南和历史记录 |
| `handoff/building-preset-standard-v1/` | 必要作者规范与测试样例，不含重复工程源码 |
| `tools/generators/` | 早期建筑生成工具及预览 vendor；部分旧生成脚本含本机路径，不属于当前 Demo 启动链路 |

如需使用 MGAIA 建筑生成工具，运行 `git submodule update --init --recursive`。唯一子模块为 `tools/generators/mgaia-wfc`，遵循上游许可证。

构建缓存、导出产物、历史 ZIP 和源码快照不纳入当前源码树。本地旧文件的归档位置、校验清单及 Git 历史恢复说明见接手指南。Git 历史未重写；需要较小下载时可使用 `git clone --depth 1`。

## 许可证

本项目自有代码采用 [GNU GPL v3.0 only](LICENSE)（SPDX：`GPL-3.0-only`）。第三方代码继续遵循各自许可证与版权声明，包括 MGAIA 的 MIT 许可证（初始化后见 `tools/generators/mgaia-wfc/LICENSE`）。本声明不改变第三方原始授权，也不追溯变更历史交接包或已发布版本的许可。Fabric/Minecraft 集成分发前仍需核查组合分发的许可兼容性。
