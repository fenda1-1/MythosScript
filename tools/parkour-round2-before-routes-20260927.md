# 实机路线明细

时间为序列开始后的墙钟秒数。运动控制器持续时间包含规划与等待，并非纯移动时间；静止与无控制器时间相互重叠，不能相加当作CPU时间。

## snow-10：通关 83.250s

原始结果：`snow-10-1.json`。

静止 33.121s；控制器时间：`{"noMovement": 6.922, "MovementDiagonal": 1.002, "MovementParkour": 74.546, "MovementDescend": 0.743}`。

事件计数：`{"astar_pop": 18, "surface_candidates": 7, "surface_edge": 57, "astar_move": 584, "planned_route": 2, "path_executor": 5, "flow_contact": 149, "trajectory_plan": 22, "traj_apply": 897, "trajectory_chain_handoff": 55, "trajectory_direct": 14, "trajectory_pre_hop": 3, "trajectory_prefetch": 10, "flow_blocked": 9, "trajectory_search_start": 9, "trajectory_head_runup": 2, "trajectory_search_progress": 5, "trajectory_sprint_runup": 5, "damage_landing_confirm": 12, "fail": 1, "trajectory_pre_hop_failed": 1, "trajectory_ledge": 1, "parkour_exhausted_no_route": 2}`。

### 最长静止区间

| 开始秒 | 持续秒 | 位置 |
|---:|---:|---|
| 53.858 | 9.903 | [44.421885, 14.0, -2061.529546] |
| 0.902 | 3.301 | [0.5, 11.0, -2067.5] |
| 6.952 | 3.100 | [8.668359, 8.0, -2067.386436] |
| 18.452 | 2.354 | [25.178065, 8.0, -2068.534665] |
| 38.661 | 2.293 | [43.076116, 12.0, -2073.604768] |
| 43.049 | 1.959 | [43.505601, 11.0, -2073.498718] |
| 76.766 | 1.947 | [77.389745, 19.0, -2072.39301] |
| 22.206 | 1.600 | [27.440681, 9.0, -2071.440635] |

### 实际控制器逐段路线

| 开始秒 | 持续秒 | 控制器 | 起点 → 终点 |
|---:|---:|---|---|
| 0.000 | 4.253 | 无控制器 | — |
| 4.253 | 0.202 | MovementDiagonal | [0, 11, -2068] → [1, 11, -2069] |
| 4.455 | 0.851 | MovementParkour | [1, 11, -2069] → [4, 10, -2070] |
| 5.306 | 0.600 | MovementParkour | [4, 10, -2070] → [5, 10, -2070] |
| 5.906 | 1.046 | MovementParkour | [5, 10, -2070] → [8, 8, -2068] |
| 6.952 | 5.454 | MovementParkour | [8, 8, -2068] → [12, 9, -2070] |
| 12.406 | 1.397 | MovementParkour | [12, 9, -2070] → [16, 10, -2069] |
| 13.803 | 0.604 | MovementParkour | [16, 10, -2069] → [16, 11, -2068] |
| 14.407 | 0.548 | MovementParkour | [16, 11, -2068] → [18, 12, -2066] |
| 14.955 | 1.899 | MovementParkour | [18, 12, -2066] → [22, 9, -2068] |
| 16.854 | 0.600 | MovementDiagonal | [22, 9, -2068] → [23, 9, -2069] |
| 17.454 | 0.743 | MovementDescend | [23, 9, -2069] → [24, 8, -2069] |
| 18.197 | 2.564 | 无控制器 | — |
| 20.761 | 1.445 | MovementParkour | [24, 8, -2069] → [27, 9, -2072] |
| 22.206 | 2.159 | MovementParkour | [27, 9, -2072] → [28, 10, -2071] |
| 24.365 | 0.344 | MovementParkour | [28, 10, -2071] → [29, 11, -2071] |
| 24.709 | 0.551 | MovementParkour | [29, 11, -2071] → [30, 12, -2072] |
| 25.260 | 0.353 | MovementParkour | [30, 12, -2072] → [30, 13, -2073] |
| 25.613 | 0.447 | MovementParkour | [30, 13, -2073] → [29, 14, -2073] |
| 26.060 | 0.454 | MovementParkour | [29, 14, -2073] → [28, 15, -2073] |
| 26.514 | 0.447 | MovementParkour | [28, 15, -2073] → [28, 16, -2072] |
| 26.961 | 0.550 | MovementParkour | [28, 16, -2072] → [29, 17, -2071] |
| 27.511 | 2.597 | MovementParkour | [29, 17, -2071] → [31, 14, -2065] |
| 30.108 | 2.003 | MovementParkour | [31, 14, -2065] → [30, 15, -2064] |
| 32.111 | 0.449 | MovementParkour | [30, 15, -2064] → [28, 16, -2066] |
| 32.560 | 0.450 | MovementParkour | [28, 16, -2066] → [27, 17, -2066] |
| 33.010 | 0.451 | MovementParkour | [27, 17, -2066] → [27, 18, -2067] |
| 33.461 | 0.450 | MovementParkour | [27, 18, -2067] → [28, 19, -2067] |
| 33.911 | 0.705 | MovementParkour | [28, 19, -2067] → [32, 19, -2068] |
| 34.616 | 0.756 | MovementParkour | [32, 19, -2068] → [30, 19, -2072] |
| 35.372 | 0.491 | MovementParkour | [30, 19, -2072] → [33, 20, -2073] |
| 35.863 | 0.195 | MovementParkour | [33, 20, -2073] → [34, 20, -2073] |
| 36.058 | 0.200 | MovementDiagonal | [34, 20, -2073] → [35, 20, -2074] |
| 36.258 | 1.167 | MovementParkour | [35, 20, -2074] → [38, 15, -2075] |
| 37.425 | 0.385 | MovementParkour | [38, 15, -2075] → [39, 15, -2075] |
| 37.810 | 3.790 | MovementParkour | [39, 15, -2075] → [43, 11, -2074] |
| 41.600 | 4.216 | MovementParkour | [43, 11, -2074] → [47, 11, -2075] |
| 45.816 | 0.602 | MovementParkour | [47, 11, -2075] → [51, 11, -2076] |
| 46.418 | 0.597 | MovementParkour | [51, 11, -2076] → [55, 11, -2076] |
| 47.015 | 0.557 | MovementParkour | [55, 11, -2076] → [58, 12, -2074] |
| 47.572 | 0.444 | MovementParkour | [58, 12, -2074] → [60, 13, -2071] |
| 48.016 | 0.449 | MovementParkour | [60, 13, -2071] → [60, 14, -2068] |
| 48.465 | 0.700 | MovementParkour | [60, 14, -2068] → [59, 14, -2064] |
| 49.165 | 0.656 | MovementParkour | [59, 14, -2064] → [56, 14, -2061] |
| 49.821 | 0.847 | MovementParkour | [56, 14, -2061] → [52, 14, -2060] |
| 50.668 | 1.214 | MovementParkour | [52, 14, -2060] → [48, 14, -2061] |
| 51.882 | 1.976 | MovementParkour | [48, 14, -2061] → [44, 14, -2062] |
| 53.858 | 10.057 | MovementParkour | [44, 14, -2062] → [44, 14, -2063] |
| 63.915 | 0.202 | MovementParkour | [44, 14, -2063] → [44, 14, -2064] |
| 64.117 | 0.754 | MovementParkour | [44, 14, -2064] → [43, 15, -2068] |
| 64.871 | 0.094 | MovementParkour | [43, 15, -2068] → [44, 15, -2068] |
| 64.965 | 0.748 | MovementParkour | [44, 15, -2068] → [48, 16, -2068] |
| 65.713 | 0.450 | MovementParkour | [48, 16, -2068] → [49, 17, -2066] |
| 66.163 | 0.350 | MovementParkour | [49, 17, -2066] → [50, 19, -2068] |
| 66.513 | 0.449 | MovementParkour | [50, 19, -2068] → [53, 19, -2068] |
| 66.962 | 0.153 | MovementParkour | [53, 19, -2068] → [54, 19, -2068] |
| 67.115 | 0.047 | MovementParkour | [54, 19, -2068] → [55, 19, -2068] |
| 67.162 | 0.152 | MovementParkour | [55, 19, -2068] → [56, 19, -2068] |
| 67.314 | 1.497 | MovementParkour | [57, 19, -2068] → [58, 19, -2068] |
| 68.811 | 0.150 | MovementParkour | [58, 19, -2068] → [59, 19, -2068] |
| 68.961 | 0.655 | MovementParkour | [59, 19, -2068] → [65, 19, -2068] |
| 69.616 | 0.047 | MovementParkour | [65, 19, -2068] → [66, 19, -2068] |
| 69.663 | 0.151 | MovementParkour | [66, 19, -2068] → [67, 19, -2068] |
| 69.814 | 0.749 | MovementParkour | [67, 19, -2068] → [71, 19, -2069] |
| 70.563 | 0.098 | MovementParkour | [71, 19, -2069] → [72, 19, -2069] |
| 70.661 | 0.401 | MovementParkour | [72, 19, -2069] → [73, 19, -2069] |
| 71.062 | 2.700 | MovementParkour | [73, 19, -2069] → [77, 19, -2073] |
| 73.762 | 5.300 | MovementParkour | [77, 19, -2073] → [78, 19, -2073] |
| 79.062 | 0.301 | MovementParkour | [78, 19, -2073] → [79, 19, -2073] |
| 79.363 | 0.605 | MovementParkour | [79, 19, -2073] → [83, 19, -2073] |
| 79.968 | 0.097 | MovementParkour | [83, 19, -2073] → [84, 19, -2073] |
| 80.065 | 0.600 | MovementParkour | [84, 19, -2073] → [87, 19, -2071] |
| 80.665 | 0.648 | MovementParkour | [87, 19, -2071] → [90, 19, -2069] |
| 81.313 | 0.152 | MovementParkour | [90, 19, -2069] → [91, 19, -2069] |
| 81.465 | 0.599 | MovementParkour | [91, 19, -2069] → [95, 19, -2068] |
| 82.064 | 0.246 | MovementParkour | [95, 19, -2068] → [96, 19, -2068] |
| 82.310 | 0.300 | MovementParkour | [96, 19, -2068] → [97, 19, -2068] |
| 82.610 | 0.050 | MovementParkour | [97, 19, -2068] → [98, 19, -2068] |
| 82.660 | 0.150 | MovementParkour | [98, 19, -2068] → [99, 19, -2068] |
| 82.810 | 0.298 | MovementParkour | [99, 19, -2068] → [100, 19, -2068] |
| 83.108 | 0.105 | 无控制器 | — |

### 失败及恢复事件

- 5.958s：`flow_blocked index=0 goal=(12.5, 9.0, -2069.5) beam=1`
- 8.054s：`flow_blocked index=0 goal=(12.5, 9.0, -2069.5) beam=1`
- 10.105s：`flow_blocked index=0 goal=(16.5, 10.0, -2068.5) beam=1`
- 18.194s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=24,y=8,z=-2069} pos=(24.953,8.232,-2068.535) motion=(0.076,-0.377,0.002)`
- 20.799s：`flow_blocked index=0 goal=(27.5, 9.0, -2071.5) beam=1`
- 40.871s：`flow_blocked index=10 goal=(44.5, 14.0, -2061.5) beam=7`
- 44.958s：`flow_blocked index=10 goal=(44.5, 14.0, -2061.5) beam=8`
- 45.174s：`flow_blocked index=0 goal=(44.5, 14.0, -2061.5) beam=1`
- 68.533s：`flow_blocked index=8 goal=(77.5, 19.0, -2072.5) beam=8`
- 71.106s：`flow_blocked index=0 goal=(77.5, 19.0, -2072.5) beam=1`

## swamp-55：通关 104.141s

原始结果：`swamp-55-1.json`。

静止 64.859s；控制器时间：`{"noMovement": 39.613, "MovementParkour": 60.188, "MovementDiagonal": 1.558, "MovementAscend": 0.597, "MovementPillar": 1.101, "MovementDescend": 1.083}`。

事件计数：`{"astar_pop": 64, "surface_candidates": 18, "surface_edge": 223, "astar_move": 1825, "planned_route": 9, "flow_contact": 51, "trajectory_plan": 37, "traj_apply": 630, "trajectory_chain_handoff": 25, "path_executor": 16, "trajectory_search_start": 22, "trajectory_ladder": 6, "fail": 8, "surface_accept": 5, "trajectory_ledge": 1, "trajectory_prefetch": 13, "flow_blocked": 19, "trajectory_corner_best": 8, "landing_sneak": 12, "trajectory_search_progress": 24, "trajectory_pre_hop_failed": 4, "trajectory_rejected_edge": 5, "trajectory_corner": 3, "trajectory_runup_try": 6, "trajectory_direct": 3, "trajectory_pre_hop": 1}`。

### 最长静止区间

| 开始秒 | 持续秒 | 位置 |
|---:|---:|---|
| 70.081 | 9.763 | [3.992609, 33.0, -3060.03809] |
| 55.093 | 8.600 | [10.423202, 25.0, -3054.51477] |
| 31.949 | 7.900 | [16.498889, 24.0, -3056.387913] |
| 0.999 | 7.701 | [0.5, 11.0, -3054.5] |
| 44.198 | 6.200 | [11.57079, 26.0, -3055.436082] |
| 17.299 | 4.850 | [10.479674, 21.0, -3054.823874] |
| 80.640 | 4.701 | [5.7, 32.0, -3059.52433] |
| 25.951 | 4.697 | [13.489729, 22.75, -3058.662505] |

### 实际控制器逐段路线

| 开始秒 | 持续秒 | 控制器 | 起点 → 终点 |
|---:|---:|---|---|
| 0.001 | 8.450 | 无控制器 | — |
| 8.451 | 0.451 | MovementParkour | [0, 11, -3055] → [1, 11, -3055] |
| 8.902 | 0.750 | MovementParkour | [1, 11, -3055] → [5, 11, -3054] |
| 9.652 | 0.198 | MovementDiagonal | [5, 11, -3054] → [6, 11, -3053] |
| 9.850 | 0.556 | MovementParkour | [6, 11, -3053] → [7, 12, -3053] |
| 10.406 | 0.946 | MovementParkour | [7, 12, -3053] → [8, 13, -3053] |
| 11.352 | 0.407 | MovementAscend | [8, 13, -3053] → [8, 14, -3054] |
| 11.759 | 1.895 | MovementParkour | [8, 14, -3054] → [9, 15, -3054] |
| 13.654 | 0.550 | MovementPillar | [9, 15, -3054] → [9, 16, -3054] |
| 14.204 | 0.655 | MovementParkour | [9, 16, -3054] → [10, 16, -3055] |
| 14.859 | 1.343 | MovementParkour | [10, 16, -3055] → [10, 19, -3055] |
| 16.202 | 0.551 | MovementPillar | [10, 19, -3055] → [10, 20, -3055] |
| 16.753 | 0.190 | MovementAscend | [10, 20, -3055] → [10, 21, -3056] |
| 16.943 | 4.658 | 无控制器 | — |
| 21.601 | 1.000 | MovementParkour | [10, 21, -3056] → [10, 22, -3057] |
| 22.601 | 1.399 | MovementParkour | [10, 22, -3057] → [9, 23, -3060] |
| 24.000 | 0.449 | MovementParkour | [9, 23, -3060] → [10, 24, -3060] |
| 24.449 | 0.250 | MovementDiagonal | [10, 24, -3060] → [11, 24, -3059] |
| 24.699 | 0.401 | MovementParkour | [11, 24, -3059] → [12, 24, -3059] |
| 25.100 | 0.948 | MovementParkour | [12, 24, -3059] → [13, 22, -3059] |
| 26.048 | 1.170 | MovementParkour | [13, 22, -3059] → [14, 24, -3055] |
| 27.218 | 3.232 | 无控制器 | — |
| 30.450 | 1.199 | MovementParkour | [13, 22, -3059] → [16, 24, -3058] |
| 31.649 | 0.353 | MovementParkour | [16, 24, -3058] → [16, 24, -3057] |
| 32.002 | 3.097 | MovementParkour | [16, 24, -3057] → [18, 25, -3056] |
| 35.099 | 4.301 | 无控制器 | — |
| 39.400 | 0.651 | MovementParkour | [16, 24, -3057] → [16, 24, -3058] |
| 40.051 | 0.748 | MovementParkour | [16, 24, -3058] → [12, 24, -3059] |
| 40.799 | 0.207 | MovementDiagonal | [12, 24, -3059] → [11, 24, -3058] |
| 41.006 | 1.646 | MovementParkour | [11, 24, -3058] → [11, 24, -3057] |
| 42.652 | 0.452 | MovementParkour | [11, 24, -3057] → [10, 25, -3057] |
| 43.104 | 0.351 | MovementParkour | [10, 25, -3057] → [10, 25, -3055] |
| 43.455 | 0.247 | MovementParkour | [10, 25, -3055] → [11, 25, -3055] |
| 43.702 | 0.553 | MovementParkour | [11, 25, -3055] → [11, 26, -3056] |
| 44.255 | 2.193 | MovementParkour | [11, 26, -3056] → [15, 26, -3056] |
| 46.448 | 4.001 | 无控制器 | — |
| 50.449 | 0.490 | MovementDescend | [11, 26, -3056] → [11, 25, -3055] |
| 50.939 | 3.709 | 无控制器 | — |
| 54.648 | 0.494 | MovementParkour | [11, 25, -3055] → [10, 25, -3055] |
| 55.142 | 4.000 | MovementParkour | [10, 25, -3055] → [14, 24, -3055] |
| 59.142 | 4.202 | 无控制器 | — |
| 63.344 | 1.849 | MovementParkour | [10, 25, -3055] → [8, 26, -3055] |
| 65.193 | 0.252 | MovementDiagonal | [8, 26, -3055] → [7, 26, -3056] |
| 65.445 | 0.553 | MovementParkour | [7, 26, -3056] → [7, 27, -3057] |
| 65.998 | 0.546 | MovementParkour | [7, 27, -3057] → [6, 28, -3058] |
| 66.544 | 0.800 | MovementParkour | [6, 28, -3058] → [3, 29, -3058] |
| 67.344 | 0.051 | MovementParkour | [3, 29, -3058] → [2, 29, -3058] |
| 67.395 | 0.450 | MovementParkour | [2, 29, -3058] → [2, 30, -3059] |
| 67.845 | 0.349 | MovementParkour | [2, 30, -3059] → [3, 31, -3059] |
| 68.194 | 0.451 | MovementParkour | [3, 31, -3059] → [5, 32, -3059] |
| 68.645 | 0.150 | MovementParkour | [5, 32, -3059] → [5, 32, -3060] |
| 68.795 | 0.449 | MovementParkour | [5, 32, -3060] → [4, 33, -3060] |
| 69.244 | 0.200 | MovementDiagonal | [4, 33, -3060] → [3, 33, -3061] |
| 69.444 | 7.149 | MovementParkour | [3, 33, -3061] → [3, 34, -3064] |
| 76.593 | 3.302 | 无控制器 | — |
| 79.895 | 0.049 | MovementDiagonal | [3, 33, -3061] → [4, 33, -3060] |
| 79.944 | 0.593 | MovementDescend | [4, 33, -3060] → [5, 32, -3060] |
| 80.537 | 3.708 | 无控制器 | — |
| 84.245 | 1.398 | MovementParkour | [5, 32, -3060] → [5, 32, -3059] |
| 85.643 | 1.401 | MovementParkour | [5, 32, -3059] → [7, 32, -3059] |
| 87.044 | 0.601 | MovementParkour | [7, 32, -3059] → [7, 32, -3060] |
| 87.645 | 1.350 | MovementParkour | [7, 32, -3060] → [7, 33, -3063] |
| 88.995 | 0.899 | MovementParkour | [7, 33, -3063] → [7, 34, -3062] |
| 89.894 | 0.848 | MovementParkour | [7, 34, -3062] → [11, 33, -3062] |
| 90.742 | 1.451 | MovementParkour | [11, 33, -3062] → [11, 34, -3063] |
| 92.193 | 0.251 | MovementParkour | [11, 34, -3063] → [12, 34, -3061] |
| 92.444 | 0.498 | MovementParkour | [12, 34, -3061] → [12, 35, -3060] |
| 92.942 | 0.451 | MovementParkour | [12, 35, -3060] → [12, 36, -3059] |
| 93.393 | 0.499 | MovementParkour | [12, 36, -3059] → [12, 37, -3058] |
| 93.892 | 0.402 | MovementDiagonal | [12, 37, -3058] → [11, 37, -3057] |
| 94.294 | 2.102 | MovementParkour | [11, 37, -3057] → [11, 38, -3056] |
| 96.396 | 0.350 | MovementParkour | [11, 38, -3056] → [12, 39, -3056] |
| 96.746 | 0.152 | MovementParkour | [12, 39, -3056] → [13, 39, -3056] |
| 96.898 | 0.198 | MovementParkour | [13, 39, -3056] → [14, 39, -3056] |
| 97.096 | 0.600 | MovementParkour | [14, 39, -3056] → [11, 40, -3055] |
| 97.696 | 0.448 | MovementParkour | [11, 40, -3055] → [11, 41, -3054] |
| 98.144 | 0.498 | MovementParkour | [11, 41, -3054] → [10, 42, -3054] |
| 98.642 | 1.350 | MovementParkour | [10, 42, -3054] → [9, 43, -3054] |
| 99.992 | 2.001 | MovementParkour | [9, 43, -3054] → [8, 44, -3055] |
| 101.993 | 1.348 | MovementParkour | [8, 44, -3055] → [8, 45, -3056] |
| 103.341 | 0.750 | MovementParkour | [8, 45, -3056] → [5, 46, -3055] |
| 104.091 | 0.050 | 无控制器 | — |

### 失败及恢复事件

- 16.941s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=10,y=21,z=-3056} pos=(10.480,21.001,-3054.720) motion=(0.000,0.165,-0.018)`
- 25.174s：`flow_blocked index=0 goal=(14.5, 24.0, -3054.5) beam=1`
- 27.216s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=13,y=22,z=-3059} pos=(13.490,22.750,-3058.663) motion=(0.000,-0.078,0.000)`
- 30.510s：`flow_blocked index=0 goal=(16.5, 24.0, -3057.5) beam=1`
- 35.097s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=16,y=24,z=-3057} pos=(16.499,24.000,-3056.388) motion=(0.000,-0.078,0.000)`
- 42.301s：`flow_blocked index=5 goal=(15.5, 26.0, -3055.5) beam=8`
- 42.435s：`flow_blocked index=0 goal=(15.5, 26.0, -3055.5) beam=1`
- 46.447s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=11,y=26,z=-3056} pos=(11.571,26.000,-3055.436) motion=(0.000,-0.078,0.000)`
- 50.938s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=11,y=25,z=-3055} pos=(11.546,25.232,-3054.300) motion=(0.000,-0.377,0.000)`
- 54.922s：`flow_blocked index=1 goal=(14.5, 24.0, -3054.5) beam=8`
- 54.995s：`flow_blocked index=0 goal=(14.5, 24.0, -3054.5) beam=1`
- 59.141s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=10,y=25,z=-3055} pos=(10.423,25.000,-3054.515) motion=(0.000,-0.078,0.000)`
- 63.415s：`flow_blocked index=0 goal=(8.5, 26.0, -3054.5) beam=1`
- 65.714s：`flow_blocked index=2 goal=(3.5, 29.0, -3057.5) beam=8`
- 69.475s：`flow_blocked index=0 goal=(3.5, 34.0, -3063.5) beam=1`
- 76.592s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=3,y=33,z=-3061} pos=(3.993,33.000,-3060.038) motion=(0.000,-0.078,0.000)`
- 80.533s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=5,y=32,z=-3060} pos=(5.700,32.232,-3059.562) motion=(0.000,-0.377,0.014)`
- 85.377s：`flow_blocked index=1 goal=(7.5, 32.0, -3058.5) beam=8`
- 85.625s：`flow_blocked index=0 goal=(7.5, 32.0, -3058.5) beam=1`
- 85.891s：`flow_blocked index=1 goal=(7.5, 33.0, -3062.5) beam=8`
- 87.339s：`flow_blocked index=1 goal=(7.5, 33.0, -3062.5) beam=8`
- 87.413s：`flow_blocked index=0 goal=(7.5, 33.0, -3062.5) beam=1`
- 87.929s：`flow_blocked index=1 goal=(11.5, 33.0, -3061.5) beam=8`
- 89.354s：`flow_blocked index=1 goal=(11.5, 33.0, -3061.5) beam=8`
- 89.426s：`flow_blocked index=0 goal=(11.5, 33.0, -3061.5) beam=1`
- 95.914s：`flow_blocked index=7 goal=(9.5, 43.5, -3053.675) beam=8`
- 96.005s：`flow_blocked index=0 goal=(9.5, 43.5, -3053.675) beam=1`

## swamp-60：通关 99.078s

原始结果：`swamp-60-1.json`。

静止 28.347s；控制器时间：`{"noMovement": 21.879, "MovementDiagonal": 2.685, "MovementParkour": 59.411, "MovementDescend": 0.96, "MovementAscend": 0.438, "MovementPistonLaunch": 2.752, "MovementSlimeBounce": 10.914}`。

事件计数：`{"astar_pop": 105, "surface_candidates": 30, "surface_edge": 657, "astar_move": 3285, "planned_route": 8, "path_executor": 12, "flow_contact": 86, "trajectory_plan": 42, "traj_apply": 891, "trajectory_chain_handoff": 28, "trajectory_pre_hop_failed": 1, "trajectory_search_start": 19, "trajectory_ladder": 12, "trajectory_prefetch": 9, "trajectory_direct": 13, "fail": 2, "trajectory_replan": 2, "poison_launch_wait": 6, "trajectory_corner": 1, "parkour_goal_loaded": 1, "parkour_capability_frontier": 2, "flow_blocked": 8, "slime_chain_refined": 3, "slime_chain_plan": 3, "parkour_exhausted_no_route": 2, "parkour_capability_changed": 2, "trajectory_corner_best": 1}`。

### 最长静止区间

| 开始秒 | 持续秒 | 位置 |
|---:|---:|---|
| 52.152 | 7.351 | [80.928747, 27.0, -3288.318359] |
| 61.342 | 6.551 | [81.744102, 27.0, -3288.380252] |
| 73.590 | 2.056 | [103.56173, 26.0, -3289.496486] |
| 0.613 | 1.881 | [0.5, 11.0, -3289.5] |
| 21.651 | 1.400 | [32.579208, 18.0, -3289.501434] |
| 31.989 | 0.802 | [49.449386, 16.0, -3290.25198] |
| 30.489 | 0.801 | [46.494865, 17.0, -3289.497445] |
| 81.498 | 0.797 | [110.505287, 46.0, -3287.523747] |

### 实际控制器逐段路线

| 开始秒 | 持续秒 | 控制器 | 起点 → 终点 |
|---:|---:|---|---|
| 0.000 | 2.544 | 无控制器 | — |
| 2.544 | 0.252 | MovementDiagonal | [0, 11, -3290] → [1, 11, -3291] |
| 2.796 | 1.053 | MovementParkour | [1, 11, -3291] → [5, 11, -3292] |
| 3.849 | 0.247 | MovementParkour | [5, 11, -3292] → [6, 11, -3292] |
| 4.096 | 0.256 | MovementDiagonal | [6, 11, -3292] → [7, 11, -3291] |
| 4.352 | 2.949 | MovementParkour | [7, 11, -3291] → [11, 11, -3291] |
| 7.301 | 0.850 | MovementParkour | [11, 11, -3291] → [11, 12, -3291] |
| 8.151 | 0.447 | MovementParkour | [11, 12, -3291] → [11, 13, -3291] |
| 8.598 | 1.451 | MovementParkour | [11, 13, -3291] → [12, 14, -3290] |
| 10.049 | 0.402 | MovementParkour | [12, 14, -3290] → [13, 14, -3290] |
| 10.451 | 0.347 | MovementParkour | [13, 14, -3290] → [14, 14, -3290] |
| 10.798 | 0.504 | MovementDescend | [14, 14, -3290] → [15, 13, -3290] |
| 11.302 | 1.848 | MovementParkour | [15, 13, -3290] → [19, 13, -3291] |
| 13.150 | 1.598 | MovementParkour | [19, 13, -3291] → [20, 14, -3291] |
| 14.748 | 0.942 | MovementParkour | [20, 14, -3291] → [21, 14, -3291] |
| 15.690 | 0.202 | MovementParkour | [21, 14, -3291] → [22, 14, -3291] |
| 15.892 | 0.199 | MovementParkour | [22, 14, -3291] → [23, 14, -3291] |
| 16.091 | 0.499 | MovementParkour | [23, 14, -3291] → [23, 15, -3292] |
| 16.590 | 1.648 | MovementParkour | [23, 15, -3292] → [27, 16, -3290] |
| 18.238 | 1.802 | MovementParkour | [27, 16, -3290] → [29, 17, -3290] |
| 20.040 | 0.438 | MovementAscend | [29, 17, -3290] → [30, 18, -3290] |
| 20.478 | 0.573 | 无控制器 | — |
| 21.051 | 0.353 | MovementParkour | [30, 18, -3290] → [31, 18, -3290] |
| 21.404 | 0.296 | MovementParkour | [31, 18, -3290] → [32, 18, -3290] |
| 21.700 | 1.202 | 无控制器 | — |
| 22.902 | 2.002 | MovementParkour | [32, 18, -3290] → [36, 20, -3290] |
| 24.904 | 3.145 | MovementParkour | [36, 20, -3290] → [37, 20, -3291] |
| 28.049 | 0.453 | MovementParkour | [37, 20, -3291] → [38, 20, -3291] |
| 28.502 | 0.106 | MovementParkour | [38, 20, -3291] → [39, 20, -3291] |
| 28.608 | 0.234 | MovementDiagonal | [39, 20, -3291] → [40, 20, -3290] |
| 28.842 | 0.252 | MovementDiagonal | [40, 20, -3290] → [41, 20, -3289] |
| 29.094 | 0.300 | MovementParkour | [41, 20, -3289] → [42, 20, -3289] |
| 29.394 | 0.198 | MovementParkour | [42, 20, -3289] → [43, 20, -3289] |
| 29.592 | 0.897 | MovementParkour | [43, 20, -3289] → [46, 17, -3290] |
| 30.489 | 1.554 | MovementParkour | [46, 17, -3290] → [49, 16, -3290] |
| 32.043 | 1.400 | MovementParkour | [49, 16, -3290] → [52, 16, -3292] |
| 33.443 | 1.350 | MovementParkour | [52, 16, -3292] → [55, 16, -3290] |
| 34.793 | 0.450 | MovementParkour | [55, 16, -3290] → [57, 17, -3290] |
| 35.243 | 0.450 | MovementParkour | [57, 17, -3290] → [60, 18, -3291] |
| 35.693 | 0.099 | MovementParkour | [60, 18, -3291] → [61, 18, -3291] |
| 35.792 | 1.801 | MovementParkour | [61, 18, -3291] → [64, 19, -3290] |
| 37.593 | 1.302 | MovementPistonLaunch | [64, 19, -3290] → [69, 23, -3290] |
| 38.895 | 1.450 | MovementPistonLaunch | [69, 23, -3290] → [74, 25, -3290] |
| 40.345 | 0.302 | MovementParkour | [74, 25, -3290] → [74, 25, -3289] |
| 40.647 | 0.196 | MovementParkour | [74, 25, -3289] → [75, 25, -3289] |
| 40.843 | 0.456 | MovementDescend | [75, 25, -3289] → [76, 24, -3289] |
| 41.299 | 2.394 | MovementParkour | [76, 24, -3289] → [79, 24, -3289] |
| 43.693 | 0.851 | MovementParkour | [79, 24, -3289] → [79, 25, -3289] |
| 44.544 | 1.749 | MovementParkour | [79, 25, -3289] → [80, 26, -3289] |
| 46.293 | 5.559 | MovementParkour | [80, 26, -3289] → [81, 27, -3289] |
| 51.852 | 7.651 | 无控制器 | — |
| 59.503 | 0.192 | MovementParkour | [81, 27, -3289] → [85, 28, -3289] |
| 59.695 | 8.098 | 无控制器 | — |
| 67.793 | 0.653 | MovementParkour | [81, 27, -3289] → [84, 28, -3289] |
| 68.446 | 0.239 | MovementParkour | [84, 28, -3289] → [85, 28, -3289] |
| 68.685 | 2.858 | MovementSlimeBounce | [85, 28, -3289] → [96, 24, -3291] |
| 71.543 | 0.750 | MovementParkour | [96, 24, -3291] → [97, 24, -3291] |
| 72.293 | 0.200 | MovementParkour | [97, 24, -3291] → [98, 24, -3291] |
| 72.493 | 0.700 | MovementParkour | [98, 24, -3291] → [102, 25, -3290] |
| 73.193 | 0.449 | MovementParkour | [102, 25, -3290] → [103, 26, -3290] |
| 73.642 | 1.761 | 无控制器 | — |
| 75.403 | 0.548 | MovementParkour | [103, 26, -3290] → [103, 26, -3289] |
| 75.951 | 1.345 | MovementParkour | [103, 26, -3289] → [104, 31, -3289] |
| 77.296 | 1.501 | MovementParkour | [104, 31, -3289] → [105, 36, -3289] |
| 78.797 | 1.453 | MovementParkour | [105, 36, -3289] → [105, 41, -3289] |
| 80.250 | 1.248 | MovementParkour | [105, 41, -3289] → [110, 46, -3288] |
| 81.498 | 0.994 | MovementParkour | [110, 46, -3288] → [111, 46, -3288] |
| 82.492 | 0.156 | MovementParkour | [111, 46, -3288] → [112, 46, -3288] |
| 82.648 | 1.296 | MovementParkour | [112, 46, -3288] → [117, 45, -3291] |
| 83.944 | 0.150 | MovementParkour | [117, 45, -3291] → [118, 45, -3291] |
| 84.094 | 0.250 | MovementParkour | [118, 45, -3291] → [119, 45, -3291] |
| 84.344 | 5.455 | MovementSlimeBounce | [119, 45, -3291] → [146, 23, -3290] |
| 89.799 | 0.651 | MovementParkour | [146, 23, -3290] → [146, 23, -3289] |
| 90.450 | 0.452 | MovementParkour | [146, 23, -3289] → [147, 24, -3289] |
| 90.902 | 0.447 | MovementParkour | [147, 24, -3289] → [148, 25, -3289] |
| 91.349 | 2.601 | MovementSlimeBounce | [148, 25, -3289] → [160, 18, -3292] |
| 93.950 | 0.750 | MovementParkour | [160, 18, -3292] → [160, 18, -3293] |
| 94.700 | 0.799 | MovementParkour | [160, 18, -3293] → [161, 18, -3293] |
| 95.499 | 0.800 | MovementParkour | [161, 18, -3293] → [162, 18, -3293] |
| 96.299 | 0.602 | MovementParkour | [162, 18, -3293] → [163, 18, -3293] |
| 96.901 | 0.397 | MovementParkour | [163, 18, -3293] → [164, 18, -3293] |
| 97.298 | 0.499 | MovementDiagonal | [164, 18, -3293] → [165, 18, -3292] |
| 97.797 | 0.801 | MovementDiagonal | [165, 18, -3292] → [166, 18, -3291] |
| 98.598 | 0.391 | MovementDiagonal | [166, 18, -3291] → [167, 18, -3290] |
| 98.989 | 0.050 | 无控制器 | — |

### 失败及恢复事件

- 20.477s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=30,y=18,z=-3290} pos=(29.720,18.126,-3289.515) motion=(0.018,0.216,0.000)`
- 25.048s：`trajectory_replan error=0.21225207354627784`
- 51.850s：`path_executor position=11 feet=BetterBlockPos{x=80,y=28,z=-3289} reason=This movement has taken too long (111 ticks, expected 10.632846884410469, movement MovementParkour). Cancelling.`
- 59.501s：`flow_blocked index=0 goal=(85.5, 28.0, -3288.5) beam=1`
- 59.649s：`trajectory_replan error=0.35865032426737287`
- 59.690s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=81,y=27,z=-3289} pos=(81.098,27.420,-3288.332) motion=(0.082,0.333,-0.007)`
- 75.653s：`flow_blocked index=1 goal=(104.5, 31.0, -3288.5) beam=8`
- 75.726s：`flow_blocked index=0 goal=(104.5, 31.0, -3288.5) beam=1`
- 75.969s：`flow_blocked index=0 goal=(105.5, 36.0, -3288.5) beam=1`
- 77.420s：`flow_blocked index=0 goal=(105.5, 36.0, -3288.5) beam=1`
- 77.458s：`flow_blocked index=0 goal=(105.5, 41.0, -3288.5) beam=1`
- 78.909s：`flow_blocked index=0 goal=(105.5, 41.0, -3288.5) beam=1`
- 79.026s：`flow_blocked index=0 goal=(110.5, 46.0, -3287.5) beam=1`
