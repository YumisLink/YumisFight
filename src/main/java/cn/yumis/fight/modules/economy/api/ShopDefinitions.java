package cn.yumis.fight.modules.economy.api;

import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.platform.ItemData;
import com.google.gson.Gson;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

/** Unit offers and conjunctive costs, shared by previews and owner-authored purchases. */
public final class ShopDefinitions {
    public static final int MAX_QUANTITY=640;
    public static final Set<String> RARE_MATERIALS=Set.of("phantom_membrane","prismarine_crystals","breeze_rod","ghast_tear","wither_skeleton_skull","shulker_shell");
    public record Cost(String item,long count) {
        public boolean gold(){return CurrencyRules.GOLD.id().equals(item);}
        public ItemStack stack(){return ShopDefinitions.stack(item);}
    }
    public record Offer(String id,String item,List<Cost> costs) {
        public ItemStack stack(){return ShopDefinitions.stack(item);}
    }
    public record Definition(String name,List<Offer> offers) {}
    public Map<String,Definition> shops=new LinkedHashMap<>();
    private static Supplier<ShopDefinitions> source=ShopDefinitions::defaults;
    private static ShopDefinitions listedSource;
    private static Set<String> listedItems=Set.of();
    public static boolean listed(ItemStack stack){
        var definitions=current();if(definitions!=listedSource){var ids=new HashSet<String>();for(var shop:definitions.shops.values())for(var offer:shop.offers())ids.add(offer.item());listedItems=Set.copyOf(ids);listedSource=definitions;}
        return listedItems.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }
    private static final class Defaults {static final String JSON=read();}
    private static String read(){
        try(var input=ShopDefinitions.class.getResourceAsStream("/data/"+ItemData.NS+"/rules/shops.json")){
            if(input==null)throw new IllegalStateException("缺少默认商店配置");
            return new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }catch(Exception failure){throw new ExceptionInInitializerError(failure);}
    }
    public static ShopDefinitions defaults(){return parse(Defaults.JSON);}
    public static ShopDefinitions current(){return source.get();}
    public static void source(Supplier<ShopDefinitions> value){source=Objects.requireNonNull(value);}
    public static ShopDefinitions parse(String json){
        var result=new Gson().fromJson(json,ShopDefinitions.class);
        if(result==null||result.shops==null)throw new IllegalArgumentException("缺少商店定义");
        for(var row:result.shops.entrySet()){
            var d=row.getValue();
            if(!row.getKey().matches("[a-z0-9_./-]+")||d==null||d.name()==null||d.name().isBlank()||d.offers()==null||d.offers().size()>54)throw new IllegalArgumentException("商店定义无效："+row.getKey());
            Set<String> ids=new HashSet<>();
            for(var offer:d.offers()){
                if(offer==null||offer.id()==null||!ids.add(offer.id())||offer.item()==null||offer.costs()==null||offer.costs().isEmpty())throw new IllegalArgumentException("商品定义无效："+row.getKey());
                Identifier.parse(offer.item());Set<String> costs=new HashSet<>();
                for(var cost:offer.costs()){
                    if(cost==null||cost.item()==null||cost.count()<=0||!costs.add(cost.item()))throw new IllegalArgumentException("商品费用无效："+offer.id());
                    Math.multiplyExact(cost.count(),MAX_QUANTITY);if(!cost.gold())Identifier.parse(cost.item());
                }
            }
        }
        return result;
    }
    public Definition require(String id){var value=shops.get(id);if(value==null)throw new IllegalArgumentException("商店不存在："+id);return value;}
    public void validateItems(){for(var definition:shops.values())for(var offer:definition.offers()){offer.stack();for(var cost:offer.costs())if(!cost.gold())cost.stack();}}
    public static ItemStack stack(String id){
        var key=Identifier.parse(id);var item=BuiltInRegistries.ITEM.getValue(key);
        if(item==null||item==Items.AIR||!BuiltInRegistries.ITEM.containsKey(key))throw new IllegalArgumentException("商品物品不存在："+id);
        return new ItemStack(item);
    }
    public static Rarity materialRarity(ItemStack stack){
        var id=BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals("minecraft")&&RARE_MATERIALS.contains(id.getPath())?Rarity.RARE:null;
    }
}
