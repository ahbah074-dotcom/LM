package com.livemine.ai;

import java.util.List;

/**
 * Планировщик мозга — обрабатывает AgentBrain-агентов порциями
 * в рамках выделенного бюджета наносекунд.
 *
 * Используется в дополнение к NPCBrain (мозг для ACTIVE NPC):
 *   - NPCBrain    — для LiveNPCEntity (полный AI)
 *   - AgentBrain  — для доменных агентов (SIMULATED/DORMANT)
 *
 * Гарантирует, что один тик не будет длиться дольше заданного бюджета.
 */
public final class BrainScheduler {

    private int cursor = 0;

    /**
     * Обрабатывает порцию агентов в рамках бюджета.
     *
     * @param agents        Список агентов
     * @param budgetNanos   Бюджет в наносекундах
     * @param currentTick   Текущий игровой тик (передаётся в AgentBrain.tick)
     * @return              Количество обработанных агентов
     */
    public int tick(List<AgentBrain> agents, long budgetNanos, long currentTick) {
        if (agents == null || agents.isEmpty()) return 0;

        long end = System.nanoTime() + Math.max(0, budgetNanos);
        int size = agents.size();
        int done = 0;

        while (done < size && System.nanoTime() < end) {
            AgentBrain agent = agents.get(cursor % size);
            if (agent != null) {
                try {
                    agent.tick(currentTick);
                } catch (Exception e) {
                    // Ошибка в одном агенте не должна валить весь тик
                    com.livemine.LiveMineMod.LOGGER.error(
                        "AgentBrain tick failed at cursor {}", cursor, e);
                }
            }
            cursor = (cursor + 1) % size;
            done++;
        }
        return done;
    }

    /**
     * Упрощённый вызов — с бюджетом по умолчанию (5 мс).
     */
    public int tick(List<AgentBrain> agents, long currentTick) {
        return tick(agents, 5_000_000L, currentTick);
    }

    public int cursor() { return cursor; }

    public void reset() { cursor = 0; }
}
