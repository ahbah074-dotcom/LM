package com.livemine.task;

/**
 * Тип игровой задачи NPC.
 *
 * Используется TaskStateMachine — конкретные работы, которые NPC выполняет
 * в мире (не путать с доменным Goal).
 */
public enum TaskType {

    // === Добыча ===
    MINE_ORE,           // добыть руду
    CHOP_WOOD,          // срубить дерево
    FARM_CROP,          // собрать урожай
    GATHER_RESOURCES,   // собрать ресурсы
    FISH,               // ловить рыбу
    HUNT,               // охотиться

    // === Производство ===
    CRAFT_ITEM,         // крафтить предмет
    COOK_FOOD,          // готовить еду
    BUILD_STRUCTURE,    // строить здание

    // === Социальное ===
    TRADE,              // торговать
    TALK,               // разговаривать
    TEACH,              // обучать

    // === Безопасность ===
    PATROL,             // патрулировать
    DEFEND,             // защищать

    // === Забота ===
    HEAL_NPC,           // лечить NPC
    FEED_NPC,           // кормить NPC

    // === Прочее ===
    EXPLORE,            // исследовать
    IDLE                // бездействие
}
