package cn.yumis.fight.modules.presentation.api.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.world.item.ItemStack;

/** Common selector tile: item image above a name, with native focus and tooltip handling. */
public final class GridItemButton extends Button {
    private final ItemStack icon;
    public boolean selected;
    public GridItemButton(Builder builder,ItemStack icon){super(builder);this.icon=icon.copyWithCount(icon.isEmpty()?0:1);}
    @Override protected void extractContents(GuiGraphicsExtractor graphics,int mx,int my,float alpha){
        UiTheme.button(graphics,getX(),getY(),getWidth(),getHeight(),active,isHoveredOrFocused(),selected);
        graphics.item(icon,getX()+(getWidth()-16)/2,getY()+7);var font=UiFonts.font();
        var lines=font.split(getMessage(),Math.max(1,getWidth()-8));for(int i=0;i<Math.min(2,lines.size());i++)graphics.text(font,lines.get(i),getX()+(getWidth()-font.width(lines.get(i)))/2,getY()+29+i*(font.lineHeight+1),active?UiTheme.TEXT:UiTheme.MUTED,false);
    }
}
