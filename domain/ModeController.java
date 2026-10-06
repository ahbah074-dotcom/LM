package com.livemine.domain;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Контроллер переключения между режимами симуляции.
 *
 * ACTIVE → VIRTUAL: освобождает навигационную аренду, фиксирует результаты.
 * VIRTUAL → ACTIVE: восстанавливает задачу из NBT.
 * VIRTUAL → DORMANT: замораживает данные.
 * DORMANT → VIRTUAL: возобновляет при приближении игрока.
 *
 * Работает на уровне VILLAGE (не NPC).
 */
public final class ModeController {

    public static final int ACTIVE_RADIUS = 64;
    public static final int VIRTUAL_RADIUS = 128;
    public static final long RECHECK_INTERVAL = 100L;
    public static final long DORMANT_THRESHOLD = 72_000L;

    private static final Map<String, VillageModeState> villageStates = new ConcurrentHashMap<>();

    private ModeController() {}

    private static final class VillageModeState {
        SimulationMode mode = SimulationMode.ACTIVE;
        long lastActiveTick = 0L;
        BlockPos center = BlockPos.ZERO;
    }

    // =========================================================================
    // Определение режима
    // =========================================================================

    public static SimulationMode determineMode(MinecraftServer server, String villageId,
                                                BlockPos center, long currentTick) {
        VillageModeState state = villageStates.computeIfAbsent(villageId, k -> new VillageModeState());
        state.center = center;

        double minDistSq = Double.MAX_VALUE;
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer p : level.players()) {
                double d = p.blockPosition().distSqr(center);
                if (d < minDistSq) minDistSq = d;
            }
        }

        if (minDistSq == Double.MAX_VALUE) {
            if (currentTick - state.lastActiveTick > DORMANT_THRESHOLD) {
                state.mode = SimulationMode.DORMANT;
            }
            return state.mode;
        }

        double dist = Math.sqrt(minDistSq);

        if (dist <= ACTIVE_RADIUS) {
            state.lastActiveTick = currentTick;
            state.mode = SimulationMode.ACTIVE;
        } else if (dist <= VIRTUAL_RADIUS) {
            state.lastActiveTick = currentTick;
            state.mode = SimulationMode.VIRTUAL;
        } else {
            if (currentTick - state.lastActiveTick > DORMANT_THRESHOLD) {
                state.mode = SimulationMode.DORMANT;
            } else {
                state.mode = SimulationMode.VIRTUAL;
            }
        }

        return state.mode;
    }

    // =========================================================================
    // Установка режима
    // =========================================================================

    public static void setMode(String villageId, SimulationMode mode) {
        VillageModeState state = villageStates.computeIfAbsent(villageId, k -> new VillageModeState());
        state.mode = mode;
    }

    public static SimulationMode getMode(String villageId) {
        VillageModeState state = villageStates.get(villageId);
        return state != null ? state.mode : SimulationMode.ACTIVE;
    }

    // =========================================================================
    // Переключение (с освобождением ресурсов)
    // =========================================================================

    public static void switchMode(com.livemine.ai.AgentBrain brain, SimulationMode newMode) {
        if (brain == null || newMode == null) return;

        SimulationMode oldMode = brain.mode();
        if (oldMode == newMode) return;

        if (oldMode.isActive() && !newMode.isActive()) {
            if (brain.currentLeaseId() != null) {
                NavigationArbiter.getInstance().release(brain.currentLeaseId());
                brain.clearLease();
            }
            brain.persistTaskState();
        }

        if (!oldMode.isActive() && newMode.isActive()) {
            brain.restoreTaskState();
        }

        brain.setMode(newMode);
    }

    // =========================================================================
    // Диагностика
    // =========================================================================

    public static int getActiveVillages() {
        int count = 0;
        for (VillageModeState s : villageStates.values()) {
            if (s.mode.isActive()) count++;
        }
        return count;
    }

    public static Map<String, SimulationMode> getAllModes() {
        Map<String, SimulationMode> map = new HashMap<>();
        for (var e : villageStates.entrySet()) {
            map.put(e.getKey(), e.getValue().mode);
        }
        return map;
    }

    public static void clear() { villageStates.clear(); }
}