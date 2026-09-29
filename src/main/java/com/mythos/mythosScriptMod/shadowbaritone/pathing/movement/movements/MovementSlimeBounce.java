package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.movements;

import com.google.common.collect.ImmutableSet;
import com.mythos.mythosScriptMod.shadowbaritone.api.IBaritone;
import com.mythos.mythosScriptMod.shadowbaritone.api.pathing.movement.MovementStatus;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.input.Input;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.*;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourSurface;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourTrajectory;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourDebugLog;
import com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour.ParkourFailureCache;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.Rotation;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import java.util.Set;

/** A fall and its slime rebound are one edge: contact must not release control. */
public final class MovementSlimeBounce extends Movement {
    private static final ParkourFailureCache REJECTED_BOUNCES = new ParkourFailureCache();
    private final BetterBlockPos slime;
    private BetterBlockPos secondSlime;
    private boolean secondBounced;
    private double previousVerticalSpeed;
    private boolean falling, bounced;
    private boolean chain, launched;
    private java.util.concurrent.CompletableFuture<java.util.List<ParkourTrajectory.Frame>> pending;
    private java.util.List<ParkourTrajectory.Frame> plan;
    private int planIndex;
    private ParkourTrajectory.Frame predicted;
    private boolean planFailed;
    public MovementSlimeBounce(IBaritone baritone, BetterBlockPos src, BetterBlockPos dest, BetterBlockPos slime) {
        super(baritone, src, dest, new BetterBlockPos[0]);
        this.slime = slime;
    }

    public static MovementSlimeBounce find(CalculationContext c, BetterBlockPos src, int dx, int dz) {
        if (!c.parkourMode || !c.allowParkour || !c.canSprint
                || ParkourSurface.support(c,src)==null) return null;
        // Only open platform edges can launch a long fall.
        if (ParkourSurface.support(c,new BetterBlockPos(src.x+dx,src.y,src.z+dz))!=null
                || !MovementHelper.canWalkThrough(c,src.x+dx,src.y,src.z+dz)
                || !MovementHelper.canWalkThrough(c,src.x+dx,src.y+1,src.z+dz)) {
            return null;
        }
        MovementSlimeBounce continuous = findChain(c, src, dx, dz);
        if (continuous != null && continuous.calculateCost(c) < COST_INF) return continuous;
        // distance 0 is the slime sitting in the column just past the lip.
        for (int distance=0; distance<=12; distance++) {
          for (int lateral=-12;lateral<=12;lateral++) {
            if (distance==0 && lateral!=0) continue;
            int x=src.x+dx*distance-dz*lateral, z=src.z+dz*distance+dx*lateral;
            if (!c.isLoaded(x,z)) continue;
            // A short drop still rebounds; the fall time below is the reach limit.
            for (int drop=1; drop<=48 && src.y-drop>0; drop++) {
                int y=src.y-drop;
                if (c.getBlock(x,y-1,z)!=Blocks.SLIME_BLOCK) continue;
                double height=0, velocity=.42;
                int fallTicks=0;
                while(height>-drop && fallTicks<80) { height+=velocity; velocity=(velocity-.08)*.98; fallTicks++; }
                if(Math.hypot(x-src.x,z-src.z)>.28*fallTicks+1.8) continue;
                // The pillar can sit beside the slime. Only the cells a body
                // occupies above the pad have to be open.
                if (!MovementHelper.canWalkThrough(c,x,y,z)
                        || !MovementHelper.canWalkThrough(c,x,y+1,z)) {
                    continue;
                }
                // Full rebound: the landing is the band the reversed impact can reach.
                double rebound=0, reboundVelocity=-velocity;
                int reboundTicks=0;
                while(reboundVelocity>0 && reboundTicks<80) {
                    rebound+=reboundVelocity; reboundVelocity=(reboundVelocity-.08)*.98; reboundTicks++;
                }
                int riseCap=Math.min(48,(int)Math.floor(rebound));
                // Highest landing wins across the whole pad: a low neighbour at
                // short reach must not hide a platform the rebound can still reach.
                for (int rise=riseCap;rise>=-1;rise--) {
                  int ty=y+rise;
                  for (int reach=1;reach<=12;reach++) {
                    for (int sideIndex=0;sideIndex<=14;sideIndex++) {
                      int sideways=sideIndex==0?0:(sideIndex+1)/2*(sideIndex%2==0?1:-1);
                      int tx=x+dx*reach-dz*sideways,tz=z+dz*reach+dx*sideways;
                      if (!c.isLoaded(tx,tz)) continue;
                      if (c.getBlock(tx,ty-1,tz)==Blocks.SLIME_BLOCK) {
                          // Prefer a landing along the outgoing axis before
                          // spending rebound range on a lateral detour.
                          for(int r=3;r<=12;r++) for(int side=0;side<=8;side++) for(int up=1;up<=6;up++) {
                              int s=ty>y ? side-4 : (side+1)/2*(side%2==0?1:-1);
                              BetterBlockPos end=new BetterBlockPos(tx+dx*r-dz*s,ty+up,tz+dz*r+dx*s);
                              if (c.isLoaded(end.x,end.z) && ParkourSurface.support(c,end)!=null
                                      && !REJECTED_BOUNCES.contains(c.failureScope,src.toLong(),end.toLong())
                                      && c.getBlock(end.x,end.y-1,end.z)!=Blocks.SLIME_BLOCK
                                      && MovementHelper.canWalkThrough(c,end.x,end.y,end.z)
                                      && MovementHelper.canWalkThrough(c,end.x,end.y+1,end.z)) {
                                  MovementSlimeBounce result=new MovementSlimeBounce(c.getBaritone(),src,end,new BetterBlockPos(x,y-1,z));
                                  result.secondSlime=new BetterBlockPos(tx,ty-1,tz);
                                  return result;
                              }
                          }
                          continue;
                      }
                      if (REJECTED_BOUNCES.contains(c.failureScope,src.toLong(),new BetterBlockPos(tx,ty,tz).toLong())) continue;
                      if (ParkourSurface.support(c,new BetterBlockPos(tx,ty,tz))!=null
                              && MovementHelper.canWalkOn(c,tx,ty-1,tz)
                              && MovementHelper.canWalkThrough(c,tx,ty,tz)
                              && MovementHelper.canWalkThrough(c,tx,ty+1,tz)) {
                          return new MovementSlimeBounce(c.getBaritone(),src,
                                  new BetterBlockPos(tx,ty,tz),new BetterBlockPos(x,y-1,z));
                      }
                    }
                  }
                }
            }
          }
        }
        return null;
    }
    private static MovementSlimeBounce findChain(CalculationContext c, BetterBlockPos src, int dx, int dz) {
        BetterBlockPos first = null;
        int last = 0, contacts = 0;
        for (int distance = 1; distance <= 24; distance++) {
            int x = src.x + dx * distance, z = src.z + dz * distance;
            if (!c.isLoaded(x, z) || distance - last > 5) return null;
            for (int y = src.y; y <= src.y + 3; y++) {
                if (!MovementHelper.canWalkThrough(c, x, y, z)) return null;
            }
            if (c.getBlock(x, src.y - 1, z) == Blocks.SLIME_BLOCK) {
                if (first == null) first = new BetterBlockPos(x, src.y - 1, z);
                contacts++;
                last = distance;
            } else if (ParkourSurface.support(c, new BetterBlockPos(x, src.y, z)) != null) {
                if (contacts < 2) return null;
                MovementSlimeBounce movement = new MovementSlimeBounce(c.getBaritone(), src,
                        new BetterBlockPos(x, src.y, z), first);
                movement.chain = true;
                return movement;
            }
        }
        return null;
    }
    @Override public double calculateCost(CalculationContext c) {
        if (REJECTED_BOUNCES.contains(c.failureScope,src.toLong(),dest.toLong())) return COST_INF;
        return slime != null && c.getBlock(slime.x,slime.y,slime.z)==Blocks.SLIME_BLOCK
                ? (chain ? Math.hypot(dest.x-src.x,dest.z-src.z)/.25 : 150) : COST_INF;
    }
    @Override protected Set<BetterBlockPos> calculateValidPositions() {
        ImmutableSet.Builder<BetterBlockPos> positions=ImmutableSet.builder();
        BetterBlockPos other=secondSlime==null?slime:secondSlime;
        for(int x=Math.min(other.x,Math.min(slime.x,Math.min(src.x,dest.x)))-1;x<=Math.max(other.x,Math.max(slime.x,Math.max(src.x,dest.x)))+1;x++)
            for(int z=Math.min(other.z,Math.min(slime.z,Math.min(src.z,dest.z)))-1;z<=Math.max(other.z,Math.max(slime.z,Math.max(src.z,dest.z)))+1;z++)
                for(int y=(secondSlime==null?slime.y:Math.min(slime.y,secondSlime.y))+1;y<=src.y+(chain?4:2);y++) positions.add(new BetterBlockPos(x,y,z));
        return positions.build();
    }
    @Override public void reset() {
        super.reset(); falling=false; bounced=false; launched=false; secondBounced=false; previousVerticalSpeed=0;
        if(pending!=null) pending.cancel(true);
        pending=null;plan=null;predicted=null;planIndex=0;planFailed=false;
    }
    @Override public MovementState updateState(MovementState state) {
        MovementState result=updateBounce(state);
        if(result.getStatus()==MovementStatus.UNREACHABLE || result.getStatus()==MovementStatus.FAILED) {
            REJECTED_BOUNCES.reject(ctx.world(),src.toLong(),dest.toLong());
            ParkourDebugLog.INSTANCE.event("slime_rejected_edge source="+src+" destination="+dest);
        }
        return result;
    }
    public static void rejectUnsearchable(CalculationContext context, BetterBlockPos from, BetterBlockPos to) {
        REJECTED_BOUNCES.reject(context.failureScope,from.toLong(),to.toLong());
    }
    private MovementState updateBounce(MovementState state) {
        super.updateState(state);
        if(state.getStatus()!=MovementStatus.RUNNING) return state;
        if (chain) {
            MovementState planned=planFailed?null:updateAscendingChain(state);
            return planned==null?updateChain(state):planned;
        }
        if((secondSlime==null || secondSlime.y>slime.y
                || Math.hypot(dest.x-secondSlime.x,dest.z-secondSlime.z)>8) && !planFailed) {
            MovementState planned=updateAscendingChain(state);
            if(planned!=null) return planned;
        }
        state.setInput(Input.SNEAK,false);
        // The lateral offset is not the platform lip: using max(x,z) made
        // diagonal jumps launch while still behind the forward edge.
        double departure = Math.abs(slime.x-src.x)>=Math.abs(slime.z-src.z)
                ? Math.signum(slime.x-src.x)*(ctx.player().posX-src.x-.5)
                : Math.signum(slime.z-src.z)*(ctx.player().posZ-src.z-.5);
        state.setInput(Input.JUMP,!falling && ctx.player().onGround
                && ctx.player().posY>=src.y-.03 && departure>=.80);
        state.setInput(Input.SPRINT,true);
        if(ctx.player().motionY<-.2 && ctx.player().posY<src.y-1) falling=true;
        if(falling && ctx.player().motionY>.2) bounced=true;
        // A higher second pad must not be mistaken for the first upward arc.
        // Confirm a new downward-to-upward collision at that pad instead.
        if(secondSlime!=null && bounced && previousVerticalSpeed<=0 && ctx.player().motionY>.2
                && Math.abs(ctx.player().posX-secondSlime.x-.5)<1
                && Math.abs(ctx.player().posZ-secondSlime.z-.5)<1
                && ctx.player().posY>=secondSlime.y+1-.03
                && ctx.player().posY<secondSlime.y+2.5) secondBounced=true;
        previousVerticalSpeed=ctx.player().motionY;
        BetterBlockPos pad=secondSlime!=null && bounced?secondSlime:slime;
        boolean finalRebound=bounced && (secondSlime==null || secondBounced);
        if(ctx.player().posY<Math.min(slime.y,secondSlime==null?slime.y:secondSlime.y)+.5)
            return state.setStatus(MovementStatus.UNREACHABLE);
        if(finalRebound && ctx.player().onGround && Math.abs(ctx.player().posY-dest.y)<.03
                && ctx.playerFeet().equals(dest)) return state.setStatus(MovementStatus.SUCCESS);
        boolean contactImminent = falling && ctx.player().motionY < 0
                && ctx.player().posY < pad.y+4
                && Math.abs(ctx.player().posX+ctx.player().motionX*2-pad.x-.5)<.45
                && Math.abs(ctx.player().posZ+ctx.player().motionZ*2-pad.z-.5)<.45;
        BetterBlockPos next=secondSlime!=null && !bounced?secondSlime:dest;
        BetterBlockPos target=finalRebound?dest:contactImminent?next:pad;
        double tx=target.x+.5, tz=target.z+.5;
        double dx=tx-ctx.player().posX, dz=tz-ctx.player().posZ;
        double lead=ctx.player().onGround?1.5:finalRebound?5:2;
        MovementHelper.moveForwardWithRotation(ctx,state,new Vec3d(
                dx-ctx.player().motionX*lead,0,dz-ctx.player().motionZ*lead));
        state.setInput(Input.MOVE_FORWARD,Math.hypot(dx,dz)>.04
                || Math.hypot(ctx.player().motionX,ctx.player().motionZ)>.02);
        return state;
    }
    private MovementState updateAscendingChain(MovementState state) {
        if(plan==null) {
            state.setInput(Input.MOVE_FORWARD,false).setInput(Input.JUMP,false)
                    .setInput(Input.SNEAK,false).setInput(Input.SPRINT,false);
            if(pending==null) {
                if(!ctx.player().onGround) {planFailed=true;return null;}
                if(Math.hypot(ctx.player().motionX,ctx.player().motionZ)>.002) return state;
                AxisAlignedBB region=planningRegion();
                java.util.List<AxisAlignedBB> boxes=new java.util.ArrayList<>(ctx.world().getCollisionBoxes(ctx.player(),region));
                ParkourTrajectory.Frame start=new ParkourTrajectory.Frame(ctx.player(),region);
                java.util.function.Predicate<AxisAlignedBB> safe=ParkourSurface.safetySnapshot(new CalculationContext(baritone),region);
                pending=java.util.concurrent.CompletableFuture.supplyAsync(() ->
                        planRoute(start,boxes,safe));
                return state;
            }
            if(!pending.isDone()) return state;
            try {plan=pending.join();} catch(java.util.concurrent.CompletionException error) {
                ParkourDebugLog.INSTANCE.event("slime_chain_search_failed "+error.getCause());
                return state.setStatus(MovementStatus.UNREACHABLE);
            }
            ParkourDebugLog.INSTANCE.event("slime_chain_plan frames="+plan.size());
            if(plan.isEmpty()) return state.setStatus(MovementStatus.UNREACHABLE);
        }
        if(predicted!=null && predicted.error(ctx.player())>.15) {
            ParkourDebugLog.INSTANCE.event("slime_chain_drift frame="+planIndex+" error="+predicted.error(ctx.player())
                    +" expected="+predicted.x+","+predicted.y+","+predicted.z
                    +" actual="+ctx.player().posX+","+ctx.player().posY+","+ctx.player().posZ
                    +" body="+ctx.player().getEntityBoundingBox()
                    +" motion="+ctx.player().motionX+","+ctx.player().motionY+","+ctx.player().motionZ);
            AxisAlignedBB region=planningRegion();
            java.util.List<AxisAlignedBB> boxes=new java.util.ArrayList<>(ctx.world().getCollisionBoxes(ctx.player(),region));
            java.util.List<ParkourTrajectory.Frame> corrected=ParkourTrajectory.replaySlimeRemainder(
                    new ParkourTrajectory.Frame(ctx.player(),region),plan,planIndex,boxes,dest,
                    ParkourSurface.safetySnapshot(new CalculationContext(baritone),region));
            // A correction may preserve the next collision and its bounce phase.
            // Resume only after replay proves a safe, stable final landing.
            if(corrected.isEmpty()) return state.setStatus(MovementStatus.UNREACHABLE);
            plan=corrected;
            planIndex=0;
            predicted=null;
            ParkourDebugLog.INSTANCE.event("slime_chain_revalidated frames="+plan.size());
        }
        if(planIndex>=plan.size()) {
            return state.setStatus(ctx.player().onGround && ctx.playerFeet().equals(dest)
                    ?MovementStatus.SUCCESS:MovementStatus.UNREACHABLE);
        }
        predicted=plan.get(planIndex++);
        ParkourTrajectory.Control input=predicted.control;
        if(input.resetHorizontal) {ctx.player().motionX=0;ctx.player().motionZ=0;}
        state.setTarget(new MovementState.MovementTarget(new Rotation(input.yaw,ctx.player().rotationPitch),true));
        state.setInput(Input.MOVE_FORWARD,input.forward).setInput(Input.SPRINT,input.sprint)
                .setInput(Input.JUMP,input.jump).setInput(Input.SNEAK,input.sneak)
                .setInput(Input.MOVE_LEFT,input.strafe>0).setInput(Input.MOVE_RIGHT,input.strafe<0);
        return state;
    }
    private AxisAlignedBB planningRegion() {
        return new AxisAlignedBB(src).union(new AxisAlignedBB(dest)).union(new AxisAlignedBB(slime))
                .union(new AxisAlignedBB(secondSlime==null?slime:secondSlime)).grow(4);
    }
    /** The same material-aware plan is replayed offline and applied in-game. */
    public java.util.List<ParkourTrajectory.Frame> planRoute(ParkourTrajectory.Frame start,
            java.util.List<AxisAlignedBB> boxes, java.util.function.Predicate<AxisAlignedBB> safe) {
        if(!chain) return ParkourTrajectory.searchSlimeChain(start,boxes,src,slime,secondSlime,dest,safe);
        return ParkourTrajectory.searchLevelSlimeChain(start,boxes,src,dest,safe);
    }
    private MovementState updateChain(MovementState state) {
        double length = Math.hypot(dest.x - src.x, dest.z - src.z);
        double progress = ((ctx.player().posX - src.x - .5) * (dest.x - src.x)
                + (ctx.player().posZ - src.z - .5) * (dest.z - src.z)) / length;
        if (!ctx.player().onGround && progress > .1) launched = true;
        if (ctx.player().posY < src.y - .5) return state.setStatus(MovementStatus.UNREACHABLE);
        if (launched && ctx.player().onGround && ctx.playerFeet().equals(dest)) {
            return state.setStatus(MovementStatus.SUCCESS);
        }
        double dx = dest.x + .5 - ctx.player().posX;
        double dz = dest.z + .5 - ctx.player().posZ;
        state.setInput(Input.SNEAK, false);
        state.setInput(Input.SPRINT, true);
        // Run to the platform lip before launching; jumping at its centre loses
        // the reach needed to catch the first slime block.
        // A collision still grants a grounded tick as the body crosses the lip.
        // Launch late enough that the body overlaps the next block BEFORE the
        // downward collision pass. On short rebounds, a fresh sprint jump also
        // preserves horizontal reach instead of waiting for a stationary landing.
        boolean launch = !launched && progress >= .84;
        boolean shortRebound = launched && ctx.player().motionY > 0
                && ctx.player().motionY < .42;
        state.setInput(Input.JUMP, ctx.player().onGround && (launch || shortRebound));
        MovementHelper.moveForwardWithRotation(ctx, state, new Vec3d(dx, 0, dz));
        state.setInput(Input.MOVE_FORWARD, true);
        return state;
    }
}
