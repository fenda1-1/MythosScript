package com.zszl.zszlScriptMod.gui;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.junit.Test;
import static org.junit.Assert.*;

public class GuiTextPixelGridTest {
    private FloatBuffer identity() {
        FloatBuffer matrix = FloatBuffer.allocate(16);
        for (int i = 0; i < 4; i++) matrix.put(i * 5, 1);
        return matrix;
    }

    private GuiTextPixelGrid grid(float scale, float tx, float ty, int width, int height) {
        FloatBuffer model = identity();
        model.put(0, scale);
        model.put(5, scale);
        model.put(12, tx);
        model.put(13, ty);
        FloatBuffer projection = identity();
        // Minecraft uses the unrounded scaled resolution for its projection.
        projection.put(0, 2f / 622.5f);
        projection.put(5, -2f / 350.5f);
        projection.put(12, -1);
        projection.put(13, 1);
        return GuiTextPixelGrid.fromMatrices(model, projection,
                IntBuffer.wrap(new int[] {7, 11, width, height}));
    }

    @Test
    public void usesActualProjectionAtOddWindowSizes() {
        GuiTextPixelGrid grid = grid(1, 0, 0, 1245, 701);
        assertEquals(2, grid.scaleX, 1e-6);
        assertEquals(-2, grid.scaleY, 1e-6);
        assertEquals(7, grid.originX, 1e-6);
        assertEquals(712, grid.originY, 1e-6);
    }

    @Test
    public void fractionalZoomAndMovingPanelsStayOnPhysicalPixels() {
        for (float scale : new float[] {0.35f, 0.85f, 1f, 1.25f, 1.5f}) {
            for (int move = 0; move < 20; move++) {
                GuiTextPixelGrid grid = grid(scale, move * 0.37f, move * 0.23f, 1245, 701);
                assertEquals(2 * scale, grid.scaleX, 1e-6);
                double px = grid.originX + grid.alignX(31.25) * grid.scaleX;
                double py = grid.originY + grid.alignY(17.75) * grid.scaleY;
                assertEquals(Math.rint(px), px, 1e-9);
                assertEquals(Math.rint(py), py, 1e-9);
                // A texture's width stays exactly its native pixel width.
                double right = grid.alignX(31.25) + 43 / Math.abs(grid.scaleX);
                assertEquals(43, (right - grid.alignX(31.25)) * grid.scaleX, 1e-9);
            }
        }
    }

    @Test
    public void detachedDpiUsesSameMapping() {
        GuiTextPixelGrid grid = grid(1, 0, 0, 778, 438);
        assertEquals(778 / 622.5, grid.scaleX, 1e-6);
        assertEquals(-438 / 350.5, grid.scaleY, 1e-6);
    }

    @Test
    public void unsupportedTransformsFallBack() {
        FloatBuffer model = identity();
        IntBuffer viewport = IntBuffer.wrap(new int[] {0, 0, 800, 600});
        model.put(4, 0.5f);
        assertNull(GuiTextPixelGrid.fromMatrices(model, identity(), viewport));
        model = identity();
        model.put(3, 0.1f);
        assertNull(GuiTextPixelGrid.fromMatrices(model, identity(), viewport));
        model = identity();
        model.put(0, 0);
        assertNull(GuiTextPixelGrid.fromMatrices(model, identity(), viewport));
    }
}
