package com.livemine.ai.goals;

import com.livemine.LiveMineSavedData;
import com.livemine.NPCSkills;
import com.livemine.ai.BrainDebugger;
import com.livemine.ai.NPCBrain;
import com.livemine.ai.NPCRegistry;
import com.livemine.crafting.CookingManager;
import com.livemine.entity.LiveNPCEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumSet;

/**
 * Цель еды NPC.
 *
 * v2.0: если еды нет — пытается приготовить (CookingManager).
 */
public class EatGoal extends Goal {

    private static final int EAT_DURATION_TICKS = 20;
    private static final int EAT_COOLDOWN_TICKS = 40;

    private final LiveNPCEntity npc;
    private int eatTimer;
    private int cooldown;

    public EatGoal(LiveNPCEntity npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!NPCRegistry.isRegistered(npc.getUUID())) return false;
        if (npc.getNpcBrain().getCurrentGoal() != NPCBrain.GoalType.FOOD) return false;

        // v2.0: пробуем приготовить еду, если её нет.
        if (findFoodSlot() < 0) {
            tryCook();
        }

        return findFoodSlot() >= 0;
    }

    private void tryCook() {
        try {
            if (npc.getSkills() == null) return;
            double cookingLevel = npc.getSkills().getLevel(NPCSkills.SkillType.COOKING);
            if (cookingLevel < 1.0) return;

            if (npc.level() instanceof ServerLevel level) {
                if (CookingManager.cookAnyFood(npc, level)) {
                    BrainDebugger.log(npc, "COOK", "приготовлена еда");
                }
            }
        } catch (Exception e) {
            com.livemine.LiveMineMod.LOGGER.error("EatGoal.tryCook failed", e);
        }
    }

    @Override
    public void start() {
        eatTimer = 0;
        cooldown = 0;
        BrainDebugger.log(npc, "EAT_START", "начинает есть");
    }

    @Override
    public boolean canContinueToUse() {
        if (npc.getNpcBrain().getCurrentGoal() != NPCBrain.GoalType.FOOD) return false;
        if (!(npc.level() instanceof ServerLevel level)) return false;

        CompoundTag tag = LiveMineSavedData.get(level).loadNPCData(npc.getUUID());
        double hunger = tag.getDouble("hunger");
        return hunger < 100 && eatTimer < EAT_DURATION_TICKS * 10;
    }

    @Override
    public void tick() {
        eatTimer++;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        if (eatTimer < EAT_DURATION_TICKS) return;

        int slot = findFoodSlot();
        if (slot < 0) return;

        if (!(npc.level() instanceof ServerLevel level)) return;

        ItemStack food = npc.getInventoryItems().get(slot);
        String foodName = food.getHoverName().getString();

        npc.removeItemFromInventory(slot, 1);

        CompoundTag tag = LiveMineSavedData.get(level).loadNPCData(npc.getUUID());
        if (tag.isEmpty()) return;

        double hunger = tag.getDouble("hunger");
        double value = getFoodValue(food);
        hunger = Math.min(100, hunger + value);
        tag.putDouble("hunger", hunger);

        if (value >= 25) {
            double health = tag.getDouble("health");
            health = Math.min(20, health + 1.0);
            tag.putDouble("health", health);
        }

        LiveMineSavedData.get(level).saveNPCData(npc.getUUID(), tag);

        level.playSound(null, npc.blockPosition(),
            SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.6f, 1.0f);

        BrainDebugger.log(npc, "EAT",
            "съедено " + foodName + " (голод: " + String.format("%.0f", hunger) + ")");

        eatTimer = 0;
        cooldown = EAT_COOLDOWN_TICKS;
    }

    @Override
    public void stop() {
        eatTimer = 0;
        cooldown = 0;
    }

    private int findFoodSlot() {
        var items = npc.getInventoryItems();
        for (int i = 0; i < items.size(); i++) {
            if (isFood(items.get(i))) return i;
        }
        return -1;
    }

    private boolean isFood(ItemStack stack) {
        return stack.is(Items.BREAD)
            || stack.is(Items.APPLE)
            || stack.is(Items.CARROT)
            || stack.is(Items.POTATO)
            || stack.is(Items.BAKED_POTATO)
            || stack.is(Items.COOKED_BEEF)
            || stack.is(Items.COOKED_PORKCHOP)
            || stack.is(Items.COOKED_MUTTON)
            || stack.is(Items.COOKED_CHICKEN)
            || stack.is(Items.COOKED_COD)
            || stack.is(Items.COOKED_SALMON)
            || stack.is(Items.GOLDEN_APPLE)
            || stack.is(Items.MELON_SLICE)
            || stack.is(Items.SWEET_BERRIES);
    }

    private double getFoodValue(ItemStack food) {
        if (food.is(Items.GOLDEN_APPLE)) return 50;
        if (food.is(Items.BREAD)) return 30;
        if (food.is(Items.COOKED_BEEF)) return 35;
        if (food.is(Items.COOKED_PORKCHOP)) return 35;
        if (food.is(Items.COOKED_MUTTON)) return 30;
        if (food.is(Items.BAKED_POTATO)) return 25;
        if (food.is(Items.COOKED_CHICKEN)) return 25;
        if (food.is(Items.COOKED_SALMON)) return 25;
        if (food.is(Items.APPLE)) return 20;
        if (food.is(Items.COOKED_COD)) return 20;
        if (food.is(Items.CARROT)) return 15;
        if (food.is(Items.MELON_SLICE)) return 12;
        if (food.is(Items.SWEET_BERRIES)) return 10;
        if (food.is(Items.POTATO)) return 10;
        return 15;
    }
}