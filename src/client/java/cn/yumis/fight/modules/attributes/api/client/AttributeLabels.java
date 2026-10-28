package cn.yumis.fight.modules.attributes.api.client;
import cn.yumis.fight.modules.attributes.api.Stat;
public final class AttributeLabels {
    private AttributeLabels(){}
    public static String statName(Stat s){
        return new String[]{
            "生命","MP","力量","智力","精神","基础物攻","基础魔攻","物理防御","魔法防御","光强化","火强化","冰强化","暗强化","光抗性","火抗性","冰抗性","暗抗性","掉宝率","装备护盾","闪避值","攻击速度","移动速度","技能释放速度","吸血值","魔力回复速度","冷却恢复速度","角色经验获取"
        }
        [s.ordinal()];
    }}
