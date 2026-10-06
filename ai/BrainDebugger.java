package com.livemine.ai;

import com.livemine.LiveMineConfig;
import com.livemine.LiveMineMod;
import com.livemine.entity.LiveNPCEntity;
import net.minecraft.world.entity.Mob;

/**
 * Логирование мозга NPC для отладки.
 *
 * Включается через config: brainDebug = true
 * Формат: [Brain] <uuid:8> (name): ACTION — detail
 */
public final class BrainDebugger {

    private BrainDebugger() {}

    // =========================================================================
    // Общие события
    // =========================================================================

    public static void log(Mob npc, String action, String detail) {
        if (!LiveMineConfig.brainDebug()) return;
        if (npc == null) return;

        String uuid = shortUuid(npc.getUUID().toString());
        String name = extractName(npc);
        LiveMineMod.LOGGER.info("[Brain] <{}> ({}): {} — {}", uuid, name, action, detail);
    }

    // =========================================================================
    // Смена цели
    // =========================================================================

    public static void logGoalChanged(LiveNPCEntity npc, String oldGoal, String newGoal, String reason) {
        if (!LiveMineConfig.brainDebug()) return;
        if (npc == null) return;

        LiveMineMod.LOGGER.info("[Brain] <{}> ({}): GOAL_CHANGED {} -> {} — {}",
            shortUuid(npc.getUUID().toString()), extractName(npc), oldGoal, newGoal, reason);
    }

    public static void logGoalFailed(LiveNPCEntity npc, String goal, String reason) {
        if (!LiveMineConfig.brainDebug()) return;
        if (npc == null) return;

        LiveMineMod.LOGGER.info("[Brain] <{}> ({}): GOAL_FAILED {} — {}",
            shortUuid(npc.getUUID().toString()), extractName(npc), goal, reason);
    }

    public static void logGoalReached(LiveNPCEntity npc, String goal, String detail) {
        if (!LiveMineConfig.brainDebug()) return;
        if (npc == null) return;

        LiveMineMod.LOGGER.info("[Brain] <{}> ({}): GOAL_REACHED {} — {}",
            shortUuid(npc.getUUID().toString()), extractName(npc), goal, detail);
    }

    // =========================================================================
    // События навыков
    // =========================================================================

    public static void logSkillUp(LiveNPCEntity npc, String skill, double newLevel) {
        if (!LiveMineConfig.brainDebug()) return;
        if (npc == null) return;

        LiveMineMod.LOGGER.info("[Brain] <{}> ({}): SKILL_UP {} = {}",
            shortUuid(npc.getUUID().toString()), extractName(npc),
            skill, String.format("%.2f", newLevel));
    }

    // =========================================================================
    // Утилиты
    // =========================================================================

    private static String shortUuid(String uuid) {
        if (uuid == null || uuid.length() < 8) return uuid;
        return uuid.substring(0, 8);
    }

    private static String extractName(Mob mob) {
        if (mob instanceof LiveNPCEntity npc) {
            String n = npc.getCustomNameTag();
            return (n != null && !n.isEmpty()) ? n : "NPC";
        }
        return mob.getName().getString();
    }
}
