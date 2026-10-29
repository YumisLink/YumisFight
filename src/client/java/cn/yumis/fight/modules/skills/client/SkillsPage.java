package cn.yumis.fight.modules.skills.client;
import cn.yumis.fight.modules.skills.api.SkillLearning;
import cn.yumis.fight.modules.skills.api.SkillCatalog;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.presentation.api.client.PlayerPageSurface;
import cn.yumis.fight.modules.presentation.api.client.ThemedButton;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import net.minecraft.client.Minecraft;
import java.util.function.Consumer;

/** Skill page content; the menu shell has no skill-specific dispatch or tooltip state. */
public final class SkillsPage {
    private SkillsPage(){}
    public static void render(PlayerPageSurface page,Progress progress,Stats stats,Consumer<String> learn,Consumer<String> unlearn,Runnable reset){
        page.cursor(page.cursor()+10);
        page.line("剩余SP："+progress.sp+"    装备可提高有效等级",UiTheme.GOLD);
        if(!page.hasSidebar())page.line("职业减伤："+Math.round((1-cn.yumis.fight.modules.character.api.JobCatalog.style(progress.jobId()).damageTaken())*100)+"%");
        page.line("点击图标绑定；右键退1级，Shift右键退5级并返还SP。",UiTheme.MUTED);
        page.line("Shift学习最多5级；卡片显示学习等级，有效等级见悬浮详情。",UiTheme.MUTED);
        int base=page.cursor()+6,cell=(page.contentWidth()-24)/4,index=0;
        for(var skill:SkillCatalog.all()){
            if(skill.advanced()&&!progress.hasJob(skill.requiredJob))continue;
            if(skill.equipmentGranted&&SkillLearning.effective(stats,progress,skill)<=0&&!java.util.Arrays.asList(progress.slots).contains(skill.id))continue;
            int x=(index%4)*cell,y=base+(index/4)*108,w=cell-5;index++;
            page.card(x,y,w,102);
            var row=page.button("",x+3,y+3,w-6,()->{var state=cn.yumis.fight.modules.skills.api.client.SkillsClient.current();if(SkillLearning.canBind(state.stats(),state.progress(),skill))Minecraft.getInstance().gui.pushScreenLayer(new SkillBindingScreen(skill));});row.setHeight(66);
            page.icon("textures/gui/skills/"+skill.icon()+".png",x+(w-26)/2,y+7,26);
            String title=cn.yumis.fight.modules.presentation.api.client.UiFonts.font().plainSubstrByWidth(skill.title,Math.max(1,w-10));
            page.text(title,x+5,y+38,UiTheme.TEXT);
            page.text(skill.equipmentGranted?"装备授予":progress.learned(skill.id)+"级",x+5,y+53,UiTheme.MUTED);
            var buy=page.button(skill.equipmentGranted?"装备授予":"学习",x+4,y+76,w-8,()->learn.accept(skill.id));buy.active=!skill.equipmentGranted;
            page.hover(x,y,w,102,(g,mx,my)->SkillTooltip.render(g,skill,mx,my));
            page.rightClick(x,y,w,102,()->{if(!skill.equipmentGranted&&cn.yumis.fight.modules.skills.api.client.SkillsClient.current().progress().learned(skill.id)>0)unlearn.accept(skill.id);});
        }
        page.cursor(base+((index+3)/4)*108);page.gap();page.rowButton("洗点并返还技能SP",reset);
    }
}
