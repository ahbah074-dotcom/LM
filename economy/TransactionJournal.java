package com.livemine.economy;

import com.livemine.LiveMineMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Журнал всех транзакций.
 *
 * 8 типов: PURCHASE, SALE, BARTER, GIFT, TAX, SALARY, INHERITANCE, FINE.
 *
 * Идемпотентность через ключ (from:to:commodity:day:type) —
 * одна и та же операция не выполнится дважды.
 *
 * При переполнении (5000+) — старые записи удаляются.
 */
public final class TransactionJournal {

    public static final int MAX_JOURNAL_SIZE = 5000;
    public static final int MAX_CLEANUP_DAYS = 365;

    private static TransactionJournal INSTANCE;

    public enum Type {
        PURCHASE, SALE, BARTER, GIFT, TAX, SALARY, INHERITANCE, FINE
    }

    public static final class Transaction {
        public UUID transactionId;
        public Type type;
        public UUID fromUUID;
        public UUID toUUID;
        public String commodityId;
        public int quantity;
        public float price;
        public long day;
        public long timestamp;
        public boolean processed;

        public Transaction(Type type, UUID from, UUID to, String commodity,
                            int qty, float price, long day) {
            this.transactionId = UUID.randomUUID();
            this.type = type;
            this.fromUUID = from;
            this.toUUID = to;
            this.commodityId = commodity;
            this.quantity = qty;
            this.price = price;
            this.day = day;
            this.timestamp = System.currentTimeMillis();
            this.processed = false;
        }

        public String getIdempotencyKey() {
            return fromUUID + ":" + toUUID + ":" + commodityId + ":" + day + ":" + type.name();
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", transactionId);
            tag.putString("type", type.name());
            tag.putUUID("from", fromUUID);
            tag.putUUID("to", toUUID);
            tag.putString("commodity", commodityId);
            tag.putInt("qty", quantity);
            tag.putFloat("price", price);
            tag.putLong("day", day);
            tag.putLong("ts", timestamp);
            tag.putBoolean("processed", processed);
            return tag;
        }

        public static Transaction load(CompoundTag tag) {
            Transaction t = new Transaction(
                Type.valueOf(tag.getString("type")),
                tag.getUUID("from"),
                tag.getUUID("to"),
                tag.getString("commodity"),
                tag.getInt("qty"),
                tag.getFloat("price"),
                tag.getLong("day")
            );
            t.transactionId = tag.getUUID("id");
            t.timestamp = tag.getLong("ts");
            t.processed = tag.getBoolean("processed");
            return t;
        }
    }

    private final List<Transaction> journal = new ArrayList<>();
    private final Set<String> processedKeys = new HashSet<>();

    private TransactionJournal() {}

    public static synchronized TransactionJournal getInstance() {
        if (INSTANCE == null) INSTANCE = new TransactionJournal();
        return INSTANCE;
    }

    // =========================================================================
    // Запись
    // =========================================================================

    public Transaction record(Type type, UUID from, UUID to, String commodity,
                               int qty, float price, long day) {
        String key = from + ":" + to + ":" + commodity + ":" + day + ":" + type.name();
        if (processedKeys.contains(key)) {
            LiveMineMod.LOGGER.debug("Duplicate transaction skipped: {}", key);
            return null;
        }
        processedKeys.add(key);

        Transaction t = new Transaction(type, from, to, commodity, qty, price, day);
        journal.add(t);
        if (journal.size() > MAX_JOURNAL_SIZE) journal.remove(0);
        return t;
    }

    // =========================================================================
    // Чтение
    // =========================================================================

    public List<Transaction> getTransactionsByPlayer(UUID playerUUID) {
        List<Transaction> result = new ArrayList<>();
        for (Transaction t : journal) {
            if (t.fromUUID.equals(playerUUID) || t.toUUID.equals(playerUUID)) result.add(t);
        }
        return result;
    }

    public List<Transaction> getTransactionsByDay(long day) {
        List<Transaction> result = new ArrayList<>();
        for (Transaction t : journal) if (t.day == day) result.add(t);
        return result;
    }

    public float getTotalVolume(long day) {
        float total = 0;
        for (Transaction t : journal) if (t.day == day) total += t.price * t.quantity;
        return total;
    }

    public List<Transaction> getRecent(int count) {
        int start = Math.max(0, journal.size() - count);
        return Collections.unmodifiableList(journal.subList(start, journal.size()));
    }

    public int getJournalSize() { return journal.size(); }

    // =========================================================================
    // Очистка
    // =========================================================================

    public void cleanup(long currentDay) {
        journal.removeIf(t -> currentDay - t.day > MAX_CLEANUP_DAYS);
        processedKeys.removeIf(key -> {
            String[] parts = key.split(":");
            if (parts.length >= 4) {
                try { return currentDay - Long.parseLong(parts[3]) > MAX_CLEANUP_DAYS; }
                catch (NumberFormatException e) { return true; }
            }
            return true;
        });
    }

    // =========================================================================
    // NBT
    // =========================================================================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Transaction t : journal) list.add(t.save());
        tag.put("journal", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        journal.clear();
        processedKeys.clear();
        ListTag list = tag.getList("journal", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Transaction t = Transaction.load(list.getCompound(i));
            journal.add(t);
            processedKeys.add(t.getIdempotencyKey());
        }
    }

    public void clear() {
        journal.clear();
        processedKeys.clear();
    }
}
