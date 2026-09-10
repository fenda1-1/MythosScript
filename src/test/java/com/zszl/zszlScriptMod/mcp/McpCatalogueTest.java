package com.zszl.zszlScriptMod.mcp;

import com.google.gson.*;
import com.zszl.zszlScriptMod.gui.path.GuiActionEditor.library.ActionDisplayCatalog;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Modifier;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class McpCatalogueTest {
    @Test public void everyActionHasAReferenceAndEveryModuleEntrypointResolves() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/mcp/actions.json")) {
            assertNotNull(in);
            JsonObject actions = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Set<String> names = new HashSet<>();
            for (Map.Entry<String, JsonElement> e : actions.entrySet()) names.add(e.getKey());
            assertEquals(ActionDisplayCatalog.getActionDisplayKeys().keySet(), names);
        }
        Set<String> ids = new HashSet<>();
        for (JsonElement entry : McpModules.list()) {
            JsonObject module = entry.getAsJsonObject();
            assertTrue("Duplicate module id", ids.add(module.get("id").getAsString()));
            Class<?> owner = Class.forName(module.get("class").getAsString(), false, getClass().getClassLoader());
            for (String operation : Arrays.asList("reload", "save"))
                for (JsonElement method : module.getAsJsonArray(operation))
                    assertTrue(Modifier.isStatic(owner.getMethod(method.getAsString()).getModifiers()));
        }
    }
}
