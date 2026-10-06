package com.livemine.achievement;

import com.livemine.LiveMineMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Менеджер достижений LiveMine (ТЗ 20.0 §35).
 *
 * 13 достижений. Хранит Map<UUID игрока, Set<Achievement>>.
 */
public final class AchievementManager {

    private static AchievementManager INSTANCE;

    public enum Achievement {
        FIRST_MASTER        ("Первый мастер", "Первый NPC достиг уровня 10"),
        SAVED_NPC           ("Спаситель", "Спасён раненый NPC"),
        HONORABLE_BATTLE    ("Честный бой", "Выигран честный бой"),
        DAUGHTER_VILLAGE    ("Расширение", "Основан дочерний посёлок"),
        ALLIANCE_MADE       ("Дипломат", "Заключён альянс"),
        RAID_DEFENDED       ("Защитник", "Отражён рейд без потерь"),
        MANSION_BUILT       ("Особняк", "Построен особняк (уровень 5)"),
        ABANDONED_FOUND     ("Археолог", "Найдена заброшенная деревня"),
        STRANGER_ACCEPTED   ("Гостеприимство", "Принят странник"),
        DYNASTY_GROWN       ("Династия", "Выращена династия 3+ поколений"),
        CONTEST_WON         ("Мастер на все руки", "Победа в конкурсе мастерства"),
        CARAVAN_DELIVERED   ("Торговец", "Караван доставлен"),
        ALL_SKILLS_MASTERED ("Легенда деревни", "Все 29 навыков освоены");

        public final String title;
        public final String description;

        Achievement(String title, String desc) {
            this.title = title;
            this.description = desc;
        }
    }

    private final Map<UUID, Set<Achievement>> playerAchievements = new HashMap<>();

    private AchievementManager() {}

    public static synchronized AchievementManager getInstance() {
        if (INSTANCE == null) INSTANCE = new AchievementManager();
        return INSTANCE;
    }

    // =========================================================================
    // Разблокировка
    // =========================================================================

    public boolean unlock(ServerPlayer player, Achievement achievement) {
        if (player == null || achievement == null) return false;
        UUID id = player.getUUID();
        Set<Achievement> set = playerAchievements.computeIfAbsent(id, k -> new HashSet<>());
        if (set.contains(achievement)) return false;

        set.add(achievement);
        player.sendSystemMessage(Component.literal(
            "§6§l[Достижение]§r §e" + achievement.title + "§r — §7" + achievement.description));

        LiveMineMod.LOGGER.info("Achievement unlocked for {}: {}",
            player.getName().getString(), achievement.title);
        return true;
    }

    public boolean unlock(UUID playerId, Achievement achievement) {
        if (playerId == null || achievement == null) return false;
        Set<Achievement> set = playerAchievements.computeIfAbsent(playerId, k -> new HashSet<>());
        return set.add(achievement);
    }

    public boolean has(UUID playerId, Achievement achievement) {
        Set<Achievement> set = playerAchievements.get(playerId);
        return set != null && set.contains(achievement);
    }

    // =========================================================================
    // Триггеры
    // =========================================================================

    public void onNpcMasterAchieved(ServerPlayer player) { unlock(player, Achievement.FIRST_MASTER); }
    public void onNpcSaved(ServerPlayer player) { unlock(player, Achievement.SAVED_NPC); }
    public void onHonorableBattleWon(ServerPlayer player) { unlock(player, Achievement.HONORABLE_BATTLE); }
    public void onDaughterVillageFounded(ServerPlayer player) { unlock(player, Achievement.DAUGHTER_VILLAGE); }
    public void onAllianceFormed(ServerPlayer player) { unlock(player, Achievement.ALLIANCE_MADE); }
    public void onRaidDefendedNoLosses(ServerPlayer player) { unlock(player, Achievement.RAID_DEFENDED); }
    public void onMansionBuilt(ServerPlayer player) { unlock(player, Achievement.MANSION_BUILT); }
    public void onAbandonedVillageFound(ServerPlayer player) { unlock(player, Achievement.ABANDONED_FOUND); }
    public void onStrangerAccepted(ServerPlayer player) { unlock(player, Achievement.STRANGER_ACCEPTED); }
    public void onDynastyGrown(ServerPlayer player) { unlock(player, Achievement.DYNASTY_GROWN); }
    public void onContestWon(ServerPlayer player) { unlock(player, Achievement.CONTEST_WON); }
    public void onCaravanDelivered(ServerPlayer player) { unlock(player, Achievement.CARAVAN_DELIVERED); }

    // =========================================================================
    // Запросы
    // =========================================================================

    public Set<Achievement> getPlayerAchievements(UUID playerId) {
        return playerAchievements.getOrDefault(playerId, new HashSet<>());
    }

    public int getUnlockedCount(UUID playerId) {
        return getPlayerAchievements(playerId).size();
    }

    public int getTotalAchievements() {
        return Achievement.values().length;
    }

    public List<Achievement> getAllAchievements() {
        List<Achievement> list = new ArrayList<>();
        for (Achievement a : Achievement.values()) list.add(a);
        return list;
    }

    // =========================================================================
    // NBT
    // =========================================================================

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();

        for (Map.Entry<UUID, Set<Achievement>> e : playerAchievements.entrySet()) {
            CompoundTag pt = new CompoundTag();
            pt.putUUID("player", e.getKey());
            ListTag achList = new ListTag();
            for (Achievement a : e.getValue()) {
                CompoundTag at = new CompoundTag();
                at.putString("id", a.name());
                achList.add(at);
            }
            pt.put("achievements", achList);
            list.add(pt);
        }
        root.put("players", list);
        return root;
    }

    public void load(CompoundTag tag) {
        playerAchievements.clear();
        ListTag list = tag.getList("players", Tag.TAG_COMPOUND);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag pt = list.getCompound(i);
            UUID playerId = pt.getUUID("player");
            Set<Achievement> set = new HashSet<>();

            ListTag achList = pt.getList("achievements", Tag.TAG_COMPOUND);
            for (int j = 0; j < achList.size(); j++) {
                try {
                    set.add(Achievement.valueOf(achList.getCompound(j).getString("id")));
                } catch (IllegalArgumentException ignored) {}
            }
            playerAchievements.put(playerId, set);
        }
    }

    public void clear() { playerAchievements.clear(); }
}
