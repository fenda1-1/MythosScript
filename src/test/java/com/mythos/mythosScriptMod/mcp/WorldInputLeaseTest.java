package com.mythos.mythosScriptMod.mcp;
import org.junit.Test;
import static org.junit.Assert.*;

public class WorldInputLeaseTest {
    @Test public void expiryClearsKeysAndCannotBeRevivedByAnUpdate() {
        WorldInputLease lease=new WorldInputLease();assertTrue(lease.acquire("desktop",1,100));
        assertTrue(lease.update("desktop",2,17,200));assertEquals(17,lease.keys(201));
        assertEquals(0,lease.keys(200+WorldInputLease.DURATION_NANOS));
        assertFalse(lease.update("desktop",3,17,201+WorldInputLease.DURATION_NANOS));
    }
    @Test public void wrongOwnerAndOutOfOrderPacketsNeverChangeHeldKeys() {
        WorldInputLease lease=new WorldInputLease();lease.acquire("a",10,0);lease.update("a",11,1,1);
        assertFalse(lease.update("b",12,16,2));assertFalse(lease.update("a",10,0,2));
        assertFalse(lease.acquire("b",12,2));assertFalse(lease.release("b",13,3));
        assertEquals(1,lease.keys(4));assertTrue(lease.release("a",12,4));assertEquals(0,lease.keys(5));
    }
    @Test public void opposingKeysCancelAndSneakingScalesVanillaAxes() {
        assertEquals(0,WorldInputLease.forward(3),0);
        assertEquals(0,WorldInputLease.strafe(12),0);
        assertEquals(1,WorldInputLease.forward(1),0);assertEquals(-1,WorldInputLease.strafe(8),0);
        float f=WorldInputLease.forward(9),s=WorldInputLease.strafe(9);
        assertEquals(1,f,0);assertEquals(-1,s,0);
        assertEquals(0.3f,WorldInputLease.forward(1|32),0.0001);
        assertEquals(-0.3f,WorldInputLease.strafe(8|32|64),0.0001);
    }
    @Test public void rejectedMasksAndDuplicateSequencesDoNotExtendLease() {
        WorldInputLease lease=new WorldInputLease();lease.acquire("a",1,0);
        assertFalse(lease.update("a",2,128,100));assertFalse(lease.update("a",1,1,200));
        assertFalse(lease.active(WorldInputLease.DURATION_NANOS));
    }
    @Test public void modifiersReleaseAndExpireWithTheSameLease() {
        WorldInputLease lease=new WorldInputLease();lease.acquire("a",1,0);
        assertTrue(lease.update("a",2,127,1));assertEquals(127,lease.keys(2));
        assertTrue(lease.update("a",3,1,3));assertEquals(1,lease.keys(4));
        assertTrue(lease.update("a",4,96,5));assertEquals(0,lease.keys(5+WorldInputLease.DURATION_NANOS));
    }
}
