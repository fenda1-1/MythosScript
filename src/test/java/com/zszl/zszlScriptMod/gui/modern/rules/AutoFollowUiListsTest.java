package com.zszl.zszlScriptMod.gui.modern.rules;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class AutoFollowUiListsTest {
    @Test public void pointEditingPreservesLegacyAutomaticHeightAndPrecision() {
        for (String point : Arrays.asList("-176.34075035635402,-402.2923186766955", "-5.5,64,23.5")) {
            String[] fields = AutoFollowUiLists.coordinates(point);
            assertEquals(point, AutoFollowUiLists.point(fields[0], fields[1], fields[2]));
        }
    }

    @Test public void rejectsNonfiniteAndIncompleteCoordinates() {
        for (String invalid : Arrays.asList("NaN", "Infinity", "-Infinity", "", "1x")) {
            try { AutoFollowUiLists.point(invalid,"64","2"); fail(invalid); }
            catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void bulkNamesAreTrimmedAndDeduplicatedWithoutSplittingSpaces() {
        assertEquals(Arrays.asList("Boss King", "僵尸", "守卫"),
                AutoFollowUiLists.entries(" Boss King，僵尸;Boss King\n守卫；僵尸", false));
    }

    @Test public void reorderUsesInsertionSlotsAndNeverDropsOrDuplicatesPoints() {
        for (int from=0;from<5;from++) for(int slot=0;slot<=5;slot++) {
            List<String> rows = new ArrayList<>(Arrays.asList("a","b","c","d","e"));
            String moved = rows.get(from);
            AutoFollowUiLists.move(rows,from,slot);
            assertEquals(5,rows.size()); assertEquals(5,new HashSet<>(rows).size());
            assertEquals(moved,rows.get(slot>from?slot-1:slot));
        }
    }

    @Test public void pointListKeepsDuplicateCoordinatesAndIndividualAxesTogether() {
        assertEquals(Arrays.asList("1,64,2", "1,64,2", "-5,7"),
                AutoFollowUiLists.entries("1,64,2;1,64,2\n-5,7", true));
    }
}
