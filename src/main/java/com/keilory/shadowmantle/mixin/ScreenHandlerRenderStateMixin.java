package com.keilory.shadowmantle.mixin;

import com.keilory.shadowmantle.ShadowMantleClient;
import com.keilory.shadowmantle.core.event.SlotChangedEvent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerRenderStateMixin {
    @Inject(method = "setStackInSlot", at = @At("TAIL"))
    private void fakePixel$onSlotSync(int slot, int revision, ItemStack stack, CallbackInfo ci) {
        ScreenHandler handler = (ScreenHandler) (Object) this;
        ShadowMantleClient.core().events().post(new SlotChangedEvent(handler, slot, stack.copy()));
    }

    @Inject(method = "updateSlotStacks", at = @At("TAIL"))
    private void fakePixel$onFullSlotSync(int revision, List<ItemStack> stacks, ItemStack cursorStack, CallbackInfo ci) {
        ScreenHandler handler = (ScreenHandler) (Object) this;
        ShadowMantleClient.core().events().post(new SlotChangedEvent(handler, -1, cursorStack.copy()));
    }
}
