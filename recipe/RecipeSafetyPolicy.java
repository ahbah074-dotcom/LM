package com.livemine.recipe;

import com.livemine.LiveMineMod;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Политика безопасности рецептов.
 *
 * v2.0: cleanupWindow удаляет только устаревшие записи (> RATE_WINDOW_TICKS),
 * а не весь map.
 */
public final class RecipeSafetyPolicy {

    private static RecipeSafetyPolicy INSTANCE;

    private static final Set<Item> FORBIDDEN_RESULTS = Set.of(
        Items.TNT, Items.END_CRYSTAL, Items.WITHER_SKELETON_SKULL,
        Items.DRAGON_EGG, Items.COMMAND_BLOCK, Items.CHAIN_COMMAND_BLOCK,
        Items.REPEATING_COMMAND_BLOCK, Items.STRUCTURE_BLOCK,
        Items.STRUCTURE_VOID, Items.JIGSAW, Items.BARRIER, Items.LIGHT,
        Items.SPAWNER, Items.BEDROCK, Items.NETHERITE_BLOCK
    );

    private static final Set<Item> FORBIDDEN_INGREDIENTS = Set.of(
        Items.WITHER_SKELETON_SKULL, Items.DRAGON_EGG,
        Items.COMMAND_BLOCK, Items.CHAIN_COMMAND_BLOCK,
        Items.REPEATING_COMMAND_BLOCK, Items.STRUCTURE_BLOCK,
        Items.JIGSAW, Items.BARRIER, Items.BEDROCK, Items.SPAWNER
    );

    private static final Set<Item> DANGEROUS_RESULTS = Set.of(
        Items.LAVA_BUCKET, Items.FLINT_AND_STEEL,
        Items.FIRE_CHARGE, Items.CAMPFIRE
    );

    private static final int RATE_LIMIT = 32;
    private static final long RATE_WINDOW_TICKS = 1200;

    /** item → очередь пар (tick, count) для окна rate-limit. */
    private final Map<Item, LinkedList<long[]>> recentlyCrafted = new ConcurrentHashMap<>();

    private RecipeSafetyPolicy() {}

    public static synchronized RecipeSafetyPolicy getInstance() {
        if (INSTANCE == null) INSTANCE = new RecipeSafetyPolicy();
        return INSTANCE;
    }

    public enum Verdict {
        ALLOW, DENY_FORBIDDEN, DENY_RATE_LIMIT, DENY_DANGEROUS
    }

    public Verdict evaluateRecipe(ItemStack result, List<Ingredient> ingredients, long currentTick) {
        if (result == null || result.isEmpty()) return Verdict.DENY_FORBIDDEN;

        Item resultItem = result.getItem();

        if (FORBIDDEN_RESULTS.contains(resultItem)) {
            LiveMineMod.LOGGER.warn("Recipe blocked: forbidden result {}", resultItem);
            return Verdict.DENY_FORBIDDEN;
        }

        if (ingredients != null) {
            for (Ingredient ing : ingredients) {
                if (ing == null) continue;
                for (ItemStack stack : ing.getItems()) {
                    if (FORBIDDEN_INGREDIENTS.contains(stack.getItem())) {
                        LiveMineMod.LOGGER.warn(
                            "Recipe blocked: forbidden ingredient {}", stack.getItem());
                        return Verdict.DENY_FORBIDDEN;
                    }
                }
            }
        }

        if (DANGEROUS_RESULTS.contains(resultItem)) {
            LiveMineMod.LOGGER.debug("Recipe flagged: dangerous result {}", resultItem);
            return Verdict.DENY_DANGEROUS;
        }

        cleanupWindow(currentTick);
        LinkedList<long[]> window = recentlyCrafted.get(resultItem);
        if (window != null) {
            int total = 0;
            for (long[] entry : window) total += (int) entry[1];
            if (total >= RATE_LIMIT) {
                LiveMineMod.LOGGER.warn(
                    "Recipe rate-limited: {} crafted {} times in window", resultItem, total);
                return Verdict.DENY_RATE_LIMIT;
            }
        }

        return Verdict.ALLOW;
    }

    public void recordCraft(ItemStack result, long currentTick) {
        if (result == null || result.isEmpty()) return;
        recentlyCrafted
            .computeIfAbsent(result.getItem(), k -> new LinkedList<>())
            .add(new long[]{currentTick, result.getCount()});
    }

    /** Удаляет записи старше окна rate-limit. */
    public void cleanupWindow(long currentTick) {
        long threshold = currentTick - RATE_WINDOW_TICKS;
        Iterator<Map.Entry<Item, LinkedList<long[]>>> it = recentlyCrafted.entrySet().iterator();
        while (it.hasNext()) {
            LinkedList<long[]> window = it.next().getValue();
            window.removeIf(entry -> entry[0] < threshold);
            if (window.isEmpty()) it.remove();
        }
    }

    public boolean isResultSafe(ItemStack result) {
        if (result == null || result.isEmpty()) return true;
        return !FORBIDDEN_RESULTS.contains(result.getItem());
    }

    public boolean areIngredientsSafe(List<Ingredient> ingredients) {
        if (ingredients == null) return true;
        for (Ingredient ing : ingredients) {
            if (ing == null) continue;
            for (ItemStack stack : ing.getItems()) {
                if (FORBIDDEN_INGREDIENTS.contains(stack.getItem())) return false;
            }
        }
        return true;
    }

    public Set<Item> getForbiddenResults() {
        return Collections.unmodifiableSet(FORBIDDEN_RESULTS);
    }

    public Set<Item> getForbiddenIngredients() {
        return Collections.unmodifiableSet(FORBIDDEN_INGREDIENTS);
    }

    public void clear() {
        recentlyCrafted.clear();
    }
}