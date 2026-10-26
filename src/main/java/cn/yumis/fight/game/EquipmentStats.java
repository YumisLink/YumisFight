package cn.yumis.fight.game;
import cn.yumis.fight.api.CommonServices;
import cn.yumis.fight.rules.*;
import cn.yumis.fight.rules.Models.*;
import static cn.yumis.fight.rules.Rules.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import java.util.*;
/** The same aggregation is used by the local calculator and the server's display projection. */
public final class EquipmentStats {
    private EquipmentStats(){
    }
    public static Stats build(Player player,Progress progress){
        return build(player,progress,CommonServices.attributes(player.getUUID()));
    }
    public static Stats build(Player player,Progress progress,cn.yumis.fight.api.AttributeSources sources){
        return build(player,progress,sources,player.getMainHandItem());
    }
    public static Stats build(Player player,Progress progress,cn.yumis.fight.api.AttributeSources sources,net.minecraft.world.item.ItemStack weapon){
        List<Gear> gear=new ArrayList<>();
        Gear hand=GameItems.gear(weapon);
        if(hand!=null&&hand.slot==Slot.WEAPON)gear.add(hand);
        for(EquipmentSlot part:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)){
            Gear g=GameItems.gear(player.getItemBySlot(part));
            Slot expected=part==EquipmentSlot.HEAD?Slot.HEAD:part==EquipmentSlot.CHEST?Slot.CHEST:part==EquipmentSlot.LEGS?Slot.LEGS:Slot.FEET;
            if(g!=null&&g.slot==expected)gear.add(g);
        }
        ExtraInventory extra=MenuHooks.extra(player);
        for(int i=0;i<extra.getContainerSize();i++){
            Gear g=GameItems.gear(extra.getItem(i));
            if(g!=null&&extra.canPlaceItem(i,extra.getItem(i)))gear.add(g);
        }
        return EquipmentRules.stats(progress,gear,sources,stats->{
            NativeRules.addEquipment(player,stats,progress,weapon);EquipmentLayout.CURRENT.attributes(extra,stats);
        }
        );
    }
}
