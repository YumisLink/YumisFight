package cn.yumis.fight.modules.dungeons.api;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class WaystonePedestalBlock extends BaseEntityBlock {
    public static final IntegerProperty STONE=IntegerProperty.create("stone",0,4);
    public static final MapCodec<WaystonePedestalBlock> CODEC=simpleCodec(WaystonePedestalBlock::new);
    public WaystonePedestalBlock(BlockBehaviour.Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(STONE,0));}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(STONE);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new WaystonePedestalBlockEntity(pos,state);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public boolean canEntityDestroy(BlockState state,BlockGetter level,BlockPos pos,net.minecraft.world.entity.Entity entity){return false;}
    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context){return context.getLevel().dimension()==Level.OVERWORLD?defaultBlockState():null;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return Block.box(1,0,1,15,14,15);}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return getShape(state,level,pos,context);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){if(player instanceof net.minecraft.server.level.ServerPlayer p&&level.getBlockEntity(pos) instanceof WaystonePedestalBlockEntity table)WaystoneActions.open(p,table);return InteractionResult.SUCCESS;}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return useWithoutItem(state,level,pos,player,hit);}
}
