package com.zszl.zszlScriptMod.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProfileConfigFieldCodecTest {

    @Test
    public void unknownLegacyFieldsRemainVisibleAndSelectable() {
        String content = "{\"enabled\":true,\"legacyCustomFlag\":\"kept\"}";

        List<ProfileConfigFieldCodec.ConfigField> fields =
                ProfileConfigFieldCodec.describe("legacy.json", content);

        assertTrue(hasSelector(fields, "/enabled"));
        assertTrue(hasSelector(fields, "/legacyCustomFlag"));
    }

    @Test
    public void emptySelectionKeepsCompleteLegacyDocument() {
        String content = "{\"known\":1,\"oldUnknown\":2}";

        assertEquals(content, ProfileConfigFieldCodec.select(
                "settings.json", content, Collections.<String>emptyList()));
    }

    @Test
    public void selectedPathExportsOnlyThatPathEntry() {
        String content = "{\"version\":4,\"sequences\":["
                + "{\"name\":\"A\",\"category\":\"日常\",\"nodes\":[1]},"
                + "{\"name\":\"B\",\"category\":\"副本\",\"nodes\":[2,3]}"
                + "],\"legacyField\":true}";

        String selected = ProfileConfigFieldCodec.select(
                "custom_paths.json", content, Collections.singletonList("/sequences/1"));
        JsonObject root = new JsonParser().parse(selected).getAsJsonObject();
        JsonArray paths = root.getAsJsonArray("sequences");

        assertEquals(1, paths.size());
        assertEquals("B", paths.get(0).getAsJsonObject().get("name").getAsString());
        assertFalse(root.has("version"));
        assertFalse(root.has("legacyField"));
    }

    @Test
    public void selectedNestedFieldKeepsItsObjectParents() {
        String content = "{\"settings\":{\"speed\":2,\"legacyMode\":\"old\"},\"enabled\":true}";

        String selected = ProfileConfigFieldCodec.select(
                "settings.json", content, Collections.singletonList("/settings/legacyMode"));
        JsonObject root = new JsonParser().parse(selected).getAsJsonObject();

        assertEquals("old", root.getAsJsonObject("settings").get("legacyMode").getAsString());
        assertFalse(root.getAsJsonObject("settings").has("speed"));
        assertFalse(root.has("enabled"));
    }

    private static boolean hasSelector(List<ProfileConfigFieldCodec.ConfigField> fields, String selector) {
        for (ProfileConfigFieldCodec.ConfigField field : fields) {
            if (selector.equals(field.getSelector())) {
                return true;
            }
        }
        return false;
    }
}
