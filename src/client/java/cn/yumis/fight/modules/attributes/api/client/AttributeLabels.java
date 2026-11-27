package cn.yumis.fight.modules.attributes.api.client;
import cn.yumis.fight.modules.attributes.api.Stat;
public final class AttributeLabels {
    private AttributeLabels(){}
    public static String statName(Stat s){
        return switch(s){
            case HEALTH->"生命";
            case MP->"MP";
            case STRENGTH->"力量";
            case INTELLIGENCE->"智力";
            case VITALITY->"体质";
            case SPIRIT->"精神";
            case PHYSICAL_ATTACK->"基础物攻";
            case MAGICAL_ATTACK->"基础魔攻";
            case PHYSICAL_DEFENSE->"物理防御";
            case MAGICAL_DEFENSE->"魔法防御";
            case LIGHT->"光强化";
            case FIRE->"火强化";
            case ICE->"冰强化";
            case DARK->"暗强化";
            case LIGHT_RESIST->"光抗性";
            case FIRE_RESIST->"火抗性";
            case ICE_RESIST->"冰抗性";
            case DARK_RESIST->"暗抗性";
            case DROP_RATE->"掉宝率";
            case SHIELD->"装备护盾";
            case EVASION->"闪避值";
            case ATTACK_SPEED->"攻击速度";
            case MOVEMENT_SPEED->"移动速度";
            case CAST_SPEED->"技能释放速度";
            case LIFE_STEAL->"吸血值";
            case MANA_REGENERATION->"魔力回复速度";
            case COOLDOWN_RECOVERY->"冷却恢复速度";
            case EXPERIENCE_GAIN->"角色经验获取";
            case STAMINA->"最大体力";
            case STAMINA_RECOVERY->"体力恢复速度";
            case SHIELD_COOLDOWN_RECOVERY->"护盾回复冷却恢复速度";
            case SHIELD_REGENERATION->"护盾回复速度";
            case HEALTH_REGENERATION->"生命恢复速度";
        };
    }}
