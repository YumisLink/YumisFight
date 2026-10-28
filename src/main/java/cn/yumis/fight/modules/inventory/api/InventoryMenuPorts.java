package cn.yumis.fight.modules.inventory.api;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.*;
/** Platform wiring for the dimensional inventory; recipes and scheduling are supplied by their owners. */
public final class InventoryMenuPorts {
    public record Upgrade(Function<List<ItemStack>,int[]> planner,String requirements){public int[] takePlan(List<ItemStack> stacks){return planner.apply(stacks);}}
    private static Supplier<MenuType<?>> menuType;
    private static BiFunction<ServerPlayer,Integer,Upgrade> upgrades;
    private static BiConsumer<ServerPlayer,Runnable> deferred;
    private static BiConsumer<ServerPlayer,Boolean> opened;
    private InventoryMenuPorts(){}
    public static synchronized void install(Supplier<MenuType<?>> type,BiFunction<ServerPlayer,Integer,Upgrade> recipes,BiConsumer<ServerPlayer,Runnable> scheduler,BiConsumer<ServerPlayer,Boolean> lifecycle){menuType=Objects.requireNonNull(type);upgrades=Objects.requireNonNull(recipes);deferred=Objects.requireNonNull(scheduler);opened=Objects.requireNonNull(lifecycle);}
    public static MenuType<?> type(){return Objects.requireNonNull(menuType,"Inventory menus have not been wired").get();}
    public static Upgrade upgrade(ServerPlayer player,int tier){return upgrades.apply(player,tier);}
    public static void defer(ServerPlayer player,Runnable action){deferred.accept(player,action);}
    public static void opened(ServerPlayer player,boolean open){opened.accept(player,open);}
}

