package com.livemine.pet;

import com.livemine.LiveMineMod;
import com.livemine.entity.LiveNPCEntity;
import com.livemine.personality.NPCEmotions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.Horse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Менеджер питомцев NPC.
 *
 * v2.0: пытается приручить дикое животное рядом,
 *       даёт эмоции, передаётся по наследству.
 */
public final class PetManager {

    private static PetManager INSTANCE;

    private static final double TAME_RADIUS = 16.0;
    private static final int MAX_PETS_PER_NPC = 1;

    public enum PetType {
        DOG("Собака", Wolf.class),
        CAT("Кошка", Cat.class),
        HORSE("Лошадь", Horse.class),
        PARROT("Попугай", Parrot.class);

        public final String ruName;
        public final Class<? extends Mob> clazz;

        PetType(String ru, Class<? extends Mob> clazz) {
            this.ruName = ru;
            this.clazz = clazz;
        }
    }

    public static final class PetRecord {
        public UUID petUUID;
        public UUID ownerUUID;
        public PetType type;
        public String name;
        public long bondedDay;
        public boolean inherited;

        public PetRecord(UUID pet, UUID owner, PetType type, String name, long day) {
            this.petUUID = pet;
            this.ownerUUID = owner;
            this.type = type;
            this.name = name != null ? name : "";
            this.bondedDay = day;
            this.inherited = false;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("pet", petUUID);
            tag.putUUID("owner", ownerUUID);
            tag.putString("type", type.name());
            tag.putString("name", name);
            tag.putLong("day", bondedDay);
            tag.putBoolean("inherited", inherited);
            return tag;
        }

        public static PetRecord load(CompoundTag tag) {
            try {
                PetRecord r = new PetRecord(
                    tag.getUUID("pet"),
                    tag.getUUID("owner"),
                    PetType.valueOf(tag.getString("type")),
                    tag.getString("name"),
                    tag.getLong("day")
                );
                r.inherited = tag.getBoolean("inherited");
                return r;
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private final Map<UUID, PetRecord> pets = new HashMap<>();

    private PetManager() {}

    public static synchronized PetManager getInstance() {
        if (INSTANCE == null) INSTANCE = new PetManager();
        return INSTANCE;
    }

    // =========================================================================
    // v2.0: Автоматическое приручение
    // =========================================================================

    /**
     * Попытка приручить животное рядом с NPC.
     * Вызывать раз в день для каждого NPC.
     */
    public boolean tryTameNearby(LiveNPCEntity npc, ServerLevel level) {
        if (npc == null || !npc.isAdult()) return false;

        UUID ownerId = npc.getUUID();
        if (pets.containsKey(ownerId)) return false;  // уже есть питомец

        // Ищем дикое животное в радиусе.
        for (PetType type : PetType.values()) {
            var entities = level.getEntitiesOfClass(type.clazz,
                npc.getBoundingBox().inflate(TAME_RADIUS));
            for (Mob animal : entities) {
                if (animal.isRemoved()) continue;
                if (pets.containsKey(animal.getUUID())) continue;
                if (!isWild(animal)) continue;

                // Приручаем.
                long day = level.getDayTime() / 24000L;
                PetRecord rec = bond(animal.getUUID(), ownerId, type,
                    animal.getName().getString(), day);
                rec.inherited = false;

                // Эмоции — радость.
                NPCEmotions em = npc.getEmotions();
                if (em != null) em.trigger(NPCEmotions.Emotion.JOY, 25);

                LiveMineMod.LOGGER.info("NPC {} tamed {} (type {})",
                    npc.getCustomNameTag(), animal.getName().getString(), type.ruName);
                return true;
            }
        }
        return false;
    }

    private boolean isWild(Mob animal) {
        if (animal instanceof Wolf w) return !w.isTame();
        if (animal instanceof Cat c) return !c.isTame();
        if (animal instanceof Horse h) return !h.isTamed();
        if (animal instanceof Parrot p) return !p.isTame();
        return false;
    }

    // =========================================================================
    // Операции с питомцами
    // =========================================================================

    public PetRecord bond(UUID petUUID, UUID ownerUUID, PetType type, String name, long day) {
        PetRecord r = new PetRecord(petUUID, ownerUUID, type, name, day);
        pets.put(petUUID, r);
        return r;
    }

    public PetRecord get(UUID petUUID) {
        return pets.get(petUUID);
    }

    public PetRecord getPetOfOwner(UUID ownerUUID) {
        for (PetRecord r : pets.values()) {
            if (r.ownerUUID.equals(ownerUUID)) return r;
        }
        return null;
    }

    /**
     * Передача питомца при смерти владельца.
     */
    public PetRecord transfer(UUID petUUID, UUID newOwner) {
        PetRecord r = pets.get(petUUID);
        if (r == null) return null;
        r.ownerUUID = newOwner;
        r.inherited = true;
        LiveMineMod.LOGGER.info("Pet transferred: {} -> {}", r.name, newOwner);
        return r;
    }

    /**
     * Передача всех питомцев умершего NPC его супругу/детям.
     */
    public void onOwnerDeath(UUID deadOwnerId, UUID heirId, ServerLevel level) {
        PetRecord pet = getPetOfOwner(deadOwnerId);
        if (pet == null) return;

        if (heirId != null) {
            transfer(pet.petUUID, heirId);
        } else {
            // Не heir — отпускаем.
            release(pet.petUUID);
        }
    }

    public void release(UUID petUUID) {
        PetRecord r = pets.remove(petUUID);
        if (r != null) {
            LiveMineMod.LOGGER.info("Pet released: {}", r.name);
        }
    }

    public List<PetRecord> getByOwner(UUID ownerUUID) {
        List<PetRecord> result = new ArrayList<>();
        for (PetRecord r : pets.values()) {
            if (r.ownerUUID.equals(ownerUUID)) result.add(r);
        }
        return result;
    }

    public Collection<PetRecord> getAll() {
        return pets.values();
    }

    public int size() { return pets.size(); }

    // =========================================================================
    // Тик — эмоции от питомца
    // =========================================================================

    /**
     * Раз в день для каждого NPC с питомцем.
     */
    public void tickNpc(LiveNPCEntity npc, ServerLevel level) {
        if (npc == null) return;
        PetRecord pet = getPetOfOwner(npc.getUUID());
        if (pet == null) return;

        // Проверяем, жив ли питомец.
        Entity e = level.getEntity(pet.petUUID);
        if (e == null || e.isRemoved()) {
            // Питомец потерян — грусть.
            pets.remove(pet.petUUID);
            NPCEmotions em = npc.getEmotions();
            if (em != null) em.trigger(NPCEmotions.Emotion.GRIEF, 20);
            LiveMineMod.LOGGER.info("NPC {} lost pet {}", npc.getCustomNameTag(), pet.name);
            return;
        }

        // Регулярное взаимодействие — небольшая радость.
        NPCEmotions em = npc.getEmotions();
        if (em != null && level.random.nextFloat() < 0.3f) {
            em.trigger(NPCEmotions.Emotion.JOY, 5);
        }
    }

    // =========================================================================
    // NBT
    // =========================================================================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (PetRecord r : pets.values()) list.add(r.save());
        tag.put("pets", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        pets.clear();
        if (tag == null) return;
        ListTag list = tag.getList("pets", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            PetRecord r = PetRecord.load(list.getCompound(i));
            if (r != null) pets.put(r.petUUID, r);
        }
    }

    public void clear() {
        pets.clear();
    }
}