package cn.yumis.fight.modules.economy.client;

import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.presentation.api.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.Locale;

/** A continuous left-to-right strip with an exact, preselected landing card. */
public final class JarScreen extends ThemedScreen {
    private Button close;
    public JarScreen(){super(Component.literal("开罐"));}
    private int panelWidth(){return Math.min(720,width-16);}
    private int panelHeight(){return Math.min(236,height-12);}
    @Override protected void init(){close=addRenderableWidget(Button.builder(Component.literal("关闭"),button->onClose()).bounds(width/2-60,(height+panelHeight())/2-28,120,20).build(ThemedButton::new));close.active=JarClient.view()!=null&&JarClient.view().complete;}
    @Override public void tick(){if(JarClient.view()==null)return;close.active=JarClient.view().complete;}
    @Override protected void drawBackground(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta){
        graphics.fill(0,0,width,height,UiTheme.OVERLAY);
        int w=panelWidth(),h=panelHeight(),left=(width-w)/2,top=(height-h)/2;UiTheme.panel(graphics,left,top,w,h);
        var value=JarClient.view();if(value==null)return;var font=UiFonts.font();
        graphics.text(font,value.name,left+14,top+12,UiTheme.GOLD,false);
        int cardWidth=72,cardHeight=Math.max(32,Math.min(72,h-136)),pitch=78,stripTop=top+38;
        double t=value.progress(),ease=1-Math.pow(1-t,3),distance=(value.winnerIndex-8d)*pitch;
        graphics.enableScissor(left+12,stripTop-2,left+w-12,stripTop+cardHeight+2);
        try{
            for(int i=0;i<value.strip.size();i++){
                int x=(int)Math.round(width/2d-cardWidth/2d+(value.winnerIndex-i)*pitch-distance*(1-ease));
                if(x+cardWidth<left+12||x>left+w-12)continue;
                ItemStack item=value.strip.get(i);var gear=EquipmentItems.view(item);int color=gear==null?Rarity.COMMON.color():gear.rarity().color();
                UiTheme.card(graphics,x,stripTop,cardWidth,cardHeight);graphics.fill(x+3,stripTop+cardHeight-3,x+cardWidth-3,stripTop+cardHeight-1,0xff000000|color);
                graphics.item(item,x+(cardWidth-16)/2,stripTop+Math.max(4,(cardHeight-32)/2));
                if(cardHeight>=42)graphics.text(font,font.plainSubstrByWidth(item.getHoverName().getString(),cardWidth-10),x+5,stripTop+cardHeight-16,0xff000000|color,false);
            }
        }finally{graphics.disableScissor();}
        int frameX=width/2-cardWidth/2-2,frameY=stripTop-3,frameW=cardWidth+4,frameH=cardHeight+6;
        graphics.fill(frameX,frameY,frameX+frameW,frameY+2,UiTheme.GOLD);graphics.fill(frameX,frameY+frameH-2,frameX+frameW,frameY+frameH,UiTheme.GOLD);graphics.fill(frameX,frameY,frameX+2,frameY+frameH,UiTheme.GOLD);graphics.fill(frameX+frameW-2,frameY,frameX+frameW,frameY+frameH,UiTheme.GOLD);
        graphics.text(font,"▼",width/2-4,stripTop-11,UiTheme.GOLD,false);
        int resultY=stripTop+cardHeight+14;
        String line=value.complete?value.winner.getHoverName().getString():t>=1?"正在结算…":String.format(Locale.ROOT,"开罐中 · %.1f秒",Math.max(0,(value.duration-(ClientUi.current().serverNow()-value.startedAt))/1000d));
        graphics.text(font,font.plainSubstrByWidth(line,w-28),left+14,resultY,value.complete?UiTheme.GOLD:UiTheme.TEXT,false);
        if(value.complete)graphics.text(font,font.plainSubstrByWidth(value.dropped?"背包已满，奖品已生成公共掉落":"已获得 · 悬停中央物品查看详情",w-28),left+14,resultY+18,UiTheme.MUTED,false);
    }
    @Override protected void drawContents(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta){
        super.drawContents(graphics,mouseX,mouseY,delta);var value=JarClient.view();if(value==null||!value.complete)return;
        int h=panelHeight(),cardHeight=Math.max(32,Math.min(72,h-136)),stripTop=(height-h)/2+38;
        if(mouseX>=width/2-36&&mouseX<width/2+36&&mouseY>=stripTop&&mouseY<stripTop+cardHeight)graphics.setComponentTooltipForNextFrame(UiFonts.font(),Screen.getTooltipFromItem(minecraft,value.winner),mouseX,mouseY,value.winner);
    }
    @Override public boolean shouldCloseOnEsc(){return JarClient.view()!=null&&JarClient.view().complete;}
    @Override public void onClose(){JarClient.dismiss();}
    @Override public boolean isPauseScreen(){return false;}
}
