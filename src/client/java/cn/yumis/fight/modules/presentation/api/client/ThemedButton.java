package cn.yumis.fight.modules.presentation.api.client;

import cn.yumis.fight.modules.presentation.api.RichText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** Native focus, narration and click rectangles with YDNF's flat immediate drawing. */
public final class ThemedButton extends Button {
    public boolean selected;
    public int textInset=-1;
    public java.util.function.Supplier<String> dynamicText;
    public int textColor=-1;
    private String labelSuffix = "";
    public ThemedButton(Builder builder){
        super(builder);
    }
    public void setLabelWithSuffix(RichText label, String suffix) {
        setMessage(label.component());
        labelSuffix = suffix;
    }
    @Override protected void extractContents(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta){
        try(var ignored=UiTheme.scoped()?null:UiTheme.use(ClientSettings.globalTheme)){draw(g,mouseX,mouseY,delta);}
    }
    private void draw(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta){
        if(dynamicText!=null){String value=dynamicText.get();if(!value.equals(getMessage().getString()))setMessage(net.minecraft.network.chat.Component.literal(value));}
        boolean hover=isHoveredOrFocused();
        int x=getX(),y=getY(),w=getWidth(),h=getHeight();
        UiTheme.button(g,x,y,w,h,active,hover,selected);
        int inset=textInset<0?UiTheme.textInset():textInset;
        var font=UiFonts.font();
        int available=Math.max(0,w-inset-6);
        int color=active?(textColor==-1?UiTheme.TEXT:textColor):UiTheme.MUTED;
        int textY=y+(h-font.lineHeight)/2;
        if (!labelSuffix.isEmpty()) {
            drawLabelWithSuffix(g, font, x + inset, textY, available, color);
            return;
        }
        String text=getMessage().getString();
        if(font.width(text)>available)text=font.width("…")<=available?font.plainSubstrByWidth(text,available-font.width("…"))+"…":"";
        g.text(font,text,x+inset,textY,color,false);
    }
    private void drawLabelWithSuffix(GuiGraphicsExtractor graphics, Font font, int x, int y, int available, int color) {
        var label = getMessage().getVisualOrderText();
        int labelWidth = Math.max(0, available - font.width(labelSuffix));
        if (font.width(label) > labelWidth) {
            int ellipsisWidth = font.width("…");
            label = FormattedCharSequence.EMPTY;
            if (ellipsisWidth <= labelWidth) {
                var prefix = font.substrByWidth(getMessage(), labelWidth - ellipsisWidth);
                label = FormattedCharSequence.composite(Language.getInstance().getVisualOrder(prefix),
                        FormattedCharSequence.forward("…", Style.EMPTY));
            }
        }
        RichTextDrawing.line(graphics, font, label, x, y, color, true);
        graphics.text(font, labelSuffix, x + font.width(label), y, color, false);
    }
}
