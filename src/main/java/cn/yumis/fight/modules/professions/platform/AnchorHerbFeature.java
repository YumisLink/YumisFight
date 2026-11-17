package cn.yumis.fight.modules.professions.platform;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import java.util.function.Supplier;
/** 在指定基底方块（骨块/黑曜石）上方生成草药丛的通用 feature。 */
public final class AnchorHerbFeature extends Feature<NoneFeatureConfiguration> {
    private final Block base;
    private final Supplier<? extends Block> plant;
    public AnchorHerbFeature(Block base,Supplier<? extends Block> plant,Codec<NoneFeatureConfiguration> codec){
        super(codec);
        this.base=base;
        this.plant=plant;
    }
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context){
        var level=context.level();
        var random=context.random();
        BlockPos origin=context.origin();
        boolean placed=false;
        for(int attempt=0;attempt<24;attempt++){
            BlockPos basePos=origin.offset(random.nextInt(17)-8,random.nextInt(17)-8,random.nextInt(17)-8);
            if(!level.getBlockState(basePos).is(base))continue;
            BlockPos target=basePos.above();
            var above=level.getBlockState(target);
            // 骨块常被灵魂沙掩埋：允许直接替换上方的灵魂沙/灵魂土
            boolean replaceable=above.isAir()||isSoulCover(above);
            if(!replaceable)continue;
            var state=plant.get().defaultBlockState();
            if(!state.canSurvive(level,target))continue;
            level.setBlock(target,state,3);
            placed=true;
        }
        return placed;
    }
    private static boolean isSoulCover(net.minecraft.world.level.block.state.BlockState state){
        String path=BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.contains("soul_soil")||path.contains("soul_sand");
    }
}
