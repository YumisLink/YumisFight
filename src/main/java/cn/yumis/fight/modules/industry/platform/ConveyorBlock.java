package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.modules.industry.api.IndustrialPorts;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Passive low-profile belt; item queues and living passengers move independently. */
public final class ConveyorBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING=BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE=Block.box(0,0,0,16,ConveyorGeometry.HEIGHT*16,16);
    private static final java.util.Map<Entity,Long> MOVED=java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    public final boolean hopper;
    public ConveyorBlock(boolean hopper,Properties properties){super(properties);this.hopper=hopper;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return simpleCodec(p->new ConveyorBlock(hopper,p));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection());}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ConveyorBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide()?null:createTickerHelper(type,IndustrialContent.CONVEYOR_BE.get(),ConveyorBlockEntity::tick);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPE;}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPE;}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return rotate(state,mirror.getRotation(state.getValue(FACING)));}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(stack.getItem() instanceof ManaWand||player.isShiftKeyDown()&&stack.getItem() instanceof BlockItem)return InteractionResult.PASS;
        interact(level,pos,player,hand);return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){interact(level,pos,player,InteractionHand.MAIN_HAND);return InteractionResult.SUCCESS;}
    private static void interact(Level level,BlockPos pos,Player player,InteractionHand hand){
        if(player instanceof ServerPlayer server&&level.getBlockEntity(pos) instanceof ConveyorBlockEntity belt)try{belt.interact(server,hand);}catch(RuntimeException failure){IndustrialPorts.message(server,failure.getMessage());}
    }
    @Override public void stepOn(Level level,BlockPos pos,BlockState state,Entity entity){
        super.stepOn(level,pos,state,entity);
        if(!(entity instanceof LivingEntity living)||!living.isAlive()||!entity.onGround()||entity.isPassenger()||!living.isPushable())return;
        if(entity instanceof Player player){if(!level.isClientSide()||!player.isLocalPlayer()||player.isShiftKeyDown()||player.isSpectator()||player.getAbilities().flying)return;}else if(level.isClientSide())return;
        BlockPos support=BlockPos.containing(entity.getX(),entity.getY()-.025,entity.getZ());
        if(!support.equals(pos)||Math.abs(entity.getY()-(pos.getY()+ConveyorGeometry.HEIGHT))>.075)return;
        long now=level.getGameTime();if(MOVED.getOrDefault(entity,Long.MIN_VALUE)==now)return;MOVED.put(entity,now);
        Direction facing=state.getValue(FACING);Vec3 motion=new Vec3(facing.getStepX()*.1,0,facing.getStepZ()*.1);
        double lateral=facing.getAxis()==Direction.Axis.X?pos.getZ()+.5-entity.getZ():pos.getX()+.5-entity.getX();
        double correction=Math.clamp(lateral,-.04,.04);
        if(entity.getDeltaMovement().horizontalDistanceSqr()<.0025)motion=motion.add(facing.getAxis()==Direction.Axis.X?0:correction,0,facing.getAxis()==Direction.Axis.X?correction:0);
        entity.move(MoverType.SELF,motion);
    }
}
