package com.livemine.ai;

import com.livemine.entity.LiveNPCEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Порционная обработка NPC — равномерное распределение нагрузки.
 *
 * Не обрабатывает всех NPC сразу (лаг-спайк), а берёт batchPerTick.
 *
 * Две очереди:
 *   activeQueue    — NPC с полной AI-обработкой (ACTIVE)
 *   simulatedQueue — NPC в SIMULATED (для SimulationEngine)
 *
 * Очереди пересобираются раз в 100 тиков (rebuildQueues).
 */
public final class TickDistributor {

    private static final List<UUID> activeQueue = new ArrayList<>();
    private static final List<UUID> simulatedQueue = new ArrayList<>();
    private static int activeIndex = 0;
    private static int simulatedIndex = 0;

    private TickDistributor() {}

    // =========================================================================
    // Перестройка очередей
    // =========================================================================

    public static void rebuildQueues() {
        activeQueue.clear();
        simulatedQueue.clear();

        for (Mob mob : NPCRegistry.getAll()) {
            UUID uuid = mob.getUUID();
            NPCActivityManager.ActivityState state = NPCRegistry.getActivityState(uuid);
            if (state == NPCActivityManager.ActivityState.ACTIVE) {
                activeQueue.add(uuid);
            } else if (state == NPCActivityManager.ActivityState.SIMULATED) {
                simulatedQueue.add(uuid);
            }
        }

        Collections.shuffle(activeQueue);
        Collections.shuffle(simulatedQueue);
        activeIndex = 0;
        simulatedIndex = 0;
    }

    // =========================================================================
    // Порции
    // =========================================================================

    public static List<Mob> getNextActiveBatch() {
        int size = AdaptiveTickRate.getActiveBatch();
        return nextBatch(activeQueue, size, true);
    }

    public static List<Mob> getNextSimulatedBatch() {
        int size = AdaptiveTickRate.getBatchPerTick();
        return nextBatch(simulatedQueue, size, false);
    }

    private static List<Mob> nextBatch(List<UUID> queue, int batchSize, boolean isActive) {
        if (queue.isEmpty()) return Collections.emptyList();

        List<Mob> result = new ArrayList<>(batchSize);
        int idx = isActive ? activeIndex : simulatedIndex;

        for (int i = 0; i < batchSize; i++) {
            if (queue.isEmpty()) break;
            if (idx >= queue.size()) idx = 0;

            UUID uuid = queue.get(idx);
            Mob mob = NPCRegistry.getNPC(uuid);
            if (mob != null && mob.isAlive() && !mob.isRemoved()) {
                result.add(mob);
            }
            idx++;
        }

        if (isActive) activeIndex = idx;
        else simulatedIndex = idx;

        return result;
    }

    // =========================================================================
    // РўРёРє ACTIVE
    // =========================================================================

    public static void tickActiveNPCs(ServerLevel level) {
        for (Mob mob : getNextActiveBatch()) {
            if (!(mob instanceof LiveNPCEntity npc)) continue;
            if (mob.level() != level) continue;
            if (!npc.isAlive()) continue;

            WorkGoalManager.processActiveNPC(level, npc);
        }
    }

    // =========================================================================
    // Диагностика
    // =========================================================================

    public static int getActiveQueueSize() { return activeQueue.size(); }
    public static int getSimulatedQueueSize() { return simulatedQueue.size(); }
}
