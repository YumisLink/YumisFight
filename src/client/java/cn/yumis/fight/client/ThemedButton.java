package cn.yumis.fight.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
/** Native focus, narration and click rectangles with YDNF's flat immediate drawing. */
public final class ThemedButton extends Button {
    public boolean selected;
    public int textInset=-1;
    public ThemedButton(Builder builder){
        super(builder);
    }
    @Override protected void extractContents(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta){
        boolean hover=isHoveredOrFocused();
        int x=getX(),y=getY(),w=getWidth(),h=getHeight();
        UiTheme.button(g,x,y,w,h,active,hover,selected);
        int inset=textInset<0?UiTheme.textInset():textInset;
        var font=UiFonts.font();
        String text=getMessage().getString();
        int available=Math.max(0,w-inset-6);
        if(font.width(text)>available)text=font.width("…")<=available?font.plainSubstrByWidth(text,available-font.width("…"))+"…":"";
        g.text(font,text,x+inset,y+(h-font.lineHeight)/2,active?UiTheme.TEXT:UiTheme.MUTED,false);
    }
}
