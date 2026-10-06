package com.livemine.world;

import com.livemine.LiveMineMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Планировщик дорог между деревнями (ТЗ 20.0 §28.4).
 *
 * A* алгоритм с лимитом итераций (1000).
 * Фаза 1: дорога шириной 1 блок (bulldozer).
 * Фаза 2: расширение до 3 блоков после соединения.
 *
 * Материал по типу биома:
 *   - plains/forest → булыжник
 *   - desert → песчаник
 *   - taiga → гравий
 */
public final class RoadPlanner {

    public static final int MAX_ITERATIONS = 1000;
    public static final int DEFAULT_MAX_PATH_LENGTH = 500;

    private RoadPlanner() {}

    /**
     * Планирует дорогу от start до end.
     * Возвращает список BlockPos или null, если не удалось.
     */
    public static List<BlockPos> plan(ServerLevel level, BlockPos start, BlockPos end) {
        if (start == null || end == null) return null;

        int maxIter = MAX_ITERATIONS;
        int maxPathLen = DEFAULT_MAX_PATH_LENGTH;

        PriorityQueue<Node> open = new PriorityQueue<>((a, b) ->
            Double.compare(a.f + a.g, b.f + b.g));
        var closed = new java.util.HashSet<Long>();

        Node startNode = new Node(start.getX(), start.getZ(), 0, heuristic(start, end), null);
        open.add(startNode);

        int iterations = 0;
        Node goal = null;

        while (!open.isEmpty() && iterations < maxIter && iterations < maxPathLen * 4) {
            iterations++;
            Node current = open.poll();

            if (current.x == end.getX() && current.z == end.getZ()) {
                goal = current;
                break;
            }

            long key = packXZ(current.x, current.z);
            if (closed.contains(key)) continue;
            closed.add(key);

            // 8 соседей
            int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
            for (int[] d : dirs) {
                int nx = current.x + d[0];
                int nz = current.z + d[1];

                BlockPos np = new BlockPos(nx, level.getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    nx, nz), nz);

                if (!isPassable(level, np)) continue;

                long nk = packXZ(nx, nz);
                if (closed.contains(nk)) continue;

                double g = current.g + distance(current.x, current.z, nx, nz);
                double h = heuristic(np, end);
                open.add(new Node(nx, nz, g, h, current));
            }
        }

        if (goal == null) {
            LiveMineMod.LOGGER.warn("RoadPlanner: no path from {} to {} (iterations {})",
                start, end, iterations);
            return null;
        }

        // Восстанавливаем путь
        List<BlockPos> path = new ArrayList<>();
        Node cur = goal;
        while (cur != null) {
            int y = level.getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                cur.x, cur.z);
            path.add(0, new BlockPos(cur.x, y, cur.z));
            cur = cur.parent;
        }

        LiveMineMod.LOGGER.info("RoadPlanner: path found, {} blocks, {} iterations",
            path.size(), iterations);
        return path;
    }

    /**
     * Прокладывает дорогу 1 блок шириной по найденному пути.
     */
    public static int buildRoad(ServerLevel level, List<BlockPos> path, String biomeStyle) {
        if (path == null || path.isEmpty()) return 0;

        BlockState roadBlock = getRoadBlock(biomeStyle);
        int placed = 0;

        for (BlockPos pos : path) {
            BlockPos below = pos.below();
            BlockState belowState = level.getBlockState(below);
            if (belowState.isAir() || belowState.canBeReplaced()) {
                level.setBlock(below, roadBlock, 3);
                placed++;
            }
            // Очищаем место над дорогой
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        return placed;
    }

    /**
     * Фаза 2: расширение дороги до 3 блоков.
     */
    public static int expandRoad(ServerLevel level, List<BlockPos> path, String biomeStyle) {
        if (path == null || path.isEmpty()) return 0;

        BlockState roadBlock = getRoadBlock(biomeStyle);
        int placed = 0;

        for (BlockPos pos : path) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos side = new BlockPos(pos.getX() + dx, pos.getY() - 1, pos.getZ() + dz);
                    if (level.getBlockState(side).canBeReplaced()) {
                        level.setBlock(side, roadBlock, 3);
                        placed++;
                    }
                }
            }
        }
        return placed;
    }

    // =========================================================================
    // Вспомогательные
    // =========================================================================

    private static boolean isPassable(ServerLevel level, BlockPos pos) {
        // Не вода, не лава
        BlockState at = level.getBlockState(pos);
        if (!at.getFluidState().isEmpty()) return false;

        // В пределах высоты
        return pos.getY() > level.getMinBuildHeight() + 1
            && pos.getY() < level.getMaxBuildHeight() - 1;
    }

    private static double heuristic(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ());
    }

    private static double distance(int x1, int z1, int x2, int z2) {
        int dx = x1 - x2;
        int dz = z1 - z2;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static BlockState getRoadBlock(String biome) {
        if (biome == null) return Blocks.COBBLESTONE.defaultBlockState();
        return switch (biome.toLowerCase()) {
            case "desert" -> Blocks.SANDSTONE.defaultBlockState();
            case "taiga" -> Blocks.GRAVEL.defaultBlockState();
            case "snowy" -> Blocks.PACKED_ICE.defaultBlockState();
            default -> Blocks.COBBLESTONE.defaultBlockState();
        };
    }

    private static long packXZ(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    // =========================================================================
    // Внутренний класс
    // =========================================================================

    private static final class Node {
        final int x, z;
        final double g, f;
        final Node parent;

        Node(int x, int z, double g, double h, Node parent) {
            this.x = x;
            this.z = z;
            this.g = g;
            this.f = g + h;
            this.parent = parent;
        }
    }
}
