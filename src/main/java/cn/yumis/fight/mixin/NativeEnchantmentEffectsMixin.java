package cn.yumis.fight.mixin;

import cn.yumis.fight.modules.combat.api.NativeEffects;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EnchantmentHelper.class)
public abstract class NativeEnchantmentEffectsMixin {
    @WrapMethod(method="doPostAttackEffectsWithItemSourceOnBreak")
    private static void ydnf$deferEnchantments(ServerLevel level,Entity victim,DamageSource source,ItemStack item,Consumer<Item> onBreak,Operation<Void> original){
        // Capture the collision's stack reference, preserving native durability and trident pickup state.
        if(!NativeEffects.defer(victim,()->original.call(level,victim,source,item,onBreak)))original.call(level,victim,source,item,onBreak);
    }
}
