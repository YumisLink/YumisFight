package cn.yumis.fight.modules.industry.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/** Public random-tick hook for the industrial crystal resource cycle. */
public final class MagicFlora {
    private MagicFlora(){}
    public static void grassTick(ServerLevel level,BlockPos grass,RandomSource random){cn.yumis.fight.modules.industry.platform.MagicFlowerGrowth.tick(level,grass,random);}
}
