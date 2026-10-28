package cn.yumis.fight.modules.equipment.api;
import cn.yumis.fight.modules.affixes.api.AffixDefinitions;
import cn.yumis.fight.modules.attributes.api.Stat;

import static cn.yumis.fight.modules.equipment.api.EquipmentCurves.defenses;
import cn.yumis.fight.modules.affixes.api.AttackSpeedRoll;

import java.util.*;
import cn.yumis.fight.modules.affixes.api.Affix;

/** Eligibility reads bases only. Effect calculation deliberately continues to include all item sources. */
public final class EquipmentAffixes {
    public static final String ALL_RESIST_FLAT="ALL_RESIST_FLAT";
    private EquipmentAffixes(){}
    public static boolean sixJewelry(Slot s){return s.jewelry()||s==Slot.SUB_EQUIPMENT||s==Slot.MAGIC_STONE||s==Slot.EARRING;}
    public static boolean elementFlat(Slot s){return s==Slot.WEAPON||s.jewelry()||s==Slot.SUB_EQUIPMENT||s==Slot.MAGIC_STONE;}
    public static boolean evasionArmor(Gear g){return g.slot.armor()&&(EquipmentTemplates.armor(g)==ArmorType.LEATHER||EquipmentTemplates.armor(g)==ArmorType.LIGHT);}
    public static double base(Gear g,Stat s){return g.affixes.stream().filter(a->a.base&&a.operation.equals("FLAT")&&a.stat.equals(s.name())).mapToDouble(a->a.value).sum();}
    public static boolean flatAllowed(Gear g,Stat s){
        Slot slot=g.slot;
        return switch(s){
            case MOVEMENT_SPEED,CAST_SPEED,LIFE_STEAL,MANA_REGENERATION,COOLDOWN_RECOVERY,EXPERIENCE_GAIN->false;
            case ATTACK_SPEED->slot==Slot.WEAPON||sixJewelry(slot);
            case HEALTH->slot.armor();
            case MP,STRENGTH,INTELLIGENCE,SPIRIT->slot.armor()||slot.jewelry();
            case PHYSICAL_ATTACK,MAGICAL_ATTACK->slot==Slot.WEAPON;
            case PHYSICAL_DEFENSE,MAGICAL_DEFENSE->true;
            case LIGHT,FIRE,ICE,DARK,LIGHT_RESIST,FIRE_RESIST,ICE_RESIST,DARK_RESIST->elementFlat(slot);
            case DROP_RATE->slot.jewelry()||slot==Slot.SUB_EQUIPMENT||slot==Slot.EARRING;
            case SHIELD->slot.armor()&&base(g,s)>0;
            case EVASION->evasionArmor(g);
        };
    }
    public static boolean percentAllowed(Gear g,Stat s){
        if(base(g,s)<=0)return false;
        Slot slot=g.slot;
        return switch(s){
            case MOVEMENT_SPEED,CAST_SPEED,LIFE_STEAL,MANA_REGENERATION,COOLDOWN_RECOVERY,EXPERIENCE_GAIN->false;
            case ATTACK_SPEED->false;
            case HEALTH,MP->slot.armor()||slot.jewelry();
            case STRENGTH,INTELLIGENCE,SPIRIT->true;
            case PHYSICAL_ATTACK,MAGICAL_ATTACK->!slot.armor();
            case PHYSICAL_DEFENSE,MAGICAL_DEFENSE,SHIELD->slot.armor();
            case LIGHT,FIRE,ICE,DARK->slot==Slot.WEAPON||slot==Slot.SUB_EQUIPMENT||slot==Slot.MAGIC_STONE||slot==Slot.EARRING;
            case DROP_RATE->sixJewelry(slot);
            case EVASION->evasionArmor(g);
            case LIGHT_RESIST,FIRE_RESIST,ICE_RESIST,DARK_RESIST->false;
        };
    }
    public static List<cn.yumis.fight.modules.affixes.api.AffixDefinition> candidates(Gear g,boolean percent,boolean duplicates){
        List<cn.yumis.fight.modules.affixes.api.AffixDefinition> result=new ArrayList<>();
        for(var d:AffixDefinitions.current().affixes.values()){
            if(!d.active()||!d.group().equals("random"))continue;
            boolean all=d.operation().equals(ALL_RESIST_FLAT);
            if(percent?!d.operation().equals("PERCENT"):!d.operation().equals("FLAT")&&!all)continue;
            if(all?!elementFlat(g.slot):d.stat()==null||!(percent?percentAllowed(g,d.stat()):flatAllowed(g,d.stat())))continue;
            if(!duplicates&&g.affixes.stream().anyMatch(a->!a.base&&(percent||!a.fixed)&&a.operation.equals(d.operation())&&(all||a.stat.equals(d.stat().name()))))continue;
            result.add(d);
        }
        return result;
    }
    public static double percentBaseline(int level,Stat s){
        return switch(s){case PHYSICAL_DEFENSE,MAGICAL_DEFENSE,SHIELD,EVASION->.175+1.075*(level-1)/79d;default->.175;};
    }
    public static double reference(Gear g,Stat s){
        int n=g.level-1;
        return switch(s){
            case ATTACK_SPEED->4;
            case HEALTH,SHIELD->100+20*n;
            case MP->100+14*n;
            case STRENGTH->20+3*n;
            case INTELLIGENCE->15+2*n;
            case SPIRIT->10+2*n;
            case PHYSICAL_ATTACK,MAGICAL_ATTACK->15+4*n;
            case PHYSICAL_DEFENSE->g.slot.armor()?defenses(g.level,false)[g.slot.ordinal()-1]:(80+15*n)/4d;
            case MAGICAL_DEFENSE->g.slot.armor()?defenses(g.level,true)[g.slot.ordinal()-1]:(40+10*n)/4d;
            case EVASION->2000*(100+20*n)/1080d*EquipmentTemplates.weight(g.slot);
            case DROP_RATE->2;
            default->3+.15*n;
        };
    }
    public static double baseline(Gear g,Affix a){
        if(a.operation.equals(ALL_RESIST_FLAT))return .375*(3+.15*(g.level-1));
        Stat s=Stat.valueOf(a.stat);
        if(a.operation.equals("PERCENT"))return percentBaseline(g.level,s);
        if(s==Stat.DROP_RATE)return 2;
        if(s.ordinal()>=Stat.LIGHT.ordinal()&&s.ordinal()<=Stat.DARK_RESIST.ordinal())return reference(g,s);
        return reference(g,s)*(s==Stat.HEALTH||s==Stat.MP||s==Stat.SHIELD?.045:.075);
    }
    public static void grow(Gear g){
        for(Affix a:g.affixes){
            if(a.base||a.fixed||!AffixDefinitions.current().affixes.containsKey(a.id))continue;
            if(a.stat.equals("ATTACK_SPEED")){a.value=AttackSpeedRoll.sample(g.level,a.roll);a.baseline=AttackSpeedRoll.sample(g.level,.8);a.baselineMaximum=AttackSpeedRoll.sample(g.level,1.2);a.origin=null;continue;}
            if(a.operation.equals("EXTRA")){
                a.baseline=.1*EquipmentRules.budget(g.level);a.baselineMaximum=.2*EquipmentRules.budget(g.level);
            }else if(a.operation.equals("FLAT")||a.operation.equals("PERCENT")||a.operation.equals(ALL_RESIST_FLAT))a.baseline=baseline(g,a);
            else continue;
            a.value=a.baseline*a.roll;a.maximum=a.baselineMaximum*a.roll;a.origin=null;
        }
    }
}
