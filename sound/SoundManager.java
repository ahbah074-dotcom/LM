package com.livemine.sound;

import com.livemine.LiveMineConfig;
import com.livemine.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * Менеджер звуков (ТЗ 20.0 §38).
 *
 * Управляет воспроизведением кастомных звуков мода:
 *   - NPC (работа, еда, отдых, общение, боль, смерть)
 *   - События (праздник, свадьба, тревога, караван)
 *   - Блоки (кузница, строительство)
 *
 * Все звуки отключаются в клиентском конфиге.
 */
public final class SoundManager {

    private static SoundManager INSTANCE;

    private SoundManager() {}

    public static synchronized SoundManager getInstance() {
        if (INSTANCE == null) INSTANCE = new SoundManager();
        return INSTANCE;
    }

    // =========================================================================
    // Воспроизведение на сервере
    // =========================================================================

    public void playAt(ServerLevel level, BlockPos pos, SoundEvent sound,
                        float volume, float pitch) {
        if (level == null || sound == null) return;
        if (!LiveMineConfig.enableSounds()) return;

        level.playSound(
            null,
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            sound, SoundSource.NEUTRAL,
            volume, pitch
        );
    }

    public void playForPlayer(Player player, SoundEvent sound, float volume, float pitch) {
        if (player == null || sound == null) return;
        if (!LiveMineConfig.enableSounds()) return;

        player.playNotifySound(sound, SoundSource.NEUTRAL, volume, pitch);
    }

    // =========================================================================
    // События NPC
    // =========================================================================

    public void npcWork(ServerLevel level, BlockPos pos) {
        playAt(level, pos, ModSounds.NPC_WORK.get(), 0.4f, 1.0f);
    }

    public void npcEat(ServerLevel level, BlockPos pos) {
        playAt(level, pos, ModSounds.NPC_EAT.get(), 0.5f, 1.0f);
    }

    public void npcRest(ServerLevel level, BlockPos pos) {
        playAt(level, pos, ModSounds.NPC_REST.get(), 0.3f, 1.0f);
    }

    public void npcSocial(ServerLevel level, BlockPos pos) {
        playAt(level, pos, ModSounds.NPC_SOCIAL.get(), 0.4f, 1.0f);
    }

    public void npcHurt(ServerLevel level, BlockPos pos) {
        playAt(level, pos, ModSounds.NPC_HURT.get(), 0.6f, 1.0f);
    }

    public void npcDeath(ServerLevel level, BlockPos pos) {
        playAt(level, pos, ModSounds.NPC_DEATH.get(), 0.7f, 1.0f);
    }

    public void npcGreeting(ServerLevel level, BlockPos pos) {
        playAt(level, pos, ModSounds.NPC_GREETING.get(), 0.5f, 1.0f);
    }

    // =========================================================================
    // События мира
    // =========================================================================

    public void villageBell(ServerLevel level, BlockPos pos) {
        playAt(level, pos, ModSounds.VILLAGE_BELL.get(), 1.0f, 1.0f);
    }

    public void buildingHammer(ServerLevel level, BlockPos pos) {
        playAt(level, pos, ModSounds.BUILDING_HAMMER.get(), 0.4f, 1.0f);
    }

    public void holidayCheer(ServerLevel level, BlockPos pos) {
        playAt(level, pos, ModSounds.HOLIDAY_CHEER.get(), 1.0f, 1.0f);
    }

    public void wedding(ServerLevel level, BlockPos pos) {
        playAt(level, pos, ModSounds.WEDDING_SOUND.get(), 1.0f, 1.0f);
    }

    public void festival(ServerLevel level, BlockPos pos) {
        playAt(level, pos, ModSounds.FESTIVAL_SOUND.get(), 1.0f, 1.0f);
    }

    public void magicCast(ServerLevel level, BlockPos pos) {
        playAt(level, pos, ModSounds.MAGIC_CAST.get(), 0.7f, 1.0f);
    }

    // =========================================================================
    // Массовые события
    // =========================================================================

    /**
     * Звук тревоги по всей деревне.
     */
    public void alert(ServerLevel level, BlockPos center, int radius) {
        if (!LiveMineConfig.enableSounds()) return;

        for (Player player : level.players()) {
            double dist = player.blockPosition().distSqr(center);
            if (dist <= (double) radius * radius) {
                playForPlayer(player, ModSounds.VILLAGE_BELL.get(), 1.0f, 1.0f);
            }
        }
    }
}
