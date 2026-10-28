package cn.yumis.fight.modules.skills.client;
import cn.yumis.fight.modules.skills.api.client.SkillController;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.combat.api.CombatRules;
import cn.yumis.fight.modules.skills.api.WeaponSkills;

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
        var state=cn.yumis.fight.modules.skills.api.client.SkillsClient.current();var s=SkillController.INSTANCE.snapshot();Stat stat=null;
        for(Stat candidate:Stat.values())if(cn.yumis.fight.modules.attributes.api.client.AttributeLabels.statName(candidate).equals(name))stat=candidate;
        String detail;
        if(stat==null){detail=switch(name){case "吸血率"->"吸血值以1000为基数换算；超过1000/19的部分按1/3效率折算，最高90%。仅按有效直接伤害实际扣除的生命回复，排除超杀、持续伤害和反伤。";case "闪避率"->"以10000为基数换算；超过30000的闪避值按1/3折算，75%后增速降低，最高90%。当前 "+cn.yumis.fight.modules.presentation.api.client.NumberText.number(100*CombatRules.evasionChance(s.get(Stat.EVASION)))+"%。";case "暴击率"->"直接暴击概率，最高100%；通常暴击造成1.5倍伤害。";case "伤害提升"->"提升与最终提升统一进入伤害MUT，同类加算；总增独立乘算。";default->"面板攻击＝基础攻击×(1＋对应力量或智力÷100)。当前 "+cn.yumis.fight.modules.presentation.api.client.NumberText.number(s.panel(name.equals("魔法面板攻击")))+"。";};}
        else detail=switch(stat){
            case HEALTH->"可承受的生命伤害上限。脱战后每秒恢复2%，当前每秒 "+cn.yumis.fight.modules.presentation.api.client.NumberText.number(s.get(stat)*.02)+"。";
            case MP->"施放技能使用的魔力上限；精神每点额外提供2点MP。";
            case STRENGTH,INTELLIGENCE->"每点增加对应基础攻击的1%。当前面板倍率 "+cn.yumis.fight.modules.presentation.api.client.NumberText.number(1+s.get(stat)/100)+"倍。";
            case SPIRIT->"每点提供2点MP、2点魔防和每秒0.04点MP恢复。当前每秒基础回魔 "+cn.yumis.fight.modules.presentation.api.client.NumberText.number(1+.04*s.get(stat))+"，脱战乘3。";
            case PHYSICAL_ATTACK,MAGICAL_ATTACK->"用于计算对应普攻与技能伤害，再乘(1＋力量或智力÷100)。当前面板攻击 "+cn.yumis.fight.modules.presentation.api.client.NumberText.number(s.panel(stat==Stat.MAGICAL_ATTACK))+"。";
            case PHYSICAL_DEFENSE,MAGICAL_DEFENSE->"对应伤害乘1000÷(1000＋防御)。当前减伤 "+cn.yumis.fight.modules.presentation.api.client.NumberText.number(100*s.get(stat)/(1000+s.get(stat)))+"%，再计算职业和其他效果。";
            case LIGHT,FIRE,ICE,DARK->"对应属性伤害倍率＝max(0，1＋(强化−目标抗性)÷120)。对0抗性目标当前倍率 "+cn.yumis.fight.modules.presentation.api.client.NumberText.number(Math.max(0,1+s.get(stat)/120))+"倍。";
            case LIGHT_RESIST,FIRE_RESIST,ICE_RESIST,DARK_RESIST->"降低对应属性伤害。倍率＝max(0，1＋(攻击者强化−本属性抗性)÷120)；每120点抵消1倍基础属性伤害。";
            case DROP_RATE->"提高高稀有度装备和通货的抽取权重，使用基数1000；不直接增加基础掉落次数。当前 "+cn.yumis.fight.modules.presentation.api.client.NumberText.number(s.get(stat))+"。";
            case SHIELD->"装备护盾先吸收伤害，受伤后5秒开始每秒恢复20%。当前每秒恢复 "+cn.yumis.fight.modules.presentation.api.client.NumberText.number(s.get(stat)*.2)+"。";
            case EVASION->"基数10000；30000闪避值达到75%，超出部分按1/3折算，210000达到90%上限。当前 "+cn.yumis.fight.modules.presentation.api.client.NumberText.number(100*CombatRules.evasionChance(s.get(stat)))+"%；持续伤害和环境伤害不参与。";
            case ATTACK_SPEED->"只影响普通攻击。间隔＝650毫秒÷(1＋总攻速÷100)。当前 "+cn.yumis.fight.modules.presentation.api.client.NumberText.number(WeaponSkills.interval(state.progress(),s))+"毫秒；剑魂巨剑横扫最终速度减半已计入。";
            case MOVEMENT_SPEED->"影响步行、奔跑与潜行。提高归入MUT；不改变游泳、飞行和技能冲刺距离。";
            case LIFE_STEAL->"造成有效直接伤害时按实际生命损失吸血。达到5%后新增数值按1/3折算，最高90%。当前吸血率 "+cn.yumis.fight.modules.presentation.api.client.NumberText.number(100*cn.yumis.fight.modules.combat.api.EquipmentDamage.lifeSteal(s.get(stat)))+"%。";
            case MANA_REGENERATION->"基础回魔(1＋0.04×精神)乘此倍率，脱战再乘3。";
            case COOLDOWN_RECOVERY->"技能基础冷却除以恢复速度倍率，最后再应用魔法技能固定减秒。";
            case EXPERIENCE_GAIN->"角色战斗经验乘此倍率；不影响副职业经验。";
            case CAST_SPEED->"技能动作、蓄力与连击时间除以此倍率；段数、位移总距离、冷却、BUFF和持续场时长不变。";
        };
        List<Component> lines=new ArrayList<>();lines.add(Component.literal(name).withStyle(style->style.withColor(UiTheme.TIP_TITLE)));lines.add(numbers(detail,UiTheme.TIP_NUMBER));
        if(stat!=null){var a=s.attribute(stat);lines.add(numbers("BASE "+cn.yumis.fight.modules.presentation.api.client.NumberText.number(a.base)+" · MUT "+cn.yumis.fight.modules.presentation.api.client.NumberText.number(a.mut*100)+"% · ADD "+cn.yumis.fight.modules.presentation.api.client.NumberText.number(a.add)+" · ALLS ×"+cn.yumis.fight.modules.presentation.api.client.NumberText.number(a.alls)+" · FADD "+cn.yumis.fight.modules.presentation.api.client.NumberText.number(a.fadd),UiTheme.TIP_NUMBER));}
        return lines;
    }
}
