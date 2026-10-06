package com.livemine.api;

import com.livemine.LiveMineMod;
import com.livemine.entity.LiveNPCEntity;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Публичный API для сторонних модов (ТЗ 20.0 §41).
 *
 * Сторонний мод может:
 *   - Подписаться на события деревни (рождение, смерть, праздник, война)
 *   - Зарегистрировать свой обработчик события
 *   - Читать данные NPC/деревни
 *
 * Документация — на английском.
 *
 * Пример:
 *   APIManager.getInstance().subscribe(VillageEvent.NPC_BORN, event -> {
 *       LiveMineMod.LOGGER.info("NPC born: {}", event.data.get("name"));
 *   });
 */
public final class APIManager {

    private static APIManager INSTANCE;

    public enum VillageEvent {
        VILLAGE_FOUNDED,
        VILLAGE_ABANDONED,
        NPC_BORN,
        NPC_DIED,
        NPC_MARRIED,
        HOLIDAY_START,
        RAID_START,
        ALLIANCE_FORMED,
        WAR_DECLARED,
        CARAVAN_DEPARTED,
        CARAVAN_ARRIVED
    }

    public static final class EventData {
        public final VillageEvent type;
        public final long day;
        public final Map<String, Object> data = new HashMap<>();

        public EventData(VillageEvent type, long day) {
            this.type = type;
            this.day = day;
        }

        public EventData set(String key, Object value) {
            data.put(key, value);
            return this;
        }
    }

    private final Map<VillageEvent, List<Consumer<EventData>>> subscribers = new HashMap<>();

    private APIManager() {}

    public static synchronized APIManager getInstance() {
        if (INSTANCE == null) INSTANCE = new APIManager();
        return INSTANCE;
    }

    // =========================================================================
    // РџРѕРґРїРёСЃРєР°
    // =========================================================================

    public void subscribe(VillageEvent type, Consumer<EventData> handler) {
        if (type == null || handler == null) return;
        subscribers.computeIfAbsent(type, k -> new ArrayList<>()).add(handler);
        LiveMineMod.LOGGER.debug("API: subscribed to {}", type);
    }

    public void unsubscribe(VillageEvent type, Consumer<EventData> handler) {
        List<Consumer<EventData>> list = subscribers.get(type);
        if (list != null) list.remove(handler);
    }

    // =========================================================================
    // Публикация (внутреннее API)
    // =========================================================================

    public void fire(VillageEvent type, EventData event) {
        if (type == null || event == null) return;

        List<Consumer<EventData>> list = subscribers.get(type);
        if (list == null || list.isEmpty()) return;

        for (Consumer<EventData> handler : list) {
            try {
                handler.accept(event);
            } catch (Exception e) {
                LiveMineMod.LOGGER.error("API subscriber failed for {}", type, e);
            }
        }
    }

    public void fire(VillageEvent type, long day, Map<String, Object> data) {
        EventData event = new EventData(type, day);
        if (data != null) event.data.putAll(data);
        fire(type, event);
    }

    // =========================================================================
    // Чтение данных (публичный API)
    // =========================================================================

    public int getNpcCount() {
        return com.livemine.ai.NPCRegistry.getTotalCount();
    }

    public int getVillageCount() {
        return com.livemine.VillageManager.getInstance().getVillageCount();
    }

    public LiveNPCEntity getNpc(UUID uuid) {
        return com.livemine.ai.NPCRegistry.getNPC(uuid);
    }

    public List<UUID> getNpcsInVillage(UUID villageId, ServerLevel level) {
        var vd = new com.livemine.VillageData("village_" + villageId, level);
        return vd.exists() ? vd.getResidentNpcIds() : new ArrayList<>();
    }

    public boolean isLoaded() {
        return true;
    }

    public String getModVersion() {
        return "1.0.0";
    }

    public String getApiVersion() {
        return "1.0";
    }

    // =========================================================================
    // Очистка
    // =========================================================================

    public void clear() {
        subscribers.clear();
    }

    public int getSubscriberCount() {
        int total = 0;
        for (List<Consumer<EventData>> list : subscribers.values()) {
            total += list.size();
        }
        return total;
    }
}
