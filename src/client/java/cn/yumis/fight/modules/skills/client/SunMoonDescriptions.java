package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.*;
import static cn.yumis.fight.modules.presentation.api.client.NumberText.number;

final class SunMoonDescriptions {
    private SunMoonDescriptions(){}
    static void install(){
        SkillDescriptions.register(SunMoonSkills.CYCLE,c->"掌握日与月的力量，转职时自动学会1级，可继续消耗SP升级。\n日曜心和月弦心不能同时拥有，各最多3层，不会随时间消失。\n当前1、2、3层日曜心分别使日系技能伤害提高"+number(100*SunMoonRules.sunDamage(c.parameters().rank(),1))+"%、"+number(100*SunMoonRules.sunDamage(c.parameters().rank(),2))+"%、"+number(100*SunMoonRules.sunDamage(c.parameters().rank(),3))+"%。\n每升一级，三档增伤均在上一级基础上提高12%。可受被动技能加级影响，魔法技能加级不生效。\n日曜心也会增加消耗，并暂停技能以外的魔力回复；月弦心大幅提高魔力回复速度。\n切换到另一系技能时，施法更快且不消耗魔力，但伤害会降低。\n调和可让一次原本需要消耗的日系技能免费施放。\n学会悖论后，满3层心切换到另一种心时获得1层悖论。\n死亡、离线、跨维度或重置职业技能会清除这些状态。");
        SkillDescriptions.register(SunMoonSkills.MOON,c->"发射一枚月光飞弹，碰到敌人、墙壁或飞到最远处时，展开一轮月亮，对周围敌人造成冰属性伤害。\n成功放出获得"+SunMoonRules.moonHearts(c.parameters().rank())+"层月弦心。技能达到5级时获得2层，10级时获得3层，装备加级也有效。\n基础咏唱1秒，飞行最远16格，伤害半径3格，无冷却。");
        SkillDescriptions.register(SunMoonSkills.SUN,c->"在目标上方召唤一颗火球，随后落地爆炸，对周围敌人造成火属性伤害。\n成功放出获得1层日曜心。\n基础咏唱1.8秒，伤害半径3格，无冷却。");
        SkillDescriptions.register(SunMoonSkills.REFLUX,c->"立即唤回月之力量，获得3层月弦心，并将调和补至至少"+SunMoonRules.refluxHarmony(c.parameters().rank())+"层。\n最多可学习1级。通过装备等方式达到5级、10级时，获得的调和各额外增加1层。\n满3层日曜心时使用也能触发已学会的悖论。\n无需咏唱，不消耗魔力，基础冷却30秒。");
        SkillDescriptions.register(SunMoonSkills.FLARE,c->"在目标位置引发猛烈的烈日爆炎，对周围敌人造成大量火属性伤害。\n成功放出获得3层日曜心。\n基础咏唱2.5秒，伤害半径4格，无冷却。");
        SkillDescriptions.register(SunMoonSkills.CORONA,c->"需要在拥有日曜心时才能释放。召唤一颗巨大的火球，在目标位置缓缓升起，随后爆炸，造成极高的火属性伤害。\n附近第一人称下直视火球且无遮挡的玩家，视野边缘会模糊0.8秒，包括自己和队友；中心仍然清晰。\n不会获得或消耗日曜心。\n基础咏唱1.8秒，火球升起0.8秒后爆炸，伤害半径4格，无冷却。");
        SkillDescriptions.register(SunMoonSkills.STARFALL,c->"需要在拥有月弦心时才能释放。在目标位置唤下月光与星辉，持续对周围敌人造成冰属性伤害。\n成功放出后将调和补至至少3层，不会获得或消耗月弦心；多次施放的月光可以重叠。\n基础咏唱2.5秒，持续2秒，每0.25秒造成一次伤害，伤害半径4格，无冷却。");
        SkillDescriptions.register(SunMoonSkills.PARADOX,c->"消耗1层悖论，召唤交缠的日月光球，旋转落下后接连爆发冰与火的力量，造成巨额伤害。\n可以瞄准空地；瞄准敌人时，光球会追随目标，目标消失后在最后的位置爆炸。\n不受日曜心增伤，不会获得或改变两种心。\n无需咏唱，光球下落0.6秒，伤害半径1格，不消耗魔力，无冷却。");
    }
}
