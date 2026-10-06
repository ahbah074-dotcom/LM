package com.livemine.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * Маркер кладбища.
 */
public class CemeteryMarkerBlock extends BaseEntityBlock {

    public static final MapCodec<CemeteryMarkerBlock> CODEC = simpleCodec(CemeteryMarkerBlock::new);

    public CemeteryMarkerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CemeteryMarkerBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof CemeteryMarkerBlockEntity marker) {
            int count = marker.getGraveCount();
            if (count == 0) {
                player.sendSystemMessage(Component.literal(
                    "§6Кладбище§r — здесь пока нет захоронений."
                ));
            } else {
                player.sendSystemMessage(Component.literal(
                    "§6Кладбище§r — захоронено: §f" + count + "§r NPC."
                ));
            }
        }
        return InteractionResult.CONSUME;
    }
}
