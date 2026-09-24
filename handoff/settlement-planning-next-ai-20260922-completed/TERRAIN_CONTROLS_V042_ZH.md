# 地形包 v0.4.2：使用与参数说明

## 这次改了什么

把 v0.4.1 的原地形生成器整理为可以直接调用、采样和调节的接口。保留起伏丘陵、山地脊线、环谷、台地四种生成方式；没有加入侵蚀模拟、笔刷、气候、生物群系或另一套地形算法。

原来的公开函数及默认结果保持。新增10个参数都接入真实高度、积水或树木生成，而不是预览效果。现有聚落规划、建筑预设、道路算法和施工预算继续保留；本版本不重做全图的片区布局，也不把地图利用率改善作为已完成结果。

### 三个使用入口

1. **浏览器调节**：滑杆和数值框同步，旁边直接说明“调低／调高”。前三项常用参数展开，轮廓／类型参数和树木参数可折叠。不适用于当前类型的项自动隐藏。
2. **HTTP接口**：`GET /api/terrain` 只生成真实地形；`GET /api/plan` 将同样参数传入原规划链。`GET /api/terrain-controls` 返回参数名、范围、默认值、适用类型和中文解释。
3. **Java接口**：原 `generateHillsideWorld(...)`、`generateWorld(...)` 保留；新增带 `TerrainParameters` 的重载及 `sampleSurfaceY(...)`。单点采样与体素生成调用同一份高度公式，不分叉维护。

## 启动

先结束旧预演服务，避免浏览器仍连到旧版本。解压后进入本包的 `source` 目录：

```powershell
python tools/build_offline.py
python tools/preview_server.py --port 18767
```

浏览器打开：

```text
http://127.0.0.1:18767/
```

沿用 Java 21、Python 3 和已有 Gson 依赖；没有新增第三方运行时依赖。离线脚本优先读取本机 Gradle 缓存中的 Gson 2.10.1。找不到时，显式指定自己的真实 Gson jar：

```powershell
$env:GSON_JAR = 'C:\deps\gson-2.10.1.jar'
python tools/build_offline.py
python tools/preview_server.py --port 18767
```

现有 `tools/start_preview.ps1` / `tools/start_preview.sh` 也可继续使用。包内不附带本机依赖、Java 编译缓存或字体。

## 推荐操作顺序

固定地图尺寸、类型、种子；先调**起伏、横向尺度**，再调**水位、细节**，最后处理类型专属项和树木。每次只动一两项，点击“只生成地形”看实际结果，不必同时跑建筑规划。

“只重新规划”保留**上次已经生成**的全部地形参数，只修改规划侧输入；尚未提交的地形滑杆值不会暗中改变地图。“重置调节项”只恢复新增10项的本类型原版默认值，不改尺寸、种子、起伏、基准Y或建筑设置。切换类型时，尚未手动偏离默认值的相对水位跟随类型默认值；自定义水位保留。

地形-only 响应没有伪造的建筑、道路、施工结果或 COMPLETE 规划状态。浏览器此时只显示原始地形和地形统计；重新生成规划后，原规划／施工／同地形方案比较功能恢复。

## 原有基础参数

| 参数 | 范围／默认 | 调低 | 调高／改变 |
|---|---|---|---|
| 地图宽度、深度 `width/depth` | 浏览器及HTTP：32–512，默认128×128；Java生成器仍支持1–512 | 可见区域减小 | 扩大采样区域，不自动扩大山丘尺度，也不增加建筑数量 |
| 起伏 `relief` | 4–36，默认24 | 高低差收小；整数化后可能更平 | 高低差拉大；同样水平距离下通常更陡 |
| 基准Y `baseElevation` | 50–75，默认58 | 地面与相对水位整体下移 | 地面与相对水位整体上移；不改变坡度或水平分布 |
| 地形种子 `terrainSeed` | 浏览器／HTTP为安全整数；Java为long，默认42 | 没有“调低”方向含义 | 更换确定性的空间分布；不是数值越大山越高 |
| 地形类型 `terrainType` | 四类，默认 `rolling_hills` | 不适用 | 选择下表对应公式；不是复杂度等级 |

四类仍是原实现：丘陵使用粗、中、细三层噪声加对角倾斜；山地使用噪声脊线叠加起伏；环谷以地图中心低、外围高为基础；台地将原起伏分级再叠加细噪声。

## 新增10项：默认、范围及调节结果

除特别注明外适用于全部四类。**相同其他参数下的预期**，不能把多个参数同时改变的结果归因于其中一个。

| 参数／API键 | 默认；范围 | 调低的预期 | 调高的预期 |
|---|---|---|---|
| 地形横向尺度 `horizontalScale` | 1；0.25–4 | 山丘、脊线更小更密，坡变通常更频繁 | 山丘、脊线更宽更疏；小地图可能只看到一个大坡 |
| 细节起伏强度 `detailStrength` | 1；0–2 | 细碎起伏减少；0只关闭细噪声 | 局部小起伏增加；不直接增大山丘总体高度 |
| 相对水位 `waterLevelRatio` | 环谷0.28，其余0.22；0–1 | 水只留在更低洼处；0完全无水 | 同一地表下水域扩大或不变，可能连成大片水面 |
| 形状扭曲强度 `warpStrength` | 1；0–2 | 轮廓扭曲减弱；0关闭坐标扭曲 | 原噪声轮廓的偏移、弯曲更明显；不保证山更高、水更多 |
| 整体倾斜强度 `slopeStrength` | 1；0–2；仅丘陵／台地 | 全图定向高低差减弱；0去掉这项倾斜 | 沿X、Z同时增大的对角方向，高低差增大 |
| 山脊收窄系数 `ridgeSharpness` | 1.65；0.5–3；仅山地 | 高脊带更宽，整体偏高 | 高脊带收窄、两侧降低；**不是把山峰抬高** |
| 谷地展开宽度 `valleyWidth` | 1；0.5–2；仅环谷 | 中心低地较窄，外围更快升高 | 低地向外展开；水位不变时水域也可能增大 |
| 台地分级数 `terraceLevels` | 5；整数2–12；仅台地 | 台面级别少、每级落差大 | 台面级别多、每级落差小；仍是阶地，不保证连续缓坡 |
| 树木密度倍率 `treeDensity` | 1；0–2 | 树木减少；0关闭树木 | 树木增加直至筛选饱和；2不保证树数翻倍 |
| 树木间距控制 `treeSpacing` | 4；整数2–10 | 允许树长得更近，数量更多 | 树间距增大、数量减少，边缘留白可能变宽 |

### 容易理解反的几个地方

**横向尺度不是地图利用率。** 它缩放高度噪声及其坐标扭曲的水平尺度，不缩放地图宽深、不扩展建筑片区、不更改环谷径向尺度，也不改变树木间距。地形由不同区域构成，不等于当前规划器会选中所有区域。

**水位不是水面面积百分比。** 水平面由下式确定，并只填充地表以下的低洼空间；不重新挖湖盆：

```text
waterY = baseElevation + floor(relief × waterLevelRatio)
```

例如基准58、起伏24，相对水位0.22得到水面Y=63，0.28得到Y=64；地表Y≥水面Y的格不积水。水面Y按整数变化，因此小幅拉动水位可能暂时没有变化。改变水位会影响河床表层材质、树木避水和后续建筑可用地，但不会改地表高度本身。

**“细节0”不等于完全平地。** 粗、中尺度噪声保留。台地如需更平的台面可先将细节设为0，但地形量化和台地边缘仍存在。

**台地分级少不等于更容易修路。** 单级落差可能反而增大。谷地变宽也可能扩大湖面，而不是自动增加可建设陆地。

**树木间距不是整齐网格。** 它调节原有局部最小优先级筛选的邻域半径。半径r保留 `dx²+dz² ≤ r²+r` 内的比较；默认r=4正好恢复原条件≤20。树冠允许交叠；密度调节不改变地表高度或水体。

API可携带全部10项以便重放；类型不适用的参数会原样回显，但不影响该类型输出。界面只展示适用项。

## 原生成函数与直接调用

源码位置：

```text
core-planner/src/main/java/org/mcsettlement/planner/simulation/TerrainBlockGenerator.java
core-planner/src/main/java/org/mcsettlement/planner/simulation/TerrainParameters.java
core-planner/src/main/java/org/mcsettlement/planner/terrain/SpatialNoise.java
```

原调用无需修改：

```java
TerrainBlockGenerator.generateHillsideWorld(width, depth, baseY, relief, terrainSeed);
TerrainBlockGenerator.generateWorld(width, depth, baseY, relief, terrainType, terrainSeed);
```

调节与单点采样示例：

```java
import java.util.Map;
import org.mcsettlement.planner.simulation.*;

String type = "rolling_hills";
TerrainParameters parameters = TerrainParameters.fromOverrides(type, Map.of(
    "horizontalScale", "1.5",
    "detailStrength", "0.5",
    "waterLevelRatio", "0.15",
    "treeDensity", "0.5"
));
// 省略项使用所选地形类型的原版默认值；未知键或越界值抛出异常。
SimulatedVoxelWorld world = TerrainBlockGenerator.generateWorld(
    128, 128, 58, 24, type, 42L, parameters
);
// 同一高度公式；没有创建第二张体素地图，也不执行规划。
int surfaceY = TerrainBlockGenerator.sampleSurfaceY(
    64, 64, 128, 128, 58, 24, type, 42L, parameters
);
int waterY = TerrainBlockGenerator.waterLevel(58, 24, parameters);
```

`generateHillsideWorld(..., seed, parameters)` 同样可用。`TerrainParameters` 是不可变Java record，可直接构造；一般优先使用 `defaults(type)` 或 `fromOverrides(type, map)`，避免十个位置参数写错。

单点采样的x/z为地图内局部坐标。它返回**土石／草皮地表Y**，不是水面或树冠高度。直接使用生成器只需本项目的 `TerrainBlockGenerator`、`TerrainParameters`、`SimulatedVoxelWorld`、`SpatialNoise` 四个类和JDK；不依赖JSON库或Minecraft运行时。

若要生成并扫描地形、但不规划，可调用：

```java
SimulatedSettlementPipeline.generateTerrain(
    width, depth, baseY, relief, type, terrainSeed, parameters
);
```

其结果的 `plan`、`worldAfter` 为null；不得当作已完成施工。完整 `run(...)` 保留原签名，并新增最后一个参数为 `TerrainParameters` 的重载。

## HTTP例子与返回值

以下为完整本地请求示例，不需要另搭服务：

```text
http://127.0.0.1:18767/api/terrain?width=256&depth=256&terrainType=rolling_hills&terrainSeed=42&relief=16&horizontalScale=1.5&detailStrength=0.5&waterLevelRatio=0.18&treeDensity=0.5
```

要用同一地形进行规划，将路径改为 `/api/plan`，再追加原有 `planSeed`、`targetPlots` 等规划参数。地形专用 `/api/terrain` 不接受规划参数，以免产生“已使用这些设置”的误解。

`/api/terrain` 返回 `kind: TERRAIN_ONLY`，包含：

- `config`：尺寸、基准、起伏、类型、种子、完整 `terrainParameters`、实际 `waterLevel`、`terrainGeneratorVersion`、`originalTerrainHash`。
- `original`：沿用现有紧凑高度、材质、水位和地上可见体素格式。数组索引为 `z * width + x`。
- `metrics`：实际最小／最大／平均地表Y、水域格数与比例、树木株数、平均相邻格高差。不包含规划耗时或施工统计。

平均相邻格高差统计所有水平／竖直相邻格的 `abs(h1-h2)` 平均值，不是道路坡度审计，也不是道路可行性证明。树木株数按实际地表上方的树干列计数。它们用于观察调参结果，不加入新的规划目标。

完整 `/api/plan` 保留原响应，并增加相同参数回显；原 `PlanningIR` 版本与施工真源关系不变。对未知参数、空值、重复键、NaN／Infinity、越界或非整数分级／间距返回HTTP 400。界面滑杆的step是交互步长，非整数型参数的数值框和API可以使用范围内其他有限小数；滑杆按说明步长变化。

## 预算与边界

保留地图≤512×512、体素分配上限、完整规划搜索／施工上限、响应≤32MiB、每层地上可见体素≤400,000、服务单请求≤90秒、并发请求429。树木密度很高且间距很小可能触发可见体素上限；会明确拒绝，不截断场景或静默调小参数。实测512×512、密度2、间距2、无水的组合返回明确的 `EXTRA_VOXEL_LIMIT_NO_TRUNCATION`，没有交付残缺地形。

本地预演仍是有限范围的模拟地形；没有改真实Minecraft地形生成器或Fabric扫描器。更改参数可能让原规划返回PARTIAL／INFEASIBLE，这不是提高预算或放宽碰撞约束的理由。

## 复测命令

```powershell
python tools/build_offline.py --test --loopback-tests
python tools/preview_server.py --port 18767
# 另一个终端，仍在source目录：
python tools/verify_terrain_controls.py --base http://127.0.0.1:18767
python tools/verify_preview_api.py --base http://127.0.0.1:18767
# 可选：本地已安装Playwright与Chromium时，执行真实浏览器检查：
python tools/verify_terrain_browser.py --base http://127.0.0.1:18767 --chromium 'C:\path\to\chrome.exe'
```

修改生成公式时以 `TerrainBlockGenerator` 为准；修改参数默认值或范围时，同时更新 `TerrainParameters` 和 `terrain-controls.json`，并运行参数一致性测试。不要只改界面的说明元数据。

实际测试结果和环境边界见包根目录 `VALIDATION_ZH.md`。两种浏览器检查不应与API套件同时运行：原服务只允许一个活动请求。
