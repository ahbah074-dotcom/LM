package com.livemine.blocks;

import com.livemine.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BlockEntity общего костра.
 */
public class CommunalFirePitBlockEntity extends BlockEntity {

    private static final int TICK_INTERVAL = 20;
    private static final int MAX_FUEL = 2400;

    private int fuel = 600;
    private int tickCounter;

    public CommunalFirePitBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COMMUNAL_FIRE_PIT_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CommunalFirePitBlockEntity entity) {
        if (level.isClientSide()) return;

        entity.tickCounter++;
        if (entity.tickCounter < TICK_INTERVAL) return;
        entity.tickCounter = 0;

        if (entity.fuel > 0) {
            entity.fuel--;
            if (state.hasProperty(CommunalFirePitBlock.LIT)
                && !state.getValue(CommunalFirePitBlock.LIT)) {
                level.setBlock(pos, state.setValue(CommunalFirePitBlock.LIT, true), 3);
            }
        } else {
            if (state.hasProperty(CommunalFirePitBlock.LIT)
                && state.getValue(CommunalFirePitBlock.LIT)) {
                level.setBlock(pos, state.setValue(CommunalFirePitBlock.LIT, false), 3);
            }
        }
        entity.setChanged();
    }

    public int getFuel() { return fuel; }
    public boolean isActive() { return fuel > 0; }

    public void addFuel(int amount) {
        this.fuel = Math.min(MAX_FUEL, Math.max(0, this.fuel + amount));
        setChanged();
    }

    public void setFuel(int amount) {
        this.fuel = Math.max(0, Math.min(MAX_FUEL, amount));
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("fuel", fuel);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("fuel")) fuel = tag.getInt("fuel");
    }
}
