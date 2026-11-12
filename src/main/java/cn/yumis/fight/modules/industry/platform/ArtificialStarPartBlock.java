package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.modules.industry.api.DeviceType;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import java.util.List;

/** Reserved cells of one artificial sun. Only the central pedestal owns a machine and drops. */
public final class ArtificialStarPartBlock extends Block {
    public static final int SIZE=5,HEIGHT=5,HALF=SIZE/2;
    public static final double BASE_RADIUS=2.45,BASE_HEIGHT=.9,SUN_Y=3.1,SUN_RADIUS=1.4;
    public static final int SUN_STEPS=14;
    public static final double SUN_CELL=2*SUN_RADIUS/SUN_STEPS;
    public static final double HAZARD_FRACTION=.2;
    public static final long HAZARD_INTERVAL_MILLIS=1000;
    public static final IntegerProperty X=IntegerProperty.create("offset_x",0,SIZE-1),
        Y=IntegerProperty.create("offset_y",0,HEIGHT-1),Z=IntegerProperty.create("offset_z",0,SIZE-1);
    private static final VoxelShape PEDESTAL=pedestal();
    private static final VoxelShape[] BASE_SHAPES=cells(PEDESTAL),SHAPES=shapes();
    public ArtificialStarPartBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(X,HALF).setValue(Y,0).setValue(Z,HALF));}
    @Override protected MapCodec<? extends Block> codec(){return simpleCodec(ArtificialStarPartBlock::new);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(X,Y,Z);}
    public static boolean isStar(BlockState state){return state.getBlock() instanceof MachineBlock machine&&machine.initial.device()==DeviceType.STAR;}
    public static BlockPos base(BlockGetter level,BlockPos pos){
        BlockState state=level.getBlockState(pos);return state.getBlock() instanceof ArtificialStarPartBlock?base(pos,state):pos;
    }
    private static BlockPos base(BlockPos pos,BlockState state){return pos.offset(HALF-state.getValue(X),-state.getValue(Y),HALF-state.getValue(Z));}
    public static boolean matches(BlockState state,int x,int y,int z){return state.getBlock() instanceof ArtificialStarPartBlock&&state.getValue(X)==x+HALF&&state.getValue(Y)==y&&state.getValue(Z)==z+HALF;}
    public static VoxelShape shape(int x,int y,int z){return SHAPES[(y*SIZE+z+HALF)*SIZE+x+HALF];}
    public static String hazardDescription(){return "运行时站在圆盘上，每秒受到最大生命值"+(int)(HAZARD_FRACTION*100)+"%的伤害。";}
    public static boolean sunCell(int x,int y,int z){
        double X=x+.5-SUN_STEPS/2d,Y=y+.5-SUN_STEPS/2d,Z=z+.5-SUN_STEPS/2d;
        return x>=0&&x<SUN_STEPS&&y>=0&&y<SUN_STEPS&&z>=0&&z<SUN_STEPS&&X*X+Y*Y+Z*Z<=SUN_STEPS*SUN_STEPS/4d;
    }
    private static VoxelShape pedestal(){
        VoxelShape volume=Shapes.empty();
        // Thin horizontal strips follow the round pedestal, rather than a solid 5x5 box.
        for(int strip=0;strip<20;strip++){
            double x0=-BASE_RADIUS+strip*BASE_RADIUS/10,x1=x0+BASE_RADIUS/10;
            double edge=Math.min(Math.abs(x0),Math.abs(x1)),depth=Math.sqrt(Math.max(0,BASE_RADIUS*BASE_RADIUS-edge*edge));
            volume=Shapes.or(volume,Shapes.box(.5+x0,0,.5-depth,.5+x1,BASE_HEIGHT,.5+depth));
        }
        volume=Shapes.or(volume,Shapes.box(.16,BASE_HEIGHT,.16,.84,1.065,.84));
        return volume;
    }
    private static VoxelShape[] shapes(){
        VoxelShape volume=PEDESTAL;
        for(int y=0;y<SUN_STEPS;y++)for(int x=0;x<SUN_STEPS;x++){
            int first=0,last=SUN_STEPS-1;while(first<SUN_STEPS&&!sunCell(x,y,first))first++;if(first==SUN_STEPS)continue;
            while(!sunCell(x,y,last))last--;
            double x0=.5-SUN_RADIUS+x*SUN_CELL,y0=SUN_Y-SUN_RADIUS+y*SUN_CELL,z0=.5-SUN_RADIUS+first*SUN_CELL;
            volume=Shapes.or(volume,Shapes.box(x0,y0,z0,x0+SUN_CELL,y0+SUN_CELL,.5-SUN_RADIUS+(last+1)*SUN_CELL));
        }
        return cells(volume);
    }
    private static VoxelShape[] cells(VoxelShape volume){
        VoxelShape[] result=new VoxelShape[SIZE*SIZE*HEIGHT];
        for(int y=0;y<HEIGHT;y++)for(int z=-HALF;z<=HALF;z++)for(int x=-HALF;x<=HALF;x++)
            result[(y*SIZE+z+HALF)*SIZE+x+HALF]=Shapes.join(volume.move(-x,-y,-z),Shapes.block(),BooleanOp.AND).optimize();
        return result;
    }
    public static boolean canPlace(BlockPlaceContext context){
        Level level=context.getLevel();BlockPos center=context.getClickedPos();Player player=context.getPlayer();
        boolean fits=center.getY()>=level.getMinY()&&center.getY()+HEIGHT-1<=level.getMaxY();
        for(int y=0;fits&&y<HEIGHT;y++)for(int z=-HALF;fits&&z<=HALF;z++)for(int x=-HALF;fits&&x<=HALF;x++){
            BlockPos pos=center.offset(x,y,z);
            if(!level.hasChunkAt(pos)||!level.getWorldBorder().isWithinBounds(pos)||(player!=null&&(!level.mayInteract(player,pos)||!player.mayUseItemAt(pos,context.getClickedFace(),context.getItemInHand())))){fits=false;break;}
            BlockState state=level.getBlockState(pos);
            if((x==0&&y==0&&z==0)?!state.canBeReplaced(context):!state.isAir()){fits=false;break;}
            if(!state.getFluidState().isEmpty()||level.getBlockEntity(pos)!=null){fits=false;break;}
            VoxelShape shape=BASE_SHAPES[(y*SIZE+z+HALF)*SIZE+x+HALF];
            if(!shape.isEmpty()&&!level.getEntitiesOfClass(LivingEntity.class,shape.bounds().move(pos),entity->entity.isAlive()&&!entity.isSpectator()).isEmpty())fits=false;
        }
        if(!fits&&player!=null&&!level.isClientSide())player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("人造太阳需要以放置点为中心，留出5×5地面、上方共5格高的空位。"));
        return fits;
    }
    public static boolean assemble(ServerLevel level,BlockPos center){
        if(!isStar(level.getBlockState(center)))return false;
        for(int y=0;y<HEIGHT;y++)for(int z=-HALF;z<=HALF;z++)for(int x=-HALF;x<=HALF;x++){
            if(x==0&&y==0&&z==0)continue;BlockPos pos=center.offset(x,y,z);
            if(!level.hasChunkAt(pos)||!level.getBlockState(pos).isAir())return false;
        }
        for(int y=0;y<HEIGHT;y++)for(int z=-HALF;z<=HALF;z++)for(int x=-HALF;x<=HALF;x++){
            if(x==0&&y==0&&z==0)continue;
            BlockState part=IndustrialContent.STAR_PART.get().defaultBlockState().setValue(X,x+HALF).setValue(Y,y).setValue(Z,z+HALF);
            if(!level.setBlock(center.offset(x,y,z),part,Block.UPDATE_ALL))return false;
        }
        return true;
    }
    public static void removeParts(ServerLevel level,BlockPos center){
        for(int y=0;y<HEIGHT;y++)for(int z=-HALF;z<=HALF;z++)for(int x=-HALF;x<=HALF;x++){
            BlockPos pos=center.offset(x,y,z);if(matches(level.getBlockState(pos),x,y,z))level.removeBlock(pos,false);
        }
    }
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.INVISIBLE;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){
        int x=state.getValue(X)-HALF,y=state.getValue(Y),z=state.getValue(Z)-HALF;
        BlockState controller=level.getBlockState(base(pos,state));
        return isStar(controller)&&controller.getValue(MachineBlock.ACTIVE)?shape(x,y,z):BASE_SHAPES[(y*SIZE+z+HALF)*SIZE+x+HALF];
    }
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return BASE_SHAPES[(state.getValue(Y)*SIZE+state.getValue(Z))*SIZE+state.getValue(X)];}
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder params){return List.of();}
    @Override protected void affectNeighborsAfterRemoval(BlockState state,ServerLevel level,BlockPos pos,boolean moved){
        super.affectNeighborsAfterRemoval(state,level,pos,moved);if(level.getBlockState(pos).getBlock()==this)return;
        BlockPos center=base(pos,state);if(isStar(level.getBlockState(center)))level.destroyBlock(center,true);
    }
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){
        if(!level.isClientSide()&&player.isCreative()){BlockPos center=base(pos,state);if(isStar(level.getBlockState(center)))level.destroyBlock(center,false,player);}
        return super.playerWillDestroy(level,pos,state,player);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        BlockPos center=base(pos,state);return level.getBlockState(center).useWithoutItem(level,player,new BlockHitResult(hit.getLocation(),hit.getDirection(),center,hit.isInside()));
    }
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(stack.getItem() instanceof ManaWand)return InteractionResult.PASS;
        BlockPos center=base(pos,state);return level.getBlockState(center).useItemOn(stack,level,player,hand,new BlockHitResult(hit.getLocation(),hit.getDirection(),center,hit.isInside()));
    }
}
