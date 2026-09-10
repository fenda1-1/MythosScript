package com.zszl.zszlScriptMod.gui.modern.rules;

import java.nio.*;
import org.lwjgl.BufferUtils;
import org.junit.Test;
import static org.junit.Assert.*;

public class AutoFollowPickProjectionTest {
    private FloatBuffer matrix(float... values) {
        FloatBuffer result=BufferUtils.createFloatBuffer(16); result.put(values); ((Buffer) result).flip(); return result;
    }
    private double[] ray(FloatBuffer model, double x,double y,double z) {
        FloatBuffer projection=matrix(0.5F,0,0,0, 0,1,0,0, 0,0,-101F/99F,-1, 0,0,-200F/99F,0);
        IntBuffer viewport=BufferUtils.createIntBuffer(16); viewport.put(new int[]{300,200,800,400}); ((Buffer) viewport).flip();
        return AutoFollowPickProjection.ray(model,projection,viewport,
                BufferUtils.createFloatBuffer(3),BufferUtils.createFloatBuffer(3),x,y,z);
    }
    @Test public void viewportCentreUsesActualTranslatedCameraAndWorldOrigin() {
        double[] ray=ray(matrix(1,0,0,0, 0,1,0,0, 0,0,1,0, -3,-2,-1,1),1000,64,-500);
        assertArrayEquals(new double[]{1003,66,-500,1003,66,-756},ray,0.001);
    }
    @Test public void cameraRotationChangesRayEvenWithoutEntityYaw() {
        double[] ray=ray(matrix(-1,0,0,0, 0,1,0,0, 0,0,-1,0, 0,0,0,1),0,0,0);
        assertArrayEquals(new double[]{0,0,1,0,0,257},ray,0.001);
    }
}
