package cn.yumis.fight.modules.economy.api;

import com.google.gson.*;
import java.util.*;
import java.util.function.Supplier;
import cn.yumis.fight.modules.equipment.api.*;

/** Data-pack chest registry. Chest tiers are not equipment rarities. */
public final class ChestDefinitions {
    public static final List<String> TIERS=List.of("青铜","白银","黄金","钻石","神器","传说","史诗","神话","太初","最终");
    public record Entry(String item,int count,double weight){}
    public record Definition(String name,boolean enabled,int equipmentBase,int equipmentPerTier,int currencyBase,int currencyPerTier,List<Entry> fixed,List<Entry> special,int specialRolls){}
    public record SpecialPool(double chance,List<Entry> entries){}
    public record WaystoneChest(String name,int currencyMinimum,int currencyMaximum,boolean inheritDropRate,List<Entry> fixed,List<Entry> special,int specialRolls){}
    public WaystoneChest waystoneDefault=new WaystoneChest("引路石%s阶宝箱",2,4,true,List.of(),List.of(),0);
    public Map<Integer,WaystoneChest> waystoneByTier=new LinkedHashMap<>();
    public Map<String,Definition> chests=new LinkedHashMap<>();
    public Map<String,SpecialPool> bossPools=new LinkedHashMap<>();
    public List<String> todo=new ArrayList<>();
    private static Supplier<ChestDefinitions> source=ChestDefinitions::defaults;
    public static void source(Supplier<ChestDefinitions> value){source=Objects.requireNonNull(value);}
    public static ChestDefinitions current(){return source.get();}
    public static ChestDefinitions defaults(){
        var d=new ChestDefinitions();
        d.chests.put("treasure",new Definition("藏宝图之%s宝箱",true,3,2,3,1,List.of(),List.of(),0));
        d.chests.put("goblin_kingdom",new Definition("哥布林王国的宝箱",true,0,0,0,0,List.of(new Entry("colorless_crystal",10,1)),List.of(),0));
        var pool=new SpecialPool(.1,List.of(new Entry("abyss_invitation",1,10),new Entry("goblin_treasure_map",1,1)));
        for(String id:List.of("goblin_camp","dragon_tower","castle_palace","treasure_forest","treasure_tower","treasure_palace"))d.bossPools.put(id,pool);
        d.todo=List.of("深渊派对挑战函用途", "哥布林王国宝箱正式掉落", "挖宝专属史诗和神话装备及掉率", "其他特殊道具");
        return d;
    }
    public static ChestDefinitions parse(String json){
        ChestDefinitions d=new Gson().fromJson(json,ChestDefinitions.class);
        if(d==null||d.chests==null||d.bossPools==null)throw new IllegalArgumentException("宝箱配置不完整");
        for(var e:d.chests.entrySet()){
            if(!e.getKey().matches("[a-z0-9_./-]+"))throw new IllegalArgumentException("宝箱ID无效");
            var c=e.getValue();if(c==null||c.name()==null||c.name().isBlank()||c.fixed()==null||c.special()==null||c.specialRolls()<0||c.specialRolls()>128)throw new IllegalArgumentException("宝箱定义无效："+e.getKey());
            if(c.equipmentBase()<0||c.equipmentPerTier()<0||c.currencyBase()<0||c.currencyPerTier()<0||(long)c.equipmentBase()+10L*c.equipmentPerTier()+(long)c.currencyBase()+10L*c.currencyPerTier()>1024)throw new IllegalArgumentException("宝箱奖励数量无效");
            validate(c.fixed());validate(c.special());
            if(c.specialRolls()>0&&c.special().stream().noneMatch(x->x.weight()>0))throw new IllegalArgumentException("特殊奖励池为空");
        }
        for(var p:d.bossPools.values()){if(p==null||!Double.isFinite(p.chance())||p.chance()<0||p.chance()>1||p.entries()==null)throw new IllegalArgumentException("首领特殊池无效");validate(p.entries());if(p.chance()>0&&p.entries().stream().noneMatch(x->x.weight()>0))throw new IllegalArgumentException("首领特殊池为空");}
        for(String required:List.of("treasure","goblin_kingdom"))if(!d.chests.containsKey(required))throw new IllegalArgumentException("缺少宝箱："+required);
        validateWaystone(d.waystoneDefault);if(d.waystoneByTier==null)throw new IllegalArgumentException("缺少引路石分阶宝箱配置");for(var entry:d.waystoneByTier.entrySet()){if(entry.getKey()<1)throw new IllegalArgumentException("引路石箱阶无效");validateWaystone(entry.getValue());}
        return d;
    }
    private static void validate(List<Entry> entries){
        for(var e:entries){
            if(e==null||e.item()==null||e.count()<1||e.count()>4096||!Double.isFinite(e.weight())||e.weight()<0)throw new IllegalArgumentException("无效宝箱奖励");
            if(e.item().contains(":")){var id=net.minecraft.resources.Identifier.parse(e.item());if(!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)||id.equals(net.minecraft.resources.Identifier.withDefaultNamespace("air")))throw new IllegalArgumentException("未知奖励物品："+id);}
            else if(!e.item().equals("goblin_treasure_map")&&!e.item().equals("abyss_invitation")&&!e.item().equals("colorless_crystal")&&CurrencyRules.find(e.item())==null)throw new IllegalArgumentException("未登记奖励："+e.item());
        }
        if(!Double.isFinite(entries.stream().mapToDouble(Entry::weight).sum()))throw new IllegalArgumentException("奖池权重总和过大");
    }
    public static Entry draw(List<Entry> pool,Random random){double total=pool.stream().mapToDouble(Entry::weight).sum();if(total<=0)return null;double v=random.nextDouble()*total;for(var e:pool){v-=e.weight();if(v<0)return e;}return pool.getLast();}
    public Definition require(String id){var c=chests.get(id);if(c==null||!c.enabled())throw new IllegalArgumentException("此宝箱尚未开放");return c;}
    private static void validateWaystone(WaystoneChest c){if(c==null||c.name()==null||c.name().isBlank()||c.currencyMinimum()<0||c.currencyMaximum()<c.currencyMinimum()||c.currencyMaximum()>1024||c.specialRolls()<0||c.specialRolls()>128||c.fixed()==null||c.special()==null)throw new IllegalArgumentException("引路石宝箱配置无效");validate(c.fixed());validate(c.special());if(c.specialRolls()>0&&c.special().stream().noneMatch(e->e.weight()>0))throw new IllegalArgumentException("引路石宝箱特殊池为空");}
    public WaystoneChest waystone(int tier){if(tier<1)throw new IllegalArgumentException("引路石箱阶无效");return waystoneByTier.getOrDefault(tier,waystoneDefault);}
    public static int upgradedTier(int tier,double probability,Random random,double[] failed){while(tier<10&&random.nextDouble()<probability){tier++;probability/=2;}failed[0]=probability;return tier;}
}
