package cn.yumis.fight.mixin;

import cn.yumis.fight.modules.industry.api.MagicFlora;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpreadingSnowyBlock.class)
public abstract class MagicGrassRandomTickMixin {
    @Inject(method="randomTick",at=@At("TAIL"))
    private void ydnf$magicFlora(BlockState state,ServerLevel level,BlockPos pos,RandomSource random,CallbackInfo callback){if(state.is(Blocks.GRASS_BLOCK))MagicFlora.grassTick(level,pos,random);}
}
