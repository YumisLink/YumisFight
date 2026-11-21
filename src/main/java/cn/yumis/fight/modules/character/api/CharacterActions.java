package cn.yumis.fight.modules.character.api;
import net.minecraft.world.entity.player.Player;
import java.util.function.BiConsumer;
/** Character-owned local item actions, wired by client composition. */
public final class CharacterActions {
    public enum Ticket { CAREER_UNLOCK,AWAKENING,PROFILE_EXPANSION,LEVEL_ONE,LEVEL_TEN,LEVEL_MAX }
    private static BiConsumer<Player,Ticket> use=(player,ticket)->{};
    private CharacterActions(){}
    public static void install(BiConsumer<Player,Ticket> action){use=java.util.Objects.requireNonNull(action);}
    public static void use(Player player,Ticket ticket){use.accept(player,ticket);}
}
