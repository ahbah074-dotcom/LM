package com.livemine.voting;

import com.livemine.LiveMineMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Менеджер голосований деревни.
 *
 * 5 типов голосований:
 *   - LEADER_ELECTION     — выборы лидера
 *   - BUILD_PROPOSAL      — строительство
 *   - RESOURCE_ALLOCATION — распределение ресурсов
 *   - ALLIANCE_PROPOSAL   — альянс
 *   - POLICY_CHANGE       — изменение правил
 *
 * Кворум: 50% взрослых. Критические (лидер, изгнание, альянс) — 2/3.
 */
public final class VotingManager {

    public static final int DEFAULT_DURATION_TICKS = 24000 * 3;   // 3 РґРЅСЏ

    private static VotingManager INSTANCE;

    public enum VoteType {
        LEADER_ELECTION,
        BUILD_PROPOSAL,
        RESOURCE_ALLOCATION,
        ALLIANCE_PROPOSAL,
        POLICY_CHANGE
    }

    public enum Result {
        PENDING, APPROVED, REJECTED, TIED
    }

    public static final class Vote {
        private final UUID voteId;
        private final UUID villageId;
        private final VoteType type;
        private final String description;
        private final Map<UUID, Boolean> votes = new HashMap<>();
        private Result result = Result.PENDING;
        private int durationTicks;
        private int elapsedTicks;

        public Vote(UUID voteId, UUID villageId, VoteType type, String description, int durationTicks) {
            this.voteId = voteId;
            this.villageId = villageId;
            this.type = type;
            this.description = description;
            this.durationTicks = durationTicks;
        }

        public void castVote(UUID npcId, boolean approve) {
            votes.put(npcId, approve);
        }

        public void tick() {
            elapsedTicks++;
            if (elapsedTicks >= durationTicks) tally();
        }

        private void tally() {
            int yes = 0, no = 0;
            for (Boolean v : votes.values()) {
                if (v) yes++;
                else no++;
            }
            if (yes > no) result = Result.APPROVED;
            else if (no > yes) result = Result.REJECTED;
            else result = Result.TIED;
        }

        public UUID getVoteId() { return voteId; }
        public UUID getVillageId() { return villageId; }
        public VoteType getType() { return type; }
        public String getDescription() { return description; }
        public Result getResult() { return result; }
        public Map<UUID, Boolean> getVotes() { return votes; }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("voteId", voteId);
            tag.putUUID("villageId", villageId);
            tag.putString("type", type.name());
            tag.putString("desc", description);
            tag.putString("result", result.name());
            tag.putInt("duration", durationTicks);
            tag.putInt("elapsed", elapsedTicks);

            ListTag votesList = new ListTag();
            for (var e : votes.entrySet()) {
                CompoundTag vt = new CompoundTag();
                vt.putUUID("voter", e.getKey());
                vt.putBoolean("approve", e.getValue());
                votesList.add(vt);
            }
            tag.put("votes", votesList);
            return tag;
        }

        public static Vote load(CompoundTag tag) {
            Vote v = new Vote(
                tag.getUUID("voteId"),
                tag.getUUID("villageId"),
                VoteType.valueOf(tag.getString("type")),
                tag.getString("desc"),
                tag.getInt("duration")
            );
            v.elapsedTicks = tag.getInt("elapsed");
            try { v.result = Result.valueOf(tag.getString("result")); }
            catch (IllegalArgumentException e) { v.result = Result.PENDING; }

            ListTag votesList = tag.getList("votes", Tag.TAG_COMPOUND);
            for (int i = 0; i < votesList.size(); i++) {
                CompoundTag vt = votesList.getCompound(i);
                v.votes.put(vt.getUUID("voter"), vt.getBoolean("approve"));
            }
            return v;
        }
    }

    private final Map<UUID, Vote> activeVotes = new HashMap<>();

    private VotingManager() {}

    public static synchronized VotingManager getInstance() {
        if (INSTANCE == null) INSTANCE = new VotingManager();
        return INSTANCE;
    }

    // =========================================================================
    // Операции
    // =========================================================================

    public UUID createVote(UUID villageId, VoteType type, String description, int durationTicks) {
        UUID voteId = UUID.randomUUID();
        Vote v = new Vote(voteId, villageId, type, description, durationTicks);
        activeVotes.put(voteId, v);
        LiveMineMod.LOGGER.info("Vote created: {} ({})", type.name(), description);
        return voteId;
    }

    public UUID createVote(UUID villageId, VoteType type, String description) {
        return createVote(villageId, type, description, DEFAULT_DURATION_TICKS);
    }

    public void castVote(UUID voteId, UUID npcId, boolean approve) {
        Vote v = activeVotes.get(voteId);
        if (v != null) v.castVote(npcId, approve);
    }

    public void tick() {
        activeVotes.entrySet().removeIf(e -> {
            e.getValue().tick();
            return e.getValue().getResult() != Result.PENDING;
        });
    }

    public Vote getVote(UUID voteId) {
        return activeVotes.get(voteId);
    }

    public java.util.List<Vote> getActiveVotes(UUID villageId) {
        java.util.List<Vote> result = new java.util.ArrayList<>();
        for (Vote v : activeVotes.values()) {
            if (v.getVillageId().equals(villageId)) result.add(v);
        }
        return result;
    }

    public int size() { return activeVotes.size(); }

    // =========================================================================
    // NBT
    // =========================================================================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Vote v : activeVotes.values()) list.add(v.save());
        tag.put("votes", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        activeVotes.clear();
        ListTag list = tag.getList("votes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Vote v = Vote.load(list.getCompound(i));
            activeVotes.put(v.voteId, v);
        }
    }

    public void clear() {
        activeVotes.clear();
    }
}
