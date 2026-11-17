package cn.yumis.fight.modules.professions.platform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
/** 噩梦藤：像藤蔓一样贴着绯红/诡异菌木侧面生长，不接触地面。 */
public final class NightmareVineBlock extends HerbBushBlock {
    public NightmareVineBlock(Properties properties){
        super(properties);
    }
    static boolean isStem(BlockState state){
        String path=BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.contains("crimson_stem")||path.contains("warped_stem")
            ||path.contains("crimson_hyphae")||path.contains("warped_hyphae");
    }
    @Override public boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){
        for(Direction dir:Direction.Plane.HORIZONTAL){
            if(isStem(level.getBlockState(pos.relative(dir))))return true;
        }
        return false;
    }
}
