package cn.yumis.fight.modules.presentation.api.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Container menus retain vanilla slot/input semantics while using the same screen palette. */
public abstract class ThemedContainerScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {
    private final ThemeContext theme;
    protected ThemedContainerScreen(M menu,Inventory inventory,Component title,int width,int height){this(menu,inventory,title,width,height,ThemeDomain.GENERAL);}
    protected ThemedContainerScreen(M menu,Inventory inventory,Component title,int width,int height,ThemeDomain domain){super(menu,inventory,title,width,height);theme=new ThemeContext(domain);}
    public final ThemeDomain themeDomain(){return theme.domain();}
    @Override public final void extractBackground(GuiGraphicsExtractor g,int x,int y,float delta){try(var ignored=theme.enter()){drawBackground(g,x,y,delta);}}
    @Override public final void extractRenderState(GuiGraphicsExtractor g,int x,int y,float delta){try(var ignored=theme.enter()){drawContents(g,x,y,delta);}}
    protected void drawBackground(GuiGraphicsExtractor g,int x,int y,float delta){super.extractBackground(g,x,y,delta);}
    protected void drawContents(GuiGraphicsExtractor g,int x,int y,float delta){super.extractRenderState(g,x,y,delta);}
    protected final UiTheme.PaletteScope themeScope(){return theme.enter();}
}
