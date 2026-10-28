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
    public static void render(PlayerPageSurface page,Progress progress,Stats stats,Consumer<String> learn,Runnable reset){
        page.line("剩余SP："+progress.sp+"    装备可提高有效等级",UiTheme.GOLD);
        page.line("悬浮查看实时技能介绍；点击已学主动技能选择快捷槽。",UiTheme.MUTED);
        page.line("按住 Shift 点击学习：最多升5级；点数不足时尽量加满。",UiTheme.MUTED);
        for(var skill:SkillCatalog.all()){
            if(skill.advanced()&&!progress.hasJob(skill.requiredJob))continue;
            if(skill.equipmentGranted&&SkillLearning.effective(stats,progress,skill)<=0&&!java.util.Arrays.asList(progress.slots).contains(skill.id))continue;
            int rank=progress.learned(skill.id),effective=SkillLearning.effective(stats,progress,skill),limit=skill.limit(progress.level,progress.jobId());
            var row=page.button(skill.title+" "+rank+"/"+limit+(effective!=rank?" ["+effective+"]":""),0,page.cursor(),page.contentWidth()-142,()->{if(!skill.passive()&&SkillLearning.effective(stats,progress,skill)>0)Minecraft.getInstance().gui.pushScreenLayer(new SkillBindingScreen(skill));});
            ((ThemedButton)row).textInset=24;
            page.hover(row,(g,x,y)->SkillTooltip.render(g,skill,x,y));
            var buy=page.button(skill.equipmentGranted?"装备授予":"学习 "+skill.sp,page.contentWidth()-128,page.cursor(),104,()->learn.accept(skill.id));
            buy.active=!skill.equipmentGranted;
            page.hover(buy,(g,x,y)->SkillTooltip.render(g,skill,x,y));
            page.icon("textures/gui/skills/"+skill.icon()+".png",4,page.cursor()+3,14);page.cursor(page.cursor()+24);
        }
        page.gap();page.rowButton("洗点并返还技能SP",reset);
    }
}
