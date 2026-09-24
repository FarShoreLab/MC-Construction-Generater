# 聚落选址专家包 v0.5.0

本包是可运行的修改版，不是规划建议或交接需求。基于 v0.4.2，保留原四种地形、10项地形调节、22个建筑预设和旧版对照模式；新增先选址后道路、建筑空间展开硬约束、人工锁定、强制连接、桩桥、水上桩基建筑和简易码头。

## 启动

先关闭旧的本地预演服务，避免浏览器连到旧版本。解压后进入 `source`：

```powershell
python tools/build_offline.py
python tools/preview_server.py --port 18767
```

浏览器打开 `http://127.0.0.1:18767/`。需要 Java 21、Python 3 和本地真实 Gson。默认查找原项目 Gradle 缓存中的 Gson 2.10.1；找不到时指定：

```powershell
$env:GSON_JAR = 'C:\deps\gson-2.10.1.jar'
python tools/build_offline.py
python tools/preview_server.py --port 18767
```

没有增加第三方运行时依赖。本包不附带本机 Gson、编译缓存或字体。实际验证环境见 `VALIDATION_ZH.md`。

## 1. 这次改变的是布局顺序

新版默认“选址专家 · 先建筑后道路”。人工锁定项先处理，然后优先处理公共／关键建筑；自动建筑使用中心与多个区域锚点产生候选，并结合平台土方、滨水距离和高地偏好评分。完整建筑掩模、门口、私人入口路径和道路接入空间同时预留。整组建筑通过占图与非线性检查后，才开始铺路。

这是确定性的规则系统，不是语言模型。没有侵蚀、生态模拟、船舶、经济系统或另一个渲染专用规划器。区域锚点是选址的软引导，不能越过完整占地、标高和施工约束。3栋的小聚落使用三个区域；4栋及以上允许中心加外围区域。

最多3个宏观布局，共用原候选、路径、标高及施工预算。道路失败可以换下一个已选布局；没有把每个备选重新发放一整份预算。自动候选来自全图，但这不是穷举所有位置，也不是承诺任意种子都存在合格方案。

旧版“沿路生长（对照）”仍可选择。原 Java 调用默认行为保持；浏览器和 HTTP 默认已切换为专家模式。

## 2. 占图与瘦长惩罚：不是看道路画得有多大

| 参数 | 默认／范围 | 调低 | 调高 |
|---|---|---|---|
| 最小建筑包围框占图 `minBBoxCoverage` | 25%；0—85% | 允许集中在较小区域 | 要求实际建筑在地图上展开更广；可能拒绝更多方案 |
| 最小短／长轴比 `minMinorAxisRatio` | 30%；0—90% | 允许更瘦长的分布 | 要求更面状、各方向更均衡的分布；不是强制方形 |
| 连续涉水路段上限 `maxBridgeSpan` | 48格；整数4—96 | 较宽水域的连接被拒绝 | 可尝试更长的连续水上通路，但仍受总预算约束 |

包围框覆盖率 = **所有实际建筑占地格的轴对齐包围框面积 ÷ 全地图面积**。不计道路和码头，水上建筑计入。它是布局展开范围，不是建筑铺满率、已施工面积或可建设土地利用率；框内可以有湖泊和留白。

仅大框仍可能是斜着排成一串。因此，≥3栋还检查按地图宽／深归一化后的建筑中心短／长轴比，以及建筑中心凸包面积。凸包最低值随包围框门槛联动，为其35%，默认8.75%。缺额平方罚分用于记录／比较，最终必须同时通过硬门槛。

1—2栋不检查无意义的中心轴比／凸包，但包围框门槛仍保留。小数量、刻意集中布局应自行降低门槛，程序不会偷偷降低。不会把远处的码头或长引路当作“占图达标”。

## 3. 人工指定位置与强制连接

展开“人工指定建筑／码头”，填写唯一英文 ID，选陆地建筑、水上桩基建筑或码头。选择预设与门朝向，用“从地图点选 X/Z”或数值框指定位置，点击“添加／更新同 ID 约束”，再生成或重新规划。

**X/Z 锁定的是旋转后占地框的最小坐标，不是房屋中心或门口。** 预设完整尺寸不变。平台Y留空表示只在这个位置选择可行高度；填数值则高度也锁定。人工建筑计入目标总数，码头不计；人工项共最多8个。

“强制连接对象”可填：

- `network`：必须接入同一个实际道路网络。
- `entry`：保留该对象到入口的明确端点路线。
- 另一个人工 ID：保留两者之间的明确端点到端点路线，可复用已有路段，不承诺另建完全独立的平行道路。

两个指定对象可以相互引用。不存在的 ID、重复 ID、自指、越界、未知预设会拒绝。编辑同 ID 更新同一约束；删除被引用的对象后，其他约束不会被自动改成别的目标，而是提示失效引用。

“只重新规划”会保留全部人工位置、预设和连接要求，同时沿用上次已生成地形；不会误用尚未提交的地形滑杆值。改变地图尺寸后越界的约束也不会自动搬家。

**强制是必须满足，不是越过施工限制。** 指定位置重叠、无法放下完整建筑、标高不可能或在预算内无法连接时，整案 `REJECTED`，0个施工编辑。页面保留候选诊断和原因，不能将它读成一个已建成方案。预算内失败不等于证明所有布局都不存在。

## 4. 桥梁、水上建筑与码头

勾选“允许桩桥和水上桩基”后，真实寻路可以经过水体。桥面在原水面上一格；连续涉水段只允许同一水平标高、直行，不允许在水中斜跳或转弯。桥头仍需通过完整道路宽度、陆地坡度和真实台阶检查。

水下是间隔桩，不是把水域填成实心堤坝。桩从原河床上一格连到桥面下，最大高度16格；桩列按固定可复现规则 `(x+z) mod 4 == 0` 生成。48格是连续涉水中心线路段限制（含刷宽接岸部分），**不是没有桥墩的悬空跨度**。这些是方块施工规则，不是现实桥梁承载设计。

水上建筑通过人工项的“水上桩基建筑”创建，使用原22个完整预设，允许占地的一部分在岸上，但至少有实际占地格覆盖水面；水上部分生成真实桩基，陆地部分仍遵守挖填限制。不是浮岛或漂浮建筑，也没有自动给所有建筑改成水上版。

码头本轮是从岸路伸向指定水点的可步行桩式栈道端，不包含船、泊位管理或码头专用房屋。人工码头是必达项；“尝试一个可选码头”只在余下预算内尝试，失败会记录 `OPTIONAL_DOCK_NOT_FOUND_IN_BUDGET`，不妨碍其他已满足硬条件的布局。

桥面、入口、水上基础和桥桩全部由 `PlanningIR.groundColumns` 授权，再交给同一个施工程序。新版专家 IR 为0.5.0，旧模式继续输出0.4.0；旧消费端不能把水上桥面误当成普通填土。

### 可直接复现的水上建筑

128×128，起伏丘陵，基准58，起伏12，地形种子42，规划种子42，混合预设，其他地形参数保持默认。加入：

```json
{"id":"water_house","kind":"building","presetId":"square_cabin","medium":"water","x":100,"z":56,"facing":"EAST","connectTo":"network"}
```

目标建筑7。该输入在本次真实 Java API及浏览器中完成了1栋水上建筑、实际桥面／桩基和1个可选码头；完整请求见 `examples/water-house.json`，不是仅用于截图的摆件。

## 5. 程序接口

原 `GET /api/terrain`、`GET /api/plan` 和10项地形参数继续使用。人工约束建议用 `POST /api/plan`，`Content-Type: application/json`，参数含义与 GET 一致：

```json
{
  "width":128,"depth":128,"terrainType":"rolling_hills",
  "terrainSeed":42,"planSeed":42,"relief":4,"waterLevelRatio":0,
  "targetPlots":7,"presetPalette":"mixed","layoutMode":"expert",
  "minBBoxCoverage":0.25,"minMinorAxisRatio":0.30,
  "allowBridges":true,"autoDock":true,"maxBridgeSpan":48,
  "pins":[
    {"id":"home","x":20,"z":20,"facing":"EAST","connectTo":"other"},
    {"id":"other","x":95,"z":85,"facing":"WEST","connectTo":"network"}
  ]
}
```

```powershell
# 在包根目录执行：
Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:18767/api/plan' `
  -ContentType 'application/json' -InFile 'examples/manual-pins.json'
```

返回的 `config.expertSettings` 是实际使用的约束；`plan.sitePlanning`／`metrics.sitePlanning` 包含规则选址理由、候选失败、空间指标、人工锁定和必达路线结果。`groundColumns` 仍是施工唯一真源。

POST≤16KiB，人工 JSON≤8KiB；拒绝重复键、未知字段、非整数坐标、非有限数。单活动请求、90秒超时、32MiB响应上限保留。旧模式携带人工项会报错，不会无声忽略。

Java 原签名不变。启用专家模式：

```java
var request = new SettlementPlanner.PlanRequest();
request.expert = new ExpertSettings();
request.expert.minBBoxCoverage = 0.25;
request.expert.pins = ExpertSettings.parsePins(json);
PlanningIR plan = SettlementPlanner.plan(heightfield, request);
```

模拟流水线新增最后一个参数为 `ExpertSettings` 的 `run(...)` 重载。原签名传入空专家配置，继续旧版；原 LLM intent／游戏内规划界面的默认请求行为未自动改成专家模式，游戏端调用方需设置 `request.expert`。浏览器的完整人工编辑器不等同于已经移植到游戏界面。

游戏施工消费端已接入0.5水上清单与重扫预检：仅实际授权的桩位置可以替换水方块，不把水普遍视为可清除材料。完整 Fabric 类型构建、Minecraft运行和原生WebGL未验证；本次实测为真实Java流水线及浏览器2D兼容视图。

## 6. 验证与文件

```powershell
cd source
python tools/build_offline.py --test --loopback-tests
python tools/preview_server.py --port 18767
# 另开终端，依次执行，不要同时竞争单请求服务：
python tools/verify_site_expert.py
python tools/verify_preview_api.py --base http://127.0.0.1:18767
python tools/verify_terrain_controls.py
python tools/verify_site_expert_browser.py --chromium 'C:\path\to\chrome.exe'
```

浏览器验证需要本地已安装 Playwright 和 Chromium，不自动下载。旧API回归显式选择 `layoutMode=legacy`，新专家模式由独立套件覆盖。

`source/` 为完整源码；`changes.diff` 以原 v0.4.2 的 source 为根应用；`VALIDATION_ZH.md` 为实际结果；`evidence/visual/index.html` 是全地图对比；`evidence/browser/` 是实际页面截图；`examples/` 是可重放请求。所有图均来自真实输出，未在预览层重新布置建筑或另画道路。
