package cn.yumis.fight.rules;
import cn.yumis.fight.rules.Models.Affix;
import cn.yumis.fight.rules.Rules.Rarity;
/** The saved baseline is authoritative, including after an equipment upgrade. */
public final class AffixRanges {
    public record Range(double minimum,double maximum,double upperMinimum,double upperMaximum,boolean interval){}
    private AffixRanges(){}
    public static Range reroll(Affix a,Rarity rarity,boolean gem){
        if(gem||a.base||a.fixed||rarity==Rarity.EPIC||rarity==Rarity.MYTHIC)return null;
        if(!a.operation.equals("FLAT")&&!a.operation.equals("PERCENT")&&!a.operation.equals("EXTRA")&&!a.operation.equals(AffixRules.ALL_RESIST_FLAT))return null;
        if(!Double.isFinite(a.baseline)||a.baseline<=0)return null;
        boolean interval=a.operation.equals("EXTRA");
        if(interval&&(!Double.isFinite(a.baselineMaximum)||a.baselineMaximum<a.baseline))return null;
        double scale=a.operation.equals("PERCENT")?100:1;
        return new Range(a.baseline*.8*scale,a.baseline*1.2*scale,a.baselineMaximum*.8,a.baselineMaximum*1.2,interval);
    }
}
