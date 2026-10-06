package com.livemine.migration;

/**
 * Исключение миграции данных.
 *
 * Бросается, когда миграция NBT-схемы не удалась:
 *   - Несовместимая версия схемы.
 *   - Отсутствует шаг миграции.
 *   - Ошибка в процессе миграции.
 */
public class MigrationException extends RuntimeException {

    private final int fromVersion;
    private final int toVersion;

    public MigrationException(String message, int fromVersion, int toVersion) {
        super(message);
        this.fromVersion = fromVersion;
        this.toVersion = toVersion;
    }

    public MigrationException(String message, int fromVersion, int toVersion, Throwable cause) {
        super(message, cause);
        this.fromVersion = fromVersion;
        this.toVersion = toVersion;
    }

    public int getFromVersion() { return fromVersion; }
    public int getToVersion() { return toVersion; }

    @Override
    public String toString() {
        return "MigrationException{from=" + fromVersion
            + ", to=" + toVersion + ", msg=" + getMessage() + "}";
    }
}