package cn.yumis.fight.modules.economy.api;

import cn.yumis.fight.modules.dungeons.api.Waystone;
import cn.yumis.fight.modules.dungeons.api.WaystoneRules;
import cn.yumis.fight.modules.equipment.api.*;
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
    public record Offer(String id,String item,String type,Integer tier,String rarity,String slot,String weapon,String armor,String jewelry,Integer level,List<Cost> costs) {
        public String kind(){return type==null||type.isBlank()?"item":type;}
        /** Deterministic display sample; the purchase output of unit-random offers is rolled separately. */
        public ItemStack stack(){return ShopDefinitions.display(this);}
        /** One purchased unit; the owner client rolls and the receiver only stores the authored result. */
        public ItemStack create(String shop,Random random){return ShopDefinitions.create(this,shop,random);}
    }
    public record Definition(String name,List<Offer> offers) {}
    public Map<String,Definition> shops=new LinkedHashMap<>();
    private static Supplier<ShopDefinitions> source=ShopDefinitions::defaults;
    private static ShopDefinitions listedSource;
    private static Set<String> listedItems=Set.of();
    public static boolean listed(ItemStack stack){
        var definitions=current();if(definitions!=listedSource){var ids=new HashSet<String>();for(var shop:definitions.shops.values())for(var offer:shop.offers())if(offer.item()!=null)ids.add(offer.item());listedItems=Set.copyOf(ids);listedSource=definitions;}
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
                if(offer==null||offer.id()==null||!ids.add(offer.id())||offer.costs()==null||offer.costs().isEmpty())throw new IllegalArgumentException("商品定义无效："+row.getKey());
                validateOffer(offer);
                Set<String> costs=new HashSet<>();
                for(var cost:offer.costs()){
                    if(cost==null||cost.item()==null||cost.count()<=0||!costs.add(cost.item()))throw new IllegalArgumentException("商品费用无效："+offer.id());
                    Math.multiplyExact(cost.count(),MAX_QUANTITY);if(!cost.gold())Identifier.parse(cost.item());
                }
            }
        }
        return result;
    }
    private static void validateOffer(Offer offer){
        switch(offer.kind()){
            case "item"->{
                if(offer.item()==null)throw new IllegalArgumentException("商品物品缺失："+offer.id());
                Identifier.parse(offer.item());
            }
            case "waystone"->{
                if(offer.item()!=null)throw new IllegalArgumentException("引路石商品不能指定注册物品："+offer.id());
                var rarity=rarity(offer);
                if(offer.tier()==null||offer.tier()<1||rarity.ordinal()>Rarity.LEGENDARY.ordinal())throw new IllegalArgumentException("引路石商品阶数或品质无效："+offer.id());
                WaystoneRules.current().monsterLevel(offer.tier());
            }
            case "gear"->{
                if(offer.item()!=null)throw new IllegalArgumentException("装备商品不能指定注册物品："+offer.id());
                var slot=slot(offer);var rarity=rarity(offer);
                if(!EquipmentTemplates.designed(rarity))throw new IllegalArgumentException("装备商品品质未开放投放："+offer.id());
                if(offer.level()==null||offer.level()<slot.minimum()||offer.level()>80)throw new IllegalArgumentException("装备商品等级未开放："+offer.id());
                var weapon=weapon(offer);var armor=armor(offer);var jewelry=jewelry(offer);
                if(slot==Slot.WEAPON){if(weapon==null||!Weapon.standard().contains(weapon))throw new IllegalArgumentException("武器商品类型无效："+offer.id());}
                else if(weapon!=null)throw new IllegalArgumentException("非武器商品不能指定武器："+offer.id());
                if(slot.armor()){if(armor==null||armor==ArmorType.UNTYPED)throw new IllegalArgumentException("防具商品需要甲种："+offer.id());}
                else if(armor!=null)throw new IllegalArgumentException("非防具商品不能指定甲种："+offer.id());
                if(slot.jewelry()){if(jewelry==null||jewelry==JewelryType.UNTYPED)throw new IllegalArgumentException("首饰商品需要类型："+offer.id());}
                else if(jewelry!=null)throw new IllegalArgumentException("非首饰商品不能指定类型："+offer.id());
            }
            default->throw new IllegalArgumentException("未知商品类型："+offer.kind());
        }
    }
    private static <E extends Enum<E>> E parse(Class<E> kind,String value,String label){
        if(value==null||value.isBlank())throw new IllegalArgumentException("商品"+label+"缺失");
        try{return Enum.valueOf(kind,value);}catch(IllegalArgumentException failure){throw new IllegalArgumentException("商品"+label+"无效："+value);}
    }
    static Rarity rarity(Offer offer){return parse(Rarity.class,offer.rarity(),"品质");}
    static Slot slot(Offer offer){return parse(Slot.class,offer.slot(),"部位");}
    static Weapon weapon(Offer offer){return offer.weapon()==null?null:parse(Weapon.class,offer.weapon(),"武器");}
    static ArmorType armor(Offer offer){return offer.armor()==null?null:parse(ArmorType.class,offer.armor(),"甲种");}
    static JewelryType jewelry(Offer offer){return offer.jewelry()==null?null:parse(JewelryType.class,offer.jewelry(),"首饰类型");}
    /** Waystone pools roll against every open dungeon; 80 is the catalog's maximum dungeon level. */
    private static final int ANY_OPEN_DUNGEON_LEVEL=80;
    private static ItemStack display(Offer offer){
        return switch(offer.kind()){
            case "item"->stack(offer.item());
            case "gear"->create(offer,"",new Random(0));
            case "waystone"->create(offer,"",new Random());
            default->throw new IllegalArgumentException("未知商品类型："+offer.kind());
        };
    }
    public static ItemStack create(Offer offer,String shop,Random random){
        return switch(offer.kind()){
            case "item"->stack(offer.item());
            case "gear"->{
                var gear=EquipmentRules.create(slot(offer),weapon(offer),rarity(offer),offer.level(),armor(offer),jewelry(offer),random);
                if(!shop.isEmpty())gear.source="shop/"+shop;
                yield EquipmentItems.gear(gear);
            }
            case "waystone"->Waystone.generate(offer.tier(),ANY_OPEN_DUNGEON_LEVEL,rarity(offer),random).item();
            default->throw new IllegalArgumentException("未知商品类型："+offer.kind());
        };
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
