package com.livemine.blocks;

import com.livemine.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * BlockEntity фермерского участка — 9 слотов культур.
 */
public class FarmPlotBlockEntity extends BlockEntity {

    public static final int MAX_SLOTS = 9;
    public static final double GROWTH_PER_TICK = 2.5;

    private static final int TICK_INTERVAL = 100;

    public static final class CropEntry {
        public int slot;
        public String cropType;
        public double growth;
        public boolean harvested;

        public CropEntry(int slot, String type) {
            this.slot = slot;
            this.cropType = type != null ? type : "wheat";
            this.growth = 0;
            this.harvested = false;
        }

        public boolean isRipe() { return growth >= 100 && !harvested; }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("slot", slot);
            tag.putString("type", cropType);
            tag.putDouble("growth", growth);
            tag.putBoolean("harvested", harvested);
            return tag;
        }

        public static CropEntry load(CompoundTag tag) {
            CropEntry entry = new CropEntry(tag.getInt("slot"), tag.getString("type"));
            entry.growth = tag.getDouble("growth");
            entry.harvested = tag.getBoolean("harvested");
            return entry;
        }
    }

    private final List<CropEntry> crops = new ArrayList<>();
    private double totalYield;
    private int tickCounter;

    public FarmPlotBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FARM_PLOT_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, FarmPlotBlockEntity entity) {
        if (level.isClientSide()) return;

        entity.tickCounter++;
        if (entity.tickCounter < TICK_INTERVAL) return;
        entity.tickCounter = 0;

        boolean changed = false;
        for (CropEntry crop : entity.crops) {
            if (!crop.harvested && crop.growth < 100) {
                crop.growth = Math.min(100, crop.growth + GROWTH_PER_TICK);
                changed = true;
            }
        }
        if (changed) entity.setChanged();
    }

    public boolean plantCrop(String type) {
        if (crops.size() >= MAX_SLOTS) return false;
        crops.add(new CropEntry(crops.size(), type));
        setChanged();
        return true;
    }

    public List<ItemStack> harvestReadyCrops() {
        List<ItemStack> drops = new ArrayList<>();
        var it = crops.iterator();
        int harvested = 0;

        while (it.hasNext()) {
            CropEntry crop = it.next();
            if (crop.isRipe()) {
                drops.addAll(getCropDrop(crop.cropType));
                totalYield += 1;
                harvested++;
                it.remove();
            }
        }

        if (harvested > 0) setChanged();
        return drops;
    }

    public boolean hasRipeCrops() {
        for (CropEntry crop : crops) if (crop.isRipe()) return true;
        return false;
    }

    public int findRipeIndex() {
        for (int i = 0; i < crops.size(); i++) {
            if (crops.get(i).isRipe()) return i;
        }
        return -1;
    }

    public List<CropEntry> getCrops() { return crops; }
    public int getCropCount() { return crops.size(); }
    public double getTotalYield() { return totalYield; }
    public boolean hasSpace() { return crops.size() < MAX_SLOTS; }

    private List<ItemStack> getCropDrop(String type) {
        List<ItemStack> drops = new ArrayList<>();
        if (type == null) return drops;

        switch (type.toLowerCase()) {
            case "wheat" -> {
                drops.add(new ItemStack(Items.WHEAT, 1));
                drops.add(new ItemStack(Items.WHEAT_SEEDS, 1));
            }
            case "carrot" -> drops.add(new ItemStack(Items.CARROT, 1));
            case "potato" -> drops.add(new ItemStack(Items.POTATO, 1));
            case "beetroot" -> {
                drops.add(new ItemStack(Items.BEETROOT, 1));
                drops.add(new ItemStack(Items.BEETROOT_SEEDS, 1));
            }
            case "melon" -> drops.add(new ItemStack(Items.MELON_SLICE, 1));
            case "pumpkin" -> drops.add(new ItemStack(Items.PUMPKIN, 1));
            default -> drops.add(new ItemStack(Items.WHEAT, 1));
        }
        return drops;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        ListTag list = new ListTag();
        for (CropEntry crop : crops) list.add(crop.save());
        tag.put("crops", list);
        tag.putDouble("yield", totalYield);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        crops.clear();

        ListTag list = tag.getList("crops", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            crops.add(CropEntry.load(list.getCompound(i)));
        }
        totalYield = tag.getDouble("yield");
    }
}
