package com.livemine.personality;

import net.minecraft.util.RandomSource;

/**
 * Сны NPC (4 типа из ТЗ §11.2).
 *
 * Раз в игровую ночь NPC видит сон. Утром эффект применяется.
 *
 *   ОБЫЧНЫЙ       60%  — нет эффекта
 *   ВДОХНОВЕНИЕ   15%  — +10% к скорости обучения на 1 день
 *   КОШМАР        15%  — страх 3 часа, −5% продуктивность
 *   ВОСПОМИНАНИЕ  10%  — +5 доверия к приснившемуся NPC
 */
public final class NPCDreams {

    public enum DreamType {
        ORDINARY    ("Обычный сон",    60),
        INSPIRATION ("Вдохновение",    15),
        NIGHTMARE   ("Кошмар",         15),
        MEMORY      ("Воспоминание",   10);

        public final String ruName;
        public final int weight;

        DreamType(String ru, int w) {
            this.ruName = ru;
            this.weight = w;
        }
    }

    private NPCDreams() {}

    /**
     * Выбирает случайный сон по весам.
     */
    public static DreamType roll(RandomSource random) {
        int total = 0;
        for (DreamType d : DreamType.values()) total += d.weight;

        int r = random.nextInt(total);
        int acc = 0;
        for (DreamType d : DreamType.values()) {
            acc += d.weight;
            if (r < acc) return d;
        }
        return DreamType.ORDINARY;
    }

    /**
     * Применяет эффект сна к NPC (модифицирует эмоции, доверие, флаги).
     *
     * @param npc       сам NPC
     * @param random    источник случайности
     */
    public static void applyDream(NPCEmotions emotions, NPCTraits traits,
                                   DreamType dream, RandomSource random) {
        if (emotions == null) return;

        switch (dream) {
            case ORDINARY -> {
                // ничего
            }
            case INSPIRATION -> {
                emotions.trigger(NPCEmotions.Emotion.CONFIDENCE, 20);
                emotions.trigger(NPCEmotions.Emotion.JOY, 10);
            }
            case NIGHTMARE -> {
                emotions.trigger(NPCEmotions.Emotion.FEAR, 40);
                emotions.trigger(NPCEmotions.Emotion.FATIGUE, 15);
            }
            case MEMORY -> {
                emotions.trigger(NPCEmotions.Emotion.JOY, 15);
            }
        }
    }

    /**
     * Возвращает строку-описание сна для лога.
     */
    public static String describe(DreamType dream) {
        return switch (dream) {
            case ORDINARY -> "видел спокойный сон";
            case INSPIRATION -> "проснулся с вдохновением";
            case NIGHTMARE -> "мучился кошмаром";
            case MEMORY -> "вспомнил дорогое сердцу";
        };
    }
}