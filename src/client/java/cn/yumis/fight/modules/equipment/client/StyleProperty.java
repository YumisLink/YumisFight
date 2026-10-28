package cn.yumis.fight.modules.equipment.client;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.platform.ItemData;

import com.mojang.serialization.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
public record StyleProperty() implements SelectItemModelProperty<String> {
    public static final Type<StyleProperty,String> TYPE=Type.create(MapCodec.unit(new StyleProperty()),Codec.STRING);
    @Override public String get(ItemStack stack,ClientLevel level,LivingEntity owner,int seed,ItemDisplayContext context){
        String forced=ItemData.string(stack,ItemData.NS+"_appearance");
        if(!forced.isEmpty())return forced;
        if(cn.yumis.fight.platform.ItemData.id(stack).equals("affix_gem")){
            try {var gem=cn.yumis.fight.modules.equipment.api.EquipmentJson.JSON.fromJson(EquipmentItems.payload(stack),cn.yumis.fight.modules.equipment.api.Gem.class);
                if(gem!=null)for(var affix:gem.affixes){if(affix.id.equals("unique/gem_crown"))return "crown_gem";if(affix.id.equals("unique/gem_clown"))return "clown_gem";}
            }catch(RuntimeException ignored){}
        }
        var gear=EquipmentItems.gear(stack);
        return gear==null?ItemData.id(stack)+"_10_base":cn.yumis.fight.modules.equipment.api.EquipmentTemplates.appearance(gear);
    }
    @Override public Type<StyleProperty,String> type(){
        return TYPE;
    }
    @Override public Codec<String> valueCodec(){
        return Codec.STRING;
    }
}
