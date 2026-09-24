# MC Construction Generater

Minecraft 地形自适应聚落规划工程。

仓库含有两个指向上游 MGAIA 项目的 Git 子模块。克隆时运行 `git clone --recurse-submodules <repository-url>`，或克隆后运行 `git submodule update --init --recursive`。

## 许可证

本项目自有代码采用 GNU General Public License v3.0 only（SPDX：`GPL-3.0-only`），完整条款见 [LICENSE](LICENSE)。分发受 GPL 覆盖的衍生程序时，须按 GPLv3 提供对应源码及许可声明；允许商用，私人修改无需公开。

第三方代码继续遵循各自的许可证与版权声明，包括 [mgaia-wfc 的 MIT 许可证](tools/generators/mgaia-wfc/LICENSE)。本声明不改变第三方代码的原始授权，也不追溯变更历史交接包或已发布版本的许可。分发 Fabric/Minecraft 集成二进制前，仍需核查组合分发的许可兼容性。

## 使用入口

- [本地启动与重建说明](README_REBUILD_ZH.md)
- [文档索引](docs/README.md)
- [预设控制说明（2026-09-22）](docs/PRESET_CONTROLS_20260922_ZH.md)
- [建筑预设交接包](handoff/building-preset-standard-v1.zip)

在项目根目录运行（需要 Java 21、Python 3 和本地 Gson）：

```powershell
python tools/build_offline.py
python tools/preview_server.py --port 8766
```

随后访问 http://127.0.0.1:8766/ 。

## 目录

| 路径 | 用途 |
| --- | --- |
| `core-planner/` | 核心规划源码与测试 |
| `fabric-mod/` | Fabric 集成 |
| `llm-bridge/` | LLM 桥接模块 |
| `tools/` | 构建、预览、验证和预设工具 |
| `docs/` | 功能说明、历史记录与问题截图 |
| `handoff/` | 交接包、源码快照与备份；部分内容被测试直接引用 |
| `build/` | 构建产物与验证证据；部分内容用于交接打包 |
| `output/` | 已有导出产物 |
| `gradle/`、`.gradle/` | Gradle Wrapper 文件与本地缓存 |

2026-09-24 仅归档根目录散落文档和图片，并更新阅读入口及打包文档路径；未清理源码、缓存、交接包或构建产物。
