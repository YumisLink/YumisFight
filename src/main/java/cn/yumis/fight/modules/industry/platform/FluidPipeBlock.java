package cn.yumis.fight.modules.industry.platform;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Six independent, persisted connections; placement facing never chooses the fluid direction. */
public final class FluidPipeBlock extends BaseEntityBlock {
    public static final BooleanProperty[] SIDES={BlockStateProperties.DOWN,BlockStateProperties.UP,BlockStateProperties.NORTH,BlockStateProperties.SOUTH,BlockStateProperties.WEST,BlockStateProperties.EAST};
    private static final VoxelShape[] SHAPES=new VoxelShape[64];
    static {
        for(int mask=0;mask<64;mask++){
            boolean cased=mask==0;for(int axis=0;axis<3;axis++){int count=0;for(Direction side:Direction.values())if(side.ordinal()/2!=axis&&(mask&bit(side))!=0)count++;if(count>2)cased=true;}
            VoxelShape shape=cased?Block.box(3,3,3,13,13,13):Block.box(4,4,4,12,12,12);
            for(Direction side:Direction.values())if((mask&bit(side))!=0){int x=side.getStepX(),y=side.getStepY(),z=side.getStepZ();shape=Shapes.or(shape,Block.box(x<0?0:4,y<0?0:4,z<0?0:4,x>0?16:12,y>0?16:12,z>0?16:12),Block.box(x<0?0:x>0?14:3,y<0?0:y>0?14:3,z<0?0:z>0?14:3,x<0?2:x>0?16:13,y<0?2:y>0?16:13,z<0?2:z>0?16:13));}
            SHAPES[mask]=shape;
        }
    }
    public final int grade;
    public FluidPipeBlock(int grade,Properties properties){super(properties);this.grade=grade;BlockState state=stateDefinition.any();for(var side:SIDES)state=state.setValue(side,false);registerDefaultState(state);}
    public static int bit(Direction side){return 1<<side.ordinal();}
    public static String directionName(Direction side){return switch(side){case DOWN->"下";case UP->"上";case NORTH->"北";case SOUTH->"南";case WEST->"西";case EAST->"东";};}
    public static int mask(BlockState state){int mask=0;for(Direction side:Direction.values())if(state.getValue(SIDES[side.ordinal()]))mask|=bit(side);return mask;}
    public static boolean straight(int mask){return mask==3||mask==12||mask==48;}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return simpleCodec(p->new FluidPipeBlock(grade,p));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(SIDES);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new FluidPipeBlockEntity(pos,state);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide()?null:createTickerHelper(type,IndustrialContent.FLUID_PIPE_BE.get(),FluidPipeBlockEntity::tick);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPES[mask(state)];}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,net.minecraft.world.level.redstone.Orientation orientation,boolean moved){if(level.getBlockEntity(pos) instanceof FluidPipeBlockEntity pipe)pipe.refreshShape();}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity owner,ItemStack stack){super.setPlacedBy(level,pos,state,owner,stack);if(level instanceof ServerLevel&&level.getBlockEntity(pos) instanceof FluidPipeBlockEntity pipe)pipe.initialize(owner instanceof Player p?p:null);}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,net.minecraft.world.InteractionHand hand,BlockHitResult hit){return InteractionResult.PASS;}
    public static Direction hitSide(BlockPos pos,Vec3 hit,Direction fallback){
        Vec3 local=hit.subtract(Vec3.atCenterOf(pos));double x=Math.abs(local.x),y=Math.abs(local.y),z=Math.abs(local.z);
        if(Math.max(x,Math.max(y,z))<.20)return fallback;
        return x>=y&&x>=z?(local.x<0?Direction.WEST:Direction.EAST):y>=z?(local.y<0?Direction.DOWN:Direction.UP):(local.z<0?Direction.NORTH:Direction.SOUTH);
    }
    @Override protected BlockState rotate(BlockState state,Rotation rotation){BlockState result=state;for(Direction side:Direction.values())result=result.setValue(SIDES[rotation.rotate(side).ordinal()],state.getValue(SIDES[side.ordinal()]));return result;}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){BlockState result=state;for(Direction side:Direction.values())result=result.setValue(SIDES[mirror.mirror(side).ordinal()],state.getValue(SIDES[side.ordinal()]));return result;}
}
