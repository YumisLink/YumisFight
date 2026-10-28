package cn.yumis.fight.mixin;
import net.minecraft.world.food.FoodData;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(FoodData.class)
public abstract class FoodMixin {
    @ModifyVariable(method="tick",at=@At("STORE"),ordinal=0)
    private boolean ydnf$ownRecovery(boolean nativeRegeneration){
        return false;
    }
}
