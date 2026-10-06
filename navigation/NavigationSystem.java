package com.livemine.navigation;

import com.livemine.ai.PathCache;
import com.livemine.domain.NavigationArbiter;
import com.livemine.domain.NavigationLease;
import com.livemine.entity.LiveNPCEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.pathfinder.Path;

/**
 * Обёртка навигации для одного NPC.
 *
 * Использует:
 *   - NavigationArbiter — блокировка точки от других NPC
 *   - PathCache         — переиспользование пути
 *   - Minecraft PathNavigation — фактическое движение
 *
 * ВАЖНО для 1.21.1:
 *   PathNavigation.moveTo(...) возвращает boolean, а не Path.
 *   Чтобы получить проложенный путь — используем getPath().
 */
public final class NavigationSystem {

    private static final int RETRY_DELAY_TICKS = 20;
    private static final double DEFAULT_SPEED = 1.0;

    private final LiveNPCEntity owner;
    private final NavigationArbiter arbiter;

    private NavigationLease currentLease;
    private BlockPos pendingTarget;
    private int pendingCooldown;

    public NavigationSystem(LiveNPCEntity owner) {
        this.owner = owner;
        this.arbiter = NavigationArbiter.getInstance();
    }

    // =========================================================================
    // Навигация
    // =========================================================================

    public boolean navigateTo(BlockPos target, double speed) {
        if (target == null) return false;
        if (!(owner.level() instanceof ServerLevel level)) return false;

        long currentTick = level.getGameTime();

        // Уже идём к этой цели?
        if (currentLease != null && currentLease.isActive(currentTick)
            && currentLease.target().equals(target)) {
            currentLease.refresh(currentTick);
            return true;
        }

        // Пытаемся получить аренду
        NavigationLease lease = arbiter.tryAcquire(owner.getUUID(), target, currentTick);

        if (lease == null) {
            pendingTarget = target.immutable();
            pendingCooldown = RETRY_DELAY_TICKS;
            return false;
        }

        // Освобождаем предыдущую, если была
        if (currentLease != null) {
            arbiter.release(currentLease.id());
        }
        currentLease = lease;

        // Проверяем PathCache
        Path cached = PathCache.get(owner.blockPosition(), target, currentTick);

        if (cached != null) {
            // 1.21.1: moveTo(Path, double) возвращает boolean
            boolean moved = owner.getNavigation().moveTo(cached, speed);
            if (moved) return true;
            // Не удалось — освобождаем и пробуем заново построить
            arbiter.release(currentLease.id());
            currentLease = null;
            return false;
        }

        // Строим новый путь
        // 1.21.1: moveTo(x, y, z, speed) возвращает boolean
        boolean moved = owner.getNavigation().moveTo(
            target.getX() + 0.5,
            target.getY(),
            target.getZ() + 0.5,
            speed
        );

        if (moved) {
            // Достаём фактически построенный путь из навигации
            Path newPath = owner.getNavigation().getPath();
            if (newPath != null) {
                PathCache.put(owner.blockPosition(), target, newPath, currentTick);
            }
            return true;
        }

        // Путь не найден — освобождаем аренду
        arbiter.release(currentLease.id());
        currentLease = null;
        return false;
    }

    public boolean navigateTo(BlockPos target) {
        return navigateTo(target, DEFAULT_SPEED);
    }

    // =========================================================================
    // Тик
    // =========================================================================

    public void tick() {
        if (!(owner.level() instanceof ServerLevel level)) return;
        long currentTick = level.getGameTime();

        if (currentLease != null) {
            if (owner.getNavigation().isInProgress()) {
                currentLease.refresh(currentTick);
            } else {
                arbiter.release(currentLease.id());
                currentLease = null;
            }
        }

        if (pendingTarget != null) {
            if (pendingCooldown > 0) {
                pendingCooldown--;
            } else {
                if (navigateTo(pendingTarget)) {
                    pendingTarget = null;
                } else {
                    pendingCooldown = RETRY_DELAY_TICKS;
                }
            }
        }
    }

    // =========================================================================
    // Остановка
    // =========================================================================

    public void stop() {
        owner.getNavigation().stop();

        if (currentLease != null) {
            arbiter.release(currentLease.id());
            currentLease = null;
        }
        pendingTarget = null;
        pendingCooldown = 0;
    }

    // =========================================================================
    // Проверки
    // =========================================================================

    public boolean hasPendingRequest() {
        return pendingTarget != null;
    }

    public boolean isNavigating() {
        return owner.getNavigation().isInProgress();
    }

    public boolean hasLease() {
        if (!(owner.level() instanceof ServerLevel level)) return false;
        return currentLease != null && currentLease.isActive(level.getGameTime());
    }

    public BlockPos currentTarget() {
        return currentLease != null ? currentLease.target() : null;
    }

    public NavigationLease currentLease() {
        return currentLease;
    }

    @Override
    public String toString() {
        return "NavigationSystem{" + (owner.getCustomNameTag())
            + ", navigating=" + isNavigating()
            + ", target=" + currentTarget() + "}";
    }
}