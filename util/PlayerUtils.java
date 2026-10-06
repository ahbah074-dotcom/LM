package com.livemine.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Утилиты для работы с игроками.
 */
public final class PlayerUtils {

    private PlayerUtils() {}

    /**
     * Находит ближайшего игрока к точке в радиусе.
     * @return игрок или null, если никого нет в радиусе
     */
    public static ServerPlayer findNearest(ServerLevel level, BlockPos center, double radius) {
        if (level == null || center == null) return null;

        double rsq = radius * radius;
        ServerPlayer best = null;
        double bestSq = rsq;

        for (ServerPlayer p : level.players()) {
            double d = p.blockPosition().distSqr(center);
            if (d < bestSq) {
                bestSq = d;
                best = p;
            }
        }
        return best;
    }

    public static ServerPlayer findNearest(ServerLevel level, BlockPos center) {
        return findNearest(level, center, 64.0);
    }

    public static ServerPlayer findAny(ServerLevel level) {
        if (level == null || level.players().isEmpty()) return null;
        return level.players().get(0);
    }
}