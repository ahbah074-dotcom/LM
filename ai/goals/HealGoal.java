package com.livemine.ai.goals;

import com.livemine.LiveMineSavedData;
import com.livemine.ai.BrainDebugger;
import com.livemine.ai.NPCBrain;
import com.livemine.ai.NPCRegistry;
import com.livemine.entity.LiveNPCEntity;
import com.livemine.navigation.NavigationSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.List;

public class HealGoal extends Goal {

    private static final int HEAL_MAX_TICKS = 1200;
    private static final int NAV_TIMEOUT_TICKS = 300;
    private static final double HEAL_SPEED = 0.6;

    private final LiveNPCEntity npc;
    private NavigationSystem navigation;

    private BlockPos healPos;
    private boolean atInfirmary;
    private int healTimer;
    private int navTimeout;

    public HealGoal(LiveNPCEntity npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!NPCRegistry.isRegistered(npc.getUUID())) return false;
        if (!(npc.level() instanceof ServerLevel level)) return false;
        if (npc.getNpcBrain().getCurrentGoal() != NPCBrain.GoalType.HEALING) return false;

        CompoundTag tag = LiveMineSavedData.get(level).loadNPCData(npc.getUUID());
        if (tag.isEmpty()) return false;

        double health = tag.getDouble("health");
        if (health >= 20) return false;

        String vid = tag.getString("village_id");
        if (vid.isEmpty()) {
            return tryFindNearbyFirePit(level, npc.blockPosition());
        }

        var vd = new com.livemine.VillageData(vid, level);
        if (!vd.exists()) return false;

        List<BlockPos> infirmaries = vd.getStructures(com.livemine.blocks.InfirmaryBlock.class);
        if (!infirmaries.isEmpty()) {
            healPos = nearest(infirmaries, npc.blockPosition());
            atInfirmary = true;
            return healPos != null;
        }

        List<BlockPos> firePits = vd.getStructures(com.livemine.blocks.CommunalFirePitBlock.class);
        if (!firePits.isEmpty()) {
            healPos = nearest(firePits, npc.blockPosition());
            atInfirmary = false;
            return healPos != null;
        }

        return false;
    }

    @Override
    public void start() {
        if (navigation == null) navigation = new NavigationSystem(npc);
        healTimer = 0;
        navTimeout = 0;

        if (healPos != null) {
            navigation.navigateTo(healPos, HEAL_SPEED);
            BrainDebugger.log(npc, "HEAL_START",
                atInfirmary ? "к лазарету @" + healPos : "к костру (fallback) @" + healPos);
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (npc.getNpcBrain().getCurrentGoal() != NPCBrain.GoalType.HEALING) return false;
        if (healPos == null) return false;
        return healTimer < HEAL_MAX_TICKS;
    }

    @Override
    public void tick() {
        if (navigation != null) navigation.tick();

        healTimer++;
        navTimeout++;

        if (healPos == null) return;
        if (!(npc.level() instanceof ServerLevel level)) return;

        double distSq = npc.blockPosition().distSqr(healPos);

        if (distSq < 16.0) {
            npc.getNavigation().stop();

            CompoundTag tag = LiveMineSavedData.get(level).loadNPCData(npc.getUUID());
            if (tag.isEmpty()) return;

            double health = tag.getDouble("health");
            double healRate = atInfirmary ? 0.05 : 0.02;
            health = Math.min(20.0, health + healRate);
            tag.putDouble("health", health);
            LiveMineSavedData.get(level).saveNPCData(npc.getUUID(), tag);

            if (npc.getHealth() < health) {
                npc.setHealth((float) health);
            }

            if (health >= 20.0) {
                BrainDebugger.logGoalReached(npc, "HEAL", "здоровье восстановлено");
                healTimer = HEAL_MAX_TICKS;
            }
            return;
        }

        if (navTimeout > NAV_TIMEOUT_TICKS) {
            BrainDebugger.logGoalFailed(npc, "HEAL", "не смог дойти");
            healPos = null;
        }
    }

    @Override
    public void stop() {
        if (navigation != null) navigation.stop();
        healPos = null;
        atInfirmary = false;
        healTimer = 0;
        navTimeout = 0;
    }

    private boolean tryFindNearbyFirePit(ServerLevel level, BlockPos around) {
        var positions = com.livemine.ai.ResourceCache.getResources(
            level, around, 32, "fire_pit", level.getGameTime());
        if (positions.isEmpty()) return false;
        healPos = nearest(positions, around);
        atInfirmary = false;
        return healPos != null;
    }

    private static BlockPos nearest(List<BlockPos> list, BlockPos from) {
        if (list.isEmpty()) return null;
        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;
        for (BlockPos p : list) {
            double d = p.distSqr(from);
            if (d < bestSq) { bestSq = d; best = p; }
        }
        return best;
    }
}