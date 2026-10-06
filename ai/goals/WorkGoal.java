package com.livemine.ai.goals;

import com.livemine.NPCSkills;
import com.livemine.LiveMineSavedData;
import com.livemine.ai.BrainDebugger;
import com.livemine.ai.NPCBrain;
import com.livemine.ai.NPCRegistry;
import com.livemine.ai.WorkGoalManager;
import com.livemine.building.BuildingSystem;
import com.livemine.crafting.CookingManager;
import com.livemine.crafting.CraftManager;
import com.livemine.entity.LiveNPCEntity;
import com.livemine.navigation.NavigationSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

/**
 * Цель работы NPC.
 *
 * v4.0:
 *   - ФОЛЛБЕК: если ресурсы рядом не найдены — ищем дерево/камень/траву в радиусе 32.
 *   - Расширенный радиус ResourceCache (48 блоков).
 *   - NPC всегда работают, если есть хоть что-то вокруг.
 */
public class WorkGoal extends Goal {

    private static final int WORK_CYCLE_TICKS = 200;
    private static final int NAV_TIMEOUT_TICKS = 300;
    private static final double WORK_SPEED = 0.5;
    private static final float CRAFT_CHANCE = 0.20f;
    private static final int SEARCH_RADIUS = 48;
    private static final int FALLBACK_RADIUS = 32;

    private final LiveNPCEntity npc;
    private NavigationSystem navigation;

    private BlockPos targetPos;
    private WorkGoalManager.WorkType workType;
    private int workTimer;
    private int navTimeout;
    private int actionCooldown;

    public WorkGoal(LiveNPCEntity npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (!NPCRegistry.isRegistered(npc.getUUID())) return false;
        if (!(npc.level() instanceof ServerLevel level)) return false;
        if (npc.getNpcBrain().getCurrentGoal() != NPCBrain.GoalType.WORK) return false;

        var tag = LiveMineSavedData.get(level).loadNPCData(npc.getUUID());
        if (tag.isEmpty()) return false;

        double energy = tag.getDouble("energy");
        double hunger = tag.getDouble("hunger");
        if (energy < 15 || hunger < 10) return false;

        workType = WorkGoalManager.selectBestWork(npc, level);
        if (workType == null) workType = WorkGoalManager.WorkType.MINING;

        // 1. Ищем целевой ресурс через ResourceCache (48 блоков).
        targetPos = WorkGoalManager.findWorkTarget(npc, level, workType);

        // 2. v4.0: ФОЛЛБЕК — ищем любое дерево/камень вокруг.
        if (targetPos == null) {
            targetPos = findFallbackTarget(level);
        }

        return targetPos != null;
    }

    /**
     * v4.0: фоллбек — ищет ближайшее дерево/камень/траву в радиусе.
     */
    private BlockPos findFallbackTarget(ServerLevel level) {
        BlockPos origin = npc.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        // Сканируем куб 32×16×32.
        for (int dx = -FALLBACK_RADIUS; dx <= FALLBACK_RADIUS; dx += 2) {
            for (int dz = -FALLBACK_RADIUS; dz <= FALLBACK_RADIUS; dz += 2) {
                for (int dy = -4; dy <= 6; dy += 2) {
                    BlockPos p = origin.offset(dx, dy, dz);
                    BlockState st = level.getBlockState(p);
                    if (st.isAir()) continue;

                    // Что годится?
                    boolean good = st.is(BlockTags.LOGS)
                        || st.is(BlockTags.LEAVES)
                        || st.is(Blocks.STONE)
                        || st.is(Blocks.COBBLESTONE)
                        || st.is(Blocks.DIRT)
                        || st.is(Blocks.GRASS_BLOCK)
                        || st.is(BlockTags.COAL_ORES)
                        || st.is(BlockTags.IRON_ORES)
                        || st.getBlock() instanceof CropBlock;

                    if (!good) continue;

                    double d = p.distSqr(origin);
                    if (d < bestDist) {
                        bestDist = d;
                        best = p;
                    }
                }
            }
        }

        if (best != null) {
            BrainDebugger.log(npc, "FALLBACK", "ищет ресурс @ " + best);
        }
        return best;
    }

    @Override
    public void start() {
        if (navigation == null) navigation = new NavigationSystem(npc);
        workTimer = 0;
        navTimeout = 0;
        actionCooldown = 0;

        if (targetPos != null) {
            navigation.navigateTo(targetPos, WORK_SPEED);
            BrainDebugger.log(npc, "WORK_START", workType + " -> " + targetPos);
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (npc.getNpcBrain().getCurrentGoal() != NPCBrain.GoalType.WORK) return false;
        if (targetPos == null) return false;
        return workTimer < WORK_CYCLE_TICKS;
    }

    @Override
    public void tick() {
        if (navigation != null) navigation.tick();

        workTimer++;
        navTimeout++;

        if (targetPos == null) return;

        double distSq = npc.blockPosition().distSqr(targetPos);

        if (distSq < 9.0) {
            npc.getNavigation().stop();
            npc.getLookControl().setLookAt(
                targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
            doWork();
            return;
        }

        if (navTimeout > NAV_TIMEOUT_TICKS) {
            targetPos = null;
            return;
        }

        if (!npc.getNavigation().isInProgress()) {
            if (!navigation.navigateTo(targetPos, WORK_SPEED)) {
                actionCooldown++;
                if (actionCooldown > 100) targetPos = null;
            }
        }
    }

    private void doWork() {
        if (actionCooldown > 0) {
            actionCooldown--;
            return;
        }

        if (!(npc.level() instanceof ServerLevel level)) return;

        NPCSkills.SkillType skill = skillForWork(workType);
        double speedMult = npc.getSkills() != null
            ? npc.getSkills().getWorkSpeedMultiplier(skill)
            : 1.0;

        actionCooldown = (int) Math.max(5, 20 / speedMult);

        // v4.0: универсальная обработка цели по типу блока.
        BlockState targetState = level.getBlockState(targetPos);

        boolean didWork = false;

        if (targetState.is(BlockTags.LOGS) || targetState.is(BlockTags.LEAVES)) {
            didWork = chopBlock(level, targetPos);
        } else if (targetState.is(BlockTags.COAL_ORES)
            || targetState.is(BlockTags.IRON_ORES)
            || targetState.is(BlockTags.COPPER_ORES)
            || targetState.is(BlockTags.GOLD_ORES)) {
            didWork = mineBlock(level, targetPos);
        } else if (targetState.getBlock() instanceof CropBlock crop && crop.isMaxAge(targetState)) {
            didWork = harvestCrop(level, targetPos);
        } else if (targetState.is(Blocks.STONE) || targetState.is(Blocks.COBBLESTONE)
            || targetState.is(Blocks.DIRT) || targetState.is(Blocks.GRASS_BLOCK)) {
            didWork = mineBlock(level, targetPos);
        }

        if (didWork) {
            targetPos = null;

            // XP.
            if (npc.getSkills() != null) {
                long day = level.getDayTime() / 24000L;
                npc.getSkills().addXP(skill, 0.1 * speedMult, day);
            }

            // Craft с шансом.
            if (level.random.nextFloat() < CRAFT_CHANCE) {
                tryCraftOrCook(level);
            }
        } else {
            // Не смог — сброс.
            targetPos = null;
        }
    }

    private boolean chopBlock(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        var drops = net.minecraft.world.level.block.Block.getDrops(
            state, level, pos, level.getBlockEntity(pos));
        for (ItemStack drop : drops) npc.addItemToInventory(drop.copy());

        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 0.6f, 1.0f);
        BrainDebugger.log(npc, "CHOP", "@ " + pos);
        return true;
    }

    private boolean mineBlock(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        var drops = net.minecraft.world.level.block.Block.getDrops(
            state, level, pos, level.getBlockEntity(pos));
        for (ItemStack drop : drops) npc.addItemToInventory(drop.copy());

        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.6f, 1.0f);
        BrainDebugger.log(npc, "MINE", "@ " + pos);
        return true;
    }

    private boolean harvestCrop(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        var drops = net.minecraft.world.level.block.Block.getDrops(
            state, level, pos, level.getBlockEntity(pos));
        for (ItemStack drop : drops) npc.addItemToInventory(drop.copy());

        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 0.4f, 1.0f);
        BrainDebugger.log(npc, "FARM", "@ " + pos);
        return true;
    }

    private void tryCraftOrCook(ServerLevel level) {
        try {
            if (npc.getSkills() != null) {
                if (npc.getSkills().getLevel(NPCSkills.SkillType.SMITHING) >= 1.0) {
                    if (CraftManager.craftAnyTool(npc, level)) return;
                }
                if (npc.getSkills().getLevel(NPCSkills.SkillType.COOKING) >= 1.0) {
                    CookingManager.cookAnyFood(npc, level);
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void stop() {
        if (navigation != null) navigation.stop();
        targetPos = null;
        workTimer = 0;
        navTimeout = 0;
        actionCooldown = 0;
    }

    private NPCSkills.SkillType skillForWork(WorkGoalManager.WorkType type) {
        return switch (type) {
            case MINING -> NPCSkills.SkillType.MINING;
            case LUMBERJACK -> NPCSkills.SkillType.LUMBERJACKING;
            case FARMING -> NPCSkills.SkillType.FARMING;
            case SMITHING -> NPCSkills.SkillType.SMITHING;
            case HEALING -> NPCSkills.SkillType.MEDICINE;
            case CAST_SPELL -> NPCSkills.SkillType.MAGIC;
            case COOKING -> NPCSkills.SkillType.COOKING;
            case FISHING -> NPCSkills.SkillType.FISHING;
            case HUNTING -> NPCSkills.SkillType.HUNTING;
            case BUILDING -> NPCSkills.SkillType.BUILDING;
            default -> NPCSkills.SkillType.GENERAL_LABOR;
        };
    }
}