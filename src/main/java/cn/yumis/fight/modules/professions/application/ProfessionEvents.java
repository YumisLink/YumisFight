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
    private static final Map<UUID,List<JsonObject>> pendingHarvest=new LinkedHashMap<>();
    private static final Map<String,Long> recentHerbHarvest=new java.util.concurrent.ConcurrentHashMap<>();
    private ProfessionEvents(){}
    public static void flushPending(net.minecraft.server.MinecraftServer server){
        if(pendingHarvest.isEmpty())return;
        var batch=new LinkedHashMap<>(pendingHarvest);
        pendingHarvest.clear();
        for(var entry:batch.entrySet()){
            var player=server.getPlayerList().getPlayer(entry.getKey());
            if(player==null||player.hasDisconnected())continue;
            var blocks=entry.getValue();
            if(blocks.isEmpty())continue;
            if(blocks.size()==1){
                var one=blocks.getFirst();
                var value=event("harvest");
                value.add("state",one.get("state").deepCopy());
                value.add("drop",one.get("drop").deepCopy());
                value.add("dropAt",one.get("dropAt").deepCopy());
                Network.send(player,"professionEvent",value);
            }else{
                var value=event("harvest");
                var array=new JsonArray();
                for(var row:blocks)array.add(row.deepCopy());
                value.add("blocks",array);
                value.add("dropAt",blocks.getFirst().get("dropAt").deepCopy());
                Network.send(player,"professionEvent",value);
            }
        }
    }
    public static void brewed(ServerPlayer player,String potion){var value=event("brew");value.addProperty("item",potion);Network.send(player,"professionEvent",value);}
    private static JsonObject herb(ServerPlayer player,net.minecraft.core.BlockPos pos,net.minecraft.world.level.block.state.BlockState state,ItemStack tool){
        var value=event("herb");value.add("state",state.CODEC.encodeStart(JsonOps.INSTANCE,state).getOrThrow());
        value.addProperty("dimension",player.level().dimension().identifier().toString());
        var point=new JsonArray();point.add(pos.getX());point.add(pos.getY());point.add(pos.getZ());value.add("pos",point);
        var dropAt=new JsonArray();dropAt.add(pos.getX()+.5);dropAt.add(pos.getY()+.5);dropAt.add(pos.getZ()+.5);value.add("dropAt",dropAt);
        value.add("tool",InventoryEdits.encode(player,tool));return value;
    }
    private static boolean harvest(ServerPlayer player,net.minecraft.core.BlockPos pos,ItemStack tool){
        var state=player.level().getBlockState(pos);if(!(state.getBlock() instanceof cn.yumis.fight.modules.professions.platform.HerbBushBlock))return false;
        long now=System.currentTimeMillis();
        String key=player.getUUID()+"/"+player.level().dimension().identifier()+"/"+pos.asLong();
        Long until=recentHerbHarvest.get(key);
        if(until!=null&&now<until)return true;
        if(recentHerbHarvest.size()>512)recentHerbHarvest.values().removeIf(t->now>=t);
        recentHerbHarvest.put(key,now+cn.yumis.fight.modules.professions.platform.SickleItem.HARVEST_DEDUPE_MILLIS);
        var value=herb(player,pos,state,tool);value.addProperty("harvest",true);value.addProperty("toolSlot",tool==player.getOffhandItem()?40:player.getInventory().getSelectedSlot());
        Network.send(player,"professionEvent",value);return true;
    }
    private static JsonObject event(String kind){var value=new JsonObject();value.addProperty("kind",kind);value.addProperty("event",UUID.randomUUID().toString());return value;}
    public static void berry(ServerPlayer player,net.minecraft.world.level.block.state.BlockState before,net.minecraft.world.level.block.state.BlockState after){var value=event("berry");value.add("before",net.minecraft.world.level.block.state.BlockState.CODEC.encodeStart(JsonOps.INSTANCE,before).getOrThrow());value.add("after",net.minecraft.world.level.block.state.BlockState.CODEC.encodeStart(JsonOps.INSTANCE,after).getOrThrow());Network.send(player,"professionEvent",value);}
    private static void item(ServerPlayer player,String kind,ItemStack item){var value=event(kind);value.addProperty("item",BuiltInRegistries.ITEM.getKey(item.getItem()).getPath());value.add("output",InventoryEdits.encode(player,item));Network.send(player,"professionEvent",value);}
    public static void register(java.util.function.BooleanSupplier ready,java.util.function.Supplier<ProfessionService> service){
        cn.yumis.fight.modules.professions.platform.SickleItem.HANDLER=ProfessionEvents::harvest;
        cn.yumis.fight.modules.professions.platform.RareSickleItem.HANDLER=ProfessionEvents::harvest;
        cn.yumis.fight.modules.professions.platform.ArtifactSickleItem.HANDLER=ProfessionEvents::harvest;
        cn.yumis.fight.modules.professions.platform.LegendarySickleItem.HANDLER=ProfessionEvents::harvest;
        cn.yumis.fight.modules.professions.platform.EpicSickleItem.HANDLER=ProfessionEvents::harvest;
        cn.yumis.fight.modules.professions.platform.MythicSickleItem.HANDLER=ProfessionEvents::harvest;
        NeoForge.EVENT_BUS.addListener((BlockDropsEvent e)->{
            if(!ready.getAsBoolean()||e.isCanceled()||!(e.getBreaker() instanceof ServerPlayer player))return;
            if(e.getState().getBlock() instanceof cn.yumis.fight.modules.professions.platform.HerbBushBlock){Network.send(player,"professionEvent",herb(player,e.getPos(),e.getState(),e.getTool()));return;}
            if(e.getDrops().isEmpty())return;
            var entry=new JsonObject();
            entry.add("state",net.minecraft.world.level.block.state.BlockState.CODEC.encodeStart(JsonOps.INSTANCE,e.getState()).getOrThrow());
            entry.add("drop",InventoryEdits.encode(player,e.getDrops().getFirst().getItem()));
            var point=new JsonArray();point.add(e.getPos().getX()+.5);point.add(e.getPos().getY()+.5);point.add(e.getPos().getZ()+.5);
            entry.add("dropAt",point);
            pendingHarvest.computeIfAbsent(player.getUUID(),id->new ArrayList<>()).add(entry);
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e)->{
            if(!ready.getAsBoolean()){pendingHarvest.clear();return;}
            flushPending(e.getServer());
        });
        NeoForge.EVENT_BUS.addListener((ItemFishedEvent e)->{if(ready.getAsBoolean()&&!e.isCanceled()&&e.getEntity() instanceof ServerPlayer player&&!e.getDrops().isEmpty()){var value=event("fishing");value.addProperty("hook",e.getHookEntity().getStringUUID());var hands=new JsonArray();hands.add(InventoryEdits.encode(player,player.getMainHandItem()));hands.add(InventoryEdits.encode(player,player.getOffhandItem()));value.add("hands",hands);Network.send(player,"professionEvent",value);}});
        NeoForge.EVENT_BUS.addListener((PlayerEvent.ItemCraftedEvent e)->{if(!ready.getAsBoolean()||!(e.getEntity() instanceof ServerPlayer player)||e.getCrafting().isEmpty())return;var value=event("craft");value.addProperty("item",BuiltInRegistries.ITEM.getKey(e.getCrafting().getItem()).getPath());value.add("output",InventoryEdits.encode(player,e.getCrafting()));for(int i=0;i<e.getInventory().getContainerSize();i++)if(!e.getInventory().getItem(i).isEmpty()){value.add("input",InventoryEdits.encode(player,e.getInventory().getItem(i)));break;}Network.send(player,"professionEvent",value);});
        NeoForge.EVENT_BUS.addListener((PlayerEvent.ItemSmeltedEvent e)->{if(ready.getAsBoolean()&&e.getEntity() instanceof ServerPlayer player&&!e.getSmelting().isEmpty())item(player,"smelt",e.getSmelting());});
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.brewing.PlayerBrewedPotionEvent e)->{if(ready.getAsBoolean()&&e.getEntity() instanceof ServerPlayer player&&!e.getStack().isEmpty())item(player,"brew",e.getStack());});
        NeoForge.EVENT_BUS.addListener((PlayerEnchantItemEvent e)->{if(ready.getAsBoolean()&&e.getEntity() instanceof ServerPlayer player&&!e.getEnchantedItem().isEmpty())item(player,"enchant",e.getEnchantedItem());});
    }
}
