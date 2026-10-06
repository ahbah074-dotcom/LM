package com.livemine.ai;

import com.livemine.LiveMineConfig;
import com.livemine.entity.LiveNPCEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Распределение NPC по состояниям активности.
 *
 * Логика (ТЗ 20.0 §3.1, ТЗ 20.1 §75):
 *   - ACTIVE — игрок в радиусе activeRadius, лимит maxActivePerPlayer на игрока
 *   - SIMULATED — игрок в радиусе simulatedRadius
 *   - DORMANT — игрок в радиусе dormantRadius, но давно не подходил
 *   - INACTIVE — дальше dormantRadius
 *
 * Вызывается раз в 20 тиков (1 сек) из NPCSimulationTickHandler.
 */
public final class NPCActivityManager {

    public enum ActivityState {
        ACTIVE, SIMULATED, DORMANT, INACTIVE
    }

    private static long lastUpdate = 0;
    private static final int UPDATE_INTERVAL = 20;

    private NPCActivityManager() {}

    // =========================================================================
    // Основной апдейт
    // =========================================================================

    public static void update(ServerLevel level, long currentTick) {
        if (currentTick - lastUpdate < UPDATE_INTERVAL) return;
        lastUpdate = currentTick;

        List<ServerPlayer> players = level.players();
        List<Mob> all = NPCRegistry.getAll();

        // Если игроков нет — все NPC падают в DORMANT (минимум вычислений)
        if (players.isEmpty()) {
            for (Mob mob : all) {
                if (mob.level() != level) continue;
                NPCRegistry.setActivityState(mob.getUUID(), ActivityState.DORMANT);
            }
            return;
        }

        int activeRadius = LiveMineConfig.activeRadius();
        int simRadius = LiveMineConfig.simulatedRadius();
        int dormantRadius = LiveMineConfig.dormantRadius();
        int maxActive = LiveMineConfig.maxActivePerPlayer();

        int activeRadiusSq = activeRadius * activeRadius;
        int simRadiusSq = simRadius * simRadius;
        int dormantRadiusSq = dormantRadius * dormantRadius;

        Map<UUID, Integer> activeCountByPlayer = new HashMap<>();
        for (ServerPlayer p : players) activeCountByPlayer.put(p.getUUID(), 0);

        List<Candidate> candidates = new ArrayList<>();

        for (Mob mob : all) {
            if (!(mob instanceof LiveNPCEntity npc)) continue;
            if (mob.level() != level) continue;

            ServerPlayer nearest = null;
            double nearestSq = Double.MAX_VALUE;

            for (ServerPlayer p : players) {
                double d = mob.distanceToSqr(p);
                if (d < nearestSq) {
                    nearestSq = d;
                    nearest = p;
                }
            }

            if (nearest == null) continue;
            candidates.add(new Candidate(npc, nearest, nearestSq));
        }

        candidates.sort(Comparator.comparingDouble(c -> c.distSq));

        for (Candidate cand : candidates) {
            ActivityState newState;

            if (cand.distSq <= activeRadiusSq) {
                int current = activeCountByPlayer.getOrDefault(cand.nearest.getUUID(), 0);
                if (current < maxActive) {
                    newState = ActivityState.ACTIVE;
                    activeCountByPlayer.put(cand.nearest.getUUID(), current + 1);
                } else {
                    newState = ActivityState.SIMULATED;
                }
            } else if (cand.distSq <= simRadiusSq) {
                newState = ActivityState.SIMULATED;
            } else if (cand.distSq <= dormantRadiusSq) {
                newState = ActivityState.DORMANT;
            } else {
                newState = ActivityState.INACTIVE;
            }

            ActivityState oldState = NPCRegistry.getActivityState(cand.npc.getUUID());

            if (oldState != newState) {
                // Если NPC переходит из SIMULATED в ACTIVE — телепорт к симулированной позиции
                if (oldState == ActivityState.SIMULATED && newState == ActivityState.ACTIVE) {
                    BlockPos target = getSimulatedTarget(cand.npc, level);
                    if (target != null) {
                        BlockPos safe = findSafeNear(level, target);
                        cand.npc.moveTo(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5);
                    }
                }
                NPCRegistry.setActivityState(cand.npc.getUUID(), newState);
                NPCRegistry.updateChunkPosition(cand.npc);
            }
        }
    }

    // =========================================================================
    // Вспомогательные
    // =========================================================================

    private static BlockPos getSimulatedTarget(LiveNPCEntity npc, ServerLevel level) {
        var tag = com.livemine.LiveMineSavedData.get(level).loadNPCData(npc.getUUID());
        if (tag == null || tag.isEmpty()) return null;
        if (!tag.contains("sim_x")) return null;
        return new BlockPos(tag.getInt("sim_x"), tag.getInt("sim_y"), tag.getInt("sim_z"));
    }

    private static BlockPos findSafeNear(ServerLevel level, BlockPos pos) {
        for (int dy = 0; dy <= 3; dy++) {
            BlockPos check = pos.above(dy);
            if (level.getBlockState(check).isAir()
                && level.getBlockState(check.above()).isAir()
                && !level.getBlockState(check.below()).isAir()) {
                return check;
            }
        }
        return pos.above(2);
    }

    // =========================================================================
    // Кандидат для сортировки
    // =========================================================================

    private static final class Candidate {
        final LiveNPCEntity npc;
        final ServerPlayer nearest;
        final double distSq;

        Candidate(LiveNPCEntity npc, ServerPlayer nearest, double distSq) {
            this.npc = npc;
            this.nearest = nearest;
            this.distSq = distSq;
        }
    }
}
