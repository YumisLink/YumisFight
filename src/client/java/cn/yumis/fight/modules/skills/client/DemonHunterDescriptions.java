package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.*;

final class DemonHunterDescriptions {
    private DemonHunterDescriptions(){}
    static void install(){
        SkillDescriptions.register(DemonHunterSkills.CHAOS,c->"暗属性物理 · 前方3格、宽3格，主手与副手先后各斩一次，两刀等伤。\n基础间隔0.15秒，受释放速度影响；不位移，可同时命中多个敌人。");
        SkillDescriptions.register(DemonHunterSkills.GLAIVE,c->"暗属性物理 · 沿起手准星投出战刃，速度20格/秒，最远16格。\n首个敌人承受一次伤害并产生20怒气；遇墙停止，不反弹、不消耗装备。");
        SkillDescriptions.register(DemonHunterSkills.SIGHT,c->"永久夜视I，不耗怒气，不占快捷栏。\n最多学习1级；退级或重置只清除职业夜视，其他来源夜视保留。");
        SkillDescriptions.register(DemonHunterSkills.AURA,c->"暗属性魔法持续伤害 · 半径4格，跟随本人6秒，每秒一次，共6次。\n右侧量谱显示图标、剩余秒数与比例条，到期移除。\n每次脉冲命中至少一个敌人产生5怒气，多目标不重复产怒。\n可移动并使用其他技能；不独立暴击、不触发装备附伤或吸血。");
        SkillDescriptions.register(DemonHunterSkills.BEAM,c->"暗属性魔法 · 0.2秒升高1格，再悬空引导2秒；低顶安全降低，可转向，不能主动移动。\n引导期间每0.25秒获得10点怒气，共80点；不需要命中，中断后停止，怒气不超过上限。\n释放期间使用反向读条，从满格递减到0，沿用咏唱读条样式。\n起跳至结束职业防护替换为80%，环境伤害保持原规则，结束恢复重力自然落地。\n粗亮绿色光柱视觉宽2格，实际伤害宽3格，射程16格；贯穿敌人，被墙阻挡。\n每0.25秒直接命中一次，共8次，保留原技能总直伤；装备效果按8次正常触发。\n普通受伤不打断；Esc或其他技能可取消，硬控、死亡、换武器中断，起手费用不退。");
        SkillDescriptions.register(DemonHunterSkills.RUSH,c->"暗属性物理 · 沿起手视角三维方向冲出12格，可在空中使用。\n基础0.4秒，受释放速度影响，保持总距离；每敌最多一次，遇墙停止。\n不自带无敌，过程中不能转向。");
        SkillDescriptions.register(DemonHunterSkills.DANCE,c->"暗属性物理 · 起手脚下半径3格圆阵，固定俯视第三人称，全程无敌。\n前半程基础2次随机直径穿斩：瞬现于圆周，基础0.10秒快速穿过，空余时间完全隐藏。\n末尾从阵心上方最多5格瞬现，基础0.15秒快速下斩扬尘，不播放缓慢升空。\n每斩命中整个圆阵，基础伤害20%＋20%＋60%；地形会安全裁剪动作。\n基础总长2秒，全部动作随释放速度缩短；段数放不下时压缩每刀，不丢段。\n变形时改为死亡横扫，额外2斩，总伤害提高至常态的140%，动作不延长。");
        SkillDescriptions.register(DemonHunterSkills.HUNT,c->"整招归物理技能类，接受物理技能加级。\n随准星方向持续恒速冲锋，最多4秒、每秒10格；无重力、霸体，遇墙、到时或命中立即停冲。\n冲锋沿实际路线留下逐渐消散的邪能拖尾。\n首个敌人承受60%暗魔法重击，随后左右武器交替连续挥动，8次暗物理斩击合计40%。\n连斩基础0.8秒，受释放速度影响，期间无敌并安全近身追随原目标。\n目标死亡、失效或无法跟随时结束，不转火。");
        SkillDescriptions.register(DemonHunterSkills.META,c->"暗属性魔法 · 准星16格内选安全落点，基础1秒跃落，落地半径4格，跃落无敌。\n落地首击即享受变形增益，随后持续20秒。\n物攻、魔攻各提高30%（MUT）；所有技能攻击力独立乘1.30。\n吸血率增加10个百分点，最终最高90%；死亡横扫额外2斩。\n周身邪能与恶魔角、翼虚影，不改变人物碰撞体积。");
    }
}
