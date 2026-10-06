package com.livemine.domain;

import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Арбитр навигации.
 *
 * Управляет арендой точек навигации: если два NPC хотят занять одну
 * и ту же точку — второй получает отказ.
 */
public final class NavigationArbiter {

    public static final long DEFAULT_TTL_TICKS = 200L;
    public static final int MAX_CONCURRENT = 50;
    public static final int MAX_PER_POINT = 1;

    private static NavigationArbiter INSTANCE;

    private final Map<UUID, NavigationLease> leases = new ConcurrentHashMap<>();
    private final Map<BlockPos, UUID> pointReservations = new ConcurrentHashMap<>();

    private NavigationArbiter() {}

    public static synchronized NavigationArbiter getInstance() {
        if (INSTANCE == null) INSTANCE = new NavigationArbiter();
        return INSTANCE;
    }

    // =========================================================================
    // Аренда
    // =========================================================================

    public synchronized NavigationLease tryAcquire(UUID agentId, BlockPos pos, long currentTick) {
        if (agentId == null || pos == null) return null;

        expire(currentTick);

        if (leases.size() >= MAX_CONCURRENT) {
            return null;
        }

        BlockPos immutable = pos.immutable();
        UUID existing = pointReservations.get(immutable);

        if (existing != null) {
            NavigationLease lease = leases.get(existing);
            if (lease != null && lease.isActive(currentTick)) {
                if (lease.agentId().equals(agentId)) {
                    return lease;
                }
                return null;
            }
            pointReservations.remove(immutable);
            leases.remove(existing);
        }

        releaseAgent(agentId);

        NavigationLease lease = new NavigationLease(agentId, immutable, currentTick, DEFAULT_TTL_TICKS);
        leases.put(lease.id(), lease);
        pointReservations.put(immutable, lease.id());

        return lease;
    }

    // =========================================================================
    // Освобождение
    // =========================================================================

    public synchronized void release(UUID leaseId) {
        if (leaseId == null) return;
        NavigationLease lease = leases.remove(leaseId);
        if (lease != null) {
            pointReservations.remove(lease.target());
        }
    }

    public synchronized void releaseAgent(UUID agentId) {
        if (agentId == null) return;
        leases.values().removeIf(lease -> {
            if (lease.agentId().equals(agentId)) {
                pointReservations.remove(lease.target());
                return true;
            }
            return false;
        });
    }

    // =========================================================================
    // Продвижение аренды
    // =========================================================================

    public synchronized void refresh(UUID leaseId, long currentTick) {
        NavigationLease lease = leases.get(leaseId);
        if (lease != null) {
            lease.refresh(currentTick);
        }
    }

    // =========================================================================
    // Очистка истёкших
    // =========================================================================

    public synchronized void expire(long currentTick) {
        leases.values().removeIf(lease -> {
            if (!lease.isActive(currentTick)) {
                pointReservations.remove(lease.target());
                return true;
            }
            return false;
        });
    }

    // =========================================================================
    // Запросы
    // =========================================================================

    public synchronized int activeCount() {
        return leases.size();
    }

    public synchronized boolean isReserved(BlockPos pos, long currentTick) {
        if (pos == null) return false;
        UUID id = pointReservations.get(pos.immutable());
        if (id == null) return false;
        NavigationLease lease = leases.get(id);
        return lease != null && lease.isActive(currentTick);
    }

    public synchronized NavigationLease getLease(UUID leaseId) {
        return leases.get(leaseId);
    }

    public synchronized NavigationLease getLeaseForAgent(UUID agentId) {
        for (NavigationLease lease : leases.values()) {
            if (lease.agentId().equals(agentId) && lease.isActive(Long.MAX_VALUE)) {
                return lease;
            }
        }
        return null;
    }

    // =========================================================================
    // Очистка
    // =========================================================================

    public synchronized void clear() {
        leases.clear();
        pointReservations.clear();
    }

    public synchronized String describe() {
        return "NavigationArbiter{leases=" + leases.size()
            + ", points=" + pointReservations.size() + "}";
    }
}