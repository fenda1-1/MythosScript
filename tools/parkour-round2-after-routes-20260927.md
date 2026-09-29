# 实机路线明细

时间为序列开始后的墙钟秒数。运动控制器持续时间包含规划与等待，并非纯移动时间；静止与无控制器时间相互重叠，不能相加当作CPU时间。

## snow-10：通关 69.766s

原始结果：`snow-10-1.json`。

静止 20.295s；控制器时间：`{"noMovement": 6.443, "MovementDiagonal": 1.014, "MovementParkour": 61.556, "MovementDescend": 0.741}`。

事件计数：`{"astar_pop": 16, "surface_candidates": 6, "surface_edge": 57, "astar_move": 584, "planned_route": 2, "path_executor": 6, "flow_contact": 153, "trajectory_plan": 25, "traj_apply": 920, "trajectory_chain_handoff": 53, "trajectory_direct": 17, "trajectory_pre_hop": 3, "trajectory_prefetch": 15, "flow_blocked": 10, "trajectory_search_start": 8, "trajectory_corner_best": 1, "trajectory_head_runup": 2, "trajectory_search_progress": 4, "trajectory_sprint_runup": 9, "damage_landing_confirm": 12, "fail": 1, "trajectory_pre_hop_failed": 1, "trajectory_ledge": 1, "parkour_exhausted_no_route": 2}`。

### 最长静止区间

| 开始秒 | 持续秒 | 位置 |
|---:|---:|---|
| 0.951 | 3.601 | [0.5, 11.0, -2067.5] |
| 7.500 | 3.100 | [8.668306, 8.0, -2067.386501] |
| 63.254 | 2.350 | [77.36203, 19.0, -2072.367455] |
| 53.058 | 1.646 | [43.702962, 15.375, -2067.156418] |
| 18.995 | 1.552 | [25.178065, 8.0, -2068.534666] |
| 29.747 | 1.503 | [31.456267, 14.0, -2064.439778] |
| 21.946 | 1.450 | [27.440681, 9.0, -2071.440635] |
| 50.703 | 1.250 | [44.446382, 14.0, -2061.522376] |

### 实际控制器逐段路线

| 开始秒 | 持续秒 | 控制器 | 起点 → 终点 |
|---:|---:|---|---|
| 0.000 | 4.633 | 无控制器 | — |
| 4.633 | 0.211 | MovementDiagonal | [0, 11, -2068] → [1, 11, -2069] |
| 4.844 | 1.020 | MovementParkour | [1, 11, -2069] → [4, 10, -2070] |
| 5.864 | 0.594 | MovementParkour | [4, 10, -2070] → [5, 10, -2070] |
| 6.458 | 1.042 | MovementParkour | [5, 10, -2070] → [8, 8, -2068] |
| 7.500 | 5.455 | MovementParkour | [8, 8, -2068] → [12, 9, -2070] |
| 12.955 | 1.396 | MovementParkour | [12, 9, -2070] → [16, 10, -2069] |
| 14.351 | 0.603 | MovementParkour | [16, 10, -2069] → [16, 11, -2068] |
| 14.954 | 0.548 | MovementParkour | [16, 11, -2068] → [18, 12, -2066] |
| 15.502 | 1.895 | MovementParkour | [18, 12, -2066] → [22, 9, -2068] |
| 17.397 | 0.603 | MovementDiagonal | [22, 9, -2068] → [23, 9, -2069] |
| 18.000 | 0.741 | MovementDescend | [23, 9, -2069] → [24, 8, -2069] |
| 18.741 | 1.759 | 无控制器 | — |
| 20.500 | 1.446 | MovementParkour | [24, 8, -2069] → [27, 9, -2072] |
| 21.946 | 2.004 | MovementParkour | [27, 9, -2072] → [28, 10, -2071] |
| 23.950 | 0.349 | MovementParkour | [28, 10, -2071] → [29, 11, -2071] |
| 24.299 | 0.553 | MovementParkour | [29, 11, -2071] → [30, 12, -2072] |
| 24.852 | 0.348 | MovementParkour | [30, 12, -2072] → [30, 13, -2073] |
| 25.200 | 0.453 | MovementParkour | [30, 13, -2073] → [29, 14, -2073] |
| 25.653 | 0.454 | MovementParkour | [29, 14, -2073] → [28, 15, -2073] |
| 26.107 | 0.447 | MovementParkour | [28, 15, -2073] → [28, 16, -2072] |
| 26.554 | 0.551 | MovementParkour | [28, 16, -2072] → [29, 17, -2071] |
| 27.105 | 2.642 | MovementParkour | [29, 17, -2071] → [31, 14, -2065] |
| 29.747 | 2.002 | MovementParkour | [31, 14, -2065] → [30, 15, -2064] |
| 31.749 | 0.451 | MovementParkour | [30, 15, -2064] → [28, 16, -2066] |
| 32.200 | 0.449 | MovementParkour | [28, 16, -2066] → [27, 17, -2066] |
| 32.649 | 0.459 | MovementParkour | [27, 17, -2066] → [27, 18, -2067] |
| 33.108 | 0.450 | MovementParkour | [27, 18, -2067] → [28, 19, -2067] |
| 33.558 | 0.699 | MovementParkour | [28, 19, -2067] → [32, 19, -2068] |
| 34.257 | 0.749 | MovementParkour | [32, 19, -2068] → [30, 19, -2072] |
| 35.006 | 0.500 | MovementParkour | [30, 19, -2072] → [33, 20, -2073] |
| 35.506 | 0.199 | MovementParkour | [33, 20, -2073] → [34, 20, -2073] |
| 35.705 | 0.200 | MovementDiagonal | [34, 20, -2073] → [35, 20, -2074] |
| 35.905 | 1.148 | MovementParkour | [35, 20, -2074] → [38, 15, -2075] |
| 37.053 | 0.401 | MovementParkour | [38, 15, -2075] → [39, 15, -2075] |
| 37.454 | 1.750 | MovementParkour | [39, 15, -2075] → [43, 11, -2074] |
| 39.204 | 1.655 | MovementParkour | [43, 11, -2074] → [47, 11, -2075] |
| 40.859 | 0.597 | MovementParkour | [47, 11, -2075] → [51, 11, -2076] |
| 41.456 | 0.603 | MovementParkour | [51, 11, -2076] → [55, 11, -2076] |
| 42.059 | 0.553 | MovementParkour | [55, 11, -2076] → [58, 12, -2074] |
| 42.612 | 0.444 | MovementParkour | [58, 12, -2074] → [60, 13, -2071] |
| 43.056 | 0.450 | MovementParkour | [60, 13, -2071] → [60, 14, -2068] |
| 43.506 | 0.701 | MovementParkour | [60, 14, -2068] → [59, 14, -2064] |
| 44.207 | 0.800 | MovementParkour | [59, 14, -2064] → [56, 14, -2061] |
| 45.007 | 1.597 | MovementParkour | [56, 14, -2061] → [52, 14, -2060] |
| 46.604 | 2.004 | MovementParkour | [52, 14, -2060] → [48, 14, -2061] |
| 48.608 | 2.095 | MovementParkour | [48, 14, -2061] → [44, 14, -2062] |
| 50.703 | 1.454 | MovementParkour | [44, 14, -2062] → [44, 14, -2063] |
| 52.157 | 0.206 | MovementParkour | [44, 14, -2063] → [44, 14, -2064] |
| 52.363 | 0.695 | MovementParkour | [44, 14, -2064] → [43, 15, -2068] |
| 53.058 | 1.750 | MovementParkour | [43, 15, -2068] → [44, 15, -2068] |
| 54.808 | 0.801 | MovementParkour | [44, 15, -2068] → [48, 16, -2068] |
| 55.609 | 0.347 | MovementParkour | [48, 16, -2068] → [49, 17, -2066] |
| 55.956 | 0.350 | MovementParkour | [49, 17, -2066] → [50, 19, -2068] |
| 56.306 | 0.450 | MovementParkour | [50, 19, -2068] → [53, 19, -2068] |
| 56.756 | 0.150 | MovementParkour | [53, 19, -2068] → [54, 19, -2068] |
| 56.906 | 0.049 | MovementParkour | [54, 19, -2068] → [55, 19, -2068] |
| 56.955 | 0.155 | MovementParkour | [55, 19, -2068] → [56, 19, -2068] |
| 57.110 | 1.395 | MovementParkour | [57, 19, -2068] → [58, 19, -2068] |
| 58.505 | 0.150 | MovementParkour | [58, 19, -2068] → [59, 19, -2068] |
| 58.655 | 0.651 | MovementParkour | [59, 19, -2068] → [65, 19, -2068] |
| 59.306 | 0.053 | MovementParkour | [65, 19, -2068] → [66, 19, -2068] |
| 59.359 | 0.146 | MovementParkour | [66, 19, -2068] → [67, 19, -2068] |
| 59.505 | 0.700 | MovementParkour | [67, 19, -2068] → [71, 19, -2069] |
| 60.205 | 0.154 | MovementParkour | [71, 19, -2069] → [72, 19, -2069] |
| 60.359 | 0.349 | MovementParkour | [72, 19, -2069] → [73, 19, -2069] |
| 60.708 | 2.546 | MovementParkour | [73, 19, -2069] → [77, 19, -2073] |
| 63.254 | 2.703 | MovementParkour | [77, 19, -2073] → [78, 19, -2073] |
| 65.957 | 0.301 | MovementParkour | [78, 19, -2073] → [79, 19, -2073] |
| 66.258 | 0.606 | MovementParkour | [79, 19, -2073] → [83, 19, -2073] |
| 66.864 | 0.147 | MovementParkour | [83, 19, -2073] → [84, 19, -2073] |
| 67.011 | 0.603 | MovementParkour | [84, 19, -2073] → [87, 19, -2071] |
| 67.614 | 0.596 | MovementParkour | [87, 19, -2071] → [90, 19, -2069] |
| 68.210 | 0.050 | MovementParkour | [90, 19, -2069] → [91, 19, -2069] |
| 68.260 | 0.604 | MovementParkour | [91, 19, -2069] → [95, 19, -2068] |
| 68.864 | 0.244 | MovementParkour | [95, 19, -2068] → [96, 19, -2068] |
| 69.108 | 0.149 | MovementParkour | [96, 19, -2068] → [97, 19, -2068] |
| 69.257 | 0.050 | MovementParkour | [97, 19, -2068] → [98, 19, -2068] |
| 69.307 | 0.150 | MovementParkour | [98, 19, -2068] → [99, 19, -2068] |
| 69.457 | 0.246 | MovementParkour | [99, 19, -2068] → [100, 19, -2068] |
| 69.703 | 0.051 | 无控制器 | — |

### 失败及恢复事件

- 6.516s：`flow_blocked index=0 goal=(12.5, 9.0, -2069.5) beam=1`
- 8.610s：`flow_blocked index=0 goal=(12.5, 9.0, -2069.5) beam=1`
- 10.657s：`flow_blocked index=0 goal=(16.5, 10.0, -2068.5) beam=1`
- 18.738s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=24,y=8,z=-2069} pos=(24.953,8.232,-2068.535) motion=(0.076,-0.377,0.002)`
- 20.538s：`flow_blocked index=0 goal=(27.5, 9.0, -2071.5) beam=1`
- 40.159s：`flow_blocked index=0 goal=(52.5, 13.875, -2059.5) beam=1`
- 45.064s：`flow_blocked index=0 goal=(48.5, 13.875, -2060.5) beam=1`
- 46.760s：`flow_blocked index=0 goal=(48.5, 13.875, -2060.5) beam=1`
- 46.859s：`flow_blocked index=0 goal=(44.5, 14.0, -2061.5) beam=1`
- 58.195s：`flow_blocked index=8 goal=(77.5, 19.0, -2072.5) beam=8`
- 58.293s：`flow_blocked index=0 goal=(77.5, 19.0, -2072.5) beam=1`

## swamp-55：失败停止 1.484s

原始结果：`swamp-55-1.json`。

静止 1.041s；控制器时间：`{"noMovement": 1.443}`。

事件计数：`{}`。

### 最长静止区间

| 开始秒 | 持续秒 | 位置 |
|---:|---:|---|
| 0.901 | 0.542 | [0.5, 11.0, -3054.5] |
| 0.000 | 0.499 | [-8999.5, 52.0, -10025.5] |

### 实际控制器逐段路线

| 开始秒 | 持续秒 | 控制器 | 起点 → 终点 |
|---:|---:|---|---|
| 0.000 | 1.443 | 无控制器 | — |

### 失败及恢复事件


## swamp-60：通关 96.094s

原始结果：`swamp-60-1.json`。

静止 25.950s；控制器时间：`{"noMovement": 17.088, "MovementDiagonal": 2.708, "MovementParkour": 60.443, "MovementDescend": 1.017, "MovementAscend": 0.439, "MovementPistonLaunch": 3.454, "MovementSlimeBounce": 10.896}`。

事件计数：`{"astar_pop": 116, "surface_candidates": 36, "surface_edge": 733, "astar_move": 3796, "planned_route": 8, "path_executor": 12, "flow_contact": 116, "trajectory_plan": 49, "traj_apply": 862, "trajectory_chain_handoff": 29, "trajectory_pre_hop_failed": 1, "trajectory_search_start": 17, "trajectory_ladder": 9, "trajectory_prefetch": 9, "trajectory_direct": 13, "fail": 2, "trajectory_pre_hop": 1, "poison_launch_wait": 3, "trajectory_corner": 1, "parkour_goal_loaded": 1, "parkour_capability_frontier": 2, "flow_blocked": 8, "trajectory_replan": 11, "slime_chain_refined": 3, "slime_chain_plan": 3, "parkour_exhausted_no_route": 5, "parkour_capability_changed": 2, "trajectory_corner_best": 1}`。

### 最长静止区间

| 开始秒 | 持续秒 | 位置 |
|---:|---:|---|
| 50.300 | 5.902 | [80.886321, 27.0, -3288.315376] |
| 57.046 | 5.501 | [81.701675, 27.0, -3288.377718] |
| 0.502 | 1.950 | [0.5, 11.0, -3289.5] |
| 68.247 | 1.299 | [103.560544, 26.0, -3289.496557] |
| 21.502 | 0.901 | [32.579208, 18.0, -3289.501434] |
| 27.302 | 0.848 | [46.551459, 17.0, -3289.515368] |
| 28.851 | 0.749 | [49.506845, 16.0, -3290.266503] |
| 75.398 | 0.648 | [110.505313, 46.0, -3287.523703] |

### 实际控制器逐段路线

| 开始秒 | 持续秒 | 控制器 | 起点 → 终点 |
|---:|---:|---|---|
| 0.001 | 2.504 | 无控制器 | — |
| 2.505 | 0.259 | MovementDiagonal | [0, 11, -3290] → [1, 11, -3291] |
| 2.764 | 1.045 | MovementParkour | [1, 11, -3291] → [5, 11, -3292] |
| 3.809 | 0.247 | MovementParkour | [5, 11, -3292] → [6, 11, -3292] |
| 4.056 | 0.248 | MovementDiagonal | [6, 11, -3292] → [7, 11, -3291] |
| 4.304 | 2.999 | MovementParkour | [7, 11, -3291] → [11, 11, -3291] |
| 7.303 | 0.851 | MovementParkour | [11, 11, -3291] → [11, 12, -3291] |
| 8.154 | 0.450 | MovementParkour | [11, 12, -3291] → [11, 13, -3291] |
| 8.604 | 1.449 | MovementParkour | [11, 13, -3291] → [12, 14, -3290] |
| 10.053 | 0.403 | MovementParkour | [12, 14, -3290] → [13, 14, -3290] |
| 10.456 | 0.347 | MovementParkour | [13, 14, -3290] → [14, 14, -3290] |
| 10.803 | 0.503 | MovementDescend | [14, 14, -3290] → [15, 13, -3290] |
| 11.306 | 1.834 | MovementParkour | [15, 13, -3290] → [19, 13, -3291] |
| 13.140 | 1.599 | MovementParkour | [19, 13, -3291] → [20, 14, -3291] |
| 14.739 | 0.802 | MovementParkour | [20, 14, -3291] → [21, 14, -3291] |
| 15.541 | 0.201 | MovementParkour | [21, 14, -3291] → [22, 14, -3291] |
| 15.742 | 0.201 | MovementParkour | [22, 14, -3291] → [23, 14, -3291] |
| 15.943 | 0.497 | MovementParkour | [23, 14, -3291] → [23, 15, -3292] |
| 16.440 | 1.549 | MovementParkour | [23, 15, -3292] → [27, 16, -3290] |
| 17.989 | 1.802 | MovementParkour | [27, 16, -3290] → [29, 17, -3290] |
| 19.791 | 0.439 | MovementAscend | [29, 17, -3290] → [30, 18, -3290] |
| 20.230 | 0.621 | 无控制器 | — |
| 20.851 | 0.403 | MovementParkour | [30, 18, -3290] → [31, 18, -3290] |
| 21.254 | 0.297 | MovementParkour | [31, 18, -3290] → [32, 18, -3290] |
| 21.551 | 0.651 | 无控制器 | — |
| 22.202 | 2.053 | MovementParkour | [32, 18, -3290] → [36, 20, -3290] |
| 24.255 | 0.349 | MovementParkour | [36, 20, -3290] → [37, 20, -3291] |
| 24.604 | 0.249 | MovementParkour | [37, 20, -3291] → [38, 20, -3291] |
| 24.853 | 0.099 | MovementParkour | [38, 20, -3291] → [39, 20, -3291] |
| 24.952 | 0.249 | MovementDiagonal | [39, 20, -3291] → [40, 20, -3290] |
| 25.201 | 0.251 | MovementDiagonal | [40, 20, -3290] → [41, 20, -3289] |
| 25.452 | 0.402 | MovementParkour | [41, 20, -3289] → [42, 20, -3289] |
| 25.854 | 0.255 | MovementParkour | [42, 20, -3289] → [43, 20, -3289] |
| 26.109 | 1.193 | MovementParkour | [43, 20, -3289] → [46, 17, -3290] |
| 27.302 | 1.608 | MovementParkour | [46, 17, -3290] → [49, 16, -3290] |
| 28.910 | 1.345 | MovementParkour | [49, 16, -3290] → [52, 16, -3292] |
| 30.255 | 1.349 | MovementParkour | [52, 16, -3292] → [55, 16, -3290] |
| 31.604 | 0.454 | MovementParkour | [55, 16, -3290] → [57, 17, -3290] |
| 32.058 | 0.446 | MovementParkour | [57, 17, -3290] → [60, 18, -3291] |
| 32.504 | 0.100 | MovementParkour | [60, 18, -3291] → [61, 18, -3291] |
| 32.604 | 1.848 | MovementParkour | [61, 18, -3291] → [64, 19, -3290] |
| 34.452 | 1.299 | MovementPistonLaunch | [64, 19, -3290] → [69, 23, -3290] |
| 35.751 | 2.155 | MovementPistonLaunch | [69, 23, -3290] → [74, 25, -3290] |
| 37.906 | 0.296 | MovementParkour | [74, 25, -3290] → [74, 25, -3289] |
| 38.202 | 0.150 | MovementParkour | [74, 25, -3289] → [75, 25, -3289] |
| 38.352 | 0.514 | MovementDescend | [75, 25, -3289] → [76, 24, -3289] |
| 38.866 | 2.785 | MovementParkour | [76, 24, -3289] → [79, 24, -3289] |
| 41.651 | 0.850 | MovementParkour | [79, 24, -3289] → [79, 25, -3289] |
| 42.501 | 1.902 | MovementParkour | [79, 25, -3289] → [80, 26, -3289] |
| 44.403 | 5.548 | MovementParkour | [80, 26, -3289] → [81, 27, -3289] |
| 49.951 | 6.251 | 无控制器 | — |
| 56.202 | 0.189 | MovementParkour | [81, 27, -3289] → [85, 28, -3289] |
| 56.391 | 6.056 | 无控制器 | — |
| 62.447 | 0.656 | MovementParkour | [81, 27, -3289] → [84, 28, -3289] |
| 63.103 | 0.201 | MovementParkour | [84, 28, -3289] → [85, 28, -3289] |
| 63.304 | 2.846 | MovementSlimeBounce | [85, 28, -3289] → [96, 24, -3291] |
| 66.150 | 0.799 | MovementParkour | [96, 24, -3291] → [97, 24, -3291] |
| 66.949 | 0.201 | MovementParkour | [97, 24, -3291] → [98, 24, -3291] |
| 67.150 | 0.699 | MovementParkour | [98, 24, -3291] → [102, 25, -3290] |
| 67.849 | 0.448 | MovementParkour | [102, 25, -3290] → [103, 26, -3290] |
| 68.297 | 0.956 | 无控制器 | — |
| 69.253 | 0.599 | MovementParkour | [103, 26, -3290] → [103, 26, -3289] |
| 69.852 | 1.345 | MovementParkour | [103, 26, -3289] → [104, 31, -3289] |
| 71.197 | 1.550 | MovementParkour | [104, 31, -3289] → [105, 36, -3289] |
| 72.747 | 1.403 | MovementParkour | [105, 36, -3289] → [105, 41, -3289] |
| 74.150 | 1.248 | MovementParkour | [105, 41, -3289] → [110, 46, -3288] |
| 75.398 | 0.852 | MovementParkour | [110, 46, -3288] → [111, 46, -3288] |
| 76.250 | 0.151 | MovementParkour | [111, 46, -3288] → [112, 46, -3288] |
| 76.401 | 1.250 | MovementParkour | [112, 46, -3288] → [117, 45, -3291] |
| 77.651 | 0.147 | MovementParkour | [117, 45, -3291] → [118, 45, -3291] |
| 77.798 | 0.251 | MovementParkour | [118, 45, -3291] → [119, 45, -3291] |
| 78.049 | 5.448 | MovementSlimeBounce | [119, 45, -3291] → [146, 23, -3290] |
| 83.497 | 0.602 | MovementParkour | [146, 23, -3290] → [146, 23, -3289] |
| 84.099 | 0.450 | MovementParkour | [146, 23, -3289] → [147, 24, -3289] |
| 84.549 | 0.448 | MovementParkour | [147, 24, -3289] → [148, 25, -3289] |
| 84.997 | 2.602 | MovementSlimeBounce | [148, 25, -3289] → [160, 18, -3292] |
| 87.599 | 0.756 | MovementParkour | [160, 18, -3292] → [160, 18, -3293] |
| 88.355 | 0.793 | MovementParkour | [160, 18, -3293] → [161, 18, -3293] |
| 89.148 | 3.952 | MovementParkour | [161, 18, -3293] → [162, 18, -3293] |
| 93.100 | 0.800 | MovementParkour | [162, 18, -3293] → [163, 18, -3293] |
| 93.900 | 0.396 | MovementParkour | [163, 18, -3293] → [164, 18, -3293] |
| 94.296 | 0.501 | MovementDiagonal | [164, 18, -3293] → [165, 18, -3292] |
| 94.797 | 0.799 | MovementDiagonal | [165, 18, -3292] → [166, 18, -3291] |
| 95.596 | 0.401 | MovementDiagonal | [166, 18, -3291] → [167, 18, -3290] |
| 95.997 | 0.049 | 无控制器 | — |

### 失败及恢复事件

- 20.229s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=30,y=18,z=-3290} pos=(29.720,18.126,-3289.515) motion=(0.018,0.216,0.000)`
- 49.949s：`path_executor position=11 feet=BetterBlockPos{x=80,y=28,z=-3289} reason=This movement has taken too long (111 ticks, expected 10.632846884410469, movement MovementParkour). Cancelling.`
- 56.199s：`flow_blocked index=0 goal=(85.5, 28.0, -3288.5) beam=1`
- 56.349s：`trajectory_replan error=0.3586725160948703`
- 56.388s：`fail reason=COLLISION_REJECTED feet=BetterBlockPos{x=81,y=27,z=-3289} pos=(81.056,27.420,-3288.329) motion=(0.082,0.333,-0.007)`
- 69.539s：`flow_blocked index=1 goal=(104.5, 31.0, -3288.5) beam=8`
- 69.623s：`flow_blocked index=0 goal=(104.5, 31.0, -3288.5) beam=1`
- 69.869s：`flow_blocked index=0 goal=(105.5, 36.0, -3288.5) beam=1`
- 71.320s：`flow_blocked index=0 goal=(105.5, 36.0, -3288.5) beam=1`
- 71.408s：`flow_blocked index=0 goal=(105.5, 41.0, -3288.5) beam=1`
- 72.856s：`flow_blocked index=0 goal=(105.5, 41.0, -3288.5) beam=1`
- 72.918s：`flow_blocked index=0 goal=(110.5, 46.0, -3287.5) beam=1`
- 89.495s：`trajectory_replan error=0.42885777758735344`
- 89.795s：`trajectory_replan error=0.4347764000178813`
- 90.095s：`trajectory_replan error=0.4347764337151432`
- 90.395s：`trajectory_replan error=0.43477646741237663`
- 90.695s：`trajectory_replan error=0.43477650111006483`
- 90.995s：`trajectory_replan error=0.4347765348072983`
- 91.295s：`trajectory_replan error=0.4347765685045033`
- 91.595s：`trajectory_replan error=0.4347766358993965`
- 91.895s：`trajectory_replan error=0.43477666959662997`
- 93.345s：`trajectory_replan error=0.584413215599767`
