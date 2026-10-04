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

package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement;

import com.mythos.mythosScriptMod.config.BlinkPathingConfig;
import com.mythos.mythosScriptMod.shadowbaritone.Baritone;
import com.mythos.mythosScriptMod.shadowbaritone.api.IBaritone;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.goals.Goal;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.ParkourProfile;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.ActionCosts;
import com.mythos.mythosScriptMod.shadowbaritone.cache.WorldData;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.precompute.PrecomputedData;
import com.mythos.mythosScriptMod.shadowbaritone.utils.BlockStateInterface;
import com.mythos.mythosScriptMod.shadowbaritone.utils.ToolSet;
import com.mythos.mythosScriptMod.shadowbaritone.utils.pathing.BetterWorldBorder;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

import static com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.ActionCosts.COST_INF;

/**
 * @author Brady
 * @since 8/7/2018
 */
public class CalculationContext {

    private static final ItemStack STACK_BUCKET_WATER = new ItemStack(Items.WATER_BUCKET);

    public final boolean safeForThreadedUse;
    public final IBaritone baritone;
    public final Goal goal;
    public final World world;
    public final WorldData worldData;
    public final BlockStateInterface bsi;
    public final ToolSet toolSet;
    public final boolean hasWaterBucket;
    public final boolean hasThrowaway;
    public final boolean canSprint;
    public final boolean allowFireContact;
    public final double maxJumpHeight;
    protected final double placeBlockCost; // protected because you should call the function instead
    public final boolean allowBreak;
    public final List<Block> allowBreakAnyway;
    public final boolean parkourMode;
    public final ParkourProfile parkourProfile;
    public final boolean parkourDebugRender;
    public final boolean allowParkour;
    public final boolean allowParkourPlace;
    public final boolean allowJumpAt256;
    public final boolean allowParkourAscend;
    public final boolean assumeWalkOnWater;
    public boolean allowFallIntoLava;
    public final int frostWalker;
    public final boolean allowDiagonalDescend;
    public final boolean allowDiagonalAscend;
    public final boolean allowDownward;
    /** The flight height at which this path calculation began. */
    public final int preferredFlightY;
    public final int routeHeightRange;
    public int minFallHeight;
    public int maxFallHeightNoWater;
    public final int maxFallHeightBucket;
    public final double waterWalkSpeed;
    public final double breakBlockAdditionalCost;
    public double backtrackCostFavoringCoefficient;
    public double jumpPenalty;
    public final double walkOnWaterOnePenalty;
    public final BetterWorldBorder worldBorder;

    public final PrecomputedData precomputedData;
    public final net.minecraft.util.math.Vec3d playerPosition;
    public final Object failureScope;
    /** Search-local derived geometry. Execution creates a fresh context when validating a move. */
    public final ThreadLocal<com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourSurface.Cache> parkourSurfaces =
            ThreadLocal.withInitial(com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourSurface.Cache::new);
    private java.util.function.BiFunction<BlockPos,net.minecraft.util.math.AxisAlignedBB,
            List<net.minecraft.util.math.AxisAlignedBB>> capturedCollisions;

    /** Read-only parkour planning against an immutable captured scene. No live client is required. */
    public CalculationContext(IBaritone owner, Goal goal, BlockStateInterface blocks,
            java.util.function.BiFunction<BlockPos,net.minecraft.util.math.AxisAlignedBB,
                    List<net.minecraft.util.math.AxisAlignedBB>> collisions, double jumpHeight,
            net.minecraft.util.math.Vec3d playerPosition, boolean allowFireContact, Object failureScope) {
        this.failureScope=java.util.Objects.requireNonNull(failureScope);
        this.playerPosition=playerPosition;
        this.allowFireContact=allowFireContact;
        this.baritone=owner;
        this.goal=goal;
        this.bsi=blocks;
        this.capturedCollisions=java.util.Objects.requireNonNull(collisions);
        this.world=null;
        this.worldData=null;
        this.toolSet=null;
        this.safeForThreadedUse=true;
        this.precomputedData=new PrecomputedData();
        this.worldBorder=blocks.worldBorder;
        this.hasWaterBucket=false;
        this.hasThrowaway=false;
        this.canSprint=Baritone.settings().allowSprint.value;
        this.maxJumpHeight=jumpHeight;
        this.placeBlockCost=Baritone.settings().blockPlacementPenalty.value;
        this.allowBreak=false;
        this.allowBreakAnyway=java.util.Collections.emptyList();
        // Mirror the live constructor so a test can flip settings().parkourMode
        // and exercise the normal Baritone planner against a captured scene.
        this.parkourMode=Baritone.settings().parkourMode.value;
        this.parkourProfile=ParkourProfile.orDefault(Baritone.settings().parkourProfile.value);
        this.parkourDebugRender=false;
        this.allowParkour=Baritone.settings().allowParkour.value || this.parkourMode;
        this.allowParkourPlace=!this.parkourMode && Baritone.settings().allowParkourPlace.value;
        this.allowJumpAt256=Baritone.settings().allowJumpAt256.value;
        this.allowParkourAscend=Baritone.settings().allowParkourAscend.value;
        this.assumeWalkOnWater=Baritone.settings().assumeWalkOnWater.value;
        this.frostWalker=0;
        this.allowDiagonalDescend=Baritone.settings().allowDiagonalDescend.value || this.parkourMode;
        this.allowDiagonalAscend=Baritone.settings().allowDiagonalAscend.value || this.parkourMode;
        this.allowDownward=Baritone.settings().allowDownward.value;
        this.preferredFlightY=0;
        this.routeHeightRange=resolveRouteHeightRange();
        this.minFallHeight=3;
        this.maxFallHeightNoWater=resolveMaxFallHeightNoWater(this.routeHeightRange);
        this.maxFallHeightBucket=Baritone.settings().maxFallHeightBucket.value;
        this.waterWalkSpeed=ActionCosts.WALK_ONE_IN_WATER_COST;
        this.breakBlockAdditionalCost=Baritone.settings().blockBreakAdditionalPenalty.value;
        this.backtrackCostFavoringCoefficient=Baritone.settings().backtrackCostFavoringCoefficient.value;
        this.jumpPenalty=Baritone.settings().jumpPenalty.value;
        this.walkOnWaterOnePenalty=Baritone.settings().walkOnWaterOnePenalty.value;
    }

    public List<net.minecraft.util.math.AxisAlignedBB> collisionBoxes(BlockPos pos,
            net.minecraft.util.math.AxisAlignedBB area) {
        if(capturedCollisions!=null) return capturedCollisions.apply(pos,area);
        List<net.minecraft.util.math.AxisAlignedBB> boxes=new ArrayList<>();
        get(pos).addCollisionBoxToList(world,pos,area,boxes,null,false);
        return boxes;
    }

    public CalculationContext(IBaritone baritone) {
        this(baritone, false);
    }

    public static double jumpHeight(EntityPlayerSP player) {
        net.minecraft.potion.PotionEffect jump = player.getActivePotionEffect(net.minecraft.init.MobEffects.JUMP_BOOST);
        if (jump == null) return 1.25;
        double velocity = .41999998688697815 + .1 * (jump.getAmplifier() + 1), height = 0;
        for (int tick = 0; tick < 256 && velocity > 0; tick++) {
            height += velocity;
            velocity = (velocity - .08) * .98;
        }
        return height;
    }

    public CalculationContext(IBaritone baritone, boolean forUseOnAnotherThread) {
        this.precomputedData = new PrecomputedData();
        this.safeForThreadedUse = forUseOnAnotherThread;
        this.baritone = baritone;
        this.goal = baritone.getPathingBehavior() == null ? null : baritone.getPathingBehavior().getGoal();
        EntityPlayerSP player = baritone.getPlayerContext().player();
        this.allowFireContact=player.capabilities.isCreativeMode;
        this.playerPosition=new net.minecraft.util.math.Vec3d(player.posX,player.posY,player.posZ);
        this.world = baritone.getPlayerContext().world();
        this.failureScope=this.world;
        this.worldData = (WorldData) baritone.getPlayerContext().worldData();
        this.bsi = new BlockStateInterface(baritone.getPlayerContext(), forUseOnAnotherThread);
        this.toolSet = new ToolSet(player);
        this.hasThrowaway = Baritone.settings().allowPlace.value
                && ((Baritone) baritone).getInventoryBehavior().hasGenericThrowaway();
        this.hasWaterBucket = Baritone.settings().allowWaterBucketFall.value
                && InventoryPlayer.isHotbar(player.inventory.getSlotFor(STACK_BUCKET_WATER))
                && !world.provider.isNether();
        this.canSprint = Baritone.settings().allowSprint.value && player.getFoodStats().getFoodLevel() > 6;
        this.maxJumpHeight = jumpHeight(player);
        this.placeBlockCost = Baritone.settings().blockPlacementPenalty.value;
        this.allowBreak = Baritone.settings().allowBreak.value;
        this.allowBreakAnyway = new ArrayList<>(Baritone.settings().allowBreakAnyway.value);
        this.parkourMode = Baritone.settings().parkourMode.value;
        this.parkourProfile = ParkourProfile.orDefault(Baritone.settings().parkourProfile.value);
        this.parkourDebugRender = Baritone.settings().parkourDebugRender.value;
        this.allowParkour = Baritone.settings().allowParkour.value || this.parkourMode;
        this.allowParkourPlace = !this.parkourMode && Baritone.settings().allowParkourPlace.value;
        this.allowJumpAt256 = Baritone.settings().allowJumpAt256.value;
        this.allowParkourAscend = Baritone.settings().allowParkourAscend.value;
        this.assumeWalkOnWater = Baritone.settings().assumeWalkOnWater.value;
        this.allowFallIntoLava = false; // Super secret internal setting for ElytraBehavior
        this.frostWalker = EnchantmentHelper.getMaxEnchantmentLevel(Enchantments.FROST_WALKER,
                baritone.getPlayerContext().player());
        this.allowDiagonalDescend = Baritone.settings().allowDiagonalDescend.value || this.parkourMode;
        this.allowDiagonalAscend = Baritone.settings().allowDiagonalAscend.value || this.parkourMode;
        this.allowDownward = Baritone.settings().allowDownward.value;
        this.preferredFlightY = MathHelper.floor(player.posY + 0.1251D);
        this.routeHeightRange = resolveRouteHeightRange();
        this.minFallHeight = 3; // Minimum fall height used by MovementFall
        this.maxFallHeightNoWater = resolveMaxFallHeightNoWater(this.routeHeightRange);
        this.maxFallHeightBucket = Baritone.settings().maxFallHeightBucket.value;
        int depth = EnchantmentHelper.getDepthStriderModifier(player);
        if (depth > 3) {
            depth = 3;
        }
        float mult = depth / 3.0F;
        this.waterWalkSpeed = ActionCosts.WALK_ONE_IN_WATER_COST * (1 - mult) + ActionCosts.WALK_ONE_BLOCK_COST * mult;
        this.breakBlockAdditionalCost = Baritone.settings().blockBreakAdditionalPenalty.value;
        this.backtrackCostFavoringCoefficient = Baritone.settings().backtrackCostFavoringCoefficient.value;
        this.jumpPenalty = Baritone.settings().jumpPenalty.value;
        this.walkOnWaterOnePenalty = Baritone.settings().walkOnWaterOnePenalty.value;
        // why cache these things here, why not let the movements just get directly from
        // settings?
        // because if some movements are calculated one way and others are calculated
        // another way,
        // then you get a wildly inconsistent path that isn't optimal for either
        // scenario.
        this.worldBorder = new BetterWorldBorder(world.getWorldBorder());
    }

    /** Blink pathing uses its own route height range so teleport routes can climb
     *  further than the walking planner default. */
    private static int resolveRouteHeightRange() {
        int configured = Baritone.settings().allowBlinkPathing.value
                ? BlinkPathingConfig.routeHeightRange
                : Baritone.settings().routeHeightRange.value;
        return Math.max(1, Math.min(100, configured));
    }

    /** Blink teleports take no fall damage (fallDistance is reset every hop), so
     *  the walking fall limit would asymmetrically block descents that ascend
     *  allowed: climbing 5 works via routeHeightRange, dropping 5 found no path.
     *  While blink pathing, allow falls up to the same route height range. */
    private static int resolveMaxFallHeightNoWater(int routeHeightRange) {
        int configured = Baritone.settings().maxFallHeightNoWater.value;
        if (Baritone.settings().allowBlinkPathing.value) {
            configured = Math.max(configured, routeHeightRange);
        }
        return configured;
    }

    public final IBaritone getBaritone() {
        return baritone;
    }

    public boolean isGoal(int x, int y, int z) {
        return goal != null && goal.isInGoal(x, y, z);
    }

    public boolean isGoal(BlockPos pos) {
        return pos != null && isGoal(pos.getX(), pos.getY(), pos.getZ());
    }

    public IBlockState get(int x, int y, int z) {
        return bsi.get0(x, y, z); // laughs maniacally
    }

    public boolean isLoaded(int x, int z) {
        return bsi.isLoaded(x, z);
    }

    public IBlockState get(BlockPos pos) {
        return get(pos.getX(), pos.getY(), pos.getZ());
    }

    public Block getBlock(int x, int y, int z) {
        return get(x, y, z).getBlock();
    }

    public double costOfPlacingAt(int x, int y, int z, IBlockState current) {
        if (!hasThrowaway) { // only true if allowPlace is true, see constructor
            return COST_INF;
        }
        if (isPossiblyProtected(x, y, z)) {
            return COST_INF;
        }
        if (!worldBorder.canPlaceAt(x, z)) {
            return COST_INF;
        }
        return placeBlockCost;
    }

    public double breakCostMultiplierAt(int x, int y, int z, IBlockState current) {
        if (!allowBreak && !allowBreakAnyway.contains(current.getBlock())) {
            return COST_INF;
        }
        if (isPossiblyProtected(x, y, z)) {
            return COST_INF;
        }
        return 1;
    }

    public double placeBucketCost() {
        return placeBlockCost; // shrug
    }

    public boolean isPossiblyProtected(int x, int y, int z) {
        // TODO more protection logic here; see #220
        return false;
    }
}
