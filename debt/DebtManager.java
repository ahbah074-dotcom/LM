package com.livemine.debt;

import com.livemine.LiveMineMod;
import com.livemine.ai.NPCRegistry;
import com.livemine.ai.NPCRelationship;
import com.livemine.entity.LiveNPCEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Менеджер долгов и кредитов (ТЗ 20.0 §45).
 *
 * Использует LiveNPCEntity для создания долгов. При просрочке > 30 дней
 * снижает доверие между NPC.
 */
public final class DebtManager {

    private static DebtManager INSTANCE;

    public static final long OVERDUE_DAYS = 30;
    public static final int MAX_DEBTS_PER_NPC = 16;
    public static final int PUBLIC_THRESHOLD = 5;

    public enum DebtType { RESOURCE, TOOL, FOOD, COINS }

    public enum DebtState { ACTIVE, OVERDUE, RESOLVED, FORGIVEN, DISPUTED }

    public static final class Debt {
        public UUID debtId;
        public UUID creditorId;
        public UUID debtorId;
        public DebtType type;
        public String itemId;
        public int amount;
        public long createdDay;
        public long dueDay;
        public DebtState state;
        public String resolution;
        public boolean isPublic;

        public Debt(UUID creditorId, UUID debtorId, DebtType type,
                    String itemId, int amount, long day) {
            this.debtId = UUID.randomUUID();
            this.creditorId = creditorId;
            this.debtorId = debtorId;
            this.type = type;
            this.itemId = itemId != null ? itemId : "";
            this.amount = amount;
            this.createdDay = day;
            this.dueDay = day + OVERDUE_DAYS;
            this.state = DebtState.ACTIVE;
            this.resolution = "";
            this.isPublic = amount >= PUBLIC_THRESHOLD;
        }

        public boolean isOverdue(long currentDay) {
            return state == DebtState.ACTIVE && currentDay > dueDay;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", debtId);
            tag.putUUID("creditor", creditorId);
            tag.putUUID("debtor", debtorId);
            tag.putString("type", type.name());
            tag.putString("item", itemId);
            tag.putInt("amount", amount);
            tag.putLong("created", createdDay);
            tag.putLong("due", dueDay);
            tag.putString("state", state.name());
            tag.putString("resolution", resolution);
            tag.putBoolean("public", isPublic);
            return tag;
        }

        public static Debt load(CompoundTag tag) {
            Debt d = new Debt(
                tag.getUUID("creditor"),
                tag.getUUID("debtor"),
                DebtType.valueOf(tag.getString("type")),
                tag.getString("item"),
                tag.getInt("amount"),
                tag.getLong("created")
            );
            d.debtId = tag.getUUID("id");
            d.dueDay = tag.getLong("due");
            try {
                d.state = DebtState.valueOf(tag.getString("state"));
            } catch (IllegalArgumentException e) {
                d.state = DebtState.ACTIVE;
            }
            d.resolution = tag.getString("resolution");
            d.isPublic = tag.getBoolean("public");
            return d;
        }
    }

    private final Map<UUID, Debt> debts = new HashMap<>();

    private DebtManager() {}

    public static synchronized DebtManager getInstance() {
        if (INSTANCE == null) INSTANCE = new DebtManager();
        return INSTANCE;
    }

    // =========================================================================
    // Создание
    // =========================================================================

    public Debt createDebt(LiveNPCEntity creditor, LiveNPCEntity debtor,
                            DebtType type, String itemId, int amount, ServerLevel level) {
        if (creditor == null || debtor == null) return null;
        if (creditor.getUUID().equals(debtor.getUUID())) return null;

        long day = level.getDayTime() / 24000L;

        long activeDebts = debts.values().stream()
            .filter(d -> d.debtorId.equals(debtor.getUUID()) && d.state == DebtState.ACTIVE)
            .count();
        if (activeDebts >= MAX_DEBTS_PER_NPC) {
            LiveMineMod.LOGGER.debug("Too many debts for {}", debtor.getCustomNameTag());
            return null;
        }

        Debt d = new Debt(creditor.getUUID(), debtor.getUUID(), type, itemId, amount, day);
        debts.put(d.debtId, d);

        LiveMineMod.LOGGER.info("Debt created: {} owes {} {} to {}",
            debtor.getCustomNameTag(), amount, itemId, creditor.getCustomNameTag());
        return d;
    }

    public Debt createDebt(UUID creditorId, UUID debtorId, String itemId, int amount, long day) {
        if (creditorId == null || debtorId == null) return null;
        if (creditorId.equals(debtorId)) return null;

        Debt d = new Debt(creditorId, debtorId, DebtType.RESOURCE, itemId, amount, day);
        debts.put(d.debtId, d);
        return d;
    }

    // =========================================================================
    // Возврат / изменение состояния
    // =========================================================================

    public void resolve(UUID debtId) {
        Debt d = debts.get(debtId);
        if (d != null) {
            d.state = DebtState.RESOLVED;
            d.resolution = "Возвращено";
        }
    }

    public void forgive(UUID debtId) {
        Debt d = debts.get(debtId);
        if (d != null) {
            d.state = DebtState.FORGIVEN;
            d.resolution = "Прощено";
        }
    }

    public void markDisputed(UUID debtId) {
        Debt d = debts.get(debtId);
        if (d != null) {
            d.state = DebtState.DISPUTED;
            d.resolution = "Передано лидеру";
        }
    }

    // =========================================================================
    // РўРёРє
    // =========================================================================

    public void tick(long currentDay) {
        for (Debt d : debts.values()) {
            if (d.isOverdue(currentDay)) {
                d.state = DebtState.OVERDUE;
                LiveMineMod.LOGGER.info("Debt {} overdue", d.debtId);

                LiveNPCEntity debtor = NPCRegistry.getNPC(d.debtorId);
                LiveNPCEntity creditor = NPCRegistry.getNPC(d.creditorId);
                if (debtor != null && creditor != null) {
                    NPCRelationship.addTrust(d.debtorId, d.creditorId, -15);
                }
            }
        }
    }

    // =========================================================================
    // Запросы
    // =========================================================================

    public List<Debt> getDebtsOf(UUID npcId) {
        List<Debt> result = new ArrayList<>();
        for (Debt d : debts.values()) {
            if (d.creditorId.equals(npcId) || d.debtorId.equals(npcId)) result.add(d);
        }
        return result;
    }

    public List<Debt> getActiveDebts() {
        List<Debt> result = new ArrayList<>();
        for (Debt d : debts.values()) {
            if (d.state == DebtState.ACTIVE || d.state == DebtState.OVERDUE) {
                result.add(d);
            }
        }
        return result;
    }

    public List<Debt> getOverdueDebts() {
        List<Debt> result = new ArrayList<>();
        for (Debt d : debts.values()) {
            if (d.state == DebtState.OVERDUE) result.add(d);
        }
        return result;
    }

    public int getTotalOwedBy(UUID borrowerId) {
        int total = 0;
        for (Debt d : debts.values()) {
            if (d.debtorId.equals(borrowerId)
                && (d.state == DebtState.ACTIVE || d.state == DebtState.OVERDUE)) {
                total += d.amount;
            }
        }
        return total;
    }

    public Debt getDebt(UUID debtId) { return debts.get(debtId); }
    public int size() { return debts.size(); }

    // =========================================================================
    // NBT
    // =========================================================================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Debt d : debts.values()) list.add(d.save());
        tag.put("debts", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        debts.clear();
        ListTag list = tag.getList("debts", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Debt d = Debt.load(list.getCompound(i));
            debts.put(d.debtId, d);
        }
    }

    public void clear() { debts.clear(); }
}
