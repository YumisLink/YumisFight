package cn.yumis.fight.modules.quests.client;

import cn.yumis.fight.modules.npc.api.NpcInteractionComponent;
import cn.yumis.fight.modules.quests.api.Commissions;
import cn.yumis.fight.modules.quests.api.Commissions.Active;
import cn.yumis.fight.modules.quests.api.Commissions.Kind;
import cn.yumis.fight.modules.presentation.api.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Village commission counter: a task card above the shared bottom dialogue. */
final class CommissionDialogueScreen extends ThemedScreen {
    private final LivingEntity entity;private final Object level;
    private final List<String> dialogue;
    private final Active shown;
    private List<FormattedCharSequence> objectiveLines=List.of();
    private int lineIndex,left,top,cardTop,cardHeight,panelWidth,panelHeight,portraitWidth,textLeft,textWidth,totalCharacters;
    private long began,heartbeat,settled;private boolean full;private Button advance;
    CommissionDialogueScreen(LivingEntity entity){
        super(Minecraft.getInstance(),UiFonts.font(),Component.literal("查看委托"),ThemeDomain.MENU);
        this.entity=entity;level=entity.level();
        settled=CommissionClient.settlementRevision();
        var definition=NpcInteractionComponent.query(entity);
        dialogue=definition!=null&&!definition.dialogue().isEmpty()?List.copyOf(definition.dialogue()):List.of("村子里总有做不完的活儿。要是有空，帮我跑一趟如何？");
        // The card mirrors the offer in hand, or the commission already being carried.
        shown=CommissionClient.pendingDraw()!=null?CommissionClient.pendingDraw():CommissionClient.activeCommission();
        lineIndex=0;
    }
    @Override protected void init(){layout();hold(true);}
    private void layout(){
        clearWidgets();int margin=Math.max(5,Math.min(18,width/40));left=margin;panelWidth=width-margin*2;portraitWidth=width>=420?112:width>=280?68:0;textLeft=left+portraitWidth+14;textWidth=Math.max(36,panelWidth-portraitWidth-28);
        objectiveLines=font.split(Component.literal(Commissions.objective(shown)),textWidth);
        cardHeight=30+objectiveLines.size()*13+16+(shown.bonusCount()>0?13:0);
        panelHeight=Math.min(height-margin*2,Math.max(104,66+1*13));
        top=height-margin-panelHeight;cardTop=top-8-cardHeight;
        advance=addRenderableWidget(Button.builder(Component.literal("继续"),b->next()).bounds(left+panelWidth-83,top+panelHeight-29,71,20).build(ThemedButton::new));
        addRenderableWidget(Button.builder(Component.literal("暂时离开"),b->onClose()).bounds(textLeft,top+panelHeight-29,72,20).build(ThemedButton::new));
        if(CommissionClient.activeCommission()==null&&shown!=null){
            addRenderableWidget(Button.builder(Component.literal("接受委托"),b->accept()).bounds(textLeft+80,top+panelHeight-29,78,20).build(ThemedButton::new));
        }else if(shown!=null&&Commissions.delivered(shown)){
            var deliver=addRenderableWidget(Button.builder(Component.literal("交付委托"),b->CommissionClient.deliver(entity)).bounds(textLeft+80,top+panelHeight-29,78,20).build(ThemedButton::new));
            deliver.active=CommissionClient.deliverable(shown);
            addRenderableWidget(Button.builder(Component.literal("放弃委托"),b->CommissionClient.abandon()).bounds(textLeft+162,top+panelHeight-29,78,20).build(ThemedButton::new));
        }else if(shown!=null){
            addRenderableWidget(Button.builder(Component.literal("放弃委托"),b->CommissionClient.abandon()).bounds(textLeft+80,top+panelHeight-29,78,20).build(ThemedButton::new));
        }
        began=ClientUi.now();full=false;
    }
    private void accept(){CommissionClient.accept(entity);onClose();}
    private int visible(){return full?totalCharacters:(int)Math.min(totalCharacters,Math.max(0,ClientUi.now()-began)/35);}
    private void next(){
        if(visible()<totalCharacters){full=true;return;}
        if(lineIndex+1<dialogue.size()){lineIndex++;layout();return;}
        onClose();
    }
    private void hold(boolean open){
        if(!ClientUi.current().ready())return;var request=ClientUi.current().operation("npcDialogue");request.addProperty("entity",entity.getStringUUID());request.addProperty("open",open);ClientUi.current().send("npcDialogue",request);heartbeat=ClientUi.now()+1000;
    }
    @Override public void tick(){
        if(Minecraft.getInstance().level!=level||!entity.isAlive()||!TutorialClient.alive()){onClose();return;}
        if(ClientUi.now()>=heartbeat)hold(true);
        advance.setMessage(Component.literal(visible()<totalCharacters?"显示全文":lineIndex+1<dialogue.size()?"继续  ›":"收起"));
        if(CommissionClient.settlementRevision()!=settled)onClose();
    }
    @Override protected void drawBackground(GuiGraphicsExtractor g,int mx,int my,float delta){g.fill(0,0,width,height,0x10000000);}
    @Override protected void drawContents(GuiGraphicsExtractor g,int mx,int my,float delta){
        var definition=NpcInteractionComponent.query(entity);
        if(shown!=null){
            UiTheme.panel(g,left,cardTop,panelWidth,cardHeight);
            g.text(font,Commissions.title(shown),left+12,cardTop+10,UiTheme.GOLD,false);
            int y=cardTop+26;
            for(var line:objectiveLines){RichTextDrawing.line(g,font,line,left+12,y,UiTheme.TEXT);y+=13;}
            g.text(font,"奖励：点券 ×"+shown.points(),left+12,y+2,UiTheme.GOLD,false);
            if(shown.bonusCount()>0)g.text(font,"附加："+CommissionClient.displayName(shown.bonus())+" ×"+shown.bonusCount(),left+12,y+15,UiTheme.TEXT,false);
        }
        UiTheme.panel(g,left,top,panelWidth,panelHeight);g.fill(left,top,left+panelWidth,top+1,0xffc6acff);
        if(portraitWidth>0){int pictureTop=Math.max(3,top-58),pictureBottom=top+panelHeight-3;int size=Math.max(12,Math.min((int)((pictureBottom-pictureTop)/3.15f),portraitWidth-8));InventoryScreen.renderEntityInInventoryFollowsAngle(g,left+2,pictureTop,left+portraitWidth,pictureBottom,size,.04f,.12f,.025f,entity);}
        String speaker=definition==null?"委托人":definition.name();
        var line=Component.literal(dialogue.get(lineIndex));
        g.text(font,speaker,textLeft,top+12,UiTheme.GOLD,false);
        String label=definition!=null&&definition.title()!=null&&!definition.title().isBlank()?definition.title():"村庄委托";
        int nameLeft=textLeft+font.width(speaker)+10;
        g.text(font,label,nameLeft,top+12,UiTheme.MUTED,false);
        String count=(lineIndex+1)+" / "+dialogue.size();g.text(font,count,left+panelWidth-13-font.width(count),top+12,UiTheme.MUTED,false);
        var lines=font.split(line,textWidth);
        totalCharacters=lines.stream().mapToInt(RichTextDrawing::length).sum();
        int remaining=visible(),y=top+34;
        for(var text:lines){if(y+font.lineHeight>top+panelHeight-34)break;int length=RichTextDrawing.length(text);RichTextDrawing.line(g,font,RichTextDrawing.prefix(text,remaining),textLeft,y,UiTheme.TEXT);remaining=Math.max(0,remaining-length);y+=13;}
        super.drawContents(g,mx,my,delta);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice){if(super.mouseClicked(event,twice))return true;if(event.button()==0&&event.y()>=top&&event.y()<cardTop){next();return true;}return false;}
    @Override public boolean keyPressed(KeyEvent event){
        if(event.isEscape()){onClose();return true;}
        if(MenuInput.MENU.matches(event)){onClose();MenuInput.open("tutorial");return true;}
        if(event.key()==GLFW.GLFW_KEY_SPACE||event.key()==GLFW.GLFW_KEY_ENTER||event.key()==GLFW.GLFW_KEY_KP_ENTER){next();return true;}
        return super.keyPressed(event);
    }
    @Override public void removed(){hold(false);super.removed();}
    @Override public void onClose(){minecraft.gui.setScreen(null);}
    @Override public boolean isPauseScreen(){return false;}
}
