package com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour;

/** An observed straight-ground sprint plateau, not a theoretical jump range. */
public final class ParkourSpeedSampler {
    private double previous = Double.NaN;
    private double sum;
    private int stableTicks;

    public void sample(double speed, boolean eligible) {
        if (!eligible || !Double.isFinite(speed) || speed <= 0.01D) {
            reset();
            return;
        }
        if (!Double.isFinite(previous) || Math.abs(speed - previous) > Math.max(0.0005D, speed * 0.005D)) {
            sum = speed;
            stableTicks = 1;
        } else {
            sum += speed;
            stableTicks++;
        }
        previous = speed;
    }

    public boolean isStable() { return stableTicks >= 6; }
    public int getStableTicks() { return stableTicks; }
    public double getEstimate() { return isStable() ? sum / stableTicks : Double.NaN; }

    public void reset() {
        previous = Double.NaN;
        sum = 0;
        stableTicks = 0;
    }
}
