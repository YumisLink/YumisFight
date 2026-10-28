package cn.yumis.fight.platform;
import net.minecraft.core.component.DataComponents;

import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
/** Generic item metadata transport; contains no gameplay registrations. */
public final class ItemData {
    public static final String NS="yumis_dungeon_and_fight";
    private ItemData(){}
    public static String string(ItemStack stack,String key){
        return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getStringOr(key,"");
    }
    public static void string(ItemStack stack,String key,String value){
        CustomData.update(DataComponents.CUSTOM_DATA,stack,t->t.putString(key,value));
    }
    public static boolean own(ItemStack stack){
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(NS);
    }
    public static String id(ItemStack stack){
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }
}
