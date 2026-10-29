package cn.yumis.fight.modules.equipment.api;

import cn.yumis.fight.modules.attributes.api.Stat;
import java.util.*;

/** Item-only tooltip projection. It never reads a player, rolls damage, or changes combat contributions. */
public final class WeaponDisplayValues {
    public static final int REVISION=1;
    public record Extra(int element,double minimum,double maximum){}
    public record Values(double physicalAttack,double magicalAttack,List<Extra> extras){
        public Values{extras=List.copyOf(extras);}
    }
    private WeaponDisplayValues(){}
    public static Values project(Gear original){
        if(original.slot!=Slot.WEAPON)throw new IllegalArgumentException("武器展示只接受武器");
        Gear gear=EquipmentResolution.resolve(original);
        Map<Stat,Double> contribution=EquipmentRules.contribution(gear);
        var affixes=EquipmentEffects.effectiveAffixes(gear);
        boolean blocked=affixes.stream().anyMatch(a->a.operation.equals("EQUIPMENT_RULE")&&a.parameter.equals("block_elemental"));
        boolean fireOnly=affixes.stream().anyMatch(a->a.operation.equals("EQUIPMENT_RULE")&&a.parameter.equals("fire_only"));
        double[] minimum=new double[4],maximum=new double[4];boolean[] present=new boolean[4];
        for(var affix:affixes){
            if(!affix.operation.equals("EXTRA")||affix.element<0||affix.element>=4||blocked||fireOnly&&affix.element!=1)continue;
            minimum[affix.element]+=affix.value;maximum[affix.element]+=affix.maximum;present[affix.element]=true;
        }
        List<Extra> extras=new ArrayList<>();for(int element=0;element<4;element++)if(present[element])extras.add(new Extra(element,minimum[element],maximum[element]));
        // Proportional/conditional post-hit additions remain effect descriptions, not fixed item intervals.
        return new Values(contribution.getOrDefault(Stat.PHYSICAL_ATTACK,0d),contribution.getOrDefault(Stat.MAGICAL_ATTACK,0d),extras);
    }
}
