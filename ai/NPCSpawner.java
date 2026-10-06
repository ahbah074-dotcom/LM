package com.livemine.ai;

import com.livemine.LiveMineConfig;
import com.livemine.LiveMineSavedData;
import com.livemine.NamePool;
import com.livemine.NPCSkills;
import com.livemine.VillageManager;
import com.livemine.entity.LiveNPCEntity;
import com.livemine.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

import java.util.UUID;

/**
 * Спавнер NPC.
 *
 * v3.2: стартовые навыки по профессии (MINING/LUMBER/FARM/SMITH/BUILDING).
 */
public final class NPCSpawner {

    private static final String[] PROFESSIONS = {
        "farmer", "miner", "guard", "merchant", "blacksmith", "fisherman"
    };

    private NPCSpawner() {}

    public static LiveNPCEntity spawn(ServerLevel level, BlockPos pos) {
        return spawn(level, pos, null, false);
    }

    public static LiveNPCEntity spawnInVillage(ServerLevel level, BlockPos pos, String villageId) {
        return spawn(level, pos, villageId, false);
    }

    public static LiveNPCEntity spawnChild(ServerLevel level, BlockPos pos, String villageId, UUID parentUUID) {
        LiveNPCEntity child = spawn(level, pos, villageId, true);
        if (child != null && parentUUID != null) {
            CompoundTag tag = LiveMineSavedData.get(level).loadNPCData(child.getUUID());
            tag.putUUID("parent_uuid", parentUUID);
            LiveMineSavedData.get(level).saveNPCData(child.getUUID(), tag);
        }
        return child;
    }

    private static LiveNPCEntity spawn(ServerLevel level, BlockPos pos, String villageId, boolean isChild) {
        if (NPCRegistry.getTotalCount() >= LiveMineConfig.maxTotalNpcs()) {
            com.livemine.LiveMineMod.LOGGER.warn(
                "NPC limit reached ({}/{}), spawn rejected",
                NPCRegistry.getTotalCount(), LiveMineConfig.maxTotalNpcs());
            return null;
        }

        if (villageId == null || villageId.isEmpty()) {
            var nearest = VillageManager.getInstance()
                .getNearestVillage(pos.getX(), pos.getZ(), 128);
            villageId = nearest != null ? nearest.rawId() : "";
        }

        if (!villageId.isEmpty()) {
            int inVillage = countInVillage(villageId, level);
            if (inVillage >= LiveMineConfig.maxNpcsPerVillage()) {
                com.livemine.LiveMineMod.LOGGER.debug(
                    "Village {} full ({}/{}), spawn rejected",
                    villageId, inVillage, LiveMineConfig.maxNpcsPerVillage());
                return null;
            }
        }

        LiveNPCEntity npc = ModEntities.LIVE_NPC.get().create(level);
        if (npc == null) return null;

        npc.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);

        RandomSource rnd = level.random;
        boolean male = rnd.nextBoolean();
        String name = NamePool.getInstance().generateName(male, "ru_ru");
        npc.setCustomNameTag(name);

        npc.setChild(isChild);
        if (isChild) {
            npc.setAgeInDays(0);
        } else {
            npc.setAgeInDays(18 + rnd.nextInt(40));
            String profession = PROFESSIONS[rnd.nextInt(PROFESSIONS.length)];
            npc.setProfessionName(profession);
        }

        if (!villageId.isEmpty()) {
            npc.setVillageRawId(villageId);
        }

        initNpcData(npc, level, villageId);
        initSkillsForProfession(npc, isChild);

        level.addFreshEntity(npc);
        NPCRegistry.register(npc);

        return npc;
    }

    /**
     * v3.2: стартовые навыки по профессии.
     * Каждый NPC имеет основной навык ≥ 3.0, чтобы точно мог работать.
     */
    private static void initSkillsForProfession(LiveNPCEntity npc, boolean isChild) {
        if (npc.getSkills() == null) return;

        if (isChild) {
            npc.getSkills().setLevel(NPCSkills.SkillType.GENERAL_LABOR, 1.0);
            return;
        }

        String prof = npc.getProfessionName();

        // Основной навык от профессии.
        NPCSkills.SkillType main = switch (prof) {
            case "farmer" -> NPCSkills.SkillType.FARMING;
            case "miner" -> NPCSkills.SkillType.MINING;
            case "guard" -> NPCSkills.SkillType.GUARDING;
            case "merchant" -> NPCSkills.SkillType.TRADING;
            case "blacksmith" -> NPCSkills.SkillType.SMITHING;
            case "fisherman" -> NPCSkills.SkillType.FISHING;
            default -> NPCSkills.SkillType.GENERAL_LABOR;
        };

        npc.getSkills().setLevel(main, 3.0 + npc.level().random.nextDouble() * 2.0);

        // Вторичные.
        npc.getSkills().setLevel(NPCSkills.SkillType.BUILDING, 1.0);
        npc.getSkills().setLevel(NPCSkills.SkillType.GENERAL_LABOR, 2.0);
        npc.getSkills().setLevel(NPCSkills.SkillType.LUMBERJACKING, 1.0);
    }

    public static void initNpcData(LiveNPCEntity npc, ServerLevel level, String villageId) {
        LiveMineSavedData data = LiveMineSavedData.get(level);
        CompoundTag tag = data.loadNPCData(npc.getUUID());

        if (tag.isEmpty()) {
            tag.putDouble("health", 20.0);
            tag.putDouble("hunger", 100.0);
            tag.putDouble("energy", 100.0);
            tag.putDouble("social", 100.0);
            tag.putString("current_goal", "WANDER");
            tag.putString("village_id", villageId != null ? villageId : "");
            tag.putBoolean("needs_chest", false);
            tag.putBoolean("needs_wood", false);
            tag.putLong("last_sim_tick", 0L);
            tag.putBoolean("dead", false);
            tag.putLong("birth_day", level.getDayTime() / 24000L);
        } else {
            if (villageId != null && !villageId.isEmpty()) {
                tag.putString("village_id", villageId);
            }
        }

        data.saveNPCData(npc.getUUID(), tag);
    }

    public static int countInVillage(String villageId, ServerLevel level) {
        int count = 0;
        LiveMineSavedData data = LiveMineSavedData.get(level);
        for (var entry : data.getAllNpcData().entrySet()) {
            CompoundTag tag = entry.getValue();
            if (tag.getBoolean("dead")) continue;
            if (villageId.equals(tag.getString("village_id"))) count++;
        }
        return count;
    }

    public static boolean canReproduce(LiveNPCEntity npc, ServerLevel level) {
        if (!npc.isAdult()) return false;
        CompoundTag tag = LiveMineSavedData.get(level).loadNPCData(npc.getUUID());
        if (tag.isEmpty()) return false;

        if (tag.getDouble("hunger") < 50) return false;
        if (tag.getDouble("energy") < 50) return false;
        if (tag.getDouble("social") < 50) return false;
        if (tag.getDouble("health") < 15) return false;

        if (NPCRegistry.getTotalCount() >= LiveMineConfig.maxTotalNpcs()) return false;

        String vid = tag.getString("village_id");
        if (!vid.isEmpty() && countInVillage(vid, level) >= LiveMineConfig.maxNpcsPerVillage()) {
            return false;
        }
        return true;
    }

    public static boolean isSpawnable(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir()
            && level.getBlockState(pos.above()).isAir()
            && !level.getBlockState(pos.below()).isAir();
    }
}