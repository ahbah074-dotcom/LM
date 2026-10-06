package com.livemine.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Сущность надгробия.
 *
 * Появляется при смерти NPC. Хранит:
 *   - Имя NPC
 *   - Эпитет
 *   - Описание жизни
 *   - День смерти
 *   - Причину смерти
 *
 * Может быть перемещено NPC-носильщиком на кладбище.
 *
 * 1.21.1: getDisplayName() → getGraveDisplayName() — иначе конфликт
 * с Entity.getDisplayName(), который возвращает Component.
 */
public class GraveStoneEntity extends Entity {

    private String npcName = "";
    private String epithet = "";
    private String lifeDescription = "";
    private int ageDays;
    private UUID villageId;
    private String deathCause = "unknown";
    private long deathDay;
    private boolean carriedToCemetery;

    public GraveStoneEntity(EntityType<? extends GraveStoneEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
        this.setNoGravity(true);
    }

    public GraveStoneEntity(Level level, double x, double y, double z,
                            String name, String epithet, String description,
                            int age, UUID village, String cause, long day) {
        super(com.livemine.registry.ModEntities.GRAVE_STONE.get(), level);
        this.setPos(x, y, z);
        this.npcName = name != null ? name : "NPC";
        this.epithet = epithet != null ? epithet : "";
        this.lifeDescription = description != null ? description : "";
        this.ageDays = age;
        this.villageId = village;
        this.deathCause = cause != null ? cause : "unknown";
        this.deathDay = day;
        this.blocksBuilding = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(GraveStoneDataAccessor.NAME, "");
        builder.define(GraveStoneDataAccessor.EPITHET, "");
        builder.define(GraveStoneDataAccessor.LIFE_DESCRIPTION, "");
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        if (carriedToCemetery) return;

        if (tickCount % 200 == 0 && villageId != null) {
            checkForCarrier();
        }
    }

    private void checkForCarrier() {
        if (!(level() instanceof ServerLevel sl)) return;

        var npcs = sl.getEntitiesOfClass(LiveNPCEntity.class, getBoundingBox().inflate(48));
        for (LiveNPCEntity npc : npcs) {
            UUID npcVillage = npc.getVillageId();
            if (npcVillage == null || !npcVillage.equals(villageId)) continue;
            if (!npc.isAdult() || !npc.isAlive()) continue;
            break;
        }
    }

    public void markCarriedToCemetery() {
        this.carriedToCemetery = true;
    }

    // =========================================================================
    // Геттеры
    // =========================================================================

    public String getNpcName() { return npcName; }
    public String getEpithet() { return epithet; }
    public String getLifeDescription() { return lifeDescription; }
    public int getAgeDays() { return ageDays; }
    public UUID getVillageId() { return villageId; }
    public String getDeathCause() { return deathCause; }
    public long getDeathDay() { return deathDay; }
    public boolean isCarriedToCemetery() { return carriedToCemetery; }

    /**
     * Переименовано из getDisplayName() — во избежание конфликта с Entity.
     */
    public String getGraveDisplayName() {
        if (epithet == null || epithet.isEmpty()) return npcName;
        return npcName + " " + epithet;
    }

    public String getFullEpitaph() {
        StringBuilder sb = new StringBuilder();
        sb.append("Здесь покоится ").append(getGraveDisplayName()).append("\n");
        sb.append("Прожил ").append(ageDays).append(" дней\n");
        if (lifeDescription != null && !lifeDescription.isEmpty()) {
            sb.append(lifeDescription);
        }
        return sb.toString();
    }

    // =========================================================================
    // NBT
    // =========================================================================

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        npcName = tag.getString("npc_name");
        epithet = tag.getString("epithet");
        lifeDescription = tag.getString("life_description");
        ageDays = tag.getInt("age_days");
        deathCause = tag.getString("death_cause");
        deathDay = tag.getLong("death_day");
        carriedToCemetery = tag.getBoolean("carried");
        if (tag.hasUUID("village_id")) villageId = tag.getUUID("village_id");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("npc_name", npcName);
        tag.putString("epithet", epithet);
        tag.putString("life_description", lifeDescription);
        tag.putInt("age_days", ageDays);
        tag.putString("death_cause", deathCause);
        tag.putLong("death_day", deathDay);
        tag.putBoolean("carried", carriedToCemetery);
        if (villageId != null) tag.putUUID("village_id", villageId);
    }

    @Override
    public boolean isPickable() { return true; }

    @Override
    public boolean canBeCollidedWith() { return true; }

    @Override
    public boolean isPushable() { return false; }
}