package cn.yumis.fight.modules.dungeons.api;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import java.util.function.BiConsumer;
/** Server item-use port, installed by application composition. */
public final class TreasureActions {
    private static BiConsumer<ServerPlayer,InteractionHand> use=(p,h)->{};
    public static void install(BiConsumer<ServerPlayer,InteractionHand> action){use=action;}
    public static void use(ServerPlayer p,InteractionHand hand){use.accept(p,hand);}
    private TreasureActions(){}
}
