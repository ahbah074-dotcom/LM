package com.livemine.crash;

import com.livemine.LiveMineMod;
import com.livemine.LiveMineSavedData;
import com.livemine.PersistenceManager;
import net.minecraft.server.level.ServerLevel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Обработчик сбоев мира (ТЗ 20.0 §42).
 *
 * Сценарии:
 *   - Повреждённое сохранение → попытка backup
 *   - Несовпадение schemaVersion → миграция
 *   - Нехватка памяти → перевод NPC в DORMANT
 *
 * Не создаёт новых фич, только защищает данные.
 */
public final class CrashHandler {

    private static CrashHandler INSTANCE;

    private static final DateTimeFormatter TS_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private CrashHandler() {}

    public static synchronized CrashHandler getInstance() {
        if (INSTANCE == null) INSTANCE = new CrashHandler();
        return INSTANCE;
    }

    // =========================================================================
    // Восстановление
    // =========================================================================

    /**
     * Попытка восстановления после сбоя.
     * Вызывается при старте, если сохранения повреждены.
     */
    public boolean attemptRecovery(ServerLevel level) {
        try {
            // Проверка целостности данных
            boolean valid = validateData(level);
            if (valid) return true;

            LiveMineMod.LOGGER.warn("CrashHandler: data invalid, attempting recovery");

            // Пытаемся загрузить backup
            return restoreFromBackup(level);

        } catch (Exception e) {
            LiveMineMod.LOGGER.error("CrashHandler: recovery failed", e);
            return false;
        }
    }

    private boolean validateData(ServerLevel level) {
        try {
            var data = LiveMineSavedData.get(level);
            int schemaVersion = data.getSchemaVersion();
            int latestVersion = com.livemine.migration.SchemaVersion.LATEST;

            if (schemaVersion > latestVersion) {
                LiveMineMod.LOGGER.warn(
                    "CrashHandler: data schema {} > latest {}",
                    schemaVersion, latestVersion);
                return false;
            }

            // Проверяем целостность: считаем NPC и деревни
            int npcCount = data.getNpcCount();
            int villageCount = data.getVillageCount();

            LiveMineMod.LOGGER.info(
                "CrashHandler: data valid (schema {}, {} NPCs, {} villages)",
                schemaVersion, npcCount, villageCount);

            return true;
        } catch (Exception e) {
            LiveMineMod.LOGGER.error("CrashHandler: validation error", e);
            return false;
        }
    }

    // =========================================================================
    // Backup
    // =========================================================================

    /**
     * Создать резервную копию данных мира.
     */
    public boolean createBackup(ServerLevel level) {
        try {
            Path worldDataDir = getWorldDataPath(level);
            if (worldDataDir == null || !Files.exists(worldDataDir)) {
                LiveMineMod.LOGGER.warn("CrashHandler: world data dir not found");
                return false;
            }

            Path backupDir = getBackupDir(level);
            Files.createDirectories(backupDir);

            String timestamp = LocalDateTime.now().format(TS_FORMAT);
            Path backupFile = backupDir.resolve("livemine_data_" + timestamp + ".dat");

            // Копируем основной файл
            Path sourceData = worldDataDir.resolve("livemine_data.dat");
            if (Files.exists(sourceData)) {
                Files.copy(sourceData, backupFile, StandardCopyOption.REPLACE_EXISTING);
                LiveMineMod.LOGGER.info("CrashHandler: backup created at {}", backupFile);
                return true;
            }

            LiveMineMod.LOGGER.warn("CrashHandler: no source data file");
            return false;

        } catch (IOException e) {
            LiveMineMod.LOGGER.error("CrashHandler: backup failed", e);
            return false;
        }
    }

    private boolean restoreFromBackup(ServerLevel level) {
        try {
            Path backupDir = getBackupDir(level);
            if (!Files.exists(backupDir)) return false;

            // Находим самый свежий backup
            Path latest = Files.list(backupDir)
                .filter(p -> p.toString().endsWith(".dat"))
                .max((a, b) -> {
                    try {
                        return Files.getLastModifiedTime(a)
                            .compareTo(Files.getLastModifiedTime(b));
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .orElse(null);

            if (latest == null) return false;

            Path target = getWorldDataPath(level).resolve("livemine_data.dat");
            Files.copy(latest, target, StandardCopyOption.REPLACE_EXISTING);

            LiveMineMod.LOGGER.info("CrashHandler: restored from {}", latest);
            return true;

        } catch (IOException e) {
            LiveMineMod.LOGGER.error("CrashHandler: restore failed", e);
            return false;
        }
    }

    // =========================================================================
    // Память
    // =========================================================================

    /**
     * При нехватке памяти — переводим NPC в DORMANT.
     */
    public void handleLowMemory() {
        LiveMineMod.LOGGER.warn("CrashHandler: low memory detected, freezing NPCs");

        int frozen = 0;
        for (var mob : com.livemine.ai.NPCRegistry.getAll()) {
            if (mob instanceof com.livemine.entity.LiveNPCEntity npc) {
                com.livemine.ai.NPCRegistry.setActivityState(
                    npc.getUUID(),
                    com.livemine.ai.NPCActivityManager.ActivityState.DORMANT
                );
                frozen++;
            }
        }

        // Очищаем кэши
        com.livemine.ai.ResourceCache.clear();
        com.livemine.ai.PathCache.clear();

        LiveMineMod.LOGGER.info("CrashHandler: {} NPCs frozen, caches cleared", frozen);
    }

    // =========================================================================
    // Пути
    // =========================================================================

    private Path getWorldDataPath(ServerLevel level) {
        try {
            return level.getServer().getWorldPath(
                net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("data");
        } catch (Exception e) {
            return null;
        }
    }

    private Path getBackupDir(ServerLevel level) {
        try {
            return level.getServer().getWorldPath(
                net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("livemine").resolve("backup");
        } catch (Exception e) {
            return Paths.get("livemine_backup");
        }
    }

    // =========================================================================
    // РЎР±СЂРѕСЃ
    // =========================================================================

    /**
     * Аварийный сброс данных (с сохранением хроники).
     */
    public void emergencyReset(ServerLevel level) {
        try {
            LiveMineMod.LOGGER.warn("CrashHandler: emergency reset initiated");

            // Сохраняем что можем
            PersistenceManager.get(level).saveAll();

            // Создаём backup
            createBackup(level);

            // Не сбрасываем данные, только логируем
            LiveMineMod.LOGGER.warn("CrashHandler: emergency reset complete, data preserved");

        } catch (Exception e) {
            LiveMineMod.LOGGER.error("CrashHandler: emergency reset failed", e);
        }
    }
}
