package cn.yumis.fight.modules.professions.platform;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
/** 在绯红/诡异森林里找菌木，把噩梦藤贴在菌木侧面。 */
public final class NightmareVineFeature extends Feature<NoneFeatureConfiguration> {
    public NightmareVineFeature(Codec<NoneFeatureConfiguration> codec){
        super(codec);
    }
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context){
        var level=context.level();
        var random=context.random();
        BlockPos origin=context.origin();
        boolean placed=false;
        for(int attempt=0;attempt<10;attempt++){
            BlockPos stem=origin.offset(random.nextInt(9)-4,random.nextInt(9)-4,random.nextInt(9)-4);
            if(!NightmareVineBlock.isStem(level.getBlockState(stem)))continue;
            for(int side=0;side<4;side++){
                Direction dir=Direction.from2DDataValue(side);
                BlockPos target=stem.relative(dir);
                if(!level.getBlockState(target).isAir())continue;
                BlockState vine=HerbContent.NIGHTMARE_VINE.get().defaultBlockState();
                if(!vine.canSurvive(level,target))continue;
                level.setBlock(target,vine,3);
                placed=true;
                break;
            }
        }
        return placed;
    }
}
