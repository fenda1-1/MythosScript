package com.mythos.mythosScriptMod.inventory;

import java.util.Arrays;
import net.minecraft.entity.Entity;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class InventoryViewerTargetTest {
    @BeforeClass
    public static void initializeMinecraft() { Bootstrap.register(); }

    private static class Target extends Entity {
        Target(double z) {
            super(null);
            setEntityBoundingBox(new AxisAlignedBB(-0.3, 0, z - 0.3, 0.3, 2, z + 0.3));
        }
        protected void entityInit() { }
        protected void readEntityFromNBT(NBTTagCompound tag) { }
        protected void writeEntityToNBT(NBTTagCompound tag) { }
        public boolean canBeCollidedWith() { return true; }
    }

    @Test
    public void excludesSelfAndCameraEvenWhenRayStartsInsideThem() {
        Entity self = new Target(0), camera = new Target(0), target = new Target(6);
        assertSame(target, InventoryViewerManager.findClosestEntity(Arrays.asList(self, camera, target),
                self, camera, new Vec3d(0, 1.6, 0), new Vec3d(0, 1.6, 20), 400));
    }

    @Test
    public void selectsNearestEntityRegardlessOfIterationOrder() {
        Entity near = new Target(6), far = new Target(15);
        assertSame(near, InventoryViewerManager.findClosestEntity(Arrays.asList(far, near),
                null, null, new Vec3d(0, 1.6, 0), new Vec3d(0, 1.6, 20), 400));
    }

    @Test
    public void blockClippedRayCannotSelectEntityBehindWall() {
        assertNull(InventoryViewerManager.findClosestEntity(Arrays.asList(new Target(6)),
                null, null, new Vec3d(0, 1.6, 0), new Vec3d(0, 1.6, 3), 9));
    }

    @Test
    public void missingTargetDoesNotFallBackToSelf() {
        Entity self = new Target(0);
        assertNull(InventoryViewerManager.findClosestEntity(Arrays.asList(self),
                self, self, new Vec3d(0, 1.6, 0), new Vec3d(0, 1.6, 20), 400));
    }
}
