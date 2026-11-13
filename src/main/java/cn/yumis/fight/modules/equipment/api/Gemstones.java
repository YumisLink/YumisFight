package cn.yumis.fight.modules.equipment.api;

import cn.yumis.fight.modules.affixes.api.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Fixed, level-one gems. Their saved affixes retain the values captured at creation. */
public final class Gemstones {
    public record Definition(String id,String name,String affix,Rarity rarity,String group,String parameter,double value,double maximum){}
    private static final List<Definition> ALL=List.of(EquipmentJson.JSON.fromJson(CraftingEssences.content().get("gems"),Definition[].class));
    private Gemstones(){}
    public static List<Definition> all(){return ALL;}
    public static Definition find(String id){return ALL.stream().filter(d->d.id().equals(id)).findFirst().orElse(null);}
    public static boolean currency(String id){return id.equals("affix_gem")||find(id)!=null;}
    public static Gem create(Definition definition){
        var d=AffixDefinitions.current().affixes.get(definition.affix());
        if(d==null||!d.active())throw new IllegalArgumentException("宝石效果未开放");
        Gem gem=new Gem();gem.name=definition.rarity().title()+"·"+definition.name();gem.level=1;gem.rarity=definition.rarity();gem.group=definition.group();
        Affix a=new Affix();a.id=d.id();a.operation=d.operation();a.stat=d.stat()==null?"":d.stat().name();a.element=d.element();a.parameter=definition.parameter();a.value=a.baseline=definition.value();a.maximum=a.baselineMaximum=definition.maximum();a.fixed=true;
        // Identical fixed currency gems share a creation snapshot so fresh drops can stack.
        AffixResolver.anchor(a,"gem/"+definition.id()+"/"+d.revision(),d,d.revision(),d.effectiveScale(1));gem.affixes.add(a);
        return gem;
    }
    public static ItemStack item(Definition definition){Gem gem=create(definition);return EquipmentItems.payload(definition.id(),gem,gem.name);}
    public static Gem read(ItemStack stack){
        String payload=EquipmentItems.payload(stack);var definition=find(cn.yumis.fight.platform.ItemData.id(stack));
        if(payload.isEmpty()&&definition!=null)return create(definition);
        Gem gem=EquipmentJson.JSON.fromJson(payload,Gem.class);
        if(gem==null||gem.affixes==null||gem.affixes.isEmpty())throw new IllegalArgumentException("宝石数据无效");
        return gem;
    }
}
