package com.livemine.quest;

import com.livemine.LiveMineMod;
import com.livemine.ReputationManager;
import com.livemine.VillageData;
import com.livemine.entity.LiveNPCEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Менеджер квестов от NPC игроку.
 *
 * v2.0: методы для работы с GUI (offerForPlayer, getPendingOfferForPlayer,
 * acceptByPlayer, declineByPlayer).
 */
public final class QuestManager {

    private static QuestManager INSTANCE;

    public enum QuestType {
        BRING_RESOURCE  ("Принести ресурсы"),
        DEFEND_RAID     ("Отбить рейд"),
        FIND_NPC        ("Найти NPC"),
        DELIVER_LETTER  ("Доставить письмо"),
        FETCH_NETHER    ("Принести из Незера"),
        HEAL_NPC        ("Вылечить NPC"),
        BUILD_HOUSE     ("Построить дом"),
        BRING_BOOK      ("Принести книгу"),
        JOIN_EXPEDITION ("Помочь в экспедиции"),
        PROTECT_CARAVAN ("Защитить караван");

        public final String ruName;
        QuestType(String ru) { this.ruName = ru; }
    }

    public enum QuestState {
        OFFERED, ACCEPTED, IN_PROGRESS, COMPLETED, FAILED, ABANDONED
    }

    public static final class Quest {
        public UUID questId;
        public UUID giverNpcId;
        public UUID villageId;
        public UUID playerId;
        public QuestType type;
        public QuestState state;
        public String description;
        public String targetResource;
        public int targetAmount;
        public int currentAmount;
        public long offeredDay;
        public long deadlineDay;
        public int reputationReward;
        public String itemReward;

        public Quest(UUID giver, UUID village, UUID player, QuestType type,
                     String description, long day) {
            this.questId = UUID.randomUUID();
            this.giverNpcId = giver;
            this.villageId = village;
            this.playerId = player;
            this.type = type;
            this.state = QuestState.OFFERED;
            this.description = description != null ? description : "";
            this.offeredDay = day;
            this.deadlineDay = day + 7;
            this.reputationReward = 10;
            this.itemReward = "";
        }

        public boolean isExpired(long currentDay) {
            return currentDay > deadlineDay;
        }

        public void progress(int amount) {
            currentAmount += amount;
            state = QuestState.IN_PROGRESS;
            if (targetAmount > 0 && currentAmount >= targetAmount) {
                currentAmount = targetAmount;
                state = QuestState.COMPLETED;
            }
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", questId);
            if (giverNpcId != null) tag.putUUID("giver", giverNpcId);
            if (villageId != null) tag.putUUID("village", villageId);
            if (playerId != null) tag.putUUID("player", playerId);
            tag.putString("type", type.name());
            tag.putString("state", state.name());
            tag.putString("desc", description);
            tag.putString("resource", targetResource != null ? targetResource : "");
            tag.putInt("targetAmount", targetAmount);
            tag.putInt("currentAmount", currentAmount);
            tag.putLong("offered", offeredDay);
            tag.putLong("deadline", deadlineDay);
            tag.putInt("repReward", reputationReward);
            tag.putString("itemReward", itemReward != null ? itemReward : "");
            return tag;
        }

        public static Quest load(CompoundTag tag) {
            Quest q = new Quest(
                tag.hasUUID("giver") ? tag.getUUID("giver") : null,
                tag.hasUUID("village") ? tag.getUUID("village") : null,
                tag.hasUUID("player") ? tag.getUUID("player") : null,
                QuestType.valueOf(tag.getString("type")),
                tag.getString("desc"),
                tag.getLong("offered")
            );
            q.questId = tag.getUUID("id");
            try {
                q.state = QuestState.valueOf(tag.getString("state"));
            } catch (IllegalArgumentException e) {
                q.state = QuestState.OFFERED;
            }
            q.targetResource = tag.getString("resource");
            q.targetAmount = tag.getInt("targetAmount");
            q.currentAmount = tag.getInt("currentAmount");
            q.deadlineDay = tag.getLong("deadline");
            q.reputationReward = tag.getInt("repReward");
            q.itemReward = tag.getString("itemReward");
            return q;
        }
    }

    private final Map<UUID, Quest> quests = new HashMap<>();
    private final Map<UUID, List<UUID>> questsByPlayer = new HashMap<>();

    /** v2.0: player → questId, чтобы не спамить предложениями. */
    private final Map<UUID, UUID> pendingOfferByPlayer = new HashMap<>();

    private QuestManager() {}

    public static synchronized QuestManager getInstance() {
        if (INSTANCE == null) INSTANCE = new QuestManager();
        return INSTANCE;
    }

    // =========================================================================
    // v2.0: Методы для GUI
    // =========================================================================

    /**
     * Возвращает текущий предложенный квест для игрока (или null).
     * Если у игрока уже есть активный/принятый квест — не предлагает новый.
     */
    public Quest getPendingOfferForPlayer(UUID playerId, UUID giverNpcId, ServerLevel level) {
        // Проверяем активные квесты игрока.
        List<UUID> active = questsByPlayer.get(playerId);
        if (active != null) {
            for (UUID qid : active) {
                Quest q = quests.get(qid);
                if (q != null && (q.state == QuestState.ACCEPTED || q.state == QuestState.IN_PROGRESS)) {
                    return null;
                }
            }
        }

        // Проверяем уже предложенный.
        UUID pendingId = pendingOfferByPlayer.get(playerId);
        if (pendingId != null) {
            Quest p = quests.get(pendingId);
            if (p != null && p.state == QuestState.OFFERED
                && p.giverNpcId != null && p.giverNpcId.equals(giverNpcId)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Предлагает игроку новый квест по нуждам деревни.
     */
    public Quest offerForPlayer(LiveNPCEntity giver, ServerPlayer player,
                                 ServerLevel level, long day) {
        if (giver == null || player == null) return null;

        UUID village = giver.getVillageId();
        if (village == null) return null;

        String rawId = giver.getVillageRawId();
        VillageData vd = new VillageData(rawId, level);
        if (!vd.exists()) return null;

        double needFood = vd.getNeedFood();
        double needWood = vd.getNeedWood();
        double needOre = vd.getNeedOre();
        double needHealing = vd.getNeedHealing();

        QuestType type;
        String description;
        String resource = "";
        int targetAmount = 10;
        int repReward = 15;

        if (needHealing > 70) {
            type = QuestType.HEAL_NPC;
            description = "Помоги вылечить больных в деревне.";
        } else if (needOre > 70) {
            type = QuestType.BRING_RESOURCE;
            description = "Принеси 10 железной руды.";
            resource = "minecraft:raw_iron";
        } else if (needWood > 70) {
            type = QuestType.BRING_RESOURCE;
            description = "Принеси 16 брёвен.";
            resource = "minecraft:oak_log";
            targetAmount = 16;
        } else if (needFood > 70) {
            type = QuestType.BRING_RESOURCE;
            description = "Принеси 20 хлеба.";
            resource = "minecraft:bread";
            targetAmount = 20;
        } else {
            type = QuestType.BRING_RESOURCE;
            description = "Помоги деревне ресурсами.";
            resource = "minecraft:iron_ingot";
        }

        Quest q = new Quest(giver.getUUID(), village, player.getUUID(), type, description, day);
        q.targetResource = resource;
        q.targetAmount = targetAmount;
        q.reputationReward = repReward;

        quests.put(q.questId, q);
        pendingOfferByPlayer.put(player.getUUID(), q.questId);

        LiveMineMod.LOGGER.info("Quest offered to {} by {}: {}",
            player.getName().getString(), giver.getCustomNameTag(), type.ruName);

        return q;
    }

    public boolean acceptByPlayer(ServerPlayer player, UUID questId) {
        Quest q = quests.get(questId);
        if (q == null) return false;
        if (q.state != QuestState.OFFERED) return false;

        q.state = QuestState.ACCEPTED;
        q.playerId = player.getUUID();

        questsByPlayer.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(questId);
        pendingOfferByPlayer.remove(player.getUUID());

        player.sendSystemMessage(Component.literal(
            "§a[Квест принят] §r" + q.description));

        LiveMineMod.LOGGER.info("Quest {} accepted by {}",
            questId.toString().substring(0, 8), player.getName().getString());
        return true;
    }

    public boolean declineByPlayer(ServerPlayer player, UUID questId) {
        Quest q = quests.get(questId);
        if (q == null) return false;
        if (q.state != QuestState.OFFERED) return false;

        q.state = QuestState.ABANDONED;
        pendingOfferByPlayer.remove(player.getUUID());

        player.sendSystemMessage(Component.literal("§7[Квест отклонён]"));
        return true;
    }

    // =========================================================================
    // Старые методы (совместимость)
    // =========================================================================

    public Quest offerQuest(LiveNPCEntity giver, QuestType type, String description, long day) {
        if (giver == null) return null;
        UUID village = giver.getVillageId();
        Quest q = new Quest(giver.getUUID(), village, null, type, description, day);
        quests.put(q.questId, q);
        LiveMineMod.LOGGER.info("Quest offered by {}: {}",
            giver.getCustomNameTag(), type.ruName);
        return q;
    }

    public Quest createQuest(QuestType type, LiveNPCEntity giver, ServerPlayer player,
                             String description, long day) {
        Quest q = new Quest(
            giver != null ? giver.getUUID() : null,
            giver != null ? giver.getVillageId() : null,
            player != null ? player.getUUID() : null,
            type, description, day
        );
        quests.put(q.questId, q);
        return q;
    }

    public Quest createDeliverQuest(LiveNPCEntity giver, ServerPlayer player,
                                    String itemId, int count, long day) {
        Quest q = createQuest(QuestType.BRING_RESOURCE, giver, player,
            "Принеси " + count + " × " + itemId, day);
        q.targetResource = itemId;
        q.targetAmount = count;
        q.reputationReward = 10 * count;
        return q;
    }

    public Quest generateByVillageNeed(LiveNPCEntity giver, ServerLevel level, long day) {
        if (giver == null) return null;
        UUID village = giver.getVillageId();
        if (village == null) return null;

        String rawId = giver.getVillageRawId();
        VillageData vd = new VillageData(rawId, level);
        if (!vd.exists()) return null;

        double needFood = vd.getNeedFood();
        double needWood = vd.getNeedWood();
        double needOre = vd.getNeedOre();
        double needHealing = vd.getNeedHealing();

        QuestType type;
        String description;

        if (needHealing > 70) {
            type = QuestType.HEAL_NPC;
            description = "Помоги вылечить больных";
        } else if (needOre > 70) {
            type = QuestType.BRING_RESOURCE;
            description = "Принеси 10 железа";
        } else if (needWood > 70) {
            type = QuestType.BRING_RESOURCE;
            description = "Принеси 16 брёвен";
        } else if (needFood > 70) {
            type = QuestType.BRING_RESOURCE;
            description = "Принеси 20 хлеба";
        } else {
            type = QuestType.BRING_RESOURCE;
            description = "Помоги деревне ресурсами";
        }

        Quest q = offerQuest(giver, type, description, day);
        if (q != null && type == QuestType.BRING_RESOURCE) {
            q.targetAmount = 10;
            q.reputationReward = 15;
        }
        return q;
    }

    public boolean accept(UUID playerId, UUID questId) {
        Quest q = quests.get(questId);
        if (q == null || q.state != QuestState.OFFERED) return false;
        q.state = QuestState.ACCEPTED;
        q.playerId = playerId;
        questsByPlayer.computeIfAbsent(playerId, k -> new ArrayList<>()).add(questId);
        return true;
    }

    public void progress(UUID playerId, UUID questId, int amount) {
        Quest q = quests.get(questId);
        if (q != null) q.progress(amount);
    }

    public void complete(UUID playerId, UUID questId) {
        Quest q = quests.get(questId);
        if (q == null) return;
        q.state = QuestState.COMPLETED;

        if (q.villageId != null) {
            ReputationManager.getInstance()
                .addVillageReputation(q.villageId, playerId, q.reputationReward);
        }
        LiveMineMod.LOGGER.info("Quest {} completed by {} (reward {} rep)",
            q.questId.toString().substring(0, 8), playerId, q.reputationReward);
    }

    public void fail(UUID questId) {
        Quest q = quests.get(questId);
        if (q != null) q.state = QuestState.FAILED;
    }

    public void abandon(UUID playerId, UUID questId) {
        Quest q = quests.get(questId);
        if (q != null) q.state = QuestState.ABANDONED;
        List<UUID> list = questsByPlayer.get(playerId);
        if (list != null) list.remove(questId);
    }

    public void tick(long currentDay) {
        for (Quest q : quests.values()) {
            if (q.state == QuestState.COMPLETED
                || q.state == QuestState.FAILED
                || q.state == QuestState.ABANDONED) continue;

            if (q.isExpired(currentDay)) {
                q.state = QuestState.FAILED;
            }
        }
    }

    public void tick(ServerLevel level) {
        tick(level.getDayTime() / 24000L);
    }

    // =========================================================================
    // Запросы
    // =========================================================================

    public Quest getQuest(UUID questId) { return quests.get(questId); }

    public List<Quest> getPlayerQuests(UUID playerId) {
        List<Quest> result = new ArrayList<>();
        List<UUID> ids = questsByPlayer.get(playerId);
        if (ids != null) {
            for (UUID id : ids) {
                Quest q = quests.get(id);
                if (q != null) result.add(q);
            }
        }
        return result;
    }

    public List<Quest> getActiveQuests() {
        List<Quest> result = new ArrayList<>();
        for (Quest q : quests.values()) {
            if (q.state == QuestState.ACCEPTED || q.state == QuestState.IN_PROGRESS) {
                result.add(q);
            }
        }
        return result;
    }

    public int size() { return quests.size(); }

    // =========================================================================
    // NBT
    // =========================================================================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Quest q : quests.values()) list.add(q.save());
        tag.put("quests", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        quests.clear();
        questsByPlayer.clear();
        pendingOfferByPlayer.clear();
        ListTag list = tag.getList("quests", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Quest q = Quest.load(list.getCompound(i));
            quests.put(q.questId, q);
            if (q.playerId != null) {
                questsByPlayer.computeIfAbsent(q.playerId, k -> new ArrayList<>()).add(q.questId);
            }
        }
    }

    public void clear() {
        quests.clear();
        questsByPlayer.clear();
        pendingOfferByPlayer.clear();
    }
}