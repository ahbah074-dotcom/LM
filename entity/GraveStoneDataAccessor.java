package com.livemine.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;

/**
 * Статические аксессоры для GraveStoneEntity.
 *
 * Вынесены в отдельный класс для переиспользования между Entity и Renderer.
 * Регистрируются один раз при загрузке класса.
 */
public final class GraveStoneDataAccessor {

    public static final EntityDataAccessor<String> NAME =
        SynchedEntityData.defineId(GraveStoneEntity.class, EntityDataSerializers.STRING);

    public static final EntityDataAccessor<String> EPITHET =
        SynchedEntityData.defineId(GraveStoneEntity.class, EntityDataSerializers.STRING);

    public static final EntityDataAccessor<String> LIFE_DESCRIPTION =
        SynchedEntityData.defineId(GraveStoneEntity.class, EntityDataSerializers.STRING);

    private GraveStoneDataAccessor() {}
}
