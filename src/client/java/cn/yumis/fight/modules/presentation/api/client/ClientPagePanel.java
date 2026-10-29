package cn.yumis.fight.modules.presentation.api.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/** A custom module panel lives alongside ordinary I tabs and never owns another screen. */
public interface ClientPagePanel {
    record Region(int x,int y,int width,int height){}
    void render(GuiGraphicsExtractor graphics,Region region,int mouseX,int mouseY,float delta);
    default void tick(){}
    default boolean mouseClicked(MouseButtonEvent event,boolean twice){return false;}
    default boolean mouseScrolled(double x,double y,double sx,double sy){return false;}
    default boolean keyPressed(KeyEvent event){return false;}
    default void close(){}
}
