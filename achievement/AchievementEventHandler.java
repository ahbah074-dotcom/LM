package com.livemine.achievement;

import com.livemine.LiveMineMod;
import com.livemine.entity.LiveNPCEntity;
import com.livemine.util.PlayerUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Обработчик событий → достижения LiveMine.
 *
 * v1.0:
 *   - HONORABLE_BATTLE: игрок убил NPC в бою
 *   - Приветствие при входе
 *
 * Расширяется: NPCBrain будет вызывать onMasterAchieved() через
 * AchievementManager.unlock(...) при достижении уровня 10.
 */
@EventBusSubscriber(modid = LiveMineMod.MOD_ID)
public final class AchievementEventHandler {

    private AchievementEventHandler() {}

    // =========================================================================
    // Убийство NPC игроком
    // =========================================================================

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof LiveNPCEntity npc)) return;
        if (!(npc.level() instanceof ServerLevel level)) return;

        Entity source = event.getSource().getEntity();
        if (!(source instanceof ServerPlayer player)) return;

        try {
            AchievementManager.getInstance()
                .unlock(player, AchievementManager.Achievement.HONORABLE_BATTLE);
        } catch (Exception e) {
            LiveMineMod.LOGGER.error("AchievementEventHandler.onLivingDeath failed", e);
        }
    }

    // =========================================================================
    // Приветствие при входе
    // =========================================================================

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        try {
            int unlocked = AchievementManager.getInstance().getUnlockedCount(player.getUUID());
            int total = AchievementManager.getInstance().getTotalAchievements();
            if (unlocked > 0) {
                player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal(
                        "§6[LiveMine]§r §7Достижения: §f" + unlocked + "§7/§f" + total
                    )
                );
            }
        } catch (Exception ignored) {}
    }

    // =========================================================================
    // Публичные хуки для других подсистем
    // =========================================================================

    /** Вызывать при рождении первого мастера в деревне. */
    public static void triggerFirstMaster(LiveNPCEntity master) {
        if (!(master.level() instanceof ServerLevel level)) return;
        ServerPlayer nearest = PlayerUtils.findNearest(level, master.blockPosition(), 96.0);
        if (nearest != null) {
            AchievementManager.getInstance()
                .unlock(nearest, AchievementManager.Achievement.FIRST_MASTER);
        }
    }

    /** Вызывать при заключении альянса. */
    public static void triggerAlliance(ServerLevel level, int x, int z) {
        ServerPlayer nearest = PlayerUtils.findNearest(level, new net.minecraft.core.BlockPos(x, 64, z), 128.0);
        if (nearest != null) {
            AchievementManager.getInstance()
                .unlock(nearest, AchievementManager.Achievement.ALLIANCE_MADE);
        }
    }

    /** Вызывать при защите рейда без потерь. */
    public static void triggerRaidDefended(ServerLevel level, int x, int z) {
        ServerPlayer nearest = PlayerUtils.findNearest(level, new net.minecraft.core.BlockPos(x, 64, z), 96.0);
        if (nearest != null) {
            AchievementManager.getInstance()
                .unlock(nearest, AchievementManager.Achievement.RAID_DEFENDED);
        }
    }

    /** Вызывать при принятии странника в деревню. */
    public static void triggerStrangerAccepted(ServerLevel level, int x, int z) {
        ServerPlayer nearest = PlayerUtils.findNearest(level, new net.minecraft.core.BlockPos(x, 64, z), 64.0);
        if (nearest != null) {
            AchievementManager.getInstance()
                .unlock(nearest, AchievementManager.Achievement.STRANGER_ACCEPTED);
        }
    }

    /** Вызывать при доставке каравана. */
    public static void triggerCaravanDelivered(ServerLevel level, int x, int z) {
        ServerPlayer nearest = PlayerUtils.findNearest(level, new net.minecraft.core.BlockPos(x, 64, z), 96.0);
        if (nearest != null) {
            AchievementManager.getInstance()
                .unlock(nearest, AchievementManager.Achievement.CARAVAN_DELIVERED);
        }
    }

    /** Вызывать при постройке особняка (уровень 5). */
    public static void triggerMansionBuilt(ServerLevel level, int x, int z) {
        ServerPlayer nearest = PlayerUtils.findNearest(level, new net.minecraft.core.BlockPos(x, 64, z), 96.0);
        if (nearest != null) {
            AchievementManager.getInstance()
                .unlock(nearest, AchievementManager.Achievement.MANSION_BUILT);
        }
    }
}