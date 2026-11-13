package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.modules.industry.api.IndustrialRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;

public final class MagicFlowerGrowth {
    private MagicFlowerGrowth(){}
    public static void tick(ServerLevel level,BlockPos grass,RandomSource random){
        var rules=IndustrialRules.current();
        if(random.nextInt(rules.magicFlowerSpreadChance)!=0||!level.getBlockState(grass).is(Blocks.GRASS_BLOCK)||!level.getBlockState(grass.above()).isAir())return;
        var flower=IndustrialContent.ALL_BLOCKS.get("magic_flower").get();var magic=IndustrialContent.ALL_BLOCKS.get("magic_grass").get();
        BlockPos plant=grass.above();var cursor=new BlockPos.MutableBlockPos();boolean seed=false;
        for(int dx=-1;dx<=1&&!seed;dx++)for(int dz=-1;dz<=1&&!seed;dz++)if(dx!=0||dz!=0)for(int dy=-1;dy<=1;dy++){
            cursor.setWithOffset(plant,dx,dy,dz);if(!level.hasChunkAt(cursor))continue;var state=level.getBlockState(cursor);if(state.is(flower)||state.is(magic)){seed=true;break;}
        }
        if(!seed)return;
        int radius=rules.magicFlowerRadius;
        for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)if(dx*dx+dz*dz<=radius*radius)for(int dy=-rules.magicFlowerHeight;dy<=rules.magicFlowerHeight;dy++){
            cursor.setWithOffset(plant,dx,dy,dz);if(level.hasChunkAt(cursor)&&level.getBlockState(cursor).is(flower)){var state=magic.defaultBlockState();if(state.canSurvive(level,plant))level.setBlockAndUpdate(plant,state);return;}
        }
    }
}
