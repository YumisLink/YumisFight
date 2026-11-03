package cn.yumis.fight.modules.equipment.client;

import cn.yumis.fight.modules.equipment.api.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;

/** A render-only offhand copy. No inventory item, UUID, affix source or extra equipment stats are created. */
public final class WarglaiveVisuals {
    private WarglaiveVisuals(){}
    public static boolean twin(Player p){var gear=EquipmentItems.view(p.getMainHandItem());return p.getOffhandItem().isEmpty()&&gear!=null&&gear.weapon()==Weapon.WARGLAIVE;}
    public static void extract(Player p,EntityRenderState raw){if(!twin(p)||!(raw instanceof AvatarRenderState s))return;boolean left=p.getMainArm()!=HumanoidArm.LEFT;ItemStack copy=p.getMainHandItem().copy();Minecraft.getInstance().getItemModelResolver().updateForLiving(left?s.leftHandItemState:s.rightHandItemState,copy,left?ItemDisplayContext.THIRD_PERSON_LEFT_HAND:ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,p);if(left){s.leftHandItemStack=copy;s.leftArmPose=net.minecraft.client.model.HumanoidModel.ArmPose.ITEM;}else{s.rightHandItemStack=copy;s.rightArmPose=net.minecraft.client.model.HumanoidModel.ArmPose.ITEM;}}
}
