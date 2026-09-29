# 实机路线明细

时间为序列开始后的墙钟秒数。运动控制器持续时间包含规划与等待，并非纯移动时间；静止与无控制器时间相互重叠，不能相加当作CPU时间。

## snow-10：通关 69.000s

原始结果：`snow-10-1.json`。

静止 19.240s；控制器时间：`{"noMovement": 5.764, "MovementDiagonal": 1.006, "MovementParkour": 61.489, "MovementDescend": 0.74}`。

事件计数：`{"astar_pop": 17, "surface_candidates": 7, "surface_edge": 57, "astar_move": 657, "planned_route": 2, "path_executor": 6, "flow_contact": 153, "trajectory_plan": 25, "traj_apply": 920, "trajectory_chain_handoff": 53, "trajectory_direct": 17, "trajectory_pre_hop": 3, "trajectory_prefetch": 15, "flow_blocked": 10, "trajectory_search_start": 8, "trajectory_corner_best": 1, "trajectory_head_runup": 2, "trajectory_search_progress": 5, "trajectory_sprint_runup": 9, "damage_landing_confirm": 12, "fail": 1, "trajectory_pre_hop_failed": 1, "trajectory_ledge": 1, "parkour_exhausted_no_route": 2}`。

### 最长静止区间

| 开始秒 | 持续秒 | 位置 |
|---:|---:|---|
| 6.702 | 3.100 | [8.668306, 8.0, -2067.386501] |
| 0.902 | 3.041 | [0.5, 11.0, -2067.5] |
| 62.449 | 2.350 | [77.36203, 19.0, -2072.367455] |
| 52.152 | 1.796 | [43.702962, 15.375, -2067.156418] |
| 18.199 | 1.450 | [25.178065, 8.0, -2068.534666] |
| 28.751 | 1.448 | [31.456267, 14.0, -2064.439778] |
| 21.050 | 1.401 | [27.440681, 9.0, -2071.440635] |
| 49.749 | 1.299 | [44.446382, 14.0, -2061.522376] |

### 实际控制器逐段路线

| 开始秒 | 持续秒 | 控制器 | 起点 → 终点 |
|---:|---:|---|---|
| 0.000 | 4.004 | 无控制器 | — |
| 4.004 | 0.201 | MovementDiagonal | [0, 11, -2068] → [1, 11, -2069] |
| 4.205 | 0.852 | MovementParkour | [1, 11, -2069] → [4, 10, -2070] |
| 5.057 | 0.597 | MovementParkour | [4, 10, -2070] → [5, 10, -2070] |
| 5.654 | 1.048 | MovementParkour | [5, 10, -2070] → [8, 8, -2068] |
| 6.702 | 5.454 | MovementParkour | [8, 8, -2068] → [12, 9, -2070] |
| 12.156 | 1.398 | MovementParkour | [12, 9, -2070] → [16, 10, -2069] |
| 13.554 | 0.602 | MovementParkour | [16, 10, -2069] → [16, 11, -2068] |
| 14.156 | 0.548 | MovementParkour | [16, 11, -2068] → [18, 12, -2066] |
| 14.704 | 1.899 | MovementParkour | [18, 12, -2066] → [22, 9, -2068] |
| 16.603 | 0.602 | MovementDiagonal | [22, 9, -2068] → [23, 9, -2069] |
| 17.205 | 0.740 | MovementDescend | [23, 9, -2069] → [24, 8, -2069] |
| 17.945 | 1.659 | 无控制器 | — |
| 19.604 | 1.446 | MovementParkour | [24, 8, -2069] → [27, 9, -2072] |
| 21.050 | 1.952 | MovementParkour | [27, 9, -2072] → [28, 10, -2071] |
| 23.002 | 0.351 | MovementParkour | [28, 10, -2071] → [29, 11, -2071] |
| 23.353 | 0.549 | MovementParkour | [29, 11, -2071] → [30, 12, -2072] |
| 23.902 | 0.349 | MovementParkour | [30, 12, -2072] → [30, 13, -2073] |
| 24.251 | 0.452 | MovementParkour | [30, 13, -2073] → [29, 14, -2073] |
| 24.703 | 0.450 | MovementParkour | [29, 14, -2073] → [28, 15, -2073] |
| 25.153 | 0.450 | MovementParkour | [28, 15, -2073] → [28, 16, -2072] |
| 25.603 | 0.549 | MovementParkour | [28, 16, -2072] → [29, 17, -2071] |
| 26.152 | 2.599 | MovementParkour | [29, 17, -2071] → [31, 14, -2065] |
| 28.751 | 1.949 | MovementParkour | [31, 14, -2065] → [30, 15, -2064] |
| 30.700 | 0.452 | MovementParkour | [30, 15, -2064] → [28, 16, -2066] |
| 31.152 | 0.451 | MovementParkour | [28, 16, -2066] → [27, 17, -2066] |
| 31.603 | 0.448 | MovementParkour | [27, 17, -2066] → [27, 18, -2067] |
| 32.051 | 0.452 | MovementParkour | [27, 18, -2067] → [28, 19, -2067] |
| 32.503 | 0.698 | MovementParkour | [28, 19, -2067] → [32, 19, -2068] |
| 33.201 | 0.751 | MovementParkour | [32, 19, -2068] → [30, 19, -2072] |
| 33.952 | 0.499 | MovementParkour | [30, 19, -2072] → [33, 20, -2073] |
| 34.451 | 0.198 | MovementParkour | [33, 20, -2073] → [34, 20, -2073] |
| 34.649 | 0.203 | MovementDiagonal | [34, 20, -2073] → [35, 20, -2074] |
| 34.852 | 1.146 | MovementParkour | [35, 20, -2074] → [38, 15, -2075] |
| 35.998 | 0.401 | MovementParkour | [38, 15, -2075] → [39, 15, -2075] |
| 36.399 | 1.799 | MovementParkour | [39, 15, -2075] → [43, 11, -2074] |
| 38.198 | 1.703 | MovementParkour | [43, 11, -2074] → [47, 11, -2075] |
| 39.901 | 0.600 | MovementParkour | [47, 11, -2075] → [51, 11, -2076] |
| 40.501 | 0.605 | MovementParkour | [51, 11, -2076] → [55, 11, -2076] |
| 41.106 | 0.546 | MovementParkour | [55, 11, -2076] → [58, 12, -2074] |
| 41.652 | 0.452 | MovementParkour | [58, 12, -2074] → [60, 13, -2071] |
| 42.104 | 0.448 | MovementParkour | [60, 13, -2071] → [60, 14, -2068] |
| 42.552 | 0.698 | MovementParkour | [60, 14, -2068] → [59, 14, -2064] |
| 43.250 | 0.801 | MovementParkour | [59, 14, -2064] → [56, 14, -2061] |
| 44.051 | 1.597 | MovementParkour | [56, 14, -2061] → [52, 14, -2060] |
| 45.648 | 2.005 | MovementParkour | [52, 14, -2060] → [48, 14, -2061] |
| 47.653 | 2.096 | MovementParkour | [48, 14, -2061] → [44, 14, -2062] |
| 49.749 | 1.504 | MovementParkour | [44, 14, -2062] → [44, 14, -2063] |
| 51.253 | 0.198 | MovementParkour | [44, 14, -2063] → [44, 14, -2064] |
| 51.451 | 0.701 | MovementParkour | [44, 14, -2064] → [43, 15, -2068] |
| 52.152 | 1.902 | MovementParkour | [43, 15, -2068] → [44, 15, -2068] |
| 54.054 | 0.799 | MovementParkour | [44, 15, -2068] → [48, 16, -2068] |
| 54.853 | 0.347 | MovementParkour | [48, 16, -2068] → [49, 17, -2066] |
| 55.200 | 0.351 | MovementParkour | [49, 17, -2066] → [50, 19, -2068] |
| 55.551 | 0.448 | MovementParkour | [50, 19, -2068] → [53, 19, -2068] |
| 55.999 | 0.152 | MovementParkour | [53, 19, -2068] → [54, 19, -2068] |
| 56.151 | 0.049 | MovementParkour | [54, 19, -2068] → [55, 19, -2068] |
| 56.200 | 0.154 | MovementParkour | [55, 19, -2068] → [56, 19, -2068] |
| 56.354 | 1.345 | MovementParkour | [57, 19, -2068] → [58, 19, -2068] |
| 57.699 | 0.155 | MovementParkour | [58, 19, -2068] → [59, 19, -2068] |
| 57.854 | 0.647 | MovementParkour | [59, 19, -2068] → [65, 19, -2068] |
| 58.501 | 0.050 | MovementParkour | [65, 19, -2068] → [66, 19, -2068] |
| 58.551 | 0.150 | MovementParkour | [66, 19, -2068] → [67, 19, -2068] |
| 58.701 | 0.700 | MovementParkour | [67, 19, -2068] → [71, 19, -2069] |
| 59.401 | 0.149 | MovementParkour | [71, 19, -2069] → [72, 19, -2069] |
| 59.550 | 0.351 | MovementParkour | [72, 19, -2069] → [73, 19, -2069] |
| 59.901 | 2.548 | MovementParkour | [73, 19, -2069] → [77, 19, -2073] |
| 62.449 | 2.702 | MovementParkour | [77, 19, -2073] → [78, 19, -2073] |
| 65.151 | 0.300 | MovementParkour | [78, 19, -2073] → [79, 19, -2073] |
| 65.451 | 0.603 | MovementParkour | [79, 19, -2073] → [83, 19, -2073] |
| 66.054 | 0.150 | MovementParkour | [83, 19, -2073] → [84, 19, -2073] |
| 66.204 | 0.601 | MovementParkour | [84, 19, -2073] → [87, 19, -2071] |
| 66.805 | 0.598 | MovementParkour | [87, 19, -2071] → [90, 19, -2069] |
| 67.403 | 0.050 | MovementParkour | [90, 19, -2069] → [91, 19, -2069] |
| 67.453 | 0.599 | MovementParkour | [91, 19, -2069] → [95, 19, -2068] |
| 68.052 | 0.248 | MovementParkour | [95, 19, -2068] → [96, 19, -2068] |
| 68.300 | 0.149 | MovementParkour | [96, 19, -2068] → [97, 19, -2068] |
| 68.449 | 0.051 | MovementParkour | [97, 19, -2068] → [98, 19, -2068] |
| 68.500 | 0.150 | MovementParkour | [98, 19, -2068] → [99, 19, -2068] |
| 68.650 | 0.248 | MovementParkour | [99, 19, -2068] → [100, 19, -2068] |
| 68.898 | 0.101 | 无控制器 | — |

### 失败及恢复事件

- 5.708s：`flow_blocked index=0 goal=(12.5, 9.0, -2069.5) beam=1`
- 7.805s：`flow_blocked index=0 goal=(12.5, 9.0, -2069.5) beam=1`
- 9.857s：`flow_blocked index=0 goal=(16.5, 10.0, -2068.5) beam=1`
- 17.944s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=24,y=8,z=-2069} pos=(24.953,8.232,-2068.535) motion=(0.076,-0.377,0.002)`
- 19.640s：`flow_blocked index=0 goal=(27.5, 9.0, -2071.5) beam=1`
- 39.208s：`flow_blocked index=0 goal=(52.5, 13.875, -2059.5) beam=1`
- 44.105s：`flow_blocked index=0 goal=(48.5, 13.875, -2060.5) beam=1`
- 45.819s：`flow_blocked index=0 goal=(48.5, 13.875, -2060.5) beam=1`
- 45.897s：`flow_blocked index=0 goal=(44.5, 14.0, -2061.5) beam=1`
- 57.389s：`flow_blocked index=8 goal=(77.5, 19.0, -2072.5) beam=8`
- 57.490s：`flow_blocked index=0 goal=(77.5, 19.0, -2072.5) beam=1`

## swamp-55：通关 95.610s

原始结果：`swamp-55-1.json`。

静止 56.566s；控制器时间：`{"noMovement": 31.691, "MovementParkour": 59.587, "MovementDiagonal": 1.556, "MovementAscend": 0.591, "MovementPillar": 1.099, "MovementDescend": 1.083}`。

事件计数：`{"astar_pop": 70, "surface_candidates": 20, "surface_edge": 243, "astar_move": 2044, "planned_route": 9, "flow_contact": 51, "trajectory_plan": 37, "traj_apply": 630, "trajectory_chain_handoff": 25, "path_executor": 16, "trajectory_search_start": 22, "trajectory_ladder": 6, "fail": 8, "surface_accept": 5, "trajectory_ledge": 1, "trajectory_prefetch": 13, "flow_blocked": 19, "trajectory_corner_best": 7, "landing_sneak": 12, "trajectory_search_progress": 24, "trajectory_pre_hop_failed": 4, "trajectory_rejected_edge": 5, "trajectory_corner": 3, "trajectory_runup_try": 6, "trajectory_direct": 3, "trajectory_pre_hop": 1}`。

### 最长静止区间

| 开始秒 | 持续秒 | 位置 |
|---:|---:|---|
| 61.460 | 10.047 | [3.992609, 33.0, -3060.03809] |
| 47.960 | 7.898 | [10.423202, 25.0, -3054.51477] |
| 26.404 | 6.199 | [16.498889, 24.0, -3056.387913] |
| 37.714 | 5.798 | [11.57079, 26.0, -3055.436082] |
| 0.709 | 4.803 | [0.5, 11.0, -3054.5] |
| 72.303 | 4.451 | [5.7, 32.0, -3059.52433] |
| 20.753 | 4.351 | [13.489729, 22.75, -3058.662505] |
| 44.054 | 3.703 | [11.546188, 25.0, -3054.3] |

### 实际控制器逐段路线

| 开始秒 | 持续秒 | 控制器 | 起点 → 终点 |
|---:|---:|---|---|
| 0.000 | 5.361 | 无控制器 | — |
| 5.361 | 0.355 | MovementParkour | [0, 11, -3055] → [1, 11, -3055] |
| 5.716 | 0.747 | MovementParkour | [1, 11, -3055] → [5, 11, -3054] |
| 6.463 | 0.198 | MovementDiagonal | [5, 11, -3054] → [6, 11, -3053] |
| 6.661 | 0.500 | MovementParkour | [6, 11, -3053] → [7, 12, -3053] |
| 7.161 | 0.951 | MovementParkour | [7, 12, -3053] → [8, 13, -3053] |
| 8.112 | 0.400 | MovementAscend | [8, 13, -3053] → [8, 14, -3054] |
| 8.512 | 1.899 | MovementParkour | [8, 14, -3054] → [9, 15, -3054] |
| 10.411 | 0.551 | MovementPillar | [9, 15, -3054] → [9, 16, -3054] |
| 10.962 | 0.649 | MovementParkour | [9, 16, -3054] → [10, 16, -3055] |
| 11.611 | 1.351 | MovementParkour | [10, 16, -3055] → [10, 19, -3055] |
| 12.962 | 0.548 | MovementPillar | [10, 19, -3055] → [10, 20, -3055] |
| 13.510 | 0.191 | MovementAscend | [10, 20, -3055] → [10, 21, -3056] |
| 13.701 | 2.804 | 无控制器 | — |
| 16.505 | 0.900 | MovementParkour | [10, 21, -3056] → [10, 22, -3057] |
| 17.405 | 1.402 | MovementParkour | [10, 22, -3057] → [9, 23, -3060] |
| 18.807 | 0.446 | MovementParkour | [9, 23, -3060] → [10, 24, -3060] |
| 19.253 | 0.251 | MovementDiagonal | [10, 24, -3060] → [11, 24, -3059] |
| 19.504 | 0.401 | MovementParkour | [11, 24, -3059] → [12, 24, -3059] |
| 19.905 | 0.948 | MovementParkour | [12, 24, -3059] → [13, 22, -3059] |
| 20.853 | 1.154 | MovementParkour | [13, 22, -3059] → [14, 24, -3055] |
| 22.007 | 2.898 | 无控制器 | — |
| 24.905 | 1.198 | MovementParkour | [13, 22, -3059] → [16, 24, -3058] |
| 26.103 | 0.352 | MovementParkour | [16, 24, -3058] → [16, 24, -3057] |
| 26.455 | 3.099 | MovementParkour | [16, 24, -3057] → [18, 25, -3056] |
| 29.554 | 3.711 | 无控制器 | — |
| 33.265 | 0.599 | MovementParkour | [16, 24, -3057] → [16, 24, -3058] |
| 33.864 | 0.751 | MovementParkour | [16, 24, -3058] → [12, 24, -3059] |
| 34.615 | 0.200 | MovementDiagonal | [12, 24, -3059] → [11, 24, -3058] |
| 34.815 | 1.349 | MovementParkour | [11, 24, -3058] → [11, 24, -3057] |
| 36.164 | 0.451 | MovementParkour | [11, 24, -3057] → [10, 25, -3057] |
| 36.615 | 0.352 | MovementParkour | [10, 25, -3057] → [10, 25, -3055] |
| 36.967 | 0.250 | MovementParkour | [10, 25, -3055] → [11, 25, -3055] |
| 37.217 | 0.546 | MovementParkour | [11, 25, -3055] → [11, 26, -3056] |
| 37.763 | 2.200 | MovementParkour | [11, 26, -3056] → [15, 26, -3056] |
| 39.963 | 3.599 | 无控制器 | — |
| 43.562 | 0.492 | MovementDescend | [11, 26, -3056] → [11, 25, -3055] |
| 44.054 | 3.457 | 无控制器 | — |
| 47.511 | 0.498 | MovementParkour | [11, 25, -3055] → [10, 25, -3055] |
| 48.009 | 3.999 | MovementParkour | [10, 25, -3055] → [14, 24, -3055] |
| 52.008 | 3.503 | 无控制器 | — |
| 55.511 | 1.847 | MovementParkour | [10, 25, -3055] → [8, 26, -3055] |
| 57.358 | 0.252 | MovementDiagonal | [8, 26, -3055] → [7, 26, -3056] |
| 57.610 | 0.500 | MovementParkour | [7, 26, -3056] → [7, 27, -3057] |
| 58.110 | 0.548 | MovementParkour | [7, 27, -3057] → [6, 28, -3058] |
| 58.658 | 0.752 | MovementParkour | [6, 28, -3058] → [3, 29, -3058] |
| 59.410 | 0.053 | MovementParkour | [3, 29, -3058] → [2, 29, -3058] |
| 59.463 | 0.449 | MovementParkour | [2, 29, -3058] → [2, 30, -3059] |
| 59.912 | 0.347 | MovementParkour | [2, 30, -3059] → [3, 31, -3059] |
| 60.259 | 0.452 | MovementParkour | [3, 31, -3059] → [5, 32, -3059] |
| 60.711 | 0.149 | MovementParkour | [5, 32, -3059] → [5, 32, -3060] |
| 60.860 | 0.448 | MovementParkour | [5, 32, -3060] → [4, 33, -3060] |
| 61.308 | 0.201 | MovementDiagonal | [4, 33, -3060] → [3, 33, -3061] |
| 61.509 | 7.149 | MovementParkour | [3, 33, -3061] → [3, 34, -3064] |
| 68.658 | 2.899 | 无控制器 | — |
| 71.557 | 0.051 | MovementDiagonal | [3, 33, -3061] → [4, 33, -3060] |
| 71.608 | 0.591 | MovementDescend | [4, 33, -3060] → [5, 32, -3060] |
| 72.199 | 3.356 | 无控制器 | — |
| 75.555 | 1.499 | MovementParkour | [5, 32, -3060] → [5, 32, -3059] |
| 77.054 | 1.401 | MovementParkour | [5, 32, -3059] → [7, 32, -3059] |
| 78.455 | 0.601 | MovementParkour | [7, 32, -3059] → [7, 32, -3060] |
| 79.056 | 1.347 | MovementParkour | [7, 32, -3060] → [7, 33, -3063] |
| 80.403 | 0.902 | MovementParkour | [7, 33, -3063] → [7, 34, -3062] |
| 81.305 | 0.848 | MovementParkour | [7, 34, -3062] → [11, 33, -3062] |
| 82.153 | 1.454 | MovementParkour | [11, 33, -3062] → [11, 34, -3063] |
| 83.607 | 0.248 | MovementParkour | [11, 34, -3063] → [12, 34, -3061] |
| 83.855 | 0.500 | MovementParkour | [12, 34, -3061] → [12, 35, -3060] |
| 84.355 | 0.451 | MovementParkour | [12, 35, -3060] → [12, 36, -3059] |
| 84.806 | 0.497 | MovementParkour | [12, 36, -3059] → [12, 37, -3058] |
| 85.303 | 0.403 | MovementDiagonal | [12, 37, -3058] → [11, 37, -3057] |
| 85.706 | 2.101 | MovementParkour | [11, 37, -3057] → [11, 38, -3056] |
| 87.807 | 0.349 | MovementParkour | [11, 38, -3056] → [12, 39, -3056] |
| 88.156 | 0.151 | MovementParkour | [12, 39, -3056] → [13, 39, -3056] |
| 88.307 | 0.200 | MovementParkour | [13, 39, -3056] → [14, 39, -3056] |
| 88.507 | 0.599 | MovementParkour | [14, 39, -3056] → [11, 40, -3055] |
| 89.106 | 0.450 | MovementParkour | [11, 40, -3055] → [11, 41, -3054] |
| 89.556 | 0.498 | MovementParkour | [11, 41, -3054] → [10, 42, -3054] |
| 90.054 | 1.349 | MovementParkour | [10, 42, -3054] → [9, 43, -3054] |
| 91.403 | 2.001 | MovementParkour | [9, 43, -3054] → [8, 44, -3055] |
| 93.404 | 1.350 | MovementParkour | [8, 44, -3055] → [8, 45, -3056] |
| 94.754 | 0.750 | MovementParkour | [8, 45, -3056] → [5, 46, -3055] |
| 95.504 | 0.103 | 无控制器 | — |

### 失败及恢复事件

- 13.699s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=10,y=21,z=-3056} pos=(10.480,21.001,-3054.720) motion=(0.000,0.165,-0.018)`
- 19.975s：`flow_blocked index=0 goal=(14.5, 24.0, -3054.5) beam=1`
- 22.006s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=13,y=22,z=-3059} pos=(13.490,22.750,-3058.663) motion=(0.000,-0.078,0.000)`
- 24.964s：`flow_blocked index=0 goal=(16.5, 24.0, -3057.5) beam=1`
- 29.553s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=16,y=24,z=-3057} pos=(16.499,24.000,-3056.388) motion=(0.000,-0.078,0.000)`
- 35.823s：`flow_blocked index=5 goal=(15.5, 26.0, -3055.5) beam=8`
- 35.938s：`flow_blocked index=0 goal=(15.5, 26.0, -3055.5) beam=1`
- 39.961s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=11,y=26,z=-3056} pos=(11.571,26.000,-3055.436) motion=(0.000,-0.078,0.000)`
- 44.052s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=11,y=25,z=-3055} pos=(11.546,25.232,-3054.300) motion=(0.000,-0.377,0.000)`
- 47.777s：`flow_blocked index=1 goal=(14.5, 24.0, -3054.5) beam=8`
- 47.850s：`flow_blocked index=0 goal=(14.5, 24.0, -3054.5) beam=1`
- 52.006s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=10,y=25,z=-3055} pos=(10.423,25.000,-3054.515) motion=(0.000,-0.078,0.000)`
- 55.576s：`flow_blocked index=0 goal=(8.5, 26.0, -3054.5) beam=1`
- 57.852s：`flow_blocked index=2 goal=(3.5, 29.0, -3057.5) beam=8`
- 61.537s：`flow_blocked index=0 goal=(3.5, 34.0, -3063.5) beam=1`
- 68.656s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=3,y=33,z=-3061} pos=(3.993,33.000,-3060.038) motion=(0.000,-0.078,0.000)`
- 72.197s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=5,y=32,z=-3060} pos=(5.700,32.232,-3059.562) motion=(0.000,-0.377,0.014)`
- 76.779s：`flow_blocked index=1 goal=(7.5, 32.0, -3058.5) beam=8`
- 77.053s：`flow_blocked index=0 goal=(7.5, 32.0, -3058.5) beam=1`
- 77.311s：`flow_blocked index=1 goal=(7.5, 33.0, -3062.5) beam=8`
- 78.768s：`flow_blocked index=1 goal=(7.5, 33.0, -3062.5) beam=8`
- 78.826s：`flow_blocked index=0 goal=(7.5, 33.0, -3062.5) beam=1`
- 79.303s：`flow_blocked index=1 goal=(11.5, 33.0, -3061.5) beam=8`
- 80.771s：`flow_blocked index=1 goal=(11.5, 33.0, -3061.5) beam=8`
- 80.838s：`flow_blocked index=0 goal=(11.5, 33.0, -3061.5) beam=1`
- 87.301s：`flow_blocked index=7 goal=(9.5, 43.5, -3053.675) beam=8`
- 87.416s：`flow_blocked index=0 goal=(9.5, 43.5, -3053.675) beam=1`
