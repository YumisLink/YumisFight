package cn.yumis.fight.modules.industry.api;

import cn.yumis.fight.modules.inventory.api.InventoryOperations;
import net.minecraft.server.level.ServerPlayer;
import java.util.function.BiConsumer;

/** Lifecycle wiring from bootstrap; industrial code receives only inventory and recovery capabilities. */
public final class IndustrialPorts {
    private static InventoryOperations inventory;
    private static Runnable recovery;
    private static BiConsumer<ServerPlayer,String> messages;
    private IndustrialPorts(){}
    public static void install(InventoryOperations operations,Runnable recover,BiConsumer<ServerPlayer,String> message){inventory=operations;recovery=recover;messages=message;}
    public static void clear(){inventory=null;recovery=null;messages=null;}
    public static boolean ready(){return inventory!=null;}
    public static InventoryOperations inventory(){if(inventory==null)throw new IllegalStateException("服务未就绪");return inventory;}
    public static void recover(){recovery.run();}
    public static void message(ServerPlayer player,String message){messages.accept(player,message);}
}
