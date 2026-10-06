package com.livemine.ai.goals;

import com.livemine.ai.NPCBrain;
import com.livemine.ai.NPCRegistry;
import com.livemine.entity.LiveNPCEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Random;

public class WanderGoal extends Goal {

    private static final int SEARCH_RADIUS = 32;
    private static final int MIN_COOLDOWN = 100;
    private static final int MAX_COOLDOWN = 300;
    private static final double WANDER_SPEED = 0.4;

    private final LiveNPCEntity npc;
    private final Random random = new Random();

    private Vec3 wanderTarget;
    private int wanderCooldown;
    private int navTimeout;
    private int stuckTicks;

    public WanderGoal(LiveNPCEntity npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!NPCRegistry.isRegistered(npc.getUUID())) return false;
        if (npc.getNpcBrain().getCurrentGoal() != NPCBrain.GoalType.WANDER) return false;

        if (wanderCooldown > 0) {
            wanderCooldown--;
            return false;
        }

        wanderTarget = findWanderTarget();
        return wanderTarget != null;
    }

    @Override
    public void start() {
        navTimeout = 0;
        stuckTicks = 0;
        if (wanderTarget != null) {
            npc.getNavigation().moveTo(
                wanderTarget.x, wanderTarget.y, wanderTarget.z, WANDER_SPEED);
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (npc.getNpcBrain().getCurrentGoal() != NPCBrain.GoalType.WANDER) return false;
        if (wanderTarget == null) return false;
        if (navTimeout > 400) return false;
        return !npc.getNavigation().isDone();
    }

    @Override
    public void tick() {
        navTimeout++;

        if (npc.getNavigation().isInProgress()
            && npc.getDeltaMovement().horizontalDistanceSqr() < 0.001) {
            stuckTicks++;
            if (stuckTicks > 60) {
                wanderTarget = findWanderTarget();
                stuckTicks = 0;
                if (wanderTarget != null) {
                    npc.getNavigation().moveTo(
                        wanderTarget.x, wanderTarget.y, wanderTarget.z, WANDER_SPEED);
                } else {
                    stop();
                }
            }
        } else {
            stuckTicks = 0;
        }
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
        wanderTarget = null;
        wanderCooldown = MIN_COOLDOWN + random.nextInt(MAX_COOLDOWN - MIN_COOLDOWN);
    }

    private Vec3 findWanderTarget() {
        if (!(npc.level() instanceof ServerLevel level)) return null;

        BlockPos origin = npc.blockPosition();

        for (int attempt = 0; attempt < 10; attempt++) {
            int dx = random.nextInt(SEARCH_RADIUS * 2 + 1) - SEARCH_RADIUS;
            int dz = random.nextInt(SEARCH_RADIUS * 2 + 1) - SEARCH_RADIUS;
            int dy = random.nextInt(9) - 4;

            BlockPos target = origin.offset(dx, dy, dz);

            if (isSafeWander(level, target)) {
                return new Vec3(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
            }
        }
        return null;
    }

    private boolean isSafeWander(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir()
            && level.getBlockState(pos.above()).isAir()
            && !level.getBlockState(pos.below()).isAir();
    }
}