package cn.yumis.fight.modules.dungeons.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.context.UseOnContext;
import java.util.function.Consumer;

/** Narrow lifecycle hooks owned by the dungeon training application. */
public final class TrainingActions {
    public interface Handler {
        void cancel(ServerPlayer player,TrainingSandbag bag);
        boolean exists(TrainingSandbag bag);
        boolean consumeVanillaUse(ServerPlayer player,UseOnContext context);
    }
    private static Handler handler;
    private static Consumer<UseOnContext> clientUse=context->{};
    private TrainingActions(){}
    public static void install(Handler value){handler=value;}
    public static void clear(){handler=null;}
    public static void client(Consumer<UseOnContext> value){clientUse=java.util.Objects.requireNonNull(value);}
    public static void clientUse(UseOnContext context){clientUse.accept(context);}
    public static boolean consumeVanillaUse(ServerPlayer player,UseOnContext context){return handler!=null&&handler.consumeVanillaUse(player,context);}
    public static void cancel(ServerPlayer player,TrainingSandbag bag){if(handler!=null)handler.cancel(player,bag);}
    public static boolean exists(TrainingSandbag bag){return handler!=null&&handler.exists(bag);}
}
