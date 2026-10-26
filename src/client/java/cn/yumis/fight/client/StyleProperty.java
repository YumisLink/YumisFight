package cn.yumis.fight.client;
import cn.yumis.fight.game.GameItems;
import com.mojang.serialization.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
public record StyleProperty() implements SelectItemModelProperty<String> {
    public static final Type<StyleProperty,String> TYPE=Type.create(MapCodec.unit(new StyleProperty()),Codec.STRING);
    @Override public String get(ItemStack stack,ClientLevel level,LivingEntity owner,int seed,ItemDisplayContext context){
        String forced=GameItems.string(stack,GameItems.NS+"_appearance");
        if(!forced.isEmpty())return forced;
        var gear=GameItems.gear(stack);
        return gear==null?GameItems.id(stack)+"_10_base":GameItems.appearance(gear);
    }
    @Override public Type<StyleProperty,String> type(){
        return TYPE;
    }
    @Override public Codec<String> valueCodec(){
        return Codec.STRING;
    }
}
