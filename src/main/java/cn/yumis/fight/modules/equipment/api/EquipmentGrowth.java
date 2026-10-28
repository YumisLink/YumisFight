package cn.yumis.fight.modules.equipment.api;
import cn.yumis.fight.modules.attributes.api.Stat;
/** Creation-time base values for every supported level. Rule reloads cannot replace an item's investment. */
public final class EquipmentGrowth {
    private EquipmentGrowth(){}
    public static void capture(Gear gear){
        if(!gear.baseGrowth.isEmpty())throw new IllegalStateException("装备成长快照已建立");
        double scale=gear.rarity==Rarity.MYTHIC?1.9:gear.rarity==Rarity.EPIC?(gear.slot==Slot.WEAPON?1.6:1.5):1;
        for(var curve:EquipmentTemplates.bases(gear)){
            double[] values=gear.baseGrowth.computeIfAbsent(curve.stat(),key->new double[85]);
            double weaponScale=gear.slot==Slot.WEAPON&&(curve.stat()==Stat.PHYSICAL_ATTACK||curve.stat()==Stat.MAGICAL_ATTACK)?WeaponTraits.attackScale(gear.weapon,curve.stat()):1;
            for(int level=1;level<=85;level++)values[level-1]+=curve.at(gear.slot,level)*scale*weaponScale;
        }
    }
}
