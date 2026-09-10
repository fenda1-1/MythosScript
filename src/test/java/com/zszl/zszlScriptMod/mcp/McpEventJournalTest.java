package com.zszl.zszlScriptMod.mcp;

import com.google.gson.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.zszl.zszlScriptMod.mcp.McpJson.*;

public class McpEventJournalTest {
    @Test public void multiGroupFiltersPaginateWithoutLosingScannedEvents() {
        McpEventJournal j=new McpEventJournal(20,100000);j.begin();
        j.append("entities","appeared",1,object("entityId",7));
        j.append("input","key",1,object("keyCode",12));
        j.append("gui","changed",2,object("entityId",7));
        JsonObject p=object("afterId",0,"groups",Arrays.asList("entities","gui"),"limit",1);
        JsonObject first=j.read(p);assertTrue(first.get("hasMore").getAsBoolean());assertEquals(1,first.get("nextAfterId").getAsLong());
        p.add("afterId",first.get("nextAfterId"));JsonObject second=j.read(p);
        assertEquals(3,second.get("nextAfterId").getAsLong());assertFalse(second.get("hasMore").getAsBoolean());
        p.addProperty("afterId",0);p.add("groups",GSON.toJsonTree(Arrays.asList("chat")));
        assertEquals(3,j.read(p).get("nextAfterId").getAsLong());
    }
    @Test public void watchAndEntityAndSpaceFiltersAreIndependent() {
        McpEventJournal j=new McpEventJournal(20,100000);j.begin();
        j.append("interaction","attack_attempt",1,object("targetId",7,"pos",new int[]{-1,64,0},"watchIds",Arrays.asList("fight")));
        JsonObject p=object("entityIds",Arrays.asList(7),"watchIds",Arrays.asList("fight","other"),"min",new int[]{-2,64,-2},"max",new int[]{0,65,2});
        assertEquals(1,j.read(p).getAsJsonArray("events").size());
        p.addProperty("toMs",0);assertEquals(0,j.read(p).getAsJsonArray("events").size());
        p.remove("toMs");p.add("entityIds",GSON.toJsonTree(Arrays.asList(9)));assertEquals(0,j.read(p).getAsJsonArray("events").size());
    }
    @Test public void sessionEvictionAndOversizeAreExplicitAndCopiesAreImmutable() {
        McpEventJournal j=new McpEventJournal(2,1500);j.begin();String old=j.sessionId();
        JsonObject d=object("entityId",1);j.append("entities","appeared",1,d);d.addProperty("entityId",99);
        assertEquals(1,j.read(new JsonObject()).getAsJsonArray("events").get(0).getAsJsonObject().getAsJsonObject("data").get("entityId").getAsInt());
        j.append("entities","moved",2,d);j.append("entities","moved",3,d);
        assertTrue(j.read(object("afterId",0)).get("cursorExpired").getAsBoolean());
        j.append("gui","changed",4,object("text",String.join("",Collections.nCopies(2000,"x"))));
        assertEquals(0,j.read(new JsonObject()).get("retainedCount").getAsInt());
        j.begin();j.append("session","world_entered",5,new JsonObject());
        assertTrue(j.read(object("sessionId",old,"afterId",4)).get("sessionChanged").getAsBoolean());
        assertEquals(5,j.read(new JsonObject()).get("latestId").getAsLong());
    }
    @Test public void waitReleasesMonitorAndWakesForMatchingEvent() throws Exception {
        McpEventJournal j=new McpEventJournal(20,100000);j.begin();ExecutorService pool=Executors.newSingleThreadExecutor();
        try {
            Future<JsonObject> f=pool.submit(()->j.await(object("afterId",0,"groups",Arrays.asList("entities")),2000,()->true));
            j.append("input","key",1,new JsonObject());j.append("entities","death_confirmed",2,object("entityId",7));
            assertEquals(1,f.get(3,TimeUnit.SECONDS).getAsJsonArray("events").size());
        } finally {pool.shutdownNow();}
    }
    @Test public void disconnectAndServiceStopReleaseWaiters() throws Exception {
        McpEventJournal j=new McpEventJournal(20,10000);j.begin();j.end();
        assertFalse(j.await(object("afterId",0),2000,()->true).get("connected").getAsBoolean());
        try {j.await(object("afterId",0),2000,()->false);fail();} catch(IllegalStateException expected) {}
    }
    @Test public void invalidFiltersFailInsteadOfSilentlyMatchingEverything() {
        McpEventJournal j=new McpEventJournal(20,10000);
        for(JsonObject p:Arrays.asList(object("groups",Arrays.asList("typo")),object("afterId",-1),object("fromMs",10,"toMs",1),object("min",new int[]{0,0,0}),object("limit",501))) {
            try {j.read(p);fail(p.toString());}catch(IllegalArgumentException expected){}
        }
    }
}
