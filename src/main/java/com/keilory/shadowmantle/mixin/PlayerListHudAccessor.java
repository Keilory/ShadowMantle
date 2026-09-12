package com.keilory.shadowmantle.mixin;

import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(PlayerListHud.class)
public interface PlayerListHudAccessor {
    @Accessor("header")
    Text fakePixel$getHeader();

    @Accessor("footer")
    Text fakePixel$getFooter();

    @Invoker("collectPlayerEntries")
    List<PlayerListEntry> fakePixel$collectPlayerEntries();
}