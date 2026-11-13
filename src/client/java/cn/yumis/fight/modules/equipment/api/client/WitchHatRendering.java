package cn.yumis.fight.modules.equipment.api.client;

import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

/** Extends avatar visibility/name attachment to match the shared NPC headwear model. */
public final class WitchHatRendering {
    private WitchHatRendering(){}
    public static void extract(Player player,EntityRenderState raw){
        var head=EquipmentItems.view(player.getItemBySlot(EquipmentSlot.HEAD));
        if(head==null||!head.appearance().equals("elaina_witch_hat")||!(raw instanceof AvatarRenderState state))return;
        state.boundingBoxHeight=Math.max(state.boundingBoxHeight,3.05f*state.scale);
        if(state.nameTagAttachment!=null)state.nameTagAttachment=state.nameTagAttachment.add(0,state.scale,0);
    }
}
