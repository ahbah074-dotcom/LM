package com.livemine.domain;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Доменная запись агента.
 *
 * НЕ является Minecraft-сущностью.
 * Существует независимо от того, загружен ли чанк с NPC.
 *
 * Это фундамент для ACTIVE/VIRTUAL/DORMANT режимов симуляции:
 *   - ACTIVE   — AgentRecord синхронизирован с LiveNPCEntity
 *   - VIRTUAL  — AgentRecord живёт в памяти, NPC без AI
 *   - DORMANT  — AgentRecord сохранён в NBT, в память загружается редко
 */
public final class AgentRecord {

    private final UUID id;
    private final UUID villageId;
    private String name;
    private final Needs needs;

    private long lastGameDay;
    private String currentTask;
    private int ageDays;
    private int birthDay;
    private float health;
    private SimulationMode mode;

    public AgentRecord(UUID id, UUID villageId, String name, Needs needs,
                       long lastGameDay, String currentTask) {
        this.id = id;
        this.villageId = villageId;
        this.name = name != null ? name : "NPC";
        this.needs = needs != null ? needs : Needs.initial();
        this.lastGameDay = Math.max(0, lastGameDay);
        this.currentTask = currentTask != null ? currentTask : "IDLE";
        this.ageDays = 0;
        this.birthDay = 0;
        this.health = 20f;
        this.mode = SimulationMode.VIRTUAL;
    }

    // =========================================================================
    // Геттеры
    // =========================================================================

    public UUID id() { return id; }
    public UUID villageId() { return villageId; }
    public String name() { return name; }
    public Needs needs() { return needs; }
    public long lastGameDay() { return lastGameDay; }
    public String currentTask() { return currentTask; }
    public int ageDays() { return ageDays; }
    public int birthDay() { return birthDay; }
    public float health() { return health; }
    public SimulationMode mode() { return mode; }

    // =========================================================================
    // Сеттеры
    // =========================================================================

    public void setName(String v) { this.name = v != null ? v : "NPC"; }
    public void setHealth(float v) { this.health = Math.max(0f, Math.min(20f, v)); }
    public void setMode(SimulationMode v) { if (v != null) this.mode = v; }
    public void setBirthDay(int day) { this.birthDay = day; }
    public void setAgeDays(int days) { this.ageDays = Math.max(0, days); }
    public void setCurrentTask(String task) { this.currentTask = task != null ? task : "IDLE"; }

    // =========================================================================
    // Продвижение симуляции
    // =========================================================================

    public void advance(long currentDay) {
        if (currentDay <= lastGameDay) return;

        long delta = currentDay - lastGameDay;
        needs.advanceDays(delta);

        ageDays += (int) delta;
        lastGameDay = currentDay;

        if (needs.isFoodCritical()) {
            currentTask = "FIND_FOOD";
        } else if (needs.isSafetyCritical()) {
            currentTask = "SEEK_SAFETY";
        } else if (needs.isRestCritical()) {
            currentTask = "REST";
        } else if (needs.canWork()) {
            currentTask = "WORK";
        } else {
            currentTask = "IDLE";
        }
    }

    public void advance(long currentDay, boolean threatened) {
        if (currentDay <= lastGameDay) return;

        long delta = currentDay - lastGameDay;
        needs.advanceDays(delta, threatened);

        ageDays += (int) delta;
        lastGameDay = currentDay;
    }

    // =========================================================================
    // Проверки
    // =========================================================================

    public boolean isAdult() {
        return ageDays >= 7;
    }

    public boolean isElderly() {
        return ageDays >= 90;
    }

    public boolean isDead() {
        return health <= 0f;
    }

    // =========================================================================
    // NBT
    // =========================================================================

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        // 1.21.1: putUUID с null бросает NPE — защищаем
        if (villageId != null) tag.putUUID("village", villageId);
        tag.putString("name", name);
        tag.putInt("food", needs.food());
        tag.putInt("rest", needs.rest());
        tag.putInt("safety", needs.safety());
        tag.putLong("lastGameDay", lastGameDay);
        tag.putString("task", currentTask);
        tag.putInt("ageDays", ageDays);
        tag.putInt("birthDay", birthDay);
        tag.putFloat("health", health);
        tag.putString("mode", mode.name());
        return tag;
    }

    public static AgentRecord fromNbt(CompoundTag tag) {
        UUID id = tag.getUUID("id");
        // 1.21.1: hasUUID корректно проверяет наличие и валидность UUID
        UUID villageId = tag.hasUUID("village") ? tag.getUUID("village") : null;
        String name = tag.getString("name");

        Needs needs = new Needs(
            tag.getInt("food"),
            tag.getInt("rest"),
            tag.getInt("safety")
        );

        long lastGameDay = tag.getLong("lastGameDay");
        String task = tag.getString("task");

        AgentRecord record = new AgentRecord(id, villageId, name, needs, lastGameDay, task);
        record.ageDays = tag.getInt("ageDays");
        record.birthDay = tag.getInt("birthDay");
        record.health = tag.getFloat("health");

        if (tag.contains("mode")) {
            try {
                record.mode = SimulationMode.valueOf(tag.getString("mode"));
            } catch (IllegalArgumentException ignored) {
                record.mode = SimulationMode.VIRTUAL;
            }
        }
        return record;
    }

    // =========================================================================
    // Утилиты
    // =========================================================================

    @Override
    public String toString() {
        return "AgentRecord{" + name + ", age=" + ageDays
            + ", health=" + String.format("%.1f", health)
            + ", mode=" + mode + ", task=" + currentTask + "}";
    }
}