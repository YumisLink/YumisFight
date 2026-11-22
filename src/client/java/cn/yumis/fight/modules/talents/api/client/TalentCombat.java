package cn.yumis.fight.modules.talents.api.client;

import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.talents.api.TalentCatalog;
import cn.yumis.fight.modules.talents.api.TalentConditions;
import cn.yumis.fight.modules.talents.api.TalentEffects;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import java.util.*;

/** 批 B 结算求值：活状态与事件上下文构成视图，条件行只在当次结算属性上叠加；无条件行已由静态合成落过。 */
public final class TalentCombat {
    /** 结算上下文：活状态读本人客户端，事件位由当次结算填入；击杀／闪避时间戳由本类持有，求值时取本机时钟。 */
    public record Context(double lifeFraction,double mpFraction,boolean shieldActive,boolean summonActive,double killWindow,boolean critEvent,Set<String> ailments,double targetLifeFraction){
        public Context withCrit(){return new Context(lifeFraction,mpFraction,shieldActive,summonActive,killWindow,true,ailments,targetLifeFraction);}
    }
    private static volatile long lastKillAt,lastDodgeAt;
    private static final int EXPECTED_LIMIT=256;
    private static final Map<UUID,Boolean> EXPECTED_SUMMON_KILLS=new LinkedHashMap<>();
    private TalentCombat(){}
    public static void killed(){lastKillAt=ClientUi.now();}
    public static void dodged(){lastDodgeAt=ClientUi.now();}
    /** 召唤物命中预测击杀：回执确认死亡时以此判定击杀来源并清除标记。 */
    public static void summonKillExpected(UUID targetId){
        synchronized(EXPECTED_SUMMON_KILLS){
            EXPECTED_SUMMON_KILLS.put(targetId,Boolean.TRUE);
            while(EXPECTED_SUMMON_KILLS.size()>EXPECTED_LIMIT)EXPECTED_SUMMON_KILLS.remove(EXPECTED_SUMMON_KILLS.keySet().iterator().next());
        }
    }
    public static boolean consumeSummonKill(UUID targetId){
        synchronized(EXPECTED_SUMMON_KILLS){return EXPECTED_SUMMON_KILLS.remove(targetId)==Boolean.TRUE;}
    }
    public static void clear(){lastKillAt=lastDodgeAt=0;synchronized(EXPECTED_SUMMON_KILLS){EXPECTED_SUMMON_KILLS.clear();}}
    public static TalentConditions.View view(Context c){
        return condition->{
            long now=ClientUi.now();
            return switch(condition.kind()){
                case "selfLifeBelow"->c.lifeFraction()<condition.threshold();
                case "selfMpAbove"->c.mpFraction()>condition.threshold();
                case "shieldActive"->c.shieldActive();
                case "summonActive"->c.summonActive();
                case "lastKillWithin"->lastKillAt>0&&now-lastKillAt<=Math.max(condition.threshold(),c.killWindow());
                case "lastDodgeWithin"->lastDodgeAt>0&&now-lastDodgeAt<=condition.threshold();
                case "critEvent"->c.critEvent();
                case "targetAilment"->!c.ailments().isEmpty()&&(condition.tag()==null||condition.tag().equals(TalentConditions.ANY_AILMENT)||c.ailments().contains(condition.tag()));
                case "targetLifeAbove"->c.targetLifeFraction()>condition.threshold();
                case "targetLifeBelow"->c.targetLifeFraction()<condition.threshold();
                default->false;
            };
        };
    }
    /** 首遍命中／受击：叠加全部满足的条件行；critEvent 位由暴击重算单独处理。 */
    public static void apply(Stats settlement,Context c,Progress p){TalentEffects.applyConditional(TalentCatalog.current(),settlement,p.talentAxis,p.talentNodes,view(c));}
    /** 暴击已定后的二次结算：只叠加 critEvent 行。 */
    public static void applyCrit(Stats settlement,Context c,Progress p){TalentEffects.applyConditionalKind(TalentCatalog.current(),settlement,p.talentAxis,p.talentNodes,view(c),"critEvent");}
    public static boolean conditionalLit(Progress p){return TalentEffects.hasConditional(TalentCatalog.current(),p.talentAxis,p.talentNodes);}
    public static boolean critLit(Progress p){return TalentEffects.hasConditionalKind(TalentCatalog.current(),p.talentAxis,p.talentNodes,"critEvent");}
}
