package com.livemine.network;

import com.livemine.LiveMineMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Пакет синхронизации торговой лавки (S→C).
 *
 * Сервер при ПКМ по MarketStall отправляет этот пакет.
 * Клиент открывает MarketStallScreen и показывает список.
 */
public final class MarketStallSyncPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MarketStallSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(LiveMineMod.MOD_ID, "market_stall_sync")
        );

    public static final StreamCodec<FriendlyByteBuf, MarketStallSyncPacket> STREAM_CODEC =
        StreamCodec.ofMember(MarketStallSyncPacket::write, MarketStallSyncPacket::new);

    // =========================================================================
    // Вложенный TradeView
    // =========================================================================
    public static final class TradeView {
        public final String offerName;
        public final int offerCount;
        public final String priceName;
        public final int priceCount;
        public final int stock;
        public final double demand;

        public TradeView(String offerName, int offerCount,
                         String priceName, int priceCount,
                         int stock, double demand) {
            this.offerName = offerName != null ? offerName : "";
            this.offerCount = offerCount;
            this.priceName = priceName != null ? priceName : "";
            this.priceCount = priceCount;
            this.stock = stock;
            this.demand = demand;
        }
    }

    // =========================================================================
    // Поля
    // =========================================================================
    private final BlockPos pos;
    private final List<TradeView> trades;

    public MarketStallSyncPacket(BlockPos pos, List<TradeView> trades) {
        this.pos = pos;
        this.trades = trades != null ? trades : Collections.emptyList();
    }

    public MarketStallSyncPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        int count = buf.readVarInt();
        List<TradeView> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String offer = buf.readUtf(64);
            int offerCount = buf.readVarInt();
            String price = buf.readUtf(64);
            int priceCount = buf.readVarInt();
            int stock = buf.readVarInt();
            double demand = buf.readDouble();
            list.add(new TradeView(offer, offerCount, price, priceCount, stock, demand));
        }
        this.trades = list;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(trades.size());
        for (TradeView t : trades) {
            buf.writeUtf(t.offerName, 64);
            buf.writeVarInt(t.offerCount);
            buf.writeUtf(t.priceName, 64);
            buf.writeVarInt(t.priceCount);
            buf.writeVarInt(t.stock);
            buf.writeDouble(t.demand);
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public BlockPos getPos() { return pos; }
    public List<TradeView> getTrades() { return Collections.unmodifiableList(trades); }

    // =========================================================================
    // Обработка на клиенте
    // =========================================================================

    public static void handleClient(MarketStallSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                com.livemine.client.gui.MarketStallScreen.open(packet.pos, packet.trades);
            } catch (Exception e) {
                LiveMineMod.LOGGER.error("MarketStallSyncPacket.handleClient failed", e);
            }
        });
    }
}