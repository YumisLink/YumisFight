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
    public enum Slope implements net.minecraft.util.StringRepresentable {
        FLAT,UP,DOWN;
        public String getSerializedName(){return name().toLowerCase(java.util.Locale.ROOT);}
    }
    public static final EnumProperty<Direction> FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Slope> SLOPE=EnumProperty.create("slope",Slope.class);
    private static final java.util.Map<BlockState,VoxelShape> SHAPES=new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<Entity,Long> MOVED=java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    public final boolean hopper;
    public final int speed;
    public ConveyorBlock(boolean hopper,Properties properties){this(hopper,1,properties);}
    public ConveyorBlock(boolean hopper,int speed,Properties properties){super(properties);this.hopper=hopper;this.speed=speed;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(SLOPE,Slope.FLAT));}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return simpleCodec(p->new ConveyorBlock(hopper,speed,p));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,SLOPE);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return ConveyorConnections.adjusted(context.getClickedPos(),defaultBlockState().setValue(FACING,context.getHorizontalDirection()),context.getLevel()::getBlockState);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ConveyorBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide()?null:createTickerHelper(type,IndustrialContent.CONVEYOR_BE.get(),ConveyorBlockEntity::tick);}
    public static VoxelShape shape(BlockState state){return SHAPES.computeIfAbsent(state,key->{
        if(key.getValue(SLOPE)==Slope.FLAT)return Block.box(0,0,0,16,ConveyorGeometry.HEIGHT*16,16);
        VoxelShape result=Shapes.empty();Direction facing=key.getValue(FACING);
        for(int i=0;i<16;i++){
            double x=facing.getAxis()==Direction.Axis.X?i/16.0:0,z=facing.getAxis()==Direction.Axis.Z?i/16.0:0;
            double X=facing.getAxis()==Direction.Axis.X?(i+1)/16.0:1,Z=facing.getAxis()==Direction.Axis.Z?(i+1)/16.0:1;
            double a=ConveyorConnections.lift(key,x,z),b=ConveyorConnections.lift(key,X,Z);
            result=Shapes.or(result,Shapes.box(x,Math.min(a,b),z,X,Math.max(a,b)+ConveyorGeometry.HEIGHT,Z));
        }
        return result.optimize();
    });}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return shape(state);}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return shape(state);}
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moved){super.onPlace(state,level,pos,old,moved);ConveyorConnections.refresh(level,pos);}
    @Override protected void affectNeighborsAfterRemoval(BlockState state,net.minecraft.server.level.ServerLevel level,BlockPos pos,boolean moved){super.affectNeighborsAfterRemoval(state,level,pos,moved);ConveyorConnections.refresh(level,pos);}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,net.minecraft.world.level.redstone.Orientation orientation,boolean moved){ConveyorConnections.refresh(level,pos);}
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
    public static double passengerSurface(BlockState state,BlockPos pos,AABB bounds){
        double lift=Math.max(ConveyorConnections.lift(state,bounds.minX-pos.getX(),bounds.minZ-pos.getZ()),ConveyorConnections.lift(state,bounds.maxX-pos.getX(),bounds.maxZ-pos.getZ()));
        return pos.getY()+ConveyorGeometry.HEIGHT+Math.ceil(lift*16)/16;
    }
    @Override public void stepOn(Level level,BlockPos pos,BlockState state,Entity entity){
        super.stepOn(level,pos,state,entity);
        if(!(entity instanceof LivingEntity living)||!living.isAlive()||!entity.onGround()||entity.getDeltaMovement().y>0||entity.isPassenger()||!living.isPushable())return;
        if(entity instanceof Player player){if(!level.isClientSide()||!player.isLocalPlayer()||player.isShiftKeyDown()||player.isSpectator()||player.getAbilities().flying)return;}else if(level.isClientSide())return;
        double x=entity.getX()-pos.getX(),z=entity.getZ()-pos.getZ();
        if(x<0||x>1||z<0||z>1||Math.abs(entity.getY()-passengerSurface(state,pos,entity.getBoundingBox()))>.15)return;
        long now=level.getGameTime();if(MOVED.getOrDefault(entity,Long.MIN_VALUE)==now)return;MOVED.put(entity,now);
        Direction facing=state.getValue(FACING);double step=.1*speed;
        double dy=passengerSurface(state,pos,entity.getBoundingBox().move(facing.getStepX()*step,0,facing.getStepZ()*step))-passengerSurface(state,pos,entity.getBoundingBox());
        Vec3 motion=new Vec3(facing.getStepX()*step,dy,facing.getStepZ()*step);
        double lateral=facing.getAxis()==Direction.Axis.X?pos.getZ()+.5-entity.getZ():pos.getX()+.5-entity.getX();
        double correction=Math.clamp(lateral,-.04,.04);
        if(entity.getDeltaMovement().horizontalDistanceSqr()<.0025)motion=motion.add(facing.getAxis()==Direction.Axis.X?0:correction,0,facing.getAxis()==Direction.Axis.X?correction:0);
        entity.move(MoverType.SELF,motion);
        // Entity.move with zero/upward Y clears onGround. Preserve real support, never an edge in mid-air.
        if(entity.getDeltaMovement().y<=0&&!level.noCollision(entity,entity.getBoundingBox().move(0,-.08,0)))entity.setOnGround(true);
    }
}
