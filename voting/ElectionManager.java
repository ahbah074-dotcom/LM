package com.livemine.voting;

import com.livemine.LiveMineMod;
import com.livemine.NPCSkills;
import com.livemine.VillageData;
import com.livemine.entity.LiveNPCEntity;
import com.livemine.family.DynastyManager;
import net.minecraft.server.level.ServerLevel;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Менеджер выборов лидера.
 *
 * v2.0: использует rawId ("village_<asLong>").
 */
public final class ElectionManager {

    private static ElectionManager INSTANCE;

    public static final float DYNASTY_BONUS = 0.10f;
    public static final float LOYALTY_THRESHOLD = 20.0f;

    private ElectionManager() {}

    public static synchronized ElectionManager getInstance() {
        if (INSTANCE == null) INSTANCE = new ElectionManager();
        return INSTANCE;
    }

    public UUID runElection(UUID villageId, ServerLevel level) {
        var record = com.livemine.VillageManager.getInstance().getVillage(villageId);
        if (record == null) return null;

        String rawId = record.rawId();
        VillageData vd = new VillageData(rawId, level);
        if (!vd.exists()) return null;

        List<UUID> residents = vd.getResidentNpcIds();
        if (residents.isEmpty()) return null;

        UUID bestCandidate = null;
        double bestScore = -1;

        for (UUID id : residents) {
            LiveNPCEntity npc = com.livemine.ai.NPCRegistry.getNPC(id);
            if (npc == null || !npc.isAlive() || !npc.isAdult()) continue;

            double score = calculateScore(npc, level);
            if (score > bestScore) {
                bestScore = score;
                bestCandidate = id;
            }
        }

        if (bestCandidate != null) {
            record.setLeaderId(bestCandidate);
            LiveMineMod.LOGGER.info("Election in village {}: new leader is {} (score {})",
                rawId, bestCandidate, String.format("%.2f", bestScore));
        }
        return bestCandidate;
    }

    public double calculateScore(LiveNPCEntity npc, ServerLevel level) {
        double score = 0;

        if (npc.getSkills() != null) {
            for (NPCSkills.SkillType type : NPCSkills.SkillType.values()) {
                score += npc.getSkills().getLevel(type);
            }
            score += npc.getSkills().getLevel(NPCSkills.SkillType.DIPLOMACY) * 5.0;
        }

        if (DynastyManager.getInstance().isInFullDynasty(npc.getUUID())) {
            score *= (1.0 + DYNASTY_BONUS);
        }

        int age = npc.getAgeInDays();
        if (age >= 60 && age <= 90) score *= 1.15;
        if (age < 20) score *= 0.7;

        return score;
    }

    public boolean shouldTriggerReelection(UUID villageId, float leaderLoyalty, ServerLevel level) {
        if (leaderLoyalty < LOYALTY_THRESHOLD) return true;

        UUID leaderId = findLeader(villageId, level);
        if (leaderId == null) return true;

        LiveNPCEntity leader = com.livemine.ai.NPCRegistry.getNPC(leaderId);
        return leader == null || !leader.isAlive();
    }

    public UUID findLeader(UUID villageId, ServerLevel level) {
        var record = com.livemine.VillageManager.getInstance().getVillage(villageId);
        return record != null ? record.leaderId() : null;
    }

    public UUID pickBestCandidate(List<LiveNPCEntity> candidates, ServerLevel level) {
        return candidates.stream()
            .filter(npc -> npc != null && npc.isAlive() && npc.isAdult())
            .max(Comparator.comparingDouble(npc -> calculateScore(npc, level)))
            .map(LiveNPCEntity::getUUID)
            .orElse(null);
    }
}