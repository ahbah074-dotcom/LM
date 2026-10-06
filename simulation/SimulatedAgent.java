package com.livemine.simulation;

import com.livemine.entity.LiveNPCEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/**
 * Агент симуляции для режима SIMULATED (ТЗ 20.0 §54.3).
 *
 * Используется вместо полноценного Minecraft AI для NPC,
 * находящихся далеко от игроков.
 *
 * Упрощённые вычисления, но результат совпадает с ACTIVE:
 *   - Добыча, продажа, покупка, крафт, строительство
 *   - За большой промежуток времени NPC накапливает ресурсы
 *
 * НЕ создаёт новых entity.
 * НЕ управляет сущностью напрямую.
 * Работает только с NBT в LiveMineSavedData.
 */
public final class SimulatedAgent {

    public static final double HUNGER_RATE_WORK = 0.05;
    public static final double HUNGER_RATE_IDLE = 0.01;
    public static final double ENERGY_RATE_WORK = 0.03;
    public static final double ENERGY_RATE_REST = 0.05;
    public static final double HEALTH_REGEN_RATE = 0.01;
    public static final double SOCIAL_RATE = 0.005;

    private final LiveNPCEntity npc;

    public SimulatedAgent(LiveNPCEntity npc) {
        this.npc = npc;
    }

    // =========================================================================
    // Основной тик
    // =========================================================================

    /**
     * Обрабатывает одного NPC в SIMULATED-режиме.
     * Вызывается из SimulationEngine.
     */
    public void simulate(CompoundTag tag, ServerLevel level, long currentTick) {
        if (tag == null || tag.isEmpty()) return;

        long lastSim = tag.contains("last_sim_tick") ? tag.getLong("last_sim_tick") : currentTick;
        double deltaSeconds = Math.max(0.5, (currentTick - lastSim) / 20.0);

        String goal = tag.getString("current_goal");
        if (goal.isEmpty()) goal = "WORK";

        // === Потребности ===
        simulateNeeds(tag, goal, deltaSeconds);

        // === Работа ===
        if ("WORK".equals(goal)) {
            simulateWork(tag, level, deltaSeconds);
        }

        // === Запись позиции ===
        updateSimulatedPosition(tag, level);

        // === Смерть ===
        double health = tag.getDouble("health");
        if (health <= 0) {
            tag.putBoolean("dead", true);
        }

        tag.putLong("last_sim_tick", currentTick);
    }

    // =========================================================================
    // Потребности
    // =========================================================================

    private void simulateNeeds(CompoundTag tag, String goal, double deltaSeconds) {
        double hunger = tag.getDouble("hunger");
        double energy = tag.getDouble("energy");
        double social = tag.getDouble("social");
        double health = tag.getDouble("health");

        // Голод
        double hungerRate = "WORK".equals(goal) ? HUNGER_RATE_WORK : HUNGER_RATE_IDLE;
        hunger = Math.max(0, hunger - hungerRate * deltaSeconds);

        // Энергия
        if ("WORK".equals(goal)) {
            energy = Math.max(0, energy - ENERGY_RATE_WORK * deltaSeconds);
        } else if ("REST".equals(goal)) {
            energy = Math.min(100, energy + ENERGY_RATE_REST * deltaSeconds);
        }

        // Социум
        social = Math.max(0, social - SOCIAL_RATE * deltaSeconds);

        // Р—РґРѕСЂРѕРІСЊРµ
        if (hunger < 10) {
            health = Math.max(0, health - 0.006 * deltaSeconds);
        } else if (hunger > 30 && energy > 20 && health < 20) {
            health = Math.min(20, health + HEALTH_REGEN_RATE * deltaSeconds);
        }

        tag.putDouble("hunger", hunger);
        tag.putDouble("energy", energy);
        tag.putDouble("social", social);
        tag.putDouble("health", health);

        // Автовыбор следующей цели
        if (hunger < 20) {
            tag.putString("current_goal", "FOOD");
        } else if (energy < 15) {
            tag.putString("current_goal", "REST");
        } else if (social < 20) {
            tag.putString("current_goal", "SOCIAL");
        } else {
            tag.putString("current_goal", "WORK");
        }
    }

    // =========================================================================
    // Работа
    // =========================================================================

    private void simulateWork(CompoundTag tag, ServerLevel level, double deltaSeconds) {
        String villageId = tag.getString("village_id");
        if (villageId.isEmpty()) return;

        // Накопление ресурсов деревни
        var savedData = com.livemine.LiveMineSavedData.get(level);
        CompoundTag villageTag = savedData.loadVillageData(villageId);
        if (villageTag.isEmpty()) return;

        double resources = villageTag.getDouble("resources") + 0.05 * deltaSeconds;
        villageTag.putDouble("resources", resources);

        // Снижение потребностей деревни
        double needFood = Math.max(0, villageTag.getDouble("need_food") - 0.02 * deltaSeconds);
        double needWood = Math.max(0, villageTag.getDouble("need_wood") - 0.02 * deltaSeconds);
        double needOre = Math.max(0, villageTag.getDouble("need_ore") - 0.02 * deltaSeconds);

        villageTag.putDouble("need_food", needFood);
        villageTag.putDouble("need_wood", needWood);
        villageTag.putDouble("need_ore", needOre);

        savedData.saveVillageData(villageId, villageTag);
    }

    // =========================================================================
    // Позиция
    // =========================================================================

    private void updateSimulatedPosition(CompoundTag tag, ServerLevel level) {
        String villageId = tag.getString("village_id");
        if (villageId.isEmpty()) {
            tag.putInt("sim_x", npc.getBlockX());
            tag.putInt("sim_y", npc.getBlockY());
            tag.putInt("sim_z", npc.getBlockZ());
            return;
        }

        var vd = new com.livemine.VillageData(villageId, level);
        if (!vd.exists() || vd.getCenter() == null) {
            tag.putInt("sim_x", npc.getBlockX());
            tag.putInt("sim_y", npc.getBlockY());
            tag.putInt("sim_z", npc.getBlockZ());
            return;
        }

        var center = vd.getCenter();
        tag.putInt("sim_x", center.getX());
        tag.putInt("sim_y", center.getY());
        tag.putInt("sim_z", center.getZ());
    }
}
