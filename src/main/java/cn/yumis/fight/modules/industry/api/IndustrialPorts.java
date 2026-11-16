package cn.yumis.fight.modules.industry.api;

import cn.yumis.fight.modules.inventory.api.InventoryOperations;
import net.minecraft.server.level.ServerPlayer;
import java.util.function.BiConsumer;

/** Lifecycle wiring from bootstrap; industrial code receives inventory and messaging capabilities. */
public final class IndustrialPorts {
    private static InventoryOperations inventory;
    private static BiConsumer<ServerPlayer,String> messages;
    private IndustrialPorts(){}
    public static void install(InventoryOperations operations,BiConsumer<ServerPlayer,String> message){inventory=operations;messages=message;}
    public static void clear(){inventory=null;messages=null;}
    public static boolean ready(){return inventory!=null;}
    public static InventoryOperations inventory(){if(inventory==null)throw new IllegalStateException("服务未就绪");return inventory;}
    public static void message(ServerPlayer player,String message){messages.accept(player,message);}
}
