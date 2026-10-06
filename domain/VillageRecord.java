package com.livemine.domain;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Доменная запись деревни.
 *
 * ВАЖНО: канонический ключ деревни в NBT — "village_" + <asLong позиции центра>.
 * Метод rawId() возвращает этот ключ. Поле id (UUID) — производное от rawId.
 */
public final class VillageRecord {

    private final UUID id;
    private String name;
    private final String dimension;
    private final int centerX, centerY, centerZ;

    private int radius;
    private int population;
    private int maxPopulation;
    private long budget;
    private UUID leaderId;
    private long foundedDay;
    private boolean abandoned;
    private String culture;

    public VillageRecord(UUID id, String dimension, int x, int y, int z) {
        this.id = id;
        this.dimension = dimension;
        this.centerX = x;
        this.centerY = y;
        this.centerZ = z;
        this.name = "Village";
        this.radius = 32;
        this.population = 0;
        this.maxPopulation = 128;
        this.budget = 0L;
        this.foundedDay = 0L;
        this.abandoned = false;
        this.culture = "generic";
    }

    // --- геттеры ---
    public UUID id() { return id; }
    public String name() { return name; }
    public String dimension() { return dimension; }
    public int centerX() { return centerX; }
    public int centerY() { return centerY; }
    public int centerZ() { return centerZ; }
    public int radius() { return radius; }
    public int population() { return population; }
    public int maxPopulation() { return maxPopulation; }
    public long budget() { return budget; }
    public UUID leaderId() { return leaderId; }
    public long foundedDay() { return foundedDay; }
    public boolean isAbandoned() { return abandoned; }
    public String culture() { return culture; }

    /**
     * Канонический ключ деревни в NBT: "village_" + BlockPos.asLong().
     * Используется как в LiveMineSavedData, так и в NPC.village_id.
     */
    public String rawId() {
        BlockPos center = new BlockPos(centerX, centerY, centerZ);
        return "village_" + center.asLong();
    }

    // --- сеттеры ---
    public void setName(String v) { this.name = v; }
    public void setRadius(int v) { this.radius = Math.max(32, Math.min(256, v)); }
    public void setPopulation(int v) { this.population = Math.max(0, v); }
    public void setMaxPopulation(int v) { this.maxPopulation = Math.max(1, v); }
    public void setLeaderId(UUID v) { this.leaderId = v; }
    public void setFoundedDay(long v) { this.foundedDay = v; }
    public void setAbandoned(boolean v) { this.abandoned = v; }
    public void setCulture(String v) { this.culture = v != null ? v : "generic"; }

    public void addCitizen() { population++; }
    public void removeCitizen() { population = Math.max(0, population - 1); }
    public void deposit(long amount) { budget += amount; }
    public boolean withdraw(long amount) {
        if (budget < amount) return false;
        budget -= amount;
        return true;
    }

    // --- NBT ---
    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("name", name);
        tag.putString("dim", dimension);
        tag.putInt("cx", centerX);
        tag.putInt("cy", centerY);
        tag.putInt("cz", centerZ);
        tag.putInt("radius", radius);
        tag.putInt("pop", population);
        tag.putInt("maxPop", maxPopulation);
        tag.putLong("budget", budget);
        if (leaderId != null) tag.putUUID("leader", leaderId);
        tag.putLong("founded", foundedDay);
        tag.putBoolean("abandoned", abandoned);
        tag.putString("culture", culture);
        return tag;
    }

    public static VillageRecord fromNbt(CompoundTag tag) {
        VillageRecord v = new VillageRecord(
            tag.getUUID("id"),
            tag.getString("dim"),
            tag.getInt("cx"), tag.getInt("cy"), tag.getInt("cz")
        );
        v.name = tag.getString("name");
        v.radius = tag.getInt("radius");
        v.population = tag.getInt("pop");
        v.maxPopulation = tag.getInt("maxPop");
        v.budget = tag.getLong("budget");
        if (tag.hasUUID("leader")) v.leaderId = tag.getUUID("leader");
        v.foundedDay = tag.getLong("founded");
        v.abandoned = tag.getBoolean("abandoned");
        v.culture = tag.getString("culture");
        return v;
    }
}