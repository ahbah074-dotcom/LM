package com.livemine.network;

import com.livemine.LiveMineMod;
import com.livemine.blocks.MarketStallBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Пакет покупки лота (C→S).
 *
 * Клиент нажимает "Купить" → отправляет этот пакет.
 * Сервер выполняет транзакцию и отправляет обновлённый MarketStallSyncPacket.
 */
public final class MarketStallBuyPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MarketStallBuyPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(LiveMineMod.MOD_ID, "market_stall_buy")
        );

    public static final StreamCodec<FriendlyByteBuf, MarketStallBuyPacket> STREAM_CODEC =
        StreamCodec.ofMember(MarketStallBuyPacket::write, MarketStallBuyPacket::new);

    private final BlockPos pos;
    private final int slotIndex;

    public MarketStallBuyPacket(BlockPos pos, int slotIndex) {
        this.pos = pos;
        this.slotIndex = slotIndex;
    }

    public MarketStallBuyPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.slotIndex = buf.readVarInt();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(slotIndex);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(BlockPos pos, int slotIndex) {
        var conn = net.minecraft.client.Minecraft.getInstance().getConnection();
        if (conn != null) {
            conn.send(new MarketStallBuyPacket(pos, slotIndex));
        }
    }

    public static void handleServer(MarketStallBuyPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!(player.level() instanceof ServerLevel level)) return;

            BlockPos pos = packet.pos;
            var be = level.getBlockEntity(pos);
            if (!(be instanceof MarketStallBlockEntity stall)) return;

            boolean ok = stall.tryPurchase(packet.slotIndex, player);
            if (ok) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§aПокупка успешна"));
            } else {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§cНе удалось купить (нет товара или недостаточно ресурсов)"));
            }

            // Отправляем обновлённые данные обратно.
            var syncPacket = stall.buildSyncPacket();
            player.connection.send(syncPacket);
        });
    }

    public BlockPos getPos() { return pos; }
    public int getSlotIndex() { return slotIndex; }
}