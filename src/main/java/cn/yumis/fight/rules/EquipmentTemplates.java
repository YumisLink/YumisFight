package cn.yumis.fight.rules;

import java.util.*;
import static cn.yumis.fight.rules.Rules.*;
import static cn.yumis.fight.rules.Models.*;

/** Typed templates are additive; legacy items and native armor retain their original bases. */
public final class EquipmentTemplates {
    private EquipmentTemplates(){}
    public static ArmorType armor(Gear g){return g.armorType==null?ArmorType.LEGACY:g.armorType;}
    public static JewelryType jewelry(Gear g){return g.jewelryType==null?JewelryType.LEGACY:g.jewelryType;}
    public static double weight(Slot slot){return switch(slot){case CHEST->.4;case HEAD,FEET->.15;case LEGS->.3;default->throw new IllegalArgumentException("不是防具部位");};}
    public static boolean designed(Rarity rarity){return rarity!=Rarity.EPIC&&rarity!=Rarity.MYTHIC;}
    public static void validate(Gear g){
        if(!g.slot.armor()&&armor(g)!=ArmorType.LEGACY||!g.slot.jewelry()&&jewelry(g)!=JewelryType.LEGACY)throw new IllegalArgumentException("装备类型与部位不符");
        if(!designed(g.rarity)&&(armor(g)!=ArmorType.LEGACY||jewelry(g)!=JewelryType.LEGACY))throw new IllegalArgumentException("此模板的史诗、神话版本待设计，尚未投放");
    }
    public static List<Definitions.Curve> bases(Gear g){
        if(g.slot.armor()&&armor(g)!=ArmorType.LEGACY){
            var t=Definitions.CURRENT.armorTypes.get(armor(g));
            if(t==null)throw new IllegalArgumentException("未知防具类型");
            var curves=new ArrayList<Definitions.Curve>();
            curves.add(new Definitions.Curve(Stat.PHYSICAL_DEFENSE,0,0,"armor_physical",t.defense()));
            curves.add(new Definitions.Curve(Stat.MAGICAL_DEFENSE,0,0,"armor_magical",t.defense()));
            if(t.shield()>0)curves.add(new Definitions.Curve(Stat.SHIELD,100,20,"",t.shield()*weight(g.slot)));
            if(t.evasion()>0)curves.add(new Definitions.Curve(Stat.EVASION,100,20,"",t.evasion()*weight(g.slot)));
            return curves;
        }
        if(g.slot.jewelry()&&jewelry(g)!=JewelryType.LEGACY){
            var t=Definitions.CURRENT.jewelryTypes.get(jewelry(g));
            if(t==null)throw new IllegalArgumentException("未知首饰类型");
            return t.base();
        }
        return Definitions.CURRENT.equipment.get(g.slot).base();
    }
    public static Gear createStandard(Slot slot,Weapon weapon,Rarity rarity,int level,Random random){
        ArmorType armor=slot.armor()&&designed(rarity)?ArmorType.values()[1+random.nextInt(5)]:ArmorType.LEGACY;
        JewelryType jewelry=slot.jewelry()&&designed(rarity)?JewelryType.values()[1+random.nextInt(11)]:JewelryType.LEGACY;
        return EquipmentRules.create(slot,weapon,rarity,level,armor,jewelry,random);
    }
    public static String typeTitle(Gear g){
        return g.slot.armor()?armor(g).title():g.slot.jewelry()?jewelry(g).title():"";
    }
    public static List<Gear> creative(){
        List<Gear> all=new ArrayList<>();
        for(Weapon w:Weapon.values())all.add(EquipmentRules.create(Slot.WEAPON,w,Rarity.COMMON,1,new Random(0)));
        for(Slot slot:Slot.values())if(slot!=Slot.WEAPON){
            if(slot.armor())for(ArmorType type:ArmorType.values()){if(type!=ArmorType.LEGACY)all.add(EquipmentRules.create(slot,Weapon.KATANA,Rarity.COMMON,1,type,JewelryType.LEGACY,new Random(0)));}
            else if(slot.jewelry())for(JewelryType type:JewelryType.values()){if(type!=JewelryType.LEGACY)all.add(EquipmentRules.create(slot,Weapon.KATANA,Rarity.COMMON,1,ArmorType.LEGACY,type,new Random(0)));}
            else all.add(EquipmentRules.create(slot,Weapon.KATANA,Rarity.COMMON,slot.minimum(),new Random(0)));
        }
        return all;
    }
}
