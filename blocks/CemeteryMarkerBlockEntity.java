package com.livemine.blocks;

import com.livemine.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * BlockEntity маркера кладбища.
 */
public class CemeteryMarkerBlockEntity extends BlockEntity {

    public static final class GraveRecord {
        public UUID npcUUID;
        public String name;
        public String epitaph;
        public long deathDay;

        public GraveRecord(UUID uuid, String name, String epitaph, long day) {
            this.npcUUID = uuid;
            this.name = name != null ? name : "";
            this.epitaph = epitaph != null ? epitaph : "";
            this.deathDay = day;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            if (npcUUID != null) tag.putUUID("uuid", npcUUID);
            tag.putString("name", name);
            tag.putString("epitaph", epitaph);
            tag.putLong("day", deathDay);
            return tag;
        }

        public static GraveRecord load(CompoundTag tag) {
            UUID uuid = tag.hasUUID("uuid") ? tag.getUUID("uuid") : null;
            return new GraveRecord(
                uuid,
                tag.getString("name"),
                tag.getString("epitaph"),
                tag.getLong("day")
            );
        }
    }

    private final List<GraveRecord> graves = new ArrayList<>();
    private String villageId = "";

    public CemeteryMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CEMETERY_MARKER_BE.get(), pos, state);
    }

    public void addGrave(GraveRecord record) {
        graves.add(record);
        setChanged();
    }

    public void clearGraves() {
        graves.clear();
        setChanged();
    }

    public int getGraveCount() { return graves.size(); }
    public List<GraveRecord> getGraves() { return graves; }

    public String getVillageId() { return villageId; }
    public void setVillageId(String id) {
        this.villageId = id != null ? id : "";
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        ListTag list = new ListTag();
        for (GraveRecord grave : graves) list.add(grave.save());
        tag.put("graves", list);
        tag.putString("village_id", villageId);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        graves.clear();

        ListTag list = tag.getList("graves", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            graves.add(GraveRecord.load(list.getCompound(i)));
        }
        villageId = tag.getString("village_id");
    }
}
