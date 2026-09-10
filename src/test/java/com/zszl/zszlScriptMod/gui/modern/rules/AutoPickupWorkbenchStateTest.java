package com.zszl.zszlScriptMod.gui.modern.rules;

import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class AutoPickupWorkbenchStateTest {
    @Test public void newEmptyCategoryIsImmediatelyVisibleInNavigationGroups() {
        AutoPickupWorkbenchState state = new AutoPickupWorkbenchState(Collections.emptyList(),
                Collections.singletonList("默认"), false);
        assertTrue(state.addCategory("测试分类"));
        assertEquals("测试分类", state.selectedCategory());
        boolean found = false;
        for (AutoPickupWorkbenchState.Group group : state.groups()) {
            if ("测试分类".equals(group.name())) {
                found = true;
                assertTrue(group.rules().isEmpty());
            }
        }
        assertTrue(found);
    }

    @Test public void duplicateCategoryIsRejectedWithoutDroppingTheExistingGroup() {
        AutoPickupWorkbenchState state = new AutoPickupWorkbenchState(Collections.emptyList(),
                Collections.singletonList("默认"), false);
        assertTrue(state.addCategory("采集"));
        assertFalse(state.addCategory("采集"));
        assertEquals(2, state.groups().size());
    }
}
