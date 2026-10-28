package cn.yumis.fight.modules.inventory.api;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.Objects;
/** Inventory asks the owning gameplay module whether a stack can occupy an accessory group. */
public final class WearablePolicy {
    @FunctionalInterface public interface Rule {boolean accepts(Player player,String group,ItemStack stack);}
    private static volatile Rule rule=(p,g,s)->false;
    private WearablePolicy(){}
    public static void install(Rule value){rule=Objects.requireNonNull(value);}
    public static boolean accepts(Player player,String group,ItemStack stack){return rule.accepts(player,group,stack);}
}
