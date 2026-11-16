package cn.yumis.fight.modules.industry.platform;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A small luminous matrix; the model exporter shares this outer outline. */
final class TechnologyBlock extends Block {
    private static final VoxelShape SHAPE=Block.box(3,3,3,13,13,13);
    TechnologyBlock(Properties properties){super(properties.noOcclusion().lightLevel(state->10));}
    @Override protected MapCodec<? extends Block> codec(){return simpleCodec(TechnologyBlock::new);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPE;}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPE;}
}
