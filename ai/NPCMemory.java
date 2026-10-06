package com.livemine.ai;

import com.livemine.MemoryEntry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Личная память NPC.
 *
 * Хранит:
 *   - 256 обычных записей (LRU-вытеснение)
 *   - 64 критических записи (не вытесняются)
 *
 * Критические: семейные, имущественные, долговые — то, что нельзя терять.
 */
public final class NPCMemory {

    public static final int MAX_ENTRIES = 256;
    public static final int MAX_CRITICAL = 64;

    private final List<MemoryEntry> entries = new ArrayList<>();
    private final List<MemoryEntry> criticalEntries = new ArrayList<>();

    // =========================================================================
    // Запись
    // =========================================================================

    public void addEntry(MemoryEntry entry) {
        if (entry == null) return;

        if (entry.isCritical()) {
            criticalEntries.add(entry);
            if (criticalEntries.size() > MAX_CRITICAL) {
                // Удаляем самую старую, если она не семейная/имущественная
                criticalEntries.sort(Comparator.comparingLong(MemoryEntry::getTimestamp).reversed());
                while (criticalEntries.size() > MAX_CRITICAL) {
                    criticalEntries.remove(criticalEntries.size() - 1);
                }
            }
        } else {
            entries.add(entry);
            if (entries.size() > MAX_ENTRIES) {
                entries.sort(Comparator.comparingLong(MemoryEntry::getTimestamp).reversed());
                while (entries.size() > MAX_ENTRIES) {
                    entries.remove(entries.size() - 1);
                }
            }
        }
    }

    // =========================================================================
    // Чтение
    // =========================================================================

    public List<MemoryEntry> getRecentEntries(int count) {
        List<MemoryEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingLong(MemoryEntry::getTimestamp).reversed());
        return sorted.subList(0, Math.min(count, sorted.size()));
    }

    public List<MemoryEntry> getAllEntries() {
        List<MemoryEntry> all = new ArrayList<>(entries);
        all.addAll(criticalEntries);
        return all;
    }

    public List<MemoryEntry> getCriticalEntries() {
        return new ArrayList<>(criticalEntries);
    }

    public List<MemoryEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public int getEntryCount() {
        return entries.size();
    }

    public int getCriticalCount() {
        return criticalEntries.size();
    }

    // =========================================================================
    // NBT
    // =========================================================================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        ListTag entryList = new ListTag();
        for (MemoryEntry e : entries) entryList.add(e.save());
        tag.put("entries", entryList);

        ListTag critList = new ListTag();
        for (MemoryEntry e : criticalEntries) critList.add(e.save());
        tag.put("critical", critList);

        return tag;
    }

    public static NPCMemory load(CompoundTag tag) {
        NPCMemory mem = new NPCMemory();
        if (tag == null) return mem;

        if (tag.contains("entries")) {
            ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                MemoryEntry e = MemoryEntry.load(list.getCompound(i));
                if (e != null) mem.entries.add(e);
            }
        }

        if (tag.contains("critical")) {
            ListTag list = tag.getList("critical", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                MemoryEntry e = MemoryEntry.load(list.getCompound(i));
                if (e != null) mem.criticalEntries.add(e);
            }
        }

        return mem;
    }

    public void clear() {
        entries.clear();
        criticalEntries.clear();
    }
}
