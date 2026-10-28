package cn.yumis.fight.modules.economy.api;
import net.minecraft.server.level.ServerPlayer;
import java.util.function.*;
/** Entity callbacks installed and cleared with the server lifecycle. */
public final class LootEntityActions {
    private static Consumer<PrivateLoot> validate=entity->{};
    private static BiConsumer<ServerPlayer,PrivateLoot> pickup=(player,entity)->{};
    private LootEntityActions(){}
    public static void install(Consumer<PrivateLoot> validity,BiConsumer<ServerPlayer,PrivateLoot> claim){validate=validity;pickup=claim;}
    public static void clear(){validate=entity->{};pickup=(player,entity)->{};}
    public static void validate(PrivateLoot entity){validate.accept(entity);}
    public static void pickup(ServerPlayer player,PrivateLoot entity){pickup.accept(player,entity);}
}
