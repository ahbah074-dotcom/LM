package com.livemine.util;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Менеджер уведомлений игроку.
 *
 * 9 типов уведомлений (ТЗ 20.0 §18):
 *   - NPC_DEATH, NPC_BIRTH, NPC_MARRIAGE
 *   - VILLAGE_FOUNDED, VILLAGE_ABANDONED
 *   - HOLIDAY_START, HOLIDAY_END
 *   - DISEASE_OUTBREAK, MASTER_ACHIEVED
 *
 * Тик — раз в 100 тиков. За раз отправляется 1 уведомление.
 * Игрок может отключить уведомления через клиентский конфиг.
 */
public final class NotificationManager {

    private static NotificationManager INSTANCE;

    public enum Type {
        NPC_DEATH          ("§4[Смерть] "),
        NPC_BIRTH          ("§a[Рождение] "),
        NPC_MARRIAGE       ("§d[Свадьба] "),
        VILLAGE_FOUNDED    ("§6[Деревня] "),
        VILLAGE_ABANDONED  ("§8[Покинута] "),
        HOLIDAY_START      ("§e[Праздник] "),
        HOLIDAY_END        ("§7[Праздник] "),
        DISEASE_OUTBREAK   ("§c[Болезнь] "),
        MASTER_ACHIEVED    ("§b[Мастер] ");

        public final String prefix;
        Type(String prefix) { this.prefix = prefix; }
    }

    public static final class Notification {
        public final Type type;
        public final String text;
        public final long timestamp;

        public Notification(Type type, String text) {
            this.type = type;
            this.text = text;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private final Queue<Notification> pending = new ConcurrentLinkedQueue<>();
    private static final int MAX_PENDING = 64;

    private NotificationManager() {}

    public static synchronized NotificationManager getInstance() {
        if (INSTANCE == null) INSTANCE = new NotificationManager();
        return INSTANCE;
    }

    // =========================================================================
    // Отправка
    // =========================================================================

    public void send(Type type, String text) {
        if (type == null || text == null) return;
        if (pending.size() >= MAX_PENDING) pending.poll();
        pending.add(new Notification(type, text));
    }

    // =========================================================================
    // РўРёРє
    // =========================================================================

    public void tick(List<ServerPlayer> players) {
        if (pending.isEmpty() || players == null || players.isEmpty()) return;

        Notification n = pending.poll();
        if (n == null) return;

        Component message = Component.literal(n.type.prefix + "В§r" + n.text);
        for (ServerPlayer p : players) {
            if (p != null && p.isAlive()) {
                p.sendSystemMessage(message);
            }
        }
    }

    // =========================================================================
    // Утилиты
    // =========================================================================

    public int size() { return pending.size(); }

    public void clear() { pending.clear(); }
}
