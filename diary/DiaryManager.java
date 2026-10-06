package com.livemine.diary;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Менеджер личного дневника игрока (ТЗ 20.0 §34).
 *
 * Максимум 500 записей. Обрезает самые старые при переполнении.
 */
public final class DiaryManager {

    private static DiaryManager INSTANCE;

    public static final int MAX_ENTRIES = 500;

    public enum EventType {
        VILLAGE_FOUNDED  ("§6Деревня основана"),
        MASTER_ACHIEVED  ("§bМастер достигнут"),
        RAID_DEFENDED    ("§aРейд отражён"),
        RAID_LOST        ("§cРейд проигран"),
        ALLIANCE_FORMED  ("§dАльянс заключён"),
        WAR_DECLARED     ("§4Война объявлена"),
        WAR_ENDED        ("§7Мир заключён"),
        BUILDING_COMPLETED("§eПостройка завершена"),
        NPC_BORN         ("§aРождение NPC"),
        NPC_DIED         ("§4Смерть NPC"),
        HOLIDAY          ("§eПраздник"),
        EXPEDITION       ("§9Экспедиция"),
        CARAVAN          ("§6Караван"),
        CUSTOM           ("§7Событие");

        public final String prefix;
        EventType(String prefix) { this.prefix = prefix; }
    }

    public static final class Entry {
        public final long day;
        public final EventType type;
        public final String text;
        public final long timestamp;

        public Entry(long day, EventType type, String text) {
            this.day = day;
            this.type = type;
            this.text = text != null ? text : "";
            this.timestamp = System.currentTimeMillis();
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("day", day);
            tag.putString("type", type.name());
            tag.putString("text", text);
            tag.putLong("ts", timestamp);
            return tag;
        }

        public static Entry load(CompoundTag tag) {
            EventType type;
            try {
                type = EventType.valueOf(tag.getString("type"));
            } catch (IllegalArgumentException e) {
                type = EventType.CUSTOM;
            }
            return new Entry(tag.getLong("day"), type, tag.getString("text"));
        }
    }

    private final Map<UUID, List<Entry>> diaries = new HashMap<>();

    private DiaryManager() {}

    public static synchronized DiaryManager getInstance() {
        if (INSTANCE == null) INSTANCE = new DiaryManager();
        return INSTANCE;
    }

    // =========================================================================
    // Запись
    // =========================================================================

    public void record(UUID playerId, EventType type, String text, long day) {
        if (playerId == null) return;
        List<Entry> diary = diaries.computeIfAbsent(playerId, k -> new ArrayList<>());
        diary.add(new Entry(day, type, text));
        while (diary.size() > MAX_ENTRIES) diary.remove(0);
    }

    public void record(ServerPlayer player, EventType type, String text, long day) {
        if (player == null) return;
        record(player.getUUID(), type, text, day);
    }

    // =========================================================================
    // Триггеры
    // =========================================================================

    public void onVillageFounded(ServerPlayer player, String villageName, long day) {
        record(player, EventType.VILLAGE_FOUNDED, "Основана деревня " + villageName, day);
    }

    public void onMasterAchieved(ServerPlayer player, String npcName, String skill, long day) {
        record(player, EventType.MASTER_ACHIEVED, npcName + " достиг уровня 10 в " + skill, day);
    }

    public void onRaidDefended(ServerPlayer player, String villageName, long day) {
        record(player, EventType.RAID_DEFENDED, "Отражён рейд на " + villageName, day);
    }

    public void onAllianceFormed(ServerPlayer player, String v1, String v2, long day) {
        record(player, EventType.ALLIANCE_FORMED, v1 + " + " + v2, day);
    }

    public void onWarDeclared(ServerPlayer player, String aggressor, String defender, String reason, long day) {
        record(player, EventType.WAR_DECLARED, aggressor + " в†' " + defender + " (" + reason + ")", day);
    }

    public void onBuildingCompleted(ServerPlayer player, String buildingName, String villageName, long day) {
        record(player, EventType.BUILDING_COMPLETED, buildingName + " РІ " + villageName, day);
    }

    // =========================================================================
    // Показ
    // =========================================================================

    public void showDiary(ServerPlayer player) {
        if (player == null) return;
        List<Entry> diary = diaries.getOrDefault(player.getUUID(), new ArrayList<>());
        if (diary.isEmpty()) {
            player.sendSystemMessage(Component.literal("§7Дневник пуст."));
            return;
        }

        player.sendSystemMessage(Component.literal("§6=== Дневник LiveMine ==="));
        int start = Math.max(0, diary.size() - 20);
        for (int i = start; i < diary.size(); i++) {
            Entry e = diary.get(i);
            player.sendSystemMessage(Component.literal(
                "В§7[" + e.day + "] В§f" + e.type.prefix + ": В§r" + e.text));
        }
        if (diary.size() > 20) {
            player.sendSystemMessage(Component.literal("§7...и ещё " + (diary.size() - 20) + " записей"));
        }
    }

    // =========================================================================
    // Запросы
    // =========================================================================

    public List<Entry> getEntries(UUID playerId) {
        return diaries.getOrDefault(playerId, new ArrayList<>());
    }

    public List<Entry> getRecent(UUID playerId, int count) {
        List<Entry> diary = getEntries(playerId);
        int start = Math.max(0, diary.size() - count);
        return new ArrayList<>(diary.subList(start, diary.size()));
    }

    public int getEntryCount(UUID playerId) {
        List<Entry> diary = diaries.get(playerId);
        return diary != null ? diary.size() : 0;
    }

    // =========================================================================
    // NBT
    // =========================================================================

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();

        for (Map.Entry<UUID, List<Entry>> e : diaries.entrySet()) {
            CompoundTag pt = new CompoundTag();
            pt.putUUID("player", e.getKey());

            ListTag entries = new ListTag();
            for (Entry entry : e.getValue()) entries.add(entry.save());
            pt.put("entries", entries);

            list.add(pt);
        }
        root.put("diaries", list);
        return root;
    }

    public void load(CompoundTag tag) {
        diaries.clear();
        ListTag list = tag.getList("diaries", Tag.TAG_COMPOUND);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag pt = list.getCompound(i);
            UUID playerId = pt.getUUID("player");
            List<Entry> diary = new ArrayList<>();

            ListTag entriesTag = pt.getList("entries", Tag.TAG_COMPOUND);
            for (int j = 0; j < entriesTag.size(); j++) {
                diary.add(Entry.load(entriesTag.getCompound(j)));
            }
            diaries.put(playerId, diary);
        }
    }

    public void clear() { diaries.clear(); }
}
