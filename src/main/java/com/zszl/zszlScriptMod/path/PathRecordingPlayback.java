package com.zszl.zszlScriptMod.path;

import com.zszl.zszlScriptMod.gui.GuiInventory;
import com.zszl.zszlScriptMod.handlers.EmbeddedNavigationHandler;
import com.zszl.zszlScriptMod.path.PathSequenceManager.PathSequence;
import com.zszl.zszlScriptMod.path.PathSequenceManager.PathStep;

import net.minecraft.client.Minecraft;

/** Executes a recording snapshot once without registering or saving a sequence. */
public final class PathRecordingPlayback {
    private static PathSequence snapshot;

    private PathRecordingPlayback() {
    }

    public static boolean start(PathSequence draft, int startStepIndex) {
        if (draft == null || startStepIndex < 0 || startStepIndex >= draft.getSteps().size()
                || Minecraft.getMinecraft().player == null) {
            return false;
        }
        PathSequence copy = new PathSequence(draft, false);
        copy.setSingleExecution(true);
        if (PathRecordingManager.isRecording() && !PathRecordingManager.isPaused()) {
            PathRecordingManager.togglePaused();
        }
        PathSequenceEventListener runner = PathSequenceEventListener.instance;
        runner.stopTracking();
        EmbeddedNavigationHandler.INSTANCE.stop();
        PathSequenceManager.clearRunSequenceCallStack();
        snapshot = copy;
        GuiInventory.loopCounter = 1;
        GuiInventory.isLooping = true;
        runner.prepareForSequenceStartDispatch("recording-preview");
        PathStep first = copy.getSteps().get(startStepIndex);
        if (first.hasGotoTarget()) {
            double[] point = first.getGotoPoint();
            EmbeddedNavigationHandler.INSTANCE.startGoto(point[0], point[1], point[2], true);
        }
        runner.startTracking(copy, 1, null, startStepIndex);
        runner.setStatus(copy.getName());
        return true;
    }

    public static boolean isRunning() {
        return snapshot != null && PathSequenceEventListener.instance.isTracking()
                && (PathSequenceEventListener.instance.currentSequence == snapshot
                        || PathSequenceManager.hasPendingCaller(snapshot));
    }

    static boolean isSnapshot(PathSequence sequence) {
        return snapshot != null && snapshot == sequence;
    }

    public static boolean isPaused() {
        return isRunning() && PathSequenceEventListener.instance.isPaused();
    }

    public static void togglePaused() {
        if (!isRunning()) return;
        if (isPaused()) PathSequenceEventListener.instance.resume();
        else PathSequenceEventListener.instance.pause();
    }

    public static void stop() {
        if (!isRunning()) return;
        PathSequenceEventListener.instance.stopTracking();
        EmbeddedNavigationHandler.INSTANCE.stop();
        snapshot = null;
    }
}
