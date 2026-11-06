package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.*;
import java.util.*;

final class CareerSkillDescriptions {
    private CareerSkillDescriptions(){}
    static void install(){
        Map<String,String> descriptions=new LinkedHashMap<>();
        descriptions.put(CareerSkills.KEIGA,"常驻附身：剑类普攻转为暗属性魔法伤害。\n防护：总闪避值提高100%。");
        descriptions.put(CareerSkills.FIELD,"放置：8格内指向地面，半径5格，持续12秒。\n解锁：有效1级红阵、3级绿阵、8级冰阵、15级紫阵。\n红阵增益自己和队友；绿阵降低敌人双防15%。\n同类增益取最强。");
        descriptions.put(CareerSkills.SOUL_SLASH,"触发：满蓄力鬼斩追加鬼神冲击，按本技能等级成长。\n增幅：位于自己的鬼阵内时进一步增强。\n不增加鬼斩的消耗和冷却。");
        descriptions.put(CareerSkills.TOMBSTONE,"引导：站定2秒，在半径4格内进行10轮落碑。\n命中：每个目标每轮最多一次。\n中断引导会取消剩余段数。");
        descriptions.put(CareerSkills.PHANTOM,"位移：穿行8格、路径宽3格，遇墙停止。\n防护：穿行阶段0.25秒无敌。\n控制：普通怪停顿0.5秒。");
        descriptions.put(CareerSkills.KALLA,"附身：持续20秒，普攻每周期发射一枚10格冥炎弹。\n冥炎：最多3层，每0.5秒造成暗伤，命中刷新3秒持续时间。\n终结：再次按键释放三连冥炎剑。\n终结后，已附着的冥炎继续按原计时结束。");
        descriptions.put(CareerSkills.BLACHE,"范围：半径8格，释放2秒期间无敌。\n吞阵：吞噬自己的鬼阵，每个鬼神使伤害提升100%，阵法每高一级再提升5%。\n吞阵不重置阵法冷却，无阵也可施放。");
        descriptions.put(CareerSkills.BRANDS,"增益：每枚现有刻印使魔力回复速度提升50%。\n生成：最多5印，每5秒自动生成1印。\n地裂、邪光、冰波、火波成功施放各产1印。\n空放也可产印，多段命中不重复生成。");
        descriptions.put(CareerSkills.ORB,"消耗：全部刻印，至少需要1印。\n命中：球体前进最多10格，附着首个敌人，攻击3＋5×印数次。\n同一玩家对同一目标，最多每0.35秒命中一次。\n多颗珠的剩余段数顺延，目标死亡后结束。");
        descriptions.put(CareerSkills.CRESCENT,"蓄力：按住最多1秒，发射前进12格的X字波。\n范围：未蓄力宽高2格，满蓄力宽高4格。\n伤害：满蓄力总伤害为未蓄力的3倍，每个目标最多3段。\n成功释放生成1枚刻印。");
        descriptions.put(CareerSkills.ICE,"范围：前方长12格、宽3格，共三轮冰刺。\n冻结：普通怪1.5秒，精英减半，Boss免疫冻结。\n成功施放生成1枚刻印。");
        descriptions.put(CareerSkills.FIRE,"投掷：向准星10格内抛出火球，沿弧线飞到落点。\n抓取：沿途宽5格，抓取可抓敌人。\n爆炸：终点半径2.5格，命中一次，碰墙提前爆炸。\nBoss与完全免击退目标免抓，仍可受爆炸伤害。\n成功施放生成1枚刻印。");
        descriptions.put(CareerSkills.AGNI,"消耗：全部刻印，消耗几印就显示几球。\n引导：站定3秒，半径5格。\n命中：每球每0.85秒攻击一次，各球错开，最后爆炸一次。\n更多刻印增加旋转命中次数；释放速度同步缩短动作和间隔。");
        descriptions.put(CareerSkills.FRENZY,"开关：双刀模式，每个普攻周期攻击两次，不持续耗血。\n属性：攻速＋15个百分点、移速提升10%、双防降低15个百分点。\n防护：完全免疫战斗击退。\n低血增伤：生命≤70%时提升10%，≤40%时提升20%，两档不叠加。");
        descriptions.put(CareerSkills.CRASH,"动作：向前小跳3格，落地后X形血气向四周飞出。\n范围：半径3格，下砸与冲击分别命中。\n不消耗生命。");
        descriptions.put(CareerSkills.HAND,"范围：前方3格抓取并喷发。\n回复：命中回复最大生命10%，每次施放最多回复一次。\nBoss同样有效，空放不回血。");
        descriptions.put(CareerSkills.RAGE,"范围：半径4格，共五段血气。\n生命代价：最大生命4%，不扣护盾。");
        descriptions.put(CareerSkills.BLADE,"动作：前方5格突刺，命中后半径3格爆炸。\n生命代价：最大生命5%。");
        descriptions.put(CareerSkills.ERUPTION,"动作：前跳6格，落地后周围随机喷射血柱。\n范围：半径6格，下砸加四段喷发。\n生命代价：最大生命8%。");
        descriptions.put(CareerSkills.SWORD,"开启：支付最大生命10%，最多蓄血60秒。\n叠层：有效伤害含持续伤害，最多每0.5秒一层，上限20层。\n终结：再次按键或到期，下斩血剑并爆炸。\n范围：以前方3格为中心，半径5格；终结2秒期间无敌。");
        descriptions.put(OracleSkills.WILL,"资格：允许使用光剑，可绑定为执行当前指令的按键。\n执行：自动切换到快捷栏第一个有效的技能匹配武器，没有则保持主手。\n技能指令：首条10秒后出现，成功施放后等待下次发令；空放有效。\n成功奖励：本次伤害提升50%，免除武器不匹配惩罚，并立即叠加赫尔墨斯。\n层数：未学觉醒最多8层；已学觉醒时9层冻结并强制觉醒，脱战可准备。\n超时：战斗中获得业。\n武器指令：每30秒换类型，不连续重复；匹配奖励随等级成长，否则伤害降低50%。");
        descriptions.put(OracleSkills.DOWN,"动作：前方4格下劈，宽3格、高3格。\n武器：钝器使本技能系数乘2.2。\n伤害类型：物理百分比。");
        descriptions.put(OracleSkills.THRUST,"动作：向前突进6格，沿途宽2.5格、高3格，每个敌人一次。\n武器：太刀使本技能系数乘2.4。\n伤害类型：物理百分比。");
        descriptions.put(OracleSkills.SWEEP,"动作：短暂蓄力后横劈，前方145°、基础半径5格。\n武器：巨剑使本技能系数乘2.2。\n伤害类型：物理百分比。");
        descriptions.put(OracleSkills.SMILE,"选敌：准星优先在10格内选敌，并自动补选。\n动作：寻找敌人身后安全落点，进行两次各50%的斩击。\n无目标或无安全落点时原地双斩。\n武器：短剑使本技能系数乘3.2。");
        descriptions.put(OracleSkills.RIFT,"动作：向前冲刺5格，从起步即造成伤害。\n范围：沿途宽3格、高3格，每个目标一次。\n武器：光剑使本技能系数乘2.4。\n伤害类型：物理百分比。");
        descriptions.put(OracleSkills.REPLICA,"条件：至少1层赫尔墨斯。\n前置：前8个动作各占6.25%，基础每0.35秒一次，动作随机且不连续重复。\n满9层：追加8次各5%的穿梭连斩，基础每0.1秒一次，共0.8秒。\n连斩只攻击路径扫中的锁定目标，随后8格冲刺重斩35%。\n武器：前置自动选快捷栏第一个对应武器；连斩用起手武器；终结优先起手武器，其次快捷栏第一个有效武器。\n缺武器：无两种指令奖励，该段伤害再乘0.7×0.5。\n有武器：该段伤害提高30%，获得武器指令奖励；9层指令段额外提高50%。\n防护：全程无敌，锁定移动、转向和手动换槽，切为第三人称。\n施放时保存面板与词条；释放速度影响动作时间，结束清层并恢复原槽与视角。");
        String will=descriptions.remove(OracleSkills.WILL);
        SkillDescriptions.register(OracleSkills.WILL,c->{int rank=Math.max(1,c.parameters().rank());return will+"\n当前成长：每层赫尔墨斯提升"+Math.round(OracleRules.layer(rank)*100)+"%，武器匹配提升"+Math.round(OracleRules.weaponReward(rank)*100)+"%。\n成功后发令间隔："+cn.yumis.fight.modules.presentation.api.client.NumberText.number(OracleRules.delay(rank)/1000d)+"秒。";});
        descriptions.forEach((id,text)->SkillDescriptions.register(id,c->text));
        SkillDescriptions.register(CareerSkills.EYE,c->"附身：持续20秒。\n普攻：缓慢直行齿轮，每枚造成炸眼系数2%的伤害，每个敌人一次。\n杀意波动：每秒对半径8格无遮挡敌人造成炸眼系数0.5%的伤害。\n波动强化：地裂、冰波、火波伤害提升"+cn.yumis.fight.modules.presentation.api.client.NumberText.number(c.parameters().get("eyeBonusMut",0)*100)+"%。\n失明：周围半径16格无遮挡敌人持续失明。\n终结：再次按键或到期凝聚眼球并爆炸，炸眼1.2秒期间无敌。");
    }
}
