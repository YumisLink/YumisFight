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
    private final Random random;
    private final cn.yumis.fight.foundation.Journal journal;
    private final LootService loot;
    public ChestService(cn.yumis.fight.foundation.Journal journal,InventoryOperations inventory,RewardPorts.Players players,Random random,LootService loot){this.journal=journal;this.inventory=inventory;this.players=players;this.random=random;this.loot=loot;}
    public static ItemStack waystoneChest(int tier){
        var definition=ChestDefinitions.current().waystone(tier);var stack=ItemRegistry.item("treasure_chest",1);var data=new JsonObject();data.addProperty("id",UUID.randomUUID().toString());data.addProperty("definition","waystone");data.addProperty("tier",tier);data.addProperty("level",80);ItemData.string(stack,"treasure_chest",data.toString());stack.set(DataComponents.CUSTOM_NAME,Component.literal(definition.name().replace("%s",Integer.toString(tier))));return stack;
    }
    public static ItemStack chest(String definition,int tier,int level){
        if(tier<1||tier>10||level<1||level>80)throw new IllegalArgumentException("宝箱实例无效");
        var def=ChestDefinitions.current().require(definition);var stack=ItemRegistry.item("treasure_chest",1);
        JsonObject data=new JsonObject();data.addProperty("id",UUID.randomUUID().toString());data.addProperty("definition",definition);data.addProperty("tier",tier);data.addProperty("level",level);
        ItemData.string(stack,"treasure_chest",data.toString());
        stack.set(DataComponents.CUSTOM_NAME,Component.literal(def.name().replace("%s",ChestDefinitions.TIERS.get(tier-1))));return stack;
    }
    public void open(ServerPlayer p,InteractionHand hand){try{
        var stack=p.getItemInHand(hand);String raw=ItemData.string(stack,"treasure_chest");if(raw.isEmpty())throw new IllegalArgumentException("没有有效的宝箱实例");
        var data=JsonParser.parseString(raw).getAsJsonObject();int tier=data.get("tier").getAsInt(),level=data.get("level").getAsInt();
        if(data.get("definition").getAsString().equals("waystone")){openWaystone(p,hand,data,raw);return;}
        if(tier<1||tier>10||level<1||level>80)throw new IllegalArgumentException("宝箱数据无效");
        var def=ChestDefinitions.current().require(data.get("definition").getAsString());
        String title=stack.getHoverName().getString();
        if(p.containerMenu!=p.inventoryMenu)p.closeContainer();
        double luck=tier*200d+players.stats(p).get(Stat.DROP_RATE);List<ItemStack> result=new ArrayList<>();
        for(int i=0,n=def.equipmentBase()+tier*def.equipmentPerTier();i<n;i++){
            Rarity rarity=LootOdds.rarity(cn.yumis.fight.modules.dungeons.api.Difficulty.NORMAL,true,false,luck,random);if(rarity.ordinal()>Rarity.LEGENDARY.ordinal())rarity=Rarity.LEGENDARY;
            List<Slot> eligible=new ArrayList<>();for(var slot:Slot.values())if(slot.minimum()<=level)eligible.add(slot);
            Slot slot=eligible.get(random.nextInt(eligible.size()));var gear=EquipmentTemplates.createStandard(slot,Weapon.standard().get(random.nextInt(Weapon.standard().size())),rarity,level,random);gear.source="treasure_chest/"+data.get("id").getAsString();result.add(EquipmentItems.gear(gear));
        }
        for(int i=0,n=def.currencyBase()+tier*def.currencyPerTier();i<n;i++)result.add(ItemRegistry.item(CurrencyRules.rollGuaranteed(luck,random).id(),1));
        for(var entry:def.fixed())add(result,entry);
        for(int i=0;i<def.specialRolls();i++){var entry=ChestDefinitions.draw(def.special(),random);if(entry!=null)add(result,entry);}
        JsonObject intent=new JsonObject();intent.addProperty("openChest",data.get("id").getAsString());
        boolean committed=inventory.transact(p,"chest/"+data.get("id").getAsString(),intent,()->{if(!ItemData.string(p.getItemInHand(hand),"treasure_chest").equals(raw))throw new IllegalArgumentException("手中宝箱发生变化");p.getItemInHand(hand).shrink(1);},Map.of());
        if(committed)RewardMenu.open(p,title,result,inventory,this::credit);
    }catch(Exception e){players.message(p,e.getMessage());}}
    private void openWaystone(ServerPlayer p,InteractionHand hand,JsonObject data,String raw){
        int tier=data.get("tier").getAsInt();var definition=ChestDefinitions.current().waystone(tier);double luck=definition.inheritDropRate()?players.stats(p).get(Stat.DROP_RATE):0;List<ItemStack> result=new ArrayList<>();
        for(int i=0,n=random.nextInt(definition.currencyMinimum(),definition.currencyMaximum()+1);i<n;i++)result.add(ItemRegistry.item(CurrencyRules.rollGuaranteed(luck,random).id(),1));
        for(var entry:definition.fixed())add(result,entry);for(int i=0;i<definition.specialRolls();i++){var entry=ChestDefinitions.draw(definition.special(),random);if(entry!=null)add(result,entry);}
        var changes=new LinkedHashMap<String,JsonElement>();var drops=new ArrayList<LootService.Drop>();var intent=new JsonObject();intent.addProperty("openChest",data.get("id").getAsString());
        boolean committed=inventory.transact(p,"chest/"+data.get("id").getAsString(),intent,()->{
            if(!ItemData.string(p.getItemInHand(hand),"treasure_chest").equals(raw))throw new IllegalArgumentException("手中宝箱发生变化");p.getItemInHand(hand).shrink(1);
            for(ItemStack reward:result){var balance=credit(p,reward);if(!balance.isEmpty()){for(var entry:balance.entrySet()){long previous=changes.containsKey(entry.getKey())?changes.get(entry.getKey()).getAsLong():journal.get(entry.getKey(),Long.class,0L);changes.put(entry.getKey(),new JsonPrimitive(Math.addExact(previous,reward.getCount())));}continue;}var remaining=reward.copy();InventoryPacking.insert(p,remaining);while(!remaining.isEmpty()){var drop=loot.prepareOverflow(p,remaining.split(remaining.getMaxStackSize()));drops.add(drop);changes.put("loot/"+drop.id,new Gson().toJsonTree(drop));}}
        },changes);
        if(committed){loot.publishDrops(drops);players.profile(p);players.message(p,"已开启引路石"+tier+"阶宝箱，放不下的奖励已生成在脚下。");}
    }
    private static void add(List<ItemStack> items,ChestDefinitions.Entry entry){
        items.add(item(entry));
    }
    static ItemStack item(ChestDefinitions.Entry entry){if(entry.item().contains(":")){var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(entry.item()));if(item==null||item==net.minecraft.world.item.Items.AIR)throw new IllegalArgumentException("未知奖励物品");return new ItemStack(item,entry.count());}return ItemRegistry.item(entry.item(),entry.count());}
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
