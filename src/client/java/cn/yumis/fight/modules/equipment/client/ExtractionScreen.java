package cn.yumis.fight.modules.equipment.client;
import cn.yumis.fight.modules.equipment.api.*;
import cn.yumis.fight.modules.equipment.api.client.EquipmentClient;
import cn.yumis.fight.modules.presentation.api.client.*;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
public final class ExtractionScreen extends ThemedScreen {
    private final JsonObject request;private final Gear gear;private final Gem gem;
    public ExtractionScreen(JsonObject request,Gear gear){super(Component.literal("提炼词条宝石"));this.request=request;this.gear=gear;this.gem=GemExtraction.create(gear);}
    @Override protected void init(){int y=height/2+42;addRenderableWidget(Button.builder(Component.literal("销毁装备并提炼"),b->{if(minecraft.player!=null&&minecraft.player.containerMenu.containerId==request.get("menu").getAsInt())EquipmentClient.current().send("currency",request);onClose();}).bounds(width/2-124,y,146,20).build(ThemedButton::new));addRenderableWidget(Button.builder(Component.literal("取消"),b->onClose()).bounds(width/2+30,y,94,20).build(ThemedButton::new));}
    @Override protected void drawBackground(GuiGraphicsExtractor g,int x,int y,float delta){g.fill(0,0,width,height,UiTheme.OVERLAY);int left=width/2-140,top=height/2-75;UiTheme.panel(g,left,top,280,150);g.text(UiFonts.font(),title,left+12,top+12,UiTheme.GOLD,false);g.text(UiFonts.font(),"销毁："+EquipmentItems.name(gear),left+12,top+34,UiTheme.TEXT,false);g.text(UiFonts.font(),"获得："+gem.name+" · 防具 · Lv."+gem.level,left+12,top+54,UiTheme.ACCENT,false);g.text(UiFonts.font(),cn.yumis.fight.modules.affixes.api.AffixText.affix(gem.affixes.getFirst()),left+12,top+73,UiTheme.TEXT,false);g.text(UiFonts.font(),"装备及其中宝石全部销毁，消耗1枚拆解石。",left+12,top+92,UiTheme.ERROR,false);}
    @Override public void onClose(){minecraft.gui.popScreenLayer();}
    @Override public boolean isPauseScreen(){return false;}
}
