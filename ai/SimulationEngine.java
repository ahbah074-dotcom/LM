package com.livemine.ai;

import com.livemine.LiveMineConfig;
import com.livemine.LiveMineSavedData;
import com.livemine.blocks.CommunalFirePitBlock;
import com.livemine.blocks.FarmPlotBlock;
import com.livemine.blocks.InfirmaryBlock;
import com.livemine.blocks.WorkshopForgeBlock;
import com.livemine.entity.LiveNPCEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.List;

/**
 * Движок симуляции SIMULATED NPC.
 *
 * Вместо полного Minecraft AI (pathfinding, Goals) — упрощённые вычисления:
 *   - Голод / энергия / здоровье / социум по времени
 *   - Работа (накопление ресурсов)
 *   - Целевая позиция для телепорта при переключении в ACTIVE
 *
 * Стоимость ~0.01 мс/NPC против ~0.13 мс для ACTIVE.
 *
 * Вызывается из NPCSimulationTickHandler каждый тик, порциями.
 */
public final class SimulationEngine {

    private SimulationEngine() {}

    // =========================================================================
    // Основной батч
    // =========================================================================

    public static void simulateBatch(ServerLevel level, int batchPerTick) {
        int interval = AdaptiveTickRate.getSimulationInterval();
        long currentTick = level.getGameTime();

        List<Mob> all = NPCRegistry.getAll();
        int processed = 0;

        LiveMineSavedData data = LiveMineSavedData.get(level);

        for (Mob mob : all) {
            if (processed >= batchPerTick) break;
            if (mob.level() != level) continue;
            if (!(mob instanceof LiveNPCEntity npc)) continue;
            if (NPCRegistry.getActivityState(npc.getUUID()) != NPCActivityManager.ActivityState.SIMULATED) {
                continue;
            }

            CompoundTag tag = data.loadNPCData(npc.getUUID());
            if (tag == null || tag.isEmpty()) continue;

            long lastSim = tag.contains("last_sim_tick") ? tag.getLong("last_sim_tick") : 0L;
            if (currentTick - lastSim < interval) continue;

            simulateOne(npc, tag, level, currentTick);
            data.saveNPCData(npc.getUUID(), tag);
            processed++;
        }
    }

    // =========================================================================
    // Симуляция одного NPC
    // =========================================================================

    private static void simulateOne(LiveNPCEntity npc, CompoundTag tag, ServerLevel level, long currentTick) {
        long lastSim = tag.contains("last_sim_tick") ? tag.getLong("last_sim_tick") : currentTick;
        double deltaSeconds = Math.max(0.5, (currentTick - lastSim) / 20.0);

        // Текущая цель
        String goal = tag.getString("current_goal");
        if (goal.isEmpty()) goal = "work";

        // Голод
        double hungerRate = goal.equals("work") ? 0.05 : 0.01;
        double hunger = tag.getDouble("hunger") - hungerRate * deltaSeconds;
        hunger = clamp(hunger, 0, 100);

        // Энергия
        double energy = tag.getDouble("energy");
        if (goal.equals("work")) {
            energy -= 0.03 * deltaSeconds;
            if (energy < 20) goal = "rest";
        } else if (goal.equals("rest")) {
            energy += 0.05 * deltaSeconds;
            if (energy > 80) goal = "work";
        } else {
            energy -= 0.005 * deltaSeconds;
        }
        energy = clamp(energy, 0, 100);

        // Р—РґРѕСЂРѕРІСЊРµ
        double health = tag.getDouble("health");
        if (hunger < 10) {
            health -= 0.006 * deltaSeconds;
        } else if (health < 20 && hunger > 30 && energy > 20) {
            health += 0.01 * deltaSeconds;
        }
        health = clamp(health, 0, 20);

        // Социум
        double social = tag.getDouble("social") - 0.005 * deltaSeconds;
        if (social < 30 && !goal.equals("socialize")) goal = "socialize";
        social = clamp(social, 0, 100);

        // Работа — накопление ресурсов деревни
        if (goal.equals("work")) {
            String villageId = tag.getString("village_id");
            if (!villageId.isEmpty()) {
                var villageTag = level.getDataStorage()
                    .computeIfAbsent(new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                        com.livemine.LiveMineSavedData::new,
                        (t, p) -> new com.livemine.LiveMineSavedData(t, p),
                        null), "livemine_data")
                    .loadVillageData(villageId);
                if (villageTag != null && !villageTag.isEmpty()) {
                    double res = villageTag.getDouble("resources") + 0.05 * deltaSeconds;
                    villageTag.putDouble("resources", res);
                }
            }
        }

        // Записываем обратно
        tag.putDouble("hunger", hunger);
        tag.putDouble("energy", energy);
        tag.putDouble("health", health);
        tag.putDouble("social", social);
        tag.putString("current_goal", goal);

        // Обновляем позицию для будущего телепорта
        updateSimulatedPosition(npc, tag, level, goal);

        // Смерть
        if (health <= 0) {
            tag.putBoolean("dead", true);
            npc.discard();
            NPCRegistry.unregister(npc.getUUID());

            if (LiveMineConfig.brainDebug()) {
                BrainDebugger.log(npc, "SIM_DEATH", "умер в симуляции (голод)");
            }
        }

        tag.putLong("last_sim_tick", currentTick);
    }

    // =========================================================================
    // Симулированная позиция
    // =========================================================================

    private static void updateSimulatedPosition(LiveNPCEntity npc, CompoundTag tag, ServerLevel level, String goal) {
        String villageId = tag.getString("village_id");
        if (villageId.isEmpty()) {
            tag.putInt("sim_x", npc.getBlockX());
            tag.putInt("sim_y", npc.getBlockY());
            tag.putInt("sim_z", npc.getBlockZ());
            return;
        }

        var vd = new com.livemine.VillageData(villageId, level);
        if (!vd.exists()) {
            tag.putInt("sim_x", npc.getBlockX());
            tag.putInt("sim_y", npc.getBlockY());
            tag.putInt("sim_z", npc.getBlockZ());
            return;
        }

        BlockPos target = switch (goal) {
            case "rest" -> nearest(vd.getStructures(CommunalFirePitBlock.class), npc);
            case "heal" -> nearest(vd.getStructures(InfirmaryBlock.class), npc);
            case "work" -> nearest(vd.getStructures(FarmPlotBlock.class), npc);
            case "smithing" -> nearest(vd.getStructures(WorkshopForgeBlock.class), npc);
            default -> npc.blockPosition();
        };

        if (target == null) target = vd.getCenter();
        if (target == null) target = npc.blockPosition();

        tag.putInt("sim_x", target.getX());
        tag.putInt("sim_y", target.getY());
        tag.putInt("sim_z", target.getZ());
    }

    private static BlockPos nearest(List<BlockPos> list, Mob npc) {
        if (list.isEmpty()) return null;
        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;
        for (BlockPos p : list) {
            double d = npc.blockPosition().distSqr(p);
            if (d < bestSq) {
                bestSq = d;
                best = p;
            }
        }
        return best;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
