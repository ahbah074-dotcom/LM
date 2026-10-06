package com.livemine.personality;

import net.minecraft.nbt.CompoundTag;

/**
 * Эмоции NPC (7 типов из ТЗ §11.1).
 *
 * Каждая эмоция имеет интенсивность 0..100 и счётчик длительности в тиках.
 * Эмоции затухают со временем и влияют на:
 *   - продуктивность работы,
 *   - боевую эффективность,
 *   - скорость обучения.
 *
 * Триггеры:
 *   РАДОСТЬ      — праздник, повышение навыка, брак, рождение
 *   ГОРЕ         — смерть родственника/друга, разрушение дома
 *   ГНЕВ         — нападение, кража, предательство
 *   СТРАХ        — рейд, сильный моб, взрыв
 *   УВЕРЕННОСТЬ  — высокий навык, поддержка деревни
 *   УСТАЛОСТЬ    — долгая работа без отдыха
 *   СКУКА        — нет задач, нет общения
 */
public final class NPCEmotions {

    public enum Emotion {
        JOY       ("Радость"),
        GRIEF     ("Горе"),
        ANGER     ("Гнев"),
        FEAR      ("Страх"),
        CONFIDENCE("Уверенность"),
        FATIGUE   ("Усталость"),
        BOREDOM   ("Скука");

        public final String ruName;
        Emotion(String ru) { this.ruName = ru; }
    }

    private static final long DECAY_INTERVAL_TICKS = 200;
    private static final int DECAY_AMOUNT = 1;

    private final int[] intensity = new int[Emotion.values().length];
    private final long[] lastUpdateTick = new long[Emotion.values().length];

    public NPCEmotions() {
        // Нейтральный старт.
    }

    // =========================================================================
    // Изменение
    // =========================================================================

    public void trigger(Emotion emotion, int amount) {
        if (emotion == null) return;
        int i = emotion.ordinal();
        intensity[i] = clamp(intensity[i] + amount);
    }

    public void set(Emotion emotion, int value) {
        if (emotion == null) return;
        intensity[emotion.ordinal()] = clamp(value);
    }

    public void clear(Emotion emotion) {
        if (emotion == null) return;
        intensity[emotion.ordinal()] = 0;
    }

    /**
     * Тик раз в 200 тиков — затухание всех эмоций на 1.
     * Эмоции с интенсивностью 0 и так остаются 0.
     */
    public void tick(long currentTick) {
        for (int i = 0; i < intensity.length; i++) {
            if (currentTick - lastUpdateTick[i] < DECAY_INTERVAL_TICKS) continue;
            lastUpdateTick[i] = currentTick;
            if (intensity[i] > 0) intensity[i] = Math.max(0, intensity[i] - DECAY_AMOUNT);
        }
    }

    // =========================================================================
    // Геттеры
    // =========================================================================

    public int get(Emotion emotion) {
        if (emotion == null) return 0;
        return intensity[emotion.ordinal()];
    }

    public Emotion getDominant() {
        Emotion best = Emotion.JOY;
        int bestVal = 0;
        for (Emotion e : Emotion.values()) {
            int v = intensity[e.ordinal()];
            if (v > bestVal) {
                bestVal = v;
                best = e;
            }
        }
        return bestVal > 0 ? best : null;
    }

    /**
     * Множитель продуктивности работы.
     * Радость/уверенность — +, горе/усталость — −.
     */
    public double getProductivityMultiplier() {
        double m = 1.0;
        m += intensity[Emotion.JOY.ordinal()] * 0.001;         // до +0.10
        m += intensity[Emotion.CONFIDENCE.ordinal()] * 0.0015; // до +0.15
        m -= intensity[Emotion.GRIEF.ordinal()] * 0.003;       // до −0.30
        m -= intensity[Emotion.FATIGUE.ordinal()] * 0.002;     // до −0.20
        return Math.max(0.2, Math.min(1.5, m));
    }

    /**
     * Множитель боевой эффективности.
     */
    public double getCombatMultiplier() {
        double m = 1.0;
        m += intensity[Emotion.ANGER.ordinal()] * 0.005;        // до +0.50
        m -= intensity[Emotion.FEAR.ordinal()] * 0.004;         // до −0.40
        m -= intensity[Emotion.FATIGUE.ordinal()] * 0.002;
        return Math.max(0.3, Math.min(1.8, m));
    }

    /**
     * Множитель скорости обучения.
     */
    public double getLearningMultiplier() {
        double m = 1.0;
        m += intensity[Emotion.JOY.ordinal()] * 0.0005;    // до +0.05
        m -= intensity[Emotion.BOREDOM.ordinal()] * 0.001; // до −0.10
        m -= intensity[Emotion.GRIEF.ordinal()] * 0.001;
        return Math.max(0.5, Math.min(1.3, m));
    }

    public boolean isAfraid() {
        return intensity[Emotion.FEAR.ordinal()] >= 40;
    }

    public boolean isAngry() {
        return intensity[Emotion.ANGER.ordinal()] >= 40;
    }

    public boolean isExhausted() {
        return intensity[Emotion.FATIGUE.ordinal()] >= 60;
    }

    // =========================================================================
    // NBT
    // =========================================================================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        for (Emotion e : Emotion.values()) {
            tag.putInt("e_" + e.name().toLowerCase(), intensity[e.ordinal()]);
        }
        return tag;
    }

    public static NPCEmotions load(CompoundTag tag) {
        NPCEmotions e = new NPCEmotions();
        if (tag == null) return e;
        for (Emotion em : Emotion.values()) {
            String key = "e_" + em.name().toLowerCase();
            if (tag.contains(key)) {
                e.intensity[em.ordinal()] = tag.getInt(key);
            }
        }
        return e;
    }

    public void clear() {
        for (int i = 0; i < intensity.length; i++) intensity[i] = 0;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }
}