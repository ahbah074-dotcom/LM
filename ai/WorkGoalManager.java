package com.livemine.ai;

import com.livemine.LiveMineConfig;
import com.livemine.LiveMineMod;
import com.livemine.LiveMineSavedData;
import com.livemine.NPCSkills;
import com.livemine.VillageData;
import com.livemine.building.BuildingSystem;
import com.livemine.building.VillageBuildingPlanner;
import com.livemine.entity.LiveNPCEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Менеджер рабочих целей.
 *
 * v3.3: исправлен импорт LiveMineMod (v3.2 его не имел).
 */
public final class WorkGoalManager {

    public enum WorkType {
        MINING, LUMBERJACK, FARMING, FISHING, HUNTING, BUILDING,
        GATHERING, PATROL, HEALING, CAST_SPELL, SMITHING, COOKING
    }

    private WorkGoalManager() {}

    // =========================================================================
    // Выбор работы
    // =========================================================================

    public static WorkType selectBestWork(LiveNPCEntity npc, ServerLevel level) {
        LiveMineSavedData data = LiveMineSavedData.get(level);
        CompoundTag tag = data.loadNPCData(npc.getUUID());
        if (tag.isEmpty()) return WorkType.GATHERING;

        String villageId = tag.getString("village_id");
        CompoundTag villageTag = villageId.isEmpty()
            ? new CompoundTag()
            : data.loadVillageData(villageId);

        double needFood    = villageTag.isEmpty() ? 50 : villageTag.getDouble("need_food");
        double needWood    = villageTag.isEmpty() ? 50 : villageTag.getDouble("need_wood");
        double needOre     = villageTag.isEmpty() ? 50 : villageTag.getDouble("need_ore");
        double needHealing = villageTag.isEmpty() ? 0  : villageTag.getDouble("need_healing");

        var skills = npc.getSkills();
        if (skills == null) return WorkType.GATHERING;

        double bestMining   = skills.getLevel(NPCSkills.SkillType.MINING);
        double bestLumber   = skills.getLevel(NPCSkills.SkillType.LUMBERJACKING);
        double bestFarm     = skills.getLevel(NPCSkills.SkillType.FARMING);
        double bestMagic    = skills.getLevel(NPCSkills.SkillType.MAGIC);
        double bestMedicine = skills.getLevel(NPCSkills.SkillType.MEDICINE);
        double bestBuilding = skills.getLevel(NPCSkills.SkillType.BUILDING);

        if (needHealing > 70 && bestMedicine > 2) return WorkType.HEALING;

        if (bestBuilding >= 1.0 && villageNeedsBuilding(villageId, level)) {
            return WorkType.BUILDING;
        }

        double scoreMining = needOre * (1 + bestMining);
        double scoreLumber = needWood * (1 + bestLumber);
        double scoreFarm   = needFood * (1 + bestFarm);
        double scoreMagic  = bestMagic > 3 ? 40 * (1 + bestMagic) : 0;

        double best = Math.max(Math.max(scoreMining, scoreLumber), Math.max(scoreFarm, scoreMagic));

        if (best == scoreMagic)  return WorkType.CAST_SPELL;
        if (best == scoreFarm)   return WorkType.FARMING;
        if (best == scoreMining) return WorkType.MINING;
        return WorkType.LUMBERJACK;
    }

    private static boolean villageNeedsBuilding(String villageId, ServerLevel level) {
        if (villageId == null || villageId.isEmpty()) return false;
        try {
            VillageData vd = new VillageData(villageId, level);
            if (!vd.exists()) return false;

            var type = VillageBuildingPlanner.decideNextBuilding(vd, level);
            if (type == null) return false;

            var spot = VillageBuildingPlanner.findFreeSpot(level, vd, type);
            return spot != null;
        } catch (Exception e) {
            return false;
        }
    }

    // =========================================================================
    // Поиск цели
    // =========================================================================

    public static BlockPos findWorkTarget(LiveNPCEntity npc, ServerLevel level, WorkType type) {
        if (type == WorkType.BUILDING) {
            return findBuildingTarget(npc, level);
        }

        String resourceType = switch (type) {
            case MINING -> "ore";
            case LUMBERJACK -> "wood";
            case FARMING -> "farm";
            case HEALING -> "infirmary";
            case CAST_SPELL -> "magic_tower";
            case SMITHING -> "forge";
            default -> null;
        };

        if (resourceType == null) return null;

        long tick = level.getGameTime();
        List<BlockPos> resources = ResourceCache.getResources(
            level, npc.blockPosition(), 24, resourceType, tick);

        if (resources.isEmpty()) return null;

        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : resources) {
            double d = pos.distSqr(npc.blockPosition());
            if (d < bestDist) {
                bestDist = d;
                best = pos;
            }
        }
        return best;
    }

    private static BlockPos findBuildingTarget(LiveNPCEntity npc, ServerLevel level) {
        LiveMineSavedData data = LiveMineSavedData.get(level);
        CompoundTag tag = data.loadNPCData(npc.getUUID());
        if (tag.isEmpty()) return null;

        String existingPlan = tag.getString("building_plan");
        if (!existingPlan.isEmpty()) {
            try {
                BlockPos origin = new BlockPos(
                    tag.getInt("building_x"),
                    tag.getInt("building_y"),
                    tag.getInt("building_z")
                );
                return origin;
            } catch (Exception e) {
                clearBuildingPlan(tag);
                data.saveNPCData(npc.getUUID(), tag);
            }
        }

        String villageId = tag.getString("village_id");
        if (villageId.isEmpty()) return null;

        VillageData vd = new VillageData(villageId, level);
        if (!vd.exists()) return null;

        var type = VillageBuildingPlanner.decideNextBuilding(vd, level);
        if (type == null) return null;

        var origin = VillageBuildingPlanner.findFreeSpot(level, vd, type);
        if (origin == null) return null;

        tag.putString("building_plan", type.name());
        tag.putInt("building_step", 0);
        tag.putInt("building_x", origin.getX());
        tag.putInt("building_y", origin.getY());
        tag.putInt("building_z", origin.getZ());
        data.saveNPCData(npc.getUUID(), tag);

        LiveMineMod.LOGGER.info("NPC {} assigned to build {} at {}",
            npc.getCustomNameTag(), type.name(), origin);

        return origin;
    }

    private static void clearBuildingPlan(CompoundTag tag) {
        tag.remove("building_plan");
        tag.remove("building_step");
        tag.remove("building_x");
        tag.remove("building_y");
        tag.remove("building_z");
    }

    // =========================================================================
    // processActiveNPC — только craft chest
    // =========================================================================

    public static void processActiveNPC(ServerLevel level, LiveNPCEntity npc) {
        LiveMineSavedData data = LiveMineSavedData.get(level);
        CompoundTag tag = data.loadNPCData(npc.getUUID());
        if (tag.isEmpty()) return;

        if (tag.getBoolean("needs_chest")) {
            int planks = countPlanks(npc);
            if (planks >= 8) {
                consumePlanks(npc, 8);
                npc.addItemToInventory(new ItemStack(Items.CHEST));
                tag.putBoolean("needs_chest", false);
                tag.putBoolean("needs_wood", false);
                data.saveNPCData(npc.getUUID(), tag);
                if (LiveMineConfig.brainDebug()) {
                    BrainDebugger.log(npc, "CRAFT", "скрафчен сундук");
                }
            } else if (!tag.getBoolean("needs_wood")) {
                tag.putBoolean("needs_wood", true);
                data.saveNPCData(npc.getUUID(), tag);
            }
        }
    }

    // =========================================================================
    // Завершение рабочего цикла
    // =========================================================================

    public static void completeWorkCycle(LiveNPCEntity npc, ServerLevel level, WorkType type) {
        LiveMineSavedData data = LiveMineSavedData.get(level);
        CompoundTag tag = data.loadNPCData(npc.getUUID());
        if (tag.isEmpty()) return;

        String villageId = tag.getString("village_id");

        if (!villageId.isEmpty()) {
            CompoundTag villageTag = data.loadVillageData(villageId);
            if (!villageTag.isEmpty()) {
                double res = villageTag.getDouble("resources") + 0.5;
                villageTag.putDouble("resources", res);

                switch (type) {
                    case MINING -> villageTag.putDouble("need_ore",
                        Math.max(0, villageTag.getDouble("need_ore") - 1));
                    case LUMBERJACK -> villageTag.putDouble("need_wood",
                        Math.max(0, villageTag.getDouble("need_wood") - 1));
                    case FARMING -> villageTag.putDouble("need_food",
                        Math.max(0, villageTag.getDouble("need_food") - 1));
                    default -> {}
                }
                data.saveVillageData(villageId, villageTag);
            }
        }

        NPCSkills.SkillType skill = switch (type) {
            case MINING -> NPCSkills.SkillType.MINING;
            case LUMBERJACK -> NPCSkills.SkillType.LUMBERJACKING;
            case FARMING -> NPCSkills.SkillType.FARMING;
            case CAST_SPELL -> NPCSkills.SkillType.MAGIC;
            case SMITHING -> NPCSkills.SkillType.SMITHING;
            case COOKING -> NPCSkills.SkillType.COOKING;
            case BUILDING -> NPCSkills.SkillType.BUILDING;
            default -> null;
        };

        if (skill != null && npc.getSkills() != null) {
            long day = level.getDayTime() / 24000L;
            npc.getSkills().addXP(skill, 0.1, day);
        }
    }

    // =========================================================================
    // Утилиты
    // =========================================================================

    private static int countPlanks(LiveNPCEntity npc) {
        int count = 0;
        for (ItemStack stack : npc.getInventoryItems()) {
            if (stack.is(ItemTags.PLANKS)) count += stack.getCount();
        }
        return count;
    }

    private static void consumePlanks(LiveNPCEntity npc, int amount) {
        List<ItemStack> items = npc.getInventoryItems();
        int remaining = amount;
        for (int i = 0; i < items.size() && remaining > 0; i++) {
            ItemStack stack = items.get(i);
            if (stack.is(ItemTags.PLANKS)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
                if (stack.isEmpty()) items.set(i, ItemStack.EMPTY);
            }
        }
        npc.setInventoryItems(items);
    }
}