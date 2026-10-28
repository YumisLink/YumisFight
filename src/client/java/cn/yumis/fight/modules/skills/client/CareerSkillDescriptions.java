package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.*;
import java.util.*;

final class CareerSkillDescriptions {
    private CareerSkillDescriptions(){}
    static void install(){
        Map<String,String> descriptions=new LinkedHashMap<>();
        descriptions.put(CareerSkills.KEIGA,"常驻附身：魔攻提升15%并随技能等级成长，剑类普攻转为暗属性魔法伤害；怪物直接伤害在职业防护后再乘0.9。");
        descriptions.put(CareerSkills.FIELD,"8格内指向地面，半径5、持续12秒。有效1级红阵、3级绿阵、8级冰阵、15级紫阵；红阵增益自己和队友，绿阵降低敌人双防15%。同类增益取最强。");
        descriptions.put(CareerSkills.SOUL_SLASH,"满蓄力鬼斩追加鬼神冲击：基础系数2.0，按本技能等级成长；不增加鬼斩的消耗和冷却。");
        descriptions.put(CareerSkills.TOMBSTONE,"站定引导2秒，半径4内10轮落碑，每目标每轮最多命中一次；中断取消剩余段数。");
        descriptions.put(CareerSkills.PHANTOM,"穿行8格、路径宽3，遇墙停止；穿行阶段0.25秒无敌，普通怪停顿0.5秒。");
        descriptions.put(CareerSkills.KALLA,"持续20秒，普攻每周期发射一枚10格冥炎弹。最多3层，每层每0.5秒造成0.10系数暗伤，持续3秒；再次按键三连冥炎剑收尾，已附着冥炎继续计时。");
        descriptions.put(CareerSkills.BLACHE,"半径8、释放2秒期间无敌。吞自己的鬼阵：每个鬼神伤害MUT+100%，阵法每高一级再+5%；不重置阵法冷却。一级一觉、四鬼15级阵系数68.4。");
        descriptions.put(CareerSkills.BRANDS,"常驻魔攻提升15%并随等级成长。最多5印，每5秒自动产1印；成功施放地裂、邪光、冰波、火波各产1印，空放也可产印，多段不重复。");
        descriptions.put(CareerSkills.ORB,"消耗全部刻印，至少1印；总系数3+0.4×印数。球体前进最多10格，首次接触后停留，3秒6段占75%，最终半径2爆炸占25%。");
        descriptions.put(CareerSkills.CRESCENT,"按住蓄力最多1秒，总系数由2.0增至6.0；前进12格、宽4，每目标最多3段；释放成功产1印。");
        descriptions.put(CareerSkills.ICE,"前方长12、宽3的三轮冰刺，每段2.0；普通怪冻结1.5秒，精英减半，Boss免冻结。成功施放产1印。");
        descriptions.put(CareerSkills.FIRE,"前方长10、宽5的四轮火焰，每段2.2；成功施放产1印。");
        descriptions.put(CareerSkills.AGNI,"消耗全部刻印，至少1印。站定引导3秒，半径5；总系数4+1.6×印数，8段旋转占60%、爆炸占40%；Boss正常受伤且免硬控。");
        descriptions.put(CareerSkills.EYE,"强化20秒：普攻及地裂、冰波、火波伤害MUT+20%，保留原消耗、冷却与段数。再次按键或到期炸眼，半径8、系数16；只有炸眼阶段无敌。");
        descriptions.put(CareerSkills.FRENZY,"开关双刀，每普攻周期两击、每击65%。物攻提升15%并随等级成长；攻速+15个百分点、移速提升10%、双防MUT-15个百分点，完全免疫战斗击退。生命≤70%伤害MUT+10%，≤40%为+20%，不叠加；不持续耗血。");
        descriptions.put(CareerSkills.CRASH,"向前小跳3格，落地半径3：下砸0.6+冲击2.0。不消耗生命。");
        descriptions.put(CareerSkills.HAND,"前方3格抓取并喷发，系数4.0；命中回复最大生命10%，每次施放最多回复一次，Boss同样有效，空放不回血。");
        descriptions.put(CareerSkills.RAGE,"半径4、五段血气，每段1.3；施放支付最大生命4%，不扣护盾。");
        descriptions.put(CareerSkills.BLADE,"前方5格突刺2.2，命中后半径3爆炸6.6；施放支付最大生命5%。");
        descriptions.put(CareerSkills.ERUPTION,"前跳6格，落地半径6；下砸4.0+四段喷发各2.0；施放支付最大生命8%。");
        descriptions.put(CareerSkills.SWORD,"开启15秒蓄血窗口并支付最大生命10%。有效普攻周期或主动技能首次命中+1层，最多10层，1秒最多2层；再次按键或到期释放，总系数24+1.6×层数，只有最后2秒演出无敌。");
        descriptions.forEach((id,text)->SkillDescriptions.register(id,c->text));
    }
}
