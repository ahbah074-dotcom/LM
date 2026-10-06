package com.livemine.network;

import com.livemine.LiveMineMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Пакет кэша деревни (ТЗ 20.1 §76 VillageClientCache).
 *
 * Направление: Server → Client.
 *
 * Вместо отправки N пакетов на N NPC — отправляется 1 пакет на деревню.
 *
 * Содержит:
 *   - villageId (UUID)
 *   - villageName (String)
 *   - population (int)
 *   - radius (int)
 *   - resources (double)
 *   - список записей NPC (краткая информация)
 *
 * Отправляется раз в 200 тиков (10 сек) для каждой активной деревни
 * или при существенном изменении.
 *
 * Оптимизация: при 30 NPC в деревне — 1 пакет вместо 30.
 */
public final class VillageClientCache implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<VillageClientCache> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(LiveMineMod.MOD_ID, "village_cache")
        );

    public static final StreamCodec<FriendlyByteBuf, VillageClientCache> STREAM_CODEC =
        StreamCodec.ofMember(VillageClientCache::write, VillageClientCache::new);

    // =========================================================================
    // Вложенный класс — запись NPC
    // =========================================================================
    public static final class NpcEntry {
        public final UUID uuid;
        public final String name;
        public final int entityId;
        public final float health;
        public final String goal;

        public NpcEntry(UUID uuid, String name, int entityId, float health, String goal) {
            this.uuid = uuid;
            this.name = name != null ? name : "NPC";
            this.entityId = entityId;
            this.health = health;
            this.goal = goal != null ? goal : "WANDER";
        }
    }

    // =========================================================================
    // Поля
    // =========================================================================
    private final UUID villageId;
    private final String villageName;
    private final int population;
    private final int radius;
    private final double resources;
    private final List<NpcEntry> npcs;

    public VillageClientCache(UUID villageId, String villageName, int population,
                              int radius, double resources, List<NpcEntry> npcs) {
        this.villageId = villageId;
        this.villageName = villageName != null ? villageName : "Village";
        this.population = population;
        this.radius = radius;
        this.resources = resources;
        this.npcs = npcs != null ? npcs : Collections.emptyList();
    }

    // =========================================================================
    // Сериализация
    // =========================================================================

    public VillageClientCache(FriendlyByteBuf buf) {
        this.villageId = buf.readUUID();
        this.villageName = buf.readUtf(64);
        this.population = buf.readVarInt();
        this.radius = buf.readVarInt();
        this.resources = buf.readDouble();

        int count = buf.readVarInt();
        List<NpcEntry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID uuid = buf.readUUID();
            String name = buf.readUtf(64);
            int entityId = buf.readVarInt();
            float health = buf.readFloat();
            String goal = buf.readUtf(32);
            list.add(new NpcEntry(uuid, name, entityId, health, goal));
        }
        this.npcs = list;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(villageId);
        buf.writeUtf(villageName, 64);
        buf.writeVarInt(population);
        buf.writeVarInt(radius);
        buf.writeDouble(resources);

        buf.writeVarInt(npcs.size());
        for (NpcEntry entry : npcs) {
            buf.writeUUID(entry.uuid);
            buf.writeUtf(entry.name, 64);
            buf.writeVarInt(entry.entityId);
            buf.writeFloat(entry.health);
            buf.writeUtf(entry.goal, 32);
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // =========================================================================
    // Обработка на клиенте
    // =========================================================================

    public static void handleClient(VillageClientCache packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Кладём в клиентский кэш
            com.livemine.client.VillageClientCacheStorage.put(packet);
        });
    }

    // =========================================================================
    // Геттеры
    // =========================================================================

    public UUID getVillageId() { return villageId; }
    public String getVillageName() { return villageName; }
    public int getPopulation() { return population; }
    public int getRadius() { return radius; }
    public double getResources() { return resources; }
    public List<NpcEntry> getNpcs() { return Collections.unmodifiableList(npcs); }
    public int getNpcCount() { return npcs.size(); }
}
