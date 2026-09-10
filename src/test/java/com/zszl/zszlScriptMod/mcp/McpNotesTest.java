package com.zszl.zszlScriptMod.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.zszl.zszlScriptMod.mcp.McpJson.object;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class McpNotesTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private McpNotes notes(String current) throws Exception {
        return new McpNotes(temporary.newFolder("notes").toPath(), new java.util.function.Supplier<String>() {
            @Override public String get() { return current; }
        });
    }

    @Test public void readMissingWriteAndDefaultOperation() throws Exception {
        McpNotes notes = notes("singleplayer");
        JsonObject missing = notes.call(new JsonObject());
        assertEquals("singleplayer", missing.get("server").getAsString());
        assertEquals("notes/singleplayer.md", missing.get("path").getAsString());
        assertEquals("", missing.get("text").getAsString());
        assertEquals("missing", missing.get("hash").getAsString());
        assertFalse(missing.get("exists").getAsBoolean());
        JsonObject written = notes.call(object("operation", "write", "text", "# 坐标\n100 64 200",
                "expectedHash", "missing"));
        assertTrue(written.get("exists").getAsBoolean());
        assertEquals("# 坐标\n100 64 200", notes.call(object("operation", "read")).get("text").getAsString());
        assertEquals(written.get("hash"), notes.call(new JsonObject()).get("hash"));
    }

    @Test public void appendInsertsNewlineAndKeepsServersSeparate() throws Exception {
        McpNotes notes = notes("play.hypixel.net:25565");
        notes.call(object("operation", "write", "text", "first"));
        JsonObject appended = notes.call(object("operation", "append", "text", "second"));
        assertEquals("play.hypixel.net_25565", appended.get("server").getAsString());
        assertEquals("first\nsecond", appended.get("text").getAsString());
        notes.call(object("operation", "write", "server", "singleplayer", "text", "solo"));
        assertEquals("first\nsecond", notes.call(object("operation", "read")).get("text").getAsString());
        assertEquals("solo", notes.call(object("operation", "read", "server", "singleplayer")).get("text").getAsString());
    }

    @Test public void expectedHashRejectsStaleWrites() throws Exception {
        McpNotes notes = notes("singleplayer");
        JsonObject first = notes.call(object("operation", "write", "text", "a"));
        try {
            notes.call(object("operation", "write", "text", "b", "expectedHash", "missing"));
            fail();
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("Notebook changed"));
        }
        notes.call(object("operation", "write", "text", "b", "expectedHash", first.get("hash").getAsString()));
        assertEquals("b", notes.call(object("operation", "read")).get("text").getAsString());
    }

    @Test public void listAndLiveEditorShareDrafts() throws Exception {
        McpNotes notes = notes("singleplayer");
        notes.call(object("operation", "write", "server", "a.example.com", "text", "alpha"));
        final String[] draft = { "typed" };
        McpNotes.Live live = new McpNotes.Live() {
            @Override public String serverKey() { return "singleplayer"; }
            @Override public String text() { return draft[0]; }
            @Override public void replace(String text) { draft[0] = text; }
        };
        notes.attach(live);
        JsonObject read = notes.call(object("operation", "read"));
        assertEquals("typed", read.get("text").getAsString());
        assertTrue(read.get("openInEditor").getAsBoolean());
        notes.call(object("operation", "write", "text", "from mcp"));
        assertEquals("from mcp", draft[0]);
        JsonArray listed = notes.call(object("operation", "list")).getAsJsonArray("notes");
        assertEquals(2, listed.size());
        notes.detach(live);
        assertFalse(notes.call(object("operation", "read")).get("openInEditor").getAsBoolean());
    }

    @Test public void sanitizesTraversalAndRejectsOversizeText() throws Exception {
        assertEquals(".._secret", McpNotes.sanitizeServerKey("../secret"));
        McpNotes notes = notes("singleplayer");
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i <= McpNotes.MAX_TEXT_LENGTH; i++) huge.append('x');
        try {
            notes.call(object("operation", "write", "text", huge.toString()));
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("exceeds"));
        }
        Path file = notes.fileFor("../secret");
        assertEquals(".._secret.md", file.getFileName().toString());
        assertTrue(file.startsWith(file.getParent()));
        assertFalse(Files.exists(temporary.getRoot().toPath().resolve("secret.md")));
    }

    @Test public void unknownOperationFails() throws Exception {
        try {
            notes("singleplayer").call(object("operation", "not-a-notes-operation"));
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Unknown notes operation"));
        }
    }

    @Test public void writesUtf8MarkdownToDisk() throws Exception {
        McpNotes notes = notes("singleplayer");
        notes.call(object("operation", "write", "text", "中文笔记"));
        assertEquals("中文笔记", new String(Files.readAllBytes(notes.fileFor("singleplayer")), StandardCharsets.UTF_8));
    }

    @Test public void renamesAndDeletesNotebookGroups() throws Exception {
        McpNotes notes = notes("singleplayer");
        notes.call(object("operation", "write", "server", "old-group", "text", "# keep me"));
        JsonObject renamed = notes.call(object("operation", "rename", "server", "old-group",
                "newServer", "new-group"));
        assertEquals("new-group", renamed.get("server").getAsString());
        assertEquals("# keep me", notes.diskText("new-group"));
        assertFalse(Files.exists(notes.fileFor("old-group")));

        JsonObject deleted = notes.call(object("operation", "delete", "server", "new-group"));
        assertFalse(deleted.get("exists").getAsBoolean());
        assertFalse(Files.exists(notes.fileFor("new-group")));
    }
}
