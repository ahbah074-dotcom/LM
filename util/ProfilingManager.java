package com.livemine.util;

import com.livemine.VillageManager;
import com.livemine.ai.NPCActivityManager;
import com.livemine.ai.NPCRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер профилирования.
 *
 * Собирает:
 *   - Время серверного тика (TPS)
 *   - Память
 *   - Количество NPC по состояниям
 *   - Top-5 NPC по нагрузке (заглушка)
 *
 * Используется командой /livemine profile.
 */
public final class ProfilingManager {

    private static ProfilingManager INSTANCE;

    private static final int MAX_SAMPLES = 200;

    private long tickStartTime = 0;
    private final Map<Long, Long> tickTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> npcTickTimes = new ConcurrentHashMap<>();

    private ProfilingManager() {}

    public static synchronized ProfilingManager getInstance() {
        if (INSTANCE == null) INSTANCE = new ProfilingManager();
        return INSTANCE;
    }

    // =========================================================================
    // РўРёРє
    // =========================================================================

    public void beginTick() {
        tickStartTime = System.nanoTime();
    }

    public void endTick() {
        if (tickStartTime == 0) return;
        long elapsed = System.nanoTime() - tickStartTime;
        tickTimes.put(System.currentTimeMillis(), elapsed);

        if (tickTimes.size() > MAX_SAMPLES) {
            long oldest = Long.MAX_VALUE;
            for (Long k : tickTimes.keySet()) if (k < oldest) oldest = k;
            tickTimes.remove(oldest);
        }
        tickStartTime = 0;
    }

    public void recordNPCTick(UUID uuid, long nanos) {
        npcTickTimes.put(uuid, nanos);
        if (npcTickTimes.size() > 500) {
            List<UUID> keys = new ArrayList<>(npcTickTimes.keySet());
            for (int i = 0; i < 100; i++) npcTickTimes.remove(keys.get(i));
        }
    }

    // =========================================================================
    // Метрики
    // =========================================================================

    public double getAverageTPS() {
        if (tickTimes.isEmpty()) return 20.0;
        double avgNanos = tickTimes.values().stream()
            .mapToLong(Long::longValue).average().orElse(0);
        if (avgNanos <= 0) return 20.0;
        double avgMs = avgNanos / 1_000_000.0;
        return Math.max(0.1, Math.min(20.0, 1000.0 / Math.max(avgMs, 1.0)));
    }

    public double getAverageTickMs() {
        if (tickTimes.isEmpty()) return 0;
        return tickTimes.values().stream()
            .mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0;
    }

    public long getUsedMemoryMB() {
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
    }

    public long getMaxMemoryMB() {
        return Runtime.getRuntime().maxMemory() / (1024 * 1024);
    }

    public List<Map.Entry<UUID, Long>> getTop5NPCs() {
        return npcTickTimes.entrySet().stream()
            .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
            .limit(5)
            .toList();
    }

    // =========================================================================
    // Отчёт
    // =========================================================================

    public String getProfileReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("В§6=== LiveMine Profiling ===В§r\n");
        sb.append(String.format("TPS: В§f%.1fВ§r\n", getAverageTPS()));
        sb.append(String.format("Avg tick: В§f%.2f msВ§r\n", getAverageTickMs()));
        sb.append(String.format("Memory: В§f%d / %d MBВ§r\n", getUsedMemoryMB(), getMaxMemoryMB()));

        int totalNPCs = NPCRegistry.getTotalCount();
        int active = NPCRegistry.getActiveCount();
        int simulated = NPCRegistry.getSimulatedCount();
        int dormant = NPCRegistry.getDormantCount();
        int villages = VillageManager.getInstance().getVillageCount();

        sb.append("NPC: В§f").append(totalNPCs)
            .append("В§r (ACTIVE: В§a").append(active)
            .append("В§r, SIMULATED: В§e").append(simulated)
            .append("В§r, DORMANT: В§8").append(dormant).append("В§r)\n");
        sb.append("Деревень: §f").append(villages).append("§r\n");

        sb.append("Top-5 NPC по нагрузке:\n");
        for (var e : getTop5NPCs()) {
            sb.append(String.format("  В§7%sВ§r: В§f%.3f msВ§r\n",
                e.getKey().toString().substring(0, 8),
                e.getValue() / 1_000_000.0));
        }
        return sb.toString();
    }

    public void reset() {
        tickTimes.clear();
        npcTickTimes.clear();
    }
}
