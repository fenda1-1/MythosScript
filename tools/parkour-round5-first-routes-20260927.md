# 实机路线明细

时间为序列开始后的墙钟秒数。运动控制器持续时间包含规划与等待，并非纯移动时间；静止与无控制器时间相互重叠，不能相加当作CPU时间。

## swamp-55：通关 85.547s

原始结果：`swamp-55-1.json`。

静止 48.424s；控制器时间：`{"noMovement": 24.427, "MovementParkour": 56.45, "MovementDiagonal": 1.557, "MovementAscend": 0.858, "MovementPillar": 1.108, "MovementDescend": 1.104}`。

事件计数：`{"astar_pop": 49, "surface_candidates": 14, "surface_edge": 178, "astar_move": 1387, "search_timing": 39, "planned_route": 6, "search_submitted": 33, "flow_contact": 42, "trajectory_plan": 32, "search_result": 33, "traj_apply": 602, "trajectory_chain_handoff": 25, "path_executor": 16, "trajectory_search_start": 21, "trajectory_ladder": 6, "trajectory_ledge": 1, "trajectory_prefetch": 11, "flow_blocked": 15, "landing_sneak": 12, "trajectory_corner_best": 8, "trajectory_search_progress": 23, "trajectory_pre_hop_failed": 4, "trajectory_rejected_edge": 5, "fail": 5, "trajectory_corner": 3, "trajectory_runup_try": 6, "surface_accept": 2, "trajectory_direct": 2, "trajectory_pre_hop": 1}`。

### 最长静止区间

| 开始秒 | 持续秒 | 位置 |
|---:|---:|---|
| 58.254 | 10.399 | [3.992609, 33.0, -3060.038071] |
| 44.307 | 8.248 | [10.420802, 25.0, -3054.51479] |
| 25.504 | 7.851 | [16.501951, 24.0, -3056.396625] |
| 37.405 | 5.899 | [11.57079, 26.0, -3055.436082] |
| 0.820 | 5.421 | [0.5, 11.0, -3054.5] |
| 19.405 | 4.903 | [13.489729, 22.75, -3058.662505] |
| 75.454 | 1.799 | [12.204077, 37.0, -3056.597632] |
| 34.454 | 1.101 | [11.911201, 24.0, -3057.965518] |

### 搜索计量

墙钟/CPU为任务累计值，可与运动及其他线程重叠；不是通关时间的分拆。未观测结果不等于失败。

| 类别 | 任务/计时 | 排队秒 | 计算墙钟秒 | 线程CPU秒 | 分配MiB | 结果 |
|---|---:|---:|---:|---:|---:|---|
| graph | 6/6 | 0.008 | 22.174 | 20.969 | 17326.4 | {'unobserved': 6} |
| flow | 15/15 | 0.003 | 13.176 | 12.484 | 9320.9 | {'applied': 14, 'empty': 1} |
| single | 7/7 | 0.001 | 3.210 | 3.016 | 1767.7 | {'applied': 5, 'drift': 1, 'empty': 1} |
| prefetch | 11/11 | 0.001 | 12.967 | 12.438 | 8868.7 | {'empty': 3, 'applied': 7, 'drift': 1} |

JVM采样覆盖：`{"samples": 86, "coveredSeconds": 85.002, "gcCount": 205, "gcMillis": 969, "maxObservedHeapBytes": 569022776}`。

### 实际控制器逐段路线

| 开始秒 | 持续秒 | 控制器 | 起点 → 终点 |
|---:|---:|---|---|
| 0.000 | 5.967 | 无控制器 | — |
| 5.967 | 0.497 | MovementParkour | [0, 11, -3055] → [1, 11, -3055] |
| 6.464 | 0.751 | MovementParkour | [1, 11, -3055] → [5, 11, -3054] |
| 7.215 | 0.192 | MovementDiagonal | [5, 11, -3054] → [6, 11, -3053] |
| 7.407 | 0.601 | MovementParkour | [6, 11, -3053] → [7, 12, -3053] |
| 8.008 | 1.045 | MovementParkour | [7, 12, -3053] → [8, 13, -3053] |
| 9.053 | 0.400 | MovementAscend | [8, 13, -3053] → [8, 14, -3054] |
| 9.453 | 1.902 | MovementParkour | [8, 14, -3054] → [9, 15, -3054] |
| 11.355 | 0.558 | MovementPillar | [9, 15, -3054] → [9, 16, -3054] |
| 11.913 | 0.647 | MovementParkour | [9, 16, -3054] → [10, 16, -3055] |
| 12.560 | 1.345 | MovementParkour | [10, 16, -3055] → [10, 19, -3055] |
| 13.905 | 0.550 | MovementPillar | [10, 19, -3055] → [10, 20, -3055] |
| 14.455 | 0.458 | MovementAscend | [10, 20, -3055] → [10, 21, -3056] |
| 14.913 | 1.103 | MovementParkour | [10, 21, -3056] → [10, 22, -3057] |
| 16.016 | 1.443 | MovementParkour | [10, 22, -3057] → [9, 23, -3060] |
| 17.459 | 0.447 | MovementParkour | [9, 23, -3060] → [10, 24, -3060] |
| 17.906 | 0.252 | MovementDiagonal | [10, 24, -3060] → [11, 24, -3059] |
| 18.158 | 0.400 | MovementParkour | [11, 24, -3059] → [12, 24, -3059] |
| 18.558 | 0.947 | MovementParkour | [12, 24, -3059] → [13, 22, -3059] |
| 19.505 | 1.151 | MovementParkour | [13, 22, -3059] → [14, 24, -3055] |
| 20.656 | 3.402 | 无控制器 | — |
| 24.058 | 1.248 | MovementParkour | [13, 22, -3059] → [16, 24, -3058] |
| 25.306 | 0.253 | MovementParkour | [16, 24, -3058] → [16, 24, -3057] |
| 25.559 | 3.094 | MovementParkour | [16, 24, -3057] → [18, 25, -3056] |
| 28.653 | 4.203 | 无控制器 | — |
| 32.856 | 0.708 | MovementParkour | [16, 24, -3057] → [16, 24, -3058] |
| 33.564 | 0.743 | MovementParkour | [16, 24, -3058] → [12, 24, -3059] |
| 34.307 | 0.200 | MovementDiagonal | [12, 24, -3059] → [11, 24, -3058] |
| 34.507 | 1.350 | MovementParkour | [11, 24, -3058] → [11, 24, -3057] |
| 35.857 | 0.453 | MovementParkour | [11, 24, -3057] → [10, 25, -3057] |
| 36.310 | 0.348 | MovementParkour | [10, 25, -3057] → [10, 25, -3055] |
| 36.658 | 0.250 | MovementParkour | [10, 25, -3055] → [11, 25, -3055] |
| 36.908 | 0.547 | MovementParkour | [11, 25, -3055] → [11, 26, -3056] |
| 37.455 | 2.198 | MovementParkour | [11, 26, -3056] → [15, 26, -3056] |
| 39.653 | 3.702 | 无控制器 | — |
| 43.355 | 0.501 | MovementDescend | [11, 26, -3056] → [11, 25, -3055] |
| 43.856 | 0.498 | MovementParkour | [11, 25, -3055] → [10, 25, -3055] |
| 44.354 | 4.000 | MovementParkour | [10, 25, -3055] → [14, 24, -3055] |
| 48.354 | 3.853 | 无控制器 | — |
| 52.207 | 1.849 | MovementParkour | [10, 25, -3055] → [8, 26, -3055] |
| 54.056 | 0.254 | MovementDiagonal | [8, 26, -3055] → [7, 26, -3056] |
| 54.310 | 0.547 | MovementParkour | [7, 26, -3056] → [7, 27, -3057] |
| 54.857 | 0.547 | MovementParkour | [7, 27, -3057] → [6, 28, -3058] |
| 55.404 | 0.802 | MovementParkour | [6, 28, -3058] → [3, 29, -3058] |
| 56.206 | 0.052 | MovementParkour | [3, 29, -3058] → [2, 29, -3058] |
| 56.258 | 0.450 | MovementParkour | [2, 29, -3058] → [2, 30, -3059] |
| 56.708 | 0.350 | MovementParkour | [2, 30, -3059] → [3, 31, -3059] |
| 57.058 | 0.449 | MovementParkour | [3, 31, -3059] → [5, 32, -3059] |
| 57.507 | 0.149 | MovementParkour | [5, 32, -3059] → [5, 32, -3060] |
| 57.656 | 0.447 | MovementParkour | [5, 32, -3060] → [4, 33, -3060] |
| 58.103 | 0.206 | MovementDiagonal | [4, 33, -3060] → [3, 33, -3061] |
| 58.309 | 7.146 | MovementParkour | [3, 33, -3061] → [3, 34, -3064] |
| 65.455 | 3.250 | 无控制器 | — |
| 68.705 | 0.050 | MovementDiagonal | [3, 33, -3061] → [4, 33, -3060] |
| 68.755 | 0.603 | MovementDescend | [4, 33, -3060] → [5, 32, -3060] |
| 69.358 | 0.550 | MovementParkour | [5, 32, -3060] → [7, 32, -3060] |
| 69.908 | 1.546 | MovementParkour | [7, 32, -3060] → [7, 33, -3063] |
| 71.454 | 0.553 | MovementParkour | [7, 33, -3063] → [7, 34, -3062] |
| 72.007 | 0.748 | MovementParkour | [7, 34, -3062] → [11, 33, -3062] |
| 72.755 | 0.651 | MovementParkour | [11, 33, -3062] → [11, 34, -3063] |
| 73.406 | 0.251 | MovementParkour | [11, 34, -3063] → [12, 34, -3061] |
| 73.657 | 0.500 | MovementParkour | [12, 34, -3061] → [12, 35, -3060] |
| 74.157 | 0.456 | MovementParkour | [12, 35, -3060] → [12, 36, -3059] |
| 74.613 | 0.491 | MovementParkour | [12, 36, -3059] → [12, 37, -3058] |
| 75.104 | 0.403 | MovementDiagonal | [12, 37, -3058] → [11, 37, -3057] |
| 75.507 | 2.250 | MovementParkour | [11, 37, -3057] → [11, 38, -3056] |
| 77.757 | 0.352 | MovementParkour | [11, 38, -3056] → [12, 39, -3056] |
| 78.109 | 0.148 | MovementParkour | [12, 39, -3056] → [13, 39, -3056] |
| 78.257 | 0.200 | MovementParkour | [13, 39, -3056] → [14, 39, -3056] |
| 78.457 | 0.605 | MovementParkour | [14, 39, -3056] → [11, 40, -3055] |
| 79.062 | 0.445 | MovementParkour | [11, 40, -3055] → [11, 41, -3054] |
| 79.507 | 0.499 | MovementParkour | [11, 41, -3054] → [10, 42, -3054] |
| 80.006 | 1.348 | MovementParkour | [10, 42, -3054] → [9, 43, -3054] |
| 81.354 | 2.001 | MovementParkour | [9, 43, -3054] → [8, 44, -3055] |
| 83.355 | 1.349 | MovementParkour | [8, 44, -3055] → [8, 45, -3056] |
| 84.704 | 0.750 | MovementParkour | [8, 45, -3056] → [5, 46, -3055] |
| 85.454 | 0.050 | 无控制器 | — |

### 失败及恢复事件

- 18.641s：`flow_blocked index=0 goal=(14.5, 24.0, -3054.5) beam=1`
- 20.655s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=13,y=22,z=-3059} pos=(13.490,22.750,-3058.663) motion=(0.000,-0.078,0.000)`
- 24.122s：`flow_blocked index=0 goal=(16.5, 24.0, -3057.5) beam=1`
- 28.653s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=16,y=24,z=-3057} pos=(16.502,24.000,-3056.397) motion=(0.000,-0.078,0.000)`
- 35.542s：`flow_blocked index=5 goal=(15.5, 26.0, -3055.5) beam=8`
- 35.631s：`flow_blocked index=0 goal=(15.5, 26.0, -3055.5) beam=1`
- 39.652s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=11,y=26,z=-3056} pos=(11.571,26.000,-3055.436) motion=(0.000,-0.078,0.000)`
- 44.133s：`flow_blocked index=1 goal=(14.5, 24.0, -3054.5) beam=8`
- 44.198s：`flow_blocked index=0 goal=(14.5, 24.0, -3054.5) beam=1`
- 48.352s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=10,y=25,z=-3055} pos=(10.421,25.000,-3054.515) motion=(0.000,-0.078,0.000)`
- 52.282s：`flow_blocked index=0 goal=(8.5, 26.0, -3054.5) beam=1`
- 54.583s：`flow_blocked index=2 goal=(3.5, 29.0, -3057.5) beam=8`
- 58.334s：`flow_blocked index=0 goal=(3.5, 34.0, -3063.5) beam=1`
- 65.453s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=3,y=33,z=-3061} pos=(3.993,33.000,-3060.038) motion=(0.000,-0.078,0.000)`
- 69.533s：`flow_blocked index=1 goal=(7.5, 33.0, -3062.5) beam=8`
- 69.581s：`flow_blocked index=0 goal=(7.5, 33.0, -3062.5) beam=1`
- 70.202s：`flow_blocked index=1 goal=(11.5, 33.0, -3061.5) beam=8`
- 71.533s：`flow_blocked index=0 goal=(11.5, 33.0, -3061.5) beam=1`
- 77.237s：`flow_blocked index=7 goal=(9.5, 43.5, -3053.675) beam=8`
- 77.374s：`flow_blocked index=0 goal=(9.5, 43.5, -3053.675) beam=1`
