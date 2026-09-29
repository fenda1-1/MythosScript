# 实机路线明细

时间为序列开始后的墙钟秒数。运动控制器持续时间包含规划与等待，并非纯移动时间；静止与无控制器时间相互重叠，不能相加当作CPU时间。

## swamp-55：通关 96.750s

原始结果：`swamp-55-2.json`。

静止 57.831s；控制器时间：`{"noMovement": 32.173, "MovementParkour": 60.177, "MovementDiagonal": 1.574, "MovementAscend": 0.591, "MovementPillar": 1.102, "MovementDescend": 1.083}`。

事件计数：`{"astar_pop": 70, "surface_candidates": 20, "surface_edge": 243, "astar_move": 2044, "planned_route": 9, "flow_contact": 51, "trajectory_plan": 37, "traj_apply": 630, "trajectory_chain_handoff": 25, "path_executor": 16, "trajectory_search_start": 22, "trajectory_ladder": 6, "fail": 8, "surface_accept": 5, "trajectory_ledge": 1, "trajectory_prefetch": 13, "flow_blocked": 19, "trajectory_corner_best": 8, "landing_sneak": 12, "trajectory_search_progress": 25, "trajectory_pre_hop_failed": 4, "trajectory_rejected_edge": 5, "trajectory_corner": 3, "trajectory_runup_try": 6, "trajectory_direct": 3, "trajectory_pre_hop": 1}`。

### 最长静止区间

| 开始秒 | 持续秒 | 位置 |
|---:|---:|---|
| 62.303 | 10.299 | [3.992609, 33.0, -3060.03809] |
| 48.595 | 7.952 | [10.423202, 25.0, -3054.51477] |
| 26.743 | 7.104 | [16.498889, 24.0, -3056.387913] |
| 38.254 | 5.848 | [11.57079, 26.0, -3055.436082] |
| 0.452 | 5.047 | [0.5, 11.0, -3054.5] |
| 73.398 | 4.550 | [5.7, 32.0, -3059.52433] |
| 21.143 | 4.300 | [13.489729, 22.75, -3058.662505] |
| 44.644 | 3.751 | [11.546188, 25.0, -3054.3] |

### 实际控制器逐段路线

| 开始秒 | 持续秒 | 控制器 | 起点 → 终点 |
|---:|---:|---|---|
| 0.002 | 5.350 | 无控制器 | — |
| 5.352 | 0.348 | MovementParkour | [0, 11, -3055] → [1, 11, -3055] |
| 5.700 | 0.750 | MovementParkour | [1, 11, -3055] → [5, 11, -3054] |
| 6.450 | 0.199 | MovementDiagonal | [5, 11, -3054] → [6, 11, -3053] |
| 6.649 | 0.501 | MovementParkour | [6, 11, -3053] → [7, 12, -3053] |
| 7.150 | 0.949 | MovementParkour | [7, 12, -3053] → [8, 13, -3053] |
| 8.099 | 0.403 | MovementAscend | [8, 13, -3053] → [8, 14, -3054] |
| 8.502 | 1.897 | MovementParkour | [8, 14, -3054] → [9, 15, -3054] |
| 10.399 | 0.551 | MovementPillar | [9, 15, -3054] → [9, 16, -3054] |
| 10.950 | 0.650 | MovementParkour | [9, 16, -3054] → [10, 16, -3055] |
| 11.600 | 1.350 | MovementParkour | [10, 16, -3055] → [10, 19, -3055] |
| 12.950 | 0.551 | MovementPillar | [10, 19, -3055] → [10, 20, -3055] |
| 13.501 | 0.188 | MovementAscend | [10, 20, -3055] → [10, 21, -3056] |
| 13.689 | 3.105 | 无控制器 | — |
| 16.794 | 1.006 | MovementParkour | [10, 21, -3056] → [10, 22, -3057] |
| 17.800 | 1.395 | MovementParkour | [10, 22, -3057] → [9, 23, -3060] |
| 19.195 | 0.448 | MovementParkour | [9, 23, -3060] → [10, 24, -3060] |
| 19.643 | 0.252 | MovementDiagonal | [10, 24, -3060] → [11, 24, -3059] |
| 19.895 | 0.398 | MovementParkour | [11, 24, -3059] → [12, 24, -3059] |
| 20.293 | 0.952 | MovementParkour | [12, 24, -3059] → [13, 22, -3059] |
| 21.245 | 1.151 | MovementParkour | [13, 22, -3059] → [14, 24, -3055] |
| 22.396 | 2.848 | 无控制器 | — |
| 25.244 | 1.199 | MovementParkour | [13, 22, -3059] → [16, 24, -3058] |
| 26.443 | 0.350 | MovementParkour | [16, 24, -3058] → [16, 24, -3057] |
| 26.793 | 3.100 | MovementParkour | [16, 24, -3057] → [18, 25, -3056] |
| 29.893 | 3.555 | 无控制器 | — |
| 33.448 | 0.602 | MovementParkour | [16, 24, -3057] → [16, 24, -3058] |
| 34.050 | 0.746 | MovementParkour | [16, 24, -3058] → [12, 24, -3059] |
| 34.796 | 0.206 | MovementDiagonal | [12, 24, -3059] → [11, 24, -3058] |
| 35.002 | 1.703 | MovementParkour | [11, 24, -3058] → [11, 24, -3057] |
| 36.705 | 0.450 | MovementParkour | [11, 24, -3057] → [10, 25, -3057] |
| 37.155 | 0.353 | MovementParkour | [10, 25, -3057] → [10, 25, -3055] |
| 37.508 | 0.250 | MovementParkour | [10, 25, -3055] → [11, 25, -3055] |
| 37.758 | 0.545 | MovementParkour | [11, 25, -3055] → [11, 26, -3056] |
| 38.303 | 2.200 | MovementParkour | [11, 26, -3056] → [15, 26, -3056] |
| 40.503 | 3.649 | 无控制器 | — |
| 44.152 | 0.492 | MovementDescend | [11, 26, -3056] → [11, 25, -3055] |
| 44.644 | 3.505 | 无控制器 | — |
| 48.149 | 0.497 | MovementParkour | [11, 25, -3055] → [10, 25, -3055] |
| 48.646 | 4.001 | MovementParkour | [10, 25, -3055] → [14, 24, -3055] |
| 52.647 | 3.601 | 无控制器 | — |
| 56.248 | 1.806 | MovementParkour | [10, 25, -3055] → [8, 26, -3055] |
| 58.054 | 0.251 | MovementDiagonal | [8, 26, -3055] → [7, 26, -3056] |
| 58.305 | 0.551 | MovementParkour | [7, 26, -3056] → [7, 27, -3057] |
| 58.856 | 0.548 | MovementParkour | [7, 27, -3057] → [6, 28, -3058] |
| 59.404 | 0.852 | MovementParkour | [6, 28, -3058] → [3, 29, -3058] |
| 60.256 | 0.049 | MovementParkour | [3, 29, -3058] → [2, 29, -3058] |
| 60.305 | 0.451 | MovementParkour | [2, 29, -3058] → [2, 30, -3059] |
| 60.756 | 0.349 | MovementParkour | [2, 30, -3059] → [3, 31, -3059] |
| 61.105 | 0.449 | MovementParkour | [3, 31, -3059] → [5, 32, -3059] |
| 61.554 | 0.151 | MovementParkour | [5, 32, -3059] → [5, 32, -3060] |
| 61.705 | 0.448 | MovementParkour | [5, 32, -3060] → [4, 33, -3060] |
| 62.153 | 0.202 | MovementDiagonal | [4, 33, -3060] → [3, 33, -3061] |
| 62.355 | 7.148 | MovementParkour | [3, 33, -3061] → [3, 34, -3064] |
| 69.503 | 3.149 | 无控制器 | — |
| 72.652 | 0.051 | MovementDiagonal | [3, 33, -3061] → [4, 33, -3060] |
| 72.703 | 0.591 | MovementDescend | [4, 33, -3060] → [5, 32, -3060] |
| 73.294 | 3.360 | 无控制器 | — |
| 76.654 | 1.595 | MovementParkour | [5, 32, -3060] → [5, 32, -3059] |
| 78.249 | 1.400 | MovementParkour | [5, 32, -3059] → [7, 32, -3059] |
| 79.649 | 0.602 | MovementParkour | [7, 32, -3059] → [7, 32, -3060] |
| 80.251 | 1.347 | MovementParkour | [7, 32, -3060] → [7, 33, -3063] |
| 81.598 | 0.904 | MovementParkour | [7, 33, -3063] → [7, 34, -3062] |
| 82.502 | 0.846 | MovementParkour | [7, 34, -3062] → [11, 33, -3062] |
| 83.348 | 1.453 | MovementParkour | [11, 33, -3062] → [11, 34, -3063] |
| 84.801 | 0.249 | MovementParkour | [11, 34, -3063] → [12, 34, -3061] |
| 85.050 | 0.499 | MovementParkour | [12, 34, -3061] → [12, 35, -3060] |
| 85.549 | 0.453 | MovementParkour | [12, 35, -3060] → [12, 36, -3059] |
| 86.002 | 0.499 | MovementParkour | [12, 36, -3059] → [12, 37, -3058] |
| 86.501 | 0.413 | MovementDiagonal | [12, 37, -3058] → [11, 37, -3057] |
| 86.914 | 2.038 | MovementParkour | [11, 37, -3057] → [11, 38, -3056] |
| 88.952 | 0.351 | MovementParkour | [11, 38, -3056] → [12, 39, -3056] |
| 89.303 | 0.149 | MovementParkour | [12, 39, -3056] → [13, 39, -3056] |
| 89.452 | 0.201 | MovementParkour | [13, 39, -3056] → [14, 39, -3056] |
| 89.653 | 0.605 | MovementParkour | [14, 39, -3056] → [11, 40, -3055] |
| 90.258 | 0.443 | MovementParkour | [11, 40, -3055] → [11, 41, -3054] |
| 90.701 | 0.519 | MovementParkour | [11, 41, -3054] → [10, 42, -3054] |
| 91.220 | 1.331 | MovementParkour | [10, 42, -3054] → [9, 43, -3054] |
| 92.551 | 2.003 | MovementParkour | [9, 43, -3054] → [8, 44, -3055] |
| 94.554 | 1.347 | MovementParkour | [8, 44, -3055] → [8, 45, -3056] |
| 95.901 | 0.750 | MovementParkour | [8, 45, -3056] → [5, 46, -3055] |
| 96.651 | 0.051 | 无控制器 | — |

### 失败及恢复事件

- 13.688s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=10,y=21,z=-3056} pos=(10.480,21.001,-3054.720) motion=(0.000,0.165,-0.018)`
- 20.369s：`flow_blocked index=0 goal=(14.5, 24.0, -3054.5) beam=1`
- 22.395s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=13,y=22,z=-3059} pos=(13.490,22.750,-3058.663) motion=(0.000,-0.078,0.000)`
- 25.303s：`flow_blocked index=0 goal=(16.5, 24.0, -3057.5) beam=1`
- 29.891s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=16,y=24,z=-3057} pos=(16.499,24.000,-3056.388) motion=(0.000,-0.078,0.000)`
- 36.390s：`flow_blocked index=5 goal=(15.5, 26.0, -3055.5) beam=8`
- 36.478s：`flow_blocked index=0 goal=(15.5, 26.0, -3055.5) beam=1`
- 40.501s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=11,y=26,z=-3056} pos=(11.571,26.000,-3055.436) motion=(0.000,-0.078,0.000)`
- 44.643s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=11,y=25,z=-3055} pos=(11.546,25.232,-3054.300) motion=(0.000,-0.377,0.000)`
- 48.413s：`flow_blocked index=1 goal=(14.5, 24.0, -3054.5) beam=8`
- 48.488s：`flow_blocked index=0 goal=(14.5, 24.0, -3054.5) beam=1`
- 52.646s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=10,y=25,z=-3055} pos=(10.423,25.000,-3054.515) motion=(0.000,-0.078,0.000)`
- 56.315s：`flow_blocked index=0 goal=(8.5, 26.0, -3054.5) beam=1`
- 58.561s：`flow_blocked index=2 goal=(3.5, 29.0, -3057.5) beam=8`
- 62.382s：`flow_blocked index=0 goal=(3.5, 34.0, -3063.5) beam=1`
- 69.501s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=3,y=33,z=-3061} pos=(3.993,33.000,-3060.038) motion=(0.000,-0.078,0.000)`
- 73.291s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=5,y=32,z=-3060} pos=(5.700,32.232,-3059.562) motion=(0.000,-0.377,0.014)`
- 77.936s：`flow_blocked index=1 goal=(7.5, 32.0, -3058.5) beam=8`
- 78.241s：`flow_blocked index=0 goal=(7.5, 32.0, -3058.5) beam=1`
- 78.524s：`flow_blocked index=1 goal=(7.5, 33.0, -3062.5) beam=8`
- 79.951s：`flow_blocked index=1 goal=(7.5, 33.0, -3062.5) beam=8`
- 80.020s：`flow_blocked index=0 goal=(7.5, 33.0, -3062.5) beam=1`
- 80.511s：`flow_blocked index=1 goal=(11.5, 33.0, -3061.5) beam=8`
- 81.966s：`flow_blocked index=1 goal=(11.5, 33.0, -3061.5) beam=8`
- 82.031s：`flow_blocked index=0 goal=(11.5, 33.0, -3061.5) beam=1`
- 88.443s：`flow_blocked index=7 goal=(9.5, 43.5, -3053.675) beam=8`
- 88.563s：`flow_blocked index=0 goal=(9.5, 43.5, -3053.675) beam=1`
