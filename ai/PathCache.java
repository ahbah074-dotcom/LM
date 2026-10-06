package com.livemine.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Path;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Кэш путей NPC.
 *
 * Если два NPC идут к одной точке — второй берёт готовый путь.
 * TTL: 1200 тиков (60 сек).
 * Инвалидация при изменении блоков.
 *
 * ВАЖНО: путь кэшируется только если его можно безопасно переиспользовать.
 * В NeoForge 1.21 Path — иммутабельный, ссылку можно отдавать.
 */
public final class PathCache {

    private static final ConcurrentHashMap<String, CachedPath> cache = new ConcurrentHashMap<>();
    private static final long TTL_TICKS = 1200;
    private static final int MAX_ENTRIES = 256;

    private PathCache() {}

    // =========================================================================
    // Геттеры
    // =========================================================================

    public static Path get(BlockPos from, BlockPos to, long currentTick) {
        String key = makeKey(from, to);
        CachedPath cp = cache.get(key);
        if (cp == null) return null;

        if (currentTick - cp.tick > TTL_TICKS) {
            cache.remove(key);
            return null;
        }
        return cp.path;
    }

    public static void put(BlockPos from, BlockPos to, Path path, long currentTick) {
        if (path == null) return;

        // Ограничение размера
        if (cache.size() >= MAX_ENTRIES) {
            evictOldest();
        }

        cache.put(makeKey(from, to), new CachedPath(path, currentTick));
    }

    // =========================================================================
    // Инвалидация
    // =========================================================================

    /**
     * Инвалидирует пути, проходящие через область вокруг pos.
     * Вызывается при ломании/установке блока.
     */
    public static void invalidateAround(BlockPos pos) {
        if (pos == null) return;
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();

        cache.entrySet().removeIf(e -> {
            CachedPath cp = e.getValue();
            if (cp.path == null) return true;
            try {
                int nodes = cp.path.getNodeCount();
                for (int i = 0; i < nodes; i++) {
                    net.minecraft.world.level.pathfinder.Node node = cp.path.getNode(i);
                    if (Math.abs(node.x - x) <= 1
                        && Math.abs(node.y - y) <= 1
                        && Math.abs(node.z - z) <= 1) {
                        return true;
                    }
                }
            } catch (Exception ignored) {
                return true;
            }
            return false;
        });
    }

    public static void clear() { cache.clear(); }
    public static int size() { return cache.size(); }

    // =========================================================================
    // Утилиты
    // =========================================================================

    private static String makeKey(BlockPos from, BlockPos to) {
        return from.getX() + ":" + from.getY() + ":" + from.getZ()
            + "->" + to.getX() + ":" + to.getY() + ":" + to.getZ();
    }

    private static void evictOldest() {
        String oldestKey = null;
        long oldestTick = Long.MAX_VALUE;
        for (var e : cache.entrySet()) {
            if (e.getValue().tick < oldestTick) {
                oldestTick = e.getValue().tick;
                oldestKey = e.getKey();
            }
        }
        if (oldestKey != null) cache.remove(oldestKey);
    }

    private record CachedPath(Path path, long tick) {}
}
