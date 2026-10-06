package com.livemine.personality;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Черты характера NPC (6 типов).
 *
 * v2.1: добавлен replaceAll(List<Trait>) для корректной загрузки из NBT.
 */
public final class NPCTraits {

    public enum Trait {
        BRAVE       ("Храбрость"),
        CAUTIOUS    ("Осторожность"),
        DILIGENT    ("Трудолюбие"),
        LAZY        ("Лень"),
        GENEROUS    ("Щедрость"),
        WITHDRAWN   ("Замкнутость");

        public final String ruName;
        Trait(String ru) { this.ruName = ru; }
    }

    private final List<Trait> traits = new ArrayList<>();

    public NPCTraits() {}

    public NPCTraits(RandomSource random) {
        assignRandom(random);
    }

    public void assignRandom(RandomSource random) {
        traits.clear();
        Trait[] all = Trait.values();

        int count = 2 + (random.nextInt(4) == 0 ? 1 : 0);

        for (int i = all.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Trait tmp = all[i]; all[i] = all[j]; all[j] = tmp;
        }

        for (int i = 0; i < count && i < all.length; i++) {
            if (contains(Trait.BRAVE) && all[i] == Trait.CAUTIOUS) continue;
            if (contains(Trait.CAUTIOUS) && all[i] == Trait.BRAVE) continue;
            if (contains(Trait.DILIGENT) && all[i] == Trait.LAZY) continue;
            if (contains(Trait.LAZY) && all[i] == Trait.DILIGENT) continue;

            traits.add(all[i]);
        }
    }

    /**
     * v2.1: заменяет список черт целиком (для загрузки из NBT).
     */
    public void replaceAll(List<Trait> newTraits) {
        traits.clear();
        if (newTraits != null) {
            for (Trait t : newTraits) {
                if (t != null && !traits.contains(t)) {
                    traits.add(t);
                }
            }
        }
    }

    public boolean contains(Trait trait) {
        return traits.contains(trait);
    }

    public List<Trait> getAll() {
        return new ArrayList<>(traits);
    }

    public int size() {
        return traits.size();
    }

    // =========================================================================
    // Множители
    // =========================================================================

    public double getProductivityMultiplier() {
        double m = 1.0;
        if (contains(Trait.DILIGENT)) m += 0.15;
        if (contains(Trait.LAZY)) m -= 0.10;
        return m;
    }

    public double getCombatMultiplier() {
        double m = 1.0;
        if (contains(Trait.BRAVE)) m += 0.20;
        if (contains(Trait.CAUTIOUS)) m -= 0.10;
        return m;
    }

    public double getLearningMultiplier() {
        double m = 1.0;
        if (contains(Trait.DILIGENT)) m -= 0.05;
        return m;
    }

    public double getFleeRisk() {
        double m = 1.0;
        if (contains(Trait.BRAVE)) m += 0.15;
        if (contains(Trait.CAUTIOUS)) m -= 0.10;
        return m;
    }

    public int getTruancyChancePercent() {
        return contains(Trait.LAZY) ? 10 : 0;
    }

    public boolean isSocial() {
        return !contains(Trait.WITHDRAWN);
    }

    public double getTrustInfluenceMultiplier() {
        double m = 1.0;
        if (contains(Trait.GENEROUS)) m += 0.10;
        if (contains(Trait.WITHDRAWN)) m -= 0.05;
        return m;
    }

    // =========================================================================
    // NBT
    // =========================================================================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        int i = 0;
        for (Trait t : traits) {
            tag.putString("t" + i, t.name());
            i++;
        }
        tag.putInt("count", i);
        return tag;
    }

    public static NPCTraits load(CompoundTag tag) {
        NPCTraits t = new NPCTraits();
        if (tag == null) return t;
        int count = tag.getInt("count");
        List<Trait> loaded = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String key = "t" + i;
            if (tag.contains(key)) {
                try {
                    loaded.add(Trait.valueOf(tag.getString(key)));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        t.replaceAll(loaded);
        return t;
    }

    public void clear() {
        traits.clear();
    }
}