package cn.yumis.fight.modules.industry.platform;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;

/** A movable, permanent seed; growth belongs to nearby grass blocks' random ticks. */
public final class MagicFlowerBlock extends VegetationBlock {
    public MagicFlowerBlock(Properties properties){super(properties);}
    @Override protected MapCodec<? extends VegetationBlock> codec(){return simpleCodec(MagicFlowerBlock::new);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return net.minecraft.world.level.block.Block.column(6,0,12);}
}
