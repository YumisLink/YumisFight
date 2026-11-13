package cn.yumis.fight.modules.mapgeneration.platform;

import cn.yumis.fight.modules.mapgeneration.api.WorldDecorations;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Reuse the complete native item-placement pipeline, including canceled placements and refunds. */
public final class DecorationPlacement {
    private DecorationPlacement(){}
    public static void register(){NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,DecorationPlacement::stick);}
    private static void stick(PlayerInteractEvent.RightClickBlock event){
        var player=event.getEntity();
        var held=event.getItemStack();
        if(event.isCanceled()||event.getUseItem()==TriState.FALSE||!held.is(Items.STICK)||!player.isShiftKeyDown()||!player.mayBuild()||player.isSpectator())return;
        var proxy=new ItemStack(WorldDecorations.block("ground_branches").asItem(),held.getCount());
        var context=new UseOnContext(event.getLevel(),player,event.getHand(),proxy,event.getHitVec());
        var result=proxy.useOn(context);
        if(result.consumesAction())held.setCount(proxy.getCount());
        event.setCancellationResult(result);
        event.setCanceled(true);
    }
}
