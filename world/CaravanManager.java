package com.livemine.world;

import com.livemine.LiveMineMod;
import com.livemine.VillageManager;
import com.livemine.domain.VillageRecord;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Менеджер торговых караванов (ТЗ 20.0 §28.5).
 *
 * Торговая группа (торговец + боец) отправляется в дружественную деревню.
 * Длительность 2–5 дней в зависимости от расстояния.
 * Караван везёт излишки, привозит дефицитные ресурсы.
 *
 * При нападении на караван — casus belli (причина для войны).
 */
public final class CaravanManager {

    private static CaravanManager INSTANCE;

    public static final int CARAVAN_SPEED_PER_DAY = 500;   // блоков за игровой день

    public static final class Caravan {
        public UUID caravanId;
        public UUID sourceVillage;
        public UUID targetVillage;
        public List<UUID> members = new ArrayList<>();
        public long departureDay;
        public long arrivalDay;
        public String cargo;
        public boolean active;
        public boolean completed;

        public Caravan(UUID source, UUID target, List<UUID> members,
                        long departureDay, String cargo, double distance) {
            this.caravanId = UUID.randomUUID();
            this.sourceVillage = source;
            this.targetVillage = target;
            this.members = new ArrayList<>(members);
            this.departureDay = departureDay;
            this.arrivalDay = departureDay + (long) Math.ceil(distance / CARAVAN_SPEED_PER_DAY);
            this.cargo = cargo;
            this.active = true;
            this.completed = false;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", caravanId);
            tag.putUUID("source", sourceVillage);
            tag.putUUID("target", targetVillage);

            ListTag list = new ListTag();
            for (UUID m : members) {
                CompoundTag mt = new CompoundTag();
                mt.putUUID("uuid", m);
                list.add(mt);
            }
            tag.put("members", list);

            tag.putLong("departure", departureDay);
            tag.putLong("arrival", arrivalDay);
            tag.putString("cargo", cargo);
            tag.putBoolean("active", active);
            tag.putBoolean("completed", completed);
            return tag;
        }

        public static Caravan load(CompoundTag tag) {
            Caravan c = new Caravan(
                tag.getUUID("source"),
                tag.getUUID("target"),
                new ArrayList<>(),
                tag.getLong("departure"),
                tag.getString("cargo"),
                0
            );
            c.caravanId = tag.getUUID("id");
            c.arrivalDay = tag.getLong("arrival");
            c.active = tag.getBoolean("active");
            c.completed = tag.getBoolean("completed");

            ListTag list = tag.getList("members", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                c.members.add(list.getCompound(i).getUUID("uuid"));
            }
            return c;
        }
    }

    private final Map<UUID, Caravan> caravans = new HashMap<>();

    private CaravanManager() {}

    public static synchronized CaravanManager getInstance() {
        if (INSTANCE == null) INSTANCE = new CaravanManager();
        return INSTANCE;
    }

    // =========================================================================
    // Создание
    // =========================================================================

    public Caravan launchCaravan(UUID sourceVillage, UUID targetVillage,
                                  List<UUID> members, long currentDay, String cargo) {
        VillageRecord source = VillageManager.getInstance().getVillage(sourceVillage);
        VillageRecord target = VillageManager.getInstance().getVillage(targetVillage);
        if (source == null || target == null) return null;

        double distance = Math.sqrt(
            Math.pow(source.centerX() - target.centerX(), 2)
            + Math.pow(source.centerZ() - target.centerZ(), 2));

        Caravan c = new Caravan(sourceVillage, targetVillage, members,
            currentDay, cargo, distance);
        caravans.put(c.caravanId, c);

        LiveMineMod.LOGGER.info(
            "Caravan launched: {} в†' {} ({} blocks, arrival day {})",
            source.name(), target.name(), (int) distance, c.arrivalDay);
        return c;
    }

    // =========================================================================
    // РўРёРє
    // =========================================================================

    public void tick(long currentDay) {
        for (Caravan c : caravans.values()) {
            if (!c.active) continue;
            if (currentDay >= c.arrivalDay) {
                c.completed = true;
                c.active = false;
                LiveMineMod.LOGGER.info("Caravan {} arrived at destination", c.caravanId);
            }
        }
    }

    // =========================================================================
    // Запросы
    // =========================================================================

    public Collection<Caravan> getActiveCaravans() {
        List<Caravan> result = new ArrayList<>();
        for (Caravan c : caravans.values()) if (c.active) result.add(c);
        return result;
    }

    public int size() { return caravans.size(); }

    // =========================================================================
    // NBT
    // =========================================================================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Caravan c : caravans.values()) list.add(c.save());
        tag.put("caravans", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        caravans.clear();
        ListTag list = tag.getList("caravans", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Caravan c = Caravan.load(list.getCompound(i));
            caravans.put(c.caravanId, c);
        }
    }

    public void clear() { caravans.clear(); }
}
