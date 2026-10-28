package cn.yumis.fight.modules.equipment.application;
import cn.yumis.fight.modules.equipment.api.*;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.platform.ItemData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.*;
/** Initializes only genuinely unconfigured items; invalid old payloads never become free new gear. */
public final class EquipmentMaintenance {
    private EquipmentMaintenance(){}
    public static void initializeUnconfiguredItems(ServerPlayer p,Progress progress,Random random){
            for(int i=0;i<p.getInventory().getContainerSize();i++){
                ItemStack stack=p.getInventory().getItem(i);
                if(!stack.isEmpty()&&ItemData.own(stack)&&EquipmentItems.GEAR_IDS.contains(ItemData.id(stack))&&ItemData.string(stack,ItemData.NS).isEmpty()){
                    String id=ItemData.id(stack);
                    Slot slot=Arrays.stream(Slot.values()).filter(x->x.item().equals(id)).findFirst().orElse(Slot.WEAPON);
                    Weapon w=Arrays.stream(Weapon.values()).filter(x->x.name().equalsIgnoreCase(id)).findFirst().orElse(Weapon.KATANA);
                    if(slot==Slot.WEAPON&&!Weapon.standard().contains(w))continue;
                    if(progress.level>=slot.minimum())EquipmentItems.write(stack,EquipmentTemplates.createStandard(slot,w,Rarity.COMMON,progress.level,random));
                }
            }
    }
}
