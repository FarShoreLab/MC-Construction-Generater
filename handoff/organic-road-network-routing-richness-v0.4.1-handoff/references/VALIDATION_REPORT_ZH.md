# 验证报告 · 2026-09-21 / v0.4.0

## 已验证结果与未验证边界

此次重跑上传基线，而不是以交接文档中的“PASS”作证据。原基线21+29+100及桥接通过；当前源码21+29+100、新增25项、桥接与Fabric语法解析通过。25项中另包含88个基数朝向与88个斜向模板变体循环，不将这些内部循环另行包装成数百项独立测试。

本次实际环境是Java21与真实Gson2.8.9（来自现有本地jar）；项目依赖声明仍保留2.10.1。**指定的Gson2.10.1版本未验证**，不把2.8.9冒充2.10.1，也不附系统jar。

**Fabric完整类型编译、Minecraft实机、真实角色行走、浏览器WebGL绘制未验证。** 已通过的Fabric检查仅为Java语法解析。Chromium本地导航被环境限制，自动化浏览器经Python传输桥调用真实本地HTTP/Java API；应用逻辑、实际参数与结果并未替换成mock。浏览器进入了二维兼容视图，包内截图均为这一模式。CPU三维几何有限数检查通过，但不等于WebGL或游戏通过。

## 自动化结果

|套件|退出码|本次运行耗时（秒）|原始日志|
|---|---:|---:|---|
|core-21|0|6.942|`evidence/current/tests/core-21.log`|
|adaptive-29|0|14.895|`evidence/current/tests/adaptive-29.log`|
|fuzz-100|0|27.951|`evidence/current/tests/fuzz-100.log`|
|organic-presets-25|0|7.795|`evidence/current/tests/organic-presets-25.log`|
|bridge-diagonal|0|0.415|`evidence/current/tests/bridge-diagonal.log`|
|fabric-syntax-only|0|0.365|`evidence/current/tests/fabric-syntax-only.log`|
|bridge-loopback|0|2.344|`evidence/current/tests/bridge-loopback.log`|

API另验证11个成功返回场景（包括有意不可行的极小编辑预算）、13个非法边界/选项返回400、单实例并发返回429、32MiB响应界限、512/256/矩形地图、12方向与45°建筑、种子隔离、22个目录、混合形状和单一中庭模板。完整值在 `evidence/current/api/api-metrics.csv` 与 `api-checks.json`。这里的通过指协议与约束结果正确，不表示每个测试都生成了目标数量。

浏览器检查目录22张真实掩码卡片、点选卡片、混合占地、只roll方案保留地形、比较不保留另一份全世界数据、自定义192×96与12方向/45°，无pageerror。512施工CPU网格生成了120个mesh、1825656个顶点、91766个实例，坐标均为有限数。

## 固定场景实测

下表计时来自独立Java导出的一次本机运行，包含JIT/机器负载影响，不作为性能保证。目标均7栋，seed均42/42、道路8方向、关闭45°。

|场景|结果|枢纽/真实分叉枢纽|真实回路/环秩|最长直段比|微锯齿比|核心规划ms|寻路扩展|施工列/编辑|
|---|---|---|---|---:|---:|---:|---|---|
|reference|COMPLETE 7/7|3 / 2|1 / 1|0.5536|0.0000|2290.9|128972/180000|2838 / 16048|
|loop|COMPLETE 7/7|3 / 3|1 / 1|0.2482|0.0000|710.5|14685/180000|1359 / 10132|
|mixed|COMPLETE 7/7|3 / 2|0 / 0|0.4063|0.0000|2483.0|180000/180000|1527 / 10576|
|mixed-low-relief|COMPLETE 7/7|3 / 3|0 / 0|0.0000|0.0000|2183.4|165871/180000|1378 / 9980|

指标定义：最长直段与微锯齿比例取中心线至少30个节点的长道路的最大值；没有这种长道路时字段为0，不代表存在一条“0%直线”的神奇道路。实际阶梯/地面体素仍通过独立施工审计。原始字段、每条走廊节点及控制点均保留在完整PlanningIR。

参考512场景的3条collector、7条local、1条secondary均有真实走廊和施工列；语义E=11、V=11、C=1，环秩1，实际铺装围合1。全部7个门口可达，独立TerrainAudit违规0。参考完整预设仍为原town_hall、artisan_blacksmith与5个nordic_cottage，并未改成新增小屋。

参考候选23993/24000，寻路128972/180000，其中粗格启发扩展19730已经包含在128972内，未另开无上限预算。参考 `budgetExhausted=true` 的原因是某些此前失败的单路/单槽配额触发，不等于总寻路超出180000。原始 `exhaustedBudgets` 保留，不因最终成功清掉历史消耗。

## 地形四通道检测

`evidence/current/terrain-audit/translation-metrics.csv` 保存新旧高度、材质、水、树干在16/32/64/96/128格的X/Z/双对角共20个位移。`autocorrelation-2d.npz` 保存每通道257×257完整二维结果，`lags`定义坐标，JSON保存最可疑局部峰。

旧高度非原点局部峰(86,86)=0.9738；新高度最强对应搜索范围内局部峰(80,80)=0.1722。这里搜索排除32格以内且找局部峰，并非声称所有近邻相关都低于0.18。水体和材质仍应在自然相邻区域相关。

新64×64完全相等滑窗中，高度和材质没有非恒定重复结构；水与树的11个候选各只有1个少数类像素，单独列为孤立点巧合。保留初版将其直接标记失败的诊断JSON及更明确的判定说明；最终没有发现包含多点结构的重复瓦片。低信息背景的相等不等于发生复制。

四种地形的完整体素hash重复生成相等，换terrainSeed发生变化；固定地形仅换planSeed时，原地形不变且语义边图改变。旧CLI另有明确测试，(86,86)高度相关0.0611，换seed有248682/262144格高度变化。

## 不只展示成功样本

随机100例：94例COMPLETE、6例PARTIAL、0例无建筑，合计安置294栋，全部硬约束审计违规0。部分分配的seed为 **7、9、18、28、29、39**，每例2/3栋；原始需求、障碍生成方法、预算与100行结果均可复跑。用约束审计通过冒充100%放置成功是不正确的。

`mixed`（128丘陵、relief12）虽然7/7，默认寻路180000用尽，只有主路和局部接入，没有可验证回路。`mixed-low-relief`也7/7但冗余边在有界尝试中失败，没有回路。这不是证明这些世界全局无解，只是当前算法及预算没有找到；原因与尝试保留在IR。

API的 `budget_rejection` 将施工列与编辑上限各设为1，返回INFEASIBLE、0栋、0编辑，是预期拒绝而非偷偷截断施工。滚动seed43及多个其他场景存在单路或总预算触发；完整API表、所有fuzz行及 `LIMITATIONS_AND_FAILURE_CASES.json` 收录这些记录。

core/adaptive中的封闭河岸、无空间、无法跨越高差等失败夹具同样保留完整IR；其正确拒绝是测试目标。原始benchmark包含较早历史对照实现，不能与本包上传0.3.0基线的重新执行记录混淆。

## 验收矩阵

|要求|证据/结论|
|---|---|
|固定种子、两种seed分离|25项测试、API重复与roll检查|
|四通道平移与二维重复诊断|terrain-audit四通道/20位移/完整二维数组|
|参考7栋且至少2真实分支、1闭环|reference512具名测试、完整IR、独立施工审计、API|
|主路/二级/每栋局部接入|reference 3/1/7条，施工前消费者校验|
|曲线不退化长直线、无高频ABAB|参考长路阈值0.80、实测0.5536；ABAB=0|
|回路不是厚道路内部小格环|最小宏观围合、真实新增铺装、伪边/缺列/环数篡改拒绝|
|不同大小/形状、旋转|22模板目录；88+88变体；7个不同模板混合|
|中庭/凹口不按外接框填满|两种保护区夹具与PlanConstruction编辑核查|
|全宽/水/保护/独立标高/编辑上限|core21、adaptive29、100随机与TerrainAudit|
|浏览器原始/规划/施工截图|reference512、loop128、mixed128各3张二维真实截图|
|Fabric/Minecraft/WebGL实机|未验证；仅Fabric语法和CPU几何通过|
|准确指定Gson2.10.1|未验证；本次真实执行2.8.9，声明未改|

## 复跑

在 `source/` 设置自己的 `GSON_JAR` 后：

```sh
python tools/build_offline.py --test --loopback-tests --evidence build/verification
python tools/export_release_evidence.py --out build/release-scenes
python tools/audit_terrain_repetition.py --current build/release-scenes/reference/terrain-fields.json --out build/terrain-audit
python tools/preview_server.py --port 8766
# 另一终端：
python tools/verify_preview_api.py --out build/api-evidence
python tools/verify_organic_browser.py --out build/browser-evidence
```

基础编译和预览不新增Python第三方包；二维自相关脚本需要本地NumPy/SciPy，浏览器自动化需要本地Playwright/Chromium。脚本不会下载安装它们。浏览器脚本可用 `--chromium` 指定已安装浏览器路径。
