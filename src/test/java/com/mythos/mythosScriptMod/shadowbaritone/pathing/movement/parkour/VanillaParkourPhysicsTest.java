package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour;

import com.mojang.authlib.GameProfile;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.WorldInfo;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import static org.junit.Assert.*;

/** Uses Minecraft's actual jump/travel/move, rather than a second physics implementation. */
public class VanillaParkourPhysicsTest {
    private static final class RecordedWorld extends World {
        private final CapturedParkourWorld capture;
        RecordedWorld(CapturedParkourWorld capture) {
            super(null,new WorldInfo(new NBTTagCompound()),new WorldProviderSurface(),new Profiler(),false);
            this.capture=capture;
        }
        @Override protected IChunkProvider createChunkProvider() { return null; }
        @Override protected boolean isChunkLoaded(int x,int z,boolean allowEmpty) { return true; }
        @Override public BlockPos getSpawnPoint() { return BlockPos.ORIGIN; }
        @Override public IBlockState getBlockState(BlockPos pos) { return capture.getBlockState(pos); }
        @Override public List<AxisAlignedBB> getCollisionBoxes(Entity entity,AxisAlignedBB box) {
            List<AxisAlignedBB> result=new ArrayList<>();
            for(AxisAlignedBB obstacle:capture.boxes) if(obstacle.intersects(box)) result.add(obstacle);
            return result;
        }
        @Override public boolean isFlammableWithin(AxisAlignedBB box) { return false; }
        @Override public boolean handleMaterialAcceleration(AxisAlignedBB box,Material material,Entity entity) { return false; }
    }

    private static final class Player extends EntityPlayer {
        Player(World world) { super(world,new GameProfile(new UUID(0,1),"physics-oracle")); }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return false; }
        @Override protected boolean canTriggerWalking() { return false; }
        @Override protected void doBlockCollisions() { }
        @Override protected void updateFallState(double y,boolean ground,IBlockState state,BlockPos pos) { }
        void tick(ParkourTrajectory.Control c) {
            rotationYaw=c.yaw;
            setSneaking(c.sneak);
            setSprinting(c.sprint && c.forward && !c.sneak && !collidedHorizontally);
            if(c.resetHorizontal) motionX=motionZ=0;
            if(Math.abs(motionX)<.003) motionX=0;
            if(Math.abs(motionY)<.003) motionY=0;
            if(Math.abs(motionZ)<.003) motionZ=0;
            if(c.jump && onGround) jump();
            float forward=c.forward?1:0,strafe=c.strafe;
            if(c.sneak) { forward*=.3F;strafe*=.3F; }
            travel(strafe*.98F,0,forward*.98F);
            jumpMovementFactor=isSprinting()?.026F:.02F;
            updateSize();
        }
    }

    @Test public void capturedDryMovementMatchesMinecraft() throws Exception {
        CapturedParkourWorld world=new CapturedParkourWorld("level70/soul-runway.json");
        RecordedWorld vanilla=new RecordedWorld(world);
        Random random=new Random(7);
        double[][] starts={{120.447119,13,-3755.3},{125.5,13,-3755.5},{130.5,13,-3755.5}};
        int checked=0;
        for(double[] start:starts) for(int run=0;run<20;run++) {
            Player player=new Player(vanilla);
            player.setPosition(start[0],start[1],start[2]);
            player.onGround=true;
            player.motionY=-.0784000015258789;
            ParkourTrajectory.Frame frame=world.standing(start[0],start[1],start[2]);
            for(int tick=0;tick<24;tick++) {
                ParkourTrajectory.Control control=new ParkourTrajectory.Control(random.nextDouble()*Math.PI*2,
                        tick%5!=4,true,tick%7==2,tick<3,random.nextInt(3)-1);
                frame=ParkourTrajectory.step(frame,control,world.boxes);
                player.tick(control);
                String at="start="+java.util.Arrays.toString(start)+" run="+run+" tick="+tick;
                assertFrame(at,player,frame);
                checked++;
                if(frame.y<start[1]-2) break;
            }
        }
        System.out.println("VANILLA_PHYSICS checkedTicks="+checked);
    }

    private static void assertFrame(String at,Player player,ParkourTrajectory.Frame frame) {
        assertEquals(at+" x",player.posX,frame.x,1e-7);
        assertEquals(at+" y",player.posY,frame.y,1e-7);
        assertEquals(at+" z",player.posZ,frame.z,1e-7);
        assertEquals(at+" vx",player.motionX,frame.vx,1e-7);
        assertEquals(at+" vy",player.motionY,frame.vy,1e-7);
        assertEquals(at+" vz",player.motionZ,frame.vz,1e-7);
        assertEquals(at+" ground",player.onGround,frame.ground);
        assertEquals(at+" horizontal collision",player.collidedHorizontally,frame.collidedHorizontally);
        assertEquals(at+" body height",player.height,frame.bodyHeight,.000001);
    }
}
