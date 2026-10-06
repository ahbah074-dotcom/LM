package com.livemine.domain;

/**
 * Ограниченные потребности выживания.
 *
 * День = 24000 серверных тиков.
 * Значения 0–100: 100 — полностью удовлетворено, 0 — критически низко.
 */
public final class Needs {

    public static final int FOOD_CRITICAL = 20;
    public static final int FOOD_WORK_MIN = 50;

    public static final int REST_CRITICAL = 25;
    public static final int REST_WORK_MIN = 35;

    public static final int SAFETY_CRITICAL = 15;
    public static final int SAFETY_WORK_MIN = 40;

    private int food;
    private int rest;
    private int safety;

    public Needs(int food, int rest, int safety) {
        this.food = clamp(food);
        this.rest = clamp(rest);
        this.safety = clamp(safety);
    }

    public static Needs initial() {
        return new Needs(80, 80, 80);
    }

    public static Needs fresh() {
        return new Needs(100, 100, 100);
    }

    // =========================================================================
    // Геттеры
    // =========================================================================

    public int food() { return food; }
    public int rest() { return rest; }
    public int safety() { return safety; }

    // =========================================================================
    // Изменение
    // =========================================================================

    public void advanceDays(long days) {
        long d = Math.max(0, Math.min(100, days));
        food = clamp((int) (food - d));
        rest = clamp((int) (rest - d));
    }

    public void advanceDays(long days, boolean threatened) {
        advanceDays(days);
        if (threatened) {
            safety = clamp((int) (safety - days * 0.5));
        }
    }

    public void setFood(int v)   { food = clamp(v); }
    public void setRest(int v)   { rest = clamp(v); }
    public void setSafety(int v) { safety = clamp(v); }

    public void eat(int value)   { food = clamp(food + Math.max(0, value)); }
    public void sleep(int value) { rest = clamp(rest + Math.max(0, value)); }
    public void reassure()       { safety = clamp(safety + 10); }

    public void addFood(int delta)   { food = clamp(food + delta); }
    public void addRest(int delta)   { rest = clamp(rest + delta); }
    public void addSafety(int delta) { safety = clamp(safety + delta); }

    // =========================================================================
    // Проверки
    // =========================================================================

    public boolean isFoodCritical()   { return food < FOOD_CRITICAL; }
    public boolean isRestCritical()   { return rest < REST_CRITICAL; }
    public boolean isSafetyCritical() { return safety < SAFETY_CRITICAL; }

    public boolean canWork() {
        return food >= FOOD_WORK_MIN
            && rest >= REST_WORK_MIN
            && safety >= SAFETY_WORK_MIN;
    }

    public int worst() {
        return Math.min(food, Math.min(rest, safety));
    }

    public int average() {
        return (food + rest + safety) / 3;
    }

    // =========================================================================
    // Сериализация
    // =========================================================================

    public int[] toArray() {
        return new int[]{food, rest, safety};
    }

    public static Needs fromArray(int[] arr) {
        if (arr == null || arr.length < 3) return initial();
        return new Needs(arr[0], arr[1], arr[2]);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    @Override
    public String toString() {
        return "Needs{food=" + food + ", rest=" + rest + ", safety=" + safety + "}";
    }
}