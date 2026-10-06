package com.livemine.registry;

import com.livemine.LiveMineMod;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Регистрация предметов мода.
 *
 * v2.0: SpawnEggItem заменён на DeferredSpawnEggItem (не-deprecated).
 */
public final class ModItems {

    public static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(LiveMineMod.MOD_ID);

    // =========================================================================
    // Яйцо призыва NPC
    // =========================================================================

    public static final DeferredItem<DeferredSpawnEggItem> LIVE_NPC_SPAWN_EGG =
        ITEMS.register("live_npc_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.LIVE_NPC,
                0x4A7F9A, 0x3A5F7A,
                new Item.Properties()));

    // =========================================================================
    // Информационные предметы
    // =========================================================================

    public static final DeferredItem<Item> LIVEMINE_GUIDE = ITEMS.registerSimpleItem(
        "livemine_guide",
        new Item.Properties().stacksTo(1)
    );

    public static final DeferredItem<Item> EXPEDITION_COMPASS = ITEMS.registerSimpleItem(
        "expedition_compass",
        new Item.Properties().stacksTo(1)
    );

    public static final DeferredItem<Item> EXPEDITION_MAP = ITEMS.registerSimpleItem(
        "expedition_map",
        new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)
    );

    public static final DeferredItem<Item> VILLAGE_CHRONICLE = ITEMS.registerSimpleItem(
        "village_chronicle",
        new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)
    );

    public static final DeferredItem<Item> NPC_NAME_TAG = ITEMS.registerSimpleItem(
        "npc_name_tag",
        new Item.Properties().stacksTo(64)
    );

    public static final DeferredItem<Item> REPUTATION_BADGE = ITEMS.registerSimpleItem(
        "reputation_badge",
        new Item.Properties().stacksTo(1).rarity(Rarity.RARE)
    );

    // =========================================================================
    // Социальные / праздничные
    // =========================================================================

    public static final DeferredItem<Item> HEART_ITEM = ITEMS.registerSimpleItem(
        "heart_item",
        new Item.Properties().stacksTo(16)
    );

    public static final DeferredItem<Item> FIREWORK_CUSTOM = ITEMS.registerSimpleItem(
        "firework_custom",
        new Item.Properties().stacksTo(16)
    );

    // =========================================================================
    // Магические свитки
    // =========================================================================

    public static final DeferredItem<Item> SPELL_SCROLL_T1 = ITEMS.registerSimpleItem(
        "spell_scroll_t1",
        new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)
    );

    public static final DeferredItem<Item> SPELL_SCROLL_T2 = ITEMS.registerSimpleItem(
        "spell_scroll_t2",
        new Item.Properties().stacksTo(16).rarity(Rarity.RARE)
    );

    public static final DeferredItem<Item> SPELL_SCROLL_T3 = ITEMS.registerSimpleItem(
        "spell_scroll_t3",
        new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)
    );

    private ModItems() {}
}