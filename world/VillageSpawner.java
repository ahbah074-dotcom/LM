package com.livemine.world;

import com.livemine.LiveMineConfig;
import com.livemine.LiveMineMod;
import com.livemine.LiveMineSavedData;
import com.livemine.VillageManager;
import com.livemine.building.BuildingSystem;
import com.livemine.entity.LiveNPCEntity;
import com.livemine.registry.ModBlocks;
import com.livemine.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashSet;
import java.util.Set;

/**
 * Спавн деревень при создании мира.
 *
 * v3.4:
 *   - placeBuilding расчищает область вместо пропуска.
 *   - Логирование причин skip.
 *   - Пытается создать больше деревень, чем нужно (компенсация отсева).
 */
public final class VillageSpawner {

    private VillageSpawner() {}

    public static void spawnInitialVillages(ServerLevel level) {
        if (!LiveMineConfig.enableVillageGeneration()) return;
        if (VillageManager.getInstance().getVillageCount() > 0) return;

        RandomSource rnd = level.random;

        int minV = LiveMineConfig.minVillages();
        int maxV = LiveMineConfig.maxVillages();
        int target = minV + rnd.nextInt(Math.max(1, maxV - minV + 1));

        // Пытаемся создать в 2 раза больше — часть отсеется.
        int attempts = target * 2;

        BlockPos spawnPos = level.getSharedSpawnPos();
        Set<Long> usedPositions = new HashSet<>();

        LiveMineMod.LOGGER.info("LiveMine: spawning {} initial villages (target {})",
            attempts, target);

        int spawned = 0;

        for (int i = 0; i < attempts && spawned < maxV; i++) {
            double angle = (2.0 * Math.PI * i) / attempts;
            int distance = LiveMineConfig.minDistanceFromSpawn()
                + rnd.nextInt(LiveMineConfig.maxDistanceFromSpawn()
                - LiveMineConfig.minDistanceFromSpawn());

            int dx = (int) (Math.cos(angle) * distance);
            int dz = (int) (Math.sin(angle) * distance);

            int x = spawnPos.getX() + dx;
            int z = spawnPos.getZ() + dz;

            if (tooClose(usedPositions, x, z,
                LiveMineConfig.minDistanceBetweenVillages())) {
                LiveMineMod.LOGGER.debug(
                    "Village skip at ({},{}): too close to another", x, z);
                continue;
            }

            forceLoadChunk(level, x, z);

            int y = getSafeY(level, x, z);
            if (y <= level.getMinBuildHeight() + 5) {
                LiveMineMod.LOGGER.warn(
                    "Village skip at ({},{}): bad Y={}", x, z, y);
                continue;
            }

            BlockPos pos = new BlockPos(x, y, z);

            if (!isSuitableBiome(level, pos)) {
                var biome = level.getBiome(pos).unwrapKey()
                    .map(k -> k.location().toString()).orElse("?");
                LiveMineMod.LOGGER.info(
                    "Village skip at {}: unsuitable biome '{}'", pos, biome);
                continue;
            }

            createVillage(level, pos, spawned);
            usedPositions.add(packXZ(x, z));
            spawned++;

            if (spawned >= maxV) break;
        }

        LiveMineMod.LOGGER.info("LiveMine: {} villages actually spawned (target {})",
            spawned, target);
    }

    // =========================================================================
    // Форс-загрузка чанка
    // =========================================================================

    private static void forceLoadChunk(ServerLevel level, int x, int z) {
        int cx = x >> 4;
        int cz = z >> 4;
        try {
            level.getChunk(cx, cz);
        } catch (Exception e) {
            LiveMineMod.LOGGER.error("forceLoadChunk failed for ({}, {})", cx, cz, e);
        }
    }

    /**
     * Реальный Y поверхности. Пытается 3 способа.
     */
    private static int getSafeY(ServerLevel level, int x, int z) {
        // Способ 1: стандартный.
        int y1 = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        if (y1 > level.getMinBuildHeight() + 5) return y1;

        // Способ 2: motion blocking.
        int y2 = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y2 > level.getMinBuildHeight() + 5) return y2;

        // Способ 3: поиск сверху вниз.
        int maxY = level.getMaxBuildHeight() - 1;
        int minY = level.getMinBuildHeight() + 1;
        for (int checkY = maxY; checkY > minY; checkY--) {
            BlockPos p = new BlockPos(x, checkY, z);
            BlockState st = level.getBlockState(p);
            if (!st.isAir() && st.getFluidState().isEmpty()
                && !st.is(Blocks.BEDROCK)) {
                return checkY + 1;
            }
        }

        return y1;
    }

    // =========================================================================
    // Создание деревни
    // =========================================================================

    private static void createVillage(ServerLevel level, BlockPos center, int index) {
        String name = "Деревня_" + (index + 1) + "_"
            + center.getX() + "_" + center.getZ();

        LiveMineMod.LOGGER.info("Creating village at {}", center);

        var village = VillageManager.getInstance()
            .createVillage(name, center, level, "generic");

        level.setBlock(center,
            ModBlocks.VILLAGE_CENTER.get().defaultBlockState(), 3);

        buildBaseSettlement(level, center);

        String vidStr = village.rawId();
        int npcCount = LiveMineConfig.npcPerNewVillage();
        int spawned = 0;
        for (int i = 0; i < npcCount; i++) {
            if (spawnNPC(level, center, vidStr)) spawned++;
        }

        LiveMineMod.LOGGER.info(
            "Village '{}' created at {} with {} NPCs (of {} planned)",
            name, center, spawned, npcCount);
    }

    private static void buildBaseSettlement(ServerLevel level, BlockPos center) {
        // Колодец.
        placeBuilding(level, center.offset(4, 0, 4), BuildingSystem.BuildingType.WELL);

        // Костёр.
        BlockPos firePitPos = center.offset(8, 0, 0);
        forceLoadChunk(level, firePitPos.getX(), firePitPos.getZ());
        int fpY = getSafeY(level, firePitPos.getX(), firePitPos.getZ());
        level.setBlock(new BlockPos(firePitPos.getX(), fpY, firePitPos.getZ()),
            ModBlocks.COMMUNAL_FIRE_PIT.get().defaultBlockState(), 3);
        LiveMineMod.LOGGER.info("Placed FIRE_PIT at ({},{},{})",
            firePitPos.getX(), fpY, firePitPos.getZ());

        // Склад.
        BlockPos storagePos = center.offset(-6, 0, 6);
        forceLoadChunk(level, storagePos.getX(), storagePos.getZ());
        int stY = getSafeY(level, storagePos.getX(), storagePos.getZ());
        level.setBlock(new BlockPos(storagePos.getX(), stY, storagePos.getZ()),
            ModBlocks.STORAGE.get().defaultBlockState(), 3);
        LiveMineMod.LOGGER.info("Placed STORAGE at ({},{},{})",
            storagePos.getX(), stY, storagePos.getZ());

        // Кузница.
        placeBuilding(level, center.offset(8, 0, -8), BuildingSystem.BuildingType.FORGE);

        // Ещё один дом.
        placeBuilding(level, center.offset(-8, 0, -4), BuildingSystem.BuildingType.HOUSE_SMALL);
    }

    /**
     * v3.4: строит здание, РАСЧИЩАЯ препятствия.
     * Не отказывается, если на пути дерево/куст — сносит.
     */
    private static void placeBuilding(ServerLevel level, BlockPos around,
                                       BuildingSystem.BuildingType type) {
        try {
            int x = around.getX();
            int z = around.getZ();

            forceLoadChunk(level, x, z);
            int y = getSafeY(level, x, z);
            if (y <= level.getMinBuildHeight() + 5) {
                LiveMineMod.LOGGER.warn("placeBuilding({}) skip at ({},{}): Y={}",
                    type, x, z, y);
                return;
            }

            BlockPos origin = new BlockPos(x, y, z);

            var plan = BuildingSystem.getInstance().getPlan(type);
            if (plan == null) {
                LiveMineMod.LOGGER.warn("placeBuilding({}): no plan", type);
                return;
            }

            // v3.4: РАСЧИЩАЕМ область — сносим всё, кроме bedrock.
            for (int dx = -1; dx <= plan.width; dx++) {
                for (int dz = -1; dz <= plan.depth; dz++) {
                    for (int dy = 0; dy <= plan.height + 1; dy++) {
                        BlockPos p = origin.offset(dx, dy, dz);
                        BlockState st = level.getBlockState(p);
                        if (st.isAir()) continue;
                        if (st.is(Blocks.BEDROCK)) continue;
                        if (st.getFluidState().isEmpty() == false) continue;
                        // Сносим всё остальное.
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }

            // Ставим блоки.
            for (var element : plan.elements) {
                BlockPos p = origin.offset(element.offset);
                level.setBlock(p, element.block, 3);
            }

            LiveMineMod.LOGGER.info("Placed {} at {}", type.name(), origin);
        } catch (Exception e) {
            LiveMineMod.LOGGER.error("placeBuilding failed for {}", type, e);
        }
    }

    // =========================================================================
    // NPC
    // =========================================================================

    private static boolean spawnNPC(ServerLevel level, BlockPos center, String villageId) {
        RandomSource rnd = level.random;
        int dx = rnd.nextInt(24) - 12;
        int dz = rnd.nextInt(24) - 12;
        int x = center.getX() + dx;
        int z = center.getZ() + dz;

        forceLoadChunk(level, x, z);
        int y = getSafeY(level, x, z);
        if (y <= level.getMinBuildHeight() + 5) return false;

        BlockPos pos = new BlockPos(x, y, z);

        // Освобождаем место для NPC.
        if (!level.getBlockState(pos).isAir()) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        if (!level.getBlockState(pos.above()).isAir()) {
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
        }

        LiveNPCEntity npc = ModEntities.LIVE_NPC.get().create(level);
        if (npc == null) return false;

        npc.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);

        String name = com.livemine.NamePool.getInstance().generateName(rnd.nextBoolean());
        npc.setCustomNameTag(name);
        npc.setChild(rnd.nextFloat() < 0.15f);

        if (npc.isChild()) {
            npc.setAgeInDays(0);
        } else {
            npc.setAgeInDays(18 + rnd.nextInt(40));
            String[] profs = {"farmer", "miner", "guard", "merchant", "blacksmith", "fisherman"};
            npc.setProfessionName(profs[rnd.nextInt(profs.length)]);
        }

        npc.setVillageRawId(villageId);

        level.addFreshEntity(npc);
        com.livemine.ai.NPCRegistry.register(npc);

        initNpcData(level, npc, villageId);
        return true;
    }

    private static void initNpcData(ServerLevel level, LiveNPCEntity npc, String villageId) {
        LiveMineSavedData data = LiveMineSavedData.get(level);
        CompoundTag tag = data.loadNPCData(npc.getUUID());

        if (tag.isEmpty()) {
            tag.putDouble("health", 20.0);
            tag.putDouble("hunger", 100.0);
            tag.putDouble("energy", 100.0);
            tag.putDouble("social", 100.0);
            tag.putString("current_goal", "WANDER");
            tag.putString("village_id", villageId != null ? villageId : "");
            tag.putBoolean("needs_chest", false);
            tag.putBoolean("needs_wood", false);
            tag.putLong("last_sim_tick", 0L);
            tag.putBoolean("dead", false);
            tag.putLong("birth_day", level.getDayTime() / 24000L);
        } else {
            if (villageId != null && !villageId.isEmpty()) {
                tag.putString("village_id", villageId);
            }
        }

        data.saveNPCData(npc.getUUID(), tag);
    }

    // =========================================================================
    // Утилиты
    // =========================================================================

    private static boolean tooClose(Set<Long> used, int x, int z, int minDist) {
        long minSq = (long) minDist * minDist;
        for (long packed : used) {
            int ux = (int) (packed >> 32);
            int uz = (int) packed;
            long dx = x - ux;
            long dz = z - uz;
            if (dx * dx + dz * dz < minSq) return true;
        }
        return false;
    }

    private static long packXZ(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static boolean isSuitableBiome(ServerLevel level, BlockPos pos) {
        var biome = level.getBiome(pos);
        String key = biome.unwrapKey().map(k -> k.location().toString()).orElse("");
        return !key.contains("ocean")
            && !key.contains("river")
            && !key.contains("deep")
            && !key.contains("nether")
            && !key.contains("end")
            && !key.contains("mushroom");
    }
}