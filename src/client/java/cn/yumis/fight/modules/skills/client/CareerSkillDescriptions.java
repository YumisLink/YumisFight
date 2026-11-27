package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.dungeons.api.TowerBattleCard;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.SkillController;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import java.util.*;
import static cn.yumis.fight.modules.presentation.api.client.NumberText.number;

final class CareerSkillDescriptions {
    private CareerSkillDescriptions(){}
    private static boolean hasCard(TowerBattleCard card){
        return SkillsClient.current().ready()&&card.held(SkillController.INSTANCE.snapshot());
    }
    static void install(){
        Map<String,String> descriptions=new LinkedHashMap<>();
        descriptions.put(CareerSkills.SWORD,"开启：支付最大生命10%，最多蓄血60秒。\n叠层：有效伤害含持续伤害，最多每0.5秒一层，上限20层。\n终结：再次按键或到期，下斩血剑并爆炸。\n范围：以前方3格为中心，半径7.5格；终结2秒期间无敌。");
        descriptions.put(OracleSkills.WILL,"资格：允许使用光剑，可绑定为执行当前指令的按键。\n执行：自动切换到快捷栏第一个有效的技能匹配武器，没有则保持主手。\n技能指令：仅抽取已学习的转职后技能；首条10秒后出现，成功施放后等待下次发令；空放有效。\n成功奖励：本次伤害提升50%，免除武器不匹配惩罚，并立即叠加赫尔墨斯。\n层数：未学觉醒最多8层；已学觉醒时9层冻结并强制觉醒，脱战可准备。\n超时：战斗中获得业。\n武器指令：每30秒换类型，不连续重复；匹配奖励随等级成长，否则伤害降低50%。");
        descriptions.put(OracleSkills.DOWN,"动作：前方4.8格下劈，宽3.6格、高3.4格。\n武器：钝器使本技能系数乘2.2。\n伤害类型：物理百分比。");
        descriptions.put(OracleSkills.THRUST,"动作：向前突进7格，沿途宽3.2格、高3.4格，每个敌人一次。\n武器：太刀使本技能系数乘2.4。\n伤害类型：物理百分比。");
        descriptions.put(OracleSkills.SWEEP,"动作：短暂蓄力后横劈，前方145°、基础半径5.8格。\n武器：巨剑使本技能系数乘2.2。\n伤害类型：物理百分比。");
        descriptions.put(OracleSkills.SMILE,"选敌：准星优先在10格内选敌，并自动补选。\n动作：寻找敌人身后安全落点，进行两次各50%的斩击。\n无目标或无安全落点时原地双斩。\n武器：短剑使本技能系数乘3.2。");
        descriptions.put(OracleSkills.RIFT,"动作：向前冲刺6格，从起步即造成伤害。\n范围：沿途宽3.6格、高3.4格，每个目标一次。\n武器：光剑使本技能系数乘2.4。\n伤害类型：物理百分比。");
        descriptions.put(OracleSkills.REPLICA,"条件：至少1层赫尔墨斯。\n前置：随机复刻五招中的招式，最多8段，每段各占6.25%，间隔按对应招式动作时长计算。相邻招式不同。\n满9层：追加8次各5%的穿梭连斩，基础每0.1秒一次，共0.8秒。\n连斩只攻击路径扫中的锁定目标，随后9格冲刺重斩35%。\n武器：前置自动选快捷栏第一个对应武器；连斩用起手武器；终结优先起手武器，其次快捷栏第一个有效武器。\n缺武器：无两种指令奖励，该段伤害再乘0.7×0.5。\n有武器：该段伤害提高30%，获得武器指令奖励；9层指令段额外提高50%。\n防护：全程无敌，锁定主动移动和手动换槽。\n施放时保存面板与词条；技能释放速度影响动作时间，结束清层并恢复原槽。");
        String will=descriptions.remove(OracleSkills.WILL);
        String replica=descriptions.remove(OracleSkills.REPLICA);
        Map<String,String> oracleActions=new LinkedHashMap<>();
        for(String id:OracleSkills.ACTIONS)oracleActions.put(id,descriptions.remove(id));
        SkillDescriptions.register(OracleSkills.WILL,c->{
            int rank=Math.max(1,c.parameters().rank());
            var stats=SkillsClient.current().ready()?SkillController.INSTANCE.snapshot():null;
            var sb=new StringBuilder(will)
                .append("\n当前成长：每层赫尔墨斯提升").append(Math.round(OracleRules.layer(rank)*100))
                .append("%，武器匹配提升").append(Math.round(OracleRules.weaponReward(rank)*100)).append("%。")
                .append("\n成功后发令间隔：").append(number(OracleRules.delay(rank,stats)/1000d)).append("秒。");
            if(hasCard(TowerBattleCard.ORACLE_HERMES_SURGE))sb.append("\n职业神话：每次执行成功改为获得3层赫尔墨斯；施放非指令伤害技能消耗1层获得全部指令伤害加成。");
            if(hasCard(TowerBattleCard.ORACLE_WILL_HASTE))sb.append("\n职业神话：发令间隔受冷却缩减影响，所有技能冷却额外缩短2秒。");
            if(hasCard(TowerBattleCard.ORACLE_CYCLE_RESET))sb.append("\n职业神话：五个基础技能释放后随机结束一个转职小技能冷却，执行指令时下次指令固定刷新为该技能。");
            return sb.toString();
        });
        SkillDescriptions.register(OracleSkills.REPLICA,c->replica+(hasCard(TowerBattleCard.ORACLE_REPLICA_UNLEASHED)?"\n职业神话：无需9层赫尔墨斯即可直接施放并触发9层完整效果，冷却时间增加30%。":""));
        oracleActions.forEach((id,text)->SkillDescriptions.register(id,c->{
            var sb=new StringBuilder(text);
            if(hasCard(TowerBattleCard.ORACLE_CYCLE_RESET))sb.append("\n职业神话：施放后使随机一个冷却中的转职小技能冷却结束；执行指令时下次指令刷新为该技能。");
            if(hasCard(TowerBattleCard.ORACLE_SHORTSWORD_WAVE))sb.append("\n职业神话（短剑）：附带发射造成20%伤害的剑气。");
            if(hasCard(TowerBattleCard.ORACLE_LIGHTSABER_ECHO))sb.append("\n职业神话（光剑）：额外斩击一次，造成50%伤害。");
            if(hasCard(TowerBattleCard.ORACLE_BLUNT_QUAKE))sb.append("\n职业神话（钝器）：使敌人眩晕3秒（首领0.25秒）并引发25%小型范围震荡波。");
            if(hasCard(TowerBattleCard.ORACLE_GREATSWORD_CLEAVE))sb.append("\n职业神话（巨剑）：攻击范围扩大30%，伤害提高30%。");
            if(hasCard(TowerBattleCard.ORACLE_KATANA_FLASH))sb.append("\n职业神话（太刀）：释放速度提高50%，伤害提高20%。");
            return sb.toString();
        }));
        descriptions.forEach((id,text)->SkillDescriptions.register(id,c->text));
        SkillDescriptions.register(CareerSkills.KEIGA,c->{
            StringBuilder sb=new StringBuilder("常驻附身：剑类普攻转为暗属性魔法伤害。\n防护：总闪避值提高100%。");
            if(hasCard(TowerBattleCard.SB_GHOST_STEP)){
                sb.append("\n职业神话 · 鬼影步：移速提高").append(number(TowerBattleCard.SB_GHOST_STEP.value("movement")))
                    .append("%、闪避值提高").append(number(TowerBattleCard.SB_GHOST_STEP.value("evasion")))
                    .append("%；成功闪避时获得").append(number(TowerBattleCard.SB_GHOST_STEP.value("invuln")))
                    .append("秒无敌并斩出半径").append(number(TowerBattleCard.SB_GHOST_STEP.value("radius")))
                    .append("格、").append(number(TowerBattleCard.SB_GHOST_STEP.value("damage"))).append("%魔攻暗属性反击（冷却")
                    .append(number(TowerBattleCard.SB_GHOST_STEP.value("cooldown"))).append("秒）。");
            }
            return sb.toString();
        });
        SkillDescriptions.register(CareerSkills.FIELD,c->{
            boolean aura=hasCard(TowerBattleCard.SB_AURA_FIELD);
            return (aura?"职业神话 · 随身鬼域：附着于自身周围随玩家移动，半径5格，持续12秒，始终视为处于自己鬼阵内。"
                :"放置：8格内指向地面，半径5格，持续12秒。")
                +"\n解锁：有效1级红阵、3级绿阵、8级冰阵、15级紫阵。\n红阵增益自己和队友；绿阵降低敌人双防15%。\n同类增益取最强。";
        });
        SkillDescriptions.register(CareerSkills.SOUL_SLASH,c->{
            StringBuilder sb=new StringBuilder(hasCard(TowerBattleCard.SB_INSTANT_SOUL)
                ?"职业神话 · 瞬发噬灵：鬼斩无需蓄力即触发满蓄力与噬灵鬼神冲击。"
                :"触发：满蓄力鬼斩追加鬼神冲击，按本技能等级成长。");
            sb.append("\n增幅：位于自己的鬼阵内时进一步增强。\n不增加鬼斩的消耗和冷却。");
            if(hasCard(TowerBattleCard.SB_HELL_SLASH)){
                sb.append("\n职业神话 · 鬼斩·炼狱：触发时向前发射射程").append(number(TowerBattleCard.SB_HELL_SLASH.value("range")))
                    .append("格的穿透鬼神冲击波，造成").append(TowerBattleCard.SB_HELL_SLASH.count("hits"))
                    .append("段暗属性伤害（每段为满蓄力总伤的").append(number(TowerBattleCard.SB_HELL_SLASH.value("damage"))).append("%）。");
            }
            return sb.toString();
        });
        SkillDescriptions.register(CareerSkills.MOONLIGHT,c->{
            int segs=SoulBenderRules.moonlightSegments(c.parameters());
            StringBuilder sb=new StringBuilder("连斩：续段窗口内可连续按出").append(segs)
                .append("段暗属性月牙斩，每段附带小幅前跨位移。\n终结：第3段").append(segs>=6?"与第6段":"")
                .append("为半径").append(number(c.parameters().get("finisherRange",4.2))).append("格的360°满月回旋斩。");
            if(hasCard(TowerBattleCard.SB_MOON_COMBO)){
                sb.append("\n职业神话 · 盈月连斩：最多6段，每段伤害较前段递增")
                    .append(number(TowerBattleCard.SB_MOON_COMBO.value("ramp"))).append("%。");
            }
            if(hasCard(TowerBattleCard.SB_MOON_WAVE)){
                sb.append("\n职业神话 · 圆月剑气：每段向前发射射程")
                    .append(number(TowerBattleCard.SB_MOON_WAVE.value("range"))).append("格的穿透暗月剑气，造成该段伤害")
                    .append(number(TowerBattleCard.SB_MOON_WAVE.value("damage"))).append("%的暗属性伤害。");
            }
            return sb.toString();
        });
        SkillDescriptions.register(CareerSkills.TOMBSTONE,c->{
            int rounds=(int)Math.max(1,Math.round(c.parameters().get("slashes",10)));
            boolean detach=hasCard(TowerBattleCard.SB_TOMB_DETACH);
            StringBuilder sb=new StringBuilder(detach?"职业神话 · 冥碑脱手：在原地脱手降下":"引导：站定2秒，在半径")
                .append(detach?"半径":"").append(number(c.parameters().get("range",4))).append("格内进行").append(rounds).append("轮落碑。");
            sb.append("\n命中：每个目标每轮最多一次。");
            if(!detach)sb.append("\n中断引导会取消剩余段数。");
            if(hasCard(TowerBattleCard.SB_TOMB_STORM)){
                sb.append("\n职业神话 · 碑林倾泻：落碑提升至").append(rounds).append("轮，单轮伤害降低")
                    .append(number(TowerBattleCard.SB_TOMB_STORM.value("reduction"))).append("%。");
            }
            if(detach){
                sb.append("\n冷却提高").append(number(TowerBattleCard.SB_TOMB_DETACH.value("cooldown")))
                    .append("%，伤害提高").append(number(TowerBattleCard.SB_TOMB_DETACH.value("damage"))).append("%。");
            }
            return sb.toString();
        });
        SkillDescriptions.register(CareerSkills.PHANTOM,c->{
            double dist=c.parameters().get("dashDistance",8);
            double width=c.parameters().get("width",3);
            int slashes=(int)Math.max(1,Math.round(c.parameters().get("slashes",1)));
            StringBuilder sb=new StringBuilder("位移：穿行").append(number(dist)).append("格、路径宽").append(number(width)).append("格，遇墙停止。\n防护：每次穿行阶段0.25秒无敌。\n控制：普通怪停顿0.5秒。");
            if(hasCard(TowerBattleCard.SB_PHANTOM_CHAIN)){
                sb.append("\n职业神话 · 鬼影连闪：可连续穿闪").append(slashes).append("次，单段伤害降低")
                    .append(number(TowerBattleCard.SB_PHANTOM_CHAIN.value("reduction"))).append("%、冷却缩短")
                    .append(number(TowerBattleCard.SB_PHANTOM_CHAIN.value("cooldown"))).append("%。");
            }
            if(hasCard(TowerBattleCard.SB_PHANTOM_RIFT)){
                sb.append("\n职业神话 · 次元断空：命中目标").append(number(TowerBattleCard.SB_PHANTOM_RIFT.value("delay")))
                    .append("秒后于目标处引发半径").append(number(TowerBattleCard.SB_PHANTOM_RIFT.value("radius")))
                    .append("格暗影爆破，造成本次伤害").append(number(TowerBattleCard.SB_PHANTOM_RIFT.value("damage"))).append("%的暗属性伤害。");
            }
            return sb.toString();
        });
        SkillDescriptions.register(CareerSkills.KALLA,c->{
            double dur=c.parameters().get("duration",20);
            if(hasCard(TowerBattleCard.SB_KALLA_ETERNAL)){
                return "职业神话 · 永驻冥炎：附身持续"+number(dur)+"秒，普攻每周期发射一枚10格冥炎弹。\n冥炎弹：命中敌人或地形时在撞击点引发半径2格小范围暗属性爆炸，飞满射程未命中则消散。\n无法再次按键释放终结三连冥炎剑。";
            }
            return "附身：持续"+number(dur)+"秒，普攻每周期发射一枚10格冥炎弹。\n冥炎弹：命中敌人或地形时在撞击点引发半径2格小范围暗属性爆炸，飞满射程未命中则消散。\n终结：再次按键释放三连冥炎剑。";
        });
        SkillDescriptions.register(CareerSkills.BLACHE,c->{
            StringBuilder sb=new StringBuilder("范围：半径8格，释放2秒期间无敌。");
            if(hasCard(TowerBattleCard.SB_BLACHE_VOID)){
                sb.append("\n职业神话 · 虚空献祭：不再清除鬼神之阵，且场上无阵时仍按自身鬼神之阵当前有效等级获得完整吞阵增伤（每鬼+100%、每高一级+5%）。");
            }else{
                sb.append("\n吞阵：吞噬自己的鬼阵，每个鬼神使伤害提升100%，阵法每高一级再提升5%。\n吞阵不重置阵法冷却，无阵也可施放。");
            }
            return sb.toString();
        });
        SkillDescriptions.register(CareerSkills.BRANDS,c->{
            int cap=(int)Math.max(1,Math.round(c.parameters().get("brandCap",5)));
            boolean flow=hasCard(TowerBattleCard.ASURA_BRAND_FLOW);
            boolean capCard=hasCard(TowerBattleCard.ASURA_BRAND_CAP);
            double manaPct=50+(flow?TowerBattleCard.ASURA_BRAND_FLOW.value("mana"):0);
            StringBuilder sb=new StringBuilder("增益：每枚现有刻印使魔力回复速度提升").append(number(manaPct)).append("%");
            if(flow)sb.append("、冷却恢复速度提升").append(number(TowerBattleCard.ASURA_BRAND_FLOW.value("haste"))).append("%");
            if(capCard)sb.append("、伤害提升").append(number(TowerBattleCard.ASURA_BRAND_CAP.value("damage"))).append("%");
            sb.append("。\n生成：最多").append(cap).append("印，每").append(number(c.parameters().get("brandInterval",5))).append("秒自动生成1印。\n地裂、邪光、冰波、火波成功施放各产1印");
            if(flow)sb.append("；普通攻击每").append(TowerBattleCard.ASURA_BRAND_FLOW.count("hits")).append("段额外生成1印");
            sb.append("。\n空放也可产印，多段命中不重复生成。");
            return sb.toString();
        });
        SkillDescriptions.register(CareerSkills.ORB,c->{
            boolean card=hasCard(TowerBattleCard.ASURA_ORB_OVERDRIVE);
            double tick=c.parameters().get("orbTickInterval",SkillResourceRules.ORB_INTERVAL/1000d);
            if(card){
                return "消耗：仅消耗1枚刻印，即可发挥5枚刻印的完整效果（总段数28段）。\n命中：球体前进最多10格，附着首个敌人，每"
                    +number(tick)+"秒命中一次。\n职业神话：伤害提高"+number(TowerBattleCard.ASURA_ORB_OVERDRIVE.value("damage"))+"%，多颗珠的剩余段数顺延，目标死亡后结束。";
            }
            return "消耗：全部刻印，至少需要1印。\n命中：球体前进最多10格，附着首个敌人，攻击3＋5×印数次。\n同一玩家对同一目标，最多每"+number(tick)+"秒命中一次。\n多颗珠的剩余段数顺延，目标死亡后结束。";
        });
        SkillDescriptions.register(CareerSkills.CRESCENT,c->{
            if(hasCard(TowerBattleCard.ASURA_CRESCENT_MAX)){
                double size=4.0*(1.0+TowerBattleCard.ASURA_CRESCENT_MAX.ratio("size"));
                return "职业神话：无需蓄力即以最大形态瞬发前进12格的巨型修罗邪光斩。\n范围：宽高"+number(size)+"格（大小提高"+number(TowerBattleCard.ASURA_CRESCENT_MAX.value("size"))+"%）。\n伤害：伤害提高"+number(TowerBattleCard.ASURA_CRESCENT_MAX.value("damage"))+"%，每个目标最多3段。\n成功释放生成1枚刻印。";
            }
            return "蓄力：按住最多1秒，发射前进12格的X字波。\n范围：未蓄力宽高2格，满蓄力宽高4格。\n伤害：满蓄力总伤害为未蓄力的3倍，每个目标最多3段。\n成功释放生成1枚刻印。";
        });
        SkillDescriptions.register(CareerSkills.ICE,c->{
            int count=(int)Math.max(1,Math.round(c.parameters().get("slashes",3)));
            StringBuilder sb=new StringBuilder("范围：前方长").append(number(c.parameters().get("range",12))).append("格、宽3格，共").append(count).append("道冰刃。\n冻结：普通怪").append(number(c.parameters().get("controlSeconds",1.5))).append("秒，精英减半，Boss免疫冻结。");
            if(hasCard(TowerBattleCard.ASURA_ICE_HOMING)){
                sb.append("\n职业神话：额外召唤1道追踪敌人的冰刃，每").append(number(TowerBattleCard.ASURA_ICE_HOMING.value("turn")))
                    .append("秒调转方向并刷新攻击计算，持续").append(number(TowerBattleCard.ASURA_ICE_HOMING.value("duration"))).append("秒。");
            }
            sb.append("\n成功施放生成1枚刻印。");
            return sb.toString();
        });
        SkillDescriptions.register(CareerSkills.FIRE,c->{
            double radius=Math.max(.5,c.parameters().get("blastRadius",2.5));
            if(hasCard(TowerBattleCard.ASURA_FIRE_TRIPLE)){
                int blasts=(int)Math.max(1,Math.round(c.parameters().get("slashes",3)));
                return "职业神话：沿前方地面连续向前引发"+blasts+"次火焰爆炸。\n范围：每次爆炸半径"+number(radius)+"格，依次推进并独立结算伤害。\n成功施放生成1枚刻印。";
            }
            return "投掷：向准星10格内抛出火球，沿弧线飞到落点。\n抓取：沿途宽5格，抓取可抓敌人。\n爆炸：终点半径"+number(radius)+"格，命中一次，碰墙提前爆炸。\nBoss与完全免击退目标免抓，仍可受爆炸伤害。\n成功施放生成1枚刻印。";
        });
        SkillDescriptions.register(CareerSkills.AGNI,c->{
            boolean dbl=hasCard(TowerBattleCard.ASURA_AGNI_DOUBLE);
            return "消耗：全部刻印"+(dbl?"，每消耗1枚刻印提供2枚刻印的效果（最多支持"+SkillResourceRules.AGNI_MAX_ORBS+"颗旋转火球）":"，消耗几印就显示几球（最多支持"+SkillResourceRules.AGNI_MAX_ORBS+"颗）")
                +"。\n引导：站定3秒，半径5格。\n命中：每球每0.85秒攻击一次，各球错开，最后爆炸一次。\n更多刻印增加旋转命中次数；释放速度同步缩短动作和间隔。";
        });
        SkillDescriptions.register(CareerSkills.EYE,c->"附身：持续20秒。\n普攻：缓慢直行齿轮，每枚造成炸眼系数2%的伤害，每个敌人一次。\n杀意波动：每秒对半径8格无遮挡敌人造成炸眼系数0.5%的伤害。\n波动强化：地裂、冰波、火波伤害提升"+cn.yumis.fight.modules.presentation.api.client.NumberText.number(c.parameters().get("eyeBonusMut",0)*100)+"%。\n失明：周围半径16格无遮挡敌人持续失明。\n终结：再次按键或到期凝聚眼球并爆炸，炸眼1.2秒期间无敌。");
        SkillDescriptions.register(CareerSkills.FRENZY,c->{
            if(hasCard(TowerBattleCard.BERSERKER_BLOOD_AWAKENING)){
                return "开关：双刀模式，每个普攻周期攻击两次，不持续耗血。\n属性：攻速＋15个百分点、移速提升10%（职业神话取消双防降低）。\n防护：完全免疫战斗击退。\n职业神话 · 血气唤醒：替换原低血增伤，生命每低于最大生命1%使伤害总增0.8%（≤30%时上限56%）且移速提高0.5%；每次技能消耗生命时，获得等同于耗血量400%的血气护盾（持续6秒，可累加至最大生命100%）。";
            }
            return "开关：双刀模式，每个普攻周期攻击两次，不持续耗血。\n属性：攻速＋15个百分点、移速提升10%、双防降低15个百分点。\n防护：完全免疫战斗击退。\n低血增伤：生命≤70%时提升10%，≤40%时提升20%，两档不叠加。";
        });
        SkillDescriptions.register(CareerSkills.CRASH,c->{
            double range=c.parameters().get("range",4);
            double hpCost=c.parameters().get("hpCost",0);
            StringBuilder sb=new StringBuilder("动作：向前小跳3格，落地后X形血气向四周飞出。\n范围：半径").append(number(range)).append("格，下砸与冲击分别命中。");
            if(hasCard(TowerBattleCard.BERSERKER_CRASH_BLOOD)){
                sb.append("\n职业神话 · 血气崩山：冷却降低30%，范围提高50%，伤害提高60%，改为消耗最大生命").append(number(hpCost*100)).append("%。");
            }else if(hpCost>0){
                sb.append("\n生命代价：最大生命").append(number(hpCost*100)).append("%。");
            }else{
                sb.append("\n不消耗生命。");
            }
            return sb.toString();
        });
        SkillDescriptions.register(CareerSkills.HAND,c->{
            boolean siphon=hasCard(TowerBattleCard.BERSERKER_HAND_SIPHON);
            boolean claw=hasCard(TowerBattleCard.BERSERKER_HAND_CLAW);
            double rangeScale=Math.max(.5,c.parameters().get("range",4)/4d);
            double widthScale=Math.max(.5,c.parameters().get("width",3)/3d);
            StringBuilder sb=new StringBuilder();
            if(claw){
                sb.append("职业神话 · 血魔巨爪：脱手向前").append(number(10*rangeScale)).append("格唤出巨型血魔之爪，将沿途宽")
                    .append(number(5*widthScale)).append("格敌人聚拢至终点捏爆，伤害提高120%；命中生命低于50%敌人时本次伤害额外提高50%并返还50%冷却。");
            }else{
                sb.append("范围：前方").append(number(c.parameters().get("range",4))).append("格、宽").append(number(c.parameters().get("width",3))).append("格抓取并喷发。");
            }
            sb.append("\n回复：命中回复最大生命10%，每次施放最多回复一次；Boss同样有效，空放不回血。");
            if(siphon){
                sb.append("\n职业神话 · 嗜血抓魂：范围提高50%、冷却降低25%；命中后获得15秒「嗜血之力」（力量+35%、伤害+25%）及最大生命15%的8秒护盾。");
            }
            return sb.toString();
        });
        SkillDescriptions.register(CareerSkills.RAGE,c->{
            double range=c.parameters().get("range",5);
            int hits=(int)Math.max(1,Math.round(c.parameters().get("slashes",5)));
            StringBuilder sb=new StringBuilder("范围：半径").append(number(range)).append("格，共").append(hits).append("段血气。\n生命代价：最大生命4%，不扣护盾。");
            if(hasCard(TowerBattleCard.BERSERKER_RAGE_ENDLESS)){
                sb.append("\n职业神话 · 无尽血怒：范围提高60%，喷发段数+5段，单段伤害降低20%。");
            }
            return sb.toString();
        });
        SkillDescriptions.register(CareerSkills.BLADE,c->{
            boolean hasThrow=hasCard(TowerBattleCard.BERSERKER_BLADE_THROW);
            boolean hasAssault=hasCard(TowerBattleCard.BERSERKER_BLADE_ASSAULT);
            double blastRadius=c.parameters().get("blastRadius",4);
            StringBuilder sb=new StringBuilder();
            if(hasThrow&&hasAssault){
                sb.append("职业神话 · 飞血巨刃＋血刃突袭：无敌向前突进8格穿刺并脱手投出12格巨型血剑，在首个目标或终点必定连续引爆2次半径")
                    .append(number(blastRadius)).append("格血气爆炸（每次为强化爆炸的75%），冷却降低20%。");
            }else if(hasThrow){
                sb.append("职业神话 · 飞血巨刃：脱手向前投掷飞行12格巨型血剑，穿透沿途敌人并在首个目标或终点必定引发半径")
                    .append(number(blastRadius)).append("格强化爆炸（爆炸范围+50%、爆炸伤害+40%）。");
            }else if(hasAssault){
                sb.append("职业神话 · 血刃突袭：附带向前8格高速无敌突进穿刺，命中后连续引发2次半径")
                    .append(number(blastRadius)).append("格血气爆炸（每次造成原爆炸75%伤害），冷却降低20%。");
            }else{
                sb.append("动作：前方6格突刺，命中后半径").append(number(blastRadius)).append("格爆炸。");
            }
            sb.append("\n生命代价：最大生命5%。");
            return sb.toString();
        });
        SkillDescriptions.register(CareerSkills.ERUPTION,c->{
            double range=c.parameters().get("range",7);
            int hits=(int)Math.max(1,Math.round(c.parameters().get("slashes",4)));
            StringBuilder sb=new StringBuilder("动作：在身前持血剑蓄势0.25秒，然后前跳6格下劈，落地后周围喷射血柱。\n范围：半径")
                .append(number(range)).append("格，下砸加").append(hits).append("段喷发。\n生命代价：最大生命8%。");
            if(hasCard(TowerBattleCard.BERSERKER_ERUPTION_QUAKE)){
                sb.append("\n职业神话 · 裂地血崩：范围提高60%，伤害提高50%。");
            }
            if(hasCard(TowerBattleCard.BERSERKER_ERUPTION_PILLARS)){
                sb.append("\n职业神话 · 集束血柱：血柱喷发段数+6段，范围缩小30%。");
            }
            return sb.toString();
        });
    }
}
