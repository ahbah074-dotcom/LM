package com.livemine.diplomacy;

import com.livemine.LiveMineMod;
import com.livemine.VillageManager;
import com.livemine.domain.VillageRecord;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Менеджер почты между деревнями (ТЗ 20.0 §52).
 *
 * Система передачи писем через гонцов.
 * Гонец — NPC с Разведка ≥ 3.
 * Время доставки = расстояние / 1000 блоков в игровой день.
 *
 * Письмо — физический предмет (книга).
 * При потере гонца в пути — письмо утеряно.
 */
public final class MailManager {

    private static MailManager INSTANCE;

    public static final int COURIER_SPEED_PER_DAY = 1000;

    public enum LetterState {
        IN_TRANSIT,
        DELIVERED,
        LOST
    }

    public static final class Letter {
        public UUID letterId;
        public UUID senderVillage;
        public UUID recipientVillage;
        public UUID courierId;
        public String subject;
        public String body;
        public long sentDay;
        public long estimatedArrivalDay;
        public LetterState state;

        public Letter(UUID sender, UUID recipient, UUID courier,
                       String subject, String body, long sentDay, double distance) {
            this.letterId = UUID.randomUUID();
            this.senderVillage = sender;
            this.recipientVillage = recipient;
            this.courierId = courier;
            this.subject = subject != null ? subject : "Без темы";
            this.body = body != null ? body : "";
            this.sentDay = sentDay;
            this.estimatedArrivalDay = sentDay
                + (long) Math.ceil(distance / COURIER_SPEED_PER_DAY);
            this.state = LetterState.IN_TRANSIT;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", letterId);
            tag.putUUID("sender", senderVillage);
            tag.putUUID("recipient", recipientVillage);
            if (courierId != null) tag.putUUID("courier", courierId);
            tag.putString("subject", subject);
            tag.putString("body", body);
            tag.putLong("sent", sentDay);
            tag.putLong("arrival", estimatedArrivalDay);
            tag.putString("state", state.name());
            return tag;
        }

        public static Letter load(CompoundTag tag) {
            UUID sender = tag.getUUID("sender");
            UUID recipient = tag.getUUID("recipient");
            UUID courier = tag.hasUUID("courier") ? tag.getUUID("courier") : null;

            Letter l = new Letter(
                sender, recipient, courier,
                tag.getString("subject"),
                tag.getString("body"),
                tag.getLong("sent"),
                0
            );
            l.letterId = tag.getUUID("id");
            l.estimatedArrivalDay = tag.getLong("arrival");
            try {
                l.state = LetterState.valueOf(tag.getString("state"));
            } catch (IllegalArgumentException e) {
                l.state = LetterState.IN_TRANSIT;
            }
            return l;
        }
    }

    private final Map<UUID, Letter> letters = new HashMap<>();

    private MailManager() {}

    public static synchronized MailManager getInstance() {
        if (INSTANCE == null) INSTANCE = new MailManager();
        return INSTANCE;
    }

    // =========================================================================
    // Отправка
    // =========================================================================

    public Letter sendLetter(UUID senderVillage, UUID recipientVillage, UUID courierId,
                              String subject, String body, long currentDay) {
        VillageRecord sender = VillageManager.getInstance().getVillage(senderVillage);
        VillageRecord recipient = VillageManager.getInstance().getVillage(recipientVillage);
        if (sender == null || recipient == null) return null;

        double distance = Math.sqrt(
            Math.pow(sender.centerX() - recipient.centerX(), 2)
            + Math.pow(sender.centerZ() - recipient.centerZ(), 2));

        Letter l = new Letter(senderVillage, recipientVillage, courierId,
            subject, body, currentDay, distance);
        letters.put(l.letterId, l);

        LiveMineMod.LOGGER.info("Letter sent: {} в†' {} (ETA day {})",
            sender.name(), recipient.name(), l.estimatedArrivalDay);
        return l;
    }

    // =========================================================================
    // РўРёРє
    // =========================================================================

    public void tick(long currentDay) {
        for (Letter l : letters.values()) {
            if (l.state != LetterState.IN_TRANSIT) continue;

            // Проверяем, жив ли гонец
            if (l.courierId != null) {
                var courier = com.livemine.ai.NPCRegistry.getNPC(l.courierId);
                if (courier == null || !courier.isAlive()) {
                    l.state = LetterState.LOST;
                    LiveMineMod.LOGGER.warn("Letter {} lost — courier died", l.letterId);
                    continue;
                }
            }

            // Проверяем, дошёл ли
            if (currentDay >= l.estimatedArrivalDay) {
                l.state = LetterState.DELIVERED;
                LiveMineMod.LOGGER.info("Letter {} delivered to {}",
                    l.letterId, l.recipientVillage);
            }
        }
    }

    // =========================================================================
    // Запросы
    // =========================================================================

    public List<Letter> getLettersFor(UUID villageId) {
        List<Letter> result = new ArrayList<>();
        for (Letter l : letters.values()) {
            if (l.recipientVillage.equals(villageId) || l.senderVillage.equals(villageId)) {
                result.add(l);
            }
        }
        return result;
    }

    public List<Letter> getInTransit() {
        List<Letter> result = new ArrayList<>();
        for (Letter l : letters.values()) {
            if (l.state == LetterState.IN_TRANSIT) result.add(l);
        }
        return result;
    }

    public Letter getLetter(UUID letterId) {
        return letters.get(letterId);
    }

    public int size() { return letters.size(); }

    // =========================================================================
    // NBT
    // =========================================================================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Letter l : letters.values()) list.add(l.save());
        tag.put("letters", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        letters.clear();
        ListTag list = tag.getList("letters", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Letter l = Letter.load(list.getCompound(i));
            letters.put(l.letterId, l);
        }
    }

    public void clear() { letters.clear(); }
}
