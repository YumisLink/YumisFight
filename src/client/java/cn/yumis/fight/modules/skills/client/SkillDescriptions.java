package cn.yumis.fight.modules.skills.client;
import cn.yumis.fight.modules.skills.api.SkillParameters;
import cn.yumis.fight.modules.skills.api.SkillDefinition;
import cn.yumis.fight.modules.skills.api.SkillCatalog;
import cn.yumis.fight.modules.skills.api.SkillPreview;
import cn.yumis.fight.modules.skills.api.*;
import java.util.*;
import static cn.yumis.fight.modules.presentation.api.client.NumberText.number;
/** Skill descriptions are registered alongside their behavior, independently of the menu shell. */
public final class SkillDescriptions {
    public record Context(double castSpeed,SkillParameters.Snapshot parameters,SkillPreview.Preview preview){}
    private static final Map<String,java.util.function.Function<Context,String>> DESCRIPTIONS=new LinkedHashMap<>();
    static {
        register(MageSkills.STAR,c->"魔道系。星弹沿瞄准方向飞12格，最多命中3个敌人，各结算一次；遇墙停止。");
        register(MageSkills.FANG,c->"体术系。人物不位移，向前5格、宽1.5格突刺，范围内敌人各命中一次；物理伤害。");
        register(MageSkills.JACK,c->"元素系。南瓜火球抛物线最远12格，碰撞即爆炸，半径3格火伤。");
        register(MageSkills.HERDER,c->"召唤系。召唤120秒，最多1只；5级解锁投石，10级三连投石（含装备加级）。召唤时保存攻击数据与属性；继承200%生命及双防、150%四抗及闪避值。");
        register(SkillCatalog.BLINK.id,c->"向水平前方至多5格闪现，不能穿墙；没有可站立的安全落点时不消耗资源、不进入冷却。");
        register(SkillCatalog.GHOST.id,c->"按住蓄力"+number(1/c.castSpeed())+"秒达到双倍系数，松开释放；满蓄后最多持有"+number(2/c.castSpeed())+"秒。");
        register(SkillCatalog.TRIPLE.id,c->"每段冲刺2.5格，窗口内再次按原槽续段，最多"+c.preview().slashes()+"段。");
        register(SkillCatalog.WAVE.id,c->"地面波前进"+number(c.parameters().get("range",10))+"格，逐目标命中一次，遇实墙停止。");
        register(SkillCatalog.OVERDRIVE.id,c->"立即获得攻击增益，不造成直接伤害。");
        register(SkillCatalog.RISING.id,c->"冲撞4格后上挑；动作与结束后0.5秒提供位移防护。");
        register(SkillCatalog.DRAW.id,c->"蓄势"+number(.4/c.castSpeed())+"秒，前方180°、半径"+number(c.parameters().get("range",5)*c.parameters().get("meleeScale",1))+"格，实墙遮挡。");
        register(SkillCatalog.DRAGON.id,c->"每段冲刺4格，窗口内按原槽续段，最多"+c.preview().slashes()+"段。");
        register(SkillCatalog.DANCE.id,c->number(c.parameters().get("duration",1.25)/c.castSpeed())+"秒内完成全部斩击，再发出穿透终结剑气。");
        register(SkillCatalog.STORM.id,c->number(c.parameters().get("duration",8))+"秒剑阵，包含斩击及终结，全程免伤。");
    }
    static {CareerSkillDescriptions.install();}
    private SkillDescriptions(){}
    public static void register(String id,java.util.function.Function<Context,String> description){if(DESCRIPTIONS.putIfAbsent(id,description)!=null)throw new IllegalArgumentException("Duplicate skill description: "+id);}
    public static String describe(SkillDefinition skill,Context context){return DESCRIPTIONS.getOrDefault(skill.id,c->"").apply(context);}
}
