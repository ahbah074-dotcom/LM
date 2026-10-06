package com.livemine.diplomacy;

import com.livemine.LiveMineMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Дипломатические отношения между деревнями (ТЗ 20.0 §28.2).
 *
 * 4 состояния: NEUTRAL / FRIEND / ALLIANCE / WAR.
 * Ключ пары: min(uuid1, uuid2) + "_" + max(uuid1, uuid2).
 */
public final class DiplomacyManager {

    private static DiplomacyManager INSTANCE;

    public enum State {
        NEUTRAL("Нейтралитет"),
        FRIEND("Дружба"),
        ALLIANCE("Альянс"),
        WAR("Р'РѕР№РЅР°");

        public final String ruName;
        State(String ru) { this.ruName = ru; }
    }

    public static final class Relation {
        public State state = State.NEUTRAL;
        public double trust = 50.0;
        public long establishedDay = 0;
        public String warReason = "";
        public long warDeclaredDay = 0;
    }

    private final Map<String, Relation> relations = new HashMap<>();

    private DiplomacyManager() {}

    public static synchronized DiplomacyManager getInstance() {
        if (INSTANCE == null) INSTANCE = new DiplomacyManager();
        return INSTANCE;
    }

    // =========================================================================
    // Ключ
    // =========================================================================

    private static String key(UUID a, UUID b) {
        String sa = a.toString();
        String sb = b.toString();
        return sa.compareTo(sb) < 0 ? sa + "_" + sb : sb + "_" + sa;
    }

    // =========================================================================
    // Чтение
    // =========================================================================

    public Relation getRelation(UUID village1, UUID village2) {
        if (village1 == null || village2 == null) return new Relation();
        return relations.computeIfAbsent(key(village1, village2), k -> new Relation());
    }

    public State getState(UUID village1, UUID village2) {
        return getRelation(village1, village2).state;
    }

    public boolean atWar(UUID village1, UUID village2) {
        return getState(village1, village2) == State.WAR;
    }

    public boolean allied(UUID village1, UUID village2) {
        return getState(village1, village2) == State.ALLIANCE;
    }

    // =========================================================================
    // Изменения
    // =========================================================================

    public void addTrust(UUID v1, UUID v2, double amount) {
        Relation r = getRelation(v1, v2);
        r.trust = Math.max(0, Math.min(100, r.trust + amount));

        if (r.state == State.NEUTRAL && r.trust >= 60.0) {
            r.state = State.FRIEND;
            LiveMineMod.LOGGER.info("Villages {} and {} became friends", v1, v2);
        }
    }

    public boolean declareWar(UUID aggressor, UUID defender, String reason, long day) {
        if (reason == null || reason.isEmpty()) {
            LiveMineMod.LOGGER.warn(
                "War declaration rejected: no reason given ({} в†' {})", aggressor, defender);
            return false;
        }
        Relation r = getRelation(aggressor, defender);
        r.state = State.WAR;
        r.warReason = reason;
        r.warDeclaredDay = day;
        r.trust = Math.max(0, r.trust - 40);

        LiveMineMod.LOGGER.info("WAR declared: {} в†' {} (reason: {})", aggressor, defender, reason);
        return true;
    }

    public void declarePeace(UUID v1, UUID v2) {
        Relation r = getRelation(v1, v2);
        if (r.state == State.WAR) {
            r.state = State.NEUTRAL;
            r.warReason = "";
            r.trust = 30.0;
            LiveMineMod.LOGGER.info("Peace declared: {} ↔ {}", v1, v2);
        }
    }

    public void formAlliance(UUID v1, UUID v2, long day) {
        Relation r = getRelation(v1, v2);
        if (r.state == State.WAR) return;
        r.state = State.ALLIANCE;
        r.establishedDay = day;
        r.trust = Math.max(r.trust, 75.0);
        LiveMineMod.LOGGER.info("Alliance formed: {} ↔ {}", v1, v2);
    }

    // =========================================================================
    // NBT
    // =========================================================================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();

        for (Map.Entry<String, Relation> e : relations.entrySet()) {
            CompoundTag rt = new CompoundTag();
            rt.putString("key", e.getKey());
            rt.putString("state", e.getValue().state.name());
            rt.putDouble("trust", e.getValue().trust);
            rt.putLong("est", e.getValue().establishedDay);
            rt.putString("warReason", e.getValue().warReason);
            rt.putLong("warDay", e.getValue().warDeclaredDay);
            list.add(rt);
        }
        tag.put("relations", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        relations.clear();
        ListTag list = tag.getList("relations", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag rt = list.getCompound(i);
            Relation r = new Relation();
            try {
                r.state = State.valueOf(rt.getString("state"));
            } catch (IllegalArgumentException e) {
                r.state = State.NEUTRAL;
            }
            r.trust = rt.getDouble("trust");
            r.establishedDay = rt.getLong("est");
            r.warReason = rt.getString("warReason");
            r.warDeclaredDay = rt.getLong("warDay");
            relations.put(rt.getString("key"), r);
        }
    }

    public void clear() { relations.clear(); }
    public int size() { return relations.size(); }
}
