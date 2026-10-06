package com.livemine.ai;

import com.livemine.LiveMineConfig;
import com.livemine.LiveMineMod;
import net.minecraft.server.MinecraftServer;

/**
 * Адаптивная частота тиков.
 *
 * 1.21.1: getAverageTickTime() → getCurrentSmoothedTickTime().
 */
public final class AdaptiveTickRate {

    public enum Tier { STABLE, NORMAL, STRESSED, CRITICAL }

    private static Tier currentTier = Tier.STABLE;
    private static int activeBatchSize = 20;

    private AdaptiveTickRate() {}

    public static void onServerTick(MinecraftServer server) {
        if (!LiveMineConfig.adaptiveTickRate()) {
            currentTier = Tier.STABLE;
            activeBatchSize = 20;
            return;
        }
        if (server.getTickCount() % 100 == 0) {
            updateTier(server);
        }
    }

    private static void updateTier(MinecraftServer server) {
        // 1.21.1
        float avgMs = server.getCurrentSmoothedTickTime();
        double tps = 1000.0 / Math.max(avgMs, 1.0);

        Tier newTier;
        if (tps > 19.0)       newTier = Tier.STABLE;
        else if (tps > 15.0)  newTier = Tier.NORMAL;
        else if (tps > 10.0)  newTier = Tier.STRESSED;
        else                  newTier = Tier.CRITICAL;

        if (newTier != currentTier) {
            currentTier = newTier;
            LiveMineMod.LOGGER.info("AdaptiveTickRate: tier changed to {} ({} TPS)",
                newTier, String.format("%.2f", tps));
        }

        activeBatchSize = switch (currentTier) {
            case STABLE   -> 20;
            case NORMAL   -> 15;
            case STRESSED -> 10;
            case CRITICAL -> 5;
        };
    }

    public static int getSimulationInterval() {
        int base = LiveMineConfig.simulationInterval();
        return switch (currentTier) {
            case STABLE   -> base;
            case NORMAL   -> base * 2;
            case STRESSED -> base * 3;
            case CRITICAL -> base * 4;
        };
    }

    public static int getBatchPerTick() {
        int base = LiveMineConfig.batchPerTick();
        return switch (currentTier) {
            case STABLE   -> base;
            case NORMAL   -> Math.max(4, base / 2);
            case STRESSED -> Math.max(3, base / 3);
            case CRITICAL -> Math.max(2, base / 4);
        };
    }

    public static int getActiveBatch() { return activeBatchSize; }
    public static Tier getCurrentTier() { return currentTier; }

    public static void init() {
        currentTier = Tier.STABLE;
        activeBatchSize = 20;
    }
}