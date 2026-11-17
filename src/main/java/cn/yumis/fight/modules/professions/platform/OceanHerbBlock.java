package cn.yumis.fight.modules.professions.platform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
/** 海底草药：像海带一样长在海床上，含水状态不排开海水。 */
public class OceanHerbBlock extends HerbBushBlock implements SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED=BlockStateProperties.WATERLOGGED;
    public OceanHerbBlock(Properties properties){
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(WATERLOGGED,true));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){
        builder.add(WATERLOGGED);
    }
    @Override public FluidState getFluidState(BlockState state){
        return state.getValue(WATERLOGGED)?Fluids.WATER.getSource(false):super.getFluidState(state);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        return defaultBlockState().setValue(WATERLOGGED,context.getLevel().getFluidState(context.getClickedPos()).is(FluidTags.WATER));
    }
    @Override public boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){
        BlockState below=level.getBlockState(pos.below());
        String path=BuiltInRegistries.BLOCK.getKey(below.getBlock()).getPath();
        boolean floor=path.contains("sand")||path.contains("gravel")||path.equals("dirt")||path.equals("clay")
            ||path.contains("mud")||path.equals("grass_block")||path.equals("stone");
        if(!floor)return false;
        return level.getFluidState(pos).is(FluidTags.WATER)||state.getValue(WATERLOGGED);
    }
}
