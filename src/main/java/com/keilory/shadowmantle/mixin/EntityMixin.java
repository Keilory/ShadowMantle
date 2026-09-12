package com.keilory.shadowmantle.mixin;

import com.keilory.shadowmantle.core.feature.DungeonMobGlow;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
    private void shadowmantle$makeDungeonMobsGlow(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof LivingEntity livingEntity && DungeonMobGlow.shouldGlow(livingEntity)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
    private void shadowmantle$changeGlowColor(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this instanceof LivingEntity livingEntity && DungeonMobGlow.shouldGlow(livingEntity)) {
            cir.setReturnValue(DungeonMobGlow.getGlowColor(livingEntity));
        }
    }
}
