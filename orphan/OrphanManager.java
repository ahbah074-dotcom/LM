package com.livemine.orphan;

import com.livemine.LiveMineMod;
import com.livemine.LiveMineSavedData;
import com.livemine.VillageData;
import com.livemine.ai.NPCRegistry;
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
 * Менеджер сирот.
 *
 * v2.0: использует rawId ("village_<asLong>").
 */
public final class OrphanManager {

    private static OrphanManager INSTANCE;

    public static final double MIN_TRUST_FOR_GUARDIAN = 50.0;

    public static final class OrphanRecord {
        public UUID orphanId;
        public String villageRawId;
        public UUID guardianId;
        public long orphanedDay;
        public boolean guardianAssigned;
        public boolean inherited;

        public OrphanRecord(UUID orphanId, String villageRawId, long day) {
            this.orphanId = orphanId;
            this.villageRawId = villageRawId != null ? villageRawId : "";
            this.orphanedDay = day;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("orphan", orphanId);
            tag.putString("village", villageRawId);
            if (guardianId != null) tag.putUUID("guardian", guardianId);
            tag.putLong("day", orphanedDay);
            tag.putBoolean("guardianAssigned", guardianAssigned);
            tag.putBoolean("inherited", inherited);
            return tag;
        }

        public static OrphanRecord load(CompoundTag tag) {
            OrphanRecord r = new OrphanRecord(
                tag.getUUID("orphan"),
                tag.getString("village"),
                tag.getLong("day")
            );
            if (tag.hasUUID("guardian")) r.guardianId = tag.getUUID("guardian");
            r.guardianAssigned = tag.getBoolean("guardianAssigned");
            r.inherited = tag.getBoolean("inherited");
            return r;
        }
    }

    private final Map<UUID, OrphanRecord> orphans = new HashMap<>();

    private OrphanManager() {}

    public static synchronized OrphanManager getInstance() {
        if (INSTANCE == null) INSTANCE = new OrphanManager();
        return INSTANCE;
    }

    public boolean registerIfOrphan(LiveNPCEntity child, ServerLevel level) {
        if (child == null || !child.isChild()) return false;

        CompoundTag tag = LiveMineSavedData.get(level).loadNPCData(child.getUUID());
        UUID pA = tag.hasUUID("parent_a_uuid") ? tag.getUUID("parent_a_uuid") : null;
        UUID pB = tag.hasUUID("parent_b_uuid") ? tag.getUUID("parent_b_uuid") : null;

        if (pA == null && pB == null) return false;

        boolean aDead = isDead(pA, level);
        boolean bDead = isDead(pB, level);

        if (aDead && bDead) {
            registerOrphan(child, level);
            return true;
        }
        return false;
    }

    private boolean isDead(UUID npcId, ServerLevel level) {
        if (npcId == null) return true;
        CompoundTag tag = LiveMineSavedData.get(level).loadNPCData(npcId);
        if (tag.isEmpty()) return true;
        return tag.getBoolean("dead");
    }

    public void registerOrphan(LiveNPCEntity child, ServerLevel level) {
        long day = level.getDayTime() / 24000L;
        OrphanRecord r = new OrphanRecord(
            child.getUUID(),
            child.getVillageRawId(),
            day
        );
        orphans.put(child.getUUID(), r);

        LiveMineMod.LOGGER.info("NPC {} registered as orphan", child.getCustomNameTag());
        assignGuardian(r, child, level);
    }

    private void assignGuardian(OrphanRecord r, LiveNPCEntity child, ServerLevel level) {
        String rawId = child.getVillageRawId();
        if (rawId.isEmpty()) return;

        VillageData vd = new VillageData(rawId, level);
        if (!vd.exists()) return;

        LiveNPCEntity best = null;
        double bestTrust = MIN_TRUST_FOR_GUARDIAN;

        for (UUID npcId : vd.getResidentNpcIds()) {
            if (npcId.equals(child.getUUID())) continue;
            LiveNPCEntity adult = NPCRegistry.getNPC(npcId);
            if (adult == null || !adult.isAlive() || !adult.isAdult()) continue;

            var prop = com.livemine.property.PropertyManager.getInstance().get(npcId);
            if (prop == null || prop.homePos == null) continue;

            double trust = com.livemine.ai.NPCRelationship.getTrust(child.getUUID(), npcId);
            if (trust > bestTrust) {
                bestTrust = trust;
                best = adult;
            }
        }

        if (best != null) {
            r.guardianId = best.getUUID();
            r.guardianAssigned = true;
            LiveMineMod.LOGGER.info("Orphan {} -> guardian {}",
                child.getCustomNameTag(), best.getCustomNameTag());
        }
    }

    public boolean onComingOfAge(LiveNPCEntity npc, ServerLevel level) {
        OrphanRecord r = orphans.get(npc.getUUID());
        if (r == null) return false;

        UUID villageId = npc.getVillageId();
        if (villageId != null) {
            var prop = com.livemine.property.PropertyManager.getInstance()
                .getOrCreate(npc.getUUID(), villageId);

            if (prop.homeLevel < 1) prop.homeLevel = 1;

            if (!r.inherited) {
                com.livemine.InheritanceManager.getInstance().processInheritance(npc);
                r.inherited = true;
            }
        }

        orphans.remove(npc.getUUID());
        LiveMineMod.LOGGER.info("Orphan {} came of age", npc.getCustomNameTag());
        return true;
    }

    public OrphanRecord getOrphan(UUID npcId) { return orphans.get(npcId); }
    public boolean isOrphan(UUID npcId) { return orphans.containsKey(npcId); }
    public UUID getGuardian(UUID npcId) {
        OrphanRecord r = orphans.get(npcId);
        return r != null ? r.guardianId : null;
    }
    public List<OrphanRecord> getAllOrphans() { return new ArrayList<>(orphans.values()); }
    public int size() { return orphans.size(); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (OrphanRecord r : orphans.values()) list.add(r.save());
        tag.put("orphans", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        orphans.clear();
        ListTag list = tag.getList("orphans", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            OrphanRecord r = OrphanRecord.load(list.getCompound(i));
            orphans.put(r.orphanId, r);
        }
    }

    public void clear() { orphans.clear(); }
}