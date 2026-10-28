package cn.yumis.fight.modules.inventory.api;

import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import java.util.Map;

/** Cross-module inventory port. Changes and item mutations are committed by one inventory transaction. */
public interface InventoryOperations {
    boolean locked(java.util.UUID player);
    boolean transact(ServerPlayer player,String operation,JsonObject intent,Runnable mutation,Map<String,JsonElement> changes);
    boolean transactMachine(ServerPlayer player,String operation,JsonObject intent,PersistentContainer container,Runnable mutation,Map<String,JsonElement> changes);
    JsonElement encode(ServerPlayer player,ItemStack stack);
    ItemStack decode(ServerPlayer player,JsonElement value);
    void restore(ServerPlayer player);
    void requireSpace(ServerPlayer player,ItemStack stack);
    void closeCursor(ServerPlayer player);
    void click(ServerPlayer player,AbstractContainerMenu menu,int slot,int button,ContainerInput input);
}
