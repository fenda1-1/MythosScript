package com.zszl.zszlScriptMod.gui.modern.rules;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class AutoFollowReturnSelectionTest {
    private AutoFollowReturnSelection selection(List<String> existing, int replacement) {
        return new AutoFollowReturnSelection(new double[]{20,20,-20,-20},existing,64,replacement);
    }

    @Test public void batchIsIsolatedUntilConfirmedAndPreservesLegacyText() {
        List<String> original = new ArrayList<>(Arrays.asList("1.123,2.456"));
        AutoFollowReturnSelection s = selection(original,-1);
        assertTrue(s.add(3.5,70,4.5)); assertTrue(s.add(5.5,80,6.5));
        assertEquals(3,s.result().size()); assertEquals("1.123,2.456",s.result().get(0));
        s.remove(0); assertEquals(Collections.singletonList("1.123,2.456"),original);
        s.result().clear(); assertEquals(2,s.points.size());
    }

    @Test public void rangeUsesBothCornersInclusivelyAndRejectsDuplicatePicks() {
        AutoFollowReturnSelection s=selection(Collections.emptyList(),-1);
        assertTrue(s.add(-20,90,20)); assertFalse(s.add(-20,90,20));
        assertFalse(s.add(20.5,64,0)); assertEquals(1,s.points.size());
    }

    @Test public void rayHitsFullHeightAndChoosesNearestPreview() {
        AutoFollowReturnSelection s=selection(Arrays.asList("0.5,64,2.5","0.5,64,8.5"),-1);
        double[] ray={0.5,65.9,-10,0.5,65.9,20};
        assertEquals(0,s.hit(ray)); s.remove(s.hit(ray));
        assertEquals(0,s.hit(ray)); assertEquals("0.5,64,8.5",s.result().get(0));
        assertEquals(-1,s.hit(new double[]{0.5,66.1,-10,0.5,66.1,20}));
        assertEquals(-1,s.hit(new double[]{3,65,-10,3,65,20}));
    }

    @Test public void cameraInsidePreviewCanDeleteAndEmptyConfirmationIsValid() {
        AutoFollowReturnSelection s=selection(Collections.singletonList("0.5,64,0.5"),-1);
        assertEquals(0,s.hit(new double[]{0.5,65,0.5,10,65,0.5}));
        s.remove(0); assertTrue(s.result().isEmpty()); assertEquals(-1,s.hit(null));
    }

    @Test public void repickRetainsOrderThenAllowsFurtherAdditions() {
        AutoFollowReturnSelection s=selection(Arrays.asList("1,64,1","2,64,2","3,64,3"),1);
        s.remove(0); assertTrue(s.add(8.5,70,8.5)); assertTrue(s.add(9.5,70,9.5));
        assertEquals(Arrays.asList("8.5,70.0,8.5","3,64,3","9.5,70.0,9.5"),s.result());
    }
}
