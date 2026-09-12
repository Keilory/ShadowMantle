package com.keilory.shadowmantle.mixin;

import com.mojang.authlib.properties.Property;
import com.mojang.authlib.yggdrasil.YggdrasilServicesKeyInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Base64;

@Mixin(value = YggdrasilServicesKeyInfo.class, remap = false)
public final class YggdrasilServicesKeyInfoMixin {
    @Inject(method = "validateProperty", at = @At("HEAD"), cancellable = true, remap = false)
    private void shadowmantle$rejectMalformedSignature(Property property, CallbackInfoReturnable<Boolean> cir) {
        if (property == null || property.signature() == null) return;
        try {
            Base64.getDecoder().decode(property.signature());
        } catch (IllegalArgumentException ignored) {
            cir.setReturnValue(false);
        }
    }
}
