package cn.yumis.fight.modules.professions.platform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.BushBlock;
/** 野生草药丛：主世界长在土类方块上，下界长在菌岩/下界岩/黑石/下界砖等方块上；无碰撞。 */
public class HerbBushBlock extends BushBlock {
    public HerbBushBlock(Properties properties){
        super(properties);
    }
    /** 可生长的基底：主世界土类按方块实例判定，下界按方块 id 判定，避免绑定具体类名。 */
    private static boolean soil(BlockState state){
        if(state.is(Blocks.GRASS_BLOCK)||state.is(Blocks.DIRT)||state.is(Blocks.PODZOL)||state.is(Blocks.COARSE_DIRT)
            ||state.is(Blocks.ROOTED_DIRT)||state.is(Blocks.FARMLAND)||state.is(Blocks.MOSS_BLOCK)||state.is(Blocks.MUD))return true;
        String path=BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.contains("nylium")
            ||path.contains("netherrack")
            ||path.contains("basalt")
            ||path.contains("blackstone")
            ||path.contains("soul_soil")
            ||path.contains("soul_sand")
            ||path.contains("nether_brick")
            ||path.equals("magma_block")
            ||path.equals("sand")||path.endsWith("_sand")
            ||path.contains("gravel")
            ||path.contains("stone")
            ||path.equals("obsidian")
            ||path.contains("bone_block")
            ||path.contains("end_stone");
    }
    /** 直接重写存活判定，保证下界方块上也能立住。 */
    @Override public boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){
        return soil(level.getBlockState(pos.below()));
    }
    /** 种子能否种在该方块上。 */
    public static boolean canPlantOn(BlockState state){
        return soil(state);
    }
}
