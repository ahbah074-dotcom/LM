package com.livemine.ai;

import com.livemine.EconomyManager;
import com.livemine.LiveMineConfig;
import com.livemine.MarriageManager;
import com.livemine.VillageData;
import com.livemine.VillageManager;
import com.livemine.abandoned.AbandonedVillageManager;
import com.livemine.debt.DebtManager;
import com.livemine.diplomacy.AllianceManager;
import com.livemine.diplomacy.MailManager;
import com.livemine.diplomacy.WarManager;
import com.livemine.entity.LiveNPCEntity;
import com.livemine.library.LibraryManager;
import com.livemine.personality.NPCDreams;
import com.livemine.pet.PetManager;
import com.livemine.property.PropertyManager;
import com.livemine.quest.QuestManager;
import com.livemine.ranking.RankingManager;
import com.livemine.temple.TempleManager;
import com.livemine.territory.TerritoryManager;
import com.livemine.util.NotificationManager;
import com.livemine.voting.VotingManager;
import com.livemine.weather.WeatherHandler;
import com.livemine.world.CaravanManager;
import com.livemine.world.ScoutManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Главный серверный тик-хендлер.
 *
 * v2.7: PropertyManager.tickNpc в daily.
 */
public final class NPCSimulationTickHandler {

    private static int activityCounter = 0;
    private static int abandonedCounter = 0;
    private static int holidayCounter = 0;
    private static int rescanCounter = 0;
    private static int autosaveCounter = 0;
    private static int queueRebuildCounter = 0;
    private static int weatherCounter = 0;
    private static int notificationCounter = 0;
    private static int populationCounter = 0;
    private static int dailyCounter = 0;
    private static int votingCounter = 0;
    private static int diplomacyCounter = 0;
    private static int territoryCounter = 0;

    private static final int ACTIVITY_INTERVAL = 20;
    private static final int ABANDONED_INTERVAL = 200;
    private static final int HOLIDAY_INTERVAL = 24000;
    private static final int RESCAN_INTERVAL = 6000;
    private static final int AUTOSAVE_INTERVAL = 6000;
    private static final int QUEUE_REBUILD_INTERVAL = 100;
    private static final int WEATHER_INTERVAL = 200;
    private static final int NOTIFICATION_INTERVAL = 100;
    private static final int POPULATION_INTERVAL = 200;
    private static final int DAILY_INTERVAL = 24000;
    private static final int VOTING_INTERVAL = 100;
    private static final int DIPLOMACY_INTERVAL = 24000;
    private static final int TERRITORY_INTERVAL = 20;

    private NPCSimulationTickHandler() {}

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        AdaptiveTickRate.init();
        NPCRegistry.clear();
        com.livemine.LiveMineMod.LOGGER.info("LiveMine: server starting, NPC systems initialized");
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            com.livemine.PersistenceManager.get(level).saveAll();
        }
        com.livemine.LiveMineMod.LOGGER.info("LiveMine: server stopping, data saved");
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();

        AdaptiveTickRate.onServerTick(server);

        long currentTick = server.overworld().getGameTime();

        boolean doActivity = (++activityCounter >= ACTIVITY_INTERVAL);
        if (doActivity) activityCounter = 0;

        boolean doQueueRebuild = (++queueRebuildCounter >= QUEUE_REBUILD_INTERVAL);
        if (doQueueRebuild) queueRebuildCounter = 0;

        boolean doHoliday = (++holidayCounter >= HOLIDAY_INTERVAL);
        if (doHoliday) holidayCounter = 0;

        boolean doAbandoned = (++abandonedCounter >= ABANDONED_INTERVAL);
        if (doAbandoned) abandonedCounter = 0;

        boolean doRescan = (++rescanCounter >= RESCAN_INTERVAL);
        if (doRescan) rescanCounter = 0;

        boolean doAutosave = (++autosaveCounter >= AUTOSAVE_INTERVAL);
        if (doAutosave) autosaveCounter = 0;

        boolean doWeather = (++weatherCounter >= WEATHER_INTERVAL);
        if (doWeather) weatherCounter = 0;

        boolean doNotify = (++notificationCounter >= NOTIFICATION_INTERVAL);
        if (doNotify) notificationCounter = 0;

        boolean doPopulation = (++populationCounter >= POPULATION_INTERVAL);
        if (doPopulation) populationCounter = 0;

        boolean doDaily = (++dailyCounter >= DAILY_INTERVAL);
        if (doDaily) dailyCounter = 0;

        boolean doVoting = (++votingCounter >= VOTING_INTERVAL);
        if (doVoting) votingCounter = 0;

        boolean doDiplomacy = (++diplomacyCounter >= DIPLOMACY_INTERVAL);
        if (doDiplomacy) diplomacyCounter = 0;

        boolean doTerritory = (++territoryCounter >= TERRITORY_INTERVAL);
        if (doTerritory) territoryCounter = 0;

        if (doQueueRebuild) {
            TickDistributor.rebuildQueues();
        }

        int simBatch = AdaptiveTickRate.getBatchPerTick();

        for (ServerLevel level : server.getAllLevels()) {
            if (doActivity) NPCActivityManager.update(level, currentTick);
            if (simBatch > 0) SimulationEngine.simulateBatch(level, simBatch);

            TickDistributor.tickActiveNPCs(level);

            if (doTerritory) {
                try {
                    TerritoryManager.getInstance().tick(level);
                } catch (Exception e) {
                    com.livemine.LiveMineMod.LOGGER.error("TerritoryManager.tick failed", e);
                }
            }

            if (doVoting) {
                try { VotingManager.getInstance().tick(); }
                catch (Exception e) { com.livemine.LiveMineMod.LOGGER.error("VotingManager", e); }
            }

            if (doHoliday && LiveMineConfig.enableHolidays()) {
                com.livemine.HolidayManager.getInstance().tick(level);
            }

            if (doWeather && LiveMineConfig.enableWeatherEffects()) {
                try { WeatherHandler.getInstance().tick(level); }
                catch (Exception e) { com.livemine.LiveMineMod.LOGGER.error("Weather", e); }
            }

            if (doNotify) {
                try {
                    List<ServerPlayer> players = new ArrayList<>(level.players());
                    if (!players.isEmpty()) NotificationManager.getInstance().tick(players);
                } catch (Exception e) { com.livemine.LiveMineMod.LOGGER.error("Notify", e); }
            }

            if (doPopulation) updateVillagePopulations(level);
            if (doDaily) processDailyEvents(level);
            if (doDiplomacy) processDiplomacyTick(level);

            if (doAbandoned && LiveMineConfig.enableAbandonedVillages()) {
                AbandonedVillageManager.checkAbandonedVillages(level);
            }

            if (doRescan) ResourceCache.rescanDirtyChunks(level, currentTick);

            if (doAutosave) {
                com.livemine.PersistenceManager.get(level).flush();
            }
        }
    }

    private static void processDailyEvents(ServerLevel level) {
        long day = level.getDayTime() / 24000L;

        try { EconomyManager.getInstance().dailyUpdate(); }
        catch (Exception e) { com.livemine.LiveMineMod.LOGGER.error("Economy", e); }

        try { DebtManager.getInstance().tick(day); }
        catch (Exception e) { com.livemine.LiveMineMod.LOGGER.error("Debt", e); }

        try { QuestManager.getInstance().tick(day); }
        catch (Exception e) { com.livemine.LiveMineMod.LOGGER.error("Quest", e); }

        try { RankingManager.getInstance().tick(level); }
        catch (Exception e) { com.livemine.LiveMineMod.LOGGER.error("Ranking", e); }

        try {
            for (var record : VillageManager.getInstance().getAllVillages()) {
                var temple = TempleManager.getInstance().getTemple(record.id());
                if (temple != null) {
                    TempleManager.getInstance()
                        .performRitual(record.id(),
                            TempleManager.RitualType.HARVEST_BLESSING, level);
                }
            }
        } catch (Exception e) {
            com.livemine.LiveMineMod.LOGGER.error("TempleManager daily ritual failed", e);
        }

        try {
            for (Mob mob : NPCRegistry.getAll()) {
                if (mob.level() != level) continue;
                if (!(mob instanceof LiveNPCEntity npc)) continue;
                if (!npc.isAlive() || !npc.isAdult()) continue;

                if (LibraryManager.getInstance().isScribe(npc)) {
                    LibraryManager.getInstance().tryWriteBook(npc, level);
                }
            }
        } catch (Exception e) {
            com.livemine.LiveMineMod.LOGGER.error("LibraryManager tryWriteBook failed", e);
        }

        try {
            for (Mob mob : NPCRegistry.getAll()) {
                if (mob.level() != level) continue;
                if (!(mob instanceof LiveNPCEntity npc)) continue;
                if (!npc.isAlive()) continue;

                if (npc.isAdult()) {
                    try {
                        MarriageManager.getInstance().tick(npc, day);
                    } catch (Exception e) {
                        com.livemine.LiveMineMod.LOGGER.error("Marriage failed for {}",
                            npc.getUUID(), e);
                    }

                    try {
                        PetManager.getInstance().tickNpc(npc, level);
                        if (level.random.nextFloat() < 0.20f) {
                            PetManager.getInstance().tryTameNearby(npc, level);
                        }
                    } catch (Exception e) {
                        com.livemine.LiveMineMod.LOGGER.error("PetManager failed for {}",
                            npc.getUUID(), e);
                    }

                    // v2.7: Собственность.
                    try {
                        PropertyManager.getInstance().tickNpc(npc, level, day);
                    } catch (Exception e) {
                        com.livemine.LiveMineMod.LOGGER.error("PropertyManager failed for {}",
                            npc.getUUID(), e);
                    }
                }

                try {
                    NPCDreams.DreamType dream = NPCDreams.roll(level.random);
                    NPCDreams.applyDream(npc.getEmotions(), npc.getTraits(), dream, level.random);

                    if (LiveMineConfig.brainDebug() && dream != NPCDreams.DreamType.ORDINARY) {
                        BrainDebugger.log(npc, "DREAM", NPCDreams.describe(dream));
                    }
                } catch (Exception e) {
                    com.livemine.LiveMineMod.LOGGER.error("Dream failed for {}", npc.getUUID(), e);
                }
            }
        } catch (Exception e) {
            com.livemine.LiveMineMod.LOGGER.error("Daily NPC loop failed", e);
        }
    }

    private static void processDiplomacyTick(ServerLevel level) {
        long day = level.getDayTime() / 24000L;

        try { ScoutManager.getInstance().tick(level); }
        catch (Exception e) { com.livemine.LiveMineMod.LOGGER.error("Scout", e); }

        try { AllianceManager.getInstance().tick(level); }
        catch (Exception e) { com.livemine.LiveMineMod.LOGGER.error("Alliance", e); }

        try { WarManager.getInstance().tick(level); }
        catch (Exception e) { com.livemine.LiveMineMod.LOGGER.error("War", e); }

        try { MailManager.getInstance().tick(day); }
        catch (Exception e) { com.livemine.LiveMineMod.LOGGER.error("Mail", e); }

        try { CaravanManager.getInstance().tick(day); }
        catch (Exception e) { com.livemine.LiveMineMod.LOGGER.error("Caravan", e); }
    }

    private static void updateVillagePopulations(ServerLevel level) {
        try {
            for (var record : VillageManager.getInstance().getAllVillages()) {
                VillageData vd = new VillageData(record.rawId(), level);
                if (!vd.exists()) continue;
                int count = vd.refreshPopulation();
                record.setPopulation(count);
            }
        } catch (Exception e) {
            com.livemine.LiveMineMod.LOGGER.error("updateVillagePopulations failed", e);
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            if (event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk) {
                ResourceCache.scanChunk(level, chunk, level.getGameTime());
            }
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            if (event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk) {
                ResourceCache.onChunkUnloaded(level, chunk);
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ResourceCache.onBlockChanged(level, event.getPos());
            PathCache.invalidateAround(event.getPos());
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ResourceCache.onBlockChanged(level, event.getPos());
        }
    }
}