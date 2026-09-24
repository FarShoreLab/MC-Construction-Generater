# v0.4.1 路线丰富度

新增实现集中在 OrganicGuide、TerrainRoadRouter、OrganicRoadNetwork、RouteQualityMetrics 和 PlanningIR附加诊断字段。地形、预设、布局以及PlanConstruction施工真源未改。算法标识为clustered_plazas_mst_multiguide_v041；IR结构版本保持0.4.0。

构建/测试命令（Java21、本地真实Gson）：

```sh
python tools/build_offline.py --test --loopback-tests
python tools/verify_route_richness.py --out build/route-richness --repeat
python tools/preview_server.py --port 18767
# 另一个终端
python tools/verify_preview_api.py --base http://127.0.0.1:18767 --out build/api-verification-v041
```

GSON_JAR可覆盖本机Gradle缓存查找。源码没有捆绑依赖下载，也没有改变原Gson2.10.1声明。

固定4丰富/1保守/1无guide候选、24步直行记忆、全宽与坡度验证后的有限真实中心线修整。总pathExpanded默认180000不变；单次修整从同一预算扣最多2048。新增质量字段是审计数据，不授权施工。

参考512保留32/23步直段、mixed128保留24/17步直段；复现512追加ring仍有失败原因。不能把新增诊断或ABAB=0当作每条路线都自然弯曲的证明。完整实现说明和实测报告在完整包上层目录CHANGELOG_V041_ZH.md / VALIDATION_V041_ZH.md，证据在../evidence/v041/。

各旧README/REBUILD报告描述的是先前版本；本轮只更新路线核心，不重做旧文档的历史记录。
