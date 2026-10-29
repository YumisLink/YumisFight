package cn.yumis.fight.modules.dungeons.client;
import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.presentation.api.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.function.Consumer;
public final class DungeonPage {
    public record Join(String dungeon,Difficulty difficulty,boolean sync){}
    private static final List<String> ART=List.of("goblin_camp","dragon_tower","castle_palace","gbl_church","behemoth_forbidden","dark_city","skasa","moonlight_tavern","hamelin","pursuit","sea_train","time_rift","mysterious_awareness","grandi","central_park");
    private DungeonPage(){}
    public static void render(PlayerPageSurface page,Dungeon current,boolean preferredSync,Consumer<Join> join,Runnable leave,Consumer<String> message){
        if(current!=null){page.line("当前："+DungeonCatalog.current().dungeon(current.dungeon).name()+" · "+current.difficulty.title());page.line("波次 "+(current.wave+1)+" / 7 · "+current.status);page.rowButton("离开地下城",leave);page.gap();}
        int cell=(page.contentWidth()-24)/2,base=page.cursor(),pictureHeight=Math.max(42,(cell-13)/2),height=pictureHeight+43,index=0;
        for(var definition:DungeonCatalog.current().dungeons){
            int x=(index%2)*cell,y=base+(index/2)*(height+6),w=cell-5;index++;
            page.card(x,y,w,height);var button=page.button("",x,y,w,()->{if(!definition.available()){message.accept("该副本尚未开放");return;}if(current!=null){message.accept("请先退出当前副本");return;}Minecraft.getInstance().gui.pushScreenLayer(new Choice(definition,preferredSync,join));});button.setHeight(height);
            int tile=ART.indexOf(definition.id());if(tile<0)tile=15;
            page.image("textures/gui/dungeons/atlas.png",x+4,y+4,w-8,pictureHeight,(tile%4)/4f,(tile%4+1)/4f,(tile/4)/4f,(tile/4+1)/4f);
            page.text(UiFonts.font().plainSubstrByWidth(definition.name(),w-12),x+6,y+pictureHeight+10,definition.available()?UiTheme.TEXT:UiTheme.MUTED);
            page.text("Lv."+definition.level()+(definition.available()?"":" · 尚未开放"),x+6,y+pictureHeight+25,UiTheme.MUTED);
        }
        page.cursor(base+((index+1)/2)*(height+6));
    }
    private static final class Choice extends ThemedScreen {
        private final DungeonCatalog.DungeonDef definition;private final Consumer<Join> join;private boolean sync;
        Choice(DungeonCatalog.DungeonDef definition,boolean sync,Consumer<Join> join){super(Component.literal(definition.name()),ThemeDomain.MENU);this.definition=definition;this.sync=sync;this.join=join;}
        @Override protected void init(){int x=(width-250)/2,y=(height-220)/2;
            addRenderableWidget(Button.builder(Component.literal("本人同步："+(sync?"开启":"关闭")),b->{sync=!sync;rebuildWidgets();}).bounds(x+10,y+35,230,22).build(ThemedButton::new));
            int[] colors={0xffffff,0x55aaff,0xb787ff,0xff9a35,0xff4545};
            for(var difficulty:Difficulty.values()){var button=addRenderableWidget(Button.builder(Component.literal(difficulty.title()),b->{minecraft.gui.popScreenLayer();join.accept(new Join(definition.id(),difficulty,sync));}).bounds(x+10,y+64+difficulty.ordinal()*25,230,22).build(ThemedButton::new));((ThemedButton)button).textColor=colors[difficulty.ordinal()];}
            addRenderableWidget(Button.builder(Component.literal("返回"),b->onClose()).bounds(x+193,y+7,47,20).build(ThemedButton::new));
        }
        @Override protected void drawBackground(GuiGraphicsExtractor g,int mx,int my,float a){g.fill(0,0,width,height,UiTheme.OVERLAY);UiTheme.panel(g,(width-250)/2,(height-220)/2,250,220);}
        @Override protected void drawContents(GuiGraphicsExtractor g,int mx,int my,float a){g.text(UiFonts.font(),definition.name(),(width-250)/2+12,(height-220)/2+14,UiTheme.TEXT,false);super.drawContents(g,mx,my,a);}
        @Override public void onClose(){minecraft.gui.popScreenLayer();}
        @Override public boolean isPauseScreen(){return false;}
    }
}
