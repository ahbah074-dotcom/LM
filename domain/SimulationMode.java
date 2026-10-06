package com.livemine.domain;

/**
 * Режим симуляции агента / деревни.
 *
 * ACTIVE   — полная симуляция, все тики, Minecraft AI работает
 * VIRTUAL  — упрощённая симуляция без pathfinding
 * DORMANT  — заморозка, только минимальные вычисления (старение)
 */
public enum SimulationMode {

    ACTIVE,
    VIRTUAL,
    DORMANT;

    public boolean isActive() {
        return this == ACTIVE;
    }

    public boolean isVirtual() {
        return this == VIRTUAL;
    }

    public boolean isDormant() {
        return this == DORMANT;
    }

    public boolean usesVanillaAI() {
        return this == ACTIVE;
    }

    public boolean ticksEveryTick() {
        return this == ACTIVE;
    }

    public SimulationMode next() {
        return switch (this) {
            case ACTIVE  -> VIRTUAL;
            case VIRTUAL -> DORMANT;
            case DORMANT -> ACTIVE;
        };
    }

    public SimulationMode previous() {
        return switch (this) {
            case ACTIVE  -> DORMANT;
            case VIRTUAL -> ACTIVE;
            case DORMANT -> VIRTUAL;
        };
    }

    public int baseTickInterval() {
        return switch (this) {
            case ACTIVE  -> 1;
            case VIRTUAL -> 200;
            case DORMANT -> 2000;
        };
    }

    public double timeScale() {
        return switch (this) {
            case ACTIVE  -> 1.0;
            case VIRTUAL -> 1.0;
            case DORMANT -> 1.0;
        };
    }

    public String ruName() {
        return switch (this) {
            case ACTIVE  -> "Активен";
            case VIRTUAL -> "Виртуален";
            case DORMANT -> "Спящий";
        };
    }

    @Override
    public String toString() {
        return name() + " (" + ruName() + ")";
    }
}