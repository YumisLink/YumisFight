package cn.yumis.fight.client;
import cn.yumis.fight.game.GameItems;
import com.mojang.serialization.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
public record MaterialProperty() implements SelectItemModelProperty<String> {
    public static final Type<MaterialProperty,String> TYPE=Type.create(MapCodec.unit(new MaterialProperty()),Codec.STRING);
    @Override public String get(ItemStack stack,ClientLevel level,LivingEntity owner,int seed,ItemDisplayContext context){
        var definition=cn.yumis.fight.rules.Catalog.CURRENT.materials.get(GameItems.material(stack));
        return definition==null?GameItems.material(stack):definition.texture();
    }
    @Override public Type<MaterialProperty,String> type(){
        return TYPE;
    }
    @Override public Codec<String> valueCodec(){
        return Codec.STRING;
    }
}
