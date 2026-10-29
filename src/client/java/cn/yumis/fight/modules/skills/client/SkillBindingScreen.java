package cn.yumis.fight.modules.skills.client;
import cn.yumis.fight.modules.presentation.api.client.*;
import cn.yumis.fight.modules.skills.api.client.SkillsClient;

import cn.yumis.fight.modules.presentation.api.client.ThemedButton;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;

import cn.yumis.fight.modules.skills.api.SkillDefinition;
import cn.yumis.fight.modules.skills.api.SkillCatalog;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import java.util.Arrays;
import net.minecraft.network.chat.Component;

/** A modal layer: opening and closing it preserves the skill list's scroll position. */
public final class SkillBindingScreen extends ThemedScreen {
    private final SkillDefinition skill;
    private int layer=-2,left,top,panelWidth,panelHeight,columns,cellWidth;
    private String bindingState="";
    private boolean available;
    private boolean available(){var state=SkillsClient.current();return cn.yumis.fight.modules.skills.api.SkillLearning.canBind(state.stats(),state.progress(),skill);}
    public SkillBindingScreen(SkillDefinition skill){
        super(Minecraft.getInstance(),UiFonts.font(),Component.literal("选择快捷槽 · "+skill.title),ThemeDomain.MENU);
        this.skill=skill;
    }
    private int currentLayer(){
        boolean ctrl=minecraft.hasControlDown(),alt=minecraft.hasAltDown();
        return ctrl&&alt?-1:ctrl?1:alt?2:0;
    }
    @Override protected void init(){rebuild();}
    private void rebuild(){
        clearWidgets();
        layer=currentLayer();
        bindingState=Arrays.toString(SkillsClient.current().progress().slots);
        available=available();
        panelWidth=Math.min(540,width-16);
        columns=panelWidth>=420||height<310?4:2;
        cellWidth=(panelWidth-24)/columns;
        boolean compact=height<210;
        int rowHeight=compact?44:54,buttonHeight=compact?25:31,clearHeight=compact?14:17;
        panelHeight=(compact?72:82)+(8/columns)*rowHeight;
        left=(width-panelWidth)/2;top=(height-panelHeight)/2;
        for(int i=0;i<8;i++){
            final int slot=Math.max(0,layer)*8+i;
            String binding=SkillsClient.current().progress().slots[slot];
            String key=cn.yumis.fight.modules.skills.api.client.SkillInputs.SKILLS[i].getTranslatedKeyMessage().getString();
            int x=left+12+(i%columns)*cellWidth,y=top+43+(i/columns)*rowHeight;
            var button=Button.builder(Component.literal(key+" · "+(binding.isEmpty()?"空槽":SkillCatalog.contains(binding)?SkillCatalog.require(binding).title:"内容未加载")),b->bind(slot,skill.id))
                .bounds(x,y,cellWidth-6,buttonHeight).build(ThemedButton::new);
            ((ThemedButton)button).selected=binding.equals(skill.id);
            button.active=layer>=0&&available;
            addRenderableWidget(button);
            if(!binding.isEmpty()){
                var clear=Button.builder(Component.literal("清空"),b->bind(slot,""))
                    .bounds(x+cellWidth-48,y+buttonHeight+2,42,clearHeight).build(ThemedButton::new);
                clear.active=layer>=0;
                addRenderableWidget(clear);
            }
        }
        addRenderableWidget(Button.builder(Component.literal("取消"),b->onClose()).bounds(left+panelWidth-64,top+panelHeight-27,52,20).build(ThemedButton::new));
    }
    private void bind(int slot,String value){
        if(currentLayer()<0||currentLayer()!=layer||!value.isEmpty()&&!available())return;
        var state=SkillsClient.current();
        state.upload();
        var request=state.operation("bind");
        request.addProperty("slot",slot);request.addProperty("skill",value);
        state.send("progress",request);
        onClose();
    }
    @Override public void tick(){
        if(layer!=currentLayer()||available!=available()||!bindingState.equals(Arrays.toString(SkillsClient.current().progress().slots)))rebuild();
    }
    @Override protected void drawBackground(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(0,0,width,height,UiTheme.OVERLAY);
        UiTheme.panel(g,left,top,(left+panelWidth)-(left),(top+panelHeight)-(top));
    }
    @Override protected void drawContents(GuiGraphicsExtractor g,int mx,int my,float delta){
        if(layer!=currentLayer())rebuild();
        g.text(font,title,left+12,top+10,UiTheme.GOLD,false);
        g.text(font,layer<0?"Ctrl 与 Alt 同时按下：不可绑定":new String[]{"普通层","Ctrl 层","Alt 层"}[layer]+" · 按住 Ctrl / Alt 切换",left+12,top+26,UiTheme.MUTED,false);
        super.drawContents(g,mx,my,delta);
        g.text(font,"点击替换 · Esc 取消",left+12,top+panelHeight-21,UiTheme.MUTED,false);
    }
    @Override public void onClose(){minecraft.gui.popScreenLayer();}
    @Override public boolean isPauseScreen(){return false;}
}
