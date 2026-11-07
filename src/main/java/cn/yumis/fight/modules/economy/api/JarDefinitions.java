package cn.yumis.fight.modules.economy.api;

import cn.yumis.fight.modules.equipment.api.*;
import cn.yumis.fight.platform.ItemData;
import cn.yumis.fight.platform.ItemRegistry;
import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

/** Data-pack single-item pools. Display sampling is separate from the weighted award draw. */
public final class JarDefinitions {
    public static final long DURATION_MILLIS=10000;
    public record Entry(String type,String item,Weapon weapon,Rarity rarity,String template,double weight){}
    public record Definition(String name,double bossDropChance,List<Entry> entries){}
    public Map<String,Definition> jars=new LinkedHashMap<>();
    private static Supplier<JarDefinitions> source=JarDefinitions::defaults;
    private static final class Defaults {
        private static final String JSON=read();
        private static String read(){
            try(var input=JarDefinitions.class.getResourceAsStream("/data/"+ItemData.NS+"/rules/jars.json")){
                if(input==null)throw new IllegalStateException("缺少默认罐子配置");
                return new String(input.readAllBytes(),StandardCharsets.UTF_8);
            }catch(Exception failure){throw new ExceptionInInitializerError(failure);}
        }
    }
    public static JarDefinitions defaults(){return parse(Defaults.JSON);}
    public static void source(Supplier<JarDefinitions> value){source=Objects.requireNonNull(value);}
    public static JarDefinitions current(){return source.get();}
    public static JarDefinitions parse(String json){
        var result=new Gson().fromJson(json,JarDefinitions.class);
        if(result==null||result.jars==null)throw new IllegalArgumentException("缺少罐子定义");
        for(var row:result.jars.entrySet()){
            String id=row.getKey();var definition=row.getValue();
            if(!id.matches("[a-z0-9_./-]+")||definition==null||definition.name()==null||definition.name().isBlank()||definition.entries()==null||definition.entries().isEmpty())throw new IllegalArgumentException("罐子定义无效："+id);
            if(!Double.isFinite(definition.bossDropChance())||definition.bossDropChance()<0||definition.bossDropChance()>1)throw new IllegalArgumentException("罐子首领掉率无效："+id);
            double total=0;
            for(var entry:definition.entries()){
                validate(entry);total+=entry.weight();
            }
            if(!Double.isFinite(total)||total<=0)throw new IllegalArgumentException("罐子权重总和必须为有限正数："+id);
            row.setValue(new Definition(definition.name(),definition.bossDropChance(),List.copyOf(definition.entries())));
        }
        return result;
    }
    private static void validate(Entry entry){
        if(entry==null||entry.type()==null||!Double.isFinite(entry.weight())||entry.weight()<0)throw new IllegalArgumentException("罐子奖励权重无效");
        switch(entry.type()){
            case "weapon"->{
                if(entry.weapon()==null||!Weapon.standard().contains(entry.weapon())||entry.rarity()==null||!EquipmentTemplates.designed(entry.rarity()))throw new IllegalArgumentException("罐子普通武器类型或品质无效");
            }
            case "named"->{
                var template=NamedEquipment.find(entry.template());
                if(template==null||template.rarity()!=Rarity.EPIC)throw new IllegalArgumentException("罐子只允许已设计的具名史诗："+entry.template());
            }
            case "item"->{
                if(entry.item()==null||entry.item().isBlank())throw new IllegalArgumentException("罐子奖励物品为空");
                var id=Identifier.parse(entry.item().contains(":")?entry.item():ItemData.NS+":"+entry.item());
                if(!BuiltInRegistries.ITEM.containsKey(id)||BuiltInRegistries.ITEM.getValue(id)==Items.AIR)throw new IllegalArgumentException("未知罐子物品："+id);
                if(id.getNamespace().equals(ItemData.NS)&&(EquipmentItems.GEAR_IDS.contains(id.getPath())||id.getPath().equals(LootJarItem.ID)))throw new IllegalArgumentException("此物品需要正式的生成定义："+id);
            }
            default->throw new IllegalArgumentException("未知罐子奖励类型："+entry.type());
        }
    }
    public Definition require(String id){var definition=jars.get(id);if(definition==null)throw new IllegalArgumentException("此罐子定义不存在："+id);return definition;}
    public static Entry draw(Definition definition,Random random){
        double total=definition.entries().stream().mapToDouble(Entry::weight).sum(),value=random.nextDouble()*total;
        Entry last=null;
        for(var entry:definition.entries())if(entry.weight()>0){last=entry;value-=entry.weight();if(value<0)return entry;}
        return Objects.requireNonNull(last,"罐子奖池为空");
    }
    /** Both previews and awards use the existing equipment factories; callers own the random stream. */
    public static ItemStack create(Entry entry,Random random,String source){
        if(entry.type().equals("item")){
            if(!entry.item().contains(":"))return ItemRegistry.item(entry.item(),1);
            return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(entry.item())),1);
        }
        Gear gear=entry.type().equals("named")?NamedEquipment.create(entry.template(),random):EquipmentTemplates.createStandard(Slot.WEAPON,entry.weapon(),entry.rarity(),80,random);
        gear.source=source;
        return EquipmentItems.gear(gear);
    }
}
