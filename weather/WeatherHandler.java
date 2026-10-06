package com.livemine.weather;

import com.livemine.LiveMineMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Обработчик погоды.
 *
 * Логика (ТЗ 20.0 §20):
 *   - Дождь: NPC сидят в домах, −10% продуктивность
 *   - Гроза: страх у NPC с чертой "осторожность"
 *   - Снег: фермы не растут на улице
 *   - Молния: тушит огонь дождём, но может поджечь
 *
 * Тик — раз в 200 тиков (10 сек).
 */
public final class WeatherHandler {

    private static WeatherHandler INSTANCE;

    private static final long CHECK_INTERVAL = 200;
    private static final int MAX_FIRE_LOG = 256;

    private boolean raining = false;
    private boolean thundering = false;
    private long lastCheck = 0;
    private final List<BlockPos> trackedFires = new ArrayList<>();

    private WeatherHandler() {}

    public static synchronized WeatherHandler getInstance() {
        if (INSTANCE == null) INSTANCE = new WeatherHandler();
        return INSTANCE;
    }

    // =========================================================================
    // РўРёРє
    // =========================================================================

    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (now - lastCheck < CHECK_INTERVAL) return;
        lastCheck = now;

        this.raining = level.isRaining();
        this.thundering = level.isThundering();

        if (raining) {
            extinguishTrackedFires(level);
        }

        if (thundering) {
            checkLightningFire(level);
        }
    }

    // =========================================================================
    // Пожары
    // =========================================================================

    /**
     * Отслеживаемые пожары — тушим при дожде.
     */
    private void extinguishTrackedFires(ServerLevel level) {
        Iterator<BlockPos> it = trackedFires.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof FireBlock) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                LiveMineMod.LOGGER.debug("Weather extinguished fire at {}", pos);
            }
            it.remove();
        }
    }

    /**
     * Гроза может поджечь блок.
     */
    private void checkLightningFire(ServerLevel level) {
        if (level.random.nextFloat() > 0.05f) return;

        BlockPos spawn = level.getSharedSpawnPos();
        int r = 64;
        int dx = level.random.nextInt(r * 2 + 1) - r;
        int dz = level.random.nextInt(r * 2 + 1) - r;
        int dy = level.random.nextInt(20) - 5;

        BlockPos pos = spawn.offset(dx, dy, dz);
        BlockPos above = pos.above();

        if (level.getBlockState(above).isAir()
            && level.getBlockState(pos).isFlammable(level, pos, net.minecraft.core.Direction.UP)) {
            level.setBlock(above, Blocks.FIRE.defaultBlockState(), 3);
            addFire(above);
            LiveMineMod.LOGGER.info("Lightning caused fire at {}", above);
        }
    }

    // =========================================================================
    // API
    // =========================================================================

    public void addFire(BlockPos pos) {
        if (trackedFires.size() >= MAX_FIRE_LOG) {
            trackedFires.remove(0);
        }
        if (!trackedFires.contains(pos)) {
            trackedFires.add(pos.immutable());
        }
    }

    public void removeFire(BlockPos pos) {
        trackedFires.remove(pos);
    }

    public List<BlockPos> getTrackedFires() {
        return Collections.unmodifiableList(trackedFires);
    }

    public boolean isRaining() { return raining; }
    public boolean isThundering() { return thundering; }
    public boolean isStormy() { return raining || thundering; }

    /**
     * Множитель продуктивности (для работы NPC).
     * Дождь — 0.9, ясно — 1.0.
     */
    public float getProductivityMultiplier() {
        if (thundering) return 0.85f;
        if (raining) return 0.90f;
        return 1.0f;
    }

    public void clear() {
        trackedFires.clear();
        raining = false;
        thundering = false;
        lastCheck = 0;
    }
}
