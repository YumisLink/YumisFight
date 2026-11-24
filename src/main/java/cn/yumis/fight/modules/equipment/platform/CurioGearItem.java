package cn.yumis.fight.modules.equipment.platform;
import cn.yumis.fight.modules.equipment.api.EquipmentEligibility;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/** Curios owns the six accessory slots; progression still decides whether gear can be worn. */
public final class CurioGearItem extends Item implements ICurioItem {
    public CurioGearItem(Properties properties){super(properties);}

    @Override public boolean canEquip(SlotContext context,ItemStack stack){
        var gear=EquipmentItems.gear(stack);
        if(gear==null||!(context.entity() instanceof Player player))return false;
        String expected="ydnf_"+gear.slot.name().toLowerCase(java.util.Locale.ROOT);
        if(!context.identifier().equals(expected))return false;
        var progress=EquipmentEligibility.PROGRESS.apply(player);
        return progress!=null&&gear.valid(progress,EquipmentEligibility.allowance(player));
    }

    @Override public boolean canEquipFromUse(SlotContext context,ItemStack stack){
        var gear=EquipmentItems.gear(stack);
        if(gear==null||!(context.entity() instanceof Player player))return false;
        var progress=EquipmentEligibility.PROGRESS.apply(player);
        boolean valid=progress!=null&&gear.valid(progress,EquipmentEligibility.allowance(player));
        if(!valid&&!player.level().isClientSide()){
            player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("装备等级或穿戴条件不足（需要 Lv."+gear.level+"）"));
        }
        return valid;
    }
}
