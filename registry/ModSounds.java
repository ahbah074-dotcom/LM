package com.livemine.registry;

import com.livemine.LiveMineMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Регистрация звуков мода.
 *
 * SoundEvent создаётся через createVariableRangeEvent — играет с позиции.
 * Файлы .ogg лежат в assets/livemine/sounds/<name>.ogg.
 * Описание — в assets/livemine/sounds.json.
 */
public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
        DeferredRegister.create(Registries.SOUND_EVENT, LiveMineMod.MOD_ID);

    // =========================================================================
    // Р—РІСѓРєРё NPC
    // =========================================================================

    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_WORK = register("npc_work");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_REST = register("npc_rest");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_EAT = register("npc_eat");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_SOCIAL = register("npc_social");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_HURT = register("npc_hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_DEATH = register("npc_death");
    public static final DeferredHolder<SoundEvent, SoundEvent> NPC_GREETING = register("npc_greeting");

    // =========================================================================
    // Звуки взаимодействия с блоками
    // =========================================================================

    public static final DeferredHolder<SoundEvent, SoundEvent> BLOCK_PLACE = register("block_place");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLOCK_BREAK = register("block_break");
    public static final DeferredHolder<SoundEvent, SoundEvent> BUILDING_HAMMER = register("building_hammer");

    // =========================================================================
    // Звуки событий
    // =========================================================================

    public static final DeferredHolder<SoundEvent, SoundEvent> VILLAGE_BELL = register("village_bell");
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLIDAY_CHEER = register("holiday_cheer");
    public static final DeferredHolder<SoundEvent, SoundEvent> MAGIC_CAST = register("magic_cast");
    public static final DeferredHolder<SoundEvent, SoundEvent> WEDDING_SOUND = register("wedding");
    public static final DeferredHolder<SoundEvent, SoundEvent> FESTIVAL_SOUND = register("festival");

    private ModSounds() {}

    // =========================================================================
    // Утилита
    // =========================================================================

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(LiveMineMod.MOD_ID, name)
        ));
    }
}
