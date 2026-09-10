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

package com.zszl.zszlScriptMod.shadowbaritone.process;

import com.zszl.zszlScriptMod.shadowbaritone.Baritone;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.Goal;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.GoalComposite;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.GoalNear;
import com.zszl.zszlScriptMod.shadowbaritone.api.pathing.goals.GoalXZ;
import com.zszl.zszlScriptMod.shadowbaritone.api.process.IFollowProcess;
import com.zszl.zszlScriptMod.shadowbaritone.api.process.PathingCommand;
import com.zszl.zszlScriptMod.shadowbaritone.api.process.PathingCommandType;
import com.zszl.zszlScriptMod.shadowbaritone.utils.BaritoneProcessHelper;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Follow an entity
 *
 * @author leijurv
 */
public final class FollowProcess extends BaritoneProcessHelper implements IFollowProcess {

    private Predicate<Entity> filter;
    private List<Entity> cache;

    public FollowProcess(Baritone baritone) {
        super(baritone);
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        scanWorld();
        Goal goal = new GoalComposite(cache.stream().map(this::towards).toArray(Goal[]::new));
        return new PathingCommand(goal, PathingCommandType.REVALIDATE_GOAL_AND_PATH);
    }

    private Goal towards(Entity following) {
        BlockPos pos;
        if (Baritone.settings().followOffsetDistance.value == 0) {
            pos = new BlockPos(following);
        } else {
            GoalXZ g = GoalXZ.fromDirection(following.getPositionVector(),
                    Baritone.settings().followOffsetDirection.value, Baritone.settings().followOffsetDistance.value);
            pos = new BlockPos(g.getX(), following.posY, g.getZ());
        }
        return new GoalNear(pos, Baritone.settings().followRadius.value);
    }

    private boolean followable(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (entity.isDead) {
            return false;
        }
        if (entity.equals(ctx.player())) {
            return false;
        }
        return ctx.world().loadedEntityList.contains(entity);
    }

    private void scanWorld() {
        cache = Stream.of(ctx.world().loadedEntityList, ctx.world().playerEntities)
                .flatMap(List::stream)
                .filter(this::followable)
                .filter(this.filter)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public boolean isActive() {
        if (filter == null) {
            return false;
        }
        scanWorld();
        return !cache.isEmpty();
    }

    @Override
    public void onLostControl() {
        filter = null;
        cache = null;
    }

    @Override
    public String displayName0() {
        return "Following " + cache;
    }

    @Override
    public void follow(Predicate<Entity> filter) {
        this.filter = filter;
    }

    @Override
    public List<Entity> following() {
        return cache;
    }

    @Override
    public Predicate<Entity> currentFilter() {
        return filter;
    }
}
