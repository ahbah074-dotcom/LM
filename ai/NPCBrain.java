package com.livemine.ai;

import com.livemine.LiveMineConfig;
import com.livemine.LiveMineSavedData;
import com.livemine.entity.LiveNPCEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/**
 * Мозг NPC — выбирает цель на основе потребностей.
 *
 * v2.0: убран внутренний gate по интервалу.
 * Интервал задаётся снаружи (LiveNPCEntity.tick).
 */
public final class NPCBrain {

    public enum GoalType {
        SURVIVAL, FOOD, SAFETY, HEALING, COMBAT, FLEE,
        WORK, LEARNING, SOCIAL, FAMILY, REST,
        ENTERTAINMENT, COLLECTIVE, BUILD, WANDER
    }

    private GoalType currentGoal = GoalType.WANDER;
    private long lastThinkTick = 0L;

    public void think(LiveNPCEntity npc, ServerLevel level) {
        long currentTick = level.getServer().getTickCount();
        lastThinkTick = currentTick;

        CompoundTag tag = LiveMineSavedData.get(level).loadNPCData(npc.getUUID());
        if (tag.isEmpty()) return;

        double health = tag.getDouble("health");
        double hunger = tag.getDouble("hunger");
        double energy = tag.getDouble("energy");
        double social = tag.getDouble("social");

        GoalType oldGoal = currentGoal;
        GoalType newGoal = selectGoal(npc, level, tag, health, hunger, energy, social);

        if (!isGoalReachable(npc, level, newGoal, tag)) {
            newGoal = GoalType.WANDER;
        }

        if (oldGoal != newGoal) {
            currentGoal = newGoal;
            tag.putString("current_goal", newGoal.name());
            LiveMineSavedData.get(level).saveNPCData(npc.getUUID(), tag);

            if (LiveMineConfig.brainDebug()) {
                BrainDebugger.logGoalChanged(npc, oldGoal.name(), newGoal.name(), "потребности");
            }
        }
    }

    private GoalType selectGoal(LiveNPCEntity npc, ServerLevel level, CompoundTag tag,
                                 double health, double hunger, double energy, double social) {
        if (health < 6.0) return GoalType.HEALING;
        if (hunger < 20.0) return GoalType.FOOD;

        if (isThreatNear(npc)) {
            return health < 6.0 ? GoalType.FLEE : GoalType.COMBAT;
        }

        if (energy < 15.0) return GoalType.REST;
        if (social < 20.0) return GoalType.SOCIAL;

        if (isWorkTime(level) && energy > 30 && hunger > 30) {
            return GoalType.WORK;
        }

        if (isEvening(level)) return GoalType.ENTERTAINMENT;

        return GoalType.WANDER;
    }

    private boolean isGoalReachable(LiveNPCEntity npc, ServerLevel level, GoalType goal, CompoundTag tag) {
        if (goal == GoalType.WANDER || goal == GoalType.FLEE
            || goal == GoalType.COMBAT || goal == GoalType.SURVIVAL) {
            return true;
        }

        String villageId = tag.getString("village_id");
        if (villageId.isEmpty()) {
            return goal == GoalType.WORK || goal == GoalType.REST;
        }

        var vd = new com.livemine.VillageData(villageId, level);
        if (!vd.exists()) return false;

        return switch (goal) {
            case REST -> !vd.getStructures(com.livemine.blocks.CommunalFirePitBlock.class).isEmpty()
                       || !vd.getStructures(com.livemine.blocks.StorageBlock.class).isEmpty();
            case HEALING -> !vd.getStructures(com.livemine.blocks.InfirmaryBlock.class).isEmpty()
                          || !vd.getStructures(com.livemine.blocks.CommunalFirePitBlock.class).isEmpty();
            default -> true;
        };
    }

    private boolean isThreatNear(LiveNPCEntity npc) {
        var attacker = npc.getLastHurtByMob();
        return attacker != null && attacker.isAlive()
            && npc.distanceTo(attacker) < 16.0;
    }

    private boolean villageNeedsWork(LiveNPCEntity npc, ServerLevel level) {
        String vid = npc.getVillageId() != null ? npc.getVillageId().toString() : "";
        if (vid.isEmpty()) return false;

        var vd = new com.livemine.VillageData("village_" + vid, level);
        if (!vd.exists()) return false;

        return vd.getNeedFood() > 50 || vd.getNeedWood() > 50 || vd.getNeedOre() > 50;
    }

    private boolean isWorkTime(ServerLevel level) {
        long dayTime = level.getDayTime() % 24000L;
        return dayTime >= 1000 && dayTime <= 11000;
    }

    private boolean isEvening(ServerLevel level) {
        long dayTime = level.getDayTime() % 24000L;
        return dayTime >= 12000 && dayTime <= 14000;
    }

    public GoalType getCurrentGoal() { return currentGoal; }

    public void setCurrentGoal(GoalType goal) {
        this.currentGoal = goal;
        this.lastThinkTick = 0L;
    }

    public String getCurrentGoalName() { return currentGoal.name(); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("current_goal", currentGoal.name());
        tag.putLong("last_think_tick", lastThinkTick);
        return tag;
    }

    public void load(CompoundTag tag) {
        if (tag.contains("current_goal")) {
            try {
                currentGoal = GoalType.valueOf(tag.getString("current_goal"));
            } catch (IllegalArgumentException e) {
                currentGoal = GoalType.WANDER;
            }
        }
        if (tag.contains("last_think_tick")) {
            lastThinkTick = tag.getLong("last_think_tick");
        }
    }
}