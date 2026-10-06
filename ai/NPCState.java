package com.livemine.ai;

import net.minecraft.nbt.CompoundTag;

/**
 * DTO состояния NPC — для синхронизации сервер ↔ клиент.
 *
 * Не хранит ссылок на сущность, только данные.
 */
public final class NPCState {

    public double hunger;
    public double energy;
    public double health;
    public double social;
    public String currentGoal;
    public String villageId;
    public String displayName;
    public String profession;

    public NPCState() {
        this.currentGoal = "WANDER";
        this.villageId = "";
        this.displayName = "NPC";
        this.profession = "";
    }

    public NPCState(double hunger, double energy, double health, double social,
                    String goal, String villageId, String name) {
        this.hunger = hunger;
        this.energy = energy;
        this.health = health;
        this.social = social;
        this.currentGoal = goal != null ? goal : "WANDER";
        this.villageId = villageId != null ? villageId : "";
        this.displayName = name != null ? name : "NPC";
        this.profession = "";
    }

    public NPCState(double hunger, double energy, double health, double social,
                    String goal, String villageId, String name, String profession) {
        this(hunger, energy, health, social, goal, villageId, name);
        this.profession = profession != null ? profession : "";
    }

    // =========================================================================
    // NBT
    // =========================================================================

    public static NPCState fromNBT(CompoundTag tag, String name) {
        if (tag == null || tag.isEmpty()) {
            return new NPCState(100, 100, 20, 100, "WANDER", "", name);
        }
        return new NPCState(
            tag.getDouble("hunger"),
            tag.getDouble("energy"),
            tag.getDouble("health"),
            tag.getDouble("social"),
            tag.getString("current_goal"),
            tag.getString("village_id"),
            name
        );
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("hunger", hunger);
        tag.putDouble("energy", energy);
        tag.putDouble("health", health);
        tag.putDouble("social", social);
        tag.putString("current_goal", currentGoal);
        tag.putString("village_id", villageId);
        tag.putString("display_name", displayName);
        tag.putString("profession", profession);
        return tag;
    }

    // =========================================================================
    // Утилиты
    // =========================================================================

    public NPCState copy() {
        return new NPCState(hunger, energy, health, social, currentGoal, villageId, displayName, profession);
    }

    /** Возвращает цель в удобочитаемом виде (RU). */
    public String getGoalRu() {
        if (currentGoal == null) return "Неизвестно";
        return switch (currentGoal.toUpperCase()) {
            case "WORK" -> "Работает";
            case "REST" -> "Отдыхает";
            case "HEALING", "HEAL" -> "Лечится";
            case "SOCIAL", "SOCIALIZE" -> "Общается";
            case "WANDER" -> "Бродит";
            case "FOOD", "EAT" -> "Ест";
            case "SURVIVAL" -> "Выживает";
            case "SAFETY" -> "Осторожничает";
            case "COMBAT" -> "Сражается";
            case "FLEE" -> "Убегает";
            case "LEARNING" -> "Учится";
            case "FAMILY" -> "С семьёй";
            case "ENTERTAINMENT" -> "Развлекается";
            case "COLLECTIVE" -> "На общих работах";
            case "BUILD" -> "Строит";
            default -> "Неизвестно";
        };
    }
}
