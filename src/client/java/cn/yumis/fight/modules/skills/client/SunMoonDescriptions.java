package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.*;

final class SunMoonDescriptions {
    private SunMoonDescriptions(){}
    static void install(){
        SkillDescriptions.register(SunMoonSkills.CYCLE,c->"转职自动获得，日/月心互斥，最多3层且无时限。\n日曜心：日技能伤害提升50%/130%/250%，费用增加100%/200%/300%；阻止非技能回魔。\n月弦心：回魔速度提升100%/1000%/10000%。\n反属性：施速提升30%/80%/200%，免原始费用；日→月伤害降低40%，月→日降低80%。\n成功放出后同属性加层，反属性清旧心后获新心；本次技能取起手旧状态。\n满层转心获得1悖论，上限1层；需已学会觉醒。\n调和最多3层，只在日技能原本需费时消耗1层免除费用。\n所有状态在死亡、离线、跨维度及职业技能重置时清除。");
        SkillDescriptions.register(SunMoonSkills.MOON,c->"月属性 · 基础咏唱1秒，期间移速为正常的20%。\n飞弹最远16格，碰敌、碰墙或到达终点展开大月亮，半径3格冰伤。\n月亮在2秒内逐渐变暗；成功放出获得1月弦心。\n无冷却，取消不退起手费用。");
        SkillDescriptions.register(SunMoonSkills.SUN,c->"日属性 · 基础咏唱1.8秒，结束时按当前准星选落点，敌人脚部优先。\n火球放出0.35秒后落地爆燃，半径3格，一次火伤，没有持续烧伤。\n成功放出获得1日曜心；无冷却。");
        SkillDescriptions.register(SunMoonSkills.REFLUX,c->"瞬发 · 立即获得3月弦心，调和补满3层。\n满3日曜心转月同样可以触发悖论。\n不消耗原始费用，基础冷却30秒。");
        SkillDescriptions.register(SunMoonSkills.FLARE,c->"日属性 · 基础咏唱2.5秒，结束时按当前准星取落点。\n半径4格火伤；同技能等级基础伤害为日炎2倍，基础费用为3倍。\n成功放出获得3日曜心；无冷却。");
        SkillDescriptions.register(SunMoonSkills.CORONA,c->"日属性 · 基础咏唱1.8秒，结束时按当前准星取落点。\n生成直径4格火球，0.8秒内从上方1.5格缓升至2格后爆炸。\n爆炸半径4格，同等级基础伤害为日炎3倍，基础费用为1.5倍。\n32格内看见且无遮挡的玩家，含自己和队友，屏幕虚化0.8秒。\n成功放出获得1日曜心；无冷却。");
        SkillDescriptions.register(SunMoonSkills.STARFALL,c->"月属性 · 基础咏唱2.5秒，结束时按当前准星取落点。\n半径4格月光，每0.25秒一段，2秒内共8段冰伤。\n同等级总伤害为月流2.2倍，各次施法独立，允许叠场。\n成功召出获得1月弦心，调和补满3层；无冷却。");
        SkillDescriptions.register(SunMoonSkills.PARADOX,c->"瞬发 · 消耗1悖论，无原始费用、无冷却。\n可指定空地；准星命中敌人时爆心随其移动。\n红蓝光球带拖尾旋转下降0.6秒，半径1格依次造成冰、火两段伤害。\n两段各占50%，合计同等级日炎10倍，不受日曜心增伤。\n目标失效后在最后位置爆炸，不返层；不产生或转换心。");
    }
}
