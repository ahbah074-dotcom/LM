package com.livemine.network;

import com.livemine.LiveMineMod;
import com.livemine.LiveMineSavedData;
import com.livemine.dialogue.DialogSystem;
import com.livemine.entity.LiveNPCEntity;
import com.livemine.quest.QuestManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Пакет клика игрока по NPC (C→S).
 *
 * v2.0: добавлены действия ACCEPT_QUEST, DECLINE_QUEST, REFRESH.
 */
public final class NPCInteractionPayload implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<NPCInteractionPayload> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(LiveMineMod.MOD_ID, "npc_interaction")
        );

    public static final StreamCodec<FriendlyByteBuf, NPCInteractionPayload> STREAM_CODEC =
        StreamCodec.ofMember(NPCInteractionPayload::write, NPCInteractionPayload::new);

    // =========================================================================
    // Действия
    // =========================================================================
    public enum Action {
        OPEN,
        ACCEPT_QUEST,
        DECLINE_QUEST,
        REFRESH
    }

    private final int entityId;
    private final Action action;
    private final String questId;

    public NPCInteractionPayload(int entityId, Action action, String questId) {
        this.entityId = entityId;
        this.action = action != null ? action : Action.OPEN;
        this.questId = questId != null ? questId : "";
    }

    public NPCInteractionPayload(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
        int a = buf.readVarInt();
        this.action = Action.values()[Math.max(0, Math.min(Action.values().length - 1, a))];
        this.questId = buf.readUtf(64);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(action.ordinal());
        buf.writeUtf(questId, 64);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // =========================================================================
    // Отправка с клиента
    // =========================================================================

    public static void send(int entityId) {
        send(entityId, Action.OPEN, "");
    }

    public static void send(int entityId, Action action, String questId) {
        var conn = net.minecraft.client.Minecraft.getInstance().getConnection();
        if (conn != null) {
            conn.send(new NPCInteractionPayload(entityId, action, questId));
        }
    }

    // =========================================================================
    // Обработка на сервере
    // =========================================================================

    public static void handleServer(NPCInteractionPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            ServerLevel level = player.serverLevel();
            Entity entity = level.getEntity(packet.entityId);
            if (!(entity instanceof LiveNPCEntity npc)) return;

            try {
                switch (packet.action) {
                    case OPEN, REFRESH -> sendStatePacket(npc, player, level);
                    case ACCEPT_QUEST -> {
                        UUID qid = parseUuid(packet.questId);
                        if (qid != null) {
                            QuestManager.getInstance().acceptByPlayer(player, qid);
                        }
                        sendStatePacket(npc, player, level);
                    }
                    case DECLINE_QUEST -> {
                        UUID qid = parseUuid(packet.questId);
                        if (qid != null) {
                            QuestManager.getInstance().declineByPlayer(player, qid);
                        }
                        sendStatePacket(npc, player, level);
                    }
                }
            } catch (Exception e) {
                LiveMineMod.LOGGER.error("NPCInteractionPayload.handleServer failed", e);
            }
        });
    }

    private static UUID parseUuid(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Собирает и отправляет NPCStatePacket с полным состоянием NPC.
     */
    private static void sendStatePacket(LiveNPCEntity npc, ServerPlayer player, ServerLevel level) {
        var tag = LiveMineSavedData.get(level).loadNPCData(npc.getUUID());

        // Диалог.
        String dialog = DialogSystem.generate(npc, player, level);

        // Квест.
        String qId = "";
        String qType = "";
        String qDesc = "";
        int qReward = 0;
        int qTarget = 0;
        int qCurrent = 0;

        try {
            var pending = QuestManager.getInstance()
                .getPendingOfferForPlayer(player.getUUID(), npc.getUUID(), level);

            if (pending == null) {
                // Пробуем сгенерировать новый по нуждам деревни.
                long day = level.getDayTime() / 24000L;
                pending = QuestManager.getInstance()
                    .offerForPlayer(npc, player, level, day);
            }

            if (pending != null) {
                qId = pending.questId.toString();
                qType = pending.type.ruName;
                qDesc = pending.description;
                qReward = pending.reputationReward;
                qTarget = pending.targetAmount;
                qCurrent = pending.currentAmount;
            }
        } catch (Exception e) {
            LiveMineMod.LOGGER.error("Quest offer failed", e);
        }

        NPCStatePacket response = new NPCStatePacket(
            npc.getId(),
            npc.getCustomNameTag(),
            npc.getProfessionName(),
            tag.getDouble("hunger"),
            tag.getDouble("energy"),
            tag.getDouble("health"),
            tag.getDouble("social"),
            npc.getNpcBrain().getCurrentGoalName(),
            npc.getVillageRawId(),
            dialog,
            qId, qType, qDesc, qReward, qTarget, qCurrent
        );

        player.connection.send(response);
    }

    public int getEntityId() { return entityId; }
    public Action getAction() { return action; }
    public String getQuestId() { return questId; }
}