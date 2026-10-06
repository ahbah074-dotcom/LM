package com.livemine.wanderer;

import com.livemine.LiveMineConfig;
import com.livemine.LiveMineMod;
import com.livemine.LiveMineSavedData;
import com.livemine.NamePool;
import com.livemine.entity.LiveNPCEntity;
import com.livemine.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Менеджер странников — NPC без деревни.
 *
 * Сценарии возникновения:
 *   1. Гибель деревни
 *   2. Изгнание
 *   3. Добровольный уход
 *   4. Сирота без опекуна
 *
 * Странник ищет ближайшее известное поселение, просит убежище.
 * Деревня голосует: принять / отказать / испытательный срок.
 */
public final class WandererManager {

    private static WandererManager INSTANCE;

    public enum State {
        SEEKING_SHELTER,
        TRAVELLING,
        ARRIVED,
        SETTLED,
        REJECTED
    }

    public static final class WandererRecord {
        public UUID npcUUID;
        public State state;
        public int villagesVisited;
        public long becameWandererDay;
        public UUID lastKnownVillage;

        public WandererRecord(UUID uuid, long day) {
            this.npcUUID = uuid;
            this.state = State.SEEKING_SHELTER;
            this.villagesVisited = 0;
            this.becameWandererDay = day;
            this.lastKnownVillage = null;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("uuid", npcUUID);
            tag.putString("state", state.name());
            tag.putInt("visited", villagesVisited);
            tag.putLong("day", becameWandererDay);
            if (lastKnownVillage != null) tag.putUUID("lastVillage", lastKnownVillage);
            return tag;
        }

        public static WandererRecord load(CompoundTag tag) {
            try {
                WandererRecord r = new WandererRecord(tag.getUUID("uuid"), tag.getLong("day"));
                r.state = State.valueOf(tag.getString("state"));
                r.villagesVisited = tag.getInt("visited");
                if (tag.hasUUID("lastVillage")) r.lastKnownVillage = tag.getUUID("lastVillage");
                return r;
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private final Set<UUID> wanderers = new HashSet<>();

    private WandererManager() {}

    public static synchronized WandererManager getInstance() {
        if (INSTANCE == null) INSTANCE = new WandererManager();
        return INSTANCE;
    }

    // =========================================================================
    // Создание
    // =========================================================================

    public boolean makeWanderer(LiveNPCEntity npc, long currentDay) {
        if (npc == null) return false;
        if (!npc.isAdult() && npc.getAgeInDays() < 3) return false;
        if (!(npc.level() instanceof ServerLevel level)) return false;

        UUID oldVillage = npc.getVillageId();
        npc.setVillageId(null);

        CompoundTag tag = LiveMineSavedData.get(level).loadNPCData(npc.getUUID());
        tag.putString("village_id", "");
        tag.putBoolean("is_wanderer", true);
        tag.putLong("wanderer_since", currentDay);
        if (oldVillage != null) tag.putUUID("last_village", oldVillage);
        LiveMineSavedData.get(level).saveNPCData(npc.getUUID(), tag);

        wanderers.add(npc.getUUID());
        LiveMineMod.LOGGER.info("NPC {} became a wanderer", npc.getCustomNameTag());
        return true;
    }

    public LiveNPCEntity spawnWanderer(ServerLevel level, BlockPos pos) {
        if (wanderers.size() >= 50) return null;

        LiveNPCEntity npc = ModEntities.LIVE_NPC.get().create(level);
        if (npc == null) return null;

        npc.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        String name = NamePool.getInstance().generateName(level.random.nextBoolean());
        npc.setCustomNameTag(name);
        npc.setAgeInDays(18 + level.random.nextInt(30));

        level.addFreshEntity(npc);
        com.livemine.ai.NPCRegistry.register(npc);
        makeWanderer(npc, level.getDayTime() / 24000L);
        return npc;
    }

    // =========================================================================
    // Тик
    // =========================================================================

    public void tick(LiveNPCEntity npc, long currentDay) {
        if (npc == null) return;
        if (!wanderers.contains(npc.getUUID())) return;
        if (npc.tickCount % 200 != 0) return;
        if (!(npc.level() instanceof ServerLevel level)) return;

        var nearest = com.livemine.VillageManager.getInstance()
            .getNearestVillage(npc.getBlockX(), npc.getBlockZ(), 64);
        if (nearest == null) return;
        if (nearest.population() >= nearest.maxPopulation()) return;

        if (level.random.nextFloat() < 0.10f) {
            joinVillage(npc, nearest.id(), level);
        }
    }

    private void joinVillage(LiveNPCEntity npc, UUID villageId, ServerLevel level) {
        npc.setVillageId(villageId);
        wanderers.remove(npc.getUUID());

        CompoundTag tag = LiveMineSavedData.get(level).loadNPCData(npc.getUUID());
        tag.putString("village_id", "village_" + villageId);
        tag.putBoolean("is_wanderer", false);
        LiveMineSavedData.get(level).saveNPCData(npc.getUUID(), tag);

        LiveMineMod.LOGGER.info("Wanderer {} joined village {}",
            npc.getCustomNameTag(), villageId);
    }

    // =========================================================================
    // Чтение
    // =========================================================================

    public boolean isWanderer(UUID uuid) {
        return wanderers.contains(uuid);
    }

    public Collection<UUID> getAllWanderers() {
        return java.util.Collections.unmodifiableSet(wanderers);
    }

    public int size() { return wanderers.size(); }

    // =========================================================================
    // NBT
    // =========================================================================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (UUID id : wanderers) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            list.add(t);
        }
        tag.put("wanderers", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        wanderers.clear();
        ListTag list = tag.getList("wanderers", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            wanderers.add(list.getCompound(i).getUUID("id"));
        }
    }

    public void clear() {
        wanderers.clear();
    }
}