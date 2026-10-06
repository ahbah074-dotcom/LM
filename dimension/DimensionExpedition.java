package com.livemine.dimension;

import com.livemine.LiveMineMod;
import com.livemine.NPCSkills;
import com.livemine.entity.LiveNPCEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Экспедиции NPC в другие измерения.
 *
 * Требования (ТЗ 20.0 §16):
 *   - Незер: железная броня + зелье огнестойкости + оружие
 *   - Энд: алмазная броня + жемчуг Энда + оружие + еда
 *
 * Экспедиция: разведчик + боец + медик + специалист.
 * Длительность 3–7 дней. Риск вычисляется от состава и снаряжения.
 */
public final class DimensionExpedition {

    private static DimensionExpedition INSTANCE;

    public enum Target { NETHER, END, OVERWORLD_CUSTOM }

    // =========================================================================
    // Expedition
    // =========================================================================
    public static final class Expedition {
        public UUID expeditionId;
        public UUID leaderUUID;
        public List<UUID> members;
        public Target target;
        public long startDay;
        public long estimatedReturnDay;
        public int requiredSupplies;
        public float riskAssessment;
        public boolean active;
        public boolean completed;
        public String outcome;

        public Expedition(UUID leader, List<UUID> members, Target target, long startDay) {
            this.expeditionId = UUID.randomUUID();
            this.leaderUUID = leader;
            this.members = new ArrayList<>(members);
            this.target = target;
            this.startDay = startDay;
            this.estimatedReturnDay = startDay + 7;
            this.requiredSupplies = members.size() * 5;
            this.riskAssessment = calculateRisk(target, members.size());
            this.active = true;
            this.completed = false;
            this.outcome = "";
        }

        private float calculateRisk(Target target, int memberCount) {
            float base = switch (target) {
                case NETHER -> 0.6f;
                case END -> 0.8f;
                case OVERWORLD_CUSTOM -> 0.3f;
            };
            return Math.max(0.1f, base - memberCount * 0.05f);
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", expeditionId);
            tag.putUUID("leader", leaderUUID);

            ListTag list = new ListTag();
            for (UUID m : members) {
                CompoundTag mt = new CompoundTag();
                mt.putUUID("uuid", m);
                list.add(mt);
            }
            tag.put("members", list);

            tag.putString("target", target.name());
            tag.putLong("start", startDay);
            tag.putLong("return", estimatedReturnDay);
            tag.putInt("supplies", requiredSupplies);
            tag.putFloat("risk", riskAssessment);
            tag.putBoolean("active", active);
            tag.putBoolean("completed", completed);
            tag.putString("outcome", outcome);
            return tag;
        }

        public static Expedition load(CompoundTag tag) {
            Expedition e = new Expedition(
                tag.getUUID("leader"),
                new ArrayList<>(),
                Target.valueOf(tag.getString("target")),
                tag.getLong("start")
            );
            e.expeditionId = tag.getUUID("id");

            ListTag list = tag.getList("members", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                e.members.add(list.getCompound(i).getUUID("uuid"));
            }

            e.estimatedReturnDay = tag.getLong("return");
            e.requiredSupplies = tag.getInt("supplies");
            e.riskAssessment = tag.getFloat("risk");
            e.active = tag.getBoolean("active");
            e.completed = tag.getBoolean("completed");
            e.outcome = tag.getString("outcome");
            return e;
        }
    }

    private final Map<UUID, Expedition> expeditions = new HashMap<>();

    private DimensionExpedition() {}

    public static synchronized DimensionExpedition getInstance() {
        if (INSTANCE == null) INSTANCE = new DimensionExpedition();
        return INSTANCE;
    }

    // =========================================================================
    // Запуск
    // =========================================================================

    public Expedition launchExpedition(LiveNPCEntity leader, List<LiveNPCEntity> members,
                                         Target target, long currentDay) {
        List<UUID> memberUUIDs = new ArrayList<>();
        for (LiveNPCEntity m : members) memberUUIDs.add(m.getUUID());

        if (leader != null && leader.getSkills() != null) {
            double scouting = leader.getSkills().getLevel(NPCSkills.SkillType.SCOUTING);
            if (scouting < 3.0) {
                LiveMineMod.LOGGER.warn(
                    "Expedition leader {} lacks scouting ({})",
                    leader.getCustomNameTag(), scouting);
            }
        }

        Expedition exp = new Expedition(
            leader != null ? leader.getUUID() : UUID.randomUUID(),
            memberUUIDs, target, currentDay
        );
        expeditions.put(exp.expeditionId, exp);

        LiveMineMod.LOGGER.info(
            "Expedition launched: {} members, target {}, risk {}%",
            memberUUIDs.size(), target, exp.riskAssessment * 100);

        return exp;
    }

    // =========================================================================
    // РўРёРє
    // =========================================================================

    public void tick(long currentDay) {
        for (Expedition e : expeditions.values()) {
            if (!e.active) continue;
            if (currentDay < e.estimatedReturnDay) continue;

            float survivalChance = 1.0f - e.riskAssessment;
            if (Math.random() < survivalChance) {
                e.outcome = "Успешная экспедиция";
                e.completed = true;
            } else {
                e.outcome = "Экспедиция провалена";
                e.completed = true;
            }
            e.active = false;
        }
    }

    // =========================================================================
    // Чтение
    // =========================================================================

    public Expedition getExpedition(UUID id) {
        return expeditions.get(id);
    }

    public Collection<Expedition> getActiveExpeditions() {
        List<Expedition> active = new ArrayList<>();
        for (Expedition e : expeditions.values()) if (e.active) active.add(e);
        return active;
    }

    public Collection<Expedition> getAllExpeditions() {
        return expeditions.values();
    }

    public int size() { return expeditions.size(); }

    // =========================================================================
    // NBT
    // =========================================================================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Expedition e : expeditions.values()) list.add(e.save());
        tag.put("expeditions", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        expeditions.clear();
        ListTag list = tag.getList("expeditions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Expedition e = Expedition.load(list.getCompound(i));
            expeditions.put(e.expeditionId, e);
        }
    }

    public void clear() {
        expeditions.clear();
    }
}
