package com.livemine.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Кэш ресурсов мира.
 *
 * Сканирует чанки и запоминает ресурсы по типам:
 *   ore, wood, water, farm, forge, infirmary, magic_tower, fire_pit, storage.
 */
public final class ResourceCache {

    private static final ConcurrentHashMap<String, ChunkResourceCache> chunkCache = new ConcurrentHashMap<>();

    private ResourceCache() {}

    private static final class ChunkResourceCache {
        final Map<String, List<BlockPos>> resources = new ConcurrentHashMap<>();
        long lastScanTick = 0L;
        volatile boolean dirty = false;

        void add(String type, BlockPos pos) {
            resources.computeIfAbsent(type, k -> new ArrayList<>()).add(pos);
        }

        void remove(BlockPos pos) {
            for (List<BlockPos> list : resources.values()) {
                list.removeIf(p -> p.equals(pos));
            }
        }

        List<BlockPos> get(String type) {
            return resources.getOrDefault(type, Collections.emptyList());
        }

        void clear() { resources.clear(); }
    }

    private static String chunkKey(Level level, int cx, int cz) {
        return level.dimension().location() + ":" + cx + ":" + cz;
    }

    private static String chunkKeyAt(Level level, BlockPos pos) {
        return chunkKey(level, pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static List<BlockPos> getResources(Level level, BlockPos center, int radius,
                                                String type, long currentTick) {
        if (radius <= 0) return Collections.emptyList();

        List<BlockPos> result = new ArrayList<>();
        int minCX = (center.getX() - radius) >> 4;
        int maxCX = (center.getX() + radius) >> 4;
        int minCZ = (center.getZ() - radius) >> 4;
        int maxCZ = (center.getZ() + radius) >> 4;
        int radiusSq = radius * radius;

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                String key = chunkKey(level, cx, cz);
                ChunkResourceCache cr = chunkCache.get(key);
                if (cr == null) continue;

                if (currentTick - cr.lastScanTick > com.livemine.LiveMineConfig.resourceCacheTtl()) {
                    cr.dirty = true;
                }

                for (BlockPos pos : cr.get(type)) {
                    if (pos.distSqr(center) <= radiusSq) {
                        result.add(pos);
                    }
                }
            }
        }
        return result;
    }

    public static void scanChunk(Level level, LevelChunk chunk, long currentTick) {
        String key = chunkKey(level, chunk.getPos().x, chunk.getPos().z);
        ChunkResourceCache cr = chunkCache.computeIfAbsent(key, k -> new ChunkResourceCache());
        cr.clear();
        cr.lastScanTick = currentTick;
        cr.dirty = false;

        // 1.21.1: getBlockEntities() возвращает Map — используем entrySet()
        for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
            String type = identifyBlockEntity(entry.getValue());
            if (type != null) cr.add(type, entry.getKey());
        }

        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        int chunkX = chunk.getPos().x << 4;
        int chunkZ = chunk.getPos().z << 4;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    BlockPos pos = new BlockPos(chunkX + x, y, chunkZ + z);
                    BlockState state = chunk.getBlockState(pos);
                    String type = identifyBlockResource(state);
                    if (type != null) cr.add(type, pos);
                }
            }
        }
    }

    public static void onBlockChanged(Level level, BlockPos pos) {
        String key = chunkKeyAt(level, pos);
        ChunkResourceCache cr = chunkCache.get(key);
        if (cr != null) {
            cr.remove(pos);
            cr.dirty = true;
        }
    }

    public static void onChunkUnloaded(Level level, LevelChunk chunk) {
        chunkCache.remove(chunkKey(level, chunk.getPos().x, chunk.getPos().z));
    }

    public static void rescanDirtyChunks(Level level, long currentTick) {
        String dimPrefix = level.dimension().location() + ":";
        int rescanCount = 0;
        int maxRescanPerTick = 3;

        for (Map.Entry<String, ChunkResourceCache> e : chunkCache.entrySet()) {
            if (rescanCount >= maxRescanPerTick) break;
            if (!e.getValue().dirty) continue;
            if (!e.getKey().startsWith(dimPrefix)) continue;

            String[] parts = e.getKey().split(":");
            if (parts.length < 3) continue;

            try {
                int cx = Integer.parseInt(parts[parts.length - 2]);
                int cz = Integer.parseInt(parts[parts.length - 1]);
                LevelChunk chunk = level.getChunk(cx, cz);
                if (chunk != null) {
                    scanChunk(level, chunk, currentTick);
                    rescanCount++;
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    public static void clear() { chunkCache.clear(); }
    public static int size() { return chunkCache.size(); }

    private static String identifyBlockEntity(BlockEntity be) {
        if (be == null) return null;
        return switch (be.getClass().getSimpleName()) {
            case "VillageCenterBlockEntity"    -> "village_center";
            case "CommunalFirePitBlockEntity"  -> "fire_pit";
            case "StorageWarehouseBlockEntity" -> "storage_warehouse";
            case "StorageBlockEntity"          -> "storage";
            case "WorkshopForgeBlockEntity"    -> "forge";
            case "FarmPlotBlockEntity"         -> "farm";
            case "InfirmaryBlockEntity"        -> "infirmary";
            case "MagicTowerCoreBlockEntity"   -> "magic_tower";
            case "MarketStallBlockEntity"      -> "market_stall";
            case "CemeteryMarkerBlockEntity"   -> "cemetery";
            case "TrophyDisplayBlockEntity"    -> "trophy_display";
            default -> null;
        };
    }

    private static String identifyBlockResource(BlockState state) {
        if (state == null || state.isAir()) return null;

        String n = state.getBlock().getDescriptionId();
        if (n.contains("_ore")) return "ore";
        if (n.contains("_log") || n.contains("_wood")) return "wood";
        if (n.contains("water")) return "water";
        return null;
    }
}