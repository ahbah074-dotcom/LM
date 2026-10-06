package com.livemine.blocks;

import com.livemine.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import javax.annotation.Nullable;

/**
 * Общий костёр — место отдыха NPC.
 */
public class CommunalFirePitBlock extends BaseEntityBlock {

    public static final MapCodec<CommunalFirePitBlock> CODEC = simpleCodec(CommunalFirePitBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public CommunalFirePitBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, true));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CommunalFirePitBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                   BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, ModBlockEntities.COMMUNAL_FIRE_PIT_BE.get(),
            CommunalFirePitBlockEntity::tick);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) return;
        if (random.nextInt(5) != 0) return;

        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
        double y = pos.getY() + 0.5 + random.nextDouble() * 0.3;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4;

        level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 0.0, 0.03, 0.0);
        level.addParticle(ParticleTypes.FLAME, x, y, z, 0.0, 0.01, 0.0);
    }
}
