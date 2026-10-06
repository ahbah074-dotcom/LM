package com.livemine.ai;

import com.livemine.domain.Goal;
import com.livemine.domain.Needs;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility-based приоритизация целей NPC.
 *
 * Критические состояния (SURVIVAL, SAFETY) — безусловный приоритет.
 * Остальные цели сортируются по utility (убывание).
 *
 * Используется в AgentBrain.
 */
public final class NeedPrioritizer {

    /**
     * Оценивает потребности и возвращает отсортированный список целей.
     *
     * @param needs       Текущие потребности агента
     * @param currentTick Текущий игровой тик
     * @return Список целей (убывание приоритета). Может быть пустым.
     */
    public List<Goal> prioritize(Needs needs, long currentTick) {
        List<Goal> goals = new ArrayList<>();
        if (needs == null) return goals;

        int food = needs.food();
        int rest = needs.rest();
        int safety = needs.safety();

        // --- Критические: безусловный приоритет ---

        if (needs.isSafetyCritical()) {
            goals.add(new Goal(Goal.Direction.SAFETY, 100f, currentTick + 200L, 3));
        }
        if (needs.isFoodCritical()) {
            goals.add(new Goal(Goal.Direction.SURVIVAL, 100f, currentTick + 200L, 3));
        }
        if (needs.isRestCritical()) {
            goals.add(new Goal(Goal.Direction.REST, 90f, currentTick + 600L, 3));
        }

        // --- Обычные: utility по потребности ---

        if (food < 50 && !needs.isFoodCritical()) {
            float util = (50 - food) / 50f * 60f;
            goals.add(new Goal(Goal.Direction.FOOD, util, 0L, 5));
        }
        if (rest < 50 && !needs.isRestCritical()) {
            float util = (50 - rest) / 50f * 50f;
            goals.add(new Goal(Goal.Direction.REST, util, 0L, 5));
        }
        if (safety < 50 && !needs.isSafetyCritical()) {
            float util = (50 - safety) / 50f * 55f;
            goals.add(new Goal(Goal.Direction.SAFETY, util, 0L, 5));
        }

        if (needs.canWork()) {
            goals.add(new Goal(Goal.Direction.WORK, 30f, 0L, 5));
        }

        // Социум — низкий постоянный приоритет
        goals.add(new Goal(Goal.Direction.SOCIAL, 20f, 0L, 5));

        // Сортировка: критическое вверх, затем по utility
        goals.sort(Goal::compare);
        return goals;
    }
}