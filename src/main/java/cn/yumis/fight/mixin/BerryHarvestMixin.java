package cn.yumis.fight.mixin;
import cn.yumis.fight.server.ServerRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.UUID;
@Mixin(SweetBerryBushBlock.class)
public abstract class BerryHarvestMixin {
    @Inject(method="useWithoutItem",at=@At("RETURN"))
    private void ydnf$harvest(BlockState before,Level level,BlockPos pos,Player player,BlockHitResult hit,CallbackInfoReturnable<InteractionResult> ci){
        if(ServerRuntime.ready()&&player instanceof ServerPlayer serverPlayer&&before.getValue(SweetBerryBushBlock.AGE)==3&&level.getBlockState(pos).is(before.getBlock())&&level.getBlockState(pos).getValue(SweetBerryBushBlock.AGE)==1)
        ServerRuntime.get().awardProfession(serverPlayer,"farmer",10,"berry/"+UUID.randomUUID());
    }
}
