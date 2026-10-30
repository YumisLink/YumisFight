package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.*;
import java.util.*;

final class CareerSkillDescriptions {
    private CareerSkillDescriptions(){}
    static void install(){
        Map<String,String> descriptions=new LinkedHashMap<>();
        descriptions.put(CareerSkills.KEIGA,"常驻附身：剑类普攻转为暗属性魔法伤害；怪物直接伤害在职业防护后再乘0.9。");
        descriptions.put(CareerSkills.FIELD,"8格内指向地面，半径5、持续12秒。有效1级红阵、3级绿阵、8级冰阵、15级紫阵；红阵增益自己和队友，绿阵降低敌人双防15%。同类增益取最强。");
        descriptions.put(CareerSkills.SOUL_SLASH,"满蓄力鬼斩追加鬼神冲击，按本技能等级成长；位于自己的鬼阵内时进一步增强。不增加鬼斩消耗和冷却。");
        descriptions.put(CareerSkills.TOMBSTONE,"站定引导2秒，半径4内10轮落碑，每目标每轮最多命中一次；中断取消剩余段数。");
        descriptions.put(CareerSkills.PHANTOM,"穿行8格、路径宽3，遇墙停止；穿行阶段0.25秒无敌，普通怪停顿0.5秒。");
        descriptions.put(CareerSkills.KALLA,"持续20秒，普攻每周期发射一枚10格冥炎弹。冥炎最多3层，每0.5秒造成暗伤并刷新3秒持续；再次按键三连冥炎剑收尾，已附着冥炎继续计时。");
        descriptions.put(CareerSkills.BLACHE,"半径8，释放2秒期间无敌。吞自己的鬼阵：每个鬼神使伤害提升100%，阵法每高一级再提升5%；不重置阵法冷却，无阵也可施放。");
        descriptions.put(CareerSkills.BRANDS,"每枚现有刻印使魔力回复速度提升50%。最多5印，每5秒自动产1印；成功施放地裂、邪光、冰波、火波各产1印，空放也可产印，多段不重复。");
        descriptions.put(CareerSkills.ORB,"消耗全部刻印，至少1印。球体前进最多10格，附着首个敌人，攻击3+5×印数次。同一玩家对同一目标最多每0.35秒一击，多颗珠剩余段数顺延；目标死亡结束。");
        descriptions.put(CareerSkills.CRESCENT,"按住蓄力最多1秒，发射一束前进12格的X字波；不蓄力宽高2格，满蓄力宽高4格、总伤害为未蓄力的3倍，每目标最多3段。成功释放产1印。");
        descriptions.put(CareerSkills.ICE,"前方长12、宽3的三轮冰刺；普通怪冻结1.5秒，精英减半，Boss免冻结。成功施放产1印。");
        descriptions.put(CareerSkills.FIRE,"向准星10格内抛出一颗火球，沿弧线飞到落点。沿途宽5格抓取可抓敌人，终点半径2.5爆炸一次；碰墙提前爆炸。Boss与完全免击退目标免抓，仍可受爆炸伤害。成功施放产1印。");
        descriptions.put(CareerSkills.AGNI,"消耗全部刻印，消耗几印就显示几球。站定引导3秒，半径5；每球每0.85秒攻击一次，各球错开命中，最后爆炸一次。更多印增加旋转命中次数；释放速度同步压缩动作和间隔。");
        descriptions.put(CareerSkills.FRENZY,"开关双刀，每普攻周期两击。攻速+15个百分点、移速提升10%、双防降低15个百分点，完全免疫战斗击退。生命≤70%时伤害提升10%，≤40%时提升20%，不叠加；不持续耗血。");
        descriptions.put(CareerSkills.CRASH,"向前小跳3格，落地后X形血气向四周飞出；半径3，下砸与冲击分别命中。不消耗生命。");
        descriptions.put(CareerSkills.HAND,"前方3格抓取并喷发；命中回复最大生命10%，每次施放最多回复一次，Boss同样有效，空放不回血。");
        descriptions.put(CareerSkills.RAGE,"半径4、五段血气。施放支付最大生命4%，不扣护盾。");
        descriptions.put(CareerSkills.BLADE,"前方5格突刺，命中后半径3爆炸。施放支付最大生命5%。");
        descriptions.put(CareerSkills.ERUPTION,"前跳6格，落地后周围随机喷射血柱；半径6，下砸加四段喷发。施放支付最大生命8%。");
        descriptions.put(CareerSkills.SWORD,"支付最大生命10%，开启最多60秒蓄血。造成有效伤害即可获得层数，包括持续伤害，同一玩家最多每0.5秒一层，上限20。再次按键或到期下斩血剑并爆炸，2秒终结期间无敌；前方3格为中心、半径5。");
        descriptions.put(OracleSkills.WILL,"允许光剑，可绑定为执行指令键；执行前自动切换到快捷栏第一个有效的技能匹配武器，没有则保持主手。首条10秒后出现；成功施放（空放有效）使本次伤害提升50%、免除武器指令不匹配惩罚并立即叠赫尔墨斯，之后等待被动间隔。超时战斗中获得业。未学觉醒最多8层，已学9层冻结并强制觉醒；脱战可准备。武器指令每30秒换类型且不连续重复，匹配奖励随等级成长，否则伤害降低50%。");
        descriptions.put(OracleSkills.DOWN,"前方4格下劈，宽3、高3；钝器使本技能系数乘2.2。物理百分比伤害。");
        descriptions.put(OracleSkills.THRUST,"向前突进6格，沿途宽2.5、高3，每个敌人一次；太刀使系数乘2.4。物理百分比伤害。");
        descriptions.put(OracleSkills.SWEEP,"短暂蓄力后横批，前方145°、基础半径5格；巨剑使系数乘2.2。物理百分比伤害。");
        descriptions.put(OracleSkills.SMILE,"准星优先在10格内选敌，自动补选，寻找敌人身后安全落点后两次各50%斩击；无目标或无安全位原地双斩。短剑使系数乘3.2。");
        descriptions.put(OracleSkills.RIFT,"从起步即造成伤害，向前冲刺5格，沿途宽3、高3，每目标一次；光剑使系数乘2.4。物理百分比伤害。");
        descriptions.put(OracleSkills.REPLICA,"至少1层赫尔墨斯。前8个动作各占6.25%、每基础0.35秒一次；满9层追加8次各5%的穿梭连斩，每基础0.1秒一次、持续0.8秒，只攻击被路径扫中的锁定目标，然后8格冲刺重斩35%。演出时间受释放速度影响。前置动作随机且不连续重复，自动选择快捷栏第一个对应武器；连斩使用起手武器，终结优先使用起手武器，找不到则选快捷栏第一个有效武器。缺武器段无两种指令奖励，再乘0.7×0.5；有武器段提高30%并获得武器指令奖励，9层指令段额外提高50%。面板与词条起手保存。全程无敌、锁移动转向和手动换槽、第三人称；结束清层并恢复原槽与视角。");
        String will=descriptions.remove(OracleSkills.WILL);
        SkillDescriptions.register(OracleSkills.WILL,c->{int rank=Math.max(1,c.parameters().rank());return will+" 当前每层赫尔墨斯提升"+Math.round(OracleRules.layer(rank)*100)+"%，武器匹配提升"+Math.round(OracleRules.weaponReward(rank)*100)+"%，成功后发令间隔"+cn.yumis.fight.modules.presentation.api.client.NumberText.number(OracleRules.delay(rank)/1000d)+"秒。";});
        descriptions.forEach((id,text)->SkillDescriptions.register(id,c->text));
        SkillDescriptions.register(CareerSkills.EYE,c->"持续20秒：普攻改为缓慢直行齿轮，每枚造成炸眼系数2%伤害，每敌一次；杀意波动每秒对半径8格无遮挡敌人造成炸眼系数0.5%伤害。地裂、冰波、火波伤害提升"+cn.yumis.fight.modules.presentation.api.client.NumberText.number(c.parameters().get("eyeBonusMut",0)*100)+"%；周围半径16格无遮挡敌人持续失明。再次按键或到期凝聚眼球并爆炸，炸眼1.2秒期间无敌。");
    }
}
