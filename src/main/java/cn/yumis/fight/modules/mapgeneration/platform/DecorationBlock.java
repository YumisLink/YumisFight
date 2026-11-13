package cn.yumis.fight.modules.mapgeneration.platform;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import java.util.*;

/** Floor props use their model geometry for selection and, where appropriate, collision. */
public class DecorationBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<DecorationBlock> CODEC=RecordCodecBuilder.mapCodec(instance->instance.group(
            Codec.STRING.fieldOf("decoration").forGetter(block->block.decoration),propertiesCodec()).apply(instance,DecorationBlock::new));
    protected final String decoration;
    private final List<Map<Direction,VoxelShape>> shapes;

    public DecorationBlock(String decoration,Properties properties){
        super(properties);
        this.decoration=decoration;
        this.shapes=DecorationDefinitions.find(decoration).shapes().stream().map(Shapes::rotateHorizontal).toList();
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));
    }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
    protected int variant(BlockState state){return 0;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return shapes.get(variant(state)).get(state.getValue(FACING));}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){var below=pos.below();return level.getBlockState(below).isFaceSturdy(level,below,Direction.UP);}
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction direction,BlockPos neighbourPos,BlockState neighbourState,RandomSource random){
        return direction==Direction.DOWN&&!state.canSurvive(level,pos)?Blocks.AIR.defaultBlockState():super.updateShape(state,level,ticks,pos,direction,neighbourPos,neighbourState,random);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!player.mayBuild())return InteractionResult.PASS;
        if(!level.isClientSide())level.setBlock(pos,state.setValue(FACING,state.getValue(FACING).getClockWise()),Block.UPDATE_ALL);
        return InteractionResult.SUCCESS;
    }
}
