package cn.yumis.fight.modules.equipment.api;

import cn.yumis.fight.modules.affixes.api.*;
import java.util.*;

/** Read-only union of the live random generation qualifications, never a second affix pool. */
public final class EquipmentAffixCatalog {
    public record Preview(String text,List<String> locations){}
    public record Entry(String id,String name,String category,String description,List<Slot> slots,List<Preview> previews){}
    private static Object affixSnapshot,equipmentSnapshot;
    private static long affixRevision=-1,equipmentRevision=-1;
    private static List<Entry> cached=List.of();
    private EquipmentAffixCatalog(){}
    private static String range(double low,double high){return "（"+AffixText.integer(low)+"～"+AffixText.integer(high)+"）";}
    private static String preview(Gear gear,AffixDefinition definition){
        if(definition.operation().equals("SKILL_FAMILY"))return definition.name()+" +（1～3）级";
        Affix affix=new Affix();affix.id=definition.id();affix.operation=definition.operation();affix.stat=definition.stat()==null?"":definition.stat().name();affix.element=definition.element();
        if(affix.operation.equals("EXTRA")){affix.baseline=.1*EquipmentRules.budget(80);affix.baselineMaximum=.2*EquipmentRules.budget(80);}
        else if(affix.stat.equals("ATTACK_SPEED")){affix.baseline=AttackSpeedRoll.sample(80,.8);affix.baselineMaximum=AttackSpeedRoll.sample(80,1.2);}
        else affix.baseline=EquipmentAffixes.baseline(gear,affix);
        affix.value=affix.baseline;affix.maximum=affix.baselineMaximum;var bounds=EquipmentAffixRanges.reroll(affix,Rarity.LEGENDARY,false);
        if(bounds==null)return AffixText.affix(affix);
        if(affix.operation.equals("EXTRA"))return "攻击时附加"+range(bounds.minimum(),bounds.upperMaximum())+"点"+new String[]{"光","火","冰","暗"}[affix.element]+"属性伤害；下限范围"+range(bounds.minimum(),bounds.maximum())+"，上限范围"+range(bounds.upperMinimum(),bounds.upperMaximum());
        String name=affix.operation.equals("ALL_RESIST_FLAT")?"所有元素抗性":definition.stat()==null?definition.name():AffixText.statName(definition.stat());
        boolean percentage=EquipmentAffixRanges.percentage(affix);return (affix.operation.equals("PERCENT")?"本装备":"")+name+" +"+range(bounds.minimum(),bounds.maximum())+(percentage||affix.stat.equals("ATTACK_SPEED")?"%":affix.stat.equals("DROP_RATE")?" 个百分点":"");
    }
    public static List<Entry> entries(){
        var affixes=AffixDefinitions.current();var equipment=EquipmentDefinitions.current();
        if(affixSnapshot==affixes&&equipmentSnapshot==equipment&&affixRevision==affixes.revision&&equipmentRevision==equipment.revision)return cached;
        var applicable=new LinkedHashMap<String,EnumSet<Slot>>();
        var previews=new LinkedHashMap<String,LinkedHashMap<String,LinkedHashSet<String>>>();
        for(var slot:Slot.values()){
            var armors=slot.armor()?equipment.armorTypes.keySet():Set.of(ArmorType.UNTYPED);
            var jewelry=slot.jewelry()?equipment.jewelryTypes.keySet():Set.of(JewelryType.UNTYPED);
            var weapons=slot==Slot.WEAPON?Weapon.standard():List.of(Weapon.KATANA);
            for(var armor:armors)for(var jewel:jewelry)for(var weapon:weapons){
                var gear=new Gear();gear.slot=slot;gear.weapon=weapon;gear.armorType=armor;gear.jewelryType=jewel;gear.rarity=Rarity.LEGENDARY;gear.level=80;
                EquipmentGrowth.capture(gear);EquipmentRules.rebuildBases(gear);
                for(var definition:AffixGeneration.candidates(EquipmentRules.generationItem(gear),false)){
                    applicable.computeIfAbsent(definition.id(),id->EnumSet.noneOf(Slot.class)).add(slot);
                    String location=slot.armor()?armor.title()+slot.title():slot==Slot.WEAPON?weapon.title():slot.title();
                    previews.computeIfAbsent(definition.id(),id->new LinkedHashMap<>()).computeIfAbsent(preview(gear,definition),text->new LinkedHashSet<>()).add(location);
                }
            }
        }
        var result=new ArrayList<Entry>();
        applicable.forEach((id,slots)->{var definition=affixes.affixes.get(id);if(definition==null||!definition.active())return;
            String category=switch(definition.operation()){case "SKILL_FAMILY"->"技能等级";case "EXTRA"->"元素附伤";case "PERCENT"->"属性提升";default->"固定属性";};
            result.add(new Entry(id,definition.name(),category,definition.description()==null?"":definition.description(),List.copyOf(slots),previews.getOrDefault(id,new LinkedHashMap<>()).entrySet().stream().map(e->new Preview(e.getKey(),List.copyOf(e.getValue()))).toList()));
        });
        result.sort(Comparator.comparing(Entry::category).thenComparing(Entry::name).thenComparing(Entry::id));
        cached=List.copyOf(result);affixSnapshot=affixes;equipmentSnapshot=equipment;affixRevision=affixes.revision;equipmentRevision=equipment.revision;return cached;
    }
}
