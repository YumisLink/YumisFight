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
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.resources.Identifier;
import cn.yumis.fight.platform.ItemData;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import net.minecraft.network.chat.Component;

/** A modal layer: opening and closing it preserves the skill list's scroll position. */
public final class SkillBindingScreen extends ThemedScreen {
    private final SkillDefinition skill;
    private int layer=-2,left,top,panelWidth,panelHeight,cellWidth,slotY,slotLeft,square;
    private String bindingState="";
    private boolean available,dragging;
    private final double originX,originY;
    private final List<Button> slots=new ArrayList<>();
    private boolean available(){var state=SkillsClient.current();return cn.yumis.fight.modules.skills.api.SkillLearning.canBind(state.stats(),state.progress(),skill);}
    public SkillBindingScreen(SkillDefinition skill){
        super(Minecraft.getInstance(),UiFonts.font(),Component.literal("选择快捷槽 · "+skill.title),ThemeDomain.MENU);
        this.skill=skill;
        var mc=Minecraft.getInstance();originX=mc.mouseHandler.getScaledXPos(mc.getWindow());originY=mc.mouseHandler.getScaledYPos(mc.getWindow());
    }
    private int currentLayer(){
        boolean ctrl=minecraft.hasControlDown(),alt=minecraft.hasAltDown();
        return ctrl&&alt?-1:ctrl?1:alt?2:0;
    }
    @Override protected void init(){rebuild();}
    private void rebuild(){
        clearWidgets();slots.clear();
        layer=currentLayer();
        bindingState=Arrays.toString(SkillsClient.current().progress().slots);
        available=available();
        cellWidth=Math.max(16,Math.min(25,(width-20)/8));square=cellWidth-3;
        panelWidth=Math.min(width-12,Math.max(252,cellWidth*8+24));panelHeight=112;
        var player=minecraft.player;int extra=player==null?0:(player.isUnderWater()||player.getAirSupply()<player.getMaxAirSupply()?10:0)+(player.isPassenger()?10:0);
        slotY=Math.max(60,height-94-extra);slotLeft=(width-(cellWidth*8-3))/2;left=(width-panelWidth)/2;top=slotY-60;
        for(int i=0;i<8;i++){
            final int slot=Math.max(0,layer)*8+i;
            String binding=SkillsClient.current().progress().slots[slot];int x=slotLeft+i*cellWidth;String key=cn.yumis.fight.modules.skills.api.client.SkillInputs.SKILLS[i].getTranslatedKeyMessage().getString();
            var button=Button.builder(Component.empty(),b->bind(slot,skill.id)).createNarration(defaultMessage->Component.literal("将「"+skill.title+"」绑定到 "+key)).bounds(x,slotY,square,square).build(ThemedButton::new);
            ((ThemedButton)button).selected=binding.equals(skill.id);
            button.active=layer>=0&&available;
            addRenderableWidget(button);slots.add(button);
        }
        addRenderableWidget(Button.builder(Component.literal("取消"),b->onClose()).bounds(left+panelWidth-48,top+7,38,20).build(ThemedButton::new));
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
        g.fill(0,0,width,height,0x25000000);
        UiTheme.panel(g,left,top,(left+panelWidth)-(left),(top+panelHeight)-(top));
    }
    @Override protected void drawContents(GuiGraphicsExtractor g,int mx,int my,float delta){
        if(layer!=currentLayer())rebuild();
        g.text(font,font.plainSubstrByWidth(skill.title,panelWidth-68),left+12,top+11,UiTheme.GOLD,false);
        g.text(font,font.plainSubstrByWidth(available?"拖到下方快捷槽后松开 · 点击也可绑定":"当前技能不可绑定",panelWidth-24),left+12,top+32,UiTheme.MUTED,false);
        super.drawContents(g,mx,my,delta);
        for(int i=0;i<8;i++){
            var button=slots.get(i);String key=cn.yumis.fight.modules.skills.api.client.SkillInputs.SKILLS[i].getTranslatedKeyMessage().getString();key=font.plainSubstrByWidth(key,cellWidth-2);g.text(font,key,button.getX()+(square-font.width(key))/2,slotY-10,UiTheme.MUTED,false);
            String binding=SkillsClient.current().progress().slots[Math.max(0,layer)*8+i];if(!binding.isEmpty()&&SkillCatalog.contains(binding)){var content=SkillCatalog.require(binding);g.blit(Identifier.fromNamespaceAndPath(ItemData.NS,"textures/gui/skills/"+content.icon()+".png"),button.getX()+2,slotY+2,button.getX()+square-2,slotY+square-2,0,1,0,1);}
            if(!dragging&&layer>=0&&button.isMouseOver(mx,my))g.setComponentTooltipForNextFrame(font,List.of(Component.literal("绑定到 "+key),Component.literal(binding.isEmpty()?"当前：空槽":"当前："+(SkillCatalog.contains(binding)?SkillCatalog.require(binding).title:"内容未加载")),Component.literal("右键清空 · 拖到已有技能上会替换绑定")),mx,my);
        }
        g.text(font,font.plainSubstrByWidth(layer<0?"Ctrl 与 Alt 同时按下：不可绑定":new String[]{"普通层","Ctrl 层","Alt 层"}[layer]+" · Ctrl / Alt 切换 · 右键清空",panelWidth-24),left+12,top+panelHeight-16,UiTheme.MUTED,false);
        if(dragging){int x=Math.clamp(mx-12,0,Math.max(0,width-24)),y=Math.clamp(my-12,0,Math.max(0,height-24));g.blit(Identifier.fromNamespaceAndPath(ItemData.NS,"textures/gui/skills/"+skill.icon()+".png"),x,y,x+24,y+24,0,1,0,1);}
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){if(event.button()==0&&Math.hypot(event.x()-originX,event.y()-originY)>=4){dragging=true;return true;}return super.mouseDragged(event,dx,dy);}
    @Override public boolean mouseReleased(MouseButtonEvent event){if(event.button()==0&&dragging){dragging=false;for(int i=0;i<slots.size();i++)if(slots.get(i).isMouseOver(event.x(),event.y())){bind(Math.max(0,layer)*8+i,skill.id);return true;}onClose();return true;}return super.mouseReleased(event);}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice){if(event.button()==1&&layer>=0){for(int i=0;i<slots.size();i++)if(slots.get(i).isMouseOver(event.x(),event.y())){bind(layer*8+i,"");return true;}}return super.mouseClicked(event,twice);}
    @Override public void onClose(){minecraft.gui.popScreenLayer();}
    @Override public boolean isPauseScreen(){return false;}
}
