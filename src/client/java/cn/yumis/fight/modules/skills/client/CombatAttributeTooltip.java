package cn.yumis.fight.modules.skills.client;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.attributes.api.client.AttributeLabels;
import cn.yumis.fight.modules.combat.api.CombatBalanceRules;
import cn.yumis.fight.modules.combat.api.CombatRules;
import cn.yumis.fight.modules.combat.api.EquipmentDamage;
import cn.yumis.fight.modules.presentation.api.client.NumberText;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import cn.yumis.fight.modules.skills.api.client.SkillController;

import net.minecraft.network.chat.*;
import java.util.*;
import java.util.regex.*;

public final class CombatAttributeTooltip {
    private static final Pattern NUMBERS=Pattern.compile("[+−-]?\\d+(?:\\.\\d+)?%?");
    private CombatAttributeTooltip(){}
    public static Component numbers(String text){return numbers(text,UiTheme.NUMERIC);}
    public static Component numbers(String text,int color){
        MutableComponent result=Component.empty();Matcher matcher=NUMBERS.matcher(text);int start=0;
        while(matcher.find()){result.append(Component.literal(text.substring(start,matcher.start())));result.append(Component.literal(matcher.group()).withStyle(style->style.withColor(color).withBold(true)));start=matcher.end();}
        return result.append(Component.literal(text.substring(start)));
    }
    public static List<Component> lines(String name){
        return lines(name,SkillController.INSTANCE.snapshot());
    }
    public static List<Component> lines(String name,Stats s){
        Stat stat=null;
        for(Stat candidate:Stat.values())if(AttributeLabels.statName(candidate).equals(name))stat=candidate;
        String detail;
        if(stat==null){
            detail=switch(name){
                case "吸血率"->"造成伤害时回复生命的比例。";
                case "闪避率"->"闪避攻击的概率。";
                case "暴击率"->"攻击触发暴击的概率。";
                case "伤害提升"->"提高造成的伤害。";
                case "物理面板攻击"->"最终物理攻击力。";
                case "魔法面板攻击"->"最终魔法攻击力。";
                default->"最终攻击力。";
            };
        }else{
            detail=switch(stat){
                case HEALTH->"最大生命值。";
                case MP->"释放技能消耗的魔力上限。";
                case STRENGTH->"提高物理攻击力。";
                case INTELLIGENCE->"提高魔法攻击力。";
                case VITALITY->"提高生命、物防、魔力、体力、体力恢复与攻击速度。";
                case SPIRIT->"提高魔防、回魔、冷却恢复与施法速度。";
                case PHYSICAL_ATTACK->"基础物理攻击力，受力量加成。";
                case MAGICAL_ATTACK->"基础魔法攻击力，受智力加成。";
                case PHYSICAL_DEFENSE->"降低受到的物理伤害，当前减伤 "+NumberText.number(defenseReductionPercent(s.get(stat)))+"%。";
                case MAGICAL_DEFENSE->"降低受到的魔法伤害，当前减伤 "+NumberText.number(defenseReductionPercent(s.get(stat)))+"%。";
                case LIGHT->"提高光属性伤害。";
                case FIRE->"提高火属性伤害。";
                case ICE->"提高冰属性伤害。";
                case DARK->"提高暗属性伤害。";
                case LIGHT_RESIST->"降低受到的光属性伤害。";
                case FIRE_RESIST->"降低受到的火属性伤害。";
                case ICE_RESIST->"降低受到的冰属性伤害。";
                case DARK_RESIST->"降低受到的暗属性伤害。";
                case DROP_RATE->"提高稀有装备与通货的掉落概率。";
                case SHIELD->"优先吸收伤害，受击后自动恢复。";
                case EVASION->"提高闪避率，当前 "+NumberText.number(100*CombatRules.evasionChance(s.get(stat)))+"%。";
                case ATTACK_SPEED->"加快普通攻击与出手速度。";
                case MOVEMENT_SPEED->"提高移动速度。";
                case CAST_SPEED->"加快技能释放与咏唱速度。";
                case LIFE_STEAL->"提高吸血率，当前 "+NumberText.ratePercent(EquipmentDamage.lifeSteal(s.get(stat)))+"。";
                case MANA_REGENERATION->"提高魔力恢复速度。";
                case COOLDOWN_RECOVERY->"缩短技能冷却时间。";
                case EXPERIENCE_GAIN->"提高战斗经验获取。";
                case STAMINA->"翻滚消耗的体力上限。";
                case STAMINA_RECOVERY->"提高体力恢复速度。";
                case SHIELD_COOLDOWN_RECOVERY->"缩短护盾开始恢复的等待时间。";
                case SHIELD_REGENERATION->"提高护盾恢复速度。";
                case HEALTH_REGENERATION->"提高脱战生命恢复速度。";
            };
        }
        return List.of(Component.literal(name).withStyle(style->style.withColor(UiTheme.TIP_TITLE)),numbers(detail,UiTheme.TIP_NUMBER));
    }
    private static double defenseReductionPercent(double defense){
        double d=Math.max(0,defense),divisor=CombatBalanceRules.current().combat.defenseDivisor();
        return 100*d/(divisor+d);
    }
}
