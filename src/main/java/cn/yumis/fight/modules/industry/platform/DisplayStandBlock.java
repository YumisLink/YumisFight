package cn.yumis.fight.modules.industry.platform;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** A passive two-slot industrial endpoint, with collision confined to the pedestal. */
public final class DisplayStandBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE=Block.box(1,0,1,15,8,15);
    public DisplayStandBlock(Properties properties){super(properties);}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return simpleCodec(DisplayStandBlock::new);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new DisplayStandBlockEntity(pos,state);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPE;}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPE;}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(stack.getItem() instanceof ManaWand)return InteractionResult.PASS;
        interact(level,pos,player,hand);return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){interact(level,pos,player,InteractionHand.MAIN_HAND);return InteractionResult.SUCCESS;}
    private static void interact(Level level,BlockPos pos,Player player,InteractionHand hand){
        if(player instanceof ServerPlayer server&&level.getBlockEntity(pos) instanceof DisplayStandBlockEntity stand)try{stand.interact(server,hand);}catch(IllegalArgumentException failure){cn.yumis.fight.modules.industry.api.IndustrialPorts.message(server,failure.getMessage());}
    }
}
