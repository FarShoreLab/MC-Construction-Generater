# 部署边界与本地使用

## 本地浏览器

Java21 + Python3 + 本地Gson2.10.1。进入source目录执行 `tools/start_preview.ps1 --port 8766`（PowerShell）或 `./tools/start_preview.sh --port 8766`。Gson不在当前用户Gradle缓存时显式设置 `GSON_JAR`。页面为 `http://127.0.0.1:8766/`，服务只允许本机访问，所有Three.js资源来自包内vendor文件。

浏览器默认128×128，支持256、512及32–512自定义长宽；独立地形/规划seed，8/12方向道路和45°建筑开关。没有WebGL时自动使用2D兼容视图。可通过表单生成新地形，或只重新规划同一地形。

## Fabric

本包未附新构建的模组jar。本轮未完成Minecraft/Fabric类型编译、启动游戏和实际行走验收。项目原有目标版本及依赖声明保留在 `fabric-mod/build.gradle`；不得根据本文件推断已经可安装到某个存档。

在具备完整依赖的环境，先运行 `./gradlew :core-planner:test :llm-bridge:test :fabric-mod:compileJava`，然后执行与项目目标版本相符的测试实例验收。测试范围需包含真实台阶转角、45°门口、受保护区重扫描、完整编辑预算与平台支撑。不要把语法检查或规划JSON通过当作游戏运行通过。

旧部署文档里的「30ms」「彻底解决」「自动深根」「已打包单jar」等笼统承诺不是本轮结论；原文只作为历史输入保留。实际数据见上一级 `VALIDATION_REPORT_ZH.md`。

## 不触碰配置

本轮浏览器链路不调用LLM，不修改API密钥、账户或网络设置。已有可选LLM桥接代码保留；离线验证只使用锁定预设与本地HTTP测试夹具。
