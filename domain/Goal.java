package com.livemine.domain;

import java.util.UUID;

/**
 * Цель NPC с utility-оценкой.
 *
 * Utility-based prioritization: срочность, риск смерти, приказ, доступность
 * ресурсов, расстояние, навык, усталость, отношения, стоимость переключения.
 *
 * Критические состояния (SURVIVAL, SAFETY) всегда приоритетнее любой
 * utility-оценки — это гарантия выживания.
 */
public final class Goal {

    public enum Direction {
        SURVIVAL,
        SAFETY,
        FOOD,
        REST,
        WORK,
        SOCIAL,
        LEARNING,
        HEALING,
        FAMILY,
        ENTERTAINMENT,
        COLLECTIVE
    }

    private final Direction direction;
    private final float utility;
    private final long deadlineTick;
    private final int maxRetries;

    private UUID assignedTaskId;
    private boolean commanded;

    public Goal(Direction direction, float utility, long deadlineTick, int maxRetries) {
        this.direction = direction;
        this.utility = utility;
        this.deadlineTick = deadlineTick;
        this.maxRetries = maxRetries;
        this.commanded = false;
    }

    // =========================================================================
    // Геттеры
    // =========================================================================

    public Direction direction() { return direction; }
    public float utility() { return utility; }
    public long deadlineTick() { return deadlineTick; }
    public int maxRetries() { return maxRetries; }
    public boolean isCommanded() { return commanded; }
    public UUID assignedTaskId() { return assignedTaskId; }

    // =========================================================================
    // Сеттеры
    // =========================================================================

    public void setCommanded(boolean v) { this.commanded = v; }
    public void assignTask(UUID taskId) { this.assignedTaskId = taskId; }
    public void clearTask() { this.assignedTaskId = null; }

    // =========================================================================
    // Приоритет
    // =========================================================================

    public boolean isCritical() {
        return direction == Direction.SURVIVAL || direction == Direction.SAFETY;
    }

    public boolean isExpired(long currentTick) {
        return deadlineTick > 0 && currentTick > deadlineTick;
    }

    // =========================================================================
    // Сравнение
    // =========================================================================

    public static int compare(Goal a, Goal b) {
        if (a.isCritical() != b.isCritical()) {
            return a.isCritical() ? -1 : 1;
        }
        return Float.compare(b.utility(), a.utility());
    }

    @Override
    public String toString() {
        return "Goal{" + direction + ", utility=" + utility
            + ", deadline=" + deadlineTick
            + (commanded ? ", COMMANDED" : "") + "}";
    }
}