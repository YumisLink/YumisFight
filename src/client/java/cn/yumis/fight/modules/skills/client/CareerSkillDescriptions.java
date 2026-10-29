package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.*;
import java.util.*;

final class CareerSkillDescriptions {
    private CareerSkillDescriptions(){}
    static void install(){
        Map<String,String> descriptions=new LinkedHashMap<>();
        descriptions.put(CareerSkills.KEIGA,"常驻附身：魔攻提升15%并随技能等级成长，剑类普攻转为暗属性魔法伤害；怪物直接伤害在职业防护后再乘0.9。");
        descriptions.put(CareerSkills.FIELD,"8格内指向地面，半径5、持续12秒。有效1级红阵、3级绿阵、8级冰阵、15级紫阵；红阵增益自己和队友，绿阵降低敌人双防15%。同类增益取最强。");
        descriptions.put(CareerSkills.SOUL_SLASH,"满蓄力鬼斩追加鬼神冲击，按本技能等级成长；位于自己的鬼阵内时进一步增强。不增加鬼斩消耗和冷却。");
        descriptions.put(CareerSkills.TOMBSTONE,"站定引导2秒，半径4内10轮落碑，每目标每轮最多命中一次；中断取消剩余段数。");
        descriptions.put(CareerSkills.PHANTOM,"穿行8格、路径宽3，遇墙停止；穿行阶段0.25秒无敌，普通怪停顿0.5秒。");
        descriptions.put(CareerSkills.KALLA,"持续20秒，普攻每周期发射一枚10格冥炎弹。冥炎最多3层，每0.5秒造成暗伤并刷新3秒持续；再次按键三连冥炎剑收尾，已附着冥炎继续计时。");
        descriptions.put(CareerSkills.BLACHE,"半径8，释放2秒期间无敌。吞自己的鬼阵：每个鬼神使伤害提升100%，阵法每高一级再提升5%；不重置阵法冷却，无阵也可施放。");
        descriptions.put(CareerSkills.BRANDS,"常驻魔攻提升15%并随等级成长。最多5印，每5秒自动产1印；成功施放地裂、邪光、冰波、火波各产1印，空放也可产印，多段不重复。");
        descriptions.put(CareerSkills.ORB,"消耗全部刻印，至少1印。球体前进最多10格，附着首个敌人，攻击3+5×印数次。同一玩家对同一目标最多每0.35秒一击，多颗珠剩余段数顺延；目标死亡结束。");
        descriptions.put(CareerSkills.CRESCENT,"按住蓄力最多1秒，发射一束前进12格的X字波；不蓄力宽高2格，满蓄力宽高4格、总伤害为未蓄力的3倍，每目标最多3段。成功释放产1印。");
        descriptions.put(CareerSkills.ICE,"前方长12、宽3的三轮冰刺；普通怪冻结1.5秒，精英减半，Boss免冻结。成功施放产1印。");
        descriptions.put(CareerSkills.FIRE,"向准星10格内抛出一颗火球，沿弧线飞到落点。沿途宽5格抓取可抓敌人，终点半径2.5爆炸一次；碰墙提前爆炸。Boss与完全免击退目标免抓，仍可受爆炸伤害。成功施放产1印。");
        descriptions.put(CareerSkills.AGNI,"消耗全部刻印，消耗几印就显示几球。站定引导3秒，半径5；每球每0.85秒攻击一次，各球错开命中，最后爆炸一次。更多印增加旋转命中次数；释放速度同步压缩动作和间隔。");
        descriptions.put(CareerSkills.FRENZY,"开关双刀，每普攻周期两击。物攻提升15%并随技能等级成长；攻速+15个百分点、移速提升10%、双防降低15个百分点，完全免疫战斗击退。生命≤70%时伤害提升10%，≤40%时提升20%，不叠加；不持续耗血。");
        descriptions.put(CareerSkills.CRASH,"向前小跳3格，落地后X形血气向四周飞出；半径3，下砸与冲击分别命中。不消耗生命。");
        descriptions.put(CareerSkills.HAND,"前方3格抓取并喷发；命中回复最大生命10%，每次施放最多回复一次，Boss同样有效，空放不回血。");
        descriptions.put(CareerSkills.RAGE,"半径4、五段血气。施放支付最大生命4%，不扣护盾。");
        descriptions.put(CareerSkills.BLADE,"前方5格突刺，命中后半径3爆炸。施放支付最大生命5%。");
        descriptions.put(CareerSkills.ERUPTION,"前跳6格，落地后周围随机喷射血柱；半径6，下砸加四段喷发。施放支付最大生命8%。");
        descriptions.put(CareerSkills.SWORD,"支付最大生命10%，开启最多60秒蓄血。造成有效伤害即可获得层数，包括持续伤害，同一玩家最多每0.5秒一层，上限20。再次按键或到期下斩血剑并爆炸，2秒终结期间无敌；前方3格为中心、半径5。");
        descriptions.forEach((id,text)->SkillDescriptions.register(id,c->text));
        SkillDescriptions.register(CareerSkills.EYE,c->"强化20秒：普攻、地裂、冰波、火波伤害提升"+cn.yumis.fight.modules.presentation.api.client.NumberText.number(c.parameters().get("eyeBonusMut",0)*100)+"%；周围半径16格无遮挡敌人持续失明。再次按键或到期凝聚眼球并爆炸，炸眼1.2秒期间无敌。");
    }
}
