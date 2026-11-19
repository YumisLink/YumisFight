package cn.yumis.fight.modules.talents.api;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.combat.api.ElementAccumulation;
import java.util.*;

/** 效果 DSL 落地：无条件行进面板合成；带条件行仅在批 B 视图求值时生效；事件参数键（taken／击杀回复／回流）由客户端结算侧消费。 */
public final class TalentEffects {
    private TalentEffects(){}
    public static void apply(TalentCatalog c,Stats s,Progress p){apply(c,s,p.talentAxis,p.talentNodes,null);}
    /** view 为 null 表示静态合成：只落无条件行；批 B 以视图求值条件行。 */
    public static void apply(TalentCatalog c,Stats s,String axis,Set<String> nodes,TalentConditions.View view){
        if(axis!=null){var birth=c.birth(axis);if(birth!=null)for(var effect:birth.effects())apply(s,effect,0,0,view);}
        int lines=TalentLedger.completeLines(c,nodes),capstones=TalentLedger.capstonesLit(c,nodes);
        for(String id:nodes){var node=c.find(id);if(node==null)continue;for(var effect:node.effects())apply(s,effect,lines,capstones,view);}
    }
    private static void apply(Stats s,TalentCatalog.Effect effect,int lines,int capstones,TalentConditions.View view){
        if(effect.condition()!=null&&(view==null||!view.satisfies(effect.condition())))return;
        switch(effect.channel()){
            case "mut"->s.mut(stat(effect),effect.value());
            case "add"->s.add(stat(effect),effect.value());
            case "total"->s.attribute(stat(effect)).alls*=1+effect.value();
            case "final"->s.modifiers.merge("final",effect.value(),Double::sum);
            case "crit"->s.modifiers.merge("crit",effect.value(),Double::sum);
            case "taken"->s.modifiers.merge("taken",effect.value(),Double::sum);
            case "param"->s.modifiers.merge(effect.key(),effect.value(),Double::sum);
            case "count"->s.modifiers.merge("final",effect.value()*(effect.key().equals("capstone")?capstones:lines),Double::sum);
            case "killHeal"->s.modifiers.merge(effect.key().isEmpty()?"talentKillHeal":"talentKillHealSummon",effect.value(),Double::sum);
            case "conversion"->{
                int split=effect.key().indexOf(':');
                for(int element:elements(effect.key().substring(0,split)))ElementAccumulation.addConversion(s,element,ElementAccumulation.kind(effect.key().substring(split+1)),effect.value());
            }
            default->throw new IllegalArgumentException("未知效果通道："+effect.channel());
        }
    }
    private static Stat stat(TalentCatalog.Effect effect){return Stat.valueOf(effect.key());}
    private static int[] elements(String token){
        if(token.equals("ALL"))return new int[]{-1,0,1,2,3};
        var parts=token.split(",",-1);var result=new int[parts.length];
        for(int index=0;index<parts.length;index++)result[index]=Integer.parseInt(parts[index]);
        return result;
    }
}
