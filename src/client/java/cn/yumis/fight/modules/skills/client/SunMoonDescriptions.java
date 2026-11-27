package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.dungeons.api.TowerBattleCard;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.SkillController;
import static cn.yumis.fight.modules.presentation.api.client.NumberText.number;

final class SunMoonDescriptions {
    private SunMoonDescriptions(){}
    private static boolean hasCard(TowerBattleCard card){return card.held(SkillController.INSTANCE.snapshot());}
    static void install(){
        SkillDescriptions.register(SunMoonSkills.CYCLE,c->{
            int cap=SunMoonRules.stackCap(SkillController.INSTANCE.snapshot());
            var sb=new StringBuilder("掌握日与月的力量，转职时自动学会1级，可继续消耗SP升级。\n日曜心和月弦心不能同时拥有，各最多"+cap+"层（装备与职业神话可提升该上限），不会随时间消失。\n当前1、2、3层日曜心分别使日系技能伤害提高"+number(100*SunMoonRules.sunDamage(c.parameters().rank(),1))+"%、"+number(100*SunMoonRules.sunDamage(c.parameters().rank(),2))+"%、"+number(100*SunMoonRules.sunDamage(c.parameters().rank(),3))+"%。\n超过3层后，每多1层日曜心再额外提高120%伤害。\n每升一级，三档增伤均在上一级基础上提高12%。可受被动技能加级影响，魔法技能加级不生效。\n日曜心也会增加消耗，并暂停技能以外的魔力回复；月弦心大幅提高魔力回复速度。\n切换到另一系技能时，施法更快且不消耗魔力，但伤害会降低。\n调和可让一次原本需要消耗的日系技能免费施放。\n学会悖论后，满层心切换到另一种心时获得1层悖论。\n死亡、离线、跨维度或重置职业技能会清除这些状态。");
            if(hasCard(TowerBattleCard.SUNMOON_FIVE_HEARTS))
                sb.append("\n职业神话（五曜天穹）：日曜心与月弦心层数上限额外提高").append(TowerBattleCard.SUNMOON_FIVE_HEARTS.count("cap")).append("层。");
            if(hasCard(TowerBattleCard.SUNMOON_ASTRAL_STEP))
                sb.append("\n职业神话（踏星咏叹）：咏唱期间保留").append(number(TowerBattleCard.SUNMOON_ASTRAL_STEP.value("move"))).append("%移动速度，日月技能咏唱速度独立提高").append(number(TowerBattleCard.SUNMOON_ASTRAL_STEP.value("speed"))).append("%。");
            if(hasCard(TowerBattleCard.SUNMOON_ECLIPSE))
                sb.append("\n职业神话（蚀日同辉）：反向切系不再降低伤害，每次切换获得").append(number(TowerBattleCard.SUNMOON_ECLIPSE.value("duration"))).append("秒全技能伤害总增").append(number(TowerBattleCard.SUNMOON_ECLIPSE.value("damage"))).append("%。");
            return sb.toString();
        });
        SkillDescriptions.register(SunMoonSkills.MOON,c->{
            var sb=new StringBuilder("发射一枚月光飞弹，碰到敌人、墙壁或飞到最远处时，展开一轮月亮，对周围敌人造成冰属性伤害。\n成功放出获得"+SunMoonRules.moonHearts(c.parameters().rank())+"层月弦心，装备可额外增加获得的层数。技能达到5级时获得2层，10级时获得3层，装备加级也有效。\n基础咏唱1秒，飞行最远16格，伤害半径3格，无冷却。");
            if(hasCard(TowerBattleCard.SUNMOON_MOON_ECHO))
                sb.append("\n职业神话（朔月伴星）：变为瞬发并保底获得").append(TowerBattleCard.SUNMOON_MOON_ECHO.count("hearts")).append("层月弦心，施放后获得").append(number(TowerBattleCard.SUNMOON_MOON_ECHO.value("duration"))).append("秒星环，日曜期每次日系施放自动追射").append(number(TowerBattleCard.SUNMOON_MOON_ECHO.value("echo"))).append("%伤害的追踪弦月弹（同享日曜心增伤）。");
            return sb.toString();
        });
        SkillDescriptions.register(SunMoonSkills.SUN,c->{
            var sb=new StringBuilder("在目标上方召唤一颗火球，随后落地爆炸，对周围敌人造成火属性伤害。\n成功放出获得1层日曜心，装备可额外增加获得的层数。\n基础咏唱1.8秒，伤害半径3格，无冷却。");
            if(hasCard(TowerBattleCard.SUNMOON_SUNFIRE_SPARK))
                sb.append("\n职业神话（炽阳火苗）：每施放").append(TowerBattleCard.SUNMOON_SUNFIRE_SPARK.count("every")).append("次日系技能获1层火苗（上限").append(TowerBattleCard.SUNMOON_SUNFIRE_SPARK.count("cap")).append("层），可在任意时刻（含咏唱期间）零硬直脱手瞬发免费日炎，范围提高").append(number(TowerBattleCard.SUNMOON_SUNFIRE_SPARK.value("range"))).append("%。");
            return sb.toString();
        });
        SkillDescriptions.register(SunMoonSkills.REFLUX,c->{
            int harmony=SunMoonRules.refluxHarmony(c.parameters().rank())+(int)c.parameters().get("harmonyGrantBonus",0);
            var sb=new StringBuilder("立即唤回月之力量，获得3层月弦心，并将调和补至至少"+harmony+"层，装备可额外增加。\n最多可学习1级。通过装备等方式达到5级、10级时，获得的调和各额外增加1层。\n满层日曜心时使用也能触发已学会的悖论。\n无需咏唱，不消耗魔力，基础冷却30秒。");
            if(hasCard(TowerBattleCard.SUNMOON_REFLUX_LEYLINES))
                sb.append("\n职业神话（星轨魔纹）：脚下展开").append(number(TowerBattleCard.SUNMOON_REFLUX_LEYLINES.value("duration"))).append("秒、半径").append(number(TowerBattleCard.SUNMOON_REFLUX_LEYLINES.value("radius"))).append("格星轨魔纹，阵内咏唱速度独立提高").append(number(TowerBattleCard.SUNMOON_REFLUX_LEYLINES.value("speed"))).append("%，额外多给").append(TowerBattleCard.SUNMOON_REFLUX_LEYLINES.count("harmony")).append("层调和。");
            return sb.toString();
        });
        SkillDescriptions.register(SunMoonSkills.FLARE,c->{
            var sb=new StringBuilder("在目标位置引发猛烈的烈日爆炎，对周围敌人造成大量火属性伤害。\n成功放出获得3层日曜心，装备可额外增加获得的层数。\n基础咏唱2.5秒，伤害半径"+number(c.parameters().get("radius",4))+"格，无冷却。");
            if(hasCard(TowerBattleCard.SUNMOON_FLARE_SUPERNOVA))
                sb.append("\n职业神话（终焉核爆）：满层日曜心且魔力低于").append(number(TowerBattleCard.SUNMOON_FLARE_SUPERNOVA.value("threshold"))).append("%时激活为瞬发收尾核爆，耗尽残余魔力，范围扩大").append(number(TowerBattleCard.SUNMOON_FLARE_SUPERNOVA.value("range"))).append("%，伤害提高").append(number(TowerBattleCard.SUNMOON_FLARE_SUPERNOVA.value("damage"))).append("%。");
            if(hasCard(TowerBattleCard.SUNMOON_FLARE_EMBER))
                sb.append("\n职业神话（熔核余烬）：落点留存").append(number(TowerBattleCard.SUNMOON_FLARE_EMBER.value("duration"))).append("秒熔核火海，每").append(number(TowerBattleCard.SUNMOON_FLARE_EMBER.value("interval"))).append("秒造成基准伤害").append(number(TowerBattleCard.SUNMOON_FLARE_EMBER.value("tick"))).append("%火伤（实时吃当前日曜心增伤）。");
            return sb.toString();
        });
        SkillDescriptions.register(SunMoonSkills.CORONA,c->{
            double chant=SunMoonRules.chantSeconds(SkillCatalog.require(SunMoonSkills.CORONA),SkillController.INSTANCE.snapshot());
            var sb=new StringBuilder("需要在拥有日曜心时才能释放。召唤一颗巨大的火球，在目标位置缓缓升起，随后爆炸，造成极高的火属性伤害。\n附近第一人称下直视火球且无遮挡的玩家，视野边缘会模糊0.8秒，包括自己和队友；中心仍然清晰。\n不会获得或消耗日曜心。\n基础咏唱"+number(chant)+"秒，火球升起"+number(c.parameters().get("impactDelay",.8))+"秒后爆炸，伤害半径"+number(c.parameters().get("radius",4))+"格，无冷却。");
            if(hasCard(TowerBattleCard.SUNMOON_CORONA_COLLAPSE))
                sb.append("\n职业神话（引力坍缩）：取消模糊与缓慢升空，引爆前牵引周围敌人聚拢，伤害提高").append(number(TowerBattleCard.SUNMOON_CORONA_COLLAPSE.value("damage"))).append("%。");
            if(hasCard(TowerBattleCard.SUNMOON_CORONA_COLOSSUS))
                sb.append("\n职业神话（重阳天坠）：咏唱与耗蓝提高").append(number(TowerBattleCard.SUNMOON_CORONA_COLOSSUS.value("slow"))).append("%，范围扩大").append(number(TowerBattleCard.SUNMOON_CORONA_COLOSSUS.value("range"))).append("%，伤害提高").append(number(TowerBattleCard.SUNMOON_CORONA_COLOSSUS.value("damage"))).append("%。");
            return sb.toString();
        });
        SkillDescriptions.register(SunMoonSkills.STARFALL,c->{
            var sb=new StringBuilder("需要在拥有月弦心时才能释放。在目标位置唤下月光与星辉，持续对周围敌人造成冰属性伤害。\n成功放出后将调和补至至少3层（装备可额外提升），不会获得或消耗月弦心；多次施放的月光可以重叠。\n基础咏唱"+number(SunMoonRules.chantSeconds(SkillCatalog.require(SunMoonSkills.STARFALL),SkillController.INSTANCE.snapshot()))+"秒，持续"+number(c.parameters().get("slashes",8)*c.parameters().get("interval",.25))+"秒，每"+number(c.parameters().get("interval",.25))+"秒造成一次伤害，伤害半径"+number(c.parameters().get("radius",4))+"格，无冷却。");
            if(hasCard(TowerBattleCard.SUNMOON_STARFALL_AURORA))
                sb.append("\n职业神话（极光星幕）：瞬发脱手释放，冻结积累提高").append(number(TowerBattleCard.SUNMOON_STARFALL_AURORA.value("buildup"))).append("%，命中使目标冰火抗性降低").append(number(TowerBattleCard.SUNMOON_STARFALL_AURORA.value("shred"))).append("点，持续").append(number(TowerBattleCard.SUNMOON_STARFALL_AURORA.value("debuff"))).append("秒。");
            return sb.toString();
        });
        SkillDescriptions.register(SunMoonSkills.PARADOX,c->{
            var sb=new StringBuilder("消耗1层悖论，召唤交缠的日月光球，旋转落下后接连爆发冰与火的力量，造成巨额伤害。\n可以瞄准空地；瞄准敌人时，光球会追随目标，目标消失后在最后的位置爆炸。\n不受日曜心增伤，不会获得或改变两种心。\n无需咏唱，光球下落"+number(c.parameters().get("impactDelay",.6))+"秒，伤害半径"+number(c.parameters().get("radius",2.5))+"格，不消耗魔力，无冷却。");
            if(hasCard(TowerBattleCard.SUNMOON_PARADOX_POLARITY))
                sb.append("\n职业神话（双极悖论）：日曜心下继承").append(number(TowerBattleCard.SUNMOON_PARADOX_POLARITY.value("sun"))).append("%日曜增伤，月弦心下伤害总增").append(number(TowerBattleCard.SUNMOON_PARADOX_POLARITY.value("moon"))).append("%。");
            if(hasCard(TowerBattleCard.SUNMOON_PARADOX_LOOP))
                sb.append("\n职业神话（因果回环）：上限提高至").append(TowerBattleCard.SUNMOON_PARADOX_LOOP.count("cap")).append("层并初始携带1层，施放回复").append(number(TowerBattleCard.SUNMOON_PARADOX_LOOP.value("mana"))).append("%最大魔力，伤害总增").append(number(TowerBattleCard.SUNMOON_PARADOX_LOOP.value("damage"))).append("%。");
            return sb.toString();
        });
    }
}
