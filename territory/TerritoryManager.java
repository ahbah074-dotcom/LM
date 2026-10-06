package com.livemine.territory;

import com.livemine.LiveMineMod;
import com.livemine.VillageManager;
import com.livemine.domain.VillageRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Менеджер территорий и границ.
 *
 * v2.0: отслеживает игроков, вошедших на территорию деревни.
 *       При входе/выходе — сообщение в чат (если есть деревня).
 */
public final class TerritoryManager {

    private static TerritoryManager INSTANCE;

    /** player → lastKnownVillageId (rawId), где игрок сейчас. */
    private final Map<UUID, String> playerTerritory = new HashMap<>();

    private TerritoryManager() {}

    public static synchronized TerritoryManager getInstance() {
        if (INSTANCE == null) INSTANCE = new TerritoryManager();
        return INSTANCE;
    }

    // =========================================================================
    // Определение владельца
    // =========================================================================

    public VillageRecord getOwner(ServerLevel level, int x, int z) {
        for (VillageRecord village : VillageManager.getInstance().getAllVillages()) {
            if (village.isAbandoned()) continue;

            int dx = x - village.centerX();
            int dz = z - village.centerZ();
            int r = village.radius();
            if (dx * dx + dz * dz <= r * r) {
                return village;
            }
        }
        return null;
    }

    public VillageRecord getOwner(ServerLevel level, BlockPos pos) {
        return getOwner(level, pos.getX(), pos.getZ());
    }

    // =========================================================================
    // v2.0: Тик — отслеживание входа/выхода игроков
    // =========================================================================

    /**
     * Вызывать раз в 20 тиков (1 сек).
     */
    public void tick(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            UUID pid = player.getUUID();
            BlockPos pos = player.blockPosition();

            VillageRecord owner = getOwner(level, pos);
            String current = owner != null ? owner.rawId() : "";
            String previous = playerTerritory.getOrDefault(pid, "");

            if (!current.equals(previous)) {
                playerTerritory.put(pid, current);

                if (!current.isEmpty() && owner != null) {
                    // Вошёл на территорию.
                    player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal(
                            "§6[Территория]§r §f" + owner.name()
                                + " §7(население: " + owner.population() + ")"),
                        true
                    );
                } else if (!previous.isEmpty()) {
                    // Покинул территорию.
                    player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal(
                            "§7[Вы покинули территорию деревни]"),
                        true
                    );
                }
            }
        }
    }

    // =========================================================================
    // Проверки
    // =========================================================================

    public boolean isInside(ServerLevel level, int x, int z, UUID villageId) {
        VillageRecord owner = getOwner(level, x, z);
        return owner != null && owner.id().equals(villageId);
    }

    public boolean isViolation(ServerLevel level, int x, int z, UUID visitorVillage) {
        VillageRecord owner = getOwner(level, x, z);
        if (owner == null) return false;
        if (owner.id().equals(visitorVillage)) return false;

        if (visitorVillage == null) {
            return false;
        }

        var dip = com.livemine.diplomacy.DiplomacyManager.getInstance();
        if (dip.allied(owner.id(), visitorVillage)) return false;
        if (dip.atWar(owner.id(), visitorVillage)) return true;

        return false;
    }

    public int distanceToNearestBorder(ServerLevel level, int x, int z) {
        int minDist = Integer.MAX_VALUE;
        for (VillageRecord v : VillageManager.getInstance().getAllVillages()) {
            if (v.isAbandoned()) continue;
            double dist = Math.sqrt(
                Math.pow(x - v.centerX(), 2) + Math.pow(z - v.centerZ(), 2));
            int distToBorder = (int) Math.abs(dist - v.radius());
            if (distToBorder < minDist) minDist = distToBorder;
        }
        return minDist == Integer.MAX_VALUE ? -1 : minDist;
    }

    // =========================================================================
    // Диагностика
    // =========================================================================

    public String getPlayerTerritory(UUID playerId) {
        return playerTerritory.getOrDefault(playerId, "");
    }

    public int getTotalTerritoryCount() {
        return VillageManager.getInstance().getVillageCount();
    }

    public void clear() {
        playerTerritory.clear();
    }
}