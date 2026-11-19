package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.presentation.api.client.*;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

final class RitualMenuScreen extends ThemedScreen {
    private final JsonObject altar;
    private final boolean awakening;
    private int left,top,panelWidth,panelHeight;

    RitualMenuScreen(JsonObject altar,boolean awakening){
        super(Component.literal("魔王祭坛"),ThemeDomain.MENU);
        this.altar=altar;
        this.awakening=awakening;
    }

    @Override protected void init(){
        panelWidth=Math.min(width-16,360);
        var bosses=altar.getAsJsonArray("bosses");
        int columns=panelWidth>=310?2:1;
        int rows=(bosses.size()+columns-1)/columns;
        panelHeight=Math.max(112,70+rows*24+(altar.has("active")?24:0)+(awakening?24:0));
        left=(width-panelWidth)/2;top=Math.max(4,(height-panelHeight)/2);
        if(altar.has("canJoin")&&altar.get("canJoin").getAsBoolean()){
            addRenderableWidget(Button.builder(Component.literal("参加本场召唤"),button->send("ritualJoin",null))
                .bounds(left+10,top+48,panelWidth-20,20).build(ThemedButton::new));
        }
        int y=top+(altar.has("active")?73:48), cell=(panelWidth-20)/columns;
        for(int i=0;i<bosses.size();i++){
            var boss=bosses.get(i).getAsJsonObject();
            String label="Lv."+boss.get("level").getAsInt()+" "+boss.get("name").getAsString()+"  ·  "+boss.get("cost").getAsLong();
            var button=addRenderableWidget(Button.builder(Component.literal(label),widget->send("ritualSummon",boss.get("id").getAsString()))
                .bounds(left+10+i%columns*cell,y+i/columns*24,cell-4,20).build(ThemedButton::new));
            button.active=!altar.has("active")&&!boss.has("obstacle")&&boss.get("affordable").getAsBoolean();
            if(boss.has("obstacle"))button.setTooltip(Tooltip.create(Component.literal(boss.get("obstacle").getAsString())));
            else if(!boss.get("affordable").getAsBoolean())button.setTooltip(Tooltip.create(Component.literal("魔能不足")));
        }
        if(awakening)addRenderableWidget(Button.builder(Component.literal("提交觉醒材料"),button->MenuInput.open("tutorial"))
            .bounds(left+10,top+panelHeight-27,panelWidth-20,20).build(ThemedButton::new));
    }

    private void send(String action,String boss){
        var ui=ClientUi.current();
        var request=ui.operation(action);
        for(var key:new String[]{"x","y","z","dimension"})request.add(key,altar.get(key));
        if(boss!=null)request.addProperty("boss",boss);
        ui.send(action,request);
        onClose();
    }

    @Override protected void drawContents(GuiGraphicsExtractor g,int mouseX,int mouseY,float partial){
        UiTheme.panel(g,left,top,panelWidth,panelHeight);
        g.text(font,title,left+10,top+8,UiTheme.GOLD,false);
        long stored=altar.get("stored").getAsLong(),grid=altar.get("grid").getAsLong();
        g.text(font,"祭坛魔能 "+stored+"  ·  信标供能 "+grid,left+10,top+25,UiTheme.TEXT,false);
        if(altar.has("active"))g.text(font,"本场 "+altar.get("active").getAsString(),left+10,top+36,UiTheme.MUTED,false);
        if(altar.getAsJsonArray("bosses").isEmpty())g.text(font,"持信物右键祭坛，解锁对应首领。",left+10,top+55,UiTheme.MUTED,false);
        super.drawContents(g,mouseX,mouseY,partial);
    }

    @Override public boolean isPauseScreen(){return false;}
}
