package com.livemine.migration;

import com.livemine.LiveMineMod;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Менеджер миграций NBT-схемы.
 *
 * Правила:
 *   - При загрузке проверяется schema_version в global_data.
 *   - Если version < LATEST — применяются все шаги по порядку.
 *   - Если version > LATEST — предупреждение, миграция не выполняется.
 *   - Если version == LATEST — ничего не делать.
 *
 * Реестр шагов пополняется статически.
 */
public final class MigrationManager {

    private static MigrationManager INSTANCE;

    private final List<MigrationStep> steps = new ArrayList<>();

    private MigrationManager() {
        registerBuiltInSteps();
    }

    public static synchronized MigrationManager getInstance() {
        if (INSTANCE == null) INSTANCE = new MigrationManager();
        return INSTANCE;
    }

    // =========================================================================
    // Регистрация шагов
    // =========================================================================

    private void registerBuiltInSteps() {
        // Шаг 0 → 1: заполнить schema_version, если отсутствует.
        // Уже реализовано в LiveMineSavedData.loadFromTag через
        //     if (globalData.getInt("schema_version") == 0) { put(LATEST); }
        // Но добавим шаг формально, чтобы реестр не был пустым.

        register(new MigrationStep() {
            @Override
            public boolean migrate(CompoundTag tag, int fromVersion) {
                CompoundTag global = tag.getCompound("global_data");
                if (global.isEmpty()) global = new CompoundTag();
                if (global.getInt("schema_version") < SchemaVersion.LATEST) {
                    global.putInt("schema_version", SchemaVersion.LATEST);
                    tag.put("global_data", global);
                    return true;
                }
                return false;
            }

            @Override public int fromVersion() { return 0; }
            @Override public int toVersion() { return SchemaVersion.LATEST; }
        });

        // Отсортировать по возрастанию fromVersion.
        steps.sort(Comparator.comparingInt(MigrationStep::fromVersion));
    }

    public void register(MigrationStep step) {
        if (step != null) steps.add(step);
    }

    // =========================================================================
    // Миграция
    // =========================================================================

    /**
     * Применяет все нужные шаги миграции.
     *
     * @param tag NBT-тег верхнего уровня (содержит global_data.schema_version).
     * @return true, если данные были изменены.
     */
    public boolean migrate(CompoundTag tag) {
        if (tag == null) return false;

        CompoundTag global = tag.getCompound("global_data");
        int currentVersion = global.getInt("schema_version");

        // Свежий NBT — сразу выставить LATEST.
        if (currentVersion == 0) {
            global.putInt("schema_version", SchemaVersion.LATEST);
            tag.put("global_data", global);
            LiveMineMod.LOGGER.info(
                "Migration: fresh data, set schema_version = {}", SchemaVersion.LATEST);
            return true;
        }

        // Данные из будущего — не мигрируем.
        if (currentVersion > SchemaVersion.LATEST) {
            LiveMineMod.LOGGER.warn(
                "Migration: data schema {} > current {} — skipping",
                currentVersion, SchemaVersion.LATEST);
            return false;
        }

        // Уже актуальная версия.
        if (currentVersion == SchemaVersion.LATEST) {
            return false;
        }

        // Применяем все шаги последовательно.
        int version = currentVersion;
        int applied = 0;

        for (MigrationStep step : steps) {
            if (step.fromVersion() != version) continue;
            try {
                boolean changed = step.migrate(tag, version);
                if (changed) {
                    version = step.toVersion();
                    applied++;
                }
            } catch (Exception e) {
                throw new MigrationException(
                    "Migration step failed: " + version + " -> " + step.toVersion(),
                    version, step.toVersion(), e);
            }
            if (version >= SchemaVersion.LATEST) break;
        }

        if (version < SchemaVersion.LATEST) {
            throw new MigrationException(
                "No migration step from version " + version
                    + " to " + SchemaVersion.LATEST,
                version, SchemaVersion.LATEST);
        }

        // Финально фиксируем версию.
        global.putInt("schema_version", version);
        tag.put("global_data", global);

        LiveMineMod.LOGGER.info(
            "Migration: {} -> {} ({} steps applied)",
            currentVersion, version, applied);
        return true;
    }

    // =========================================================================
    // Диагностика
    // =========================================================================

    public int getStepCount() { return steps.size(); }

    public int getLatestVersion() { return SchemaVersion.LATEST; }

    public void clear() {
        steps.clear();
        registerBuiltInSteps();
    }
}