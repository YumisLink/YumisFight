package cn.yumis.fight.client;

import cn.yumis.fight.api.SkillParameters;
import cn.yumis.fight.rules.SkillPreview;
import cn.yumis.fight.rules.Rules.Skill;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import static cn.yumis.fight.client.FightScreen.number;

/** Rebuild while hovered so temporary buffs expire even when no profile packet arrives. */
public final class SkillTooltip {
    private SkillTooltip(){}
    private static String amount(SkillPreview.Damage value){
        return Math.abs(value.maximum()-value.minimum())<.05?number(value.minimum()):number(value.minimum())+"–"+number(value.maximum());
    }
    public static List<Component> lines(Skill skill){
        var state=ClientState.INSTANCE;
        var stats=ClientCombat.INSTANCE.snapshot();
        int rank=stats.effective(state.progress,skill);
        var parameters=cn.yumis.fight.rules.WeaponRules.skill(SkillParameters.forCast(Minecraft.getInstance().player.getUUID(),skill,Math.max(1,rank),stats.parameters),state.progress,stats);
        var preview=SkillPreview.resolve(state.progress,stats,skill,parameters);
        List<Component> result=new ArrayList<>();
        add(result,skill.title+" · "+(rank>0?"有效等级 "+rank:"未学习 · 1级预览"),UiTheme.GOLD);
        add(result,"学习等级 "+skill.unlock+(skill.advanced()?" · 剑魂":"")+" · 每级 "+skill.sp+" SP",UiTheme.MUTED);
        if(skill==Skill.MASTERY){
            double power=Math.pow(1.12,Math.max(1,rank)-1);
            add(result,"随当前武器生效（新增效果不随等级成长）：",UiTheme.TEXT);
            add(result,"光剑普攻2×75%；巨剑90°横扫6格、150%伤害，最终攻速减半。",UiTheme.TEXT);
            add(result,"三段斩：太刀/光剑7段，其余5段；光剑猛龙+1段。",UiTheme.TEXT);
            add(result,"太刀/光剑幻影20刀，持续1.25秒。",UiTheme.TEXT);
            add(result,"太刀：10%造成流血；流血目标5%触发 "+number(2*power)+"倍伤害。",UiTheme.TEXT);
            add(result,"光剑：20%造成感电；命中感电目标附加 "+number((10+2*state.progress.level)*power)+" 点基础光伤害。",UiTheme.TEXT);
            add(result,"巨剑：技能伤害 +"+number(20*power)+"%。",UiTheme.TEXT);
            add(result,"短剑：普攻、三段斩、拔刀斩与剑舞追加 "+number(20*power)+"%系数剑气。",UiTheme.TEXT);
            add(result,"钝器：5%眩晕 "+number(.5*power)+"秒，精英和首领减半。",UiTheme.TEXT);
        }else{
            add(result,"MP "+preview.mp()+" · 冷却 "+number(preview.cooldown())+"秒",UiTheme.TEXT);
            if(skill==Skill.OVERDRIVE){
                add(result,"持续30秒，基础物理攻击 +"+number(15*Math.pow(1.12,Math.max(1,rank)-1))+"%。",UiTheme.TEXT);
            }else{
                add(result,"面板伤害："+amount(preview.hit())+" × "+preview.slashes()+"段",UiTheme.TEXT);
                if(preview.finisher().maximum()>0)add(result,"终结伤害："+amount(preview.finisher())+" × 1段",UiTheme.TEXT);
                if(skill==Skill.GHOST)add(result,"满蓄力单段："+amount(SkillPreview.damage(state.progress,stats,skill,parameters.get("damage",0)*2,false)),UiTheme.TEXT);
                else add(result,"主技能全段命中："+amount(preview.total()),UiTheme.ACCENT);
                if(preview.shortSwordWave().maximum()>0)add(result,"每次短剑追加剑气："+amount(preview.shortSwordWave()),UiTheme.TEXT);
                add(result,"零防御、零抗性、非暴击；随机附伤显示区间。",UiTheme.MUTED);
            }
            add(result,switch(skill){
                case GHOST->"按住蓄力1秒达到双倍系数，松开释放；满蓄后最多持有2秒。";
                case TRIPLE->"每段冲刺2.5格，窗口内再次按原槽续段，最多"+preview.slashes()+"段。";
                case WAVE->"地面波前进"+number(parameters.get("range",10))+"格，逐目标命中一次，遇实墙停止。";
                case OVERDRIVE->"立即获得攻击增益，不造成直接伤害。";
                case RISING->"冲撞4格后上挑；动作与结束后0.5秒提供位移防护。";
                case DRAW->"蓄势0.4秒，前方180°、半径"+number(parameters.get("range",5)*parameters.get("meleeScale",1))+"格，实墙遮挡。";
                case DRAGON->"每段冲刺4格，窗口内按原槽续段，最多"+preview.slashes()+"段。";
                case DANCE->number(parameters.get("duration",1.25))+"秒内完成全部斩击，再发出穿透终结剑气。";
                case STORM->number(parameters.get("duration",8))+"秒剑阵，包含斩击及终结，全程免伤。";
                default->"";
            },UiTheme.MUTED);
        }
        add(result,skill.passive()?"被动技能，无需绑定。":rank<=0?"学习后点击此行选择快捷槽。":"点击此行选择快捷槽。",UiTheme.ACCENT);
        return result;
    }
    private static void add(List<Component> lines,String text,int color){
        lines.add(UiFonts.decorate(Component.literal(text).withColor(UiTheme.tooltipColor(color))));
    }
    public static void render(GuiGraphicsExtractor graphics,Skill skill,int mouseX,int mouseY){
        graphics.setComponentTooltipForNextFrame(UiFonts.font(),lines(skill),mouseX,mouseY,ItemStack.EMPTY);
    }
}
