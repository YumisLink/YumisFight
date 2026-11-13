package cn.yumis.fight.modules.mapgeneration.platform;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.world.phys.BlockHitResult;
import java.util.Map;

/** An anchored block, not a falling entity: only its stone surface gently bobs over air. */
public final class FloatingShardBlock extends BaseEntityBlock {
    public static final MapCodec<FloatingShardBlock> CODEC=simpleCodec(FloatingShardBlock::new);
    public static final EnumProperty<Direction> FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty FLOATING=BooleanProperty.create("floating");
    public static final double AMPLITUDE=.08,PERIOD_TICKS=80,HEIGHT=.10;
    private final Map<Direction,VoxelShape> shapes;
    public FloatingShardBlock(Properties properties){
        super(properties);
        shapes=Shapes.rotateHorizontal(DecorationDefinitions.find("palace_floating_shard").shapes().getFirst());
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(FLOATING,false));
    }
    @Override protected MapCodec<FloatingShardBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,FLOATING);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new FloatingShardBlockEntity(pos,state);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.INVISIBLE;}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite()).setValue(FLOATING,context.getLevel().getBlockState(context.getClickedPos().below()).isAir());}
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction direction,BlockPos neighbourPos,BlockState neighbourState,RandomSource random){
        return direction==Direction.DOWN?state.setValue(FLOATING,neighbourState.isAir()):super.updateShape(state,level,ticks,pos,direction,neighbourPos,neighbourState,random);
    }
    /** World time and a position phase keep visuals and physical shape on the same timeline. */
    public static double bob(double ticks,BlockPos pos){return HEIGHT+AMPLITUDE*Math.sin((ticks%PERIOD_TICKS)*Math.PI*2/PERIOD_TICKS+Math.floorMod(pos.asLong(),80)*Math.PI*2/80);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){
        double y=state.getValue(FLOATING)&&level instanceof Level world?bob(world.getGameTime(),pos):0;
        return shapes.get(state.getValue(FACING)).move(0,y,0);
    }
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!player.mayBuild())return InteractionResult.PASS;
        if(!level.isClientSide())level.setBlock(pos,state.setValue(FACING,state.getValue(FACING).getClockWise()),Block.UPDATE_ALL);
        return InteractionResult.SUCCESS;
    }
}
