package com.livemine.ai;

import com.livemine.domain.Goal;
import com.livemine.domain.Needs;
import com.livemine.domain.SimulationMode;
import com.livemine.domain.TaskState;

import java.util.List;
import java.util.UUID;

/**
 * Мозг доменного агента.
 *
 * Композиция: Needs + NeedPrioritizer + AgentTask + SimulationMode.
 *
 * Pipeline:
 *   1. Обновить потребности (advanceDays — при загрузке)
 *   2. Оценить цели через NeedPrioritizer
 *   3. Создать/переиспользовать AgentTask
 *   4. Обновить FSM задачи
 *
 * Используется для SIMULATED/DORMANT агентов.
 */
public final class AgentBrain {

    private final Needs needs = Needs.initial();
    private final NeedPrioritizer prioritizer = new NeedPrioritizer();

    private AgentTask task;
    private List<Goal> currentGoals;

    private SimulationMode mode = SimulationMode.VIRTUAL;
    private UUID currentLeaseId;

    private String persistedTaskState;

    public void tick(long currentTick) {
        currentGoals = prioritizer.prioritize(needs, currentTick);

        if (task == null || task.isTerminal()) {
            if (currentGoals != null && !currentGoals.isEmpty()) {
                Goal top = currentGoals.get(0);
                task = new AgentTask(goalToTaskName(top));
            } else {
                task = new AgentTask("IDLE");
            }
        }

        if (task != null) {
            task.checkDeadline(currentTick);
            if (task.state() == TaskState.PLANNED && topGoal() != null) {
                task.transition(TaskState.EXECUTING);
            }
        }
    }

    private static String goalToTaskName(Goal goal) {
        if (goal == null || goal.direction() == null) return "idle";
        return switch (goal.direction()) {
            case SURVIVAL, FOOD -> "find_food";
            case SAFETY -> "seek_safety";
            case REST -> "rest";
            case WORK -> "work";
            case SOCIAL -> "socialize";
            case LEARNING -> "learn";
            case HEALING -> "heal";
            case FAMILY -> "family";
            case ENTERTAINMENT -> "entertain";
            case COLLECTIVE -> "collective";
        };
    }

    public void advance(long days, boolean threatened) {
        needs.advanceDays(days);
        if (threatened) {
            needs.setSafety(Math.max(0, needs.safety() - (int) days));
        }
    }

    public Needs needs() { return needs; }
    public AgentTask task() { return task; }
    public List<Goal> currentGoals() { return currentGoals; }
    public SimulationMode mode() { return mode; }
    public UUID currentLeaseId() { return currentLeaseId; }

    public Goal topGoal() {
        if (currentGoals == null || currentGoals.isEmpty()) return null;
        return currentGoals.get(0);
    }

    public void setMode(SimulationMode mode) {
        if (mode != null) this.mode = mode;
    }

    public void setLease(UUID leaseId) { this.currentLeaseId = leaseId; }
    public void clearLease() { this.currentLeaseId = null; }

    public void persistTaskState() {
        if (task != null) {
            persistedTaskState = task.goal();
        }
    }

    public void restoreTaskState() {
        if (persistedTaskState != null && (task == null || task.isTerminal())) {
            task = new AgentTask(persistedTaskState);
            persistedTaskState = null;
        }
    }

    public void freeze() {
        setMode(SimulationMode.DORMANT);
        persistTaskState();
    }

    public void wake() {
        setMode(SimulationMode.ACTIVE);
        restoreTaskState();
    }

    @Override
    public String toString() {
        return "AgentBrain{mode=" + mode + ", task=" + (task != null ? task.goal() : "none") + "}";
    }
}