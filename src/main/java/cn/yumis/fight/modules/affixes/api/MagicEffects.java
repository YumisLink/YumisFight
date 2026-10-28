package cn.yumis.fight.modules.affixes.api;
import cn.yumis.fight.modules.attributes.api.Stat;

import java.util.*;
import java.util.function.BiConsumer;

/** List-valued magic content. IDs and handlers stay in the affix module. */
public final class MagicEffects {
    public interface Sink {void extra(Stat stat,double amount);void mut(Stat stat,double amount);void modifier(String name,double amount,boolean mut);}
    public record Definition(String id,String title,String description,BiConsumer<Sink,Double> effect){}
    private static final Map<String,Definition> DEFINITIONS=new LinkedHashMap<>();
    static {
        register("BASALT","玄武岩","双防提高150%",(s,q)->{s.mut(Stat.PHYSICAL_DEFENSE,1.5*q);s.mut(Stat.MAGICAL_DEFENSE,1.5*q);});
        register("GRANITE","灰岩","四元素抗性各+30",(s,q)->{for(Stat stat:List.of(Stat.LIGHT_RESIST,Stat.FIRE_RESIST,Stat.ICE_RESIST,Stat.DARK_RESIST))s.extra(stat,30*q);});
        register("DIAMOND","钻石","暴击率+10%",(s,q)->s.modifier("crit",.1*q,false));
        register("GOLD","黄金","掉宝率+80",(s,q)->s.extra(Stat.DROP_RATE,80*q));
        register("ROCK","坚岩","双防各+4000",(s,q)->{s.extra(Stat.PHYSICAL_DEFENSE,4000*q);s.extra(Stat.MAGICAL_DEFENSE,4000*q);});
        register("JADE","青玉","闪避值+2000",(s,q)->s.extra(Stat.EVASION,2000*q));
        register("MERCURY","水银","移动速度提高30%",(s,q)->s.mut(Stat.MOVEMENT_SPEED,.3*q));
        register("RUBY","红玉","火强化、火抗各+30",(s,q)->{s.extra(Stat.FIRE,30*q);s.extra(Stat.FIRE_RESIST,30*q);});
        register("SAPPHIRE","蓝玉","冰强化、冰抗各+30",(s,q)->{s.extra(Stat.ICE,30*q);s.extra(Stat.ICE_RESIST,30*q);});
        register("TOPAZ","黄玉","光强化、光抗各+30",(s,q)->{s.extra(Stat.LIGHT,30*q);s.extra(Stat.LIGHT_RESIST,30*q);});
        register("AMETHYST","紫晶","暗强化、暗抗各+30",(s,q)->{s.extra(Stat.DARK,30*q);s.extra(Stat.DARK_RESIST,30*q);});
        register("SULPHUR","硫磺","伤害提高60%",(s,q)->s.modifier("final",.6*q,true));
        register("ANTIMONY","黑锑","闪避值提高150%",(s,q)->s.mut(Stat.EVASION,1.5*q));
        register("SILVER","白银","技能释放速度提高30%",(s,q)->s.mut(Stat.CAST_SPEED,.3*q));
    }
    private MagicEffects(){}
    public static synchronized void register(String id,String title,String description,BiConsumer<Sink,Double> effect){
        if(id==null||id.isBlank()||title==null||description==null||effect==null)throw new IllegalArgumentException("Invalid magic");
        if(DEFINITIONS.putIfAbsent(id,new Definition(id,title,description,effect))!=null)throw new IllegalArgumentException("Duplicate magic: "+id);
    }
    public static synchronized List<Definition> all(){return List.copyOf(DEFINITIONS.values());}
    public static Definition find(String id){return DEFINITIONS.get(id);}
    public static String text(Affix a){
        if(!a.operation.equals("MAGIC"))return "魔法增幅：每条重复魔法使所有魔法效果提高"+AffixText.integer(a.value*100)+"%";
        var effect=MagicEffects.find(a.family);if(effect==null)return "未知魔法："+a.family+"（保留原记录）";
        var definition=AffixDefinitions.current().affixes.get(AffixResolver.definitionId(a));
        String title=definition==null?effect.title():definition.name();
        return "提供魔法："+title+"（"+effect.description()+"）";
    }
}
