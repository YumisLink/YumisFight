package cn.yumis.fight.modules.skills.client;
import cn.yumis.fight.modules.skills.api.CareerSkills;
import cn.yumis.fight.modules.skills.api.CareerRules;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import cn.yumis.fight.modules.skills.api.client.SkillController;

import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import cn.yumis.fight.modules.attributes.api.Stat;

import cn.yumis.fight.modules.skills.api.SkillPreview;
import cn.yumis.fight.modules.skills.api.WeaponSkills;
import cn.yumis.fight.modules.skills.api.SkillDefinition;
import cn.yumis.fight.modules.skills.api.SkillCatalog;
import cn.yumis.fight.modules.skills.api.SkillLearning;

import cn.yumis.fight.modules.skills.api.SkillParameters;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import static cn.yumis.fight.modules.presentation.api.client.NumberText.number;

/** Rebuild while hovered so temporary buffs expire even when no profile packet arrives. */
public final class SkillTooltip {
    private SkillTooltip(){}
    private static String amount(SkillPreview.Damage value){
        return Math.abs(value.maximum()-value.minimum())<.05?number(value.minimum()):number(value.minimum())+"–"+number(value.maximum());
    }
    public static List<Component> lines(SkillDefinition skill){
        var state=SkillsClient.current();
        var stats=SkillController.INSTANCE.snapshot();
        int rank=SkillLearning.effective(stats,state.progress(),skill);
        double castSpeed=Math.max(.01,stats.get(Stat.CAST_SPEED));
        var parameters=cn.yumis.fight.modules.skills.api.WeaponSkills.skill(SkillParameters.forCast(Minecraft.getInstance().player.getUUID(),skill,Math.max(1,rank),stats.parameters),state.progress(),stats);
        if(CareerSkills.contains(skill.id)){var view=CareerClient.INSTANCE.view;parameters=CareerRules.resourceSnapshot(parameters,Math.max(1,view.brands),view.blood,view.field==null?0:view.field.rank,false);}
        var preview=SkillPreview.resolve(state.progress(),stats,skill,parameters,state.resources().equipment);
        List<Component> result=new ArrayList<>();
        add(result,skill.title+" · "+(rank>0?"有效等级 "+rank:"未学习 · 1级预览"),UiTheme.GOLD);
        add(result,(skill.equipmentGranted?"装备授予，无需SP · ":"")+"学习等级 "+skill.unlock+(skill.advanced()?" · "+cn.yumis.fight.modules.character.api.JobCatalog.require(skill.requiredJob).title():"")+" · 每级 "+skill.sp+" SP",UiTheme.MUTED);
        if(skill==SkillCatalog.MASTERY){
            double power=Math.pow(1.12,Math.max(1,rank)-1);
            add(result,"随当前武器生效（新增效果不随等级成长）：",UiTheme.TEXT);
            add(result,"光剑普攻2×75%；巨剑90°横扫6格、150%伤害，最终攻速减半。",UiTheme.TEXT);
            add(result,"三段斩：太刀/光剑7段，其余5段；光剑猛龙+1段。",UiTheme.TEXT);
            add(result,"太刀/光剑幻影20刀，持续1.25秒。",UiTheme.TEXT);
            add(result,"太刀：10%造成流血；流血目标5%触发 "+number(2*power)+"倍伤害。",UiTheme.TEXT);
            add(result,"光剑：20%造成感电；命中感电目标附加 "+number((10+2*state.progress().level)*power)+" 点基础光伤害。",UiTheme.TEXT);
            add(result,"巨剑：技能伤害 +"+number(20*power)+"%。",UiTheme.TEXT);
            add(result,"短剑：普攻、三段斩、拔刀斩与剑舞追加 "+number(20*power)+"%系数剑气。",UiTheme.TEXT);
            add(result,"钝器：5%眩晕 "+number(.5*power)+"秒，精英和首领减半。",UiTheme.TEXT);
        }else{
            var costs=cn.yumis.fight.modules.skills.api.SkillCosts.resolve(skill,Math.max(1,rank),stats,state.resources().equipment,CareerRules.magic(skill,state.progress()));
            add(result,(costs.life()>0?"生命 "+number(costs.life()):"MP "+costs.mana())+" · 冷却 "+number(preview.cooldown())+"秒",UiTheme.TEXT);
            if(skill==SkillCatalog.OVERDRIVE){
                add(result,"持续30秒，基础物理攻击 +"+number(15*Math.pow(1.12,Math.max(1,rank)-1))+"%。",UiTheme.TEXT);
            }else{
                add(result,"面板伤害："+amount(preview.hit())+" × "+preview.slashes()+"段",UiTheme.TEXT);
                if(preview.finisher().maximum()>0)add(result,"终结伤害："+amount(preview.finisher())+" × 1段",UiTheme.TEXT);
                if(skill==SkillCatalog.GHOST){int soul=SkillLearning.effective(stats,state.progress(),SkillCatalog.require(CareerSkills.SOUL_SLASH));add(result,"满蓄力单段："+amount(SkillPreview.damage(state.progress(),stats,skill,parameters.get("damage",0)*2+(soul>0?2*CareerSkills.growth(soul):0),false,state.resources().equipment)),UiTheme.TEXT);}
                else {add(result,"主技能全段命中："+amount(preview.total()),UiTheme.ACCENT);if(skill.id.equals(CareerSkills.CRESCENT))add(result,"满蓄力三段总计："+amount(preview.total().times(3)),UiTheme.TEXT);}
                if(preview.shortSwordWave().maximum()>0)add(result,"每次短剑追加剑气："+amount(preview.shortSwordWave()),UiTheme.TEXT);
                add(result,"零防御、零抗性、非暴击；随机附伤显示区间。",UiTheme.MUTED);
            }
            add(result,cn.yumis.fight.modules.skills.client.SkillDescriptions.describe(skill,new cn.yumis.fight.modules.skills.client.SkillDescriptions.Context(castSpeed,parameters,preview)),UiTheme.MUTED);
            if(skill.id.equals(CareerSkills.ORB)||skill.id.equals(CareerSkills.AGNI))add(result,"当前刻印 "+CareerClient.INSTANCE.view.brands+" / 5；零印时按1印预览，无法施放。",UiTheme.ACCENT);
            var content=CareerSkills.content(skill.id);if(content!=null&&content.hpCost()>0)add(result,"生命消耗：最大生命的"+number(content.hpCost()*100)+"%；不足支付时不施放。",UiTheme.TEXT);
        }
        add(result,skill.passive()?"被动技能，无需绑定。":rank<=0?"学习后点击此行选择快捷槽。":"点击此行选择快捷槽。",UiTheme.ACCENT);
        return result;
    }
    private static void add(List<Component> lines,String text,int color){
        lines.add(UiFonts.decorate(Component.literal(text).withColor(UiTheme.tooltipColor(color))));
    }
    public static void render(GuiGraphicsExtractor graphics,SkillDefinition skill,int mouseX,int mouseY){
        graphics.setComponentTooltipForNextFrame(UiFonts.font(),lines(skill),mouseX,mouseY,ItemStack.EMPTY);
    }
}
