# 聚落规划完整升级包 v0.5.1

这是可独立使用的完整源码包，基于 v0.5.0 整合了分散选址、跨区联系、8＋12混合航向、近路复用与有界失败恢复。无需先安装 v0.5.0，也不要只复制三份 Java 文件。

## 启动／升级

先关闭旧预演服务，把本包解压到一个新目录，进入 `source`。沿用 Java 21、Python 3 和本机真实 Gson：

```powershell
python tools/build_offline.py
python tools/preview_server.py --port 18767
```

也可在 `source` 中执行 `powershell -ExecutionPolicy Bypass -File tools/start_preview.ps1`，Linux/macOS 用 `sh tools/start_preview.sh`。

浏览器地址为 `http://127.0.0.1:18767/`。页面显示 **v0.5.1**；专家实际输出 `metrics.sitePlanning.algorithm = site_expert/0.5.1`。先编译新源码再开服务，不能继续连到旧窗口中的服务。

离线构建优先找本机 Gradle 缓存的 Gson 2.10.1。没有缓存时，设置自己已安装的真实 jar：

```powershell
$env:GSON_JAR = 'C:\deps\gson-2.10.1.jar'
python tools/build_offline.py
python tools/preview_server.py --port 18767
```

包内不附带本机依赖、编译缓存或字体。验证环境和未测部分在 `VALIDATION_ZH.md`，不把源码包当作已构建的 Fabric mod jar。

## 本轮变化

**不再固定三个片区。** 每放一栋自动建筑，重新按地形和已有建筑计算区域目标。近邻拥挤罚分独立于包围框、中心凸包和轴比；大框中仍然分成几个拥挤小团会被识别。人工位置与门朝向仍是硬约束，不被软罚分挪动。

**跨区联系不再等于添加一条重复走廊。** 对现有步行距离明显绕远的建筑对，尝试新增联系。接受前检查真实铺装的新围合面积和旋转不变厚度；贴近的双路围出窄缝不算宏观闭环。三叉路口仍允许存在，但不是固定三片区模板。

**保留已连通部分，有限恢复失败。** 在同一个共享预算内继续未完成的连接；必要时最多四次尝试调整最后一栋尚未接通的自动建筑。已接通建筑和人工锁定点不动。最终仍要求全部建筑、强制连接、覆盖率和施工审计通过；没有用半成品冒充 COMPLETE。

**8向和12向可以在同一路线上混用。** 新 `roadDirections=16` 是两组格点方向的并集，四个正交方向重合，因此是16种实际航向，不是20向。包括45°斜向和(2,1)/(1,2)斜向。界面默认混合；原8向、12向仍可选择，HTTP/Java未指定时仍默认8向。建筑45°旋转开关独立。水中桥梁仍遵守原正交直行和标高限制。

**近路先尝试共享。** 特定端点连接可以接入并复用已有中心线，再验证拼接后的整条路。一般寻路对附近同层近似平行道路增加代价，并检查短距离平均走向，避免交替斜步绕过惩罚。交叉路口、不同标高不强行合并。没有渲染后移动道路，也不保证所有近路都能安全合并。

## 两项新增调节

| 界面／API键 | 默认与范围 | 调低 | 调高 |
|---|---|---|---|
| 建筑拥挤惩罚 `buildingRepulsion` | 1；0—3 | 更允许接近；0关闭这项软评分，但不恢复三片区模板 | 更排斥建筑挤在一起；不覆盖人工锁定或明确邻近要求 |
| 近路复用范围 `roadMergeDistance` | 7格；整数0—16 | 缩小考虑范围；0关闭近路场与主动中心线复用 | 更倾向接入附近同层道路；仍由全宽、坡度、标高与施工验证决定 |

原地形参数、22个建筑预设、覆盖硬门槛、人工点选／指定连接、桥梁、水上桩基建筑、码头和旧版对照模式保留。

## 观察结果

“选址依据与拒绝明细”记录候选、局部连接配额、恢复、换址和可选跨区联系失败原因。近邻对数、拥挤罚分、宏观围合数、共享中心线长度、近邻平行警报以及45°／(2,1)实际步数都来自当前真实计划。

布局统计与施工状态分开：`REJECTED` 可以保留候选诊断，但没有建筑清单、没有施工列、没有编辑。有限预算失败不被解释成全局不可行。没有把“所有种子都有宏观闭环／512地图必定成功”作为保证。

## 接口示例

直接请求完整混合规划：

```text
http://127.0.0.1:18767/api/plan?width=512&depth=512&terrainType=rolling_hills&terrainSeed=42&planSeed=54&relief=24&targetPlots=9&presetPalette=mixed&roadDirections=16&buildingRepulsion=1&roadMergeDistance=7
```

人工项继续使用 `POST /api/plan`，格式见 `examples/manual-pins.json`、`examples/water-house.json`。新增参数与原地形参数一样随实际 config 回显；“只重新规划”保留上次已生成地形，使用当前规划参数与人工约束，不应用尚未生成的地形滑杆值。

## 校验／复测

```powershell
python tools/build_offline.py --test --loopback-tests
python tools/preview_server.py --port 18767
# 另一个终端，依次执行，避免竞争单活动请求服务：
python tools/verify_network_diversity.py
python tools/verify_network_browser.py --chromium 'C:\path\to\chrome.exe'
```

完整回归、旧API、地形与人工编辑的实际执行结果见 `VALIDATION_ZH.md` 和 `evidence/`。浏览器脚本需要本地已安装 Playwright 与 Chromium。

`source/` 是完整工程；`changes-from-v0.5.0.diff` 是可选的精确增量补丁；`CHANGED_FILES.txt` 和 `SHA256SUMS.txt` 用于核对。旧版说明保留在 source 内的带版本文件中，当前行为以本说明为准。
