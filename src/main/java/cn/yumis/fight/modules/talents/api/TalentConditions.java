package cn.yumis.fight.modules.talents.api;
import java.util.*;

/** 条件来源位合同：标签集合与描述文案。批 A 只登记合同与 DSL 校验；求值实现属批 B 客户端。 */
public final class TalentConditions {
    private TalentConditions(){}
    /** 活状态标签读自身客户端；事件标签由伤害／受击结算当次上下文填入视图。 */
    public static final Set<String> KINDS=Set.of("selfLifeBelow","selfMpAbove","shieldActive","summonActive","lastKillWithin","lastDodgeWithin","critEvent","targetAilment","targetLifeAbove","targetLifeBelow");
    public static final Set<String> AILMENTS=Set.of("ELEMENT_SHOCK","ELEMENT_IGNITE","ELEMENT_FREEZE","ELEMENT_EROSION");
    public static final String ANY_AILMENT="ANY";
    /** 求值视图：批 B 客户端以活状态与事件上下文实现；静态合成为 null＝只落无条件行。 */
    public interface View{boolean satisfies(TalentCatalog.Condition condition);}
    public static String describe(String kind,double threshold,String tag){
        return switch(kind){
            case "selfLifeBelow"->"生命低于"+percent(threshold)+"时";
            case "selfMpAbove"->"法力高于"+percent(threshold)+"时";
            case "shieldActive"->"护盾存在时";
            case "summonActive"->"召唤兽存在时";
            case "lastKillWithin"->"击败敌人后"+(long)(threshold/1000)+"秒内";
            case "lastDodgeWithin"->"闪避成功后"+(long)(threshold/1000)+"秒内";
            case "critEvent"->"暴击时";
            case "targetAilment"->"目标处于"+(tag==null||tag.equals(ANY_AILMENT)?"任意异常":tag)+"时";
            case "targetLifeAbove"->"目标生命高于"+percent(threshold)+"时";
            case "targetLifeBelow"->"目标生命低于"+percent(threshold)+"时";
            default->throw new IllegalArgumentException("未知条件标签："+kind);
        };
    }
    private static String percent(double threshold){return Math.round(threshold*100)+"%";}
}
