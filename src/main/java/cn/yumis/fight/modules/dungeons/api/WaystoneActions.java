package cn.yumis.fight.modules.dungeons.api;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Module-owned interaction boundary, installed by application composition. */
public final class WaystoneActions {
    public interface Handler {
        void open(ServerPlayer player,WaystonePedestalBlockEntity table);
        boolean countingDown(WaystonePedestalBlockEntity table);
        void removed(WaystonePedestalBlockEntity table);
        void recover();
    }
    private static Handler handler;
    private WaystoneActions(){}
    public static void install(Handler next){handler=next;}
    public static void open(ServerPlayer player,WaystonePedestalBlockEntity table){if(handler!=null)handler.open(player,table);}
    public static void recover(){if(handler!=null)handler.recover();}
    public static void registerEvents(){NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.block.BreakBlockEvent e)->{
        if(e.getLevel().getBlockEntity(e.getPos()) instanceof WaystonePedestalBlockEntity table){
            if(!table.isEmpty()||handler!=null&&handler.countingDown(table)){e.setCanceled(true);if(e.getPlayer() instanceof ServerPlayer p)p.sendSystemMessage(net.minecraft.network.chat.Component.literal("请先取下引路石或取消倒数。"));}
            else if(handler!=null)handler.removed(table);
        }
    });}
}
