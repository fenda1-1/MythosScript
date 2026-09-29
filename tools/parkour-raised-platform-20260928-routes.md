# 实机路线明细

时间为导航请求开始后的墙钟秒数；旧探针未直接记录开始时间戳，以采样 timestampMs − elapsed 的中位数对齐日志，最大样本对齐差为 22.0ms。到达时间来自原探针单调时钟，额外1秒落稳检查不计入。运动控制器持续时间包含规划与等待，并非纯移动时间；静止与无控制器时间相互重叠，不能相加当作CPU时间。

## live-raised：通关 1.140s

原始结果：`analysis-input.json`。

静止 0.397s；控制器时间：`{"noMovement": 0.391, "MovementParkour": 0.706}`。

事件计数：`{"astar_pop": 2, "surface_candidates": 1, "surface_edge": 9, "astar_move": 73, "search_timing": 2, "planned_route": 1, "search_submitted": 1, "flow_contact": 1, "trajectory_plan": 1, "search_result": 1, "traj_apply": 14}`。

### 最长静止区间

| 开始秒 | 持续秒 | 位置 |
|---:|---:|---|
| 0.000 | 0.397 | [8.5, 30.0, -0.5] |

### 搜索计量

墙钟/CPU为任务累计值，可与运动及其他线程重叠；不是通关时间的分拆。未观测结果不等于失败。

| 类别 | 任务/计时 | 排队秒 | 计算墙钟秒 | 线程CPU秒 | 分配MiB | 结果 |
|---|---:|---:|---:|---:|---:|---|
| graph | 1/1 | 0.006 | 0.145 | 0.125 | 59.4 | {'unobserved': 1} |
| flow | 1/1 | 0.002 | 0.051 | 0.062 | 13.8 | {'applied': 1} |

JVM采样覆盖：`{"samples": 1, "coveredSeconds": 0.0, "gcCount": 0, "gcMillis": 0, "maxObservedHeapBytes": 352305368}`。

### 实际控制器逐段路线

| 开始秒 | 持续秒 | 控制器 | 起点 → 终点 |
|---:|---:|---|---|
| 0.000 | 0.391 | 无控制器 | — |
| 0.391 | 0.706 | MovementParkour | [8, 30, -1] → [8, 31, -3] |

### 失败及恢复事件


## live-doorway：通关 1.750s

原始结果：`analysis-input.json`。

静止 0.301s；控制器时间：`{"noMovement": 0.106, "MovementParkour": 1.593}`。

事件计数：`{"astar_pop": 3, "surface_candidates": 2, "surface_edge": 6, "astar_move": 146, "search_timing": 2, "planned_route": 1, "search_submitted": 1, "flow_blocked": 1, "trajectory_pre_hop_failed": 1, "trajectory_search_start": 1, "trajectory_corner": 1, "trajectory_plan": 1, "search_result": 1, "traj_apply": 28}`。

### 最长静止区间

| 开始秒 | 持续秒 | 位置 |
|---:|---:|---|
| 0.049 | 0.301 | [12.304735, 97.0, -97.621767] |

### 搜索计量

墙钟/CPU为任务累计值，可与运动及其他线程重叠；不是通关时间的分拆。未观测结果不等于失败。

| 类别 | 任务/计时 | 排队秒 | 计算墙钟秒 | 线程CPU秒 | 分配MiB | 结果 |
|---|---:|---:|---:|---:|---:|---|
| graph | 1/1 | 0.001 | 0.010 | 0.016 | 2.8 | {'unobserved': 1} |
| flow | 1/1 | 0.000 | 0.201 | 0.188 | 90.7 | {'applied': 1} |

JVM采样覆盖：`{"samples": 2, "coveredSeconds": 1.001, "gcCount": 1, "gcMillis": 7, "maxObservedHeapBytes": 315443992}`。

### 实际控制器逐段路线

| 开始秒 | 持续秒 | 控制器 | 起点 → 终点 |
|---:|---:|---|---|
| 0.049 | 0.106 | 无控制器 | — |
| 0.155 | 1.593 | MovementParkour | [12, 97, -98] → [12, 97, -103] |

### 失败及恢复事件

- 0.188s：`flow_blocked index=0 goal=(12.5, 97.0, -102.5) beam=1`

## live-continuous：通关 21.750s

原始结果：`analysis-input.json`。

静止 10.390s；控制器时间：`{"noMovement": 0.312, "MovementParkour": 21.388}`。

事件计数：`{"astar_pop": 7, "surface_candidates": 2, "surface_edge": 40, "astar_move": 219, "search_timing": 12, "planned_route": 1, "search_submitted": 11, "flow_contact": 1, "flow_blocked": 11, "trajectory_plan": 8, "search_result": 11, "trajectory_prefetch": 6, "traj_apply": 169, "trajectory_direct": 3, "trajectory_sprint_runup": 1, "trajectory_pre_hop_failed": 11, "trajectory_search_start": 11, "trajectory_search_progress": 25, "landing_sneak": 65, "trajectory_replan": 1}`。

### 最长静止区间

| 开始秒 | 持续秒 | 位置 |
|---:|---:|---|
| 9.702 | 4.450 | [12.737165, 131.0, -70.521685] |
| 3.403 | 3.499 | [12.734978, 131.0, -76.524116] |
| 16.810 | 1.640 | [10.204211, 131.0, -67.474248] |
| 0.000 | 0.551 | [11.494937, 131.0, -81.313925] |
| 20.602 | 0.250 | [12.737259, 131.0, -64.504604] |

### 搜索计量

墙钟/CPU为任务累计值，可与运动及其他线程重叠；不是通关时间的分拆。未观测结果不等于失败。

| 类别 | 任务/计时 | 排队秒 | 计算墙钟秒 | 线程CPU秒 | 分配MiB | 结果 |
|---|---:|---:|---:|---:|---:|---|
| graph | 1/1 | 0.001 | 0.160 | 0.172 | 97.1 | {'unobserved': 1} |
| flow | 5/5 | 0.009 | 8.787 | 8.625 | 5060.8 | {'applied': 5} |
| prefetch | 6/6 | 0.001 | 6.342 | 6.078 | 3687.7 | {'applied': 3, 'drift': 3} |

JVM采样覆盖：`{"samples": 22, "coveredSeconds": 20.993, "gcCount": 46, "gcMillis": 211, "maxObservedHeapBytes": 469008520}`。

### 实际控制器逐段路线

| 开始秒 | 持续秒 | 控制器 | 起点 → 终点 |
|---:|---:|---|---|
| 0.000 | 0.312 | 无控制器 | — |
| 0.312 | 0.502 | MovementParkour | [11, 131, -82] → [11, 131, -81] |
| 0.814 | 2.788 | MovementParkour | [11, 131, -81] → [13, 131, -77] |
| 3.602 | 4.503 | MovementParkour | [13, 131, -77] → [9, 131, -74] |
| 8.105 | 1.795 | MovementParkour | [9, 131, -74] → [13, 131, -71] |
| 9.900 | 6.910 | MovementParkour | [13, 131, -71] → [9, 131, -68] |
| 16.810 | 3.940 | MovementParkour | [9, 131, -68] → [13, 131, -65] |
| 20.750 | 0.950 | MovementParkour | [13, 131, -65] → [11, 131, -61] |

### 失败及恢复事件

- 0.552s：`flow_blocked index=1 goal=(12.735, 131.0, -76.5) beam=8`
- 0.643s：`flow_blocked index=0 goal=(12.735, 131.0, -76.5) beam=1`
- 0.823s：`flow_blocked index=0 goal=(10.265, 131.0, -73.5) beam=1`
- 3.714s：`flow_blocked index=0 goal=(10.265, 131.0, -73.5) beam=1`
- 6.964s：`flow_blocked index=0 goal=(12.735, 131.0, -70.5) beam=1`
- 8.309s：`flow_blocked index=0 goal=(10.265, 131.0, -67.5) beam=1`
- 10.508s：`flow_blocked index=0 goal=(10.265, 131.0, -67.5) beam=1`
- 14.214s：`flow_blocked index=0 goal=(12.735, 131.0, -64.5) beam=1`
- 16.823s：`flow_blocked index=0 goal=(11.5, 131.0, -60.5) beam=1`
- 16.853s：`trajectory_replan error=0.036896404960957696 motionError=0.020111319585652307`
- 16.872s：`flow_blocked index=0 goal=(12.735, 131.0, -64.5) beam=1`
- 20.859s：`flow_blocked index=0 goal=(11.5, 131.0, -60.5) beam=1`
