package cn.yumis.fight.modules.dungeons.api;

import com.mojang.serialization.MapCodec;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class AwakeningTowerStele extends DoublePlantBlock {
    public static final EnumProperty<AwakeningTower> TOWER=EnumProperty.create("tower",AwakeningTower.class);
    private static final MapCodec<AwakeningTowerStele> CODEC=simpleCodec(AwakeningTowerStele::new);
    private static final VoxelShape FOOT=Shapes.or(Block.box(1,0,1,15,2,15),Block.box(2,2,2,14,4,14));
    private static final VoxelShape LOWER_NS=Shapes.or(FOOT,Block.box(3,4,5,13,16,11));
    private static final VoxelShape LOWER_EW=Shapes.or(FOOT,Block.box(5,4,3,11,16,13));
    private static final VoxelShape UPPER_NS=Shapes.or(Block.box(3,0,5,13,13,11),Block.box(5,13,5,11,16,11));
    private static final VoxelShape UPPER_EW=Shapes.or(Block.box(5,0,3,11,13,13),Block.box(5,13,5,11,16,11));
    private static BiConsumer<ServerPlayer,BlockPos> interaction;

    public AwakeningTowerStele(Properties properties){
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.SOUTH).setValue(TOWER,AwakeningTower.ROOT));
    }

    public static void install(BiConsumer<ServerPlayer,BlockPos> next){interaction=next;}

    public static AwakeningTower at(ServerPlayer player,BlockPos pos){
        Level level=player.level();
        if(!Level.OVERWORLD.equals(level.dimension())||!player.isWithinBlockInteractionRange(pos,0)
            ||!level.getChunkSource().hasChunk(pos.getX()>>4,pos.getZ()>>4))return null;
        BlockState state=level.getBlockState(pos);
        if(!(state.getBlock() instanceof AwakeningTowerStele))return null;
        var half=state.getValue(HALF);
        return level.getBlockState(pos.relative(half.getDirectionToOther()))==state.setValue(HALF,half.getOtherHalf())
            ?state.getValue(TOWER):null;
    }

    @Override public MapCodec<AwakeningTowerStele> codec(){return CODEC;}

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){
        super.createBlockStateDefinition(builder);
        builder.add(BlockStateProperties.HORIZONTAL_FACING,TOWER);
    }

    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){
        if(state.getValue(HALF)==DoubleBlockHalf.UPPER)return super.canSurvive(state,level,pos);
        return level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP);
    }

    @Override protected BlockState rotate(BlockState state,Rotation rotation){
        return state.setValue(BlockStateProperties.HORIZONTAL_FACING,rotation.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    @Override protected BlockState mirror(BlockState state,Mirror mirror){
        return rotate(state,mirror.getRotation(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){
        boolean eastWest=state.getValue(BlockStateProperties.HORIZONTAL_FACING).getAxis()==Direction.Axis.X;
        if(state.getValue(HALF)==DoubleBlockHalf.UPPER)return eastWest?UPPER_EW:UPPER_NS;
        return eastWest?LOWER_EW:LOWER_NS;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(player instanceof ServerPlayer serverPlayer&&interaction!=null)interaction.accept(serverPlayer,pos);
        return InteractionResult.SUCCESS;
    }

    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        return useWithoutItem(state,level,pos,player,hit);
    }
}
