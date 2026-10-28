package cn.yumis.fight.platform;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.registries.*;
import java.util.*;
/** Shared platform registration target. Content owners register their own items. */
public final class ItemRegistry {
    public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(ItemData.NS);
    public static final Map<String,DeferredItem<Item>> ALL=new LinkedHashMap<>();
    private ItemRegistry(){}
    public static ItemStack item(String id,int count){
        return new ItemStack(ALL.get(id).get(),count);
    }
}
