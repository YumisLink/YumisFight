package cn.yumis.fight.modules.equipment.api;
import cn.yumis.fight.modules.affixes.api.AffixDefinitions;

import cn.yumis.fight.modules.affixes.api.Affix;
/** The saved baseline is authoritative, including after an equipment upgrade. */
public final class EquipmentAffixRanges {
    public record Range(double minimum,double maximum,double upperMinimum,double upperMaximum,boolean interval){}
    private EquipmentAffixRanges(){}
    public static boolean percentage(Affix a){return a.operation.equals("PERCENT")||a.operation.equals("CHARACTER_PERCENT")||a.operation.equals("EQUIPMENT_RULE")&&java.util.Set.of("gem_amplification","fire_after_damage","kill_recovery","life_cost","mana_share").contains(a.parameter);}
    public static Range reroll(Affix a,Rarity rarity,boolean gem){
        if(gem||!EquipmentRules.washable(a))return null;
        var definition=AffixDefinitions.current().affixes.get(a.id);
        if(definition!=null&&definition.minimum()!=null){
            double scale=definition.scale()*(percentage(a)?100:1);
            return new Range(definition.minimum()*scale,definition.maximum()*scale,definition.upperMinimum()==null?0:definition.upperMinimum()*definition.scale(),definition.upperMaximum()==null?0:definition.upperMaximum()*definition.scale(),a.operation.equals("EXTRA"));
        }
        if(a.stat.equals("ATTACK_SPEED"))return new Range(a.baseline,a.baselineMaximum,0,0,false);
        if(!a.operation.equals("FLAT")&&!a.operation.equals("PERCENT")&&!a.operation.equals("EXTRA")&&!a.operation.equals(EquipmentAffixes.ALL_RESIST_FLAT))return null;
        if(!Double.isFinite(a.baseline)||a.baseline<=0)return null;
        boolean interval=a.operation.equals("EXTRA");
        if(interval&&(!Double.isFinite(a.baselineMaximum)||a.baselineMaximum<a.baseline))return null;
        double scale=(a.operation.equals("PERCENT")||a.operation.equals("CHARACTER_PERCENT"))?100:1;
        return new Range(a.baseline*.8*scale,a.baseline*1.2*scale,a.baselineMaximum*.8,a.baselineMaximum*1.2,interval);
    }
}
