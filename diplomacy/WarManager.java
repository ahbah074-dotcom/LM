package com.livemine.diplomacy;

import com.livemine.LiveMineMod;
import com.livemine.VillageManager;
import com.livemine.domain.VillageRecord;
import net.minecraft.core.BlockPos;
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
 * Менеджер войн между деревнями (ТЗ 20.0 §28.3).
 *
 * Война объявляется только при зафиксированной причине:
 *   - Ущерб жителю (убийство NPC)
 *   - Ущерб имуществу (разрушение дома)
 *   - Нападение на караван (casus belli)
 *
 * Причина записывается в хронике обеих деревень.
 * Объявление войны — после голосования (простое большинство).
 */
public final class WarManager {

    private static WarManager INSTANCE;

    public static final long MIN_WAR_DURATION_DAYS = 3;
    public static final long MAX_WAR_DURATION_DAYS = 30;

    public enum WarReason {
        NPC_KILLED("Убийство жителя"),
        PROPERTY_DAMAGE("Разрушение имущества"),
        CARAVAN_ATTACKED("Нападение на караван"),
        TERRITORY_VIOLATION("Нарушение границы"),
        ALLIANCE_BROKEN("Нарушение альянса");

        public final String ruName;
        WarReason(String ru) { this.ruName = ru; }
    }

    public static final class War {
        public UUID warId;
        public UUID aggressor;
        public UUID defender;
        public WarReason reason;
        public String details;
        public long declaredDay;
        public long peaceDay = -1;
        public boolean active;
        public boolean won;              // aggressor выиграл
        public int casualtiesAggressor;
        public int casualtiesDefender;

        public War(UUID aggressor, UUID defender, WarReason reason, String details, long day) {
            this.warId = UUID.randomUUID();
            this.aggressor = aggressor;
            this.defender = defender;
            this.reason = reason;
            this.details = details != null ? details : "";
            this.declaredDay = day;
            this.active = true;
        }

        public boolean involves(UUID villageId) {
            return aggressor.equals(villageId) || defender.equals(villageId);
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", warId);
            tag.putUUID("aggressor", aggressor);
            tag.putUUID("defender", defender);
            tag.putString("reason", reason.name());
            tag.putString("details", details);
            tag.putLong("declared", declaredDay);
            tag.putLong("peace", peaceDay);
            tag.putBoolean("active", active);
            tag.putBoolean("won", won);
            tag.putInt("casA", casualtiesAggressor);
            tag.putInt("casD", casualtiesDefender);
            return tag;
        }

        public static War load(CompoundTag tag) {
            War w = new War(
                tag.getUUID("aggressor"),
                tag.getUUID("defender"),
                WarReason.valueOf(tag.getString("reason")),
                tag.getString("details"),
                tag.getLong("declared")
            );
            w.warId = tag.getUUID("id");
            w.peaceDay = tag.getLong("peace");
            w.active = tag.getBoolean("active");
            w.won = tag.getBoolean("won");
            w.casualtiesAggressor = tag.getInt("casA");
            w.casualtiesDefender = tag.getInt("casD");
            return w;
        }
    }

    private final Map<UUID, War> wars = new HashMap<>();

    private WarManager() {}

    public static synchronized WarManager getInstance() {
        if (INSTANCE == null) INSTANCE = new WarManager();
        return INSTANCE;
    }

    // =========================================================================
    // Объявление войны
    // =========================================================================

    /**
     * Объявление войны. Требует причину.
     * Автоматически вызывает DiplomacyManager.declareWar.
     */
    public War declareWar(UUID aggressor, UUID defender, WarReason reason,
                           String details, long day) {
        if (reason == null) {
            LiveMineMod.LOGGER.warn("War declaration rejected: no reason");
            return null;
        }

        // Проверка, не в войне ли уже
        if (isAtWar(aggressor, defender)) return null;

        // Формируем casus belli
        String fullReason = reason.ruName + (details.isEmpty() ? "" : ": " + details);

        boolean accepted = DiplomacyManager.getInstance()
            .declareWar(aggressor, defender, fullReason, day);
        if (!accepted) return null;

        War war = new War(aggressor, defender, reason, details, day);
        wars.put(war.warId, war);

        // Расторгаем альянсы
        AllianceManager.getInstance().dissolveAlliance(aggressor, defender);

        LiveMineMod.LOGGER.info("WAR #{}: {} в†' {} ({})",
            war.warId.toString().substring(0, 8), aggressor, defender, fullReason);
        return war;
    }

    // =========================================================================
    // РњРёСЂ
    // =========================================================================

    public void declarePeace(UUID warId, long day, boolean aggressorWon) {
        War war = wars.get(warId);
        if (war == null || !war.active) return;

        war.active = false;
        war.peaceDay = day;
        war.won = aggressorWon;

        DiplomacyManager.getInstance().declarePeace(war.aggressor, war.defender);

        LiveMineMod.LOGGER.info("War #{} ended ({} {}). Winner: {}",
            warId.toString().substring(0, 8), day - war.declaredDay, "дней",
            aggressorWon ? "aggressor" : "defender");
    }

    // =========================================================================
    // РўРёРє
    // =========================================================================

    public void tick(ServerLevel level) {
        long currentDay = level.getDayTime() / 24000L;

        for (War war : wars.values()) {
            if (!war.active) continue;

            // Автоматическое завершение при очень долгой войне
            if (currentDay - war.declaredDay > MAX_WAR_DURATION_DAYS) {
                declarePeace(war.warId, currentDay, war.casualtiesDefender > war.casualtiesAggressor);
            }
        }
    }

    // =========================================================================
    // Учёт потерь
    // =========================================================================

    public void recordCasualty(UUID warId, UUID villageId) {
        War war = wars.get(warId);
        if (war == null || !war.active) return;

        if (war.aggressor.equals(villageId)) war.casualtiesAggressor++;
        else if (war.defender.equals(villageId)) war.casualtiesDefender++;
    }

    // =========================================================================
    // Запросы
    // =========================================================================

    public boolean isAtWar(UUID village1, UUID village2) {
        for (War w : wars.values()) {
            if (!w.active) continue;
            if (w.involves(village1) && w.involves(village2)) return true;
        }
        return false;
    }

    public War getWarBetween(UUID v1, UUID v2) {
        for (War w : wars.values()) {
            if (w.active && w.involves(v1) && w.involves(v2)) return w;
        }
        return null;
    }

    public List<War> getWarsFor(UUID villageId) {
        List<War> result = new ArrayList<>();
        for (War w : wars.values()) {
            if (w.involves(villageId)) result.add(w);
        }
        return result;
    }

    public List<War> getActiveWars() {
        List<War> result = new ArrayList<>();
        for (War w : wars.values()) if (w.active) result.add(w);
        return result;
    }

    public int size() { return wars.size(); }

    // =========================================================================
    // NBT
    // =========================================================================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (War w : wars.values()) list.add(w.save());
        tag.put("wars", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        wars.clear();
        ListTag list = tag.getList("wars", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            War w = War.load(list.getCompound(i));
            wars.put(w.warId, w);
        }
    }

    public void clear() { wars.clear(); }
}
