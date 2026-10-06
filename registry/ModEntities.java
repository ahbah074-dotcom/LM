package com.livemine.registry;

import com.livemine.LiveMineMod;
import com.livemine.entity.GraveStoneEntity;
import com.livemine.entity.LiveNPCEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Регистрация сущностей мода.
 *
 * 1.21.1: для GraveStoneEntity нужно явно указать generic <GraveStoneEntity>
 * — иначе Java не может выбрать конструктор (их два).
 */
public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(Registries.ENTITY_TYPE, LiveMineMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<LiveNPCEntity>> LIVE_NPC =
        ENTITY_TYPES.register("live_npc", () ->
            EntityType.Builder.of(LiveNPCEntity::new, MobCategory.CREATURE)
                .sized(0.6f, 1.95f)
                .eyeHeight(1.74f)
                .clientTrackingRange(10)
                .updateInterval(3)
                .build("live_npc"));

    public static final DeferredHolder<EntityType<?>, EntityType<GraveStoneEntity>> GRAVE_STONE =
        ENTITY_TYPES.register("grave_stone", () ->
            EntityType.Builder.<GraveStoneEntity>of(GraveStoneEntity::new, MobCategory.MISC)
                .sized(0.9f, 1.0f)
                .clientTrackingRange(8)
                .updateInterval(20)
                .fireImmune()
                .build("grave_stone"));

    private ModEntities() {}

    public static void onAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(LIVE_NPC.get(), LiveNPCEntity.createAttributes().build());
    }
}