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
        register(MageSkills.STAR,c->"魔道系 · 星弹沿瞄准方向飞行12格。\n命中：最多3个敌人，每个敌人结算一次；遇墙停止。");
        register(MageSkills.FANG,c->"体术系 · 物理伤害，人物不位移。\n范围：向前5格、宽1.5格突刺，范围内敌人各命中一次。");
        register(MageSkills.JACK,c->"元素系 · 南瓜火球沿准星方向直线飞行，最远12格。\n爆炸：碰撞即爆炸，造成半径3格火属性伤害。");
        register(MageSkills.HERDER,c->"召唤：持续120秒，最多1只。\n成长：5级解锁投石，10级解锁三连投石，包含装备加级。\n投弹：每第三轮投掷改为投弹，沿用该轮总伤害。\n继承：200%生命及双防，150%四抗及闪避值。\n属性在召唤时保存，重新召唤才更新。");
        register(SkillCatalog.BLINK.id,c->"位移：向水平前方至多5格闪现，不能穿墙。\n没有可站立的安全落点时，不消耗资源、不进入冷却。");
        register(SkillCatalog.GHOST.id,c->"蓄力：按住"+number(1/c.castSpeed())+"秒达到双倍系数，松开释放。\n满蓄力后，最多继续持有"+number(2/c.castSpeed())+"秒。");
        register(SkillCatalog.TRIPLE.id,c->"位移：每段冲刺2.5格，最多"+c.preview().slashes()+"段。\n续段：在窗口内再次按下原技能槽。");
        register(SkillCatalog.WAVE.id,c->"范围：地面波前进"+number(c.parameters().get("range",10))+"格。\n命中：每个目标一次，遇实墙停止。");
        register(SkillCatalog.MASTERY.id,c->"巨剑：按住左键蓄力、松键出刀，基础0.8秒由100%线性升至250%。\n长按保持蓄力，梯云纵保留蓄力。\n短剑：追加剑气斩面随机，拔刀剑气宽高翻倍。\n光剑／太刀：拔刀追加固定20%光斩。");
        register(SkillCatalog.OVERDRIVE.id,c->"立即获得攻击增益，不造成直接伤害。");
        register(SkillCatalog.RISING.id,c->"动作：冲撞4格后上挑。\n防护：动作期间及结束后0.5秒提供位移防护。");
        register(SkillCatalog.DRAW.id,c->"蓄势："+number(.4/c.castSpeed())+"秒。\n范围：周身360°、半径"+number(c.parameters().get("range",5)*c.parameters().get("meleeScale",1))+"格，实墙遮挡。\n精通：光剑或太刀追加固定20%光斩。");
        register(SkillCatalog.DRAGON.id,c->"位移：每段冲刺4格，最多"+c.preview().slashes()+"段。\n续段：在窗口内再次按下原技能槽。");
        register(SkillCatalog.DANCE.id,c->"斩击："+number(c.parameters().get("duration",1.25)/c.castSpeed())+"秒内完成全部斩击。\n终结：发出穿透剑气。");
        register(SkillCatalog.STORM.id,c->"持续："+number(c.parameters().get("duration",8))+"秒剑阵，包含斩击及终结。\n防护：全程免伤。");
    }
    static {CareerSkillDescriptions.install();
        register(SummonerSkills.SPIRITS,c->"召唤：火、冰、光、暗精灵各1只，持续120秒，共享等级。\n招式：火焰近身二连；冰系近攻与远程水弹；变形放电；暗影近身爪击。\n伤害：四只各占总伤害的25%，无额外控制或治疗。\n重召完整替换四只，空间不足不扣费。");
        register(SummonerSkills.MASTERY,c->"召唤伤害提升"+(10+2*(c.parameters().rank()-1))+"%。\n召唤生命与双防提升"+(20+4*(c.parameters().rank()-1))+"%。\n属性在召唤时固定，重新召唤才更新。");
        register(SummonerSkills.LOUISE,c->"召唤：持续120秒，悬浮移动，继承150%生命及双防。\n招式：每2秒依次使用冰针、火球、寒冰柱、陨石。\n近身时以杖击替代火球；陨石半径3格。\n冰针：减速20%，持续1.5秒，不冰冻。");
        register(SummonerSkills.SANDOR,c->"召唤：持续120秒，继承350%生命、300%双防与150%护盾。\n招式：每1.8秒交替挥剑与突刺，攻击射程3格。\n每第5次施放贯穿灵剑，并举盾3秒。\n举盾时承受的直接伤害再乘0.75；无队友光环，不强制嘲讽Boss。");
        register(SummonerSkills.KING,c->"召唤：持续120秒，每2秒行动一次。\n顺序：触击→月牙波→七段落雷→月牙波→三段激光。\n2格外触击改为月牙波；波与激光射程16格，各段最多命中3人。\n落雷半径1.5格，多段共用同轮总伤害。\n无额外控制、光环或吞噬。");
        register(SummonerSkills.FRENZY,c->"增益：20秒内，召唤伤害提升"+(20+3*(c.parameters().rank()-1))+"%。\n生效范围：期间新召出的召唤兽也会获得增益。\n已有召唤兽的装备属性保持召唤时记录。");
        register(SummonerSkills.KASIJAS,c->"召唤：持续20秒，继承400%生命与250%双防。\n攻击：固定18次拔刀，第5、10、15次改为突进斩。\n终结：18.6秒自动千鬼杀，19.5秒退场落剑，两者共用原终结总伤害。\n攻击段数不随攻速增加。");
    }
    private SkillDescriptions(){}
    public static void register(String id,java.util.function.Function<Context,String> description){if(DESCRIPTIONS.putIfAbsent(id,description)!=null)throw new IllegalArgumentException("Duplicate skill description: "+id);}
    public static String describe(SkillDefinition skill,Context context){return DESCRIPTIONS.getOrDefault(skill.id,c->"").apply(context);}
}
