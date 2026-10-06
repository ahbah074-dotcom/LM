package com.livemine.registry;

import com.livemine.LiveMineMod;
import com.livemine.blocks.CemeteryMarkerBlock;
import com.livemine.blocks.CommunalFirePitBlock;
import com.livemine.blocks.FarmPlotBlock;
import com.livemine.blocks.InfirmaryBlock;
import com.livemine.blocks.MagicTowerCoreBlock;
import com.livemine.blocks.MarketStallBlock;
import com.livemine.blocks.StorageBlock;
import com.livemine.blocks.StorageWarehouseBlock;
import com.livemine.blocks.TrophyDisplayBlock;
import com.livemine.blocks.VillageCenterBlock;
import com.livemine.blocks.WorkshopForgeBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Регистрация блоков мода.
 *
 * 11 функциональных блоков + 3 декоративных.
 * Для каждого блока автоматически создаётся BlockItem.
 */
public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
        DeferredRegister.createBlocks(LiveMineMod.MOD_ID);

    public static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(LiveMineMod.MOD_ID);

    // =========================================================================
    // Функциональные блоки (BlockEntity)
    // =========================================================================

    public static final DeferredBlock<VillageCenterBlock> VILLAGE_CENTER =
        registerBlockWithItem("village_center", VillageCenterBlock::new,
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(2.5f)
                .noOcclusion());

    public static final DeferredBlock<CommunalFirePitBlock> COMMUNAL_FIRE_PIT =
        registerBlockWithItem("communal_fire_pit", CommunalFirePitBlock::new,
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(1.5f)
                .lightLevel(s -> 15)
                .noOcclusion());

    public static final DeferredBlock<StorageBlock> STORAGE =
        registerBlockWithItem("storage", StorageBlock::new,
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(2.0f));

    public static final DeferredBlock<StorageWarehouseBlock> STORAGE_WAREHOUSE =
        registerBlockWithItem("storage_warehouse", StorageWarehouseBlock::new,
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(3.0f));

    public static final DeferredBlock<WorkshopForgeBlock> WORKSHOP_FORGE =
        registerBlockWithItem("workshop_forge", WorkshopForgeBlock::new,
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(3.0f)
                .requiresCorrectToolForDrops());

    public static final DeferredBlock<FarmPlotBlock> FARM_PLOT =
        registerBlockWithItem("farm_plot", FarmPlotBlock::new,
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.GRASS)
                .strength(0.5f)
                .noOcclusion());

    public static final DeferredBlock<InfirmaryBlock> INFIRMARY =
        registerBlockWithItem("infirmary", InfirmaryBlock::new,
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.QUARTZ)
                .strength(2.5f));

    public static final DeferredBlock<MagicTowerCoreBlock> MAGIC_TOWER_CORE =
        registerBlockWithItem("magic_tower_core", MagicTowerCoreBlock::new,
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE)
                .strength(3.5f)
                .lightLevel(s -> 12)
                .noOcclusion());

    public static final DeferredBlock<MarketStallBlock> MARKET_STALL =
        registerBlockWithItem("market_stall", MarketStallBlock::new,
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(2.0f)
                .noOcclusion());

    public static final DeferredBlock<CemeteryMarkerBlock> CEMETERY_MARKER =
        registerBlockWithItem("cemetery_marker", CemeteryMarkerBlock::new,
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(2.5f)
                .requiresCorrectToolForDrops());

    public static final DeferredBlock<TrophyDisplayBlock> TROPHY_DISPLAY =
        registerBlockWithItem("trophy_display", TrophyDisplayBlock::new,
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.GOLD)
                .strength(2.0f)
                .noOcclusion());

    // =========================================================================
    // Декоративные блоки (без BlockEntity)
    // =========================================================================

    public static final DeferredBlock<Block> VILLAGE_ROAD =
        registerBlockWithItem("village_road", Block::new,
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(1.0f));

    public static final DeferredBlock<Block> VILLAGE_BRIDGE =
        registerBlockWithItem("village_bridge", Block::new,
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(2.0f));

    public static final DeferredBlock<Block> VILLAGE_FENCE =
        registerBlockWithItem("village_fence", Block::new,
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(1.5f)
                .noOcclusion());

    private ModBlocks() {}

    // =========================================================================
    // Утилита регистрации
    // =========================================================================

    /**
     * Регистрирует блок и автоматически создаёт BlockItem.
     */
    private static <B extends Block> DeferredBlock<B> registerBlockWithItem(
            String name,
            java.util.function.Function<BlockBehaviour.Properties, B> factory,
            BlockBehaviour.Properties props) {

        DeferredBlock<B> block = BLOCKS.register(name, () -> factory.apply(props));
        ITEMS.registerSimpleBlockItem(name, block);
        return block;
    }
}
