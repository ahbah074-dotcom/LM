package com.livemine.entity;

import com.livemine.DiseaseManager;
import com.livemine.LiveMineConfig;
import com.livemine.LiveMineMod;
import com.livemine.LiveMineSavedData;
import com.livemine.NPCSkills;
import com.livemine.VillageManager;
import com.livemine.ai.NPCBrain;
import com.livemine.ai.NPCActivityManager;
import com.livemine.ai.NPCRegistry;
import com.livemine.ai.NPCRelationship;
import com.livemine.ai.goals.EatGoal;
import com.livemine.ai.goals.HealGoal;
import com.livemine.ai.goals.RestGoal;
import com.livemine.ai.goals.SocializeGoal;
import com.livemine.ai.goals.WanderGoal;
import com.livemine.ai.goals.WorkGoal;
import com.livemine.dialogue.DialogSystem;
import com.livemine.dialogue.NPCDialogMemory;
import com.livemine.network.NPCStatePacket;
import com.livemine.personality.NPCEmotions;
import com.livemine.personality.NPCTraits;
import com.livemine.pet.PetManager;
import com.livemine.property.PropertyManager;
import com.livemine.quest.QuestManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Сущность NPC.
 *
 * v3.5:
 *   - 8 подсистем в die(): наследство, храм, сироты, NBT, кладбище,
 *     горе, питомец, дом.
 *   - Питомцы, эмоции, черты, сны, диалоги.
 */
public class LiveNPCEntity extends Mob {

    private static final EntityDataAccessor<String> DATA_NAME =
        SynchedEntityData.defineId(LiveNPCEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_PROFESSION =
        SynchedEntityData.defineId(LiveNPCEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_AGE_DAYS =
        SynchedEntityData.defineId(LiveNPCEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_IS_CHILD =
        SynchedEntityData.defineId(LiveNPCEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> DATA_VILLAGE_ID =
        SynchedEntityData.defineId(LiveNPCEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_GOAL =
        SynchedEntityData.defineId(LiveNPCEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_ENERGY =
        SynchedEntityData.defineId(LiveNPCEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HUNGER =
        SynchedEntityData.defineId(LiveNPCEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SOCIAL =
        SynchedEntityData.defineId(LiveNPCEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_SPOUSE_UUID =
        SynchedEntityData.defineId(LiveNPCEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_DISEASE =
        SynchedEntityData.defineId(LiveNPCEntity.class, EntityDataSerializers.STRING);

    private final NPCBrain brain = new NPCBrain();
    private NPCSkills skills;

    private final NPCEmotions emotions = new NPCEmotions();
    private final NPCTraits traits;
    private NPCDialogMemory dialogMemory = new NPCDialogMemory();

    private final SimpleContainer inventory = new SimpleContainer(27);

    private int tickCounter;
    private int thinkCounter;
    private int simSyncCounter;
    private int diseaseCounter;
    private int emotionCounter;

    private boolean newlyGenerated = true;

    public LiveNPCEntity(EntityType<? extends LiveNPCEntity> type, Level level) {
        super(type, level);
        this.skills = new NPCSkills();
        this.traits = new NPCTraits(level.random);
        if (!level.isClientSide()) {
            this.setPersistenceRequired();
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.MOVEMENT_SPEED, 0.35)
            .add(Attributes.ATTACK_DAMAGE, 2.0)
            .add(Attributes.FOLLOW_RANGE, 48.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.3);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_NAME, "");
        builder.define(DATA_PROFESSION, "");
        builder.define(DATA_AGE_DAYS, 0);
        builder.define(DATA_IS_CHILD, false);
        builder.define(DATA_VILLAGE_ID, "");
        builder.define(DATA_GOAL, "WANDER");
        builder.define(DATA_ENERGY, 100);
        builder.define(DATA_HUNGER, 100);
        builder.define(DATA_SOCIAL, 100);
        builder.define(DATA_SPOUSE_UUID, "");
        builder.define(DATA_DISEASE, "none");
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new HealGoal(this));
        this.goalSelector.addGoal(2, new EatGoal(this));
        this.goalSelector.addGoal(3, new RestGoal(this));
        this.goalSelector.addGoal(4, new WorkGoal(this));
        this.goalSelector.addGoal(5, new SocializeGoal(this));
        this.goalSelector.addGoal(6, new WanderGoal(this));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0f));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide()) return;
        if (!(level() instanceof ServerLevel level)) return;

        tickCounter++;

        NPCActivityManager.ActivityState state = NPCRegistry.getActivityState(getUUID());
        if (state != NPCActivityManager.ActivityState.ACTIVE) {
            if (tickCounter % 100 == 0) syncFromSavedData(level);
            return;
        }

        int thinkInterval = Math.max(20,
            com.livemine.ai.AdaptiveTickRate.getSimulationInterval() / 4);
        thinkCounter++;
        if (thinkCounter >= thinkInterval) {
            thinkCounter = 0;
            brain.think(this, level);
        }

        if (tickCounter % 20 == 0) syncFromSavedData(level);
        if (tickCounter % 20 == 0) applyPassiveDecay(level);

        diseaseCounter++;
        if (diseaseCounter >= 200) {
            diseaseCounter = 0;
            try {
                DiseaseManager.getInstance().tick(this);
            } catch (Exception e) {
                LiveMineMod.LOGGER.error("DiseaseManager.tick failed for {}", getUUID(), e);
            }
        }

        emotionCounter++;
        if (emotionCounter >= 200) {
            emotionCounter = 0;
            emotions.tick(level.getGameTime());
        }

        simSyncCounter++;
        if (simSyncCounter >= 200) {
            simSyncCounter = 0;
            pushToClient();
        }
    }

    private void applyPassiveDecay(ServerLevel level) {
        LiveMineSavedData data = LiveMineSavedData.get(level);
        CompoundTag tag = data.loadNPCData(getUUID());
        if (tag.isEmpty()) return;

        double prodMult = emotions.getProductivityMultiplier()
            * (traits != null ? traits.getProductivityMultiplier() : 1.0);

        double hunger = tag.getDouble("hunger") - 0.01 * prodMult;
        double energy = tag.getDouble("energy") - 0.005;
        double social = tag.getDouble("social") - 0.008;

        String goal = tag.getString("current_goal");
        if ("REST".equals(goal)) energy += 0.06;
        if ("FOOD".equals(goal)) hunger += 0.03;

        hunger = clamp(hunger, 0, 100);
        energy = clamp(energy, 0, 100);
        social = clamp(social, 0, 100);

        tag.putDouble("hunger", hunger);
        tag.putDouble("energy", energy);
        tag.putDouble("social", social);

        if (hunger < 10.0) {
            double health = tag.getDouble("health");
            health = Math.max(0, health - 0.006);
            tag.putDouble("health", health);

            if (health <= 0) {
                tag.putBoolean("dead", true);
                data.saveNPCData(getUUID(), tag);
                this.discard();
                NPCRegistry.unregister(getUUID());
                return;
            }
        }

        data.saveNPCData(getUUID(), tag);

        double storedHealth = tag.getDouble("health");
        if (storedHealth > 0 && getHealth() != (float) storedHealth) {
            this.setHealth((float) Math.max(1, Math.min(getMaxHealth(), storedHealth)));
        }
    }

    private void syncFromSavedData(ServerLevel level) {
        LiveMineSavedData data = LiveMineSavedData.get(level);
        CompoundTag tag = data.loadNPCData(getUUID());
        if (tag.isEmpty()) return;

        entityData.set(DATA_HUNGER, (int) tag.getDouble("hunger"));
        entityData.set(DATA_ENERGY, (int) tag.getDouble("energy"));
        entityData.set(DATA_SOCIAL, (int) tag.getDouble("social"));

        String goal = tag.getString("current_goal");
        if (goal.isEmpty()) goal = brain.getCurrentGoalName();
        entityData.set(DATA_GOAL, goal);
    }

    private void pushToClient() {
        entityData.set(DATA_NAME, getCustomNameTag());
        entityData.set(DATA_AGE_DAYS, getAgeInDays());
        entityData.set(DATA_IS_CHILD, isChild());
        entityData.set(DATA_VILLAGE_ID, getVillageRawId());
    }

    public NPCBrain getNpcBrain() { return brain; }
    public NPCSkills getSkills() { return skills; }
    public NPCEmotions getEmotions() { return emotions; }
    public NPCTraits getTraits() { return traits; }
    public NPCDialogMemory getDialogMemory() { return dialogMemory; }

    public List<ItemStack> getInventoryItems() {
        List<ItemStack> list = new ArrayList<>(inventory.getContainerSize());
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            list.add(inventory.getItem(i));
        }
        return list;
    }

    public void setInventoryItems(List<ItemStack> items) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            inventory.setItem(i, i < items.size() ? items.get(i) : ItemStack.EMPTY);
        }
    }

    public void addItemToInventory(ItemStack stack) {
        if (stack.isEmpty()) return;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack existing = inventory.getItem(i);
            if (ItemStack.isSameItemSameComponents(existing, stack)
                && existing.getCount() < existing.getMaxStackSize()) {
                int canAdd = Math.min(stack.getCount(),
                    existing.getMaxStackSize() - existing.getCount());
                existing.grow(canAdd);
                stack.shrink(canAdd);
                if (stack.isEmpty()) return;
            }
        }
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).isEmpty()) {
                inventory.setItem(i, stack.copy());
                return;
            }
        }
        if (!level().isClientSide() && level() instanceof ServerLevel sl) {
            LiveMineSavedData data = LiveMineSavedData.get(sl);
            CompoundTag tag = data.loadNPCData(getUUID());
            tag.putBoolean("needs_chest", true);
            data.saveNPCData(getUUID(), tag);
        }
    }

    public void removeItemFromInventory(int slot, int count) {
        if (slot < 0 || slot >= inventory.getContainerSize()) return;
        ItemStack stack = inventory.getItem(slot);
        if (stack.isEmpty()) return;
        stack.shrink(count);
        if (stack.isEmpty()) inventory.setItem(slot, ItemStack.EMPTY);
    }

    public SimpleContainer getInventory() {
        return inventory;
    }

    public String getCustomNameTag() {
        String n = entityData.get(DATA_NAME);
        return (n != null && !n.isEmpty()) ? n : "NPC";
    }

    public void setCustomNameTag(String name) {
        String v = name != null ? name : "NPC";
        entityData.set(DATA_NAME, v);
        setCustomName(Component.literal(v));
        setCustomNameVisible(true);
    }

    public String getProfessionName() {
        String p = entityData.get(DATA_PROFESSION);
        return p != null ? p : "";
    }

    public void setProfessionName(String p) {
        entityData.set(DATA_PROFESSION, p != null ? p : "");
    }

    public int getAgeInDays() {
        return entityData.get(DATA_AGE_DAYS);
    }

    public void setAgeInDays(int days) {
        entityData.set(DATA_AGE_DAYS, Math.max(0, days));
    }

    public boolean isChild() {
        return entityData.get(DATA_IS_CHILD);
    }

    public void setChild(boolean v) {
        entityData.set(DATA_IS_CHILD, v);
    }

    public boolean isAdult() {
        return !isChild() && getAgeInDays() >= 7;
    }

    public UUID getVillageId() {
        String rawId = getVillageRawId();
        if (rawId == null || rawId.isEmpty()) return null;
        try {
            return UUID.nameUUIDFromBytes(rawId.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    public String getVillageRawId() {
        String s = entityData.get(DATA_VILLAGE_ID);
        return (s != null) ? s : "";
    }

    public void setVillageRawId(String rawId) {
        entityData.set(DATA_VILLAGE_ID, rawId != null ? rawId : "");
    }

    public void setVillageId(UUID id) {
        if (id == null) { setVillageRawId(""); return; }
        var record = VillageManager.getInstance().getVillage(id);
        if (record != null) setVillageRawId(record.rawId());
        else setVillageRawId("");
    }

    public String getCurrentGoalName() {
        return entityData.get(DATA_GOAL);
    }

    public boolean isNewlyGenerated() { return newlyGenerated; }
    public void setNewlyGenerated(boolean v) { this.newlyGenerated = v; }

    public UUID getSpouseUUID() {
        String s = entityData.get(DATA_SPOUSE_UUID);
        if (s == null || s.isEmpty()) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setSpouseUUID(UUID id) {
        entityData.set(DATA_SPOUSE_UUID, id != null ? id.toString() : "");
    }

    public String getCurrentDisease() {
        String d = entityData.get(DATA_DISEASE);
        return d != null ? d : "none";
    }

    public void setCurrentDisease(String disease) {
        entityData.set(DATA_DISEASE, disease != null ? disease : "none");
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.CONSUME;

        try {
            ServerLevel level = (ServerLevel) level();
            CompoundTag tag = LiveMineSavedData.get(level).loadNPCData(getUUID());

            String dialogText = DialogSystem.generate(this, sp, level);

            String qId = "";
            String qType = "";
            String qDesc = "";
            int qReward = 0;
            int qTarget = 0;
            int qCurrent = 0;

            try {
                var pending = QuestManager.getInstance()
                    .getPendingOfferForPlayer(sp.getUUID(), getUUID(), level);

                if (pending == null) {
                    if (isAdult() && tag.getDouble("health") >= 15) {
                        long day = level.getDayTime() / 24000L;
                        pending = QuestManager.getInstance()
                            .offerForPlayer(this, sp, level, day);
                    }
                }

                if (pending != null) {
                    qId = pending.questId.toString();
                    qType = pending.type.ruName;
                    qDesc = pending.description;
                    qReward = pending.reputationReward;
                    qTarget = pending.targetAmount;
                    qCurrent = pending.currentAmount;
                }
            } catch (Exception e) {
                LiveMineMod.LOGGER.error("mobInteract: quest offer failed", e);
            }

            NPCStatePacket packet = new NPCStatePacket(
                getId(),
                getCustomNameTag(),
                getProfessionName(),
                tag.getDouble("hunger"),
                tag.getDouble("energy"),
                tag.getDouble("health"),
                tag.getDouble("social"),
                brain.getCurrentGoalName(),
                getVillageRawId(),
                dialogText,
                qId, qType, qDesc, qReward, qTarget, qCurrent
            );

            sp.connection.send(packet);
        } catch (Exception e) {
            LiveMineMod.LOGGER.error("mobInteract failed for {}", getUUID(), e);
        }

        return InteractionResult.CONSUME;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        tag.putString("custom_name", getCustomNameTag());
        tag.putString("profession", getProfessionName());
        tag.putInt("age_days", getAgeInDays());
        tag.putBoolean("is_child", isChild());

        String rawId = getVillageRawId();
        if (!rawId.isEmpty()) tag.putString("village_id", rawId);

        UUID spouse = getSpouseUUID();
        if (spouse != null) tag.putUUID("spouse_uuid", spouse);
        tag.putString("current_disease", getCurrentDisease());
        tag.putBoolean("newly_generated", newlyGenerated);

        tag.put("skills", skills.save());
        tag.put("emotions", emotions.save());
        tag.put("traits", traits.save());
        tag.put("dialog_memory", dialogMemory.save());

        ListTag list = new ListTag();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putByte("Slot", (byte) i);
                CompoundTag stackTag = (CompoundTag) stack.save(this.registryAccess());
                itemTag.put("Item", stackTag);
                list.add(itemTag);
            }
        }
        CompoundTag invTag = new CompoundTag();
        invTag.put("Items", list);
        tag.put("inventory", invTag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        if (tag.contains("custom_name")) setCustomNameTag(tag.getString("custom_name"));
        if (tag.contains("profession")) setProfessionName(tag.getString("profession"));
        setAgeInDays(tag.getInt("age_days"));
        setChild(tag.getBoolean("is_child"));

        if (tag.contains("village_id")) {
            String rawId = tag.getString("village_id");
            if (!rawId.isEmpty()) {
                if (rawId.length() == 36 && rawId.contains("-")) {
                    try {
                        UUID oldUuid = UUID.fromString(rawId);
                        var record = VillageManager.getInstance().getVillage(oldUuid);
                        if (record != null) setVillageRawId(record.rawId());
                    } catch (Exception ignored) {}
                } else {
                    setVillageRawId(rawId);
                }
            }
        }

        if (tag.hasUUID("spouse_uuid")) setSpouseUUID(tag.getUUID("spouse_uuid"));
        if (tag.contains("current_disease")) setCurrentDisease(tag.getString("current_disease"));
        newlyGenerated = tag.getBoolean("newly_generated");

        if (tag.contains("skills")) {
            this.skills = NPCSkills.load(tag.getCompound("skills"));
        }

        if (tag.contains("emotions")) {
            try {
                NPCEmotions loaded = NPCEmotions.load(tag.getCompound("emotions"));
                for (NPCEmotions.Emotion e : NPCEmotions.Emotion.values()) {
                    emotions.set(e, loaded.get(e));
                }
            } catch (Exception ignored) {}
        }

        if (tag.contains("traits")) {
            try {
                NPCTraits loaded = NPCTraits.load(tag.getCompound("traits"));
                traits.replaceAll(loaded.getAll());
                if (traits.size() == 0) {
                    traits.assignRandom(level().random);
                }
            } catch (Exception ignored) {}
        }

        if (tag.contains("dialog_memory")) {
            try {
                this.dialogMemory = NPCDialogMemory.load(tag.getCompound("dialog_memory"));
            } catch (Exception ignored) {}
        }

        if (tag.contains("inventory")) {
            CompoundTag invTag = tag.getCompound("inventory");
            ListTag list = invTag.getList("Items", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag itemTag = list.getCompound(i);
                int slot = itemTag.getByte("Slot") & 0xFF;
                if (slot >= 0 && slot < inventory.getContainerSize()) {
                    if (itemTag.contains("Item")) {
                        ItemStack stack = ItemStack.parse(this.registryAccess(),
                            itemTag.getCompound("Item")).orElse(ItemStack.EMPTY);
                        inventory.setItem(slot, stack);
                    }
                }
            }
        }
    }

    // =========================================================================
    // v3.5: смерть — 8 подсистем
    // =========================================================================

    @Override
    public void die(DamageSource cause) {
        if (!level().isClientSide() && level() instanceof ServerLevel sl) {
            try {
                // 1. Наследство.
                com.livemine.InheritanceManager.getInstance().onNpcDeath(this);

                // 2. Похоронный ритуал.
                try {
                    com.livemine.temple.TempleManager.getInstance().onNpcDeath(this, sl);
                } catch (Exception e) {
                    LiveMineMod.LOGGER.error("TempleManager.onNpcDeath failed", e);
                }

                // 3. Сироты.
                try {
                    var data = LiveMineSavedData.get(sl);
                    for (var entry : data.getAllNpcData().entrySet()) {
                        CompoundTag childTag = entry.getValue();
                        if (childTag.getBoolean("dead")) continue;

                        UUID pA = childTag.hasUUID("parent_a_uuid")
                            ? childTag.getUUID("parent_a_uuid") : null;
                        UUID pB = childTag.hasUUID("parent_b_uuid")
                            ? childTag.getUUID("parent_b_uuid") : null;

                        if (getUUID().equals(pA) || getUUID().equals(pB)) {
                            LiveNPCEntity child = NPCRegistry.getNPC(entry.getKey());
                            if (child != null && child.isAlive()) {
                                com.livemine.orphan.OrphanManager.getInstance()
                                    .registerIfOrphan(child, sl);
                            }
                        }
                    }
                } catch (Exception e) {
                    LiveMineMod.LOGGER.error("OrphanManager failed on death", e);
                }

                // 4. NBT метка.
                CompoundTag tag = LiveMineSavedData.get(sl).loadNPCData(getUUID());
                tag.putBoolean("dead", true);
                LiveMineSavedData.get(sl).saveNPCData(getUUID(), tag);

                // 5. Кладбище.
                try {
                    String rawId = getVillageRawId();
                    if (!rawId.isEmpty()) {
                        com.livemine.CemeteryManager.getInstance()
                            .recordDeath(rawId, getUUID(),
                                getCustomNameTag(), cause.getMsgId(),
                                sl.getDayTime() / 24000L);
                    }
                } catch (Exception e) {
                    LiveMineMod.LOGGER.error("CemeteryManager failed on death", e);
                }

                // 6. Горе у ближайших.
                try {
                    var nearby = sl.getEntitiesOfClass(LiveNPCEntity.class,
                        getBoundingBox().inflate(24.0));
                    for (LiveNPCEntity witness : nearby) {
                        if (witness.getUUID().equals(getUUID())) continue;
                        NPCRelationship.onDeath(witness, getUUID());
                    }
                } catch (Exception e) {
                    LiveMineMod.LOGGER.error("Emotion broadcast on death failed", e);
                }

                // 7. Питомец передаётся наследнику.
                try {
                    UUID heirId = findHeir(sl);
                    PetManager.getInstance().onOwnerDeath(getUUID(), heirId, sl);
                } catch (Exception e) {
                    LiveMineMod.LOGGER.error("PetManager.onOwnerDeath failed", e);
                }

                // 8. v3.5: Дом передаётся наследнику.
                try {
                    UUID heirId = findHeir(sl);
                    PropertyManager.getInstance().onOwnerDeath(getUUID(), heirId, sl);
                } catch (Exception e) {
                    LiveMineMod.LOGGER.error("PropertyManager.onOwnerDeath failed", e);
                }
            } catch (Exception e) {
                LiveMineMod.LOGGER.error("die(): post-death processing failed", e);
            }
        }

        super.die(cause);
        NPCRegistry.unregister(getUUID());
    }

    /**
     * v3.5: ищет наследника: супруг → первый живой ребёнок.
     */
    private UUID findHeir(ServerLevel sl) {
        UUID spouse = getSpouseUUID();
        if (spouse != null) {
            LiveNPCEntity s = NPCRegistry.getNPC(spouse);
            if (s != null && s.isAlive()) return spouse;
        }

        var data = LiveMineSavedData.get(sl);
        for (var entry : data.getAllNpcData().entrySet()) {
            CompoundTag t = entry.getValue();
            if (t.getBoolean("dead")) continue;

            UUID pA = t.hasUUID("parent_a_uuid") ? t.getUUID("parent_a_uuid") : null;
            UUID pB = t.hasUUID("parent_b_uuid") ? t.getUUID("parent_b_uuid") : null;

            if (getUUID().equals(pA) || getUUID().equals(pB)) {
                LiveNPCEntity c = NPCRegistry.getNPC(entry.getKey());
                if (c != null && c.isAlive()) return entry.getKey();
            }
        }
        return null;
    }

    @Override
    protected SoundEvent getAmbientSound() { return SoundEvents.VILLAGER_AMBIENT; }
    @Override
    protected SoundEvent getHurtSound(DamageSource src) { return SoundEvents.VILLAGER_HURT; }
    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.VILLAGER_DEATH; }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }

    @Override
    public boolean isPersistenceRequired() { return true; }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}