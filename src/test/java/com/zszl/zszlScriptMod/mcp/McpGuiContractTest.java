package com.zszl.zszlScriptMod.mcp;

import java.util.Arrays;

import org.junit.Test;

import com.zszl.zszlScriptMod.utils.guiinspect.GuiElementInspector;

import static org.junit.Assert.*;

/** Contract tests for the metadata returned by mythos_gui/inspect. */
public class McpGuiContractTest {

    @Test
    public void semanticMetadataSurvivesGeometryAndPathCopies() {
        GuiElementInspector.GuiElementInfo original = new GuiElementInspector.GuiElementInfo(
                GuiElementInspector.ElementType.CUSTOM, "screen/test/field/range", "Range", 10, 20, 80, 22,
                Integer.MIN_VALUE, -1, "input", "4.2", true, true,
                Arrays.asList("click", "input", "set"), Arrays.<String>asList("1", "8"));

        GuiElementInspector.GuiElementInfo copied = original.withGeometry(15, 25, 90, 24)
                .withPath("screen/test/field/range/input").withVisibility(false);

        assertEquals("input", copied.getControlType());
        assertEquals("4.2", copied.getValue());
        assertEquals(Arrays.asList("click", "input", "set"), copied.getActions());
        assertEquals(Arrays.asList("1", "8"), copied.getChoices());
        assertFalse(copied.isVisible());
        assertEquals(15, copied.getX());
        assertEquals("screen/test/field/range/input", copied.getPath());
    }

    @Test
    public void legacyElementsRemainClickableWithDefaultMetadata() {
        GuiElementInspector.GuiElementInfo button = new GuiElementInspector.GuiElementInfo(
                GuiElementInspector.ElementType.BUTTON, "screen/test/button[0]", "Save", 1, 2, 40, 20, 0, -1);

        assertEquals("button", button.getControlType());
        assertTrue(button.isEnabled());
        assertFalse(button.isEditable());
        assertEquals(Arrays.asList("click"), button.getActions());
    }
}
