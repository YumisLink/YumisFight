package cn.yumis.fight.modules.combat.api;
import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.equipment.api.EquipmentEffects;
import java.util.*;
/** Explicit component policies and post-resolution additions, shared by preview and actual damage. */
public final class EquipmentDamage {
    private EquipmentDamage(){}
    public static int[] elements(EquipmentEffects.Snapshot effects,int[] original){
        int[] merged=original;
        if(effects.weaponElement>=0&&Arrays.stream(original).noneMatch(e->e==effects.weaponElement)){
            merged=Arrays.copyOf(original,original.length+1);
            merged[original.length]=effects.weaponElement;
        }
        if(effects.fireOnly){
            if(merged.length==0||Arrays.stream(merged).anyMatch(e->e==1))return new int[]{1};
            return merged;
        }
        return merged;
    }
    public static boolean allowed(EquipmentEffects.Snapshot effects,int[] elements){return !(effects.blockElemental&&elements.length>0)&&(!effects.fireOnly||elements.length==0||Arrays.stream(elements).anyMatch(e->e==1));}
    public static double[] resistances(EquipmentEffects.Snapshot effects,double[] original){double[] result=original.clone();if(effects.inverseResists)for(int i=0;i<result.length;i++)result[i]=-result[i];return result;}
    public static double append(double resolved,Stats stats,EquipmentEffects.Snapshot effects,double defense,double[] resist){if(resolved<=0||effects.fireAfterDamage<=0||!allowed(effects,new int[]{1}))return 0;return resolved*effects.fireAfterDamage*1000/(1000+Math.max(0,defense))*Math.max(0,1+(stats.get(Stat.FIRE)-resist[1])/120);}
    public static double appendLight(double resolved,Stats stats,EquipmentEffects.Snapshot effects,double defense,double[] resist,double ratio){
        if(resolved<=0||ratio<=0||!allowed(effects,new int[]{0}))return 0;
        double divisor=CombatBalanceRules.current().combat.elementalDivisor();
        return resolved*ratio*CombatRules.defenseMultiplier(defense)*Math.max(0,1+(stats.get(Stat.LIGHT)-resist[0])/divisor);
    }
    public static double appendNeutralPhysical(double resolved,EquipmentEffects.Snapshot effects,double physicalDefense,double[] resist){
        if(resolved<=0||effects.neutralPhysicalAfterDamage<=0)return 0;
        return resolved*effects.neutralPhysicalAfterDamage*CombatRules.defenseMultiplier(physicalDefense)
            *Math.max(0,1-CombatRules.highestPositiveResistance(resist)/120);
    }
    /** Diminishing curve: 1 pt ≈ 0.01%, strictly decreasing marginal return, asymptotic cap 0.9%. */
    public static double lifeSteal(double rating){if(!Double.isFinite(rating)||rating<=0)return 0;return .009*rating/(90+rating);}
}
