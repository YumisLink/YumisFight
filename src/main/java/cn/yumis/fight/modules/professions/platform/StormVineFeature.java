package cn.yumis.fight.modules.professions.platform;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
/** 在地表岩壁附近找裸露石头，把风暴藤贴到侧面。 */
public final class StormVineFeature extends Feature<NoneFeatureConfiguration> {
    public StormVineFeature(Codec<NoneFeatureConfiguration> codec){
        super(codec);
    }
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context){
        var level=context.level();
        var random=context.random();
        BlockPos origin=context.origin();
        boolean placed=false;
        for(int attempt=0;attempt<24;attempt++){
            // 以地表为基准，向下多扫、向上少扫，贴近真实岩壁
            BlockPos cell=origin.offset(random.nextInt(13)-6,random.nextInt(8)-6,random.nextInt(13)-6);
            if(!level.getBlockState(cell).isAir())continue;
            for(int side=0;side<4;side++){
                Direction dir=Direction.from2DDataValue(side);
                if(!StormVineBlock.isRock(level.getBlockState(cell.relative(dir))))continue;
                BlockState vine=HerbContent.STORM_VINE.get().defaultBlockState();
                if(!vine.canSurvive(level,cell))continue;
                level.setBlock(cell,vine,3);
                placed=true;
                break;
            }
        }
        return placed;
    }
}
