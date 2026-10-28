package cn.yumis.fight.modules.equipment.api;
import cn.yumis.fight.modules.attributes.api.Stat;

import java.util.*;

/** Typed templates and current native-equipment reference curves. */
public final class EquipmentTemplates {
    private EquipmentTemplates(){}
    public static ArmorType armor(Gear g){return g.armorType==null?ArmorType.UNTYPED:g.armorType;}
    public static JewelryType jewelry(Gear g){return g.jewelryType==null?JewelryType.UNTYPED:g.jewelryType;}
    public static double weight(Slot slot){return switch(slot){case CHEST->.4;case HEAD,FEET->.15;case LEGS->.3;default->throw new IllegalArgumentException("不是防具部位");};}
    public static boolean designed(Rarity rarity){return rarity!=Rarity.EPIC&&rarity!=Rarity.MYTHIC;}
    public static void validate(Gear g){
        if(NamedEquipment.find(g.template)!=null)return;
        if(MageBlood.is(g)){if(g.slot!=Slot.LEGS||g.rarity!=Rarity.MYTHIC||armor(g)!=ArmorType.CLOTH||g.level<1||g.level>85)throw new IllegalArgumentException("法师之血模板无效");return;}
        if(!g.slot.armor()&&armor(g)!=ArmorType.UNTYPED||!g.slot.jewelry()&&jewelry(g)!=JewelryType.UNTYPED)throw new IllegalArgumentException("装备类型与部位不符");
        if(!designed(g.rarity)&&(armor(g)!=ArmorType.UNTYPED||jewelry(g)!=JewelryType.UNTYPED))throw new IllegalArgumentException("此模板的史诗、神话版本待设计，尚未投放");
    }
    public static List<EquipmentDefinitions.Curve> bases(Gear g){
        if(MageBlood.is(g))return List.of();
        if(g.slot.armor()&&armor(g)!=ArmorType.UNTYPED){
            var t=EquipmentDefinitions.current().armorTypes.get(armor(g));
            if(t==null)throw new IllegalArgumentException("未知防具类型");
            var curves=new ArrayList<EquipmentDefinitions.Curve>();
            curves.add(new EquipmentDefinitions.Curve(Stat.PHYSICAL_DEFENSE,0,0,"armor_physical",t.defense()));
            curves.add(new EquipmentDefinitions.Curve(Stat.MAGICAL_DEFENSE,0,0,"armor_magical",t.defense()));
            if(t.shield()>0)curves.add(new EquipmentDefinitions.Curve(Stat.SHIELD,100,20,"",t.shield()*weight(g.slot)));
            if(t.evasion()>0)curves.add(new EquipmentDefinitions.Curve(Stat.EVASION,100,20,"",t.evasion()*weight(g.slot)));
            return curves;
        }
        if(g.slot.jewelry()&&jewelry(g)!=JewelryType.UNTYPED){
            var t=EquipmentDefinitions.current().jewelryTypes.get(jewelry(g));
            if(t==null)throw new IllegalArgumentException("未知首饰类型");
            return t.base();
        }
        return EquipmentDefinitions.current().equipment.get(g.slot).base();
    }
    public static Gear createStandard(Slot slot,Weapon weapon,Rarity rarity,int level,Random random){
        ArmorType armor=slot.armor()&&designed(rarity)?ArmorType.values()[1+random.nextInt(5)]:ArmorType.UNTYPED;
        JewelryType jewelry=slot.jewelry()&&designed(rarity)?JewelryType.values()[1+random.nextInt(11)]:JewelryType.UNTYPED;
        return EquipmentRules.create(slot,weapon,rarity,level,armor,jewelry,random);
    }
    public static String typeTitle(Gear g){
        return g.slot.armor()?armor(g).title():g.slot.jewelry()?jewelry(g).title():"";
    }
    public static List<Gear> creative(){
        List<Gear> all=new ArrayList<>();
        for(Weapon w:Weapon.standard())all.add(EquipmentRules.create(Slot.WEAPON,w,Rarity.COMMON,1,new Random(0)));
        for(Slot slot:Slot.values())if(slot!=Slot.WEAPON){
            if(slot.armor())for(ArmorType type:ArmorType.values()){if(type!=ArmorType.UNTYPED)all.add(EquipmentRules.create(slot,Weapon.KATANA,Rarity.COMMON,1,type,JewelryType.UNTYPED,new Random(0)));}
            else if(slot.jewelry())for(JewelryType type:JewelryType.values()){if(type!=JewelryType.UNTYPED)all.add(EquipmentRules.create(slot,Weapon.KATANA,Rarity.COMMON,1,ArmorType.UNTYPED,type,new Random(0)));}
            else all.add(EquipmentRules.create(slot,Weapon.KATANA,Rarity.COMMON,slot.minimum(),new Random(0)));
        }
        return all;
    }
    public static List<Gear> creative(Rarity rarity){
        if(rarity!=Rarity.EPIC&&rarity!=Rarity.MYTHIC)throw new IllegalArgumentException("仅史诗、神话目录");
        List<Gear> all=new ArrayList<>();Random random=new Random(0);
        for(int level:List.of(10,20,30,40,50)){
            for(Weapon w:Weapon.standard())all.add(EquipmentRules.create(Slot.WEAPON,w,rarity,level,random));
            for(Slot slot:List.of(Slot.BRACELET,Slot.NECKLACE,Slot.RING))all.add(EquipmentRules.create(slot,Weapon.KATANA,rarity,level,random));
        }
        if(rarity==Rarity.EPIC)for(Slot slot:List.of(Slot.CHEST,Slot.HEAD,Slot.LEGS,Slot.FEET))all.add(EquipmentRules.create(slot,Weapon.KATANA,rarity,50,random));
        for(Slot slot:List.of(Slot.SUB_EQUIPMENT,Slot.MAGIC_STONE,Slot.EARRING))all.add(EquipmentRules.create(slot,Weapon.KATANA,rarity,Math.max(50,slot.minimum()),random));
        if(rarity==Rarity.MYTHIC)all.add(MageBlood.create(random));
        for(var template:NamedEquipment.all())if(template.rarity()==rarity)all.add(NamedEquipment.create(template.id(),random));
        all.forEach(g->g.creativeTemplate=true);
        return all;
    }
    public static String appearance(Gear g){
        if(g.appearance!=null&&!g.appearance.isEmpty())return g.appearance;
        if(g.slot.armor()&&EquipmentTemplates.armor(g)!=ArmorType.UNTYPED)return EquipmentTemplates.armor(g).name().toLowerCase(Locale.ROOT)+"_"+g.slot.name().toLowerCase(Locale.ROOT);
        String kind=g.slot==Slot.WEAPON?g.weapon.name().toLowerCase(Locale.ROOT):g.slot.name().toLowerCase(Locale.ROOT);
        int band=g.level<20?10:g.level<30?20:g.level<40?30:g.level<50?40:50;
        String style=g.rarity==Rarity.EPIC?"epic":g.rarity==Rarity.MYTHIC?"mythic":"base";
        return kind+"_"+band+"_"+style;
    }
}
