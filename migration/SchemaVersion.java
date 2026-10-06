package com.livemine.migration;

/**
 * Версия схемы сохранённых данных.
 *
 * Используется в LiveMineSavedData для контроля совместимости NBT.
 * При breaking-изменениях структуры — увеличивать LATEST и добавлять
 * шаг миграции в MigrationManager.
 */
public final class SchemaVersion {

    /** Текущая версия схемы. */
    public static final int LATEST = 1;

    /** Первая публичная версия. */
    public static final int INITIAL = 1;

    private SchemaVersion() {}
}