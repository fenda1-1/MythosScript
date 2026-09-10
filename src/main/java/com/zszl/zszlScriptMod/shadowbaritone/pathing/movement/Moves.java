/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.zszl.zszlScriptMod.shadowbaritone.pathing.movement;

import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.zszl.zszlScriptMod.shadowbaritone.pathing.movement.movements.*;
import com.zszl.zszlScriptMod.shadowbaritone.utils.pathing.MutableMoveResult;
import net.minecraft.util.EnumFacing;

/**
 * An enum of all possible movements attached to all possible directions they
 * could be taken in
 *
 * @author leijurv
 */
public enum Moves {
    DOWNWARD(0, -1, 0) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            return new MovementDownward(context.getBaritone(), src, src.down());
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z) {
            return MovementDownward.cost(context, x, y, z);
        }
    },

    PILLAR(0, +1, 0) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            return new MovementPillar(context.getBaritone(), src, src.up());
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z) {
            return MovementPillar.cost(context, x, y, z);
        }
    },

    TRAVERSE_NORTH(0, 0, -1) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            return new MovementTraverse(context.getBaritone(), src, src.north());
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z) {
            return MovementTraverse.cost(context, x, y, z, x, z - 1);
        }
    },

    TRAVERSE_SOUTH(0, 0, +1) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            return new MovementTraverse(context.getBaritone(), src, src.south());
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z) {
            return MovementTraverse.cost(context, x, y, z, x, z + 1);
        }
    },

    TRAVERSE_EAST(+1, 0, 0) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            return new MovementTraverse(context.getBaritone(), src, src.east());
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z) {
            return MovementTraverse.cost(context, x, y, z, x + 1, z);
        }
    },

    TRAVERSE_WEST(-1, 0, 0) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            return new MovementTraverse(context.getBaritone(), src, src.west());
        }

        @Override
        public double cost(CalculationContext context, int x, int y, int z) {
            return MovementTraverse.cost(context, x, y, z, x - 1, z);
        }
    },

    ASCEND_NORTH(0, +1, -1, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            return ascendMovement(context, src, src.x, src.z - 1);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            applyAscend(context, x, y, z, x, z - 1, result);
        }
    },

    ASCEND_SOUTH(0, +1, +1, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            return ascendMovement(context, src, src.x, src.z + 1);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            applyAscend(context, x, y, z, x, z + 1, result);
        }
    },

    ASCEND_EAST(+1, +1, 0, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            return ascendMovement(context, src, src.x + 1, src.z);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            applyAscend(context, x, y, z, x + 1, z, result);
        }
    },

    ASCEND_WEST(-1, +1, 0, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            return ascendMovement(context, src, src.x - 1, src.z);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            applyAscend(context, x, y, z, x - 1, z, result);
        }
    },

    DESCEND_EAST(+1, -1, 0, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            MutableMoveResult res = new MutableMoveResult();
            apply(context, src.x, src.y, src.z, res);
            if (res.y == src.y - 1) {
                return new MovementDescend(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z));
            } else {
                return new MovementFall(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z));
            }
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            MovementDescend.cost(context, x, y, z, x + 1, z, result);
        }
    },

    DESCEND_WEST(-1, -1, 0, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            MutableMoveResult res = new MutableMoveResult();
            apply(context, src.x, src.y, src.z, res);
            if (res.y == src.y - 1) {
                return new MovementDescend(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z));
            } else {
                return new MovementFall(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z));
            }
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            MovementDescend.cost(context, x, y, z, x - 1, z, result);
        }
    },

    DESCEND_NORTH(0, -1, -1, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            MutableMoveResult res = new MutableMoveResult();
            apply(context, src.x, src.y, src.z, res);
            if (res.y == src.y - 1) {
                return new MovementDescend(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z));
            } else {
                return new MovementFall(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z));
            }
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            MovementDescend.cost(context, x, y, z, x, z - 1, result);
        }
    },

    DESCEND_SOUTH(0, -1, +1, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            MutableMoveResult res = new MutableMoveResult();
            apply(context, src.x, src.y, src.z, res);
            if (res.y == src.y - 1) {
                return new MovementDescend(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z));
            } else {
                return new MovementFall(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z));
            }
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            MovementDescend.cost(context, x, y, z, x, z + 1, result);
        }
    },

    DIAGONAL_NORTHEAST(+1, 0, -1, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            MutableMoveResult res = new MutableMoveResult();
            apply(context, src.x, src.y, src.z, res);
            return new MovementDiagonal(context.getBaritone(), src, EnumFacing.NORTH, EnumFacing.EAST, res.y - src.y);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            MovementDiagonal.cost(context, x, y, z, x + 1, z - 1, result);
        }
    },

    DIAGONAL_NORTHWEST(-1, 0, -1, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            MutableMoveResult res = new MutableMoveResult();
            apply(context, src.x, src.y, src.z, res);
            return new MovementDiagonal(context.getBaritone(), src, EnumFacing.NORTH, EnumFacing.WEST, res.y - src.y);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            MovementDiagonal.cost(context, x, y, z, x - 1, z - 1, result);
        }
    },

    DIAGONAL_SOUTHEAST(+1, 0, +1, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            MutableMoveResult res = new MutableMoveResult();
            apply(context, src.x, src.y, src.z, res);
            return new MovementDiagonal(context.getBaritone(), src, EnumFacing.SOUTH, EnumFacing.EAST, res.y - src.y);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            MovementDiagonal.cost(context, x, y, z, x + 1, z + 1, result);
        }
    },

    DIAGONAL_SOUTHWEST(-1, 0, +1, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            MutableMoveResult res = new MutableMoveResult();
            apply(context, src.x, src.y, src.z, res);
            return new MovementDiagonal(context.getBaritone(), src, EnumFacing.SOUTH, EnumFacing.WEST, res.y - src.y);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            MovementDiagonal.cost(context, x, y, z, x - 1, z + 1, result);
        }
    },

    NARROW_GAP_NORTH_EAST(+1, 0, -2, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            return narrowGapMovement(context, src, src.x + 1, src.z - 2);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            applyNarrowGap(context, x, y, z, x + 1, z - 2, result);
        }
    },

    NARROW_GAP_NORTH_WEST(-1, 0, -2, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            return narrowGapMovement(context, src, src.x - 1, src.z - 2);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            applyNarrowGap(context, x, y, z, x - 1, z - 2, result);
        }
    },

    NARROW_GAP_SOUTH_EAST(+1, 0, +2, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            return narrowGapMovement(context, src, src.x + 1, src.z + 2);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            applyNarrowGap(context, x, y, z, x + 1, z + 2, result);
        }
    },

    NARROW_GAP_SOUTH_WEST(-1, 0, +2, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            return narrowGapMovement(context, src, src.x - 1, src.z + 2);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            applyNarrowGap(context, x, y, z, x - 1, z + 2, result);
        }
    },

    NARROW_GAP_EAST_NORTH(+2, 0, -1, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            return narrowGapMovement(context, src, src.x + 2, src.z - 1);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            applyNarrowGap(context, x, y, z, x + 2, z - 1, result);
        }
    },

    NARROW_GAP_EAST_SOUTH(+2, 0, +1, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            return narrowGapMovement(context, src, src.x + 2, src.z + 1);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            applyNarrowGap(context, x, y, z, x + 2, z + 1, result);
        }
    },

    NARROW_GAP_WEST_NORTH(-2, 0, -1, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            return narrowGapMovement(context, src, src.x - 2, src.z - 1);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            applyNarrowGap(context, x, y, z, x - 2, z - 1, result);
        }
    },

    NARROW_GAP_WEST_SOUTH(-2, 0, +1, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            return narrowGapMovement(context, src, src.x - 2, src.z + 1);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            applyNarrowGap(context, x, y, z, x - 2, z + 1, result);
        }
    },

    NARROW_GAP_STRAIGHT_NORTH_EAST_SIDE(0, 0, -2, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            BetterBlockPos mid = src.north();
            return narrowGapMovement(context, src, src.x, src.z - 2, mid, mid.east());
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            BetterBlockPos mid = new BetterBlockPos(x, y, z - 1);
            applyNarrowGap(context, x, y, z, x, z - 2, mid, mid.east(), result);
        }
    },

    NARROW_GAP_STRAIGHT_NORTH_WEST_SIDE(0, 0, -2, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            BetterBlockPos mid = src.north();
            return narrowGapMovement(context, src, src.x, src.z - 2, mid, mid.west());
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            BetterBlockPos mid = new BetterBlockPos(x, y, z - 1);
            applyNarrowGap(context, x, y, z, x, z - 2, mid, mid.west(), result);
        }
    },

    NARROW_GAP_STRAIGHT_SOUTH_EAST_SIDE(0, 0, +2, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            BetterBlockPos mid = src.south();
            return narrowGapMovement(context, src, src.x, src.z + 2, mid, mid.east());
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            BetterBlockPos mid = new BetterBlockPos(x, y, z + 1);
            applyNarrowGap(context, x, y, z, x, z + 2, mid, mid.east(), result);
        }
    },

    NARROW_GAP_STRAIGHT_SOUTH_WEST_SIDE(0, 0, +2, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            BetterBlockPos mid = src.south();
            return narrowGapMovement(context, src, src.x, src.z + 2, mid, mid.west());
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            BetterBlockPos mid = new BetterBlockPos(x, y, z + 1);
            applyNarrowGap(context, x, y, z, x, z + 2, mid, mid.west(), result);
        }
    },

    NARROW_GAP_STRAIGHT_EAST_NORTH_SIDE(+2, 0, 0, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            BetterBlockPos mid = src.east();
            return narrowGapMovement(context, src, src.x + 2, src.z, mid, mid.north());
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            BetterBlockPos mid = new BetterBlockPos(x + 1, y, z);
            applyNarrowGap(context, x, y, z, x + 2, z, mid, mid.north(), result);
        }
    },

    NARROW_GAP_STRAIGHT_EAST_SOUTH_SIDE(+2, 0, 0, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            BetterBlockPos mid = src.east();
            return narrowGapMovement(context, src, src.x + 2, src.z, mid, mid.south());
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            BetterBlockPos mid = new BetterBlockPos(x + 1, y, z);
            applyNarrowGap(context, x, y, z, x + 2, z, mid, mid.south(), result);
        }
    },

    NARROW_GAP_STRAIGHT_WEST_NORTH_SIDE(-2, 0, 0, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            BetterBlockPos mid = src.west();
            return narrowGapMovement(context, src, src.x - 2, src.z, mid, mid.north());
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            BetterBlockPos mid = new BetterBlockPos(x - 1, y, z);
            applyNarrowGap(context, x, y, z, x - 2, z, mid, mid.north(), result);
        }
    },

    NARROW_GAP_STRAIGHT_WEST_SOUTH_SIDE(-2, 0, 0, false, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            BetterBlockPos mid = src.west();
            return narrowGapMovement(context, src, src.x - 2, src.z, mid, mid.south());
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            BetterBlockPos mid = new BetterBlockPos(x - 1, y, z);
            applyNarrowGap(context, x, y, z, x - 2, z, mid, mid.south(), result);
        }
    },

    PARKOUR_NORTH(0, 0, -4, true, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            return MovementParkour.cost(context, src, EnumFacing.NORTH);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            MovementParkour.cost(context, x, y, z, EnumFacing.NORTH, result);
        }
    },

    PARKOUR_SOUTH(0, 0, +4, true, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            return MovementParkour.cost(context, src, EnumFacing.SOUTH);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            MovementParkour.cost(context, x, y, z, EnumFacing.SOUTH, result);
        }
    },

    PARKOUR_EAST(+4, 0, 0, true, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            return MovementParkour.cost(context, src, EnumFacing.EAST);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            MovementParkour.cost(context, x, y, z, EnumFacing.EAST, result);
        }
    },

    PARKOUR_WEST(-4, 0, 0, true, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            return MovementParkour.cost(context, src, EnumFacing.WEST);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            MovementParkour.cost(context, x, y, z, EnumFacing.WEST, result);
        }
    },

    PARKOUR_NORTH_EAST(+1, 0, -3, true, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            return MovementParkour.cost(context, src, EnumFacing.NORTH, EnumFacing.EAST);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            MovementParkour.cost(context, x, y, z, EnumFacing.NORTH, EnumFacing.EAST, result);
        }
    },

    PARKOUR_NORTH_WEST(-1, 0, -3, true, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            return MovementParkour.cost(context, src, EnumFacing.NORTH, EnumFacing.WEST);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            MovementParkour.cost(context, x, y, z, EnumFacing.NORTH, EnumFacing.WEST, result);
        }
    },

    PARKOUR_SOUTH_EAST(+1, 0, +3, true, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            return MovementParkour.cost(context, src, EnumFacing.SOUTH, EnumFacing.EAST);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            MovementParkour.cost(context, x, y, z, EnumFacing.SOUTH, EnumFacing.EAST, result);
        }
    },

    PARKOUR_SOUTH_WEST(-1, 0, +3, true, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            return MovementParkour.cost(context, src, EnumFacing.SOUTH, EnumFacing.WEST);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            MovementParkour.cost(context, x, y, z, EnumFacing.SOUTH, EnumFacing.WEST, result);
        }
    },

    PARKOUR_EAST_NORTH(+3, 0, -1, true, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            return MovementParkour.cost(context, src, EnumFacing.EAST, EnumFacing.NORTH);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            MovementParkour.cost(context, x, y, z, EnumFacing.EAST, EnumFacing.NORTH, result);
        }
    },

    PARKOUR_EAST_SOUTH(+3, 0, +1, true, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            return MovementParkour.cost(context, src, EnumFacing.EAST, EnumFacing.SOUTH);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            MovementParkour.cost(context, x, y, z, EnumFacing.EAST, EnumFacing.SOUTH, result);
        }
    },

    PARKOUR_WEST_NORTH(-3, 0, -1, true, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            return MovementParkour.cost(context, src, EnumFacing.WEST, EnumFacing.NORTH);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            MovementParkour.cost(context, x, y, z, EnumFacing.WEST, EnumFacing.NORTH, result);
        }
    },

    PARKOUR_WEST_SOUTH(-3, 0, +1, true, true) {
        @Override
        public Movement apply0(CalculationContext context, BetterBlockPos src) {
            return MovementParkour.cost(context, src, EnumFacing.WEST, EnumFacing.SOUTH);
        }

        @Override
        public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
            MovementParkour.cost(context, x, y, z, EnumFacing.WEST, EnumFacing.SOUTH, result);
        }
    },
    FLIGHT_NORTH(0, 0, -1, true), FLIGHT_SOUTH(0, 0, 1, true),
    FLIGHT_EAST(1, 0, 0, true), FLIGHT_WEST(-1, 0, 0, true),
    FLIGHT_UP(0, 1, 0, true), FLIGHT_DOWN(0, -1, 0, true),
    FLIGHT_NORTHEAST(1, 0, -1, true), FLIGHT_NORTHWEST(-1, 0, -1, true),
    FLIGHT_SOUTHEAST(1, 0, 1, true), FLIGHT_SOUTHWEST(-1, 0, 1, true),
    FLIGHT_UP_NORTH(0, 1, -1, true), FLIGHT_UP_SOUTH(0, 1, 1, true),
    FLIGHT_UP_EAST(1, 1, 0, true), FLIGHT_UP_WEST(-1, 1, 0, true),
    FLIGHT_DOWN_NORTH(0, -1, -1, true), FLIGHT_DOWN_SOUTH(0, -1, 1, true),
    FLIGHT_DOWN_EAST(1, -1, 0, true), FLIGHT_DOWN_WEST(-1, -1, 0, true),
    FLIGHT_UP_NORTHEAST(1, 1, -1, true), FLIGHT_UP_NORTHWEST(-1, 1, -1, true),
    FLIGHT_UP_SOUTHEAST(1, 1, 1, true), FLIGHT_UP_SOUTHWEST(-1, 1, 1, true),
    FLIGHT_DOWN_NORTHEAST(1, -1, -1, true), FLIGHT_DOWN_NORTHWEST(-1, -1, -1, true),
    FLIGHT_DOWN_SOUTHEAST(1, -1, 1, true), FLIGHT_DOWN_SOUTHWEST(-1, -1, 1, true);

    public final boolean dynamicXZ;
    public final boolean dynamicY;

    public final int xOffset;
    public final int yOffset;
    public final int zOffset;
    public boolean flight;

    Moves(int x, int y, int z, boolean dynamicXZ, boolean dynamicY) {
        this.xOffset = x;
        this.yOffset = y;
        this.zOffset = z;
        this.dynamicXZ = dynamicXZ;
        this.dynamicY = dynamicY;
        this.flight = false;
    }

    Moves(int x, int y, int z, boolean flight) {
        this(x, y, z, false, false);
        this.flight = flight;
    }

    Moves(int x, int y, int z) {
        this(x, y, z, false, false);
    }

    private static Movement narrowGapMovement(CalculationContext context, BetterBlockPos src, int destX, int destZ) {
        MutableMoveResult res = new MutableMoveResult();
        MovementNarrowGapTraverse.cost(context, src.x, src.y, src.z, destX, destZ, res);
        return new MovementNarrowGapTraverse(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z));
    }

    private static Movement ascendMovement(CalculationContext context, BetterBlockPos src, int destX, int destZ) {
        MutableMoveResult result = new MutableMoveResult();
        applyAscend(context, src.x, src.y, src.z, destX, destZ, result);
        return new MovementAscend(context.getBaritone(), src, new BetterBlockPos(result.x, result.y, result.z));
    }

    private static void applyAscend(CalculationContext context, int x, int y, int z, int destX, int destZ,
            MutableMoveResult result) {
        result.x = destX;
        result.y = y + 1;
        result.z = destZ;
        for (int height = 1; height <= context.routeHeightRange && y + height <= 256; height++) {
            double cost = MovementAscend.cost(context, x, y, z, destX, y + height, destZ);
            if (cost < Movement.COST_INF) {
                result.y = y + height;
                result.cost = cost;
                return;
            }
        }
    }

    private static Movement narrowGapMovement(CalculationContext context, BetterBlockPos src, int destX, int destZ,
            BetterBlockPos barrierA, BetterBlockPos barrierB) {
        MutableMoveResult res = new MutableMoveResult();
        MovementNarrowGapTraverse.cost(context, src.x, src.y, src.z, destX, destZ, barrierA, barrierB, res);
        return new MovementNarrowGapTraverse(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z),
                barrierA, barrierB);
    }

    private static void applyNarrowGap(CalculationContext context, int x, int y, int z, int destX, int destZ,
            MutableMoveResult result) {
        MovementNarrowGapTraverse.cost(context, x, y, z, destX, destZ, result);
    }

    private static void applyNarrowGap(CalculationContext context, int x, int y, int z, int destX, int destZ,
            BetterBlockPos barrierA, BetterBlockPos barrierB, MutableMoveResult result) {
        MovementNarrowGapTraverse.cost(context, x, y, z, destX, destZ, barrierA, barrierB, result);
    }

    public Movement apply0(CalculationContext context, BetterBlockPos src) {
        if (flight) {
            return new MovementFly(context.getBaritone(), src,
                    new BetterBlockPos(src.x + xOffset, src.y + yOffset, src.z + zOffset));
        }
        throw new UnsupportedOperationException("Movement implementation missing for " + this);
    }

    public void apply(CalculationContext context, int x, int y, int z, MutableMoveResult result) {
        if (dynamicXZ || dynamicY) {
            throw new UnsupportedOperationException();
        }
        result.x = x + xOffset;
        result.y = y + yOffset;
        result.z = z + zOffset;
        result.cost = cost(context, x, y, z);
    }

    public double cost(CalculationContext context, int x, int y, int z) {
        if (flight) {
            return MovementFly.cost(context, x, y, z, x + xOffset, y + yOffset, z + zOffset);
        }
        throw new UnsupportedOperationException();
    }
}
