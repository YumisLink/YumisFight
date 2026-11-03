package cn.yumis.fight.mixin;

import cn.yumis.fight.modules.equipment.client.WarglaiveVisuals;
import cn.yumis.fight.modules.skills.client.Effects;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemInHandRenderer.class)
public abstract class WarglaiveHandMixin {
    @WrapMethod(method="submitArmWithItem")
    private void ydnf$visualTwin(AbstractClientPlayer player,float partial,float pitch,InteractionHand hand,float attack,ItemStack item,float equip,PoseStack pose,SubmitNodeCollector nodes,int light,Operation<Void> original){
        if(WarglaiveVisuals.twin(player)){
            if(hand==InteractionHand.OFF_HAND&&item.isEmpty())item=player.getMainHandItem().copy();
            attack=Effects.VFX.handAttack(player,hand,attack);
        }
        original.call(player,partial,pitch,hand,attack,item,equip,pose,nodes,light);
    }
}
