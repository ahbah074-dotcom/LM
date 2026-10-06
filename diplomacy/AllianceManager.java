package com.livemine.diplomacy;

import com.livemine.LiveMineMod;
import com.livemine.VillageManager;
import com.livemine.domain.VillageRecord;
import com.livemine.world.RoadPlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Менеджер альянсов между деревнями (ТЗ 20.0 §28.4).
 *
 * Альянс формируется голосованием 2/3 в обеих деревнях.
 * При альянсе:
 *   - Общая оборона
 *   - Общие экспедиции
 *   - Торговля со скидкой 10%
 *   - Строительство дороги навстречу
 *
 * Дорога строится в 2 фазы:
 *   1. Ширина 1 блок (bulldozer)
 *   2. Расширение до 3 блоков после соединения
 */
public final class AllianceManager {

    private static AllianceManager INSTANCE;

    public static final double MIN_TRUST_FOR_ALLIANCE = 75.0;
    public static final float VOTE_THRESHOLD = 2.0f / 3.0f;   // 2/3

    public static final class Alliance {
        public UUID id;
        public UUID villageA;
        public UUID villageB;
        public long formedDay;
        public boolean roadBuilt;
        public boolean roadExpanded;
        public List<BlockPos> roadPath = new ArrayList<>();
        public long roadPhase1DoneDay = -1;
        public long roadPhase2DoneDay = -1;

        public Alliance(UUID a, UUID b, long day) {
            this.id = UUID.randomUUID();
            this.villageA = a;
            this.villageB = b;
            this.formedDay = day;
            this.roadBuilt = false;
            this.roadExpanded = false;
        }

        public boolean involves(UUID villageId) {
            return villageA.equals(villageId) || villageB.equals(villageId);
        }

        public UUID other(UUID villageId) {
            return villageA.equals(villageId) ? villageB : villageA;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", id);
            tag.putUUID("a", villageA);
            tag.putUUID("b", villageB);
            tag.putLong("day", formedDay);
            tag.putBoolean("roadBuilt", roadBuilt);
            tag.putBoolean("roadExpanded", roadExpanded);
            tag.putLong("phase1", roadPhase1DoneDay);
            tag.putLong("phase2", roadPhase2DoneDay);

            ListTag pathList = new ListTag();
            for (BlockPos p : roadPath) {
                CompoundTag pt = new CompoundTag();
                pt.putInt("x", p.getX());
                pt.putInt("y", p.getY());
                pt.putInt("z", p.getZ());
                pathList.add(pt);
            }
            tag.put("roadPath", pathList);
            return tag;
        }

        public static Alliance load(CompoundTag tag) {
            Alliance a = new Alliance(tag.getUUID("a"), tag.getUUID("b"), tag.getLong("day"));
            a.id = tag.getUUID("id");
            a.roadBuilt = tag.getBoolean("roadBuilt");
            a.roadExpanded = tag.getBoolean("roadExpanded");
            a.roadPhase1DoneDay = tag.getLong("phase1");
            a.roadPhase2DoneDay = tag.getLong("phase2");

            ListTag pathList = tag.getList("roadPath", Tag.TAG_COMPOUND);
            for (int i = 0; i < pathList.size(); i++) {
                CompoundTag pt = pathList.getCompound(i);
                a.roadPath.add(new BlockPos(pt.getInt("x"), pt.getInt("y"), pt.getInt("z")));
            }
            return a;
        }
    }

    private final Map<UUID, Alliance> alliances = new HashMap<>();

    private AllianceManager() {}

    public static synchronized AllianceManager getInstance() {
        if (INSTANCE == null) INSTANCE = new AllianceManager();
        return INSTANCE;
    }

    // =========================================================================
    // Формирование альянса
    // =========================================================================

    /**
     * Попытка сформировать альянс после голосования.
     * Проверяет доверие и состояние дипломатии.
     */
    public boolean tryFormAlliance(UUID villageA, UUID villageB, long currentDay) {
        DiplomacyManager dip = DiplomacyManager.getInstance();

        // РќРµ РІ РІРѕР№РЅРµ
        if (dip.atWar(villageA, villageB)) {
            LiveMineMod.LOGGER.debug("Alliance rejected: villages at war");
            return false;
        }

        // Уже в альянсе
        if (dip.allied(villageA, villageB)) {
            return false;
        }

        // Проверка trust
        double trust = dip.getRelation(villageA, villageB).trust;
        if (trust < MIN_TRUST_FOR_ALLIANCE) {
            LiveMineMod.LOGGER.debug("Alliance rejected: trust {} < {}", trust, MIN_TRUST_FOR_ALLIANCE);
            return false;
        }

        // Формируем альянс
        dip.formAlliance(villageA, villageB, currentDay);

        Alliance alliance = new Alliance(villageA, villageB, currentDay);
        alliances.put(alliance.id, alliance);

        LiveMineMod.LOGGER.info("Alliance formed between {} and {}", villageA, villageB);
        return true;
    }

    public void dissolveAlliance(UUID villageA, UUID villageB) {
        alliances.values().removeIf(a ->
            (a.villageA.equals(villageA) && a.villageB.equals(villageB))
                || (a.villageA.equals(villageB) && a.villageB.equals(villageA)));

        DiplomacyManager.getInstance().declarePeace(villageA, villageB);
        LiveMineMod.LOGGER.info("Alliance dissolved: {} ↔ {}", villageA, villageB);
    }

    // =========================================================================
    // Дороги
    // =========================================================================

    /**
     * Тик — раз в игровой день проверяет готовность строить дорогу.
     */
    public void tick(ServerLevel level) {
        long currentDay = level.getDayTime() / 24000L;

        for (Alliance alliance : alliances.values()) {
            // Фаза 1: планируем и строим дорогу 1 блок шириной
            if (!alliance.roadBuilt) {
                planAndBuildRoadPhase1(alliance, level, currentDay);
                continue;
            }

            // Фаза 2: расширение до 3 блоков
            if (alliance.roadBuilt && !alliance.roadExpanded
                && currentDay - alliance.roadPhase1DoneDay >= 5) {
                expandRoadPhase2(alliance, level, currentDay);
            }
        }
    }

    private void planAndBuildRoadPhase1(Alliance alliance, ServerLevel level, long currentDay) {
        VillageRecord va = VillageManager.getInstance().getVillage(alliance.villageA);
        VillageRecord vb = VillageManager.getInstance().getVillage(alliance.villageB);
        if (va == null || vb == null) return;

        BlockPos start = new BlockPos(va.centerX(), va.centerY(), va.centerZ());
        BlockPos end = new BlockPos(vb.centerX(), vb.centerY(), vb.centerZ());

        List<BlockPos> path = RoadPlanner.plan(level, start, end);
        if (path == null) return;

        String biome = level.getBiome(start).unwrapKey()
            .map(k -> k.location().getPath()).orElse("plains");

        int placed = RoadPlanner.buildRoad(level, path, biome);
        if (placed > 0) {
            alliance.roadPath = path;
            alliance.roadBuilt = true;
            alliance.roadPhase1DoneDay = currentDay;
            LiveMineMod.LOGGER.info(
                "Road phase 1 complete: {} blocks between {} and {}",
                placed, va.name(), vb.name());
        }
    }

    private void expandRoadPhase2(Alliance alliance, ServerLevel level, long currentDay) {
        VillageRecord va = VillageManager.getInstance().getVillage(alliance.villageA);
        if (va == null) return;

        BlockPos start = new BlockPos(va.centerX(), va.centerY(), va.centerZ());
        String biome = level.getBiome(start).unwrapKey()
            .map(k -> k.location().getPath()).orElse("plains");

        int placed = RoadPlanner.expandRoad(level, alliance.roadPath, biome);
        if (placed > 0) {
            alliance.roadExpanded = true;
            alliance.roadPhase2DoneDay = currentDay;
            LiveMineMod.LOGGER.info("Road phase 2 complete: {} blocks expanded", placed);
        }
    }

    // =========================================================================
    // Запросы
    // =========================================================================

    public List<Alliance> getAlliancesFor(UUID villageId) {
        List<Alliance> result = new ArrayList<>();
        for (Alliance a : alliances.values()) {
            if (a.involves(villageId)) result.add(a);
        }
        return result;
    }

    public boolean areAllied(UUID villageA, UUID villageB) {
        for (Alliance a : alliances.values()) {
            if (a.involves(villageA) && a.involves(villageB)) return true;
        }
        return false;
    }

    public int size() { return alliances.size(); }

    // =========================================================================
    // NBT
    // =========================================================================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Alliance a : alliances.values()) list.add(a.save());
        tag.put("alliances", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        alliances.clear();
        ListTag list = tag.getList("alliances", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Alliance a = Alliance.load(list.getCompound(i));
            alliances.put(a.id, a);
        }
    }

    public void clear() { alliances.clear(); }
}
