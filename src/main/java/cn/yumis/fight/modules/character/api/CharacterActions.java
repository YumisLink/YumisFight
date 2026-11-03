package cn.yumis.fight.modules.character.api;
import net.minecraft.server.level.ServerPlayer;
import java.util.function.BiConsumer;
/** Character-owned item actions, wired to the application by bootstrap. */
public final class CharacterActions {
    public enum Ticket { PROFESSION_RESET,CAREER_UNLOCK,AWAKENING,LEVEL_ONE,LEVEL_TEN,LEVEL_MAX }
    private static BiConsumer<ServerPlayer,Ticket> use=(player,ticket)->{};
    private CharacterActions(){}
    public static void install(BiConsumer<ServerPlayer,Ticket> action){use=java.util.Objects.requireNonNull(action);}
    public static void use(ServerPlayer player,Ticket ticket){use.accept(player,ticket);}
    public static void clear(){use=(player,ticket)->{};}
}
