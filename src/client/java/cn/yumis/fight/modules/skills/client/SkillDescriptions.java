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
        register(MageSkills.STAR,c->ElementalistDescriptions.inheritedCastLine(c)+"魔道系 · 星弹沿瞄准方向飞行12格。\n命中：最多3个敌人，每个敌人结算一次；遇墙停止。"+WitchDescriptions.magicStarNote(c));
        register(MageSkills.FANG,c->ElementalistDescriptions.inheritedCastLine(c)+"体术系 · 物理伤害，人物不位移。\n范围：向前5格、宽1.5格突刺，范围内敌人各命中一次。"+BattleMageDescriptions.dragonFangNote(c));
        register(MageSkills.JACK,c->{
            if(cn.yumis.fight.modules.skills.api.client.SkillsClient.current().progress().hasJob(cn.yumis.fight.modules.character.api.JobCatalog.ELEMENTALIST)){
                return "元素系（固有属性：火）。"+ElementalistDescriptions.chargeLine(c)+"取消不退起手费。\n南瓜火球沿准星方向直线飞行，最远12格；碰撞造成半径3格火属性爆炸并点亮火灯。"+ElementalistDescriptions.jackBombNote(c);
            }
            return "元素系 · 基础咏唱0.5秒，期间移速为正常的20%；取消不退起手费。\n南瓜火球沿准星方向直线飞行，最远12格。\n爆炸：碰撞即爆炸，造成半径3格火属性伤害。";
        });
        register(MageSkills.HERDER,c->{
            var snap=cn.yumis.fight.modules.skills.api.client.SkillsClient.current().ready()?cn.yumis.fight.modules.skills.api.client.SkillController.INSTANCE.snapshot():null;
            boolean squad=cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_HERDER_SQUAD.held(snap);
            boolean cannon=cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_HERDER_CANNON.held(snap);
            int squadCount=cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_HERDER_SQUAD.count("count");
            String note=(squad?"\n职业神话：同时召唤"+squadCount+"只赫尔德，全部仅使用投弹攻击，爆炸半径+"+number(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_HERDER_SQUAD.value("radius"))+"%、伤害+"+number(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_HERDER_SQUAD.value("damage"))+"%。":"")
                +(cannon?"\n职业神话：进化为巨型烈焰重炮形态，血防继承+"+number(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_HERDER_CANNON.value("tank"))+"%，每"+number(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_HERDER_CANNON.value("interval"))+"秒轰出半径"+number(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_HERDER_CANNON.value("radius"))+"格重炮（伤害+"+number(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_HERDER_CANNON.value("damage"))+"%、火异常+"+number(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_HERDER_CANNON.value("buildup"))+"%）。":"");
            return ElementalistDescriptions.inheritedCastLine(c)+"召唤：持续120秒，最多"+(squad?squadCount:"1")+"只。\n成长：5级解锁投石，10级解锁三连投石，包含装备加级。\n投弹：每第三轮投掷改为投弹，沿用该轮总伤害。\n继承：200%生命及双防，150%四抗及闪避值。\n属性在召唤时保存，重新召唤才更新。"+note;
        });
        register(SkillCatalog.BLINK.id,c->"位移：向水平前方至多5格闪现，不能穿墙。\n没有可站立的安全落点时，不消耗资源、不进入冷却。");
        register(SkillCatalog.GHOST.id,c->{
            boolean instant=held(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SB_INSTANT_SOUL);
            boolean hell=held(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SB_HELL_SLASH);
            String base=instant?"职业神话 · 瞬发噬灵：无需蓄力即可直接打出满蓄力双倍系数与噬灵鬼斩，鬼斩与噬灵伤害提高"+number(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SB_INSTANT_SOUL.value("damage"))+"%。"
                :"蓄力：按住"+number(1/c.castSpeed())+"秒达到双倍系数，松开释放。\n满蓄力后，最多继续持有"+number(2/c.castSpeed())+"秒。";
            return hell?base+"\n职业神话 · 鬼斩·炼狱：触发噬灵鬼斩时向前发射"+number(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SB_HELL_SLASH.value("range"))+"格"+cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SB_HELL_SLASH.count("hits")+"段穿透鬼神冲击波（每段为满蓄力总伤的"+number(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SB_HELL_SLASH.value("damage"))+"%）。":base;
        });
        register(SkillCatalog.TRIPLE.id,c->{
            boolean inf=held(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_TRIPLE_INFINITE);
            return "位移：每段冲刺"+number(c.parameters().get("dashDistance",3))+"格，"+(inf?"无限释放次数。":"最多"+c.preview().slashes()+"段。")+"\n续段：在窗口内再次按下原技能槽。"+(inf?"\n职业神话：窗口期内可无限续段释放，单段伤害提高"+number(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_TRIPLE_INFINITE.value("damage"))+"%。":"");
        });
        register(SkillCatalog.WAVE.id,c->{
            boolean ring=held(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.ASURA_WAVE_RING);
            return "范围：地面波前进"+number(c.parameters().get("range",12))+"格。\n命中：每道波对每个目标最多一次，遇实墙停止。"
                +(ring?"\n职业神话：同时向"+cn.yumis.fight.modules.dungeons.api.TowerBattleCard.ASURA_WAVE_RING.count("angles")+"个均匀角度方向释放，伤害提高"+number(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.ASURA_WAVE_RING.value("damage"))+"%。":"");
        });
        register(SkillCatalog.MASTERY.id,c->{
            boolean overload=held(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_MASTERY_OVERLOAD);
            double chargeSec=overload?0.8/(1.0+cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_MASTERY_OVERLOAD.ratio("great")):0.8;
            return "巨剑：按住左键蓄力、松键出刀，基础"+number(chargeSec)+"秒由100%线性升至250%。\n长按保持蓄力，梯云纵保留蓄力。\n短剑：追加剑气"+(overload?"扩大至幻影剑舞大小，伤害提高"+number(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_MASTERY_OVERLOAD.value("short"))+"%":"斩面随机，拔刀剑气宽高翻倍")+"。\n光剑／太刀：拔刀追加固定20%光斩。";
        });
        register(SkillCatalog.OVERDRIVE.id,c->"立即获得攻击增益，不造成直接伤害。"+(held(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_OVERDRIVE_LIGHT)?"\n职业神话：持续期间武器附带"+number(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_OVERDRIVE_LIGHT.value("light"))+"%光属性攻击。":""));
        register(SkillCatalog.RISING.id,c->{
            if(held(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_RISING_DRAGON_SLAY)){
                return "动作：破军斩龙击——向前突进7格连斩3段、上挑浮空后跃起猛砸，在前方5.5格引发斩龙冲击波，总伤害提高"+number(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_RISING_DRAGON_SLAY.value("bonus"))+"%。\n防护：动作期间及结束后0.5秒提供位移防护。";
            }
            return "动作：冲撞"+number(c.parameters().get("dashDistance",4.5))+"格后上挑。\n防护：动作期间及结束后0.5秒提供位移防护。";
        });
        register(SkillCatalog.DRAW.id,c->{
            boolean barrage=held(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_DRAW_BARRAGE);
            boolean arc=held(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_DRAW_ARC_WAVE);
            if(barrage){
                int count=WeaponSkills.drawBarrageCount(cn.yumis.fight.modules.skills.api.client.SkillController.INSTANCE.snapshot());
                return "蓄势："+number(.4/c.castSpeed())+"秒。\n职业神话：不再造成周身弧形伤害，改为朝准心连发"+count+"道幻影剑舞剑气（光剑／太刀"+cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_DRAW_BARRAGE.count("fast")+"道），每道造成"+number(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_DRAW_BARRAGE.value("damage"))+"%拔刀斩伤害。"
                    +(arc?"\n同时受极意拔刀加成：剑气范围与宽度提高"+number(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_DRAW_ARC_WAVE.value("range"))+"%。":"");
            }
            return "蓄势："+number(.4/c.castSpeed())+"秒。\n范围：周身360°、半径"+number(c.parameters().get("range",6)*c.parameters().get("meleeScale",1))+"格，实墙遮挡。\n精通：光剑或太刀追加固定20%二段光斩。"
                +(arc?"\n职业神话：范围提高"+number(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_DRAW_ARC_WAVE.value("range"))+"%，并释放扩散至"+number(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_DRAW_ARC_WAVE.value("distance"))+"格的弧形剑气，造成"+number(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_DRAW_ARC_WAVE.value("damage"))+"%拔刀斩伤害。":"");
        });
        register(SkillCatalog.DRAGON.id,c->{
            boolean single=held(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_DRAGON_SINGLE);
            boolean whirl=held(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_DRAGON_WHIRLWIND);
            StringBuilder sb=new StringBuilder("位移：每段冲刺"+number(c.parameters().get("dashDistance",4.8))+"格，"+(single?"单段爆发。":"最多"+c.preview().slashes()+"段。\n续段：在窗口内再次按下原技能槽。"));
            if(single)sb.append("\n职业神话：缩减至").append(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_DRAGON_SINGLE.count("slashes")).append("段，伤害提高").append(number(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_DRAGON_SINGLE.value("damage"))).append("%，冷却缩减至原本的").append(number(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_DRAGON_SINGLE.value("cooldown"))).append("%。");
            if(whirl)sb.append("\n职业神话：").append(single?"施放时":"次数+"+cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_DRAGON_WHIRLWIND.count("extra")+"，且每次冲刺").append("发射追踪敌人的旋风，造成").append(number(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_DRAGON_WHIRLWIND.value("damage"))).append("%猛龙断空斩伤害。");
            return sb.toString();
        });
        register(SkillCatalog.DANCE.id,c->"斩击："+number(c.parameters().get("duration",1.25)/c.castSpeed())+"秒内完成全部斩击。\n终结：发出穿透剑气。"+(held(cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_DANCE_OMNI)?"\n职业神话：斩击次数提高"+cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SM_DANCE_OMNI.count("extra")+"段，并随玩家面向实时改变斩击与剑气方向。":""));
        register(SkillCatalog.STORM.id,c->"持续："+number(c.parameters().get("duration",8))+"秒剑阵，包含斩击及终结。\n防护：全程免伤。");
        register(SkillCatalog.METEOR_RISE.id,c->"流星落式 · 升：斜上跃起并挥出3段上挑剑气，击飞前方路径上的敌人。\n卡牌授予：由职业神话卡牌【流星三式】提供，等级随角色等级自动成长。");
        register(SkillCatalog.METEOR_LEAP.id,c->"流星落式 · 跃：向准星方向腾跃落斩，落地处爆发环形剑压，震荡半径4.5格内敌人。\n卡牌授予：由职业神话卡牌【流星三式】提供，等级随角色等级自动成长。");
        register(SkillCatalog.METEOR_PIERCE.id,c->"流星落式 · 刺：沿前方极速突刺7.5格，路径连刺4段并在终点爆发终结刺击；动作期间提供位移防护。\n卡牌授予：由职业神话卡牌【流星三式】提供，等级随角色等级自动成长。");
    }
    private static boolean held(cn.yumis.fight.modules.dungeons.api.TowerBattleCard card){
        return cn.yumis.fight.modules.skills.api.client.SkillsClient.current().ready()
            &&card.held(cn.yumis.fight.modules.skills.api.client.SkillController.INSTANCE.snapshot());
    }
    static {CareerSkillDescriptions.install();SunMoonDescriptions.install();
        register(SummonerSkills.SPIRITS,c->{
            var snap=cn.yumis.fight.modules.skills.api.client.SkillsClient.current().ready()?cn.yumis.fight.modules.skills.api.client.SkillController.INSTANCE.snapshot():null;
            var sac=cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_SPIRITS_SACRIFICE;
            var asc=cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_SPIRITS_ASCEND;
            String note=(sac.held(snap)?"\n职业神话：在场重召立即引爆存活四灵并重召满血新四灵（半径"+number(sac.value("radius"))+"格、总面板"+number(sac.value("damage"))+"%、"+number(sac.value("buildup"))+"%元素异常），阵亡触发"+number(sac.value("death"))+"%自爆。":"")
                +(asc.held(snap)?"\n职业神话：四灵血防继承+"+number(asc.value("tank"))+"%，全员进化为"+number(asc.value("range"))+"格远程范围攻击（半径"+number(asc.value("radius"))+"格），伤害+"+number(asc.value("damage"))+"%。":"");
            return "召唤：火、冰、光、暗精灵各1只，持续120秒，共享等级。\n招式：火焰近身二连；冰系近攻与远程水弹；变形放电；暗影近身爪击。\n伤害：四只各占总伤害的25%，无额外控制或治疗。\n重召完整替换四只，空间不足不扣费。"+note;
        });
        register(SummonerSkills.MASTERY,c->{
            var snap=cn.yumis.fight.modules.skills.api.client.SkillsClient.current().ready()?cn.yumis.fight.modules.skills.api.client.SkillController.INSTANCE.snapshot():null;
            var mark=cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_MASTERY_MARK;
            var pact=cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_MASTERY_PACT;
            String note=(mark.held(snap)?"\n职业神话：普攻/星弹/龙牙/杰克命中标记目标"+number(mark.value("duration"))+"秒，召唤兽对其伤害总增"+number(mark.value("damage"))+"%、暴击+"+number(mark.value("crit"))+"%，每"+number(mark.value("teleport"))+"秒瞬移超"+number(mark.value("dist"))+"格近战召唤兽集火。":"")
                +(pact.held(snap)?"\n职业神话：普通召唤兽永久存在、攻速+"+number(pact.value("speed"))+"%、攻击仅回复自身"+number(pact.value("heal"))+"%最大生命（不再回复护盾）；玩家受击"+number(pact.value("share"))+"%伤害由存活召唤兽均摊。":"");
            return "召唤伤害提升"+(10+2*(c.parameters().rank()-1))+"%。\n召唤生命与双防提升"+(20+4*(c.parameters().rank()-1))+"%。\n属性在召唤时固定，重新召唤才更新。"+note;
        });
        register(SummonerSkills.LOUISE,c->{
            var snap=cn.yumis.fight.modules.skills.api.client.SkillsClient.current().ready()?cn.yumis.fight.modules.skills.api.client.SkillController.INSTANCE.snapshot():null;
            var inf=cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_LOUISE_INFERNO;
            var fr=cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_LOUISE_FROST;
            String note=(inf.held(snap)?"\n职业神话：保留全部招式并强化火系——火球"+inf.count("fireballs")+"连发，陨石半径+"+number(inf.value("radius"))+"%、伤害+"+number(inf.value("damage"))+"%并留"+number(inf.value("duration"))+"秒烈焰火海（每跳"+number(inf.value("magma"))+"%）。":"")
                +(fr.held(snap)?"\n职业神话：保留全部招式并强化冰系——冰针"+fr.count("needles")+"连扇形（冻结+"+number(fr.value("buildup"))+"%），寒冰柱"+fr.count("pillars")+"连爆（半径+"+number(fr.value("radius"))+"%），对冻结/冰缓目标追加"+number(fr.value("shatter"))+"%碎冰伤。":"");
            return "召唤：持续120秒，悬浮移动，继承150%生命及双防。\n招式：每2秒依次使用冰针、火球、寒冰柱、陨石。\n近身时以杖击替代火球；陨石半径3格。\n冰针：减速20%，持续1.5秒，不冰冻。"+note;
        });
        register(SummonerSkills.SANDOR,c->{
            var snap=cn.yumis.fight.modules.skills.api.client.SkillsClient.current().ready()?cn.yumis.fight.modules.skills.api.client.SkillController.INSTANCE.snapshot():null;
            var pal=cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_SANDOR_PALADIN;
            String note=pal.held(snap)?"\n职业神话：常驻"+number(pal.value("radius"))+"格守护光环（双防+"+number(pal.value("defense"))+"%、受击-"+number(pal.value("reduction"))+"%、每秒回"+number(pal.value("regen"))+"%血盾），灵剑释放时嘲讽"+number(pal.value("taunt"))+"格非首领敌人并使其双防降低"+number(pal.value("shred"))+"%（"+number(pal.value("duration"))+"秒）。":"";
            return "召唤：持续120秒，继承350%生命、300%双防与150%护盾。\n招式：每1.8秒交替挥剑与突刺，攻击射程3格。\n每第5次施放贯穿灵剑，并举盾3秒。\n举盾时承受的直接伤害再乘0.75；无队友光环，不强制嘲讽Boss。"+note;
        });
        register(SummonerSkills.KING,c->{
            var snap=cn.yumis.fight.modules.skills.api.client.SkillsClient.current().ready()?cn.yumis.fight.modules.skills.api.client.SkillController.INSTANCE.snapshot():null;
            var sov=cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_KING_SOVEREIGN;
            var las=cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_KING_LASER;
            String note=(sov.held(snap)?"\n职业神话：每存在1只下位精灵，伊伽贝拉伤害总增"+number(sov.value("damage"))+"%、行动速度+"+number(sov.value("speed"))+"%。":"")
                +(las.held(snap)?"\n职业神话：仅释放宽"+number(las.value("width"))+"格无限穿透"+las.count("hits")+"段强化激光（伤害+"+number(las.value("damage"))+"%、激光CD-"+number(las.value("cdr"))+"%）；在场再按进入"+number(las.value("commandcd"))+"秒CD，瞬移至召唤师身前刷新激光CD并朝准星发射激光。":"");
            return "召唤：持续120秒，每2秒行动一次。\n顺序：触击→月牙波→七段落雷→月牙波→三段激光。\n2格外触击改为月牙波；波与激光射程16格，各段最多命中3人。\n落雷半径1.5格，多段共用同轮总伤害。\n无额外控制、光环或吞噬。"+note;
        });
        register(SummonerSkills.FRENZY,c->{
            var snap=cn.yumis.fight.modules.skills.api.client.SkillsClient.current().ready()?cn.yumis.fight.modules.skills.api.client.SkillController.INSTANCE.snapshot():null;
            var vol=cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_FRENZY_VOLLEY;
            String note=vol.held(snap)?"\n职业神话：施放瞬间强制场上全部存活召唤兽立即同步释放一次各自的招牌大招；冷却时间缩短"+number(vol.value("cdr"))+"%。":"";
            return "增益：20秒内，召唤伤害提升"+(20+3*(c.parameters().rank()-1))+"%。\n生效范围：期间新召出的召唤兽也会获得增益。\n已有召唤兽的装备属性保持召唤时记录。"+note;
        });
        register(SummonerSkills.KASIJAS,c->"召唤：持续20秒，继承400%生命与250%双防。\n攻击：固定18次拔刀，第5、10、15次改为突进斩。\n终结：18.6秒自动千鬼杀，19.5秒退场落剑，两者共用原终结总伤害。\n攻击段数不随攻速增加。");
    }
    static {DemonHunterDescriptions.install();ElementalistDescriptions.install();BattleMageDescriptions.install();WitchDescriptions.install();}
    private SkillDescriptions(){}
    public static void register(String id,java.util.function.Function<Context,String> description){if(DESCRIPTIONS.putIfAbsent(id,description)!=null)throw new IllegalArgumentException("Duplicate skill description: "+id);}
    public static String describe(SkillDefinition skill,Context context){return DESCRIPTIONS.getOrDefault(skill.id,c->"").apply(context);}
}
