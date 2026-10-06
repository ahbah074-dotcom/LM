package com.livemine.ranking;

import com.livemine.LiveMineMod;
import com.livemine.VillageData;
import com.livemine.VillageManager;
import com.livemine.domain.VillageRecord;
import com.livemine.diplomacy.WarManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Менеджер рейтинга деревень.
 *
 * v2.0: использует rawId для VillageData.
 */
public final class RankingManager {

    private static RankingManager INSTANCE;

    public static final long UPDATE_INTERVAL_DAYS = 50;

    public static final class VillageRanking {
        public final UUID villageId;
        public String name;
        public double totalScore;
        public double skillScore;
        public double economyScore;
        public double warScore;
        public double populationScore;
        public double achievementScore;
        public int position;

        public VillageRanking(UUID villageId, String name) {
            this.villageId = villageId;
            this.name = name;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", villageId);
            tag.putString("name", name);
            tag.putDouble("total", totalScore);
            tag.putDouble("skill", skillScore);
            tag.putDouble("economy", economyScore);
            tag.putDouble("war", warScore);
            tag.putDouble("pop", populationScore);
            tag.putDouble("ach", achievementScore);
            tag.putInt("pos", position);
            return tag;
        }

        public static VillageRanking load(CompoundTag tag) {
            VillageRanking r = new VillageRanking(tag.getUUID("id"), tag.getString("name"));
            r.totalScore = tag.getDouble("total");
            r.skillScore = tag.getDouble("skill");
            r.economyScore = tag.getDouble("economy");
            r.warScore = tag.getDouble("war");
            r.populationScore = tag.getDouble("pop");
            r.achievementScore = tag.getDouble("ach");
            r.position = tag.getInt("pos");
            return r;
        }
    }

    private final List<VillageRanking> rankings = new ArrayList<>();
    private long lastUpdateDay = -1;

    private RankingManager() {}

    public static synchronized RankingManager getInstance() {
        if (INSTANCE == null) INSTANCE = new RankingManager();
        return INSTANCE;
    }

    public void tick(ServerLevel level) {
        long currentDay = level.getDayTime() / 24000L;
        if (lastUpdateDay < 0 || currentDay - lastUpdateDay >= UPDATE_INTERVAL_DAYS) {
            lastUpdateDay = currentDay;
            recalculate(level);
        }
    }

    public void recalculate(ServerLevel level) {
        rankings.clear();

        for (VillageRecord v : VillageManager.getInstance().getAllVillages()) {
            if (v.isAbandoned()) continue;
            rankings.add(calculateRanking(v, level));
        }

        rankings.sort(Comparator.comparingDouble((VillageRanking r) -> r.totalScore).reversed());

        for (int i = 0; i < rankings.size(); i++) {
            rankings.get(i).position = i + 1;
        }

        LiveMineMod.LOGGER.info("Ranking recalculated: {} villages", rankings.size());
    }

    private VillageRanking calculateRanking(VillageRecord v, ServerLevel level) {
        VillageRanking r = new VillageRanking(v.id(), v.name());

        VillageData vd = new VillageData(v.rawId(), level);

        double totalSkill = 0;
        if (vd.exists()) {
            for (UUID npcId : vd.getResidentNpcIds()) {
                var npc = com.livemine.ai.NPCRegistry.getNPC(npcId);
                if (npc != null && npc.getSkills() != null) {
                    for (var type : com.livemine.NPCSkills.SkillType.values()) {
                        totalSkill += npc.getSkills().getLevel(type);
                    }
                }
            }
        }
        r.skillScore = totalSkill;

        r.economyScore = vd.exists() ? vd.getResources() : v.budget();

        int wins = 0;
        WarManager wm = WarManager.getInstance();
        if (wm != null) {
            for (var war : wm.getWarsFor(v.id())) {
                if (!war.active && war.won
                    && (war.aggressor.equals(v.id()) || war.defender.equals(v.id()))) {
                    wins++;
                }
            }
        }
        r.warScore = wins * 100.0;
        r.populationScore = v.population() * 10.0;
        r.achievementScore = 0;

        r.totalScore = r.skillScore + r.economyScore
            + r.warScore + r.populationScore + r.achievementScore;

        return r;
    }

    public List<VillageRanking> getTop(int count) {
        int end = Math.min(count, rankings.size());
        return new ArrayList<>(rankings.subList(0, end));
    }

    public List<VillageRanking> getTop3() { return getTop(3); }
    public List<VillageRanking> getAll() { return new ArrayList<>(rankings); }
    public List<VillageRanking> getRanking() { return getAll(); }

    public VillageRanking getRanking(UUID villageId) {
        for (VillageRanking r : rankings) {
            if (r.villageId.equals(villageId)) return r;
        }
        return null;
    }

    public int getPosition(UUID villageId) {
        VillageRanking r = getRanking(villageId);
        return r != null ? r.position : -1;
    }

    public int getRank(UUID villageId) { return getPosition(villageId); }
    public int size() { return rankings.size(); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("lastUpdate", lastUpdateDay);
        ListTag list = new ListTag();
        for (VillageRanking r : rankings) list.add(r.save());
        tag.put("rankings", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        rankings.clear();
        lastUpdateDay = tag.getLong("lastUpdate");
        ListTag list = tag.getList("rankings", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            rankings.add(VillageRanking.load(list.getCompound(i)));
        }
    }

    public void clear() {
        rankings.clear();
        lastUpdateDay = -1;
    }
}