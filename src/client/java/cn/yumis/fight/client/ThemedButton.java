package cn.yumis.fight.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
/** Native focus, narration and click rectangles with YDNF's flat immediate drawing. */
public final class ThemedButton extends Button {
    public boolean selected;
    public int textInset=6;
    public ThemedButton(Builder builder){
        super(builder);
    }
    @Override protected void extractContents(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta){
        boolean hover=isHoveredOrFocused();
        int x=getX(),y=getY(),w=getWidth(),h=getHeight();
        g.fill(x,y,x+w,y+h,!active?UiTheme.DISABLED:selected?UiTheme.SELECTED:hover?UiTheme.HOVER:UiTheme.BUTTON);
        if(active&&hover)g.fill(x,y,x+w,y+h,0x557F8F9F);
        if(selected)g.fill(x,y+h-1,x+w,y+h,UiTheme.ACCENT);
        var font=UiFonts.font();
        String text=getMessage().getString();
        int available=Math.max(0,w-textInset-6);
        if(font.width(text)>available)text=font.width("…")<=available?font.plainSubstrByWidth(text,available-font.width("…"))+"…":"";
        g.text(font,text,x+textInset,y+(h-font.lineHeight)/2,active?UiTheme.TEXT:UiTheme.MUTED,false);
    }
}
