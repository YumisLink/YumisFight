package cn.yumis.fight.client;

import net.minecraft.world.item.crafting.RecipeMap;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Native recipe synchronization; this class has no JEI linkage and is safe without JEI. */
public final class IndustrialRecipeCache {
    public static RecipeMap recipes=RecipeMap.EMPTY;
    public static void register(){
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH,(RecipesReceivedEvent event)->recipes=event.getRecipeMap());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event)->recipes=RecipeMap.EMPTY);
    }
}
