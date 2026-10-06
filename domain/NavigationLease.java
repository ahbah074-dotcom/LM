package com.livemine.domain;

import net.minecraft.core.BlockPos;

import java.util.UUID;

/**
 * Аренда точки навигации.
 *
 * Выдаётся NavigationArbiter'ом агенту (NPC) на время движения к цели.
 * TTL по умолчанию — 200 тиков (10 сек).
 * Продлевается через refresh() пока NPC движется.
 */
public final class NavigationLease {

    public static final long DEFAULT_TTL_TICKS = 200L;

    private final UUID id;
    private final UUID agentId;
    private final BlockPos target;
    private final long issuedTick;
    private final long ttlTicks;

    private long lastRefreshTick;
    private volatile boolean active = true;

    public NavigationLease(UUID agentId, BlockPos target, long issuedTick, long ttlTicks) {
        this.id = UUID.randomUUID();
        this.agentId = agentId;
        this.target = target.immutable();
        this.issuedTick = issuedTick;
        this.lastRefreshTick = issuedTick;
        this.ttlTicks = ttlTicks > 0 ? ttlTicks : DEFAULT_TTL_TICKS;
    }

    public NavigationLease(UUID agentId, BlockPos target, long issuedTick) {
        this(agentId, target, issuedTick, DEFAULT_TTL_TICKS);
    }

    // =========================================================================
    // Геттеры
    // =========================================================================

    public UUID id() { return id; }
    public UUID agentId() { return agentId; }
    public BlockPos target() { return target; }
    public long issuedTick() { return issuedTick; }
    public long lastRefreshTick() { return lastRefreshTick; }
    public long ttlTicks() { return ttlTicks; }
    public boolean isReleased() { return !active; }

    // =========================================================================
    // Активность
    // =========================================================================

    public boolean isActive(long currentTick) {
        if (!active) return false;
        if (currentTick - lastRefreshTick > ttlTicks) {
            active = false;
            return false;
        }
        return true;
    }

    public synchronized void refresh(long currentTick) {
        if (active) {
            this.lastRefreshTick = currentTick;
        }
    }

    public void release() {
        active = false;
    }

    public long remainingTicks(long currentTick) {
        if (!active) return 0;
        return Math.max(0, ttlTicks - (currentTick - lastRefreshTick));
    }

    @Override
    public String toString() {
        return "NavigationLease{id=" + id.toString().substring(0, 8)
            + ", agent=" + (agentId != null ? agentId.toString().substring(0, 8) : "null")
            + ", target=" + target + ", active=" + active + "}";
    }
}