package com.livemine.ai.goals;

import com.livemine.LiveMineSavedData;
import com.livemine.ai.BrainDebugger;
import com.livemine.ai.NPCBrain;
import com.livemine.ai.NPCRegistry;
import com.livemine.ai.NPCRelationship;
import com.livemine.entity.LiveNPCEntity;
import com.livemine.navigation.NavigationSystem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.List;

public class SocializeGoal extends Goal {

    private static final int SOCIALIZE_DURATION_TICKS = 200;
    private static final int NAV_TIMEOUT_TICKS = 200;
    private static final double SOCIALIZE_SPEED = 0.4;
    private static final double SEARCH_RADIUS_SQ = 16 * 16;

    private final LiveNPCEntity npc;
    private NavigationSystem navigation;

    private LiveNPCEntity targetNpc;
    private int socializeTimer;
    private int navTimeout;
    private int talkCooldown;

    public SocializeGoal(LiveNPCEntity npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!NPCRegistry.isRegistered(npc.getUUID())) return false;
        if (!(npc.level() instanceof ServerLevel level)) return false;
        if (npc.getNpcBrain().getCurrentGoal() != NPCBrain.GoalType.SOCIAL) return false;

        CompoundTag tag = LiveMineSavedData.get(level).loadNPCData(npc.getUUID());
        if (tag.isEmpty()) return false;

        double social = tag.getDouble("social");
        if (social >= 80) return false;

        targetNpc = findNearbyNpc(level);
        return targetNpc != null;
    }

    @Override
    public void start() {
        if (navigation == null) navigation = new NavigationSystem(npc);
        socializeTimer = 0;
        navTimeout = 0;
        talkCooldown = 0;

        if (targetNpc != null) {
            navigation.navigateTo(targetNpc.blockPosition(), SOCIALIZE_SPEED);
            BrainDebugger.log(npc, "SOCIALIZE_START",
                "к " + targetNpc.getCustomNameTag());
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (npc.getNpcBrain().getCurrentGoal() != NPCBrain.GoalType.SOCIAL) return false;
        if (targetNpc == null || !targetNpc.isAlive()) return false;
        return socializeTimer < SOCIALIZE_DURATION_TICKS;
    }

    @Override
    public void tick() {
        if (navigation != null) navigation.tick();

        socializeTimer++;
        navTimeout++;

        if (targetNpc == null) return;

        double distSq = npc.blockPosition().distSqr(targetNpc.blockPosition());

        if (distSq < 9.0) {
            npc.getNavigation().stop();
            npc.getLookControl().setLookAt(targetNpc, 30.0f, 30.0f);

            if (talkCooldown > 0) { talkCooldown--; return; }

            NPCRelationship.onSocialize(npc, targetNpc);
            BrainDebugger.log(npc, "SOCIALIZE", "общается с " + targetNpc.getCustomNameTag());

            talkCooldown = 20;

            if (socializeTimer >= SOCIALIZE_DURATION_TICKS / 2) {
                BrainDebugger.logGoalReached(npc, "SOCIAL", "поговорили");
                socializeTimer = SOCIALIZE_DURATION_TICKS;
            }
            return;
        }

        if (navTimeout > NAV_TIMEOUT_TICKS) {
            BrainDebugger.logGoalFailed(npc, "SOCIALIZE", "не смог догнать");
            targetNpc = null;
        }
    }

    @Override
    public void stop() {
        if (navigation != null) navigation.stop();
        targetNpc = null;
        socializeTimer = 0;
        navTimeout = 0;
        talkCooldown = 0;
    }

    private LiveNPCEntity findNearbyNpc(ServerLevel level) {
        LiveNPCEntity best = null;
        double bestSq = SEARCH_RADIUS_SQ;

        List<Mob> all = NPCRegistry.getAll();
        for (Mob mob : all) {
            if (mob == npc) continue;
            if (mob.level() != level) continue;
            if (!(mob instanceof LiveNPCEntity other)) continue;
            if (!other.isAlive()) continue;

            double d = other.blockPosition().distSqr(npc.blockPosition());
            if (d < bestSq) { bestSq = d; best = other; }
        }
        return best;
    }
}