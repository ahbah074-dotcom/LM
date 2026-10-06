package com.livemine.dialogue;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Память диалогов NPC.
 *
 * v2.0: уровни знакомства (Stranger → Family), история реплик,
 *       счётчики разговоров.
 */
public final class NPCDialogMemory {

    public static final int MAX_RECENT = 10;

    public enum Familiarity {
        STRANGER     ("Незнакомец"),
        ACQUAINTANCE ("Знакомый"),
        FRIEND       ("Друг"),
        CLOSE_FRIEND ("Близкий друг"),
        FAMILY       ("Родня");

        public final String ruName;
        Familiarity(String ru) { this.ruName = ru; }
    }

    private final Deque<DialogSystem.DialogType> recentTypes = new ArrayDeque<>();
    private final Map<UUID, Integer> talkCounts = new HashMap<>();
    private final Map<UUID, Long> lastTalkDay = new HashMap<>();

    public NPCDialogMemory() {}

    public void remember(DialogSystem.DialogType type) {
        if (type == null) return;
        recentTypes.addLast(type);
        while (recentTypes.size() > MAX_RECENT) recentTypes.removeFirst();
    }

    public boolean wasRecentlyUsed(DialogSystem.DialogType type) {
        return type != null && recentTypes.contains(type);
    }

    public void recordTalkWith(UUID playerId, long currentDay) {
        if (playerId == null) return;
        talkCounts.merge(playerId, 1, Integer::sum);
        lastTalkDay.put(playerId, currentDay);
    }

    public int getTalkCount(UUID playerId) {
        return talkCounts.getOrDefault(playerId, 0);
    }

    public long getLastTalkDay(UUID playerId) {
        return lastTalkDay.getOrDefault(playerId, -1L);
    }

    public boolean knowsPlayer(UUID playerId) {
        return talkCounts.getOrDefault(playerId, 0) > 0;
    }

    public boolean isCloseFriend(UUID playerId) {
        return talkCounts.getOrDefault(playerId, 0) >= 15;
    }

    /**
     * v2.0: возвращает уровень знакомства с игроком.
     */
    public Familiarity getFamiliarity(UUID playerId) {
        int count = talkCounts.getOrDefault(playerId, 0);
        if (count == 0) return Familiarity.STRANGER;
        if (count < 5)  return Familiarity.ACQUAINTANCE;
        if (count < 15) return Familiarity.FRIEND;
        if (count < 30) return Familiarity.CLOSE_FRIEND;
        return Familiarity.FAMILY;
    }

    /**
     * v2.0: копирование (для загрузки из NBT).
     */
    public void copyFrom(NPCDialogMemory other) {
        if (other == null) return;
        recentTypes.clear();
        recentTypes.addAll(other.recentTypes);
        talkCounts.clear();
        talkCounts.putAll(other.talkCounts);
        lastTalkDay.clear();
        lastTalkDay.putAll(other.lastTalkDay);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        ListTag types = new ListTag();
        for (DialogSystem.DialogType t : recentTypes) {
            CompoundTag tt = new CompoundTag();
            tt.putString("t", t.name());
            types.add(tt);
        }
        tag.put("recentTypes", types);

        CompoundTag counts = new CompoundTag();
        for (var e : talkCounts.entrySet()) {
            counts.putInt(e.getKey().toString(), e.getValue());
        }
        tag.put("talkCounts", counts);

        CompoundTag lastDays = new CompoundTag();
        for (var e : lastTalkDay.entrySet()) {
            lastDays.putLong(e.getKey().toString(), e.getValue());
        }
        tag.put("lastTalkDay", lastDays);

        return tag;
    }

    public static NPCDialogMemory load(CompoundTag tag) {
        NPCDialogMemory m = new NPCDialogMemory();
        if (tag == null) return m;

        ListTag types = tag.getList("recentTypes", Tag.TAG_COMPOUND);
        for (int i = 0; i < types.size(); i++) {
            try {
                m.recentTypes.addLast(
                    DialogSystem.DialogType.valueOf(types.getCompound(i).getString("t")));
            } catch (IllegalArgumentException ignored) {}
        }

        CompoundTag counts = tag.getCompound("talkCounts");
        for (String k : counts.getAllKeys()) {
            try {
                m.talkCounts.put(UUID.fromString(k), counts.getInt(k));
            } catch (IllegalArgumentException ignored) {}
        }

        CompoundTag lastDays = tag.getCompound("lastTalkDay");
        for (String k : lastDays.getAllKeys()) {
            try {
                m.lastTalkDay.put(UUID.fromString(k), lastDays.getLong(k));
            } catch (IllegalArgumentException ignored) {}
        }

        return m;
    }

    public void clear() {
        recentTypes.clear();
        talkCounts.clear();
        lastTalkDay.clear();
    }
}