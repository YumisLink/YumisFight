package cn.yumis.fight.modules.economy.application;

import cn.yumis.fight.modules.economy.api.*;
import cn.yumis.fight.modules.inventory.api.*;
import cn.yumis.fight.modules.equipment.api.*;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.platform.*;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Generates rewards once at consumption; the take-only menu intentionally has no reconnect recovery. */
public final class ChestService {
    private final InventoryOperations inventory;
    private final RewardPorts.Players players;
    private final cn.yumis.fight.foundation.Journal journal;
    private final LootService loot;
    private final EconomyService.Drafts drafts;
    public ChestService(cn.yumis.fight.foundation.Journal journal,InventoryOperations inventory,RewardPorts.Players players,LootService loot,EconomyService.Drafts drafts){this.journal=journal;this.inventory=inventory;this.players=players;this.loot=loot;this.drafts=drafts;}
    public static ItemStack chest(String definition,int tier,int level){
        if(tier<1||tier>10||level<1||level>80)throw new IllegalArgumentException("宝箱实例无效");
        var def=ChestDefinitions.current().require(definition);var stack=ItemRegistry.item("treasure_chest",1);
        JsonObject data=new JsonObject();data.addProperty("id",UUID.randomUUID().toString());data.addProperty("definition",definition);data.addProperty("tier",tier);data.addProperty("level",level);
        ItemData.string(stack,"treasure_chest",data.toString());
        stack.set(DataComponents.CUSTOM_NAME,Component.literal(def.name().replace("%s",ChestDefinitions.TIERS.get(tier-1))));return stack;
    }
    public void register(cn.yumis.fight.architecture.CommandRouter<ServerPlayer,JsonObject> router){router.register("economy","chestOpen",this::receive);}
    public void receive(ServerPlayer p,JsonObject request){
        var prior=journal.get("inventoryReceipt/"+p.getUUID()+"/"+request.get("operation").getAsString());if(!prior.isJsonNull()){if(!prior.getAsJsonObject().get("intent").equals(request))throw new IllegalArgumentException("操作号冲突");inventory.restore(p);players.profile(p);return;}
        var rewards=new ArrayList<ItemStack>();for(var raw:drafts.value(p,request,"rewards").getAsJsonArray())rewards.add(inventory.decode(p,raw));
        var changes=new LinkedHashMap<String,JsonElement>();var drops=new ArrayList<LootService.Drop>();boolean direct=request.get("direct").getAsBoolean();
        boolean committed=inventory.transact(p,request.get("operation").getAsString(),request,()->{
            InventoryEdits.apply(p,inventory,request);
            if(direct)for(var reward:rewards){
                var balance=credit(p,reward);if(!balance.isEmpty()){for(var entry:balance.entrySet()){long previous=changes.containsKey(entry.getKey())?changes.get(entry.getKey()).getAsLong():journal.get(entry.getKey(),Long.class,0L);changes.put(entry.getKey(),new JsonPrimitive(Math.addExact(previous,reward.getCount())));}continue;}
                var remaining=reward.copy();InventoryPacking.insert(p,remaining);while(!remaining.isEmpty()){var drop=loot.prepareOverflow(p,remaining.split(remaining.getMaxStackSize()));drops.add(drop);changes.put("loot/"+drop.id,new Gson().toJsonTree(drop));}
            }
        },changes);
        if(committed){if(direct){loot.publishDrops(drops);players.message(p,"已开启宝箱，放不下的奖励已生成在脚下。");}else RewardMenu.open(p,request.get("title").getAsString(),rewards,inventory,this::credit);players.profile(p);}
    }
    private Map<String,JsonElement> credit(ServerPlayer p,ItemStack stack){if(!ItemData.own(stack)||!ItemData.id(stack).equals(CurrencyRules.COLORLESS_CRYSTAL.id()))return Map.of();String key=CurrencyRules.COLORLESS_CRYSTAL.id()+"/"+p.getUUID();return Map.of(key,new JsonPrimitive(Math.addExact(journal.get(key,Long.class,0L),stack.getCount())));}
    public static void commands(com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> root,java.util.function.Supplier<ChestService> service){
        var countNode=net.minecraft.commands.Commands.argument("count",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,64)).executes(c->{
                ServerPlayer p=net.minecraft.commands.arguments.EntityArgument.getPlayer(c,"player");String definition=com.mojang.brigadier.arguments.StringArgumentType.getString(c,"definition");
                int tier=com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"tier"),level=com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"level"),count=com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"count");
                JsonObject intent=new JsonObject();intent.addProperty("grantChest",definition);intent.addProperty("tier",tier);intent.addProperty("level",level);intent.addProperty("count",count);
                var instance=service.get();instance.inventory.transact(p,"admin-chests/"+UUID.randomUUID(),intent,()->{for(int i=0;i<count;i++)instance.inventory.requireSpace(p,chest(definition,tier,level));},Map.of());return count;
        });
        var levelNode=net.minecraft.commands.Commands.argument("level",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,80)).then(countNode);
        var tierNode=net.minecraft.commands.Commands.argument("tier",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,10)).then(levelNode);
        var definitionNode=net.minecraft.commands.Commands.argument("definition",com.mojang.brigadier.arguments.StringArgumentType.word()).suggests((c,b)->{ChestDefinitions.current().chests.keySet().forEach(b::suggest);return b.buildFuture();}).then(tierNode);
        var playerNode=net.minecraft.commands.Commands.argument("player",net.minecraft.commands.arguments.EntityArgument.player()).then(definitionNode);
        root.then(net.minecraft.commands.Commands.literal("treasure").then(net.minecraft.commands.Commands.literal("give").requires(s->s.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)).then(playerNode)));
    }
}
