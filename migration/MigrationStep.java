package com.livemine.migration;

import net.minecraft.nbt.CompoundTag;

/**
 * Один шаг миграции.
 *
 * Каждый шаг повышает схему на 1 (или более) уровень.
 * Реализации — функциональный интерфейс.
 */
@FunctionalInterface
public interface MigrationStep {

    /**
     * Мигрирует NBT со версии fromVersion на toVersion.
     * Возвращает true, если миграция применилась.
     *
     * @param tag         NBT-тег верхнего уровня (livemine_data)
     * @param fromVersion текущая версия
     * @return true, если данные были изменены
     */
    boolean migrate(CompoundTag tag, int fromVersion);

    /**
     * Версия, С КОТОРОЙ работает шаг.
     */
    default int fromVersion() { return -1; }

    /**
     * Версия, НА КОТОРУЮ переводит шаг.
     */
    default int toVersion() { return -1; }
}