package com.livemine.registry;

import com.livemine.LiveMineMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Креативная вкладка LiveMine.
 *
 * Содержит все предметы и блоки мода.
 * Название берётся из ключа "itemGroup.livemine".
 */
public final class ModCreativeTab {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LiveMineMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> LIVE_MINE_TAB =
        CREATIVE_MODE_TABS.register("livemine_tab", () ->
            CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.livemine"))
                .icon(() -> new ItemStack(ModItems.LIVEMINE_GUIDE.get()))
                .displayItems((params, output) -> {

                    // --- Блоки функциональные ---
                    output.accept(ModBlocks.VILLAGE_CENTER.get());
                    output.accept(ModBlocks.COMMUNAL_FIRE_PIT.get());
                    output.accept(ModBlocks.STORAGE.get());
                    output.accept(ModBlocks.STORAGE_WAREHOUSE.get());
                    output.accept(ModBlocks.WORKSHOP_FORGE.get());
                    output.accept(ModBlocks.FARM_PLOT.get());
                    output.accept(ModBlocks.INFIRMARY.get());
                    output.accept(ModBlocks.MAGIC_TOWER_CORE.get());
                    output.accept(ModBlocks.MARKET_STALL.get());
                    output.accept(ModBlocks.CEMETERY_MARKER.get());
                    output.accept(ModBlocks.TROPHY_DISPLAY.get());

                    // --- Блоки декоративные ---
                    output.accept(ModBlocks.VILLAGE_ROAD.get());
                    output.accept(ModBlocks.VILLAGE_BRIDGE.get());
                    output.accept(ModBlocks.VILLAGE_FENCE.get());

                    // --- Яйцо призыва ---
                    output.accept(ModItems.LIVE_NPC_SPAWN_EGG.get());

                    // --- Информационные предметы ---
                    output.accept(ModItems.LIVEMINE_GUIDE.get());
                    output.accept(ModItems.EXPEDITION_COMPASS.get());
                    output.accept(ModItems.EXPEDITION_MAP.get());
                    output.accept(ModItems.VILLAGE_CHRONICLE.get());
                    output.accept(ModItems.NPC_NAME_TAG.get());
                    output.accept(ModItems.REPUTATION_BADGE.get());

                    // --- Социальные / праздничные ---
                    output.accept(ModItems.HEART_ITEM.get());
                    output.accept(ModItems.FIREWORK_CUSTOM.get());

                    // --- Магические свитки ---
                    output.accept(ModItems.SPELL_SCROLL_T1.get());
                    output.accept(ModItems.SPELL_SCROLL_T2.get());
                    output.accept(ModItems.SPELL_SCROLL_T3.get());
                })
                .build());

    private ModCreativeTab() {}
}
