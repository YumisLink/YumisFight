package cn.yumis.fight.modules.equipment.api;
import cn.yumis.fight.modules.affixes.api.*;
import java.util.*;
/** Approved named templates only. Generic enum registration never authorizes natural generation. */
public final class NamedEquipment {
    public record Template(String id,String name,int level,Rarity rarity,Slot slot,Weapon weapon,ArmorType armor,JewelryType jewelry,int sockets,List<String> effects){}
    private static final Map<String,Template> ALL=new LinkedHashMap<>();
    static {
        add("starter_staff","学徒法杖",1,Rarity.COMMON,Slot.WEAPON,Weapon.STAFF,ArmorType.UNTYPED,JewelryType.UNTYPED,0);
        add("willy_staff","威利的戒严法杖",55,Rarity.MYTHIC,Slot.WEAPON,Weapon.STAFF,ArmorType.UNTYPED,JewelryType.UNTYPED,8,"gem_amplification");
        add("skyspine","天脊乾坤剑",50,Rarity.EPIC,Slot.WEAPON,Weapon.LIGHTSABER,ArmorType.UNTYPED,JewelryType.UNTYPED,0,"fire_after_damage","fire_conversion","fire_only");
        add("bylle","别云剑-无用",55,Rarity.EPIC,Slot.WEAPON,Weapon.GREATSWORD,ArmorType.UNTYPED,JewelryType.UNTYPED,0,"bylle_physical_percent","bylle_physical_flat","block_elemental");
        add("golden_cup","波利斯的黄金杯",60,Rarity.EPIC,Slot.SUB_EQUIPMENT,Weapon.KATANA,ArmorType.UNTYPED,JewelryType.UNTYPED,0,"golden_power");
        add("rosetta","罗塞塔石碑",60,Rarity.EPIC,Slot.MAGIC_STONE,Weapon.KATANA,ArmorType.UNTYPED,JewelryType.UNTYPED,0,"imprint_power");
        add("hero_symbol","英雄王的象征",70,Rarity.EPIC,Slot.EARRING,Weapon.KATANA,ArmorType.UNTYPED,JewelryType.UNTYPED,0,"hero_power");
        add("niwu","尼巫的职责",40,Rarity.MYTHIC,Slot.WEAPON,Weapon.CLUB,ArmorType.UNTYPED,JewelryType.UNTYPED,0,"magic_ranks","niwu_speed","chaser_resistance_todo","chaser_limit_todo","chaser_skill_todo");
        add("spacetime","时空之肌",80,Rarity.MYTHIC,Slot.CHEST,Weapon.KATANA,ArmorType.CLOTH,JewelryType.UNTYPED,0,"cooldown_recovery","magic_cooldown","grant_blink");
        add("blood_oath","鲜血誓言",55,Rarity.EPIC,Slot.CHEST,Weapon.KATANA,ArmorType.HEAVY,JewelryType.UNTYPED,0,"kill_recovery","life_cost","life_steal");
        add("ryze","芮兹的第六元素",75,Rarity.MYTHIC,Slot.NECKLACE,Weapon.KATANA,ArmorType.UNTYPED,JewelryType.ALL_RESIST,0,"inverse_resists","resistance_cap");
        add("clown_hat","华丽的小丑帽",15,Rarity.EPIC,Slot.HEAD,Weapon.KATANA,ArmorType.CLOTH,JewelryType.UNTYPED,0,"clown_drop");
        add("crown","皇冠",35,Rarity.EPIC,Slot.HEAD,Weapon.KATANA,ArmorType.LIGHT,JewelryType.UNTYPED,0,"crown_resistance","crown_drop","experience_gain");
        add("mana_flow","魔力流转",45,Rarity.EPIC,Slot.BRACELET,Weapon.KATANA,ArmorType.UNTYPED,JewelryType.MP,0,"mana_regeneration","mana_share");
    }
    private static void add(String id,String name,int level,Rarity rarity,Slot slot,Weapon weapon,ArmorType armor,JewelryType jewelry,int sockets,String... effects){ALL.put("named/"+id,new Template("named/"+id,name,level,rarity,slot,weapon,armor,jewelry,sockets,List.of(effects)));}
    private NamedEquipment(){}
    public static Collection<Template> all(){return List.copyOf(ALL.values());}
    public static Template find(String id){return ALL.get(id);}
    public static Gear create(String id,Random random){
        Template t=Objects.requireNonNull(find(id),"未知具名装备");Gear g=new Gear();g.template=t.id;g.savedName=t.name;g.appearance=t.id.substring(6);g.level=t.level;g.rarity=t.rarity;g.slot=t.slot;g.weapon=t.weapon;g.armorType=t.armor;g.jewelryType=t.jewelry;g.sockets=t.sockets;
        EquipmentGrowth.capture(g);EquipmentRules.rebuildBases(g);
        for(String key:t.effects){var d=AffixDefinitions.current().affixes.get("unique/"+key);if(d==null)throw new IllegalStateException("缺少独特词条定义："+key);
            Affix a=new Affix();a.id=d.id();a.operation=d.operation();a.stat=d.stat()==null?"":d.stat().name();a.parameter=key;a.fixed=true;a.instanceId=UUID.randomUUID().toString();
            a.value=d.minimum()==null?(d.fixedValue()==null?1:d.fixedValue()):d.minimum()+random.nextDouble()*(d.maximum()-d.minimum());a.baseline=a.value;
            g.affixes.add(a);AffixResolver.anchor(a,g.instance+"/"+a.instanceId,d,d.revision());
        }
        return EquipmentResolution.initialize(g);
    }
}
