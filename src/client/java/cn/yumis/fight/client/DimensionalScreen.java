package cn.yumis.fight.client;

import cn.yumis.fight.industry.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import java.util.List;

public final class DimensionalScreen extends AbstractContainerScreen<DimensionalMenu> {
    public DimensionalScreen(DimensionalMenu menu,Inventory inventory,Component title){super(menu,inventory,title,170,menu.rows*18+148);inventoryLabelY=menu.rows*18+40;}
    @Override protected void init(){
        super.init();button("‹",4,4,20,0);button("›",113,4,20,1);button(menu.overflow?"返回":"暂存",135,4,32,2);button("升级",111,imageHeight-22,48,3).active=!menu.overflow&&menu.extra.bagTier<16;
    }
    private Button button(String text,int x,int y,int width,int id){return addRenderableWidget(Button.builder(Component.literal(text),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id)).bounds(leftPos+x,topPos+y,width,18).build(ThemedButton::new));}
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float alpha){
        g.fill(0,0,width,height,UiTheme.OVERLAY);g.fill(leftPos-5,topPos-5,leftPos+imageWidth+5,topPos+imageHeight+4,UiTheme.PANEL);g.fill(leftPos-5,topPos-5,leftPos+imageWidth+5,topPos-3,UiTheme.ACCENT);
        for(var slot:menu.slots)if(slot.isActive()){
            if(slot.index<DimensionalMenu.PLAYER_START&&!menu.overflow&&slot.getContainerSlot()>=StorageRules.capacity(menu.extra.bagTier))continue;
            int x=leftPos+slot.x,y=topPos+slot.y;g.fill(x-1,y-1,x+17,y+17,UiTheme.CARD);g.fill(x,y,x+16,y+16,UiTheme.BUTTON);
        }
        if(mx>=leftPos+imageWidth-16&&my>=topPos+24&&my<topPos+40)g.setComponentTooltipForNextFrame(UiFonts.font(),List.of(Component.literal("每页64格；快捷存入搜索全部容量。暂存区只取不存。"),Component.literal(menu.preview.has("requirements")?menu.preview.get("requirements").getAsString():"升级材料载入中")),mx,my);
        g.text(UiFonts.font(),"?",leftPos+imageWidth-14,topPos+26,UiTheme.ACCENT,false);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g,int x,int y){
        int page=menu.overflow?menu.overflowPage+1:menu.extra.bagPage+1;
        g.text(UiFonts.font(),menu.overflow?"迁移暂存 · 只取不存":StorageRules.NAMES[menu.extra.bagTier-1],8,21,0xffe5e8ef,false);
        g.text(UiFonts.font(),page+" / "+(menu.overflow?1:StorageRules.pages(menu.extra.bagTier)),49,9,0xffe5e8ef,false);
        g.text(UiFonts.font(),"玩家背包",0,inventoryLabelY,0xffa9b4c5,false);
        int used=0;for(int i=0;i<menu.extra.bagLimit();i++)if(!menu.extra.bag.getItem(i).isEmpty())used++;
        g.text(UiFonts.font(),menu.overflow?"暂存 "+menu.overflowUsed()+" 格":used+" / "+menu.extra.bagLimit()+" 格",8,imageHeight-17,0xffb8c9d9,false);
    }
    @Override public boolean keyPressed(KeyEvent e){if(ClientBootstrap.MENU.matches(e)){onClose();minecraft.gui.setScreen(new FightScreen());return true;}return super.keyPressed(e);}
}
