package com.livemine.ai;

import com.livemine.entity.LiveNPCEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Реестр всех NPC на сервере.
 *
 * Хранит:
 *   - Ссылки на сущности (UUID → LiveNPCEntity)
 *   - Состояния активности (ACTIVE / SIMULATED / DORMANT / INACTIVE)
 *   - Пространственный индекс по чанкам (chunkKey → Set<UUID>)
 *   - Кэшированные списки для быстрого доступа
 *
 * Все операции O(1) или O(n), но только там, где это оправдано.
 */
public final class NPCRegistry {

    private static final ConcurrentHashMap<UUID, LiveNPCEntity> npcMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, NPCActivityManager.ActivityState> activityStates = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, ChunkKey> lastChunk = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ChunkKey, Set<UUID>> chunkIndex = new ConcurrentHashMap<>();

    private static volatile List<Mob> cachedAll = Collections.emptyList();
    private static volatile List<Mob> cachedActive = Collections.emptyList();
    private static volatile boolean dirty = true;

    private NPCRegistry() {}

    // =========================================================================
    // Регистрация
    // =========================================================================

    public static void register(LiveNPCEntity npc) {
        if (npc == null) return;
        UUID uuid = npc.getUUID();

        npcMap.put(uuid, npc);
        activityStates.putIfAbsent(uuid, NPCActivityManager.ActivityState.INACTIVE);

        ChunkKey ck = chunkKeyOf(npc);
        lastChunk.put(uuid, ck);
        chunkIndex.computeIfAbsent(ck, k -> ConcurrentHashMap.newKeySet()).add(uuid);

        dirty = true;
    }

    public static void unregister(UUID uuid) {
        npcMap.remove(uuid);
        activityStates.remove(uuid);

        ChunkKey old = lastChunk.remove(uuid);
        if (old != null) {
            Set<UUID> set = chunkIndex.get(old);
            if (set != null) {
                set.remove(uuid);
                if (set.isEmpty()) chunkIndex.remove(old);
            }
        }
        dirty = true;
    }

    // =========================================================================
    // Запросы
    // =========================================================================

    public static List<Mob> getAll() {
        if (dirty) {
            cachedAll = List.copyOf(npcMap.values());
            cachedActive = Collections.emptyList();
            dirty = false;
        }
        return cachedAll;
    }

    public static List<Mob> getActive() {
        getAll(); // обеспечивает актуальность
        if (cachedActive.isEmpty()) {
            List<Mob> list = new ArrayList<>();
            for (Mob mob : cachedAll) {
                if (getActivityState(mob.getUUID()) == NPCActivityManager.ActivityState.ACTIVE) {
                    list.add(mob);
                }
            }
            cachedActive = Collections.unmodifiableList(list);
        }
        return cachedActive;
    }

    public static LiveNPCEntity getNPC(UUID uuid) {
        return npcMap.get(uuid);
    }

    public static boolean isRegistered(UUID uuid) {
        return npcMap.containsKey(uuid);
    }

    public static int getTotalCount() {
        return npcMap.size();
    }

    public static int getActiveCount() {
        int count = 0;
        for (NPCActivityManager.ActivityState s : activityStates.values()) {
            if (s == NPCActivityManager.ActivityState.ACTIVE) count++;
        }
        return count;
    }

    public static int getSimulatedCount() {
        int count = 0;
        for (NPCActivityManager.ActivityState s : activityStates.values()) {
            if (s == NPCActivityManager.ActivityState.SIMULATED) count++;
        }
        return count;
    }

    public static int getDormantCount() {
        int count = 0;
        for (NPCActivityManager.ActivityState s : activityStates.values()) {
            if (s == NPCActivityManager.ActivityState.DORMANT) count++;
        }
        return count;
    }

    // =========================================================================
    // Состояния активности
    // =========================================================================

    public static NPCActivityManager.ActivityState getActivityState(UUID uuid) {
        return activityStates.getOrDefault(uuid, NPCActivityManager.ActivityState.INACTIVE);
    }

    public static void setActivityState(UUID uuid, NPCActivityManager.ActivityState state) {
        activityStates.put(uuid, state);
        dirty = true;
    }

    public static void setActive(UUID uuid) {
        setActivityState(uuid, NPCActivityManager.ActivityState.ACTIVE);
    }

    public static void setSimulated(UUID uuid) {
        setActivityState(uuid, NPCActivityManager.ActivityState.SIMULATED);
    }

    // =========================================================================
    // Пространственный индекс по чанкам
    // =========================================================================

    public static void updateChunkPosition(LiveNPCEntity npc) {
        if (npc == null) return;
        UUID uuid = npc.getUUID();
        ChunkKey now = chunkKeyOf(npc);
        ChunkKey old = lastChunk.get(uuid);
        if (now.equals(old)) return;

        if (old != null) {
            Set<UUID> set = chunkIndex.get(old);
            if (set != null) {
                set.remove(uuid);
                if (set.isEmpty()) chunkIndex.remove(old);
            }
        }
        chunkIndex.computeIfAbsent(now, k -> ConcurrentHashMap.newKeySet()).add(uuid);
        lastChunk.put(uuid, now);
    }

    public static Set<UUID> getNPCsInChunk(ChunkKey key) {
        return chunkIndex.getOrDefault(key, Collections.emptySet());
    }

    public static ChunkKey chunkKeyOf(Mob mob) {
        return new ChunkKey(
            mob.level().dimension().location().toString(),
            mob.blockPosition().getX() >> 4,
            mob.blockPosition().getZ() >> 4
        );
    }

    // =========================================================================
    // Очистка
    // =========================================================================

    public static void clear() {
        npcMap.clear();
        activityStates.clear();
        lastChunk.clear();
        chunkIndex.clear();
        cachedAll = Collections.emptyList();
        cachedActive = Collections.emptyList();
        dirty = true;
    }

    /** Дополнительная очистка NPC, умерших/выгруженных вне событий. */
    public static void purgeInvalid() {
        boolean changed = false;
        for (var entry : npcMap.entrySet()) {
            LiveNPCEntity npc = entry.getValue();
            if (npc == null || !npc.isAlive() || npc.isRemoved()) {
                unregister(entry.getKey());
                changed = true;
            }
        }
        if (changed) dirty = true;
    }

    // =========================================================================
    // Вспомогательное
    // =========================================================================

    public static void markDirty() { dirty = true; }

    /**
     * Ключ чанка — используется для пространственного индекса.
     */
    public static final class ChunkKey {
        private final String dim;
        private final int x, z;

        public ChunkKey(String dim, int x, int z) {
            this.dim = dim;
            this.x = x;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ChunkKey k)) return false;
            return x == k.x && z == k.z && dim.equals(k.dim);
        }

        @Override
        public int hashCode() {
            return dim.hashCode() * 31 + x * 17 + z;
        }

        @Override
        public String toString() {
            return dim + ":" + x + ":" + z;
        }
    }
}
