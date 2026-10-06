package com.livemine.network;

import com.livemine.LiveMineMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Регистрация сетевых пакетов.
 *
 * v2.2: + MarketStallSyncPacket, MarketStallBuyPacket.
 */
@EventBusSubscriber(modid = LiveMineMod.MOD_ID)
public final class NetworkHandler {

    public static final String PROTOCOL_VERSION = "1";

    private NetworkHandler() {}

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        // --- Существующие 3 ---
        registrar.playToClient(
            NPCStatePacket.TYPE,
            NPCStatePacket.STREAM_CODEC,
            NPCStatePacket::handleClient
        );

        registrar.playToServer(
            NPCInteractionPayload.TYPE,
            NPCInteractionPayload.STREAM_CODEC,
            NPCInteractionPayload::handleServer
        );

        registrar.playToClient(
            VillageClientCache.TYPE,
            VillageClientCache.STREAM_CODEC,
            VillageClientCache::handleClient
        );

        // --- v2.2: 2 новых для торговли ---
        registrar.playToClient(
            MarketStallSyncPacket.TYPE,
            MarketStallSyncPacket.STREAM_CODEC,
            MarketStallSyncPacket::handleClient
        );

        registrar.playToServer(
            MarketStallBuyPacket.TYPE,
            MarketStallBuyPacket.STREAM_CODEC,
            MarketStallBuyPacket::handleServer
        );

        LiveMineMod.LOGGER.info(
            "LiveMine: registered 5 network payloads (protocol {})", PROTOCOL_VERSION);
    }
}