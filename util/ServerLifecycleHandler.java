package com.livemine.util;

import com.livemine.LiveMineMod;
import com.livemine.PersistenceManager;
import com.livemine.VillageManager;
import com.livemine.ai.NPCRegistry;
import com.livemine.world.VillageSpawner;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * Обработчик жизненного цикла сервера.
 *
 * v2.3: loadAll() вызывается один раз на Overworld (не на каждом уровне).
 */
public final class ServerLifecycleHandler {

    private ServerLifecycleHandler() {}

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        LiveMineMod.LOGGER.info("LiveMine: server starting, initializing world data");
        NPCRegistry.clear();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();

        // Загружаем существующие деревни (Overworld — основной).
        VillageManager.getInstance().loadFromSavedData(overworld);

        // Загружаем кэши и дипломатию.
        PersistenceManager.get(overworld).loadAll();

        // Автогенерация стартовых деревень.
        if (VillageManager.getInstance().getVillageCount() == 0) {
            try {
                VillageSpawner.spawnInitialVillages(overworld);
            } catch (Exception e) {
                LiveMineMod.LOGGER.error("VillageSpawner failed", e);
            }
        }

        LiveMineMod.LOGGER.info("LiveMine: server started, {} villages loaded",
            VillageManager.getInstance().getVillageCount());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        LiveMineMod.LOGGER.info("LiveMine: server stopping, saving all data");

        for (ServerLevel level : server.getAllLevels()) {
            PersistenceManager.get(level).saveAll();
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        NPCRegistry.clear();
        LiveMineMod.LOGGER.info("LiveMine: server stopped, caches cleared");
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        // Заглушка для будущей синхронизации
    }

    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        // Заглушка
    }
}