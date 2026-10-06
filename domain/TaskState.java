package com.livemine.domain;

/**
 * Состояние FSM задачи доменного агента.
 *
 * Жизненный цикл:
 *   PLANNED → RESERVED → MOVING → EXECUTING → SUCCEEDED
 *                              → COOLDOWN → MOVING (повтор)
 *   любые → FAILED → (retry или CANCELLED)
 */
public enum TaskState {

    PLANNED,
    RESERVED,
    MOVING,
    EXECUTING,
    COOLDOWN,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    public boolean terminal() {
        return this == SUCCEEDED || this == CANCELLED;
    }

    public boolean canRetry() {
        return this != SUCCEEDED && this != CANCELLED;
    }

    public boolean isActive() {
        return !terminal();
    }

    public boolean canTransitionTo(TaskState next) {
        if (terminal()) return false;

        return switch (this) {
            case PLANNED   -> next == RESERVED || next == MOVING || next == FAILED || next == CANCELLED;
            case RESERVED  -> next == MOVING || next == EXECUTING || next == FAILED || next == CANCELLED;
            case MOVING    -> next == EXECUTING || next == COOLDOWN || next == FAILED || next == CANCELLED;
            case EXECUTING -> next == SUCCEEDED || next == COOLDOWN || next == FAILED || next == CANCELLED;
            case COOLDOWN  -> next == MOVING || next == PLANNED || next == FAILED || next == CANCELLED;
            case FAILED    -> next == PLANNED || next == CANCELLED;
            default        -> false;
        };
    }

    public String ruName() {
        return switch (this) {
            case PLANNED   -> "Запланирована";
            case RESERVED  -> "Зарезервирована";
            case MOVING    -> "В пути";
            case EXECUTING -> "Выполняется";
            case COOLDOWN  -> "Пауза";
            case SUCCEEDED -> "Успешно";
            case FAILED    -> "Провалена";
            case CANCELLED -> "Отменена";
        };
    }

    @Override
    public String toString() {
        return name() + " (" + ruName() + ")";
    }
}