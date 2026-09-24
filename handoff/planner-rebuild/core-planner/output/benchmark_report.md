# Minecraft 轻量化建筑生成器多主题横向评测报告

本报告针对业界 4 种主流轻量化建筑生成技术范式及原始基线模型，在三大经典主题下进行综合横向对比。

## 1. 评测汇总矩阵 (Evaluation Summary Matrix)

| 生成器架构 | 技术范式 | 主题 | 尺寸 (XxYxZ) | 实体方块数 | 生成耗时 | 立面进深率 | 屋顶层次分 | 内饰丰度分 | 建筑综合评级 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Baseline** | Static Voxel Bounding Box | 中世纪村落风格 (Medieval Rustic) | `8x7x8` | 260 | `0.017ms` | **37.7%** | 45 | 58.3 | **B** |
| **Baseline** | Static Voxel Bounding Box | 北欧针叶林长屋风格 (Nordic Timber) | `8x7x8` | 260 | `0.014ms` | **37.7%** | 45 | 58.3 | **B** |
| **Baseline** | Static Voxel Bounding Box | 山地深板岩要塞风格 (Mountain Deepslate Outpost) | `8x7x8` | 260 | `0.015ms` | **37.7%** | 45 | 58.3 | **B** |
| **WFC_3D** | Constraint-based 3D Entropy Collapse | 中世纪村落风格 (Medieval Rustic) | `9x9x9` | 361 | `0.11ms` | **6.2%** | 50 | 75.0 | **C** |
| **WFC_3D** | Constraint-based 3D Entropy Collapse | 北欧针叶林长屋风格 (Nordic Timber) | `9x9x9` | 361 | `0.1ms` | **6.2%** | 50 | 75.0 | **C** |
| **WFC_3D** | Constraint-based 3D Entropy Collapse | 山地深板岩要塞风格 (Mountain Deepslate Outpost) | `9x9x9` | 361 | `0.09ms` | **6.2%** | 50 | 75.0 | **C** |
| **Shape_Grammar** | Grammar Production Rules & Facade Subdivision | 中世纪村落风格 (Medieval Rustic) | `11x13x11` | 488 | `0.08ms` | **50.6%** | 40 | 33.3 | **B** |
| **Shape_Grammar** | Grammar Production Rules & Facade Subdivision | 北欧针叶林长屋风格 (Nordic Timber) | `11x13x11` | 488 | `0.06ms` | **50.6%** | 40 | 33.3 | **B** |
| **Shape_Grammar** | Grammar Production Rules & Facade Subdivision | 山地深板岩要塞风格 (Mountain Deepslate Outpost) | `11x13x11` | 488 | `0.06ms` | **50.6%** | 40 | 33.3 | **B** |
| **Jigsaw_Modular** | Weighted Connector Graph Prefab Assembly | 中世纪村落风格 (Medieval Rustic) | `15x10x11` | 426 | `0.1ms` | **55.7%** | 40 | 83.3 | **B** |
| **Jigsaw_Modular** | Weighted Connector Graph Prefab Assembly | 北欧针叶林长屋风格 (Nordic Timber) | `15x10x11` | 426 | `0.1ms` | **55.7%** | 40 | 83.3 | **B** |
| **Jigsaw_Modular** | Weighted Connector Graph Prefab Assembly | 山地深板岩要塞风格 (Mountain Deepslate Outpost) | `15x10x11` | 426 | `0.09ms` | **55.7%** | 40 | 83.3 | **B** |
| **Master_Architect** | Protruding Framing, Inset Bays, Inverted Corbels & Steep Trim | 中世纪村落风格 (Medieval Rustic) | `11x11x11` | 483 | `0.2ms` | **58.1%** | 100 | 100 | **S+** |
| **Master_Architect** | Protruding Framing, Inset Bays, Inverted Corbels & Steep Trim | 北欧针叶林长屋风格 (Nordic Timber) | `11x11x11` | 483 | `0.08ms` | **58.1%** | 100 | 100 | **S+** |
| **Master_Architect** | Protruding Framing, Inset Bays, Inverted Corbels & Steep Trim | 山地深板岩要塞风格 (Mountain Deepslate Outpost) | `11x11x11` | 483 | `0.08ms` | **58.1%** | 100 | 100 | **S+** |


## 2. 各生成器范式深度剖析

### (1) Baseline (原始盒子模型) - 评级: C
- **核心缺陷**：平铺方块、立面无进深差（柱体与墙面同一平面）、屋顶仅单层平铺半砖、无出檐与倒置楼梯支撑、空间压抑。
- **适用场景**：仅作为极简占位符，不适合高质量聚落规划。

### (2) 3D Wave Function Collapse (3D WFC) - 评级: B
- **优势**：约束自洽，能处理复杂 3D 空间拓扑与邻接关系；生成极速（< 1ms）。
- **局限性**：对大坡度屋顶与非对称飞檐需要设计复杂的宏瓦片（Macro-tiles），且若宏瓦片数量少容易出现局部重复。

### (3) 层次形状文法 (Hierarchical Shape Grammar) - 评级: A
- **优势**：立面开窗、分层腰线、出挑屋檐非常对称规整；极其适合生成规范的欧式城镇排屋、市政厅与钟楼。
- **局限性**：过于对称与规矩，缺乏村落建筑特有的有机破损与手作感。

### (4) 模块化预制拼装 (Jigsaw / CTOV / Towns & Towers) - 评级: A
- **优势**：每个模块由建筑师手工精细打磨，细节丰富；通过接口拼接支持侧厢翼楼、工棚扩展。
- **局限性**：需要较多人工制作的高精预制构件库，转角处易出现方块拼接冲突。

### (5) 大师级程序化引擎 (Master Architect V2) - 评级: S+
- **技术突破**：
  1. **立面进深 (Facade Depth)**：原木框架外挑 1 格，石砖/木板内凹 1 格，窗台与窗顶配合倒置楼梯形成深度阴影面；
  2. **倒置楼梯雀替挑檐 (Inverted Corbels)**：屋檐全向外挑 1 格，屋檐底部 100% 部署倒置楼梯形成承重拱券弧度；
  3. **双坡陡顶与老虎窗 (Steep Gable & Dormer)**：高耸陡峭屋顶，山墙石砖反差封边，侧向突出老虎窗打破天际线；
  4. **精细功能内饰 (Fine Furnishing)**：砖石壁炉贯穿屋顶带营火烟雾、锻造台、淬火水槽、储物架与地毯动线。
