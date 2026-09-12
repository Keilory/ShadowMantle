package com.keilory.shadowmantle.mixin;

import com.keilory.shadowmantle.bazaar.DungeonChestOverlay;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class HandledScreenRenderMixin {
    @Inject(method = "drawSlot", at = @At("HEAD"))
    private void fakePixel$drawSlotHighlight(DrawContext context, Slot slot, CallbackInfo ci) {
        HandledScreen<?> screen = (HandledScreen<?>) (Object) this;
        HandledScreenAccessor accessor = (HandledScreenAccessor) screen;
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(-accessor.fakePixel$getX(), -accessor.fakePixel$getY());
        DungeonChestOverlay.renderSlotHighlight(screen, context, slot);
        context.getMatrices().popMatrix();
    }
}
