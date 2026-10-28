package cn.yumis.fight.modules.equipment.api;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.attributes.api.ParameterModifier;
import cn.yumis.fight.modules.affixes.api.AffixEffects;
import cn.yumis.fight.modules.affixes.api.Affix;
import cn.yumis.fight.modules.attributes.api.AttributeSources;

import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.equipment.api.*;
import cn.yumis.fight.modules.affixes.api.*;

import java.util.*;
import cn.yumis.fight.modules.attributes.api.*;
/** Attribute aggregation order is owned here; affix effects arrive through accumulation ports. */
public final class EquipmentAttributes {
    private EquipmentAttributes(){}
    public static Stats stats(Progress progress,List<Gear> equipped){return stats(progress,equipped,new cn.yumis.fight.modules.attributes.api.AttributeSources(),0,ignored->{});}
    public static Stats stats(Progress p,List<Gear> equipped,cn.yumis.fight.modules.attributes.api.AttributeSources sources,int allowance,java.util.function.Consumer<Stats> nativeItems){
        Stats s=new Stats();
        Map<String,Integer> sets=new HashMap<>();
        Map<Stat,Double> ratios=new EnumMap<>(Stat.class);
        for(Gear input:equipped){
            Gear g=cn.yumis.fight.modules.equipment.api.EquipmentResolution.resolve(input);
            if(!g.valid(p,allowance))continue;
            EquipmentRules.contribution(g).forEach(s::add);
            List<Affix> all=EquipmentEffects.effectiveAffixes(g);
            var sink=new AffixEffects.CharacterSink(){
                public void extra(Affix a){s.extras.add(new cn.yumis.fight.modules.attributes.api.ExtraDamage(a.element,a.value,a.maximum));}
                public void family(String family,int rank){s.ranks.merge(family,rank,Integer::sum);}
                public void percent(Stat stat,double value){ratios.merge(stat,value,Double::sum);}
                public void modifier(String id,double value,boolean product){if(product)s.modifiers.merge(id,value,(x,y)->x*y);else s.modifiers.merge(id,value,Double::sum);}
                public void parameter(String source,String skill,String parameter,double add,double multiply){s.parameters.add(new ParameterModifier(source,skill,parameter,add,multiply));}
            };
            for(Affix a:all)AffixEffects.character(a,g.instance.toString(),sink);
            if(g.slot==Slot.WEAPON){
                s.weaponValid=true;
                s.weaponInstance=g.instance.toString();
                s.weaponType=g.weapon.name();
                s.add(Stat.ATTACK_SPEED,WeaponTraits.speed(s.weaponType));
                if(g.weapon==Weapon.STAFF)ratios.merge(Stat.CAST_SPEED,-.2,Double::sum);
            }
            EquipmentDefinitions.SetDefinition set=EquipmentDefinitions.current().sets.get(g.set);
            if(set!=null&&(set.group().equals("armor")&&g.slot.armor()||set.group().equals("jewelry")&&g.slot.jewelry()))sets.merge(g.set,1,Integer::sum);
        }
        s.armorSet=sets.getOrDefault("first_sword",0);
        s.jewelrySet=sets.getOrDefault("four_elements",0);
        for(var entry:sets.entrySet())for(var effect:EquipmentDefinitions.current().sets.get(entry.getKey()).effects())if(entry.getValue()>=effect.pieces()){
            if(effect.stat()!=null)s.add(effect.stat(),effect.value());
            else if(effect.modifier()!=null&&!effect.modifier().isEmpty())s.modifiers.merge(effect.modifier(),effect.value(),Double::sum);
        }
        nativeItems.accept(s);
        var items=sources.apply(s.values,cn.yumis.fight.modules.attributes.api.AttributeSources.Stage.ITEM);
        s.values.clear();
        s.values.putAll(items);
        cn.yumis.fight.modules.character.api.CharacterAttributes.apply(p,s);
        for(var ratio:ratios.entrySet())s.mut(ratio.getKey(),ratio.getValue());
        for(var pair:Map.of(Stat.STRENGTH,"strengthRatio",Stat.INTELLIGENCE,"intelligenceRatio",Stat.PHYSICAL_ATTACK,"physicalAttackRatio",Stat.MAGICAL_ATTACK,"magicalAttackRatio").entrySet())s.mut(pair.getKey(),s.modifiers.getOrDefault(pair.getValue(),0d));
        sources.applyCharacter(s);
        for(Gear g:equipped)if(g.valid(p,allowance)&&MageBlood.is(g))MageBlood.apply(cn.yumis.fight.modules.equipment.api.EquipmentResolution.resolve(g),s);
        s.attribute(Stat.MP).fadd+=2*s.get(Stat.SPIRIT);
        s.attribute(Stat.MAGICAL_DEFENSE).fadd+=2*s.get(Stat.SPIRIT);
        EquipmentEffects.finish(s,EquipmentEffects.resolve(p,equipped,allowance));
        return s;
    }
}
