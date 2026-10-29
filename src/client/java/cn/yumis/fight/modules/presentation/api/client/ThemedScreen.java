package cn.yumis.fight.modules.presentation.api.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Ordinary screens and container screens share the same theme context and drawing contract. */
public abstract class ThemedScreen extends Screen {
    private final ThemeContext theme;
    protected ThemedScreen(Component title){this(title,ThemeDomain.GENERAL);}
    protected ThemedScreen(Component title,ThemeDomain domain){super(title);theme=new ThemeContext(domain);}
    protected ThemedScreen(Minecraft minecraft,Font font,Component title){this(minecraft,font,title,ThemeDomain.GENERAL);}
    protected ThemedScreen(Minecraft minecraft,Font font,Component title,ThemeDomain domain){super(minecraft,font,title);theme=new ThemeContext(domain);}
    public final ThemeDomain themeDomain(){return theme.domain();}
    @Override public final void extractBackground(GuiGraphicsExtractor g,int x,int y,float delta){try(var ignored=theme.enter()){drawBackground(g,x,y,delta);}}
    @Override public final void extractRenderState(GuiGraphicsExtractor g,int x,int y,float delta){try(var ignored=theme.enter()){drawContents(g,x,y,delta);}}
    protected void drawBackground(GuiGraphicsExtractor g,int x,int y,float delta){super.extractBackground(g,x,y,delta);}
    protected void drawContents(GuiGraphicsExtractor g,int x,int y,float delta){super.extractRenderState(g,x,y,delta);}
    protected final UiTheme.PaletteScope themeScope(){return theme.enter();}
}
