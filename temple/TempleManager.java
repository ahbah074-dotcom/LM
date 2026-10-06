package com.livemine.temple;

import com.livemine.LiveMineMod;
import com.livemine.LiveMineSavedData;
import com.livemine.VillageData;
import com.livemine.entity.LiveNPCEntity;
import com.livemine.personality.NPCEmotions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Менеджер храмов и ритуалов.
 *
 * v2.0: ритуалы применяются к NPC:
 *   - BATTLE_BLESSING   — снимает страх, даёт уверенность
 *   - FUNERAL           — снижает горе у очевидцев
 *   - EXPEDITION_BLESSING — уверенность + радость
 */
public final class TempleManager {

    private static TempleManager INSTANCE;

    public static final double MIN_MAGIC_FOR_TEMPLE = 5.0;

    public enum RitualType {
        BATTLE_BLESSING     ("Благословение на бой",    30, 20),
        FUNERAL             ("Похоронный обряд",        -40, 10),
        EXPEDITION_BLESSING ("Благословение экспедиции",20, 15),
        HARVEST_BLESSING    ("Благословение урожая",   10, 10),
        FOUNDING            ("Обряд основания",        0,  20);

        public final String ruName;
        public final int fearChange;
        public final int confidenceChange;

        RitualType(String ru, int fearChange, int confidenceChange) {
            this.ruName = ru;
            this.fearChange = fearChange;
            this.confidenceChange = confidenceChange;
        }
    }

    public static final class Temple {
        public final UUID villageId;
        public BlockPos center;
        public UUID priestId;
        public long foundedDay;
        public int ritualsPerformed;

        public Temple(UUID villageId, BlockPos center, UUID priestId, long day) {
            this.villageId = villageId;
            this.center = center;
            this.priestId = priestId;
            this.foundedDay = day;
            this.ritualsPerformed = 0;
        }
    }

    private final Map<UUID, Temple> temples = new HashMap<>();

    private TempleManager() {}

    public static synchronized TempleManager getInstance() {
        if (INSTANCE == null) INSTANCE = new TempleManager();
        return INSTANCE;
    }

    // =========================================================================
    // Храмы
    // =========================================================================

    public Temple getTemple(UUID villageId) {
        return temples.get(villageId);
    }

    public Temple createTemple(UUID villageId, BlockPos center, UUID priestId, long day) {
        Temple t = new Temple(villageId, center, priestId, day);
        temples.put(villageId, t);
        LiveMineMod.LOGGER.info("Temple created for village {} at {}", villageId, center);
        return t;
    }

    public boolean hasTemple(UUID villageId) {
        return temples.containsKey(villageId);
    }

    public boolean canBuildTemple(UUID villageId, ServerLevel level) {
        if (hasTemple(villageId)) return false;

        var record = com.livemine.VillageManager.getInstance().getVillage(villageId);
        if (record == null) return false;

        VillageData vd = new VillageData(record.rawId(), level);
        if (!vd.exists()) return false;

        var towers = vd.getStructures(com.livemine.blocks.MagicTowerCoreBlock.class);
        return !towers.isEmpty();
    }

    // =========================================================================
    // v2.0: Ритуалы с эффектами
    // =========================================================================

    /**
     * Проводит ритуал. Применяет эффекты к NPC в радиусе.
     *
     * @return true, если ритуал проведён
     */
    public boolean performRitual(UUID villageId, RitualType type, ServerLevel level) {
        Temple t = temples.get(villageId);
        if (t == null) return false;
        if (t.priestId == null) return false;
        if (t.center == null) return false;

        t.ritualsPerformed++;

        // Применяем эффекты к NPC в радиусе 20 блоков.
        var npcs = level.getEntitiesOfClass(LiveNPCEntity.class,
            new net.minecraft.world.phys.AABB(t.center).inflate(20));

        for (LiveNPCEntity npc : npcs) {
            NPCEmotions em = npc.getEmotions();
            if (em == null) continue;

            switch (type) {
                case BATTLE_BLESSING -> {
                    em.trigger(NPCEmotions.Emotion.CONFIDENCE, 30);
                    em.clear(NPCEmotions.Emotion.FEAR);
                }
                case FUNERAL -> {
                    em.trigger(NPCEmotions.Emotion.GRIEF, -40);
                    em.trigger(NPCEmotions.Emotion.CONFIDENCE, 10);
                }
                case EXPEDITION_BLESSING -> {
                    em.trigger(NPCEmotions.Emotion.CONFIDENCE, 20);
                    em.trigger(NPCEmotions.Emotion.JOY, 15);
                }
                case HARVEST_BLESSING -> {
                    em.trigger(NPCEmotions.Emotion.JOY, 10);
                }
                case FOUNDING -> {
                    em.trigger(NPCEmotions.Emotion.CONFIDENCE, 20);
                }
            }
        }

        LiveMineMod.LOGGER.info("Ritual performed: {} at village {} ({} NPC affected)",
            type.ruName, villageId, npcs.size());
        return true;
    }

    /**
     * Упрощённый вызов без эффектов (для совместимости).
     */
    public boolean performRitual(UUID villageId, RitualType type) {
        Temple t = temples.get(villageId);
        if (t == null) return false;
        if (t.priestId == null) return false;

        t.ritualsPerformed++;
        LiveMineMod.LOGGER.info("Ritual performed (no effect): {} at village {}",
            type.ruName, villageId);
        return true;
    }

    // =========================================================================
    // События
    // =========================================================================

    /**
     * Вызывается при смерти NPC — ритуал похорон.
     */
    public void onNpcDeath(LiveNPCEntity deceased, ServerLevel level) {
        if (deceased == null) return;
        UUID vId = deceased.getVillageId();
        if (vId == null) return;
        if (!hasTemple(vId)) return;

        performRitual(vId, RitualType.FUNERAL, level);
    }

    /**
     * Вызывается перед боевым заданием.
     */
    public void onBattleStart(LiveNPCEntity npc, ServerLevel level) {
        if (npc == null) return;
        UUID vId = npc.getVillageId();
        if (vId == null) return;
        if (!hasTemple(vId)) return;
        performRitual(vId, RitualType.BATTLE_BLESSING, level);
    }

    // =========================================================================
    // Запросы
    // =========================================================================

    public int getTempleCount() {
        return temples.size();
    }

    public void clear() {
        temples.clear();
    }
}