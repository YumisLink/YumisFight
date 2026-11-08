package cn.yumis.fight.modules.economy.client;

import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.modules.economy.api.*;
import cn.yumis.fight.modules.equipment.api.*;
import cn.yumis.fight.modules.inventory.api.InventoryEdits;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.platform.*;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import java.util.*;
import java.util.function.*;

/** Personal prices, costs and reward randomness; the server receives fixed inventory/account results. */
public final class EconomyActions {
    private static final Gson JSON=new Gson();private static final Random RANDOM=new Random();
    private static Supplier<JsonObject> profile;private static Supplier<Progress> progress;private static Supplier<Stats> stats;private static BooleanSupplier jarEligible;
    private static final Set<String> offered=new HashSet<>();
    public static void clear(){offered.clear();}
    private EconomyActions(){}
    public static void install(CommandRouter<Void,JsonObject> router,Supplier<JsonObject> view,Supplier<Progress> growth,Supplier<Stats> attributes,BooleanSupplier canOpen){
        profile=view;progress=growth;stats=attributes;jarEligible=canOpen;
        router.register("economy","purchase",(ignored,j)->purchase(j));router.register("economy","sell",(ignored,j)->sale(j,false));router.register("economy","dismantle",(ignored,j)->sale(j,true));
        router.register("economy","jarFinish",(ignored,j)->{});
        router.register("economy","jarStart",(ignored,j)->jar(j));router.register("economy","chestOpen",(ignored,j)->chest(j));router.register("economy","lootReward",(ignored,j)->kill(j));
        LootJarItem.opening((player,hand)->{if(player.level().isClientSide())open("jarStart",hand);});TreasureChestItem.opening((player,hand)->{if(player.level().isClientSide())open("chestOpen",hand);});
    }
    private static void open(String action,InteractionHand hand){var j=ClientUi.current().operation(action);j.addProperty("hand",hand.name());ClientUi.current().send(action,j);}
    private static long balance(String id){return profile.get().get(id).getAsLong();}
    private static void balances(JsonObject j,Map<String,Long> values){var before=new JsonObject();var after=new JsonObject();for(var entry:values.entrySet()){before.addProperty(entry.getKey(),balance(entry.getKey()));after.addProperty(entry.getKey(),entry.getValue());}if(values.containsKey("gold")){before.addProperty("goldRevision",balance("goldRevision"));after.addProperty("goldRevision",Math.incrementExact(balance("goldRevision")));}j.add("beforeBalances",before);j.add("balances",after);}
    private static void purchase(JsonObject j){
        String product=j.get("product").getAsString();long price=CurrencyRules.ticketPrice(product);var outputs=new JsonArray();
        if(price>=0){if(balance("points")<price)throw new IllegalArgumentException("点券不足，需要"+price+"点券");balances(j,Map.of("points",balance("points")-price));outputs.add(InventoryEdits.encode(Minecraft.getInstance().player,ItemRegistry.item(product,1)));}
        else if(product.equals("overlord_contract")){if(balance("gold")<CurrencyRules.CONTRACT_PRICE)throw new IllegalArgumentException("金币不足，需要100000金币");balances(j,Map.of("gold",balance("gold")-CurrencyRules.CONTRACT_PRICE));j.addProperty("beforeContract",balance("contractUntil"));j.addProperty("contractUntil",Math.addExact(Math.max(ClientUi.current().serverNow(),balance("contractUntil")),CurrencyRules.CONTRACT_DURATION));}
        else throw new IllegalArgumentException("商品未上架");j.add("outputs",outputs);
    }
    static void decorate(JsonObject state){
        var player=Minecraft.getInstance().player;
        for(var raw:state.getAsJsonArray("rows")){var row=raw.getAsJsonObject();var stack=row.has("source")?InventoryEdits.decode(player,row.get("source")):ItemStack.EMPTY;var gear=EquipmentItems.gear(stack);boolean hand=!row.get("bag").getAsBoolean()&&row.get("slot").getAsInt()==player.getInventory().getSelectedSlot();boolean allowed=gear!=null&&!gear.creativeTemplate&&stack.getCount()==1&&ItemData.own(stack)&&!hand;
            row.addProperty("allowed",allowed);row.addProperty("reason",allowed?"":row.get("empty").getAsBoolean()?"空槽":hand?"当前主手不可处理":gear!=null&&gear.creativeTemplate?"装备正在初始化":"不是可处理的装备");
            if(gear==null)continue;row.addProperty("equipment",gear.instance.toString());row.addProperty("revision",gear.revision);row.addProperty("rarity",gear.rarity.name());row.addProperty("price",CurrencyRules.salePrice(gear));row.addProperty("gems",gear.gems.size());row.addProperty("dismantle",(gear.rarity==Rarity.EPIC||gear.rarity==Rarity.MYTHIC?"1个":"1～3个同种随机")+String.join("／",CurrencyRules.dismantlePool(gear.rarity).stream().map(id->ItemRegistry.item(id,1).getHoverName().getString()).toList()));var lines=new JsonArray();for(var affix:gear.affixes){var line=new JsonObject();line.addProperty("id",cn.yumis.fight.modules.affixes.api.AffixResolver.definitionId(affix));line.addProperty("text",cn.yumis.fight.modules.affixes.api.AffixText.affix(affix));lines.add(line);}row.add("lines",lines);
        }
    }
    private static void sale(JsonObject j,boolean dismantle){
        var player=Minecraft.getInstance().player;var quote=SaleClient.snapshot();if(quote==null||!quote.get("token").equals(j.get("token")))throw new IllegalArgumentException("装备快照已失效");
        var rows=new HashMap<String,JsonObject>();for(var raw:quote.getAsJsonArray("rows"))rows.put(raw.getAsJsonObject().get("key").getAsString(),raw.getAsJsonObject());var seen=new HashSet<String>();var edits=new JsonArray();var drops=new JsonArray();long total=0;
        for(var raw:j.getAsJsonArray("items")){String key=raw.getAsString();var row=rows.get(key);if(!seen.add(key)||row==null||!row.get("allowed").getAsBoolean())throw new IllegalArgumentException("请选择有效装备");String area=row.get("bag").getAsBoolean()?"bag":"inventory";int slot=row.get("slot").getAsInt();var source=InventoryEdits.source(player,area,slot);
            if(!row.get("source").equals(InventoryEdits.encode(player,source))||area.equals("inventory")&&slot==player.getInventory().getSelectedSlot())throw new IllegalArgumentException("装备已变化，请刷新后重试");var gear=EquipmentItems.gear(source);total=Math.addExact(total,CurrencyRules.salePrice(gear));edits.add(InventoryEdits.change(player,area,slot,source,ItemStack.EMPTY));
            if(dismantle){var result=CurrencyRules.dismantle(gear.rarity,RANDOM);drops.add(InventoryEdits.encode(player,ItemRegistry.item(result.item(),result.count())));}
        }
        if(edits.isEmpty())throw new IllegalArgumentException("请选择有效装备");j.add("inventoryChanges",edits);j.add("drops",drops);if(!dismantle){balances(j,Map.of("gold",Math.addExact(balance("gold"),total)));j.addProperty("goldGain",total);}
    }
    private static void draft(JsonObject request,String field){
        String value=request.get(field).toString();if(value.length()<=64000)return;String hash=cn.yumis.fight.modules.economy.application.EconomyService.Drafts.hash(value);var chunks=new ArrayList<String>();
        for(int from=0;from<value.length();){int to=Math.min(value.length(),from+16000);if(to<value.length()&&Character.isHighSurrogate(value.charAt(to-1)))to--;chunks.add(value.substring(from,to));from=to;}
        for(int i=0;i<chunks.size();i++){var part=new JsonObject();part.add("operation",request.get("operation"));part.add("session",request.get("session"));part.addProperty("hash",hash);part.addProperty("part",i);part.addProperty("count",chunks.size());part.addProperty("text",chunks.get(i));ClientUi.current().send("economyDraft",part);}
        request.remove(field);request.addProperty(field+"Draft",hash);
    }
    private static void consume(JsonObject j){var player=Minecraft.getInstance().player;var hand=InteractionHand.valueOf(j.get("hand").getAsString());var source=player.getItemInHand(hand);var after=source.copy();after.shrink(1);int slot=hand==InteractionHand.MAIN_HAND?player.getInventory().getSelectedSlot():40;var changes=new JsonArray();changes.add(InventoryEdits.change(player,"inventory",slot,source,after));j.add("inventoryChanges",changes);}
    private static void jar(JsonObject j){
        if(JarClient.blocking())throw new IllegalArgumentException("请先完成当前开罐");if(!jarEligible.getAsBoolean())throw new IllegalArgumentException("请在副本外脱战且存活时开罐");var player=Minecraft.getInstance().player;var source=player.getItemInHand(InteractionHand.valueOf(j.get("hand").getAsString()));String definition=LootJarItem.definition(source);var rule=JarDefinitions.current().require(definition);String id=j.get("operation").getAsString();var value=new JsonObject();value.addProperty("id",id);value.addProperty("definition",definition);value.addProperty("name",rule.name());value.addProperty("dimension",player.level().dimension().identifier().toString());value.addProperty("startedAt",ClientUi.current().serverNow());value.addProperty("seed",RANDOM.nextLong());value.addProperty("offset",RANDOM.nextInt(7)-3);value.add("entries",JSON.toJsonTree(rule.entries().stream().filter(e->e.weight()>0).toList()));value.add("reward",InventoryEdits.encode(player,JarDefinitions.create(JarDefinitions.draw(rule,RANDOM),RANDOM,"jar/"+definition+"/"+id)));value.addProperty("delivered",false);value.addProperty("dropped",false);j.add("draw",value);consume(j);draft(j,"draw");
    }
    private static void chest(JsonObject j){var player=Minecraft.getInstance().player;var source=player.getItemInHand(InteractionHand.valueOf(j.get("hand").getAsString()));var data=JsonParser.parseString(ItemData.string(source,"treasure_chest")).getAsJsonObject();var result=ChestRewards.generate(data,stats.get(),RANDOM);var rewards=new JsonArray();for(var item:result)rewards.add(InventoryEdits.encode(player,item));j.add("rewards",rewards);j.addProperty("chest",data.get("id").getAsString());j.addProperty("title",source.getHoverName().getString());j.addProperty("direct",data.get("definition").getAsString().equals("waystone"));consume(j);draft(j,"rewards");}
    private static void kill(JsonObject j){if(j.has("experienceGain")){var current=progress.get();var next=cn.yumis.fight.modules.character.api.CareerProfiles.copy(current);next.grant(j.get("experienceGain").getAsLong());j.add("beforeProgress",JSON.toJsonTree(current));j.add("progress",JSON.toJsonTree(next));balances(j,Map.of("gold",Math.addExact(balance("gold"),j.get("goldGain").getAsLong())));return;}var context=JSON.fromJson(j.get("context"),RewardRules.Context.class);var current=progress.get();var result=RewardRules.kill(current,stats.get(),Minecraft.getInstance().player.getUUID(),context,RANDOM);j.add("beforeProgress",JSON.toJsonTree(current));j.add("progress",JSON.toJsonTree(result.progress()));balances(j,Map.of("gold",Math.addExact(balance("gold"),result.gold())));j.addProperty("goldGain",result.gold());j.addProperty("experienceGain",result.experience());if(result.experience()==0&&result.gold()==0&&result.drops().isEmpty())j.addProperty("unchanged",true);var drops=new JsonArray();for(var item:result.drops()){var row=new JsonObject();row.add("item",InventoryEdits.encode(Minecraft.getInstance().player,item.item()));row.addProperty("privateLoot",item.privateLoot());drops.add(row);}j.add("rewardDrops",drops);}
    public static void reward(JsonObject context){if(!offered.add(context.get("id").getAsString()))return;var j=ClientUi.current().operation("lootReward");j.add("context",context);j.addProperty("retryStorage",true);ClientUi.current().send("lootReward",j);}
}
