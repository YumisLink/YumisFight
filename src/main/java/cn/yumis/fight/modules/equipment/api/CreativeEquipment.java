package cn.yumis.fight.modules.equipment.api;
import java.util.*;
import net.minecraft.world.item.ItemStack;
/** Only catalog templates materialize. Moving, reading or copying existing rolls never rerolls them. */
public final class CreativeEquipment {
    private CreativeEquipment(){}
    public static ItemStack materialize(ItemStack original,Random random){Gear template=EquipmentItems.gear(original);if(template==null||!template.creativeTemplate)return original;Gear gear;
        if(NamedEquipment.find(template.template)!=null)gear=NamedEquipment.create(template.template,random);
        else if(MageBlood.is(template))gear=MageBlood.create(random);
        else if(!EquipmentTemplates.designed(template.rarity))return original;
        else gear=EquipmentRules.create(template.slot,template.weapon,template.rarity,template.level,template.armorType,template.jewelryType,random);
        gear.creativeTemplate=false;return EquipmentItems.gear(gear);
    }
}
