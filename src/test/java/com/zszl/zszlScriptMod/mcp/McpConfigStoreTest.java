package com.zszl.zszlScriptMod.mcp;
import com.google.gson.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;
import static com.zszl.zszlScriptMod.mcp.McpJson.*;
public class McpConfigStoreTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    @Test public void writeReadPatchDeleteAndHashConflict() throws Exception {
        Path root=temporary.newFolder("config").toPath();
        McpConfigStore store=new McpConfigStore(root);
        JsonObject written=store.call(object("operation","write","path","nested/a.json","value",object("enabled",false)));
        JsonObject read=store.call(object("operation","read","path","nested/a.json"));
        assertEquals(written.get("hash"),read.get("hash"));
        JsonArray patch=new JsonArray();patch.add(object("op","replace","path","/enabled","value",true));
        store.call(object("operation","patch","path","nested/a.json","patch",patch,"expectedHash",read.get("hash")));
        assertTrue(store.call(object("operation","read","path","nested/a.json")).getAsJsonObject("value").get("enabled").getAsBoolean());
        try {store.call(object("operation","delete","path","nested/a.json","expectedHash",read.get("hash")));fail();}catch(IllegalStateException expected){}
        store.call(object("operation","delete","path","nested/a.json"));
        assertFalse(Files.exists(root.resolve("nested/a.json")));
    }
    @Test public void failedPatchIsAtomic() throws Exception {
        Path root=temporary.newFolder("config").toPath(); McpConfigStore store=new McpConfigStore(root);
        store.call(object("operation","write","path","a.json","value",object("x",1)));
        byte[] before=Files.readAllBytes(root.resolve("a.json"));
        JsonArray patch=new JsonArray();patch.add(object("op","replace","path","/x","value",2));patch.add(object("op","test","path","/x","value",7));
        try {store.call(object("operation","patch","path","a.json","patch",patch));fail();}catch(IllegalStateException expected){}
        assertArrayEquals(before,Files.readAllBytes(root.resolve("a.json")));
    }
    @Test public void pointersSupportArraysAndEscaping() {
        JsonElement source=new JsonParser().parse("{\"a/b\":{\"~\":[1,3]}}");
        JsonArray patch=new JsonArray();patch.add(object("op","add","path","/a~1b/~0/1","value",2));patch.add(object("op","remove","path","/a~1b/~0/0"));
        assertEquals(new JsonParser().parse("{\"a/b\":{\"~\":[2,3]}}"),McpConfigStore.patch(source,patch));
        assertEquals(new JsonParser().parse("{\"a/b\":{\"~\":[1,3]}}"),source);
    }
    @Test public void rejectsTraversalAndTokenExposure() throws Exception {
        McpConfigStore store=new McpConfigStore(temporary.newFolder("config").toPath());
        for(String path:new String[]{"../outside.json","a/../../outside.json","mcp.token"}) {
            try {store.call(object("operation","write","path",path,"value",1));fail(path);}catch(IllegalArgumentException expected){}
        }
    }
    @Test public void rejectsSymlinksWhenSupported() throws Exception {
        Path root=temporary.newFolder("config").toPath(),outside=temporary.newFolder("outside").toPath();
        try {Files.createSymbolicLink(root.resolve("link"),outside);}catch(Exception e){Assume.assumeNoException(e);}
        try {new McpConfigStore(root).call(object("operation","write","path","link/x.json","value",1));fail();}catch(java.io.IOException expected){}
        assertFalse(Files.exists(outside.resolve("x.json")));
    }
}
