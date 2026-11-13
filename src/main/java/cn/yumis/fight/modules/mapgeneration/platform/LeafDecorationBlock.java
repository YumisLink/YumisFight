package cn.yumis.fight.modules.mapgeneration.platform;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Vanilla segment placement with a persistent choice of flat litter patterns. */
public final class LeafDecorationBlock extends LeafLitterBlock {
    public static final MapCodec<LeafLitterBlock> CODEC=simpleCodec(LeafDecorationBlock::new);
    public static final IntegerProperty LAYOUT=IntegerProperty.create("layout",0,DecorationDefinitions.LEAF_LAYOUTS-1);
    public LeafDecorationBlock(Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(LAYOUT,0));}
    @Override protected MapCodec<LeafLitterBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(LAYOUT);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        var state=super.getStateForPlacement(context);
        return context.getLevel().getBlockState(context.getClickedPos()).is(this)?state:
                state.setValue(LAYOUT,Math.floorMod(Mth.getSeed(context.getClickedPos()),DecorationDefinitions.LEAF_LAYOUTS));
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!player.mayBuild())return InteractionResult.PASS;
        var next=player.isShiftKeyDown()?state.setValue(FACING,state.getValue(FACING).getClockWise()):state.cycle(LAYOUT);
        if(!level.isClientSide())level.setBlock(pos,next,Block.UPDATE_ALL);
        return InteractionResult.SUCCESS;
    }
}
