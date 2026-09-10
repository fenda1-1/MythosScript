package com.zszl.zszlScriptMod.shadowbaritone.launch.mixins;

import com.zszl.zszlScriptMod.system.SimulatedKeyInputManager;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds per-client synthetic events to LWJGL's Keyboard.next() stream. */
@Mixin(value = Keyboard.class, remap = false)
public abstract class MixinKeyboard {
    @Inject(method = "next", at = @At("HEAD"), cancellable = true)
    private static void zszlScript$next(CallbackInfoReturnable<Boolean> callback) {
        if (SimulatedKeyInputManager.beginSyntheticKeyboardEvent()) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "getEventKey", at = @At("HEAD"), cancellable = true)
    private static void zszlScript$getEventKey(CallbackInfoReturnable<Integer> callback) {
        SimulatedKeyInputManager.SyntheticKeyboardEvent event =
                SimulatedKeyInputManager.getCurrentSyntheticKeyboardEvent();
        if (event != null) {
            callback.setReturnValue(event.getKeyCode());
        }
    }

    @Inject(method = "getEventCharacter", at = @At("HEAD"), cancellable = true)
    private static void zszlScript$getEventCharacter(CallbackInfoReturnable<Character> callback) {
        SimulatedKeyInputManager.SyntheticKeyboardEvent event =
                SimulatedKeyInputManager.getCurrentSyntheticKeyboardEvent();
        if (event != null) {
            callback.setReturnValue(event.getCharacter());
        }
    }

    @Inject(method = "getEventKeyState", at = @At("HEAD"), cancellable = true)
    private static void zszlScript$getEventKeyState(CallbackInfoReturnable<Boolean> callback) {
        SimulatedKeyInputManager.SyntheticKeyboardEvent event =
                SimulatedKeyInputManager.getCurrentSyntheticKeyboardEvent();
        if (event != null) {
            callback.setReturnValue(event.isKeyState());
        }
    }

    @Inject(method = "isRepeatEvent", at = @At("HEAD"), cancellable = true)
    private static void zszlScript$isRepeatEvent(CallbackInfoReturnable<Boolean> callback) {
        if (SimulatedKeyInputManager.getCurrentSyntheticKeyboardEvent() != null) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true)
    private static void zszlScript$isKeyDown(int keyCode, CallbackInfoReturnable<Boolean> callback) {
        Boolean override = SimulatedKeyInputManager.getSyntheticKeyDownOverride(keyCode);
        if (override != null) {
            callback.setReturnValue(override.booleanValue());
        }
    }
}
