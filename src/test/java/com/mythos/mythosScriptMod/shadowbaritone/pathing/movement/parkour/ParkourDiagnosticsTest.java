package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import static org.junit.Assert.*;

public class ParkourDiagnosticsTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void sessionsNeverOverwriteAndFlushOnClose() throws Exception {
        ParkourLogSession first = new ParkourLogSession(temp.getRoot().toPath());
        ParkourLogSession second = new ParkourLogSession(temp.getRoot().toPath());
        assertNotEquals(first.getPath(), second.getPath());
        JsonObject row = new JsonObject();
        row.addProperty("message", "旋转跳\n落地");
        first.write(row);
        first.flush();
        assertEquals(1, Files.readAllLines(first.getPath(), StandardCharsets.UTF_8).size());
        first.write(row);
        first.close();
        first.close();
        second.write(row);
        second.close();
        List<String> lines = Files.readAllLines(first.getPath(), StandardCharsets.UTF_8);
        assertEquals(2, lines.size());
        assertEquals("旋转跳\n落地", new JsonParser().parse(lines.get(0)).getAsJsonObject().get("message").getAsString());
        assertEquals(1, Files.readAllLines(second.getPath(), StandardCharsets.UTF_8).size());
    }

    @Test public void accelerationMustSettleBeforePlateauIsReported() {
        ParkourSpeedSampler sampler = new ParkourSpeedSampler();
        for (double speed : new double[] {0.13, 0.20, 0.25, 0.27, 0.28}) {
            sampler.sample(speed, true);
            assertFalse(sampler.isStable());
        }
        for (int i = 0; i < 6; i++) sampler.sample(0.286, true);
        assertTrue(sampler.isStable());
        assertEquals(0.286, sampler.getEstimate(), 1E-6);
    }

    @Test public void airborneCollisionTurnOrChangedSurfaceInvalidatesPlateau() {
        ParkourSpeedSampler sampler = new ParkourSpeedSampler();
        for (int i = 0; i < 8; i++) sampler.sample(0.286, true);
        sampler.sample(0.60, false);
        assertFalse(sampler.isStable());
        assertTrue(Double.isNaN(sampler.getEstimate()));
        sampler.sample(0.286, true);
        assertEquals(1, sampler.getStableTicks());
        sampler.sample(Double.NaN, true);
        assertEquals(0, sampler.getStableTicks());
    }
}
