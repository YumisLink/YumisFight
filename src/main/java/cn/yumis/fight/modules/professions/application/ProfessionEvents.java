package cn.yumis.fight.modules.professions.application;

import cn.yumis.fight.modules.inventory.api.InventoryEdits;
import cn.yumis.fight.network.Network;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import java.util.*;

/** Relays raw world interactions. No profession level, reward roll or player movement is decided here. */
public final class ProfessionEvents {
    private ProfessionEvents(){}
    private static JsonObject event(String kind){var value=new JsonObject();value.addProperty("kind",kind);value.addProperty("event",UUID.randomUUID().toString());return value;}
    public static void berry(ServerPlayer player,net.minecraft.world.level.block.state.BlockState before,net.minecraft.world.level.block.state.BlockState after){var value=event("berry");value.add("before",net.minecraft.world.level.block.state.BlockState.CODEC.encodeStart(JsonOps.INSTANCE,before).getOrThrow());value.add("after",net.minecraft.world.level.block.state.BlockState.CODEC.encodeStart(JsonOps.INSTANCE,after).getOrThrow());Network.send(player,"professionEvent",value);}
    private static void item(ServerPlayer player,String kind,ItemStack item){var value=event(kind);value.addProperty("item",BuiltInRegistries.ITEM.getKey(item.getItem()).getPath());value.add("output",InventoryEdits.encode(player,item));Network.send(player,"professionEvent",value);}
    public static void register(java.util.function.BooleanSupplier ready,java.util.function.Supplier<ProfessionService> service){
        NeoForge.EVENT_BUS.addListener((BlockDropsEvent e)->{if(ready.getAsBoolean()&&!e.isCanceled()&&e.getBreaker() instanceof ServerPlayer player&&!e.getDrops().isEmpty()){var value=event("harvest");value.add("state",net.minecraft.world.level.block.state.BlockState.CODEC.encodeStart(JsonOps.INSTANCE,e.getState()).getOrThrow());value.add("drop",InventoryEdits.encode(player,e.getDrops().getFirst().getItem()));var point=new JsonArray();point.add(e.getPos().getX()+.5);point.add(e.getPos().getY()+.5);point.add(e.getPos().getZ()+.5);value.add("dropAt",point);Network.send(player,"professionEvent",value);}});
        NeoForge.EVENT_BUS.addListener((ItemFishedEvent e)->{if(ready.getAsBoolean()&&!e.isCanceled()&&e.getEntity() instanceof ServerPlayer player&&!e.getDrops().isEmpty()){var value=event("fishing");value.addProperty("hook",e.getHookEntity().getStringUUID());var hands=new JsonArray();hands.add(InventoryEdits.encode(player,player.getMainHandItem()));hands.add(InventoryEdits.encode(player,player.getOffhandItem()));value.add("hands",hands);Network.send(player,"professionEvent",value);}});
        NeoForge.EVENT_BUS.addListener((PlayerEvent.ItemCraftedEvent e)->{if(!ready.getAsBoolean()||!(e.getEntity() instanceof ServerPlayer player)||e.getCrafting().isEmpty())return;var value=event("craft");value.addProperty("item",BuiltInRegistries.ITEM.getKey(e.getCrafting().getItem()).getPath());value.add("output",InventoryEdits.encode(player,e.getCrafting()));for(int i=0;i<e.getInventory().getContainerSize();i++)if(!e.getInventory().getItem(i).isEmpty()){value.add("input",InventoryEdits.encode(player,e.getInventory().getItem(i)));break;}Network.send(player,"professionEvent",value);});
        NeoForge.EVENT_BUS.addListener((PlayerEvent.ItemSmeltedEvent e)->{if(ready.getAsBoolean()&&e.getEntity() instanceof ServerPlayer player&&!e.getSmelting().isEmpty())item(player,"smelt",e.getSmelting());});
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.brewing.PlayerBrewedPotionEvent e)->{if(ready.getAsBoolean()&&e.getEntity() instanceof ServerPlayer player&&!e.getStack().isEmpty())item(player,"brew",e.getStack());});
        NeoForge.EVENT_BUS.addListener((PlayerEnchantItemEvent e)->{if(ready.getAsBoolean()&&e.getEntity() instanceof ServerPlayer player&&!e.getEnchantedItem().isEmpty())item(player,"enchant",e.getEnchantedItem());});
    }
}
