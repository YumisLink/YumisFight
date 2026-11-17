package cn.yumis.fight.modules.professions.platform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
/** 风暴藤：贴在尖峰岩壁侧面的藤蔓方块，不接触地面。 */
public final class StormVineBlock extends HerbBushBlock {
    public StormVineBlock(Properties properties){
        super(properties);
    }
    static boolean isRock(BlockState state){
        String path=BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.contains("stone")||path.contains("deepslate")||path.contains("tuff")||path.contains("andesite")
            ||path.contains("granite")||path.contains("diorite")||path.contains("cobblestone")||path.contains("gravel");
    }
    @Override public boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){
        for(Direction dir:Direction.Plane.HORIZONTAL){
            if(isRock(level.getBlockState(pos.relative(dir))))return true;
        }
        return false;
    }
}
