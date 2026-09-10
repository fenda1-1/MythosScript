package com.zszl.zszlScriptMod.mcp;
import com.google.gson.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static com.zszl.zszlScriptMod.mcp.McpJson.*;
public class McpFieldsTest {
    public static class Fixture {
        public static int count=2;
        public static boolean enabled=false;
        public static float range=3;
        public static List<String> names=new ArrayList<>(Arrays.asList("a"));
        public static final String CONSTANT="constant";
    }
    @Test public void validatesAllFieldsBeforeMutationAndRestoresSnapshot() {
        JsonObject before=McpFields.snapshot(Fixture.class);
        try {
            try{McpFields.decode(Fixture.class,object("count",10,"missing",1));fail();}catch(IllegalArgumentException expected){}
            assertEquals(2,Fixture.count);
            McpFields.apply(McpFields.decode(Fixture.class,object("enabled",true,"count",5,"names",Arrays.asList("b"))));
            assertTrue(Fixture.enabled);assertEquals(Arrays.asList("b"),Fixture.names);
        }finally{McpFields.apply(McpFields.decode(Fixture.class,before));}
        assertEquals(2,Fixture.count);assertFalse(Fixture.enabled);assertEquals(Arrays.asList("a"),Fixture.names);
    }
    @Test public void constantsAndWrongTypesAreRejected() {
        for(JsonObject p:Arrays.asList(object("CONSTANT","new"),object("enabled","false"),object("count",1.5))) {
            try{McpFields.decode(Fixture.class,p);fail();}catch(RuntimeException expected){}
        }
    }
}
