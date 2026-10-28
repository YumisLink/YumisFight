package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;
import cn.yumis.fight.modules.character.api.*;
import cn.yumis.fight.modules.presentation.api.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Read-only class preview; opening it never changes the player's job, SP or bindings. */
public final class CareerPreviewScreen extends Screen {
    private final String job;
    private final List<SkillDefinition> skills;
    private int left,top,panelWidth,panelHeight,page,rows;
    public CareerPreviewScreen(String job){super(Minecraft.getInstance(),UiFonts.font(),Component.literal(JobCatalog.require(job).title()+" · 技能预览"));this.job=job;skills=SkillCatalog.all().stream().filter(s->!s.advanced()||s.requiredJob.equals(job)).toList();}
    @Override protected void init(){rebuild();}
    private void rebuild(){
        clearWidgets();panelWidth=Math.min(520,width-20);panelHeight=Math.min(340,height-20);left=(width-panelWidth)/2;top=(height-panelHeight)/2;rows=Math.max(1,(panelHeight-78)/23);page=Math.clamp(page,0,(skills.size()-1)/rows);
        for(int i=0;i<rows&&page*rows+i<skills.size();i++){var skill=skills.get(page*rows+i);addRenderableWidget(Button.builder(Component.literal("Lv."+skill.unlock+"  "+skill.title+(skill.passive()?" · 被动":"  MP "+skill.mp(1)+" / "+skill.cooldown+"秒")),b->{}).bounds(left+12,top+44+i*23,panelWidth-24,20).build(ThemedButton::new));}
        addRenderableWidget(Button.builder(Component.literal("上一页"),b->{page--;rebuild();}).bounds(left+12,top+panelHeight-27,62,19).build(ThemedButton::new)).active=page>0;
        addRenderableWidget(Button.builder(Component.literal("下一页"),b->{page++;rebuild();}).bounds(left+80,top+panelHeight-27,62,19).build(ThemedButton::new)).active=(page+1)*rows<skills.size();
        addRenderableWidget(Button.builder(Component.literal("返回"),b->onClose()).bounds(left+panelWidth-76,top+panelHeight-27,64,19).build(ThemedButton::new));
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float delta){g.fill(0,0,width,height,UiTheme.OVERLAY);UiTheme.panel(g,left,top,panelWidth,panelHeight);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.text(font,title,left+12,top+11,UiTheme.GOLD,false);g.text(font,"一级技能 · 悬浮查看效果 · 职业防护 "+Math.round((1-JobCatalog.style(job).damageTaken())*100)+"%",left+12,top+27,UiTheme.MUTED,false);super.extractRenderState(g,mx,my,delta);
        for(int i=0;i<rows&&page*rows+i<skills.size();i++){int y=top+44+i*23;if(mx<left+12||mx>=left+panelWidth-12||my<y||my>=y+20)continue;var skill=skills.get(page*rows+i);var parameters=SkillParameters.resolve(skill,1,List.of());var p=new Progress();p.promote(job);p.skills.put(skill.id,1);var preview=SkillPreview.resolve(p,SkillsClient.current().stats(),skill,CareerRules.resourceSnapshot(parameters,5,10,0,false),SkillsClient.current().resources().equipment);String description=SkillDescriptions.describe(skill,new SkillDescriptions.Context(1,parameters,preview));g.setComponentTooltipForNextFrame(UiFonts.font(),List.of(Component.literal(skill.title).withColor(UiTheme.GOLD),Component.literal(description),Component.literal("每级 "+skill.sp+" SP；学习上限 "+skill.cap+"；伤害每级成长12%。").withColor(UiTheme.MUTED)),mx,my,net.minecraft.world.item.ItemStack.EMPTY);}
    }
    @Override public void onClose(){minecraft.gui.popScreenLayer();}
    @Override public boolean isPauseScreen(){return false;}
}
