package cn.yumis.fight.game;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/** Curios owns the six accessory slots; progression still decides whether gear can be worn. */
public final class CurioGearItem extends Item implements ICurioItem {
    public CurioGearItem(Properties properties){super(properties);}

    @Override public boolean canEquip(SlotContext context,ItemStack stack){
        var gear=GameItems.gear(stack);
        if(gear==null||!(context.entity() instanceof Player player))return false;
        String expected="ydnf_"+gear.slot.name().toLowerCase(java.util.Locale.ROOT);
        return context.identifier().equals(expected)&&gear.valid(ExtraInventory.PROGRESS.apply(player));
    }
}
