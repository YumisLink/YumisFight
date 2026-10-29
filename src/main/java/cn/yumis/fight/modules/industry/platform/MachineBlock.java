package cn.yumis.fight.modules.industry.platform;
import cn.yumis.fight.modules.industry.api.DeviceType;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import java.util.*;

public final class MachineBlock extends BaseEntityBlock {
    public static final IntegerProperty TIER=IntegerProperty.create("tier",1,5);
    public static final EnumProperty<Direction> FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty CONNECTED=BooleanProperty.create("connected"),ACTIVE=BooleanProperty.create("active");
    public final IndustrialContent.Spec initial;
    public MachineBlock(IndustrialContent.Spec spec,BlockBehaviour.Properties props){super(props);initial=spec;registerDefaultState(stateDefinition.any().setValue(TIER,spec.tier()).setValue(FACING,Direction.NORTH).setValue(CONNECTED,false).setValue(ACTIVE,false));}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return simpleCodec(p->new MachineBlock(initial,p));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(TIER,FACING,CONNECTED,ACTIVE);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new MachineBlockEntity(pos,state);}
    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext c){
        if(initial.device().has(DeviceType.Capability.WIND)){if(c.getClickedPos().getY()+2>c.getLevel().getMaxY())return null;for(int i=1;i<=2;i++)if(!c.getLevel().getBlockState(c.getClickedPos().above(i)).canBeReplaced(c))return null;}
        return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity owner,ItemStack stack){super.setPlacedBy(level,pos,state,owner,stack);if(level instanceof ServerLevel server&&level.getBlockEntity(pos) instanceof MachineBlockEntity be){if(initial.device().has(DeviceType.Capability.WIND))WindGeneratorPartBlock.ensure(server,pos);IndustrialWorld.get(server).placed(be);}}
    @Override protected void affectNeighborsAfterRemoval(BlockState state,ServerLevel level,BlockPos pos,boolean moved){super.affectNeighborsAfterRemoval(state,level,pos,moved);if(initial.device().has(DeviceType.Capability.WIND)&&level.getBlockState(pos).getBlock()!=this)WindGeneratorPartBlock.removeUpper(level,pos);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){if(!level.isClientSide()&&level.getBlockEntity(pos) instanceof MachineBlockEntity be)IndustrialWorld.get((ServerLevel)level).opening.prepare((net.minecraft.server.level.ServerPlayer)player,be);return InteractionResult.SUCCESS;}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){if(stack.getItem() instanceof ManaWand)return InteractionResult.PASS;if(IndustrialUpgrades.recognized(stack)){if(player instanceof net.minecraft.server.level.ServerPlayer server)try{IndustrialUpgrades.use(server,hand,(MachineBlockEntity)level.getBlockEntity(pos));}catch(RuntimeException failure){cn.yumis.fight.modules.industry.api.IndustrialPorts.message(server,failure.getMessage());}return InteractionResult.SUCCESS;}if(!level.isClientSide())try(var tx=net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()){if(net.neoforged.neoforge.transfer.fluid.FluidUtil.interactWithFluidHandler(player,hand,level,pos,hit.getDirection(),tx)){tx.commit();return InteractionResult.SUCCESS;}}return InteractionResult.TRY_WITH_EMPTY_HAND;}
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder params){
        BlockEntity raw=params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if(!(raw instanceof MachineBlockEntity be))return List.of(new ItemStack(asItem()));
        return List.of(IndustrialContent.stack(be.spec().id(),1));
    }
}
