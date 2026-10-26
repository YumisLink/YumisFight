package cn.yumis.fight.client;
import cn.yumis.fight.game.*;
import cn.yumis.fight.mixin.CreativeSlotAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ScreenEvent;
import java.util.*;
public final class CurrencyUi {
    public record Target(AbstractContainerMenu menu,Slot slot,int index){}
    private CurrencyUi(){}
    public static boolean isCurrency(ItemStack stack){return CurrencySlots.isCurrency(stack);}
    public static Target target(AbstractContainerScreen<?> screen,double mouseX,double mouseY){
        var player=Minecraft.getInstance().player;
        if(player==null)return null;
        double x=mouseX-screen.getLeftPos(),y=mouseY-screen.getTopPos();
        for(Slot visible:screen.getMenu().slots){
            if(!visible.isActive()||x<visible.x-1||x>=visible.x+17||y<visible.y-1||y>=visible.y+17)continue;
            Slot actual=visible;
            AbstractContainerMenu menu=screen.getMenu();
            if(visible instanceof CreativeSlotAccessor wrapper){actual=wrapper.ydnf$target();menu=player.inventoryMenu;}
            else if(screen instanceof CreativeModeInventoryScreen){
                if(visible.container!=player.getInventory())return null;
                actual=player.inventoryMenu.slots.stream().filter(s->s.container==visible.container&&s.getContainerSlot()==visible.getContainerSlot()).findFirst().orElse(null);
                menu=player.inventoryMenu;
            }
            int index=menu.slots.indexOf(actual);
            return index<0?null:new Target(menu,actual,index);
        }
        return null;
    }
    private static boolean registered;
    private static String notice="";
    private static long noticeUntil;
    public static void notice(String text){notice=text==null?"":text;noticeUntil=System.currentTimeMillis()+4000;}
    public static void register(){
        if(registered)return;registered=true;
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Render.Post event)->{
            if((event.getScreen() instanceof AbstractContainerScreen<?>||event.getScreen() instanceof GemScreen)&&System.currentTimeMillis()<noticeUntil&&!notice.isEmpty()){
                var g=event.getGuiGraphics();var font=UiFonts.font();
                int width=event.getScreen().width,x=Math.max(6,(width-font.width(notice))/2),y=6;
                g.fill(x-4,y-3,Math.min(width-2,x+font.width(notice)+4),y+font.lineHeight+3,0xe0201111);
                g.text(font,notice,x,y,UiTheme.ERROR,false);
            }
        });
        // Foreground runs before Screen extracts its deferred tooltip; Post is too late.
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Render.Foreground event)->{
            if(!(event.getScreen() instanceof AbstractContainerScreen<?> screen))return;
            ItemStack currency=screen.getMenu().getCarried();
            if(!isCurrency(currency))return;
            Target target=target(screen,event.getMouseX(),event.getMouseY());
            if(target==null)return;
            ItemStack equipment=target.slot().getItem();
            var gear=GameItems.gear(equipment);
            if(gear==null)return;
            List<Component> lines=new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(),equipment));
            lines.add(Component.literal("──────── 当前通货 ────────").withColor(0x78869c));
            lines.add(currency.getHoverName());
            lines.addAll(Tooltips.currencyLines(currency));
            String reason=Tooltips.processingReason(currency,gear);
            if(!CurrencySlots.isWritable(target.slot(),Minecraft.getInstance().player))reason="该栏位不能加工装备";
            lines.add(Component.literal(reason.isEmpty()?"右键加工 · 消耗1枚":reason).withColor(reason.isEmpty()?0x85e4d6:0xff9696));
            event.getGuiGraphics().setComponentTooltipForNextFrame(UiFonts.font(),lines.stream().map(UiFonts::decorate).toList(),event.getMouseX(),event.getMouseY(),equipment);
        });
    }
}
