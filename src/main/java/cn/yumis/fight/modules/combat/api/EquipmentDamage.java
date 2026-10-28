package cn.yumis.fight.modules.combat.api;
import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.equipment.api.EquipmentEffects;
import java.util.*;
/** Explicit component policies and post-resolution additions, shared by preview and actual damage. */
public final class EquipmentDamage {
    private EquipmentDamage(){}
    public static int[] elements(EquipmentEffects.Snapshot effects,int[] original){if(effects.fireOnly){if(original.length==0||Arrays.stream(original).anyMatch(e->e==1))return new int[]{1};return original;}return original;}
    public static boolean allowed(EquipmentEffects.Snapshot effects,int[] elements){return !(effects.blockElemental&&elements.length>0)&&(!effects.fireOnly||elements.length==0||Arrays.stream(elements).anyMatch(e->e==1));}
    public static double[] resistances(EquipmentEffects.Snapshot effects,double[] original){double[] result=original.clone();if(effects.inverseResists)for(int i=0;i<result.length;i++)result[i]=-result[i];return result;}
    public static double append(double resolved,Stats stats,EquipmentEffects.Snapshot effects,double defense,double[] resist){if(resolved<=0||effects.fireAfterDamage<=0||!allowed(effects,new int[]{1}))return 0;return resolved*effects.fireAfterDamage*1000/(1000+Math.max(0,defense))*Math.max(0,1+(stats.get(Stat.FIRE)-resist[1])/120);}
    public static double lifeSteal(double rating){double r=Math.max(0,rating),threshold=1000d/19;if(r>threshold)r=threshold+(r-threshold)/3;return Math.min(.9,r/(1000+r));}
}
