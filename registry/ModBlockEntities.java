package com.livemine.registry;

import com.livemine.LiveMineMod;
import com.livemine.blocks.CemeteryMarkerBlockEntity;
import com.livemine.blocks.CommunalFirePitBlockEntity;
import com.livemine.blocks.FarmPlotBlockEntity;
import com.livemine.blocks.InfirmaryBlockEntity;
import com.livemine.blocks.MagicTowerCoreBlockEntity;
import com.livemine.blocks.MarketStallBlockEntity;
import com.livemine.blocks.StorageBlockEntity;
import com.livemine.blocks.StorageWarehouseBlockEntity;
import com.livemine.blocks.TrophyDisplayBlockEntity;
import com.livemine.blocks.VillageCenterBlockEntity;
import com.livemine.blocks.WorkshopForgeBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Регистрация BlockEntity для функциональных блоков.
 */
public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, LiveMineMod.MOD_ID);

    // =========================================================================
    // BlockEntity регистрации
    // =========================================================================

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VillageCenterBlockEntity>> VILLAGE_CENTER_BE =
        BLOCK_ENTITIES.register("village_center_be", () ->
            BlockEntityType.Builder.of(
                VillageCenterBlockEntity::new,
                ModBlocks.VILLAGE_CENTER.get()
            ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CommunalFirePitBlockEntity>> COMMUNAL_FIRE_PIT_BE =
        BLOCK_ENTITIES.register("communal_fire_pit_be", () ->
            BlockEntityType.Builder.of(
                CommunalFirePitBlockEntity::new,
                ModBlocks.COMMUNAL_FIRE_PIT.get()
            ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StorageBlockEntity>> STORAGE_BE =
        BLOCK_ENTITIES.register("storage_be", () ->
            BlockEntityType.Builder.of(
                StorageBlockEntity::new,
                ModBlocks.STORAGE.get()
            ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StorageWarehouseBlockEntity>> STORAGE_WAREHOUSE_BE =
        BLOCK_ENTITIES.register("storage_warehouse_be", () ->
            BlockEntityType.Builder.of(
                StorageWarehouseBlockEntity::new,
                ModBlocks.STORAGE_WAREHOUSE.get()
            ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WorkshopForgeBlockEntity>> WORKSHOP_FORGE_BE =
        BLOCK_ENTITIES.register("workshop_forge_be", () ->
            BlockEntityType.Builder.of(
                WorkshopForgeBlockEntity::new,
                ModBlocks.WORKSHOP_FORGE.get()
            ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FarmPlotBlockEntity>> FARM_PLOT_BE =
        BLOCK_ENTITIES.register("farm_plot_be", () ->
            BlockEntityType.Builder.of(
                FarmPlotBlockEntity::new,
                ModBlocks.FARM_PLOT.get()
            ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InfirmaryBlockEntity>> INFIRMARY_BE =
        BLOCK_ENTITIES.register("infirmary_be", () ->
            BlockEntityType.Builder.of(
                InfirmaryBlockEntity::new,
                ModBlocks.INFIRMARY.get()
            ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MagicTowerCoreBlockEntity>> MAGIC_TOWER_CORE_BE =
        BLOCK_ENTITIES.register("magic_tower_core_be", () ->
            BlockEntityType.Builder.of(
                MagicTowerCoreBlockEntity::new,
                ModBlocks.MAGIC_TOWER_CORE.get()
            ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MarketStallBlockEntity>> MARKET_STALL_BE =
        BLOCK_ENTITIES.register("market_stall_be", () ->
            BlockEntityType.Builder.of(
                MarketStallBlockEntity::new,
                ModBlocks.MARKET_STALL.get()
            ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CemeteryMarkerBlockEntity>> CEMETERY_MARKER_BE =
        BLOCK_ENTITIES.register("cemetery_marker_be", () ->
            BlockEntityType.Builder.of(
                CemeteryMarkerBlockEntity::new,
                ModBlocks.CEMETERY_MARKER.get()
            ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TrophyDisplayBlockEntity>> TROPHY_DISPLAY_BE =
        BLOCK_ENTITIES.register("trophy_display_be", () ->
            BlockEntityType.Builder.of(
                TrophyDisplayBlockEntity::new,
                ModBlocks.TROPHY_DISPLAY.get()
            ).build(null));

    private ModBlockEntities() {}
}
