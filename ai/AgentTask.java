package com.livemine.ai;

import com.livemine.domain.TaskState;

import java.util.UUID;

/**
 * Задача доменного агента.
 *
 * Простая обёртка с id, целью (строка), FSM-состоянием и счётчиком провалов.
 * Используется AgentBrain'ом для отслеживания текущей задачи.
 *
 * Полная FSM-машина задач — TaskStateMachine (для ACTIVE NPC).
 */
public final class AgentTask {

    public static final int MAX_FAILURES = 3;

    private final UUID id = UUID.randomUUID();
    private final String goal;
    private TaskState state = TaskState.PLANNED;
    private int failures;
    private long startedTick;
    private long deadlineTick;

    public AgentTask(String goal) {
        this.goal = goal;
    }

    public AgentTask(String goal, long currentTick, long durationTicks) {
        this.goal = goal;
        this.startedTick = currentTick;
        this.deadlineTick = currentTick + durationTicks;
    }

    // =========================================================================
    // Геттеры
    // =========================================================================

    public UUID id() { return id; }
    public String goal() { return goal; }
    public TaskState state() { return state; }
    public int failures() { return failures; }
    public long startedTick() { return startedTick; }
    public long deadlineTick() { return deadlineTick; }

    public boolean isTerminal() {
        return state != null && state.terminal();
    }

    // =========================================================================
    // Переходы
    // =========================================================================

    /**
     * Переход в новое состояние. Возвращает false, если из текущего
     * состояния переход невозможен (терминальное).
     */
    public boolean transition(TaskState next) {
        if (state != null && state.terminal()) return false;
        state = next;
        return true;
    }

    /**
     * Регистрирует провал. При достижении MAX_FAILURES — CANCELLED.
     */
    public void fail() {
        failures++;
        if (failures >= MAX_FAILURES) {
            state = TaskState.CANCELLED;
        } else {
            state = TaskState.FAILED;
        }
    }

    /**
     * Проверка дедлайна. Если истёк — помечает задачу как FAILED.
     * Возвращает true, если дедлайн истёк.
     */
    public boolean checkDeadline(long currentTick) {
        if (deadlineTick <= 0) return false;
        if (currentTick > deadlineTick) {
            fail();
            return true;
        }
        return false;
    }

    /**
     * Завершение успехом.
     */
    public void succeed() {
        if (state == null || !state.terminal()) {
            state = TaskState.SUCCEEDED;
        }
    }

    // =========================================================================
    // Утилиты
    // =========================================================================

    @Override
    public String toString() {
        return "AgentTask{" + goal + ", state=" + state + ", failures=" + failures + "}";
    }
}
