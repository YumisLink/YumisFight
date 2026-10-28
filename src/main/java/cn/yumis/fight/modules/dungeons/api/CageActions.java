package cn.yumis.fight.modules.dungeons.api;
import net.minecraft.server.level.ServerPlayer;
import com.google.gson.JsonObject;
import java.util.function.BiConsumer;
public final class CageActions {
    private static BiConsumer<ServerPlayer,JsonObject> hit=(player,request)->{};
    private CageActions(){}
    public static void install(BiConsumer<ServerPlayer,JsonObject> handler){hit=handler;}
    public static void clear(){hit=(player,request)->{};}
    public static void hit(ServerPlayer player,JsonObject request){hit.accept(player,request);}
}
