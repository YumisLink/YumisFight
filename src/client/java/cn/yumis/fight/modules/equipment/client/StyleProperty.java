package cn.yumis.fight.modules.equipment.client;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.platform.ItemData;

import com.mojang.serialization.*;
import java.util.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
public record StyleProperty() implements SelectItemModelProperty<String> {
    public static final Type<StyleProperty,String> TYPE=Type.create(MapCodec.unit(new StyleProperty()),Codec.STRING);
    private static final Map<String,String> GEMS=new LinkedHashMap<>(16,.75f,true);
    @Override public String get(ItemStack stack,ClientLevel level,LivingEntity owner,int seed,ItemDisplayContext context){
        String forced=EquipmentItems.metadata(stack,ItemData.NS+"_appearance");
        if(!forced.isEmpty())return forced;
        if(cn.yumis.fight.platform.ItemData.id(stack).equals("affix_gem")){
            String payload=EquipmentItems.payload(stack);String appearance=GEMS.get(payload);
            if(appearance==null){appearance="";try{var gem=cn.yumis.fight.modules.equipment.api.EquipmentJson.JSON.fromJson(payload,cn.yumis.fight.modules.equipment.api.Gem.class);if(gem!=null)for(var affix:gem.affixes){if(affix.id.equals("unique/gem_crown")){appearance="crown_gem";break;}if(affix.id.equals("unique/gem_clown")){appearance="clown_gem";break;}}}catch(RuntimeException ignored){}if(payload.length()<=65536){GEMS.put(payload,appearance);if(GEMS.size()>128)GEMS.remove(GEMS.keySet().iterator().next());}}
            if(!appearance.isEmpty())return appearance;
        }
        var gear=EquipmentItems.view(stack);
        return gear==null?ItemData.id(stack)+"_10_base":gear.appearance();
    }
    @Override public Type<StyleProperty,String> type(){
        return TYPE;
    }
    @Override public Codec<String> valueCodec(){
        return Codec.STRING;
    }
}
