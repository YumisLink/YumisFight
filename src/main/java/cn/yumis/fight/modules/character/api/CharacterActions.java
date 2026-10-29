package cn.yumis.fight.modules.character.api;
import net.minecraft.server.level.ServerPlayer;
import java.util.function.BiConsumer;
/** Item use enters the character application without a dependency on bootstrap. */
public final class CharacterActions {
    private static BiConsumer<ServerPlayer,Boolean> use=(p,base)->{};
    private CharacterActions(){}
    public static void install(BiConsumer<ServerPlayer,Boolean> action){use=java.util.Objects.requireNonNull(action);}
    public static void reset(ServerPlayer player,boolean base){use.accept(player,base);}
    public static void clear(){use=(p,base)->{};}
}
