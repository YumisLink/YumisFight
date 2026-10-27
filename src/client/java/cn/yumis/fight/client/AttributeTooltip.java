package cn.yumis.fight.client;

import cn.yumis.fight.rules.*;
import static cn.yumis.fight.rules.Rules.*;
import net.minecraft.network.chat.*;
import java.util.*;
import java.util.regex.*;

public final class AttributeTooltip {
    private static final Pattern NUMBERS=Pattern.compile("[+−-]?\\d+(?:\\.\\d+)?%?");
    private AttributeTooltip(){}
    public static Component numbers(String text){return numbers(text,UiTheme.NUMERIC);}
    public static Component numbers(String text,int color){
        MutableComponent result=Component.empty();Matcher matcher=NUMBERS.matcher(text);int start=0;
        while(matcher.find()){result.append(Component.literal(text.substring(start,matcher.start())));result.append(Component.literal(matcher.group()).withStyle(style->style.withColor(color).withBold(true)));start=matcher.end();}
        return result.append(Component.literal(text.substring(start)));
    }
    public static List<Component> lines(String name){
        var state=ClientState.INSTANCE;var s=ClientCombat.INSTANCE.snapshot();Stat stat=null;
        for(Stat candidate:Stat.values())if(FightScreen.statName(candidate).equals(name))stat=candidate;
        String detail;
        if(stat==null){detail=name.equals("闪避率")?"闪避率＝闪避值÷(4000＋闪避值)，仅躲避敌方直接攻击。当前 "+FightScreen.number(100*CombatRules.evasionChance(s.get(Stat.EVASION)))+"%。":"面板攻击＝基础攻击×(1＋对应力量或智力÷100)。当前 "+FightScreen.number(s.panel(name.equals("魔法面板攻击")))+"。";}
        else detail=switch(stat){
            case HEALTH->"可承受的生命伤害上限。脱战后每秒恢复2%，当前每秒 "+FightScreen.number(s.get(stat)*.02)+"。";
            case MP->"施放技能使用的魔力上限；精神每点额外提供2点MP。";
            case STRENGTH,INTELLIGENCE->"每点增加对应基础攻击的1%。当前面板倍率 "+FightScreen.number(1+s.get(stat)/100)+"倍。";
            case SPIRIT->"每点提供2点MP、2点魔防和每秒0.04点MP恢复。当前每秒基础回魔 "+FightScreen.number(1+.04*s.get(stat))+"，脱战乘3。";
            case PHYSICAL_ATTACK,MAGICAL_ATTACK->"用于计算对应普攻与技能伤害，再乘(1＋力量或智力÷100)。当前面板攻击 "+FightScreen.number(s.panel(stat==Stat.MAGICAL_ATTACK))+"。";
            case PHYSICAL_DEFENSE,MAGICAL_DEFENSE->"对应伤害乘1000÷(1000＋防御)。当前减伤 "+FightScreen.number(100*s.get(stat)/(1000+s.get(stat)))+"%，再计算职业和其他效果。";
            case LIGHT,FIRE,ICE,DARK->"对应属性伤害倍率＝max(0，1＋(强化−目标抗性)÷120)。对0抗性目标当前倍率 "+FightScreen.number(Math.max(0,1+s.get(stat)/120))+"倍。";
            case LIGHT_RESIST,FIRE_RESIST,ICE_RESIST,DARK_RESIST->"降低对应属性伤害。倍率＝max(0，1＋(攻击者强化−本属性抗性)÷120)；每120点抵消1倍基础属性伤害。";
            case DROP_RATE->"提高高稀有度装备和通货的抽取权重，使用基数1000；不直接增加基础掉落次数。当前 "+FightScreen.number(s.get(stat))+"。";
            case SHIELD->"装备护盾先吸收伤害，受伤后5秒开始每秒恢复20%。当前每秒恢复 "+FightScreen.number(s.get(stat)*.2)+"。";
            case EVASION->"闪避率＝闪避值÷(4000＋闪避值)。当前 "+FightScreen.number(100*CombatRules.evasionChance(s.get(stat)))+"%；成功免除直接攻击及附带效果，持续伤害和环境伤害不参与。";
            case ATTACK_SPEED->"只影响普通攻击。间隔＝650毫秒÷(1＋总攻速÷100)。当前 "+FightScreen.number(WeaponRules.interval(state.progress,s))+"毫秒；剑魂巨剑横扫最终速度减半已计入。";
        };
        return List.of(Component.literal(name).withStyle(style->style.withColor(UiTheme.TIP_TITLE)),numbers(detail,UiTheme.TIP_NUMBER));
    }
}
