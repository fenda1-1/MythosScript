/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.mythos.mythosScriptMod.shadowbaritone.launch.mixins;

import com.mythos.mythosScriptMod.shadowbaritone.api.BaritoneAPI;
import com.mythos.mythosScriptMod.otherfeatures.handler.movement.WallClimbFeatureHandler;
import com.mythos.mythosScriptMod.otherfeatures.handler.movement.MovementFeatureManager;
import com.mythos.mythosScriptMod.shadowbaritone.api.IBaritone;
import com.mythos.mythosScriptMod.shadowbaritone.api.event.events.RotationMoveEvent;
import com.mythos.mythosScriptMod.shadowbaritone.api.utils.input.Input;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

import static org.objectweb.asm.Opcodes.GETFIELD;

/**
 * @author Brady
 * @since 9/10/2018
 */
@Mixin(EntityLivingBase.class)
public abstract class MixinEntityLivingBase extends Entity {

    @Inject(method = "canBePushed", at = @At("HEAD"), cancellable = true, require = 1)
    private void mythos$excludePlayerFromPushCandidates(CallbackInfoReturnable<Boolean> ci) {
        if (MovementFeatureManager.hasNoCollision((Entity) (Object) this)) {
            ci.setReturnValue(false);
        }
    }

    @Inject(method = "collideWithNearbyEntities", at = @At("HEAD"), cancellable = true, require = 1)
    private void mythos$skipPlayerEntityCollisions(CallbackInfo ci) {
        if (MovementFeatureManager.hasNoCollision((Entity) (Object) this)) {
            ci.cancel();
        }
    }

    @Unique private double mythos$beforeKnockbackX;
    @Unique private double mythos$beforeKnockbackY;
    @Unique private double mythos$beforeKnockbackZ;

    @Inject(method = "knockBack", at = @At("HEAD"), cancellable = true, require = 1)
    private void mythos$beforeKnockback(Entity attacker, float strength, double x, double z, CallbackInfo ci) {
        if ((Object) this != net.minecraft.client.Minecraft.getMinecraft().player) return;
        mythos$beforeKnockbackX = motionX;
        mythos$beforeKnockbackY = motionY;
        mythos$beforeKnockbackZ = motionZ;
        if (MovementFeatureManager.getKnockbackResistance() >= 1.0D) ci.cancel();
    }

    @Inject(method = "knockBack", at = @At("RETURN"), require = 1)
    private void mythos$afterKnockback(Entity attacker, float strength, double x, double z, CallbackInfo ci) {
        if ((Object) this != net.minecraft.client.Minecraft.getMinecraft().player) return;
        double resistance = MovementFeatureManager.getKnockbackResistance();
        motionX += (mythos$beforeKnockbackX - motionX) * resistance;
        motionY += (mythos$beforeKnockbackY - motionY) * resistance;
        motionZ += (mythos$beforeKnockbackZ - motionZ) * resistance;
    }

    /**
     * Event called to override the movement direction when jumping
     */
    @Unique
    private RotationMoveEvent mythosScript$jumpRotationEvent;

    @Unique
    private RotationMoveEvent mythosScript$elytraRotationEvent;

    private MixinEntityLivingBase(World worldIn) {
        super(worldIn);
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void mythosScript$surfaceTravel(float strafe, float vertical, float forward, CallbackInfo ci) {
        if ((Object) this instanceof EntityPlayerSP
                && WallClimbFeatureHandler.travel((EntityPlayerSP) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "jump", at = @At("HEAD"))
    private void preMoveRelative(CallbackInfo ci) {
        Optional<IBaritone> baritone = this.getBaritone();
        if (!baritone.isPresent()) {
            this.mythosScript$jumpRotationEvent = null;
            return;
        }
        if (!shouldOverrideMovementRotation(baritone.get())) {
            this.mythosScript$jumpRotationEvent = null;
            return;
        }
        this.mythosScript$jumpRotationEvent = new RotationMoveEvent(RotationMoveEvent.Type.JUMP, this.rotationYaw,
                this.rotationPitch);
        baritone.get().getGameEventHandler().onPlayerRotationMove(this.mythosScript$jumpRotationEvent);
    }

    @Redirect(method = "jump", at = @At(value = "FIELD", opcode = GETFIELD, target = "net/minecraft/entity/EntityLivingBase.rotationYaw:F"))
    private float overrideYaw(EntityLivingBase self) {
        if (self instanceof EntityPlayerSP
                && this.mythosScript$jumpRotationEvent != null
                && BaritoneAPI.getProvider().getBaritoneForPlayer((EntityPlayerSP) (Object) this) != null) {
            return this.mythosScript$jumpRotationEvent.getYaw();
        }
        return self.rotationYaw;
    }

    @Inject(method = "travel", at = @At(value = "INVOKE", target = "net/minecraft/entity/EntityLivingBase.getLookVec()Lnet/minecraft/util/math/Vec3d;"))
    private void onPreElytraMove(float strafe, float vertical, float forward, CallbackInfo ci) {
        Optional<IBaritone> baritone = this.getBaritone();
        if (!baritone.isPresent()) {
            this.mythosScript$elytraRotationEvent = null;
            return;
        }
        this.mythosScript$elytraRotationEvent = new RotationMoveEvent(RotationMoveEvent.Type.MOTION_UPDATE,
                this.rotationYaw, this.rotationPitch);
        baritone.get().getGameEventHandler().onPlayerRotationMove(this.mythosScript$elytraRotationEvent);
        this.rotationYaw = this.mythosScript$elytraRotationEvent.getYaw();
        this.rotationPitch = this.mythosScript$elytraRotationEvent.getPitch();
    }

    @Inject(method = "travel", at = @At(value = "INVOKE", target = "net/minecraft/entity/EntityLivingBase.move(Lnet/minecraft/entity/MoverType;DDD)V", shift = At.Shift.AFTER))
    private void onPostElytraMove(float strafe, float vertical, float forward, CallbackInfo ci) {
        if (this.mythosScript$elytraRotationEvent != null) {
            this.rotationYaw = this.mythosScript$elytraRotationEvent.getOriginal().getYaw();
            this.rotationPitch = this.mythosScript$elytraRotationEvent.getOriginal().getPitch();
            this.mythosScript$elytraRotationEvent = null;
        }
    }

    @Redirect(method = "travel", at = @At(value = "INVOKE", target = "net/minecraft/entity/EntityLivingBase.moveRelative(FFFF)V"))
    private void onMoveRelative(EntityLivingBase self, float strafe, float up, float forward, float friction) {
        Optional<IBaritone> baritone = this.getBaritone();
        if (!baritone.isPresent()) {
            // If a shadow is used here it breaks on Forge
            this.moveRelative(strafe, up, forward, friction);
            return;
        }
        if (!shouldOverrideMovementRotation(baritone.get())) {
            this.moveRelative(strafe, up, forward, friction);
            return;
        }

        RotationMoveEvent event = new RotationMoveEvent(RotationMoveEvent.Type.MOTION_UPDATE, this.rotationYaw,
                this.rotationPitch);
        baritone.get().getGameEventHandler().onPlayerRotationMove(event);

        this.rotationYaw = event.getYaw();
        this.rotationPitch = event.getPitch();

        this.moveRelative(strafe, up, forward, friction);

        this.rotationYaw = event.getOriginal().getYaw();
        this.rotationPitch = event.getOriginal().getPitch();
    }

    @Unique
    private boolean shouldOverrideMovementRotation(IBaritone baritone) {
        if (baritone == null || baritone.getInputOverrideHandler() == null || baritone.getLookBehavior() == null) {
            return false;
        }
        if (!isBaritoneActivelyControllingMovement(baritone)) {
            return false;
        }
        // Free-look uses a camera-relative input vector calculated in
        // PlayerMovementInput. Applying another yaw here would rotate that vector
        // a second time and make movement depend on the visual camera again.
        if (baritone.getLookBehavior().shouldDecoupleMovementFromVisualYaw()) {
            return false;
        }
        return baritone.getInputOverrideHandler().isInputForcedDown(Input.MOVE_FORWARD)
                || baritone.getInputOverrideHandler().isInputForcedDown(Input.MOVE_BACK)
                || baritone.getInputOverrideHandler().isInputForcedDown(Input.MOVE_LEFT)
                || baritone.getInputOverrideHandler().isInputForcedDown(Input.MOVE_RIGHT)
                || baritone.getInputOverrideHandler().isInputForcedDown(Input.JUMP)
                || baritone.getInputOverrideHandler().isInputForcedDown(Input.SNEAK);
    }

    @Unique
    private boolean isBaritoneActivelyControllingMovement(IBaritone baritone) {
        if (baritone.getPathingBehavior() != null
                && baritone.getPathingBehavior().isPathing()
                && baritone.getPathingBehavior().getCurrent() != null) {
            return true;
        }
        return baritone.getElytraProcess() != null && baritone.getElytraProcess().isActive();
    }

    @Unique
    private Optional<IBaritone> getBaritone() {
        // noinspection ConstantConditions
        if (EntityPlayerSP.class.isInstance(this)) {
            return Optional.ofNullable(BaritoneAPI.getProvider().getBaritoneForPlayer((EntityPlayerSP) (Object) this));
        } else {
            return Optional.empty();
        }
    }
}
