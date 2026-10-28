package cn.yumis.fight.modules.equipment.api;
import cn.yumis.fight.modules.inventory.api.MenuHooks;
import cn.yumis.fight.modules.inventory.api.ExtraInventory;

import cn.yumis.fight.platform.ItemData;

import cn.yumis.fight.modules.attributes.api.AttributeSources;
import cn.yumis.fight.modules.attributes.api.Stat;

import cn.yumis.fight.modules.attributes.api.AttributeServices;

import cn.yumis.fight.modules.character.api.Progress;

import cn.yumis.fight.modules.attributes.api.Stats;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import java.util.*;
/** The same aggregation is used by the local calculator and the server's display projection. */
public final class EquipmentStats {
    private static final net.minecraft.resources.Identifier SPEED_ID=net.minecraft.resources.Identifier.fromNamespaceAndPath(ItemData.NS,"equipment_move_speed");
    private EquipmentStats(){
    }
    public static void applyMovement(Player player,Stats stats){
        var attribute=player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);if(attribute==null)return;
        double amount=player.isInWater()||player.getAbilities().flying||player.isFallFlying()?0:Math.max(0,stats.get(Stat.MOVEMENT_SPEED))-1;
        var old=attribute.getModifier(SPEED_ID);
        if(amount==0){if(old!=null)attribute.removeModifier(SPEED_ID);}
        else if(old==null||old.amount()!=amount)attribute.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(SPEED_ID,amount,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    public static Stats build(Player player,Progress progress){
        return build(player,progress,AttributeServices.attributes(player.getUUID()));
    }
    public static Stats build(Player player,Progress progress,cn.yumis.fight.modules.attributes.api.AttributeSources sources){
        return build(player,progress,sources,player.getMainHandItem());
    }
    public static Stats build(Player player,Progress progress,cn.yumis.fight.modules.attributes.api.AttributeSources sources,net.minecraft.world.item.ItemStack weapon){
        List<Gear> gear=collect(player,weapon);
        ExtraInventory extra=MenuHooks.extra(player);
        return cn.yumis.fight.modules.equipment.api.EquipmentAttributes.stats(progress,gear,sources,EquipmentEligibility.allowance(player),stats->{
            NativeEquipmentRules.addEquipment(player,stats,progress,weapon);EquipmentLayout.current().attributes(extra,stats);
        }
        );
    }
    public static EquipmentEffects.Snapshot effects(Player player,Progress progress){return EquipmentEffects.resolve(progress,collect(player,player.getMainHandItem()),EquipmentEligibility.allowance(player));}
    public static List<Gear> collect(Player player,net.minecraft.world.item.ItemStack weapon){
        List<Gear> gear=new ArrayList<>();
        Gear hand=EquipmentItems.gear(weapon);
        if(hand!=null&&hand.slot==Slot.WEAPON)gear.add(hand);
        for(EquipmentSlot part:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)){
            Gear g=EquipmentItems.gear(player.getItemBySlot(part));
            Slot expected=part==EquipmentSlot.HEAD?Slot.HEAD:part==EquipmentSlot.CHEST?Slot.CHEST:part==EquipmentSlot.LEGS?Slot.LEGS:Slot.FEET;
            if(g!=null&&g.slot==expected)gear.add(g);
        }
        ExtraInventory extra=MenuHooks.extra(player);
        for(int i=0;i<extra.getContainerSize();i++){
            Gear g=EquipmentItems.gear(extra.getItem(i));
            if(g!=null&&extra.canPlaceItem(i,extra.getItem(i)))gear.add(g);
        }
        return gear;
    }

}
