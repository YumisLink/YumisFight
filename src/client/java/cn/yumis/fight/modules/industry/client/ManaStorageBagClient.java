package cn.yumis.fight.modules.industry.client;

import cn.yumis.fight.modules.industry.platform.IndustrialContent;
import cn.yumis.fight.modules.industry.platform.ManaStorageBagItem;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.*;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public final class ManaStorageBagClient {
    private ManaStorageBagClient(){}
    public static void register(IEventBus bus){
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW,ManaStorageBagClient::scroll);
        NeoForge.EVENT_BUS.addListener((ItemTooltipEvent event)->{
            if(event.getItemStack().getItem() instanceof ManaStorageBagItem)
                event.getToolTip().addAll(ManaStorageBagItem.information(event.getItemStack()).stream().map(UiFonts::decorate).toList());
        });
        bus.addListener((RegisterGuiLayersEvent event)->event.registerAboveAll(IndustrialContent.id("mana_storage_bag"),ManaStorageBagClient::render));
    }
    private static InteractionHand hand(){
        var player=Minecraft.getInstance().player;
        if(player==null)return null;
        if(player.getMainHandItem().getItem() instanceof ManaStorageBagItem)return InteractionHand.MAIN_HAND;
        if(player.getOffhandItem().getItem() instanceof ManaStorageBagItem)return InteractionHand.OFF_HAND;
        return null;
    }
    private static void scroll(InputEvent.MouseScrollingEvent event){
        var mc=Minecraft.getInstance();InteractionHand hand=hand();
        if(hand==null||mc.gui.screen()!=null||!mc.hasShiftDown()||!ClientUi.current().ready())return;
        event.setCanceled(true);
        int direction=Integer.signum(event.getAccumulatedScrollY());
        if(direction==0)return;
        var ui=ClientUi.current();var request=ui.operation("manaBagTier");
        request.addProperty("session",ui.session());request.addProperty("hand",hand.name());request.addProperty("direction",direction);
        ui.send("manaBagTier",request);
    }
    private static void render(GuiGraphicsExtractor graphics,DeltaTracker delta){
        var mc=Minecraft.getInstance();InteractionHand hand=hand();
        if(hand==null||mc.gui.screen()!=null||mc.gui.hud.isHidden())return;
        var lines=ManaStorageBagItem.information(mc.player.getItemInHand(hand)).stream().map(UiFonts::decorate).toList();
        var font=UiFonts.font();int width=lines.stream().mapToInt(font::width).max().orElse(0)+16;
        float scale=Math.min(1f,Math.max(1,graphics.guiWidth()-16)/(float)width);
        graphics.pose().pushMatrix();
        graphics.pose().translate((graphics.guiWidth()-width*scale)/2,Math.max(8,graphics.guiHeight()-166));
        graphics.pose().scale(scale,scale);
        cn.yumis.fight.modules.presentation.api.client.UiTheme.hud(graphics,0,0,width,lines.size()*12+8);
        for(int i=0;i<lines.size();i++)graphics.text(font,lines.get(i),8,5+i*12,0xffffffff,true);
        graphics.pose().popMatrix();
    }
}
