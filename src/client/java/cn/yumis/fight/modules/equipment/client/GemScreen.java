package cn.yumis.fight.modules.equipment.client;
import cn.yumis.fight.modules.equipment.api.*;
import cn.yumis.fight.modules.equipment.api.client.EquipmentClient;
import cn.yumis.fight.modules.presentation.api.client.*;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
public final class GemScreen extends Screen {
    private final Screen parent;private final JsonObject request;private final Gear gear;private int page,top,panelHeight;
    public GemScreen(Screen parent,JsonObject request,Gear gear){super(Component.literal("选择宝石孔"));this.parent=parent;this.request=request;this.gear=gear;}
    @Override protected void init(){panelHeight=Math.min(height-16,340);top=(height-panelHeight)/2;int capacity=Math.max(1,(panelHeight-108)/25),pages=Math.max(1,(gear.sockets+capacity-1)/capacity);page=Math.clamp(page,0,pages-1);
        for(int i=page*capacity;i<Math.min(gear.sockets,(page+1)*capacity);i++){int index=i;String existing=i<gear.gems.size()?gear.gems.get(i).name+"（覆盖销毁）":"空孔";addRenderableWidget(Button.builder(Component.literal("孔 "+(i+1)+" · "+existing),b->{if(valid()){request.addProperty("socket",index);EquipmentClient.current().send("currency",request);}onClose();}).bounds(width/2-120,top+65+(i-page*capacity)*25,240,20).build(ThemedButton::new));}
        addRenderableWidget(Button.builder(Component.literal("上一页"),b->{page--;rebuildWidgets();}).bounds(width/2-120,top+panelHeight-30,70,20).build(ThemedButton::new)).active=page>0;
        addRenderableWidget(Button.builder(Component.literal("下一页"),b->{page++;rebuildWidgets();}).bounds(width/2-45,top+panelHeight-30,70,20).build(ThemedButton::new)).active=page+1<pages;
        addRenderableWidget(Button.builder(Component.literal("返回"),b->onClose()).bounds(width/2+30,top+panelHeight-30,90,20).build(ThemedButton::new));
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float delta){g.fill(0,0,width,height,UiTheme.OVERLAY);UiTheme.panel(g,width/2-135,top,270,panelHeight);g.text(UiFonts.font(),title,width/2-120,top+12,UiTheme.GOLD,false);double multiplier=EquipmentEffects.gemMultiplier(gear);g.text(UiFonts.font(),"本装备宝石数值 ×"+cn.yumis.fight.modules.presentation.api.client.NumberText.number(multiplier),width/2-120,top+31,UiTheme.ACCENT,false);g.text(UiFonts.font(),"同效果宝石不可重复；覆盖不能取回。",width/2-120,top+46,UiTheme.MUTED,false);}
    private boolean valid(){return minecraft.player!=null&&minecraft.player.containerMenu.containerId==request.get("menu").getAsInt()&&parent instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>;}
    @Override public void tick(){if(!valid())minecraft.gui.setScreen(null);}
    @Override public void onClose(){minecraft.gui.popScreenLayer();}
    @Override public boolean isPauseScreen(){return false;}
}
