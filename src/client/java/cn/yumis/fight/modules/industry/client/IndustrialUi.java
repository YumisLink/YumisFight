package cn.yumis.fight.modules.industry.client;
import cn.yumis.fight.modules.presentation.api.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;

/** Industrial controls use the shared MACHINE screen context, including modal child panels. */
final class IndustrialUi {
    static void panel(GuiGraphicsExtractor g,int x,int y,int w,int h){UiTheme.panel(g,x,y,w,h);}
    static void card(GuiGraphicsExtractor g,int x,int y,int w,int h){UiTheme.card(g,x,y,w,h);}
    static void slot(GuiGraphicsExtractor g,int x,int y){UiTheme.slot(g,x,y);}
    static final class IndustrialButton extends Button {
        IndustrialButton(Builder builder){super(builder);}
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
            int x=getX(),y=getY(),w=getWidth(),h=getHeight();UiTheme.button(g,x,y,w,h,active,isHoveredOrFocused(),false);
            var font=UiFonts.font();String text=font.plainSubstrByWidth(getMessage().getString(),w-6);
            g.text(font,text,x+(w-font.width(text))/2,y+(h-font.lineHeight)/2,active?UiTheme.TEXT:UiTheme.MUTED,false);
        }
    }
}
