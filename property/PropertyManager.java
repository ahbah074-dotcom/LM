package com.livemine.property;

import com.livemine.LiveMineMod;
import com.livemine.VillageData;
import com.livemine.building.BuildingSystem;
import com.livemine.entity.LiveNPCEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Менеджер собственности NPC.
 *
 * v3.0:
 *   - Автоматическое назначение дома (поиск свободного HOUSE_SMALL/MEDIUM).
 *   - Улучшение дома при накоплении ресурсов.
 *   - Ремонт при повреждении.
 *   - Передача дома наследнику.
 */
public final class PropertyManager {

    private static PropertyManager INSTANCE;

    public static final int MAX_HOME_LEVEL = 6;
    public static final int DAILY_TICK_CHANCE = 20;   // % шанс раз в день

    private final Map<UUID, NPCProperty> properties = new HashMap<>();

    private PropertyManager() {}

    public static synchronized PropertyManager getInstance() {
        if (INSTANCE == null) INSTANCE = new PropertyManager();
        return INSTANCE;
    }

    // =========================================================================
    // NPCProperty
    // =========================================================================

    public static final class NPCProperty {
        public final UUID ownerId;
        public UUID villageId;

        public BlockPos homePos;
        public int homeLevel;      // 1–6
        public BlockPos workPos;

        public final List<BlockPos> personalChests = new ArrayList<>();
        public final List<BlockPos> hiddenCaches = new ArrayList<>();

        public NPCProperty(UUID ownerId, UUID villageId) {
            this.ownerId = ownerId;
            this.villageId = villageId;
            this.homeLevel = 0;
        }

        public boolean hasHome() {
            return homePos != null && homeLevel > 0;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("owner", ownerId);
            if (villageId != null) tag.putUUID("village", villageId);
            if (homePos != null) {
                tag.putInt("home_x", homePos.getX());
                tag.putInt("home_y", homePos.getY());
                tag.putInt("home_z", homePos.getZ());
                tag.putInt("home_level", homeLevel);
            }
            if (workPos != null) {
                tag.putInt("work_x", workPos.getX());
                tag.putInt("work_y", workPos.getY());
                tag.putInt("work_z", workPos.getZ());
            }
            tag.put("chests", savePositions(personalChests));
            tag.put("caches", savePositions(hiddenCaches));
            return tag;
        }

        public static NPCProperty load(CompoundTag tag) {
            UUID owner = tag.hasUUID("owner") ? tag.getUUID("owner") : UUID.randomUUID();
            UUID village = tag.hasUUID("village") ? tag.getUUID("village") : null;
            NPCProperty prop = new NPCProperty(owner, village);

            if (tag.contains("home_x")) {
                prop.homePos = new BlockPos(
                    tag.getInt("home_x"), tag.getInt("home_y"), tag.getInt("home_z"));
                prop.homeLevel = tag.getInt("home_level");
            }
            if (tag.contains("work_x")) {
                prop.workPos = new BlockPos(
                    tag.getInt("work_x"), tag.getInt("work_y"), tag.getInt("work_z"));
            }
            prop.personalChests.addAll(loadPositions(tag.getList("chests", Tag.TAG_COMPOUND)));
            prop.hiddenCaches.addAll(loadPositions(tag.getList("caches", Tag.TAG_COMPOUND)));
            return prop;
        }

        private static ListTag savePositions(List<BlockPos> list) {
            ListTag result = new ListTag();
            for (BlockPos pos : list) {
                CompoundTag pt = new CompoundTag();
                pt.putInt("x", pos.getX());
                pt.putInt("y", pos.getY());
                pt.putInt("z", pos.getZ());
                result.add(pt);
            }
            return result;
        }

        private static List<BlockPos> loadPositions(ListTag list) {
            List<BlockPos> result = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                CompoundTag pt = list.getCompound(i);
                result.add(new BlockPos(pt.getInt("x"), pt.getInt("y"), pt.getInt("z")));
            }
            return result;
        }
    }

    // =========================================================================
    // Основные операции
    // =========================================================================

    public NPCProperty getOrCreate(UUID ownerId, UUID villageId) {
        return properties.computeIfAbsent(ownerId, k -> new NPCProperty(k, villageId));
    }

    public NPCProperty get(UUID ownerId) {
        return properties.get(ownerId);
    }

    public void remove(UUID ownerId) {
        properties.remove(ownerId);
    }

    public Collection<NPCProperty> getAllProperties() {
        return properties.values();
    }

    public int size() { return properties.size(); }

    public int countWithHome() {
        int c = 0;
        for (NPCProperty p : properties.values()) {
            if (p.hasHome()) c++;
        }
        return c;
    }

    // =========================================================================
    // v3.0: Автоматическое назначение дома
    // =========================================================================

    /**
     * Раз в день: проверка дома. Если нет — попытка найти свободный.
     * Если есть — проверка ремонта и улучшения.
     */
    public void tickNpc(LiveNPCEntity npc, ServerLevel level, long currentDay) {
        if (npc == null || !npc.isAdult()) return;

        UUID ownerId = npc.getUUID();
        UUID villageId = npc.getVillageId();
        if (villageId == null) return;

        NPCProperty prop = getOrCreate(ownerId, villageId);

        // 1. Нет дома — ищем свободный.
        if (!prop.hasHome() && level.random.nextInt(100) < DAILY_TICK_CHANCE) {
            tryAssignHome(npc, prop, level);
        }

        // 2. Есть дом — проверка состояния.
        if (prop.hasHome()) {
            checkRepair(npc, prop, level);
            checkUpgrade(npc, prop, level, currentDay);
        }
    }

    /**
     * Ищет свободный дом поблизости.
     */
    private void tryAssignHome(LiveNPCEntity npc, NPCProperty prop, ServerLevel level) {
        String rawId = npc.getVillageRawId();
        if (rawId.isEmpty()) return;

        VillageData vd = new VillageData(rawId, level);
        if (!vd.exists()) return;

        // Ищем уже построенные дома в деревне, которые никто не занял.
        BlockPos freeHouse = findFreeHouse(vd, level);
        if (freeHouse != null) {
            prop.homePos = freeHouse;
            prop.homeLevel = 1;
            setChanged();
            LiveMineMod.LOGGER.info("NPC {} got home at {}", npc.getCustomNameTag(), freeHouse);
        }
    }

    /**
     * Ищет свободный блок дома в радиусе деревни.
     * Дом = земляной/деревянный блок, зарегистрированный в деревне.
     */
    private BlockPos findFreeHouse(VillageData vd, ServerLevel level) {
        BlockPos center = vd.getCenter();
        if (center == null) return null;

        // Сканируем область радиуса vd.getRadius() на 8 блоков вниз.
        int r = Math.min(vd.getRadius(), 48);

        for (int radius = 4; radius <= r; radius += 4) {
            for (int angle = 0; angle < 16; angle++) {
                double a = 2 * Math.PI * (angle / 16.0);
                int dx = (int) (Math.cos(a) * radius);
                int dz = (int) (Math.sin(a) * radius);

                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                int y = level.getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    x, z);

                BlockPos candidate = new BlockPos(x, y - 1, z);
                if (isClaimed(candidate)) continue;

                // Проверяем, что это часть деревянной/земляной постройки.
                BlockState state = level.getBlockState(candidate);
                if (isHomeBlock(state)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private boolean isClaimed(BlockPos pos) {
        for (NPCProperty p : properties.values()) {
            if (p.homePos != null && p.homePos.equals(pos)) return true;
        }
        return false;
    }

    private boolean isHomeBlock(BlockState state) {
        // Любой не-air блок, не растительность.
        if (state.isAir()) return false;
        String n = state.getBlock().getDescriptionId().toLowerCase();
        return n.contains("planks") || n.contains("log")
            || n.contains("cobble") || n.contains("stone_brick")
            || n.contains("bricks") || n.contains("wool")
            || n.contains("dirt") || n.contains("grass_block");
    }

    // =========================================================================
    // Ремонт
    // =========================================================================

    /**
     * Проверка состояния дома: если рядом есть воздух там, где был блок — ремонт.
     * Упрощённая эвристика: ищем в радиусе 3 от homePos, если есть дыры.
     */
    private void checkRepair(LiveNPCEntity npc, NPCProperty prop, ServerLevel level) {
        if (prop.homePos == null) return;
        if (level.random.nextInt(100) >= 10) return; // 10% шанс

        int holes = countHoles(prop.homePos, level, 4);
        if (holes > 3) {
            LiveMineMod.LOGGER.info("NPC {} repairs home at {} ({} holes)",
                npc.getCustomNameTag(), prop.homePos, holes);
            // TODO: фактический ремонт (установка блоков).
            // Пока — просто лог, чтобы не ломать чужие постройки.
        }
    }

    private int countHoles(BlockPos center, ServerLevel level, int radius) {
        int holes = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -1; dy <= 3; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (level.getBlockState(p).isAir()) holes++;
                }
            }
        }
        return holes;
    }

    // =========================================================================
    // Улучшение дома
    // =========================================================================

    private void checkUpgrade(LiveNPCEntity npc, NPCProperty prop, ServerLevel level, long currentDay) {
        if (prop.homeLevel >= MAX_HOME_LEVEL) return;
        if (level.random.nextInt(100) >= 2) return; // 2% шанс раз в день

        // Требуется навык BUILDING.
        if (npc.getSkills() == null) return;
        double building = npc.getSkills().getLevel(com.livemine.NPCSkills.SkillType.BUILDING);
        int nextLevel = prop.homeLevel + 1;
        if (building < nextLevel * 1.5) return;

        // Требуются ресурсы деревни.
        String rawId = npc.getVillageRawId();
        if (rawId.isEmpty()) return;

        VillageData vd = new VillageData(rawId, level);
        if (!vd.exists()) return;
        if (vd.getResources() < 50.0) return;

        vd.setResources(vd.getResources() - 50.0);
        prop.homeLevel = nextLevel;
        setChanged();

        // Хроника.
        try {
            vd.addChronicleEntry(com.livemine.Chronicle.EntryType.BUILDING, currentDay,
                "Дом улучшен до уровня " + nextLevel + ": " + npc.getCustomNameTag(),
                npc.getCustomNameTag());
        } catch (Exception ignored) {}

        LiveMineMod.LOGGER.info("NPC {} upgraded home to level {}",
            npc.getCustomNameTag(), nextLevel);
    }

    // =========================================================================
    // Наследование
    // =========================================================================

    /**
     * Передача дома наследнику.
     */
    public void onOwnerDeath(UUID deadOwnerId, UUID heirId, ServerLevel level) {
        NPCProperty prop = properties.get(deadOwnerId);
        if (prop == null) return;

        if (heirId == null) {
            // Дом возвращается деревне.
            properties.remove(deadOwnerId);
            setChanged();
            return;
        }

        NPCProperty heirProp = properties.computeIfAbsent(heirId,
            k -> new NPCProperty(k, prop.villageId));

        // Если у наследника уже есть дом — не забираем.
        if (!heirProp.hasHome() && prop.hasHome()) {
            heirProp.homePos = prop.homePos;
            heirProp.homeLevel = prop.homeLevel;
            LiveMineMod.LOGGER.info("Home inherited: {} -> {}",
                prop.homePos, heirId);
        }

        properties.remove(deadOwnerId);
        setChanged();
    }

    // =========================================================================
    // NBT
    // =========================================================================

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        int i = 0;
        for (NPCProperty prop : properties.values()) {
            root.put("prop_" + i, prop.save());
            i++;
        }
        root.putInt("count", i);
        return root;
    }

    public void load(CompoundTag tag) {
        properties.clear();
        if (tag == null) return;
        int count = tag.getInt("count");
        for (int i = 0; i < count; i++) {
            if (!tag.contains("prop_" + i)) continue;
            NPCProperty prop = NPCProperty.load(tag.getCompound("prop_" + i));
            if (prop.ownerId != null) properties.put(prop.ownerId, prop);
        }
    }

    public void clear() {
        properties.clear();
    }

    private void setChanged() {
        // PersistenceManager подхватит при flush().
    }
}