# 本轮验证结果入口

只引用 `../VALIDATION_REPORT_ZH.md` 和 `../evidence/current/`。旧测试记录在 `../evidence/historical_input/`，不得混作本轮成功证据。

可复现入口：`python tools/build_offline.py --test --evidence build/verification`。可选本地桥接HTTP夹具加 `--loopback-tests`。浏览器服务另行用 `tools/verify_preview_api.py` 和 `tools/verify_preview_browser.py` 检查。

Fabric语法解析不等于类型编译；CPU三维网格生成不等于WebGL绘制；2D回退截图不等于游戏内验证。公平土方比较需同时查看两侧建筑数量和footprint面积；不满足同数量者只报告为退化，不算改善。
