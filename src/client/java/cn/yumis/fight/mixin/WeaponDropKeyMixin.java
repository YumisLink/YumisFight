package cn.yumis.fight.mixin;

import cn.yumis.fight.modules.equipment.api.Weapon;
import cn.yumis.fight.platform.ItemData;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class WeaponDropKeyMixin {
    @WrapOperation(method="handleKeybinds",at=@At(value="INVOKE",target="Lnet/minecraft/client/player/LocalPlayer;drop(Z)Z"))
    private boolean ydnf$dropKey(LocalPlayer player,boolean all,Operation<Boolean> original){
        var stack=player.getMainHandItem();
        if(!player.isCreative()&&!stack.isEmpty()&&ItemData.own(stack)){
            String id=ItemData.id(stack);
            for(Weapon weapon:Weapon.values())if(weapon.name().equalsIgnoreCase(id))return false;
        }
        return original.call(player,all);
    }
}
