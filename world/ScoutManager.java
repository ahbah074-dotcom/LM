package com.livemine.world;

import com.livemine.LiveMineMod;
import com.livemine.VillageManager;
import com.livemine.domain.VillageRecord;
import com.livemine.entity.LiveNPCEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Менеджер разведки территории (ТЗ 20.1 §28.1).
 *
 * Каждые 3–7 дней деревня отправляет разведчика (SCOUTING ≥ 3)
 * в радиусе 2000 блоков для обнаружения соседних деревень.
 *
 * При обнаружении деревни — записывает в общую память через
 * VillageData, лидеру передаётся доклад.
 *
 * Знания о других деревнях хранятся в Map<UUID, Set<UUID>>:
 *   knownVillages.get(observerVillage) = {known1, known2, ...}
 */
public final class ScoutManager {

    private static ScoutManager INSTANCE;

    public static final int SCOUT_RADIUS = 2000;
    public static final int MIN_INTERVAL_DAYS = 3;
    public static final int MAX_INTERVAL_DAYS = 7;

    private final Map<UUID, Set<UUID>> knownVillages = new HashMap<>();
    private final Map<UUID, Long> lastScoutDay = new HashMap<>();

    private ScoutManager() {}

    public static synchronized ScoutManager getInstance() {
        if (INSTANCE == null) INSTANCE = new ScoutManager();
        return INSTANCE;
    }

    // =========================================================================
    // Разведка
    // =========================================================================

    /**
     * Тик менеджера. Вызывается раз в игровой день.
     */
    public void tick(ServerLevel level) {
        long day = level.getDayTime() / 24000L;

        for (VillageRecord village : VillageManager.getInstance().getAllVillages()) {
            if (village.isAbandoned()) continue;

            long last = lastScoutDay.getOrDefault(village.id(), 0L);
            int interval = MIN_INTERVAL_DAYS
                + level.random.nextInt(MAX_INTERVAL_DAYS - MIN_INTERVAL_DAYS + 1);

            if (day - last >= interval) {
                lastScoutDay.put(village.id(), day);
                performScout(village, level);
            }
        }
    }

    /**
     * Выполняет разведку: ищет деревни в радиусе SCOUT_RADIUS.
     */
    private void performScout(VillageRecord observer, ServerLevel level) {
        BlockPos observerCenter = new BlockPos(
            observer.centerX(), observer.centerY(), observer.centerZ());

        Set<UUID> known = knownVillages.computeIfAbsent(observer.id(), k -> new HashSet<>());

        for (VillageRecord other : VillageManager.getInstance().getAllVillages()) {
            if (other.id().equals(observer.id())) continue;
            if (other.isAbandoned()) continue;
            if (known.contains(other.id())) continue;

            BlockPos otherCenter = new BlockPos(
                other.centerX(), other.centerY(), other.centerZ());

            double dist = Math.sqrt(observerCenter.distSqr(otherCenter));
            if (dist <= SCOUT_RADIUS) {
                known.add(other.id());
                LiveMineMod.LOGGER.info(
                    "Village {} discovered village {} (distance {} blocks)",
                    observer.name(), other.name(), (int) dist);
            }
        }
    }

    // =========================================================================
    // Запросы
    // =========================================================================

    public Set<UUID> getKnownVillages(UUID observerId) {
        return knownVillages.getOrDefault(observerId, java.util.Collections.emptySet());
    }

    public boolean knowsVillage(UUID observerId, UUID otherId) {
        return getKnownVillages(observerId).contains(otherId);
    }

    public int getKnownCount(UUID observerId) {
        return getKnownVillages(observerId).size();
    }

    public void clear() {
        knownVillages.clear();
        lastScoutDay.clear();
    }
}
