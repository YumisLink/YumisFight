package cn.yumis.fight.modules.affixes.api;

import cn.yumis.fight.modules.attributes.api.Stat;

import java.math.*;

/** Pure display formatting; never writes back rounded numbers. */
public final class AffixText {
    private AffixText(){}
    public static String integer(double value){return Double.isFinite(value)?BigDecimal.valueOf(value).setScale(0,RoundingMode.HALF_UP).toPlainString():"?";}
    public static String statName(Stat s){return switch(s){
        case HEALTH->"最大生命";case MP->"最大魔力";case STRENGTH->"力量";case INTELLIGENCE->"智力";case SPIRIT->"精神";
        case PHYSICAL_ATTACK->"基础物理攻击";case MAGICAL_ATTACK->"基础魔法攻击";case PHYSICAL_DEFENSE->"物理防御";case MAGICAL_DEFENSE->"魔法防御";
        case LIGHT->"光属性强化";case FIRE->"火属性强化";case ICE->"冰属性强化";case DARK->"暗属性强化";
        case LIGHT_RESIST->"光属性抗性";case FIRE_RESIST->"火属性抗性";case ICE_RESIST->"冰属性抗性";case DARK_RESIST->"暗属性抗性";
        case DROP_RATE->"掉宝率";case SHIELD->"装备护盾上限";case EVASION->"闪避值";case ATTACK_SPEED->"攻击速度";case MOVEMENT_SPEED->"移动速度倍率";case CAST_SPEED->"技能释放速度倍率";case LIFE_STEAL->"吸血值";case MANA_REGENERATION->"魔力回复速度";case COOLDOWN_RECOVERY->"冷却恢复速度";case EXPERIENCE_GAIN->"角色经验获取";
    };}
    public static String affix(Affix a){
        var current=AffixDefinitions.current().affixes.get(cn.yumis.fight.modules.affixes.api.AffixResolver.definitionId(a));
        if(current!=null&&current.description()!=null&&!current.description().isBlank())return current.name()+"："+current.description();
        if(a.operation.equals("MAGIC")||a.operation.equals("MAGIC_AMPLIFICATION"))return MagicEffects.text(a);
        if(a.operation.equals("EQUIPMENT_RULE"))return equipmentRule(a);
        String body;
        if(a.operation.equals("SKILL_FAMILY")){return (current==null?a.family:current.name())+" +"+a.rank+"级（不可重掷，可替换）";}
        if(a.operation.equals("EXTRA")&&(a.element<0||a.element>=4))return a.id+"（保留原记录）";
        if(a.operation.equals("EXTRA"))body="附加"+integer(a.value)+"～"+integer(a.maximum)+"点"+new String[]{"光","火","冰","暗"}[a.element]+"属性伤害";
        else if(a.operation.equals("ALL_RESIST_FLAT"))body="所有元素抗性 "+signed(a.value);
        else if(a.stat.isEmpty())body=a.id;
        else{
            boolean percent=a.operation.equals("PERCENT")||a.operation.equals("CHARACTER_PERCENT");var stat=cn.yumis.fight.modules.affixes.api.AffixEffects.stat(a);if(stat.isEmpty())return a.id+"（保留原记录）";Stat s=stat.get();
            body=(a.operation.equals("PERCENT")?"本装备":a.operation.equals("ITEM_FINAL_ADD")?"本武器最终固定增加":"")+statName(s)+" "+signed(a.value*(percent?100:1))+(percent||s==Stat.ATTACK_SPEED?"%":s==Stat.DROP_RATE?" 个百分点":"");
        }
        var definition=AffixDefinitions.current().affixes.get(a.id);
        return !a.base&&definition!=null&&!definition.name().equals(a.id)?definition.name()+"："+body:body;
    }
    public static String signed(double value){return (value<0?"":"+")+integer(value);}
    private static String equipmentRule(Affix a){String n=integer(a.value*100),v=integer(a.value);return switch(a.parameter){
        case "gem_amplification"->"本武器镶嵌宝石的数值效果提高 "+n+"%";
        case "fire_after_damage"->"造成伤害后，额外造成相当于该次伤害 "+n+"% 的火属性伤害（另算防御与火强化／火抗）";
        case "fire_conversion"->"保留火强化，其余元素强化按 "+n+"% 转为火强化";
        case "fire_only"->"无元素伤害转为火；只允许含火的元素伤害，混合元素仅保留火";
        case "block_elemental"->"无法造成带元素的伤害；无元素物理／魔法伤害保留";
        case "golden_power"->"有效击杀获得黄金之力：每层掉宝率+1，最多255层，20秒未触发清除";
        case "imprint_power"->"成功施放主动技能获得印记之力：每层伤害提高1%，最多18层，20秒未触发清除";
        case "hero_power"->"击败精英获得对应力量，持续 "+v+" 秒；同类刷新，不同类共存";
        case "magic_ranks"->"所有可用魔法技能等级 "+signed(a.value);
        case "magic_cooldown"->"魔法技能最终冷却时间减少 "+v+" 秒";
        case "grant_blink"->"授予主动技能：闪现（可在技能页面装配）";
        case "kill_recovery"->"击杀敌人回复最大生命的 "+n+"%";
        case "life_cost"->"技能以原魔耗的 "+n+"% 消耗生命，至少保留1点生命";
        case "inverse_resists"->"命中时将目标四种元素抗性视为相反数";
        case "resistance_cap"->"自身所有元素抗性的最终上限为 "+v;
        case "mana_share"->"受到伤害的 "+n+"% 由魔力承担（1MP抵1伤害，有效上限100%）";
        default->a.id;
    };}

}
