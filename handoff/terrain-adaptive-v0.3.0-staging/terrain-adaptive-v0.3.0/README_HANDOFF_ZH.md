# 地形适应聚落规划器 · 八/十二方向、多标高版

本轮基于用户提交的 `browser-terrain-adaptive-handoff.zip` 继续实现。当前数据契约为 **PlanningIR 0.3.0**。这是包含真实规划算法、施工清单和浏览器预演的源码交接包；不是 Minecraft 已构建模组。

## 直接运行

解压后进入 `source/`。需要 Java 21、Python 3 和本机已有的 Gson。工程仍声明 Gson **2.10.1**，没有增加第三方依赖，也不会自动下载依赖。

Windows PowerShell：

```powershell
cd source
# 本机 Gradle 缓存已有 Gson 2.10.1 时不必设置下一行。
# $env:GSON_JAR = 'D:\dependencies\gson-2.10.1.jar'
.\tools\start_preview.ps1 --port 8766
```

Linux / macOS：

```sh
cd source
# export GSON_JAR=/path/to/gson-2.10.1.jar
./tools/start_preview.sh --port 8766
```

浏览器打开 `http://127.0.0.1:8766/`。默认 **128×128**，提供 **256×256、512×512、自定义长宽**；每一边允许 32–512。界面分别控制道路 8/12 方向和「允许45°建筑区域」。只重新规划会保持地形种子、地形类型、尺寸、高度参数不变；比较只保留上一方案的轻量规划数据。

## 本轮真正完成的部分

| 要求 | 实现落点 | 验证入口 |
|---|---|---|
| 8/12方向随坡、沿等高线延伸 | `TerrainRoadRouter` 的高度状态 A*；代价惩罚挖填、高差、反复升降和转弯 | `real_diagonal_network_*`、`contour_extension_*` |
| 可施工升降段 | 宽路全格标高求解，实际圆石楼梯及上坡朝向，门槛和低层接入连接 | `height_state_stairs_*`、原始编辑方块状态审计 |
| 每栋独立平台高度 | 对实际 footprint 求可行标高交集，以中位数为首选，接入不可行时尝试其他可行高度 | `independent_platforms_and_raw_stair_blocks` |
| 全宽斜向避障 | 中心线闭线段 supercover 加宽；包含长斜段中间格和角点接触格，整份施工再次校验 | 宽1–5、8/12方向、建筑/水/保护区六组用例 |
| 默认128及大地图 | API、仿真、浏览器均支持512和矩形地图；高度场+暴露体素传输 | API实测、128/256/512/192×96/511×257核心测试 |
| 明确资源上限 | 候选、路径展开、状态、等级传播、施工列、编辑、响应体均有限；服务端单规划任务 | 极小预算、超限请求、32 MiB拒绝、HTTP429 |
| 唯一施工真源 | `groundColumns` 指定每格地基/接入/路面高度与楼梯状态；仿真与Fabric共用 `PlanConstruction` | 清单篡改拒绝、施工方块/可步行审计 |
| 回归及公平对照 | 原21个具名回归场景保留；新增29项；原100随机用例；锁定预设同数量同占地对照 | `evidence/current/` |
| 斜向建筑开关 | 打开后加入实际45°栅格化建筑候选；占地、平台、门、方块和预演同步变换 | 开/关对照及40个预设朝向用例 |

**12方向定义**：四个正交方向，加八个对称的 `(±2,±1)/(±1,±2)` 航向。它是有界方格地图上的12个航向，约为0°、26.6°、63.4°、90°等，**不是精确每30°划分**。8方向则是四正交加四45°斜向。长斜段不会跳过中间碰撞格；最终铺装可通过实际四邻格台阶行走。

**斜向建筑定义**：开关允许45°栅格化预设，并不强制每栋都斜放。尺寸约束作用于旋转后的外接尺寸；占地硬约束和施工使用实际栅格掩码，不填满外接框、不事后裁剪以强塞建筑。方块化旋转会有锯齿、重复或省略细小装饰，不是任意角度连续 OBB 建模。

## 当前证据和复现

最新结果只看 `VALIDATION_REPORT_ZH.md` 及 `evidence/current/`。`evidence/historical_input/` 是完整保留的输入证据与旧文档，不是本轮结论。提供的单标高算法被原样冻结到测试目录，除包名外源文件字节一致；校验记录见 `baseline-identity.json`。

```sh
cd source
python tools/build_offline.py --test --evidence build/verification
# 额外运行仅访问本地的 Java HTTP 桥接夹具：
python tools/build_offline.py --test --loopback-tests --evidence build/verification
# 先启动预演服务，再验证实际HTTP接口：
python tools/verify_preview_api.py --out build/api-evidence
# 可选；需本机已有 Playwright 和 Chromium，不会自动安装：
python tools/verify_preview_browser.py --out build/browser-evidence --chromium /path/to/chromium
```

`build_offline.py` 编译真实核心与桥接源码，并运行可执行测试主类；不是 JUnit 或 Minecraft 的替代实现。普通 Gradle/JUnit 入口仍保留，新增 `AdaptiveTerrainJUnitTest` 包装同一套29项测试。当前环境缺少 Gradle/Fabric 依赖缓存，未执行完整 Gradle/JUnit/Fabric 类型编译。

## 默认预算和硬上限

| 资源 | 默认 | 可配置硬上限 |
|---|---:|---:|
| 布局尝试 | 3 | 8 |
| 候选检查 | 24,000 | 200,000 |
| 路径展开（含可达性预筛） | 180,000 | 2,000,000 |
| 单次路由唯一状态 | 100,000 | 200,000 |
| 单次路由开放队列 | 状态上限的2倍 | 400,000 |
| 全局标高传播工作 | 2,000,000 | 10,000,000 |
| 唯一施工列 | 40,000 | 100,000 |
| 去重后的施工编辑 | 500,000 | 1,000,000 |
| 单层附加暴露体素 | 400,000 | 固定；超限拒绝，不截断 |
| UTF-8响应体 | 32 MiB | 固定；超限拒绝，不截断 |
| 单个Java请求 | 90秒超时、`-Xmx1g` | 服务端固定 |

核心最多32栋，浏览器最多12栋；道路宽度1–5，单格道路/平台最大挖填配置范围0–16。服务端只绑定127.0.0.1，同时只执行一个规划任务，竞争请求返回429，不排无界队列。`-Xmx1g` 限制Java堆，并不等于整个服务器进程组RSS不超过1 GiB。

`budgetExhausted=true` 可以与 `COMPLETE` 同时出现：例如已经满足全部建筑，但候选预算已用完，不能继续优化。`ROUTE_OR_SLOT_EXPANSIONS` 表示某一路由或需求份额耗尽，不冒充全局路径预算耗尽。

## 仍然存在的限制

本轮是有界启发式搜索，不是全局最优或可行性完备证明。已确认一个更陡的固定对照场景中，输入单标高版放4栋，新版只放3栋；该例明确列为退化，不能算挖填改善。原100个随机场景本轮也存在PARTIAL结果，不能把硬约束测试通过说成所有需求均满足。

不生成跨水桥、隧道、悬空高架、跨层同坐标道路，不自动降低地块间距、保护规则或数量目标。已有道路标高提交后锁定；宽路等级和楼梯朝向解法保守，可能拒绝仍存在其他工程解的地形。

本轮实际离线编译使用本机已有的 **真实 Gson 2.8.9**，没有伪造 Gson 类，没有改变工程声明的2.10.1，也没有把该本地jar塞进交付包。最终在声明版本和Fabric依赖下的完整构建仍需另行执行。

本次 Chromium 没有可用 WebGL，且原生访问本地页面受到浏览器策略限制。记录中的截图是**Canvas 2D回退**；交互测试注入了指向真实本地HTTP接口的传输桥，同时检查512地图3D几何在CPU上生成且坐标有限。**不宣称已验证原生浏览器HTTP导航、GPU帧率或Minecraft内实际行走。**
