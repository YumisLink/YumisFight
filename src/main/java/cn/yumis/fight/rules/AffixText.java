package cn.yumis.fight.rules;

import java.math.*;
import static cn.yumis.fight.rules.Rules.*;
import static cn.yumis.fight.rules.Models.*;

/** Pure display formatting; never writes back rounded numbers. */
public final class AffixText {
    private AffixText(){}
    public static String integer(double value){return Double.isFinite(value)?BigDecimal.valueOf(value).setScale(0,RoundingMode.HALF_UP).toPlainString():"?";}
    public static String statName(Stat s){return switch(s){
        case HEALTH->"最大生命";case MP->"最大魔力";case STRENGTH->"力量";case INTELLIGENCE->"智力";case SPIRIT->"精神";
        case PHYSICAL_ATTACK->"基础物理攻击";case MAGICAL_ATTACK->"基础魔法攻击";case PHYSICAL_DEFENSE->"物理防御";case MAGICAL_DEFENSE->"魔法防御";
        case LIGHT->"光属性强化";case FIRE->"火属性强化";case ICE->"冰属性强化";case DARK->"暗属性强化";
        case LIGHT_RESIST->"光属性抗性";case FIRE_RESIST->"火属性抗性";case ICE_RESIST->"冰属性抗性";case DARK_RESIST->"暗属性抗性";
        case DROP_RATE->"掉宝率";case SHIELD->"装备护盾上限";case EVASION->"闪避值";
    };}
    public static String affix(Affix a){
        String body;
        if(a.operation.equals("SKILL_FAMILY"))return "所有"+Family.valueOf(a.family).title()+"系技能 +"+a.rank+"级（固定）";
        if(a.operation.equals("EXTRA"))body="附加"+new String[]{"光","火","冰","暗"}[a.element]+"属性伤害 "+integer(a.value)+"～"+integer(a.maximum);
        else if(a.operation.equals(AffixRules.ALL_RESIST_FLAT))body="所有属性抗性 +"+integer(a.value);
        else if(a.stat.isEmpty())body=a.id;
        else{
            boolean percent=a.operation.equals("PERCENT");Stat s=Stat.valueOf(a.stat);
            body=(percent?"本装备":"")+statName(s)+" +"+integer(a.value*(percent?100:1))+(percent?"%":s==Stat.DROP_RATE?" 个百分点":"");
        }
        var definition=Definitions.CURRENT.affixes.get(a.id);
        return !a.base&&definition!=null&&!definition.name().equals(a.id)?definition.name()+"："+body:body;
    }
}
