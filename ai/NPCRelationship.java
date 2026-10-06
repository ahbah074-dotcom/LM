package com.livemine.ai;

import com.livemine.LiveMineSavedData;
import com.livemine.entity.LiveNPCEntity;
import com.livemine.personality.NPCEmotions;
import com.livemine.personality.NPCTraits;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Отношения между NPC (trust).
 *
 * v2.0: учитывает черты и эмоции NPC при социальных событиях.
 */
public final class NPCRelationship {

    public static final double BASE_TRUST = 50.0;
    public static final double MAX_TRUST = 100.0;
    public static final double MIN_TRUST = 0.0;

    private static final Map<UUID, Map<UUID, Double>> relationships = new ConcurrentHashMap<>();

    private NPCRelationship() {}

    // =========================================================================
    // Геттеры
    // =========================================================================

    public static double getTrust(UUID npc1, UUID npc2) {
        Map<UUID, Double> map = relationships.get(npc1);
        if (map == null) return BASE_TRUST;
        return map.getOrDefault(npc2, BASE_TRUST);
    }

    public static double getTrust(LiveNPCEntity a, LiveNPCEntity b) {
        return getTrust(a.getUUID(), b.getUUID());
    }

    // =========================================================================
    // Изменение
    // =========================================================================

    public static void addTrust(UUID npc1, UUID npc2, double amount) {
        double current = getTrust(npc1, npc2);
        double newVal = clamp(current + amount);

        relationships.computeIfAbsent(npc1, k -> new ConcurrentHashMap<>()).put(npc2, newVal);
        relationships.computeIfAbsent(npc2, k -> new ConcurrentHashMap<>()).put(npc1, newVal);
    }

    public static void setTrust(UUID npc1, UUID npc2, double value) {
        double v = clamp(value);
        relationships.computeIfAbsent(npc1, k -> new ConcurrentHashMap<>()).put(npc2, v);
        relationships.computeIfAbsent(npc2, k -> new ConcurrentHashMap<>()).put(npc1, v);
    }

    // =========================================================================
    // v2.0: События с чертами и эмоциями
    // =========================================================================

    /**
     * Общение (SocializeGoal).
     * Учитывает traits и emotions: щедрость +, замкнутость −, радость +, гнев −.
     */
    public static void onSocialize(LiveNPCEntity a, LiveNPCEntity b) {
        if (a.level().isClientSide()) return;

        double baseAmount = 2.0;
        double mult = 1.0;

        // Черты обоих.
        NPCTraits traitsA = a.getTraits();
        NPCTraits traitsB = b.getTraits();
        if (traitsA != null) mult *= traitsA.getTrustInfluenceMultiplier();
        if (traitsB != null) mult *= traitsB.getTrustInfluenceMultiplier();

        // Эмоции обоих.
        NPCEmotions emA = a.getEmotions();
        NPCEmotions emB = b.getEmotions();
        if (emA != null) {
            if (emA.isAngry()) mult *= 0.5;
            if (emA.get(NPCEmotions.Emotion.JOY) > 30) mult *= 1.2;
        }
        if (emB != null) {
            if (emB.isAngry()) mult *= 0.5;
            if (emB.get(NPCEmotions.Emotion.JOY) > 30) mult *= 1.2;
        }

        addTrust(a.getUUID(), b.getUUID(), baseAmount * mult);

        ServerLevel level = (ServerLevel) a.level();
        LiveMineSavedData data = LiveMineSavedData.get(level);

        CompoundTag tagA = data.loadNPCData(a.getUUID());
        if (!tagA.isEmpty()) {
            tagA.putDouble("social", Math.min(100, tagA.getDouble("social") + 0.5));
            data.saveNPCData(a.getUUID(), tagA);
        }

        CompoundTag tagB = data.loadNPCData(b.getUUID());
        if (!tagB.isEmpty()) {
            tagB.putDouble("social", Math.min(100, tagB.getDouble("social") + 0.5));
            data.saveNPCData(b.getUUID(), tagB);
        }

        // v2.0: положительный опыт даёт радость.
        if (emA != null) emA.trigger(NPCEmotions.Emotion.JOY, 5);
        if (emB != null) emB.trigger(NPCEmotions.Emotion.JOY, 5);
    }

    public static void onWorkTogether(LiveNPCEntity a, LiveNPCEntity b) {
        addTrust(a.getUUID(), b.getUUID(), 0.5);
    }

    public static void onFight(LiveNPCEntity a, LiveNPCEntity b) {
        addTrust(a.getUUID(), b.getUUID(), -20.0);

        // v2.0: гнев после драки.
        if (a.getEmotions() != null) a.getEmotions().trigger(NPCEmotions.Emotion.ANGER, 30);
        if (b.getEmotions() != null) b.getEmotions().trigger(NPCEmotions.Emotion.ANGER, 30);
    }

    public static void onGift(LiveNPCEntity giver, LiveNPCEntity receiver) {
        double amount = 10.0;
        if (giver.getTraits() != null && giver.getTraits().contains(NPCTraits.Trait.GENEROUS)) {
            amount *= 1.3;
        }
        addTrust(giver.getUUID(), receiver.getUUID(), amount);

        if (receiver.getEmotions() != null) {
            receiver.getEmotions().trigger(NPCEmotions.Emotion.JOY, 20);
        }
    }

    /**
     * v2.0: NPC увидел смерть друга/родственника — горе.
     */
    public static void onDeath(LiveNPCEntity witness, UUID deadNpcId) {
        if (witness == null) return;
        double trust = getTrust(witness.getUUID(), deadNpcId);
        if (trust < 40) return;  // незнакомый

        if (witness.getEmotions() != null) {
            witness.getEmotions().trigger(NPCEmotions.Emotion.GRIEF, 40);
        }
    }

    // =========================================================================
    // NBT
    // =========================================================================

    public static CompoundTag save() {
        CompoundTag root = new CompoundTag();
        for (Map.Entry<UUID, Map<UUID, Double>> entry : relationships.entrySet()) {
            CompoundTag inner = new CompoundTag();
            for (Map.Entry<UUID, Double> rel : entry.getValue().entrySet()) {
                inner.putDouble(rel.getKey().toString(), rel.getValue());
            }
            root.put(entry.getKey().toString(), inner);
        }
        return root;
    }

    public static void load(CompoundTag root) {
        relationships.clear();
        if (root == null) return;
        for (String k1 : root.getAllKeys()) {
            try {
                UUID npc1 = UUID.fromString(k1);
                CompoundTag inner = root.getCompound(k1);
                Map<UUID, Double> map = new ConcurrentHashMap<>();
                for (String k2 : inner.getAllKeys()) {
                    map.put(UUID.fromString(k2), inner.getDouble(k2));
                }
                relationships.put(npc1, map);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public static void clear() {
        relationships.clear();
    }

    public static int size() {
        return relationships.size();
    }

    private static double clamp(double v) {
        return Math.max(MIN_TRUST, Math.min(MAX_TRUST, v));
    }

    public static String describeTrust(double trust) {
        if (trust < 20) return "Враг";
        if (trust < 40) return "Недоверие";
        if (trust < 60) return "Нейтрально";
        if (trust < 80) return "Друг";
        return "Близкий друг";
    }
}