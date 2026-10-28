package cn.yumis.fight.modules.presentation.api.client;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import java.util.function.Supplier;
/** Small page rendering port; pages own their data and command callbacks. */
public interface PlayerPageSurface {
    @FunctionalInterface interface Hover {void render(GuiGraphicsExtractor graphics,int mouseX,int mouseY);}
    void line(String text);
    void line(String text,int color);
    void gap();
    void rowButton(String label,Runnable action);
    Screen screen();
    int contentWidth();
    int windowWidth();
    int cursor();
    void cursor(int position);
    AbstractWidget button(String label,int x,int y,int width,Runnable action);
    void text(String text,int x,int y,int color);
    void card(int x,int y,int width,int height);
    void icon(String path,int x,int y,int size);
    void hover(AbstractWidget widget,Hover renderer);
    void hover(int x,int y,int width,int height,Hover renderer);
    Supplier<String> textInput(String key,String label,int maximum);
    <T>T remember(String key,T fallback);
    void rememberValue(String key,Object value);
    void rebuild();
    void resize();
}
