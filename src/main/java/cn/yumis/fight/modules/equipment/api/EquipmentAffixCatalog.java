package cn.yumis.fight.modules.equipment.api;

import cn.yumis.fight.modules.affixes.api.*;
import java.util.*;

/** Read-only union of the live random generation qualifications, never a second affix pool. */
public final class EquipmentAffixCatalog {
    public record Entry(String id,String name,String category,String description,List<Slot> slots){}
    private static Object affixSnapshot,equipmentSnapshot;
    private static long affixRevision=-1,equipmentRevision=-1;
    private static List<Entry> cached=List.of();
    private EquipmentAffixCatalog(){}
    public static List<Entry> entries(){
        var affixes=AffixDefinitions.current();var equipment=EquipmentDefinitions.current();
        if(affixSnapshot==affixes&&equipmentSnapshot==equipment&&affixRevision==affixes.revision&&equipmentRevision==equipment.revision)return cached;
        var applicable=new LinkedHashMap<String,EnumSet<Slot>>();
        for(var slot:Slot.values()){
            var armors=slot.armor()?equipment.armorTypes.keySet():Set.of(ArmorType.UNTYPED);
            var jewelry=slot.jewelry()?equipment.jewelryTypes.keySet():Set.of(JewelryType.UNTYPED);
            var weapons=slot==Slot.WEAPON?Weapon.standard():List.of(Weapon.KATANA);
            for(var armor:armors)for(var jewel:jewelry)for(var weapon:weapons){
                var gear=new Gear();gear.slot=slot;gear.weapon=weapon;gear.armorType=armor;gear.jewelryType=jewel;gear.rarity=Rarity.LEGENDARY;gear.level=80;
                EquipmentGrowth.capture(gear);EquipmentRules.rebuildBases(gear);
                for(var definition:AffixGeneration.candidates(EquipmentRules.generationItem(gear),false))applicable.computeIfAbsent(definition.id(),id->EnumSet.noneOf(Slot.class)).add(slot);
            }
        }
        var result=new ArrayList<Entry>();
        applicable.forEach((id,slots)->{var definition=affixes.affixes.get(id);if(definition==null||!definition.active())return;
            String category=switch(definition.operation()){case "SKILL_FAMILY"->"技能等级";case "EXTRA"->"元素附伤";case "PERCENT"->"属性提升";default->"固定属性";};
            result.add(new Entry(id,definition.name(),category,definition.description()==null?"":definition.description(),List.copyOf(slots)));
        });
        result.sort(Comparator.comparing(Entry::category).thenComparing(Entry::name).thenComparing(Entry::id));
        cached=List.copyOf(result);affixSnapshot=affixes;equipmentSnapshot=equipment;affixRevision=affixes.revision;equipmentRevision=equipment.revision;return cached;
    }
}
