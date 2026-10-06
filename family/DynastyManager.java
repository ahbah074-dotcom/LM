package com.livemine.family;

import com.livemine.LiveMineMod;
import com.livemine.LiveMineSavedData;
import com.livemine.entity.LiveNPCEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Менеджер династий (ТЗ 20.1 §22.1).
 *
 * Династия = семья с 3+ поколениями.
 * Бонусы:
 *   - +10% доверие в деревне
 *   - Приоритет при выборах лидера
 *   - Запись в хронике
 *
 * Хранится в коллективной памяти как публичное сведение.
 */
public final class DynastyManager {

    private static DynastyManager INSTANCE;

    public static final int MIN_GENERATIONS = 3;

    public static final class Dynasty {
        public UUID id;
        public String surname;
        public UUID founderId;
        public long foundedDay;
        public Set<UUID> members = new HashSet<>();
        public Map<UUID, Integer> generationByMember = new HashMap<>();
        public int maxGeneration = 1;

        public Dynasty(String surname, UUID founderId, long day) {
            this.id = UUID.randomUUID();
            this.surname = surname != null ? surname : "Безымянные";
            this.founderId = founderId;
            this.foundedDay = day;
            if (founderId != null) {
                members.add(founderId);
                generationByMember.put(founderId, 1);
            }
        }

        public boolean isFullDynasty() {
            return maxGeneration >= MIN_GENERATIONS;
        }

        public void addMember(UUID uuid, int generation) {
            members.add(uuid);
            generationByMember.put(uuid, generation);
            if (generation > maxGeneration) maxGeneration = generation;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", id);
            tag.putString("surname", surname);
            if (founderId != null) tag.putUUID("founder", founderId);
            tag.putLong("day", foundedDay);
            tag.putInt("maxGen", maxGeneration);

            CompoundTag membersTag = new CompoundTag();
            for (var e : generationByMember.entrySet()) {
                membersTag.putInt(e.getKey().toString(), e.getValue());
            }
            tag.put("members", membersTag);
            return tag;
        }

        public static Dynasty load(CompoundTag tag) {
            UUID founder = tag.hasUUID("founder") ? tag.getUUID("founder") : null;
            Dynasty d = new Dynasty(tag.getString("surname"), founder, tag.getLong("day"));
            d.id = tag.getUUID("id");
            d.maxGeneration = tag.getInt("maxGen");

            CompoundTag membersTag = tag.getCompound("members");
            for (String k : membersTag.getAllKeys()) {
                try {
                    UUID id = UUID.fromString(k);
                    int gen = membersTag.getInt(k);
                    d.members.add(id);
                    d.generationByMember.put(id, gen);
                } catch (IllegalArgumentException ignored) {}
            }
            return d;
        }
    }

    private final Map<UUID, Dynasty> dynasties = new HashMap<>();
    private final Map<UUID, UUID> memberToDynasty = new HashMap<>();

    private DynastyManager() {}

    public static synchronized DynastyManager getInstance() {
        if (INSTANCE == null) INSTANCE = new DynastyManager();
        return INSTANCE;
    }

    // =========================================================================
    // Создание и добавление
    // =========================================================================

    public Dynasty createDynasty(String surname, UUID founderId, long day) {
        Dynasty d = new Dynasty(surname, founderId, day);
        dynasties.put(d.id, d);
        if (founderId != null) memberToDynasty.put(founderId, d.id);
        LiveMineMod.LOGGER.info("Dynasty founded: {} (founder {})", surname, founderId);
        return d;
    }

    public void addToDynasty(UUID npcId, UUID dynastyId, int generation) {
        Dynasty d = dynasties.get(dynastyId);
        if (d == null) return;
        d.addMember(npcId, generation);
        memberToDynasty.put(npcId, dynastyId);

        if (d.isFullDynasty()) {
            LiveMineMod.LOGGER.info("Dynasty {} became a full dynasty (gen {})",
                d.surname, d.maxGeneration);
        }
    }

    /**
     * Автоматическое причисление ребёнка к династии родителей.
     */
    public void inheritFromParents(LiveNPCEntity child, ServerLevel level) {
        if (child == null) return;
        CompoundTag tag = LiveMineSavedData.get(level).loadNPCData(child.getUUID());

        UUID parentA = tag.hasUUID("parent_a_uuid") ? tag.getUUID("parent_a_uuid") : null;
        UUID parentB = tag.hasUUID("parent_b_uuid") ? tag.getUUID("parent_b_uuid") : null;

        UUID parentDynasty = null;
        int parentGen = 0;

        if (parentA != null) {
            UUID dId = memberToDynasty.get(parentA);
            if (dId != null) {
                parentDynasty = dId;
                parentGen = dynasties.get(dId).generationByMember.getOrDefault(parentA, 1);
            }
        }
        if (parentDynasty == null && parentB != null) {
            UUID dId = memberToDynasty.get(parentB);
            if (dId != null) {
                parentDynasty = dId;
                parentGen = dynasties.get(dId).generationByMember.getOrDefault(parentB, 1);
            }
        }

        if (parentDynasty != null) {
            addToDynasty(child.getUUID(), parentDynasty, parentGen + 1);
        }
    }

    // =========================================================================
    // Запросы
    // =========================================================================

    public Dynasty getDynasty(UUID dynastyId) {
        return dynasties.get(dynastyId);
    }

    public Dynasty getDynastyOf(UUID npcId) {
        UUID dId = memberToDynasty.get(npcId);
        return dId != null ? dynasties.get(dId) : null;
    }

    public boolean isInFullDynasty(UUID npcId) {
        Dynasty d = getDynastyOf(npcId);
        return d != null && d.isFullDynasty();
    }

    public List<Dynasty> getAllDynasties() {
        return new ArrayList<>(dynasties.values());
    }

    public int size() { return dynasties.size(); }

    // =========================================================================
    // NBT
    // =========================================================================

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        CompoundTag list = new CompoundTag();
        for (Dynasty d : dynasties.values()) {
            list.put(d.id.toString(), d.save());
        }
        root.put("dynasties", list);
        return root;
    }

    public void load(CompoundTag tag) {
        dynasties.clear();
        memberToDynasty.clear();

        CompoundTag list = tag.getCompound("dynasties");
        for (String k : list.getAllKeys()) {
            try {
                Dynasty d = Dynasty.load(list.getCompound(k));
                dynasties.put(d.id, d);
                for (UUID m : d.members) memberToDynasty.put(m, d.id);
            } catch (Exception ignored) {}
        }
    }

    public void clear() {
        dynasties.clear();
        memberToDynasty.clear();
    }
}
