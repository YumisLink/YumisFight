package cn.yumis.fight.modules.equipment.client;
import cn.yumis.fight.modules.equipment.client.EquipmentTooltip;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import cn.yumis.fight.modules.inventory.api.SlotAccess;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;

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
    public static boolean isCurrency(ItemStack stack){return SlotAccess.isCurrency(stack);}
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
            var gear=EquipmentItems.gear(equipment);
            if(gear==null)return;
            List<Component> lines=new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(),equipment));
            lines.add(Component.literal("──────── 当前通货 ────────").withColor(0x78869c));
            lines.add(currency.getHoverName());
            lines.addAll(EquipmentTooltip.currencyLines(currency));
            if(cn.yumis.fight.platform.ItemData.id(currency).equals("affix_gem")){try{var gem=cn.yumis.fight.modules.equipment.api.EquipmentJson.JSON.fromJson(EquipmentItems.payload(currency),cn.yumis.fight.modules.equipment.api.Gem.class);double factor=cn.yumis.fight.modules.equipment.api.EquipmentEffects.gemMultiplier(gear);lines.add(Component.literal("镶嵌后数值 ×"+cn.yumis.fight.modules.presentation.api.client.NumberText.number(factor)).withColor(0x85e4d6));for(var original:gem.affixes){var a=cn.yumis.fight.modules.equipment.api.EquipmentJson.copy(original,cn.yumis.fight.modules.affixes.api.Affix.class);if(java.util.Set.of("FLAT","PERCENT","CHARACTER_PERCENT","ALL_RESIST_FLAT","EXTRA").contains(a.operation)){a.value*=factor;a.maximum*=factor;}lines.add(Component.literal(cn.yumis.fight.modules.affixes.api.AffixText.affix(a)).withColor(0x85e4d6));}}catch(RuntimeException ignored){}}
            String reason=EquipmentTooltip.processingReason(currency,gear);
            if(!SlotAccess.isWritable(target.slot(),Minecraft.getInstance().player))reason="该栏位不能加工装备";
            lines.add(Component.literal(reason.isEmpty()?"右键加工 · 消耗1枚":reason).withColor(reason.isEmpty()?0x85e4d6:0xff9696));
            event.getGuiGraphics().setComponentTooltipForNextFrame(UiFonts.font(),lines.stream().map(UiFonts::decorate).toList(),event.getMouseX(),event.getMouseY(),equipment);
        });
    }
}
