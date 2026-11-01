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
        register(MageSkills.JACK,c->"元素系。南瓜火球沿准星方向直线飞行，最远12格，碰撞即爆炸，半径3格火伤。");
        register(MageSkills.HERDER,c->"召唤系。召唤120秒，最多1只；5级解锁投石，10级三连投石（含装备加级）；每第三轮投掷改为投弹，沿用该轮伤害预算。召唤时保存攻击数据与属性；继承200%生命及双防、150%四抗及闪避值。");
        register(SkillCatalog.BLINK.id,c->"向水平前方至多5格闪现，不能穿墙；没有可站立的安全落点时不消耗资源、不进入冷却。");
        register(SkillCatalog.GHOST.id,c->"按住蓄力"+number(1/c.castSpeed())+"秒达到双倍系数，松开释放；满蓄后最多持有"+number(2/c.castSpeed())+"秒。");
        register(SkillCatalog.TRIPLE.id,c->"每段冲刺2.5格，窗口内再次按原槽续段，最多"+c.preview().slashes()+"段。");
        register(SkillCatalog.WAVE.id,c->"地面波前进"+number(c.parameters().get("range",10))+"格，逐目标命中一次，遇实墙停止。");
        register(SkillCatalog.MASTERY.id,c->"巨剑普攻按住左键蓄力、松键出刀，基础0.8秒由100%线性升至250%，长按保持；梯云纵保留蓄力。短剑追加剑气斩面随机，拔刀剑气宽高翻倍。光剑/太刀拔刀追加固定20%光斩。");
        register(SkillCatalog.OVERDRIVE.id,c->"立即获得攻击增益，不造成直接伤害。");
        register(SkillCatalog.RISING.id,c->"冲撞4格后上挑；动作与结束后0.5秒提供位移防护。");
        register(SkillCatalog.DRAW.id,c->"蓄势"+number(.4/c.castSpeed())+"秒，周身360°、半径"+number(c.parameters().get("range",5)*c.parameters().get("meleeScale",1))+"格，实墙遮挡；光剑或太刀精通追加固定20%光斩。");
        register(SkillCatalog.DRAGON.id,c->"每段冲刺4格，窗口内按原槽续段，最多"+c.preview().slashes()+"段。");
        register(SkillCatalog.DANCE.id,c->number(c.parameters().get("duration",1.25)/c.castSpeed())+"秒内完成全部斩击，再发出穿透终结剑气。");
        register(SkillCatalog.STORM.id,c->number(c.parameters().get("duration",8))+"秒剑阵，包含斩击及终结，全程免伤。");
    }
    static {CareerSkillDescriptions.install();
        register(SummonerSkills.SPIRITS,c->"一次召出火、冰、光、暗精灵各一只，持续120秒，共享等级与伤害预算；火焰近身二连、冰系近攻与远程水弹、变形放电、暗影近身爪击；四只各占25%预算，无额外控制或治疗。完整替换四只，空间不足不扣费。");
        register(SummonerSkills.MASTERY,c->"召唤伤害提升"+(10+2*(c.parameters().rank()-1))+"%，生命与双防提升"+(20+4*(c.parameters().rank()-1))+"%，均进入对应MUT；召唤时固定，重召更新。");
        register(SummonerSkills.LOUISE,c->"持续120秒，悬浮移动；每2秒依次使用冰针、火球、寒冰柱、陨石，近身时火球位改为杖击。冰针减速20%持续1.5秒，不冰冻；陨石半径3格。继承150%生命及双防。");
        register(SummonerSkills.SANDOR,c->"持续120秒，每1.8秒交替挥剑与突刺，每第5次施放贯穿灵剑并举盾3秒；攻击射程3格，无队友光环；举盾时直接伤害再乘0.75。继承350%生命、300%双防与150%护盾，不强制嘲讽Boss。");
        register(SummonerSkills.KING,c->"持续120秒，每2秒按触击、月牙波、七段落雷、月牙波、三段激光行动；2格外触击换月牙波。波与激光16格、各段最多命中3人；落雷半径1.5格，多段分摊同轮预算。无额外控制、光环或吞噬。");
        register(SummonerSkills.FRENZY,c->"20秒内召唤伤害提升"+(20+3*(c.parameters().rank()-1))+"%，进入现有伤害MUT；覆盖期间新召出的召唤兽，不刷新装备快照。");
        register(SummonerSkills.KASIJAS,c->"持续20秒，固定18次拔刀攻击，其中第5、10、15次为突进斩；18.6秒自动千鬼杀，19.5秒退场落剑，共用原终结预算；不随攻速增加段数。继承400%生命与250%双防。");
    }
    private SkillDescriptions(){}
    public static void register(String id,java.util.function.Function<Context,String> description){if(DESCRIPTIONS.putIfAbsent(id,description)!=null)throw new IllegalArgumentException("Duplicate skill description: "+id);}
    public static String describe(SkillDefinition skill,Context context){return DESCRIPTIONS.getOrDefault(skill.id,c->"").apply(context);}
}
