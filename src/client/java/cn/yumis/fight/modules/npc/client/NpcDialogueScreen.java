package cn.yumis.fight.modules.npc.client;

import cn.yumis.fight.modules.npc.api.*;
import cn.yumis.fight.modules.presentation.api.RichText;
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

/** Private bottom dialogue. Only the shared entity's temporary conversation hold is sent to the server. */
public final class NpcDialogueScreen extends ThemedScreen {
    private final LivingEntity entity;private final Object level;private final String dialogueKey;
    private final List<RichText> pages;private List<FormattedCharSequence> lines=List.of();
    private int page,left,top,panelWidth,panelHeight,portraitWidth,textLeft,textWidth,totalCharacters;
    private long began,heartbeat;private boolean full;private Button advance;
    public NpcDialogueScreen(LivingEntity entity){
        super(Minecraft.getInstance(),UiFonts.font(),Component.literal("与伊蕾娜交谈"),ThemeDomain.MENU);
        this.entity=entity;level=entity.level();dialogueKey=TutorialClient.dialogueKey();pages=List.copyOf(TutorialClient.dialogue());page=Math.clamp(TutorialClient.savedPage(dialogueKey),0,pages.size()-1);
    }
    @Override protected void init(){layout();hold(true);}
    private void layout(){
        clearWidgets();int margin=Math.max(5,Math.min(18,width/40));left=margin;panelWidth=width-margin*2;portraitWidth=width>=420?112:width>=280?68:0;textLeft=left+portraitWidth+14;textWidth=Math.max(36,panelWidth-portraitWidth-28);
        lines=font.split(pages.get(page).component(),textWidth);totalCharacters=lines.stream().mapToInt(RichTextDrawing::length).sum();
        panelHeight=Math.min(height-margin*2,Math.max(104,66+lines.size()*13));top=height-margin-panelHeight;
        advance=addRenderableWidget(Button.builder(Component.literal("继续"),b->next()).bounds(left+panelWidth-83,top+panelHeight-29,71,20).build(ThemedButton::new));
        addRenderableWidget(Button.builder(Component.literal("暂时离开"),b->onClose()).bounds(textLeft,top+panelHeight-29,72,20).build(ThemedButton::new));
        began=ClientUi.now();full=false;
    }
    private int visible(){return full?totalCharacters:(int)Math.min(totalCharacters,Math.max(0,ClientUi.now()-began)/35);}
    private void next(){
        if(visible()<totalCharacters){full=true;return;}
        if(page+1<pages.size()){page++;TutorialClient.savePage(dialogueKey,page);layout();return;}
        finishIntroduction();onClose();
    }
    private void finishIntroduction(){if(dialogueKey.equals("introduction")&&page==pages.size()-1)TutorialClient.start();}
    private void hold(boolean open){
        if(!ClientUi.current().ready())return;var request=ClientUi.current().operation("npcDialogue");request.addProperty("entity",entity.getStringUUID());request.addProperty("open",open);ClientUi.current().send("npcDialogue",request);heartbeat=ClientUi.now()+1000;
    }
    @Override public void tick(){
        if(Minecraft.getInstance().level!=level||!entity.isAlive()||!TutorialClient.alive()){onClose();return;}
        if(ClientUi.now()>=heartbeat)hold(true);
        advance.setMessage(Component.literal(visible()<totalCharacters?"显示全文":page+1<pages.size()?"继续  ›":dialogueKey.equals("introduction")?"去试试  ›":"收起"));
    }
    @Override protected void drawBackground(GuiGraphicsExtractor g,int mx,int my,float delta){g.fill(0,0,width,height,0x10000000);}
    @Override protected void drawContents(GuiGraphicsExtractor g,int mx,int my,float delta){
        UiTheme.panel(g,left,top,panelWidth,panelHeight);g.fill(left,top,left+panelWidth,top+1,0xffc6acff);
        if(portraitWidth>0){int pictureTop=Math.max(3,top-58),pictureBottom=top+panelHeight-3;int size=Math.max(12,Math.min((int)((pictureBottom-pictureTop)/3.15f),portraitWidth-8));InventoryScreen.renderEntityInInventoryFollowsAngle(g,left+2,pictureTop,left+portraitWidth,pictureBottom,size,.04f,.12f,.025f,entity);}
        g.text(font,"伊蕾娜",textLeft,top+12,UiTheme.GOLD,false);g.text(font,"旅途引路人",textLeft+font.width("伊蕾娜")+10,top+12,UiTheme.MUTED,false);
        String count=(page+1)+" / "+pages.size();g.text(font,count,left+panelWidth-13-font.width(count),top+12,UiTheme.MUTED,false);
        int remaining=visible(),y=top+34;
        for(var line:lines){if(y+font.lineHeight>top+panelHeight-34)break;int length=RichTextDrawing.length(line);RichTextDrawing.line(g,font,RichTextDrawing.prefix(line,remaining),textLeft,y,UiTheme.TEXT);remaining=Math.max(0,remaining-length);y+=13;}
        super.drawContents(g,mx,my,delta);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice){if(super.mouseClicked(event,twice))return true;if(event.button()==0&&event.y()>=top){next();return true;}return false;}
    @Override public boolean keyPressed(KeyEvent event){
        if(event.isEscape()){onClose();return true;}
        if(MenuInput.MENU.matches(event)){finishIntroduction();onClose();MenuInput.open("tutorial");return true;}
        if(event.key()==GLFW.GLFW_KEY_SPACE||event.key()==GLFW.GLFW_KEY_ENTER||event.key()==GLFW.GLFW_KEY_KP_ENTER){next();return true;}
        return super.keyPressed(event);
    }
    @Override public void removed(){TutorialClient.savePage(dialogueKey,page);hold(false);super.removed();}
    @Override public void onClose(){minecraft.gui.setScreen(null);}
    @Override public boolean isPauseScreen(){return false;}
}
