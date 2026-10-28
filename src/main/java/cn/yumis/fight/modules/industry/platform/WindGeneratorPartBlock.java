package cn.yumis.fight.modules.industry.platform;
import cn.yumis.fight.modules.industry.api.DeviceType;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import java.util.List;

/** The two reserved upper cells of a wind generator; only the base owns inventory/energy. */
public final class WindGeneratorPartBlock extends Block {
    private static final VoxelShape MIDDLE_COLLISION=Shapes.or(Block.box(6,0,6,10,16,10),Block.box(5,2,5,11,3,11),Block.box(5,12,5,11,13,11));
    private static final VoxelShape HEAD_COLLISION=Shapes.or(Block.box(6,0,6,10,11,10),Block.box(5,5,5,11,7,11),Block.box(4,11,4,12,13,12),Block.box(5,13,5,11,14,11),Block.box(7,14,7,9,16,9));
    public static final IntegerProperty PART=IntegerProperty.create("part",1,2);
    public static final EnumProperty<Direction> FACING=BlockStateProperties.HORIZONTAL_FACING;
    public WindGeneratorPartBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(PART,1).setValue(FACING,Direction.NORTH));}
    @Override protected MapCodec<? extends Block> codec(){return simpleCodec(WindGeneratorPartBlock::new);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(PART,FACING);}
    public static BlockPos base(LevelReader level,BlockPos pos){
        BlockState state=level.getBlockState(pos);
        return state.getBlock() instanceof WindGeneratorPartBlock?pos.below(state.getValue(PART)):pos;
    }
    public static boolean isWind(BlockState state){return state.getBlock() instanceof MachineBlock b&&b.initial.device().has(DeviceType.Capability.WIND);}
    public static boolean complete(LevelReader level,BlockPos pos){
        if(!isWind(level.getBlockState(pos)))return false;
        for(int part=1;part<=2;part++){BlockState state=level.getBlockState(pos.above(part));if(!(state.getBlock() instanceof WindGeneratorPartBlock)||state.getValue(PART)!=part)return false;}
        return true;
    }
    /** Repairs missing current-generation parts without replacing obstructing blocks. */
    public static boolean ensure(ServerLevel level,BlockPos pos){
        if(!isWind(level.getBlockState(pos))||pos.getY()+2>level.getMaxY())return false;
        for(int part=1;part<=2;part++){
            BlockState s=level.getBlockState(pos.above(part));
            if(!(s.getBlock() instanceof WindGeneratorPartBlock&&s.getValue(PART)==part)&&!s.canBeReplaced())return false;
        }
        Direction facing=level.getBlockState(pos).getValue(MachineBlock.FACING);
        for(int part=1;part<=2;part++){
            BlockState next=IndustrialContent.WIND_PART.get().defaultBlockState().setValue(PART,part).setValue(FACING,facing);
            if(!level.getBlockState(pos.above(part)).equals(next))level.setBlock(pos.above(part),next,3);
        }
        return true;
    }
    public static void removeUpper(ServerLevel level,BlockPos pos){
        for(int part=1;part<=2;part++){BlockPos upper=pos.above(part);BlockState state=level.getBlockState(upper);if(state.getBlock() instanceof WindGeneratorPartBlock&&state.getValue(PART)==part)level.removeBlock(upper,false);}
    }
    @Override protected void affectNeighborsAfterRemoval(BlockState state,ServerLevel level,BlockPos pos,boolean moved){
        super.affectNeighborsAfterRemoval(state,level,pos,moved);
        if(level.getBlockState(pos).getBlock()==this)return;
        BlockPos base=pos.below(state.getValue(PART));
        if(isWind(level.getBlockState(base)))level.destroyBlock(base,true);
    }
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){
        if(!level.isClientSide()&&player.isCreative()){
            BlockPos base=pos.below(state.getValue(PART));if(isWind(level.getBlockState(base)))level.destroyBlock(base,false,player);
        }
        return super.playerWillDestroy(level,pos,state,player);
    }
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder params){return List.of();}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return state.getValue(PART)==1?Block.box(5,0,5,11,16,11):Block.box(0,0,3,16,16,13);}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return state.getValue(PART)==1?MIDDLE_COLLISION:HEAD_COLLISION;}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        BlockPos base=base(level,pos);return level.getBlockState(base).useWithoutItem(level,player,new BlockHitResult(hit.getLocation(),hit.getDirection(),base,hit.isInside()));
    }
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(stack.getItem() instanceof ManaWand)return InteractionResult.PASS;
        BlockPos base=base(level,pos);return level.getBlockState(base).useItemOn(stack,level,player,hand,new BlockHitResult(hit.getLocation(),hit.getDirection(),base,hit.isInside()));
    }
}
