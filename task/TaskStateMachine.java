package com.livemine.task;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Конечный автомат игровых задач NPC.
 *
 * Обрабатывает конкретные работы: добыча, крафт, стройка, охота, лечение.
 * Отличается от доменного TaskState (domain.TaskState) — там только FSM
 * состояний задачи, здесь — FSM с типом задачи, координатами, прогрессом.
 *
 * Схема:
 *   IDLE в†' ASSIGNED в†' MOVING_TO_TARGET в†' WORKING в†' COMPLETED
 *                     ↓                       ↓
 *                     WAITING_FOR_RESOURCES   FAILED → CANCELLED (после retry)
 *
 * Хранит активные и завершённые задачи, поддерживает retry до 3 раз.
 */
public final class TaskStateMachine {

    // =========================================================================
    // Состояние задачи
    // =========================================================================
    public enum TaskState {
        IDLE,
        ASSIGNED,
        MOVING_TO_TARGET,
        WORKING,
        WAITING_FOR_RESOURCES,
        COMPLETED,
        FAILED,
        CANCELLED,
        PAUSED;

        public boolean terminal() {
            return this == COMPLETED || this == FAILED || this == CANCELLED;
        }

        public boolean canRetry() {
            return this != COMPLETED && this != CANCELLED;
        }
    }

    // =========================================================================
    // Task
    // =========================================================================
    public static final class Task {
        public UUID taskId;
        public TaskType type;
        public TaskState state;
        public UUID assignedNPC;
        public int targetX, targetY, targetZ;
        public long createdTick;
        public long lastStateChangeTick;
        public int retryCount;
        public int maxRetries;
        public String failureReason;
        public float progress;

        public Task(TaskType type, UUID npc, int x, int y, int z, long tick) {
            this.taskId = UUID.randomUUID();
            this.type = type;
            this.state = TaskState.ASSIGNED;
            this.assignedNPC = npc;
            this.targetX = x;
            this.targetY = y;
            this.targetZ = z;
            this.createdTick = tick;
            this.lastStateChangeTick = tick;
            this.retryCount = 0;
            this.maxRetries = 3;
            this.failureReason = "";
            this.progress = 0.0f;
        }

        public boolean transitionTo(TaskState newState, long tick) {
            if (!isValidTransition(state, newState)) return false;
            state = newState;
            lastStateChangeTick = tick;
            return true;
        }

        public boolean canRetry() {
            return retryCount < maxRetries;
        }

        public void retry(long tick) {
            retryCount++;
            state = TaskState.ASSIGNED;
            lastStateChangeTick = tick;
            failureReason = "";
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("taskId", taskId);
            tag.putString("type", type.name());
            tag.putString("state", state.name());
            tag.putUUID("assignedNPC", assignedNPC);
            tag.putInt("targetX", targetX);
            tag.putInt("targetY", targetY);
            tag.putInt("targetZ", targetZ);
            tag.putLong("createdTick", createdTick);
            tag.putLong("lastChange", lastStateChangeTick);
            tag.putInt("retryCount", retryCount);
            tag.putInt("maxRetries", maxRetries);
            tag.putString("failureReason", failureReason);
            tag.putFloat("progress", progress);
            return tag;
        }

        public static Task load(CompoundTag tag) {
            Task t = new Task(
                TaskType.valueOf(tag.getString("type")),
                tag.getUUID("assignedNPC"),
                tag.getInt("targetX"),
                tag.getInt("targetY"),
                tag.getInt("targetZ"),
                tag.getLong("createdTick")
            );
            t.taskId = tag.getUUID("taskId");
            t.state = TaskState.valueOf(tag.getString("state"));
            t.lastStateChangeTick = tag.getLong("lastChange");
            t.retryCount = tag.getInt("retryCount");
            t.maxRetries = tag.getInt("maxRetries");
            t.failureReason = tag.getString("failureReason");
            t.progress = tag.getFloat("progress");
            return t;
        }
    }

    // =========================================================================
    // Валидные переходы
    // =========================================================================
    private static boolean isValidTransition(TaskState from, TaskState to) {
        if (from.terminal()) return false;

        return switch (from) {
            case IDLE -> to == TaskState.ASSIGNED;
            case ASSIGNED -> to == TaskState.MOVING_TO_TARGET
                          || to == TaskState.WAITING_FOR_RESOURCES
                          || to == TaskState.FAILED
                          || to == TaskState.CANCELLED;
            case MOVING_TO_TARGET -> to == TaskState.WORKING
                                  || to == TaskState.FAILED
                                  || to == TaskState.WAITING_FOR_RESOURCES
                                  || to == TaskState.CANCELLED;
            case WORKING -> to == TaskState.COMPLETED
                         || to == TaskState.FAILED
                         || to == TaskState.PAUSED
                         || to == TaskState.CANCELLED;
            case WAITING_FOR_RESOURCES -> to == TaskState.MOVING_TO_TARGET
                                       || to == TaskState.FAILED
                                       || to == TaskState.CANCELLED;
            case PAUSED -> to == TaskState.WORKING || to == TaskState.CANCELLED;
            default -> false;
        };
    }

    // =========================================================================
    // Хранилище
    // =========================================================================
    private static TaskStateMachine INSTANCE;

    private final Map<UUID, Task> activeTasks = new HashMap<>();
    private final List<Task> completedTasks = new ArrayList<>();
    private static final int MAX_COMPLETED = 500;

    private TaskStateMachine() {}

    public static synchronized TaskStateMachine getInstance() {
        if (INSTANCE == null) INSTANCE = new TaskStateMachine();
        return INSTANCE;
    }

    // =========================================================================
    // Операции
    // =========================================================================

    public Task createTask(TaskType type, UUID npc, int x, int y, int z, long tick) {
        Task t = new Task(type, npc, x, y, z, tick);
        activeTasks.put(t.taskId, t);
        return t;
    }

    public Task getTask(UUID taskId) {
        return activeTasks.get(taskId);
    }

    public List<Task> getTasksByNPC(UUID npcUUID) {
        List<Task> result = new ArrayList<>();
        for (Task t : activeTasks.values()) {
            if (t.assignedNPC.equals(npcUUID)) result.add(t);
        }
        return result;
    }

    public void updateTask(UUID taskId, TaskState newState, long tick, float progressDelta) {
        Task t = activeTasks.get(taskId);
        if (t == null) return;

        t.progress = Math.min(1.0f, t.progress + progressDelta);

        if (newState != t.state) {
            if (!t.transitionTo(newState, tick)) {
                com.livemine.LiveMineMod.LOGGER.warn(
                    "Invalid task transition: {} -> {} (task {})",
                    t.state, newState, taskId);
                return;
            }
        }

        if (t.state.terminal()) {
            if (t.state == TaskState.FAILED && t.canRetry()) {
                t.retry(tick);
            } else {
                activeTasks.remove(taskId);
                completedTasks.add(t);
                if (completedTasks.size() > MAX_COMPLETED) {
                    completedTasks.remove(0);
                }
            }
        }
    }

    public List<Task> getActiveTasks() {
        return new ArrayList<>(activeTasks.values());
    }

    public int getActiveCount() {
        return activeTasks.size();
    }

    /**
     * Отменить задачу по id.
     */
    public boolean cancelTask(UUID taskId) {
        Task t = activeTasks.remove(taskId);
        if (t == null) return false;
        t.state = TaskState.CANCELLED;
        completedTasks.add(t);
        return true;
    }

    /**
     * Отменить все задачи NPC.
     */
    public void cancelTasksForNPC(UUID npcUUID) {
        activeTasks.values().removeIf(t -> {
            if (t.assignedNPC.equals(npcUUID)) {
                t.state = TaskState.CANCELLED;
                completedTasks.add(t);
                return true;
            }
            return false;
        });
    }

    public void clear() {
        activeTasks.clear();
        completedTasks.clear();
    }

    // =========================================================================
    // NBT
    // =========================================================================

    public void save(CompoundTag tag) {
        ListTag active = new ListTag();
        for (Task t : activeTasks.values()) active.add(t.save());
        tag.put("activeTasks", active);

        ListTag completed = new ListTag();
        for (Task t : completedTasks) completed.add(t.save());
        tag.put("completedTasks", completed);
    }

    public void load(CompoundTag tag) {
        activeTasks.clear();
        completedTasks.clear();

        ListTag active = tag.getList("activeTasks", Tag.TAG_COMPOUND);
        for (int i = 0; i < active.size(); i++) {
            Task t = Task.load(active.getCompound(i));
            activeTasks.put(t.taskId, t);
        }

        ListTag completed = tag.getList("completedTasks", Tag.TAG_COMPOUND);
        for (int i = 0; i < completed.size(); i++) {
            completedTasks.add(Task.load(completed.getCompound(i)));
        }
    }
}
