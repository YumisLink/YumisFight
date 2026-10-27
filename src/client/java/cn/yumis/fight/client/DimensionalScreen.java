package cn.yumis.fight.client;

import cn.yumis.fight.industry.*;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import java.util.*;

/** Gray beveled storage, nine-column inventory, search, sort and a draggable viewport. */
public final class DimensionalScreen extends AbstractContainerScreen<DimensionalMenu> {
    private boolean dragging;
    private EditBox search;
    private String lastFilter="";
    private int lastContents;
    public DimensionalScreen(DimensionalMenu menu,Inventory inventory,Component title){super(menu,inventory,title,188,menu.rows*18+174);inventoryLabelY=menu.rows*18+58;}
    @Override protected void init(){
        UiTheme.check();menu.resizeRows(Math.max(1,Math.min(Math.min(8,Math.max(1,menu.totalRows())),(height-186)/18)));((cn.yumis.fight.mixin.ContainerScreenSizeAccessor)(Object)this).ydnf$height(menu.rows*18+174);inventoryLabelY=menu.rows*18+58;super.init();minecraft.gameMode.handleInventoryButtonClick(menu.containerId,1000+menu.rows);
        search=addRenderableWidget(new EditBox(UiFonts.font(),leftPos+8,topPos+24,124,18,Component.literal("搜索物品")));search.setMaxLength(64);search.setHint(Component.literal("搜索物品…"));search.setValue(menu.query);search.setResponder(text->filter());
        if(!menu.overflow){Button sort=button("↧",137,24,18,4);sort.setTooltip(Tooltip.create(Component.literal("整理：合并相同物品并按类型排序")));}
        button(menu.overflow?"返":"存",158,24,22,2).setTooltip(Tooltip.create(Component.literal(menu.overflow?"返回次元背包":"迁移暂存：只取不存")));
        Button upgrade=button(menu.extra.bagTier>=16?"最高档":"升级",130,imageHeight-23,50,3);upgrade.active=!menu.overflow&&menu.extra.bagTier<16;upgrade.setTooltip(Tooltip.create(Component.literal(menu.preview.has("requirements")?menu.preview.get("requirements").getAsString():"升级材料载入中")));
        if(!menu.overflow&&width>=240)button("↑",-18,29,17,3).active=menu.extra.bagTier<16;
        lastContents=0;lastFilter="";
    }
    private Button button(String text,int x,int y,int width,int id){return addRenderableWidget(Button.builder(Component.literal(text),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id)).bounds(leftPos+x,topPos+y,width,18).build(ThemedButton::new));}
    private void filter(){
        if(search==null)return;String query=search.getValue(),normalized=query.toLowerCase(Locale.ROOT);List<Integer> indices=new ArrayList<>();int start=menu.overflow?DimensionalMenu.OVERFLOW_START:0,end=menu.overflow?DimensionalMenu.PLAYER_START:menu.extra.bagLimit();int hash=1;
        for(int i=start;i<end;i++){var stack=menu.slots.get(i).getItem();hash=31*hash+stack.getCount();hash=31*hash+stack.getHoverName().getString().hashCode();if(!stack.isEmpty()&&(stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(normalized)||net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().contains(normalized)))indices.add(i);}
        if(query.equals(lastFilter)&&hash==lastContents)return;lastFilter=query;lastContents=hash;
        if(query.isEmpty()&&menu.query.isEmpty())return;
        menu.filter(query,indices);JsonObject request=new JsonObject();request.addProperty("menu",menu.containerId);request.addProperty("query",query);request.add("indices",cn.yumis.fight.rules.Models.JSON.toJsonTree(indices));ClientState.INSTANCE.send("bagView",request);
    }
    @Override protected void containerTick(){super.containerTick();filter();}
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float alpha){
        UiTheme.check();g.fill(0,0,width,height,UiTheme.OVERLAY);UiTheme.panel(g,leftPos,topPos,imageWidth,imageHeight);menu.layout();
        for(var slot:menu.slots)if(slot.isActive())UiTheme.slot(g,leftPos+slot.x,topPos+slot.y);
        if(menu.maxScroll()>0){int h=menu.rows*18,thumb=Math.max(12,h*menu.rows/Math.max(1,menu.totalRows())),offset=(h-thumb)*menu.scrollRow()/menu.maxScroll();UiTheme.scrollbar(g,leftPos+174,topPos+DimensionalMenu.BAG_Y-1,h,thumb,offset);}
        if(!menu.overflow&&width>=240)UiTheme.panel(g,leftPos-20,topPos+26,21,24);
        if(menu.query.length()>0&&menu.totalRows()==0)g.text(UiFonts.font(),"没有匹配物品",leftPos+12,topPos+DimensionalMenu.BAG_Y+8,UiTheme.MUTED,false);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g,int x,int y){
        g.text(UiFonts.font(),menu.overflow?"迁移暂存":StorageRules.NAMES[menu.extra.bagTier-1],8,8,UiTheme.TEXT,false);g.text(UiFonts.font(),menu.extra.bagTier+"阶",imageWidth-29,8,UiTheme.MUTED,false);g.text(UiFonts.font(),"玩家背包",8,inventoryLabelY,UiTheme.TEXT,false);
        int used=0;for(int i=0;i<menu.extra.bagLimit();i++)if(!menu.extra.bag.getItem(i).isEmpty())used++;g.text(UiFonts.font(),menu.overflow?"只取不存 · "+menu.overflowUsed()+"格":used+" / "+menu.extra.bagLimit()+" 格",8,imageHeight-17,UiTheme.MUTED,false);
    }
    private void scroll(int row){menu.scrollTo(row);minecraft.gameMode.handleInventoryButtonClick(menu.containerId,100+menu.scrollRow());}
    private boolean scrollbar(double x,double y){return menu.maxScroll()>0&&x>=leftPos+172&&x<=leftPos+185&&y>=topPos+DimensionalMenu.BAG_Y&&y<=topPos+DimensionalMenu.BAG_Y+menu.rows*18;}
    private void drag(double y){int h=menu.rows*18,thumb=Math.max(12,h*menu.rows/Math.max(1,menu.totalRows()));scroll((int)Math.round(Math.clamp((y-topPos-DimensionalMenu.BAG_Y-thumb/2d)/Math.max(1,h-thumb),0,1)*menu.maxScroll()));}
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy){if(x>=leftPos&&x<leftPos+imageWidth&&y>=topPos+DimensionalMenu.BAG_Y&&y<topPos+DimensionalMenu.BAG_Y+menu.rows*18){scroll(menu.scrollRow()-(int)Math.signum(sy));return true;}return super.mouseScrolled(x,y,sx,sy);}
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){if(e.button()==0&&scrollbar(e.x(),e.y())){dragging=true;drag(e.y());return true;}return super.mouseClicked(e,twice);}
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){if(dragging){drag(e.y());return true;}return super.mouseDragged(e,dx,dy);}
    @Override public boolean mouseReleased(MouseButtonEvent e){if(dragging){dragging=false;return true;}return super.mouseReleased(e);}
    @Override public boolean keyPressed(KeyEvent e){if(search!=null&&search.isFocused())return super.keyPressed(e);if(ClientBootstrap.MENU.matches(e)){onClose();minecraft.gui.setScreen(new FightScreen());return true;}return super.keyPressed(e);}
}
