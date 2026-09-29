package com.mythos.mythosScriptMod.shadowbaritone.launch.mixins;

import com.mythos.mythosScriptMod.otherfeatures.handler.movement.MovementFeatureManager;
import com.mythos.mythosScriptMod.system.SimulatedKeyInputManager;
import com.mythos.mythosScriptMod.shadowbaritone.utils.GuiPathingPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MovementInput;
import net.minecraft.util.MovementInputFromOptions;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MovementInputFromOptions.class, priority = 1200)
public class MixinMovementInputFromOptions {

    @Inject(method = "updatePlayerMoveState", at = @At("RETURN"))
    private void mythosScript$applyGuiMovementAfterVanilla(CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        boolean allowGuiMovement = MovementFeatureManager.shouldAllowMovementDuringGui(mc)
                || GuiPathingPolicy.shouldKeepPathingDuringGui(mc);
        if (!allowGuiMovement || mc == null || mc.gameSettings == null) {
            return;
        }

        MovementInput movementInput = (MovementInput) (Object) this;
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

    private boolean isMovementKeyDown(KeyBinding keyBinding) {
        if (keyBinding == null) {
            return false;
        }

        int keyCode = keyBinding.getKeyCode();
        if (keyCode == Keyboard.KEY_NONE) {
            return false;
        }
        return keyCode < 0 ? keyBinding.isKeyDown() : SimulatedKeyInputManager.isKeyDown(keyCode);
    }
}
