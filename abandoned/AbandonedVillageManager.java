package com.livemine.abandoned;

import com.livemine.LiveMineMod;
import com.livemine.LiveMineSavedData;
import com.livemine.VillageData;
import com.livemine.blocks.CommunalFirePitBlock;
import com.livemine.blocks.FarmPlotBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер заброшенных деревень.
 *
 * v2.0: grace-period 3 игровых дня с момента основания — свежая деревня
 * с 0 NPC не считается заброшенной.
 */
public final class AbandonedVillageManager {

    private static final ConcurrentHashMap<String, AbandonedVillage> abandonedVillages = new ConcurrentHashMap<>();

    /** Сколько игровых дней после основания деревня не может стать заброшенной. */
    public static final long GRACE_PERIOD_DAYS = 3;

    private AbandonedVillageManager() {}

    public static final class AbandonedVillage {
        public String villageId;
        public BlockPos center;
        public int formerPopulation;
        public long abandonedTick;
        public int decayStage;

        public AbandonedVillage(String id, BlockPos center, int pop, long tick) {
            this.villageId = id;
            this.center = center;
            this.formerPopulation = pop;
            this.abandonedTick = tick;
            this.decayStage = 0;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", villageId);
            tag.putInt("x", center.getX());
            tag.putInt("y", center.getY());
            tag.putInt("z", center.getZ());
            tag.putInt("pop", formerPopulation);
            tag.putLong("tick", abandonedTick);
            tag.putInt("decay", decayStage);
            return tag;
        }

        public static AbandonedVillage load(CompoundTag tag) {
            AbandonedVillage v = new AbandonedVillage(
                tag.getString("id"),
                new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")),
                tag.getInt("pop"),
                tag.getLong("tick")
            );
            v.decayStage = tag.getInt("decay");
            return v;
        }
    }

    public static void markAbandoned(String villageId, BlockPos center, int pop, ServerLevel level) {
        AbandonedVillage v = new AbandonedVillage(villageId, center, pop, level.getGameTime());
        abandonedVillages.put(villageId, v);
        LiveMineSavedData.get(level).saveAbandonedVillage(villageId, v.save());
        LiveMineMod.LOGGER.info("Village {} marked as abandoned", villageId);
    }

    public static void markRepopulated(String villageId, ServerLevel level) {
        AbandonedVillage v = abandonedVillages.remove(villageId);
        if (v != null) {
            LiveMineSavedData.get(level).removeAbandonedVillage(villageId);
            LiveMineMod.LOGGER.info("Village {} repopulated", villageId);
        }
    }

    /**
     * v2.0: не помечаем деревню заброшенной в течение GRACE_PERIOD_DAYS
     * с момента её основания.
     */
    public static void checkAbandonedVillages(ServerLevel level) {
        LiveMineSavedData data = LiveMineSavedData.get(level);
        Map<UUID, CompoundTag> allNpcData = data.getAllNpcData();

        // Считаем население
        Map<String, Integer> populations = new ConcurrentHashMap<>();
        for (CompoundTag tag : allNpcData.values()) {
            if (tag.getBoolean("dead")) continue;
            String vid = tag.getString("village_id");
            if (!vid.isEmpty()) {
                populations.merge(vid, 1, Integer::sum);
            }
        }

        long currentDay = level.getDayTime() / 24000L;

        for (var record : com.livemine.VillageManager.getInstance().getAllVillages()) {
            String vid = record.rawId();
            int pop = populations.getOrDefault(vid, 0);

            // Grace-period: деревня существует меньше GRACE_PERIOD_DAYS.
            long ageDays = currentDay - record.foundedDay();
            if (ageDays < GRACE_PERIOD_DAYS) {
                continue;
            }

            if (pop == 0 && !record.isAbandoned()) {
                BlockPos center = new BlockPos(record.centerX(), record.centerY(), record.centerZ());
                markAbandoned(vid, center, 0, level);
                record.setAbandoned(true);
            } else if (pop > 0 && record.isAbandoned()) {
                markRepopulated(vid, level);
                record.setAbandoned(false);
            }
        }
    }

    public static void tick(ServerLevel level) {
        long now = level.getGameTime();

        for (AbandonedVillage v : abandonedVillages.values()) {
            long elapsed = now - v.abandonedTick;

            int newStage;
            if (elapsed > 24000L * 7)       newStage = 3;
            else if (elapsed > 24000L * 3)  newStage = 2;
            else if (elapsed > 24000L)      newStage = 1;
            else                             newStage = 0;

            if (newStage != v.decayStage) {
                v.decayStage = newStage;
                applyDecayStage(level, v);
                LiveMineSavedData.get(level).saveAbandonedVillage(v.villageId, v.save());
                LiveMineMod.LOGGER.info("Village {} decay stage -> {}", v.villageId, newStage);
            }
        }
    }

    private static void applyDecayStage(ServerLevel level, AbandonedVillage v) {
        VillageData vd = new VillageData(v.villageId, level);
        if (!vd.exists()) return;

        switch (v.decayStage) {
            case 1 -> {
                for (BlockPos pos : vd.getStructures(FarmPlotBlock.class)) {
                    level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                }
            }
            case 2 -> {
                for (BlockPos pos : vd.getStructures(CommunalFirePitBlock.class)) {
                    level.setBlock(pos, Blocks.COBBLESTONE.defaultBlockState(), 3);
                }
            }
            case 3 -> vd.setAbandoned(true);
        }
    }

    public static boolean isAbandoned(String villageId) {
        return abandonedVillages.containsKey(villageId);
    }

    public static int getDecayStage(String villageId) {
        AbandonedVillage v = abandonedVillages.get(villageId);
        return v != null ? v.decayStage : 0;
    }

    public static Collection<AbandonedVillage> getAll() {
        return abandonedVillages.values();
    }

    public static int getCount() { return abandonedVillages.size(); }

    public static void clear() { abandonedVillages.clear(); }

    public static void saveTo(LiveMineSavedData data) {
        for (AbandonedVillage v : abandonedVillages.values()) {
            data.saveAbandonedVillage(v.villageId, v.save());
        }
    }

    public static void loadFrom(ServerLevel level) {
        LiveMineMod.LOGGER.debug("AbandonedVillageManager loaded from SavedData");
    }
}