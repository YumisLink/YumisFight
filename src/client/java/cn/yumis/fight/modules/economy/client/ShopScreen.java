package cn.yumis.fight.modules.economy.client;

import cn.yumis.fight.modules.economy.api.*;
import cn.yumis.fight.modules.equipment.api.client.MythicDisplay;
import cn.yumis.fight.modules.presentation.api.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import java.util.*;
import org.lwjgl.glfw.GLFW;

/** A fixed double-chest storefront with an in-place modal, preserving its container lifecycle. */
public final class ShopScreen extends ThemedContainerScreen<ShopMenu> {
    private ShopDefinitions.Offer selected;
    private EditBox quantity;
    private Button minus,plus,cancel,buy;
    private boolean submitted;
    private String feedback="";
    public boolean modal(){return selected!=null;}
    public ShopScreen(ShopMenu menu,Inventory inventory,Component title){super(menu,inventory,title,176,222);inventoryLabelY=128;}
    private int modalWidth(){return Math.min(300,width-12);}
    private int modalHeight(){return selected==null?154:154+Math.max(0,selected.costs().size()-1)*14;}
    private int mx(){return (width-modalWidth())/2;}
    private int my(){return (height-modalHeight())/2;}
    @Override protected void init(){super.init();if(selected!=null)controls();}
    private void choose(int index){if(index<0||index>=menu.definition.offers().size()||ShopClient.pending())return;selected=menu.definition.offers().get(index);feedback="";submitted=false;controls();}
    private void controls(){
        clearWidgets();int left=mx(),top=my(),w=modalWidth();
        quantity=addWidget(new EditBox(UiFonts.font(),left+w/2-36,top+51,72,20,Component.literal("购买数量")));quantity.setMaxLength(3);quantity.setValue("1");quantity.setFocused(true);setFocused(quantity);
        minus=button("−",left+w/2-65,top+51,24,b->change(-1));plus=button("+",left+w/2+41,top+51,24,b->change(1));
        cancel=button("取消",left+12,top+modalHeight()-30,(w-30)/2,b->closeModal());buy=button("购买",left+18+(w-30)/2,top+modalHeight()-30,(w-30)/2,b->submit());
        quantity.setResponder(value->refreshControls());refreshControls();
    }
    private Button button(String label,int x,int y,int w,Button.OnPress action){return addWidget(Button.builder(Component.literal(label),action).bounds(x,y,w,20).build(ThemedButton::new));}
    private int count(){try{return Integer.parseInt(quantity.getValue());}catch(NumberFormatException invalid){return 0;}}
    private void change(int delta){if(!ShopClient.pending())quantity.setValue(Integer.toString(Math.clamp(count()+delta,1,ShopDefinitions.MAX_QUANTITY)));}
    private void closeModal(){if(ShopClient.pending())return;selected=null;quantity=null;clearWidgets();setFocused(null);}
    private void submit(){if(selected!=null&&ShopClient.affordable(menu,selected,count())&&!ShopClient.pending()){ShopClient.buy(menu,selected,count());submitted=ShopClient.pending();}}
    private void refreshControls(){boolean pending=ShopClient.pending();buy.active=!pending&&ShopClient.affordable(menu,selected,count());cancel.active=!pending;minus.active=!pending;plus.active=!pending;quantity.setEditable(!pending);}
    @Override protected void containerTick(){
        super.containerTick();if(!ShopClient.valid(menu)){minecraft.player.closeContainer();return;}
        if(selected!=null){boolean pending=ShopClient.pending();refreshControls();
            if(submitted&&!pending&&ShopClient.result()!=null){submitted=false;if(ShopClient.result().success())closeModal();else feedback=ShopClient.result().message();}
        }
    }
    @Override protected void drawBackground(GuiGraphicsExtractor g,int x,int y,float delta){
        g.fill(0,0,width,height,UiTheme.OVERLAY);UiTheme.panel(g,leftPos,topPos,imageWidth,imageHeight);
        for(var slot:menu.slots)UiTheme.slot(g,leftPos+slot.x-1,topPos+slot.y-1);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g,int x,int y){g.text(UiFonts.font(),title,8,6,UiTheme.TEXT,false);g.text(UiFonts.font(),playerInventoryTitle,8,128,UiTheme.MUTED,false);}
    @Override protected void extractTooltip(GuiGraphicsExtractor g,int x,int y){
        if(selected!=null)return;
        if(hoveredSlot!=null&&hoveredSlot.index<54&&hoveredSlot.index<menu.definition.offers().size()){
            var offer=menu.definition.offers().get(hoveredSlot.index);var item=offer.stack();var lines=new ArrayList<Component>();
            lines.add(UiFonts.decorate(Component.literal("单件价格").withColor(UiTheme.TIP_TITLE)));for(var cost:offer.costs())lines.add(costLine(cost,1));
            lines.add(Component.empty());lines.addAll(Screen.getTooltipFromItem(minecraft,item));lines.add(UiFonts.decorate(Component.literal("点击选择数量 · 最多640件").withColor(UiTheme.TIP_MUTED)));
            g.setComponentTooltipForNextFrame(UiFonts.font(),lines,x,y,item);
        }else super.extractTooltip(g,x,y);
    }
    private Component costLine(ShopDefinitions.Cost cost,int n){long required=Math.multiplyExact(cost.count(),Math.max(1,n)),owned=ShopClient.owned(cost);String label=cost.gold()?CurrencyRules.GOLD.title():cost.stack().getHoverName().getString();return UiFonts.decorate(Component.literal(label+" × "+required+"  （拥有 "+owned+"）").withColor(owned>=required?0x55b873:0xe05b61));}
    @Override protected void drawContents(GuiGraphicsExtractor g,int x,int y,float delta){
        super.drawContents(g,x,y,delta);if(selected==null)return;
        g.fill(0,0,width,height,UiTheme.OVERLAY);int left=mx(),top=my(),w=modalWidth();UiTheme.panel(g,left,top,w,modalHeight());var item=selected.stack();var rarity=ItemRarity.rarity(item);
        g.text(UiFonts.font(),"购买 "+UiFonts.font().plainSubstrByWidth(item.getHoverName().getString(),w-64),left+12,top+12,0xff000000|rarity.color(),false);g.item(item,left+12,top+32);cn.yumis.fight.modules.economy.api.client.RarityFrames.draw(g,item,left+12,top+32,false);
        g.text(UiFonts.font(),"数量（1～640）",left+36,top+32,UiTheme.MUTED,false);
        int rowY=top+81;for(var cost:selected.costs()){g.text(UiFonts.font(),costLine(cost,count()),left+12,rowY,UiTheme.TEXT,false);rowY+=14;}
        String status=ShopClient.pending()?"正在购买…":!ShopClient.ready(menu)?"正在同步库存…":count()<1||count()>640?"请输入1～640的整数":feedback;
        if(!status.isEmpty())g.text(UiFonts.font(),UiFonts.font().plainSubstrByWidth(status,w-24),left+12,top+modalHeight()-47,UiTheme.ERROR,false);
        quantity.extractRenderState(g,x,y,delta);minus.extractRenderState(g,x,y,delta);plus.extractRenderState(g,x,y,delta);cancel.extractRenderState(g,x,y,delta);buy.extractRenderState(g,x,y,delta);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice){
        if(selected!=null){if(ShopClient.pending())return true;quantity.mouseClicked(event,twice);for(var b:List.of(minus,plus,cancel,buy))if(b.mouseClicked(event,twice))break;return true;}
        int col=(int)((event.x()-leftPos-8)/18),row=(int)((event.y()-topPos-18)/18);
        if(event.x()>=leftPos+8&&event.x()<leftPos+170&&event.y()>=topPos+18&&event.y()<topPos+126){if(event.button()==0)choose(row*9+col);return true;}
        return super.mouseClicked(event,twice);
    }
    @Override protected void slotClicked(Slot slot,int index,int button,ContainerInput input){if(selected==null&&(index<0||index>=54))super.slotClicked(slot,index,button,input);}
    @Override public boolean mouseReleased(MouseButtonEvent event){if(selected!=null){quantity.mouseReleased(event);return true;}return super.mouseReleased(event);}
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){if(selected!=null){if(!ShopClient.pending())quantity.mouseDragged(event,dx,dy);return true;}return super.mouseDragged(event,dx,dy);}
    @Override public boolean mouseScrolled(double x,double y,double sx,double sy){return selected!=null||super.mouseScrolled(x,y,sx,sy);}
    @Override public boolean keyPressed(KeyEvent event){if(selected==null)return super.keyPressed(event);if(event.key()==GLFW.GLFW_KEY_ESCAPE){closeModal();return true;}if(ShopClient.pending())return true;if(event.key()==GLFW.GLFW_KEY_ENTER||event.key()==GLFW.GLFW_KEY_KP_ENTER){submit();return true;}quantity.keyPressed(event);return true;}
    @Override public boolean charTyped(CharacterEvent event){return selected!=null?(ShopClient.pending()||quantity.charTyped(event)):super.charTyped(event);}
    @Override public void onClose(){if(ShopClient.pending())return;if(selected!=null){closeModal();return;}super.onClose();}
}
