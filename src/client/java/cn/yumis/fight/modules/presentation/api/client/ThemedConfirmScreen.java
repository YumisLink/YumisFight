package cn.yumis.fight.modules.presentation.api.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.List;
import java.util.function.Consumer;

/** Module confirmations use their parent's domain without replacing vanilla/third-party dialogs. */
public final class ThemedConfirmScreen extends ThemedScreen {
    private final Consumer<Boolean> result;
    private final Component message;
    private List<FormattedCharSequence> lines=List.of();
    private int left,top,panelWidth,panelHeight;
    public ThemedConfirmScreen(Consumer<Boolean> result,Component title,Component message,ThemeDomain domain){super(title,domain);this.result=result;this.message=message;}
    @Override protected void init(){
        panelWidth=Math.min(360,width-20);lines=UiFonts.font().split(message,panelWidth-24);panelHeight=Math.min(height-16,Math.max(104,70+lines.size()*12));left=(width-panelWidth)/2;top=(height-panelHeight)/2;
        addRenderableWidget(Button.builder(Component.literal("确认"),b->result.accept(true)).bounds(left+12,top+panelHeight-29,(panelWidth-30)/2,20).build(ThemedButton::new));
        addRenderableWidget(Button.builder(Component.literal("取消"),b->result.accept(false)).bounds(left+18+(panelWidth-30)/2,top+panelHeight-29,(panelWidth-30)/2,20).build(ThemedButton::new));
    }
    @Override protected void drawBackground(GuiGraphicsExtractor g,int x,int y,float delta){g.fill(0,0,width,height,UiTheme.OVERLAY);UiTheme.panel(g,left,top,panelWidth,panelHeight);}
    @Override protected void drawContents(GuiGraphicsExtractor g,int x,int y,float delta){
        g.text(UiFonts.font(),title,left+12,top+12,UiTheme.GOLD,false);int line=0;for(var text:lines){if(top+34+line*12>=top+panelHeight-34)break;g.text(UiFonts.font(),text,left+12,top+34+line++*12,UiTheme.TEXT,false);}super.drawContents(g,x,y,delta);
    }
    @Override public void onClose(){result.accept(false);}
    @Override public boolean isPauseScreen(){return false;}
}
