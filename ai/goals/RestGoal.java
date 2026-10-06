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

public class RestGoal extends Goal {

    private static final int REST_MAX_TICKS = 600;
    private static final int NAV_TIMEOUT_TICKS = 300;
    private static final double REST_SPEED = 0.5;

    private final LiveNPCEntity npc;
    private NavigationSystem navigation;

    private BlockPos firePitPos;
    private int restTimer;
    private int navTimeout;

    public RestGoal(LiveNPCEntity npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!NPCRegistry.isRegistered(npc.getUUID())) return false;
        if (!(npc.level() instanceof ServerLevel level)) return false;
        if (npc.getNpcBrain().getCurrentGoal() != NPCBrain.GoalType.REST) return false;

        CompoundTag tag = LiveMineSavedData.get(level).loadNPCData(npc.getUUID());
        if (tag.isEmpty()) return false;

        double energy = tag.getDouble("energy");
        if (energy >= 80) return false;

        String vid = tag.getString("village_id");
        if (vid.isEmpty()) return false;

        var vd = new com.livemine.VillageData(vid, level);
        if (!vd.exists()) return false;

        List<BlockPos> firePits = vd.getStructures(com.livemine.blocks.CommunalFirePitBlock.class);
        if (firePits.isEmpty()) return false;

        firePitPos = nearest(firePits, npc.blockPosition());
        return firePitPos != null;
    }

    @Override
    public void start() {
        if (navigation == null) navigation = new NavigationSystem(npc);
        restTimer = 0;
        navTimeout = 0;

        if (firePitPos != null) {
            navigation.navigateTo(firePitPos, REST_SPEED);
            BrainDebugger.log(npc, "REST_START", "к костру @ " + firePitPos);
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (npc.getNpcBrain().getCurrentGoal() != NPCBrain.GoalType.REST) return false;
        if (firePitPos == null) return false;
        return restTimer < REST_MAX_TICKS;
    }

    @Override
    public void tick() {
        if (navigation != null) navigation.tick();

        restTimer++;
        navTimeout++;

        if (firePitPos == null) return;
        if (!(npc.level() instanceof ServerLevel level)) return;

        double distSq = npc.blockPosition().distSqr(firePitPos);

        if (distSq < 16.0) {
            npc.getNavigation().stop();

            CompoundTag tag = LiveMineSavedData.get(level).loadNPCData(npc.getUUID());
            if (tag.isEmpty()) return;

            double energy = tag.getDouble("energy");
            energy = Math.min(100, energy + 0.05);
            tag.putDouble("energy", energy);
            LiveMineSavedData.get(level).saveNPCData(npc.getUUID(), tag);

            if (energy >= 100) {
                BrainDebugger.logGoalReached(npc, "REST", "энергия восстановлена");
                restTimer = REST_MAX_TICKS;
            }
            return;
        }

        if (navTimeout > NAV_TIMEOUT_TICKS) {
            BrainDebugger.logGoalFailed(npc, "REST", "не смог дойти до костра");
            firePitPos = null;
        }
    }

    @Override
    public void stop() {
        if (navigation != null) navigation.stop();
        firePitPos = null;
        restTimer = 0;
        navTimeout = 0;
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