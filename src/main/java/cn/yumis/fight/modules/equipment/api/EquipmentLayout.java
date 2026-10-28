package cn.yumis.fight.modules.equipment.api;
import cn.yumis.fight.modules.inventory.api.ExtraInventory;
import cn.yumis.fight.platform.ItemData;
import cn.yumis.fight.modules.attributes.api.Stat;

import static cn.yumis.fight.modules.attributes.api.AttributeNumbers.finite;
import cn.yumis.fight.modules.equipment.api.EquipmentJson;

import cn.yumis.fight.modules.attributes.api.Stats;
import com.google.gson.*;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.*;
import net.minecraft.tags.TagKey;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import java.util.*;
/** Namespace-agnostic slot groups and matching rules, loaded atomically. */
public final class EquipmentLayout {
    public static final int CAPACITY=64,PAGE_SIZE=6;
    public record Group(String id,String name,int count,int order,String icon){
    }
    public record Attribute(String attribute,double value,String operation){
    }
    public record Match(String group,List<String> items,List<String> tags,List<Attribute> attributes){
    }
    public List<Group> groups=new ArrayList<>();
    public List<Match> matches=new ArrayList<>();
    private static final EquipmentLayout DEFAULT=defaults();
    private static volatile java.util.function.Supplier<EquipmentLayout> source;
    public static EquipmentLayout current(){var read=source;return read==null?DEFAULT:read.get();}
    public static void source(java.util.function.Supplier<EquipmentLayout> value){source=java.util.Objects.requireNonNull(value);}
    private static EquipmentLayout defaults(){
        EquipmentLayout d=new EquipmentLayout();
        int index=100;
        for(Slot slot:List.of(Slot.BRACELET,Slot.NECKLACE,Slot.RING,Slot.SUB_EQUIPMENT,Slot.MAGIC_STONE,Slot.EARRING)){
            String id=ItemData.NS+":"+slot.name().toLowerCase(Locale.ROOT);
            d.groups.add(new Group(id,slot.title(),1,index++,""));
            d.matches.add(new Match(id,List.of(ItemData.NS+":"+slot.item()),List.of(),List.of()));
        }
        return d;
    }
    public static EquipmentLayout load(ResourceManager manager)throws Exception{
        EquipmentLayout next=new EquipmentLayout();
        Set<String> ids=new HashSet<>();
        for(var entry:manager.listResources("equipment/slot_groups",id->id.getPath().endsWith(".json")).entrySet())try(var reader=entry.getValue().openAsReader()){
            Identifier path=entry.getKey();
            String id=path.getNamespace()+":"+path.getPath().substring("equipment/slot_groups/".length(),path.getPath().length()-5);
            JsonObject j=JsonParser.parseReader(reader).getAsJsonObject();
            if(!ids.add(id))throw new IllegalArgumentException("重复槽组 "+id);
            int count=j.get("count").getAsInt();
            if(count<0||count>CAPACITY)throw new IllegalArgumentException("槽数无效 "+id);
            next.groups.add(new Group(id,j.get("name").getAsString(),count,j.has("order")?j.get("order").getAsInt():100,j.has("icon")?j.get("icon").getAsString():""));
        }
        for(var entry:manager.listResources("equipment/equipment",id->id.getPath().endsWith(".json")).entrySet())try(var reader=entry.getValue().openAsReader()){
            Match rule=EquipmentJson.JSON.fromJson(reader,Match.class);
            if(!ids.contains(rule.group))throw new IllegalArgumentException("匹配规则引用不存在的组 "+rule.group);
            if((rule.items==null||rule.items.isEmpty())&&(rule.tags==null||rule.tags.isEmpty()))throw new IllegalArgumentException("空装备匹配规则");
            if(rule.attributes!=null)for(Attribute a:rule.attributes){
                finite(a.value);
                if(a.operation!=null&&!Set.of("ADD","BASE_RATIO","TOTAL_RATIO").contains(a.operation))throw new IllegalArgumentException("无效属性操作");
            }
            next.matches.add(rule);
        }
        next.groups.sort(Comparator.comparingInt(Group::order).thenComparing(Group::id));
        if(next.groups.stream().mapToInt(Group::count).sum()>CAPACITY)throw new IllegalArgumentException("基础槽数超过64");
        return next;
    }
    public static EquipmentLayout parse(String json){
        EquipmentLayout next=EquipmentJson.JSON.fromJson(json,EquipmentLayout.class);
        if(next==null||next.groups==null||next.matches==null)throw new IllegalArgumentException("缺少装备槽定义");
        Set<String> ids=new HashSet<>();
        for(Group group:next.groups){if(group==null||group.id()==null||!ids.add(group.id())||group.count()<0||group.count()>CAPACITY)throw new IllegalArgumentException("无效装备槽定义");}
        if(next.groups.stream().mapToInt(Group::count).sum()>CAPACITY)throw new IllegalArgumentException("基础槽数超过64");
        for(Match rule:next.matches){
            if(rule==null||!ids.contains(rule.group())||(rule.items()==null||rule.items().isEmpty())&&(rule.tags()==null||rule.tags().isEmpty()))throw new IllegalArgumentException("无效装备匹配规则");
            if(rule.attributes()!=null)for(Attribute attribute:rule.attributes()){finite(attribute.value());if(attribute.operation()!=null&&!Set.of("ADD","BASE_RATIO","TOTAL_RATIO").contains(attribute.operation()))throw new IllegalArgumentException("无效属性操作");}
        }
        return next;
    }
    public Group group(String id){
        return groups.stream().filter(g->g.id.equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("未知槽组 "+id));
    }
    public List<String> slots(Map<String,Integer> bonus){
        List<String> out=new ArrayList<>();
        for(Group group:groups){
            int count=group.count+bonus.getOrDefault(group.id,0);
            if(count<0)throw new IllegalArgumentException("负槽数");
            for(int i=0;i<count;i++)out.add(group.id);
        }
        if(out.size()>CAPACITY)throw new IllegalArgumentException("总槽数超过64");
        return List.copyOf(out);
    }
    private boolean matches(Match rule,ItemStack stack){
        String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if(rule.items!=null&&rule.items.contains(id))return true;
        if(rule.tags!=null)for(String tag:rule.tags)if(stack.is(TagKey.create(Registries.ITEM,Identifier.parse(tag))))return true;
        return false;
    }
    public boolean accepts(String group,ItemStack stack){
        if(stack.isEmpty())return false;
        return matches.stream().anyMatch(r->r.group.equals(group)&&matches(r,stack));
    }
    public void attributes(ExtraInventory inventory,Stats stats){
        for(int i=0;i<inventory.getContainerSize();i++){
            ItemStack stack=inventory.getItem(i);
            if(stack.isEmpty()||!inventory.canPlaceItem(i,stack))continue;
            for(Match rule:matches)if(rule.group.equals(inventory.groups.get(i))&&matches(rule,stack)&&rule.attributes!=null)for(Attribute a:rule.attributes){
                try{
                    String key=a.attribute.substring(a.attribute.indexOf(':')+1).toUpperCase(Locale.ROOT);
                    Stat stat=Stat.valueOf(key);
                    String op=a.operation==null?"ADD":a.operation;
                    if(op.equals("ADD"))stats.add(stat,a.value);
                    else if(op.equals("BASE_RATIO"))stats.mut(stat,a.value);
                    else stats.attribute(stat).more(a.value);
                }
                catch(IllegalArgumentException ignored){
                    /* Unmapped third-party attributes stay available to public consumers. */
                }
            }
        }
    }
}
