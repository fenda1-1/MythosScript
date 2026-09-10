package com.zszl.zszlScriptMod.otherfeatures.handler.movement;

import com.zszl.zszlScriptMod.shadowbaritone.utils.GuiPathingPolicy;
import com.zszl.zszlScriptMod.shadowbaritone.utils.PlayerMovementInput;
import com.zszl.zszlScriptMod.zszlScriptMod;
import com.zszl.zszlScriptMod.system.SimulatedKeyInputManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MovementInput;
import org.lwjgl.input.Keyboard;

public final class GuiMoveFeatureHandler {

    private static final float LOOK_STEP = 5.0F;

    private static boolean overridingMovementKeys;

    private GuiMoveFeatureHandler() {
    }

    public static void apply(Minecraft mc) {
        if (mc == null) {
            return;
        }

        if (!shouldAllowMovementDuringGui(mc)) {
            if (overridingMovementKeys) {
                restoreMovementKeyStates(mc);
                overridingMovementKeys = false;
            }
            return;
        }

        if (shouldDeferToBaritone(mc)) {
            if (overridingMovementKeys) {
                restoreMovementKeyStates(mc);
                overridingMovementKeys = false;
            }
            return;
        }

        if (mc.currentScreen != null) {
            mc.currentScreen.allowUserInput = true;
        }

        syncMovementKeyStates(mc);
        applyMovementInput(mc, mc.player == null ? null : mc.player.movementInput);
        overridingMovementKeys = true;
        applyArrowKeyLook(mc.player);
    }

    public static void onClientDisconnect() {
        overridingMovementKeys = false;
    }

    public static boolean shouldAllowMovementDuringGui(Minecraft mc) {
        if (!MovementFeatureManager.isEnabled("gui_move")) {
            return false;
        }
        if (mc == null || mc.player == null || mc.world == null) {
            return false;
        }
        if (mc.currentScreen instanceof GuiChat) {
            return false;
        }
        return mc.currentScreen != null || zszlScriptMod.isGuiVisible;
    }

    public static void applyMovementInput(Minecraft mc, MovementInput movementInput) {
        if (!shouldAllowMovementDuringGui(mc) || movementInput == null || mc == null || mc.gameSettings == null) {
            return;
        }
        if (movementInput instanceof PlayerMovementInput || shouldDeferToBaritone(mc)) {
            return;
        }

        boolean forwardDown = isMovementKeyDown(mc.gameSettings.keyBindForward);
        boolean backDown = isMovementKeyDown(mc.gameSettings.keyBindBack);
        boolean leftDown = isMovementKeyDown(mc.gameSettings.keyBindLeft);
        boolean rightDown = isMovementKeyDown(mc.gameSettings.keyBindRight);
        boolean jumpDown = isMovementKeyDown(mc.gameSettings.keyBindJump);
        boolean sneakDown = isMovementKeyDown(mc.gameSettings.keyBindSneak);

        movementInput.moveStrafe = 0.0F;
        movementInput.moveForward = 0.0F;
        movementInput.forwardKeyDown = forwardDown;
        movementInput.backKeyDown = backDown;
        movementInput.leftKeyDown = leftDown;
        movementInput.rightKeyDown = rightDown;

        if (forwardDown) {
            movementInput.moveForward++;
        }
        if (backDown) {
            movementInput.moveForward--;
        }
        if (leftDown) {
            movementInput.moveStrafe++;
        }
        if (rightDown) {
            movementInput.moveStrafe--;
        }

        movementInput.jump = jumpDown;
        movementInput.sneak = sneakDown;
        if (movementInput.sneak) {
            movementInput.moveStrafe *= 0.3F;
            movementInput.moveForward *= 0.3F;
        }

        EntityPlayerSP player = mc.player;
        if (player != null && isMovementKeyDown(mc.gameSettings.keyBindSprint)
                && movementInput.moveForward > 0.0F
                && !movementInput.sneak) {
            player.setSprinting(true);
        }
    }

    private static void syncMovementKeyStates(Minecraft mc) {
        if (mc.gameSettings == null) {
            return;
        }

        syncKeyState(mc.gameSettings.keyBindForward, true);
        syncKeyState(mc.gameSettings.keyBindBack, true);
        syncKeyState(mc.gameSettings.keyBindLeft, true);
        syncKeyState(mc.gameSettings.keyBindRight, true);
        syncKeyState(mc.gameSettings.keyBindJump, true);
        syncKeyState(mc.gameSettings.keyBindSneak, true);
        syncKeyState(mc.gameSettings.keyBindSprint, true);
    }

    private static void restoreMovementKeyStates(Minecraft mc) {
        if (mc.gameSettings == null) {
            return;
        }

        boolean keepPhysicalState = mc.currentScreen == null;
        syncKeyState(mc.gameSettings.keyBindForward, keepPhysicalState);
        syncKeyState(mc.gameSettings.keyBindBack, keepPhysicalState);
        syncKeyState(mc.gameSettings.keyBindLeft, keepPhysicalState);
        syncKeyState(mc.gameSettings.keyBindRight, keepPhysicalState);
        syncKeyState(mc.gameSettings.keyBindJump, keepPhysicalState);
        syncKeyState(mc.gameSettings.keyBindSneak, keepPhysicalState);
        syncKeyState(mc.gameSettings.keyBindSprint, keepPhysicalState);
    }

    private static void syncKeyState(KeyBinding keyBinding, boolean mirrorPhysicalState) {
        if (keyBinding == null) {
            return;
        }

        int keyCode = keyBinding.getKeyCode();
        if (keyCode == Keyboard.KEY_NONE || keyCode < 0) {
            return;
        }

        KeyBinding.setKeyBindState(keyCode, mirrorPhysicalState && SimulatedKeyInputManager.isKeyDown(keyCode));
    }

    private static boolean isMovementKeyDown(KeyBinding keyBinding) {
        if (keyBinding == null) {
            return false;
        }

        int keyCode = keyBinding.getKeyCode();
        if (keyCode == Keyboard.KEY_NONE) {
            return false;
        }
        return keyCode < 0 ? keyBinding.isKeyDown() : SimulatedKeyInputManager.isKeyDown(keyCode);
    }

    private static boolean shouldDeferToBaritone(Minecraft mc) {
        return mc != null && GuiPathingPolicy.shouldKeepPathingDuringGui(mc);
    }

    private static void applyArrowKeyLook(EntityPlayerSP player) {
        if (player == null) {
            return;
        }

        if (Keyboard.isKeyDown(Keyboard.KEY_UP)) {
            player.rotationPitch -= LOOK_STEP;
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_DOWN)) {
            player.rotationPitch += LOOK_STEP;
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_RIGHT)) {
            player.rotationYaw += LOOK_STEP;
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_LEFT)) {
            player.rotationYaw -= LOOK_STEP;
        }

        if (player.rotationPitch > 90.0F) {
            player.rotationPitch = 90.0F;
        }
        if (player.rotationPitch < -90.0F) {
            player.rotationPitch = -90.0F;
        }
    }
}
