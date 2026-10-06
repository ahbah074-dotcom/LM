package com.livemine.network;

import com.livemine.LiveMineMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Пакет синхронизации состояния NPC (S→C).
 *
 * v2.0: добавлены поля диалога и квеста.
 * Открывает NPCStatusScreen с тремя секциями.
 */
public final class NPCStatePacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<NPCStatePacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(LiveMineMod.MOD_ID, "npc_state")
        );

    public static final StreamCodec<FriendlyByteBuf, NPCStatePacket> STREAM_CODEC =
        StreamCodec.ofMember(NPCStatePacket::write, NPCStatePacket::new);

    // =========================================================================
    // Поля
    // =========================================================================
    private final int entityId;
    private final String name;
    private final String profession;
    private final double hunger;
    private final double energy;
    private final double health;
    private final double social;
    private final String currentGoal;
    private final String villageRawId;

    // v2.0: диалог
    private final String dialogText;

    // v2.0: квест (пусто, если нет предложения)
    private final String questId;
    private final String questType;
    private final String questDescription;
    private final int questReward;
    private final int questTargetAmount;
    private final int questCurrentAmount;

    public NPCStatePacket(int entityId, String name, String profession,
                          double hunger, double energy, double health, double social,
                          String currentGoal, String villageRawId,
                          String dialogText,
                          String questId, String questType, String questDescription,
                          int questReward, int questTargetAmount, int questCurrentAmount) {
        this.entityId = entityId;
        this.name = name != null ? name : "NPC";
        this.profession = profession != null ? profession : "";
        this.hunger = hunger;
        this.energy = energy;
        this.health = health;
        this.social = social;
        this.currentGoal = currentGoal != null ? currentGoal : "WANDER";
        this.villageRawId = villageRawId != null ? villageRawId : "";
        this.dialogText = dialogText != null ? dialogText : "";
        this.questId = questId != null ? questId : "";
        this.questType = questType != null ? questType : "";
        this.questDescription = questDescription != null ? questDescription : "";
        this.questReward = questReward;
        this.questTargetAmount = questTargetAmount;
        this.questCurrentAmount = questCurrentAmount;
    }

    public NPCStatePacket(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
        this.name = buf.readUtf(64);
        this.profession = buf.readUtf(32);
        this.hunger = buf.readDouble();
        this.energy = buf.readDouble();
        this.health = buf.readDouble();
        this.social = buf.readDouble();
        this.currentGoal = buf.readUtf(32);
        this.villageRawId = buf.readUtf(64);
        this.dialogText = buf.readUtf(256);
        this.questId = buf.readUtf(64);
        this.questType = buf.readUtf(32);
        this.questDescription = buf.readUtf(256);
        this.questReward = buf.readVarInt();
        this.questTargetAmount = buf.readVarInt();
        this.questCurrentAmount = buf.readVarInt();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUtf(name, 64);
        buf.writeUtf(profession, 32);
        buf.writeDouble(hunger);
        buf.writeDouble(energy);
        buf.writeDouble(health);
        buf.writeDouble(social);
        buf.writeUtf(currentGoal, 32);
        buf.writeUtf(villageRawId, 64);
        buf.writeUtf(dialogText, 256);
        buf.writeUtf(questId, 64);
        buf.writeUtf(questType, 32);
        buf.writeUtf(questDescription, 256);
        buf.writeVarInt(questReward);
        buf.writeVarInt(questTargetAmount);
        buf.writeVarInt(questCurrentAmount);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // =========================================================================
    // Обработка
    // =========================================================================

    public static void handleClient(NPCStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                com.livemine.client.gui.NPCStatusScreen.open(
                    packet.entityId,
                    packet.name,
                    packet.profession,
                    packet.hunger,
                    packet.energy,
                    packet.health,
                    packet.social,
                    packet.currentGoal,
                    packet.villageRawId,
                    packet.dialogText,
                    packet.questId,
                    packet.questType,
                    packet.questDescription,
                    packet.questReward,
                    packet.questTargetAmount,
                    packet.questCurrentAmount
                );
            } catch (Exception e) {
                LiveMineMod.LOGGER.error("NPCStatePacket.handleClient failed", e);
            }
        });
    }

    // =========================================================================
    // Геттеры
    // =========================================================================

    public int getEntityId() { return entityId; }
    public String getName() { return name; }
    public String getProfession() { return profession; }
    public double getHunger() { return hunger; }
    public double getEnergy() { return energy; }
    public double getHealth() { return health; }
    public double getSocial() { return social; }
    public String getCurrentGoal() { return currentGoal; }
    public String getVillageRawId() { return villageRawId; }
    public String getDialogText() { return dialogText; }
    public String getQuestId() { return questId; }
    public String getQuestType() { return questType; }
    public String getQuestDescription() { return questDescription; }
    public int getQuestReward() { return questReward; }
    public int getQuestTargetAmount() { return questTargetAmount; }
    public int getQuestCurrentAmount() { return questCurrentAmount; }

    public boolean hasQuestOffer() {
        return questId != null && !questId.isEmpty();
    }
}