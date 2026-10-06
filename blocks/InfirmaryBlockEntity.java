package com.livemine.blocks;

import com.livemine.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BlockEntity лазарета: medicine + patientsHealed.
 */
public class InfirmaryBlockEntity extends BlockEntity {

    public static final int HEAL_RADIUS = 6;
    public static final int MAX_MEDICINE = 500;

    private static final int TICK_INTERVAL = 60;

    private int medicine = 100;
    private int patientsHealed;
    private int tickCounter;

    public InfirmaryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INFIRMARY_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, InfirmaryBlockEntity entity) {
        if (level.isClientSide()) return;

        entity.tickCounter++;
        if (entity.tickCounter < TICK_INTERVAL) return;
        entity.tickCounter = 0;

        if (entity.medicine > 0) {
            entity.medicine = Math.max(0, entity.medicine - 1);
        }
        entity.setChanged();
    }

    public int getMedicine() { return medicine; }
    public int getPatientsHealed() { return patientsHealed; }
    public boolean hasMedicine() { return medicine > 0; }

    public void addMedicine(int amount) {
        this.medicine = Math.min(MAX_MEDICINE, this.medicine + Math.max(0, amount));
        setChanged();
    }

    public boolean useMedicine(int amount) {
        if (medicine < amount) return false;
        medicine -= amount;
        patientsHealed++;
        setChanged();
        return true;
    }

    public int getCapacityForPatients() { return medicine; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("medicine", medicine);
        tag.putInt("healed", patientsHealed);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("medicine")) medicine = tag.getInt("medicine");
        if (tag.contains("healed")) patientsHealed = tag.getInt("healed");
    }
}
