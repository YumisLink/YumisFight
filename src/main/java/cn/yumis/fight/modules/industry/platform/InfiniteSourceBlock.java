package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.modules.industry.api.IndustrialPorts;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

/** Creative acquisition only; once obtained, either source also works in survival. */
public final class InfiniteSourceBlock extends BaseEntityBlock {
    public final boolean liquid;
    public InfiniteSourceBlock(boolean liquid,BlockBehaviour.Properties properties){super(properties);this.liquid=liquid;}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return simpleCodec(p->new InfiniteSourceBlock(liquid,p));}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new InfiniteSourceBlockEntity(pos,state);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return liquid?Block.box(1,0,1,15,16,15):Shapes.block();}
    /** The four signs occupy the same central 8x8 pixels as their rendered texture. */
    private boolean interactive(BlockPos pos,BlockHitResult hit){
        if(liquid)return true;
        if(hit.getDirection().getAxis().isVertical())return false;
        var local=hit.getLocation().subtract(pos.getX(),pos.getY(),pos.getZ());
        double across=hit.getDirection().getAxis()==net.minecraft.core.Direction.Axis.X?local.z:local.x;
        return across>=.25&&across<=.75&&local.y>=.25&&local.y<=.75;
    }
    private static void interact(Level level,BlockPos pos,Player player,InteractionHand hand){
        if(player instanceof ServerPlayer server&&level.getBlockEntity(pos) instanceof InfiniteSourceBlockEntity source){
            try{source.interact(server,hand);}catch(RuntimeException failure){IndustrialPorts.message(server,failure.getMessage());}
        }
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!interactive(pos,hit))return InteractionResult.PASS;
        interact(level,pos,player,InteractionHand.MAIN_HAND);return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useItemOn(ItemStack held,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!interactive(pos,hit)||held.getItem() instanceof ManaWand)return InteractionResult.PASS;
        if(liquid&&!held.is(Items.BUCKET)&&(!(held.getItem() instanceof BucketItem)||FluidUtil.getFirstStackContained(held).getAmount()<1000))return InteractionResult.PASS;
        interact(level,pos,player,hand);return InteractionResult.SUCCESS;
    }
}
