# 实机路线明细

时间为实机探针发出导航后的墙钟秒数。运动控制器持续时间包含规划与等待，并非纯移动时间；静止与无控制器时间相互重叠，不能相加当作CPU时间。

## 102层连续跳（局部实机）：通关 6.187s

原始结果：`build/parkour-evidence/raised-platform-20260928/live-platform-102/result.json`；统计适配文件为同目录`analysis-input.json`。

静止 0.820s；控制器时间：`{"noMovement": 0.654, "MovementParkour": 5.493}`。

事件计数：`{"astar_pop": 7, "surface_candidates": 2, "surface_edge": 46, "astar_move": 219, "search_timing": 3, "planned_route": 1, "search_submitted": 2, "flow_contact": 8, "trajectory_plan": 2, "search_result": 2, "trajectory_prefetch": 1, "traj_apply": 102, "trajectory_chain_handoff": 6}`。

### 最长静止区间

| 开始秒 | 持续秒 | 位置 |
|---:|---:|---|
| 0.226 | 0.820 | [11.5, 102.0, -118.0] |

### 搜索计量

墙钟/CPU为任务累计值，可与运动及其他线程重叠；不是通关时间的分拆。未观测结果不等于失败。

| 类别 | 任务/计时 | 排队秒 | 计算墙钟秒 | 线程CPU秒 | 分配MiB | 结果 |
|---|---:|---:|---:|---:|---:|---|
| graph | 1/1 | 0.012 | 0.339 | 0.281 | 111.7 | {'unobserved': 1} |
| flow | 1/1 | 0.001 | 0.443 | 0.422 | 346.2 | {'applied': 1} |
| prefetch | 1/1 | 0.000 | 0.314 | 0.281 | 217.6 | {'applied': 1} |

JVM采样覆盖：`{"samples": 6, "coveredSeconds": 5.002, "gcCount": 5, "gcMillis": 29, "maxObservedHeapBytes": 366862824}`。

### 实际控制器逐段路线

| 开始秒 | 持续秒 | 控制器 | 起点 → 终点 |
|---:|---:|---|---|
| 0.001 | 0.654 | 无控制器 | — |
| 0.655 | 1.052 | MovementParkour | [11, 102, -118] → [11, 103, -122] |
| 1.707 | 0.704 | MovementParkour | [11, 103, -122] → [11, 102, -126] |
| 2.411 | 0.496 | MovementParkour | [11, 102, -126] → [11, 103, -129] |
| 2.907 | 0.495 | MovementParkour | [11, 103, -129] → [11, 104, -133] |
| 3.402 | 0.944 | MovementParkour | [11, 104, -133] → [11, 103, -138] |
| 4.346 | 0.702 | MovementParkour | [11, 103, -138] → [11, 103, -142] |
| 5.048 | 0.301 | MovementParkour | [11, 103, -142] → [11, 103, -144] |
| 5.349 | 0.799 | MovementParkour | [11, 103, -144] → [11, 102, -149] |

### 失败及恢复事件
