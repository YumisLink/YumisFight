package cn.yumis.fight.modules.economy.client;

import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.modules.economy.api.*;
import cn.yumis.fight.modules.inventory.api.*;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;
import java.util.function.BiPredicate;

/** Local affordability and frozen slot edits; the receiver only stores the authored result. */
public final class ShopClient {
    public record Result(String operation,boolean success,String message) {}
    private static Supplier<JsonObject> profile;
    private static BooleanSupplier living=()->false;
    private static BiPredicate<LivingEntity,String> hostAvailable=(entity,shop)->false;
    private static List<ItemStack> bag=List.of();
    private static int bagMenu=-1,nextPart;
    private static boolean ready;
    private static String pending="";
    private static long pendingSequence;
    private static Result result;
    private ShopClient(){}
    public static void install(CommandRouter<Void,JsonObject> preparations,Supplier<JsonObject> view,BooleanSupplier alive,BiPredicate<LivingEntity,String> available){profile=view;living=alive;hostAvailable=available;preparations.register("economy","shopPurchase",(ignored,j)->prepare(j));}
    public static void clear(){bag=List.of();bagMenu=-1;nextPart=0;ready=false;pending="";pendingSequence=0;result=null;}
    public static boolean ready(ShopMenu menu){return ready&&bagMenu==menu.containerId;}
    public static boolean pending(){return !pending.isEmpty();}
    public static Result result(){return result;}
    public static LivingEntity host(ShopMenu menu){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return null;
        return mc.level.getEntitiesOfClass(LivingEntity.class,mc.player.getBoundingBox().inflate(48),e->e.getUUID().equals(menu.host)).stream().findFirst().orElse(null);
    }
    public static boolean valid(ShopMenu menu){var mc=Minecraft.getInstance();var entity=host(menu);return living.getAsBoolean()&&mc.player!=null&&mc.player.isAlive()&&entity!=null&&entity.isAlive()&&hostAvailable.test(entity,menu.shop)&&mc.player.isWithinEntityInteractionRange(entity,0)&&mc.player.hasLineOfSight(entity);}
    public static long gold(){var value=profile.get();return value.has("gold")?value.get("gold").getAsLong():0;}
    public static long owned(ShopDefinitions.Cost cost){
        if(cost.gold())return gold();var player=Minecraft.getInstance().player;if(player==null)return 0;
        var required=cost.stack();long total=0;
        for(int i=0;i<36;i++){var item=player.getInventory().getItem(i);if(ItemStack.isSameItemSameComponents(item,required))total+=item.getCount();}
        for(var item:bag)if(ItemStack.isSameItemSameComponents(item,required))total+=item.getCount();return total;
    }
    public static boolean affordable(ShopMenu menu,ShopDefinitions.Offer offer,int quantity){return ready(menu)&&quantity>=1&&quantity<=ShopDefinitions.MAX_QUANTITY&&offer.costs().stream().allMatch(cost->owned(cost)>=Math.multiplyExact(cost.count(),quantity));}
    public static void bag(JsonObject value){
        var mc=Minecraft.getInstance();if(mc.player==null||!(mc.player.containerMenu instanceof ShopMenu menu)||!ClientUi.current().session().equals(value.get("session").getAsString())||value.get("menu").getAsInt()!=menu.containerId)return;
        int part=value.get("part").getAsInt();
        if(part==0){bagMenu=menu.containerId;nextPart=0;ready=false;bag=new ArrayList<>(Collections.nCopies(StorageRules.capacity(value.get("tier").getAsInt()),ItemStack.EMPTY));}
        if(bagMenu!=menu.containerId||part!=nextPart++){ready=false;return;}
        for(var raw:value.getAsJsonArray("rows")){var row=raw.getAsJsonObject();int slot=row.get("slot").getAsInt();if(slot<0||slot>=bag.size()){ready=false;return;}bag.set(slot,InventoryEdits.decode(mc.player,row.get("item")));}
        ready=value.get("done").getAsBoolean();
    }
    public static void result(JsonObject value){
        if(!ClientUi.current().session().equals(value.get("session").getAsString()))return;
        String operation=value.get("operation").getAsString();if(!operation.equals(pending))return;
        pending="";pendingSequence=0;result=new Result(operation,value.get("success").getAsBoolean(),value.get("message").getAsString());ClientUi.current().message(result.message());
    }
    public static void personalCommit(JsonObject value){
        if(pending()&&pendingSequence==value.get("sequence").getAsLong()&&!value.get("received").getAsBoolean()){
            result=new Result(pending,false,"购买状态已变化，请重新确认");pending="";pendingSequence=0;
        }
    }
    public static void buy(ShopMenu menu,ShopDefinitions.Offer offer,int quantity){
        if(pending()||!valid(menu)||!affordable(menu,offer,quantity))return;
        var request=ClientUi.current().operation("shopPurchase");request.addProperty("menu",menu.containerId);request.addProperty("shop",menu.shop);request.addProperty("offer",offer.id());request.addProperty("quantity",quantity);
        pending=request.get("operation").getAsString();result=null;ClientUi.current().send("shopPurchase",request);
    }
    private record Slot(String area,int index,ItemStack before,ItemStack after) {}
    private static void prepare(JsonObject request){
        try{
            pendingSequence=request.get("clientTransaction").getAsLong();
            var mc=Minecraft.getInstance();var player=mc.player;
            if(player==null||!(player.containerMenu instanceof ShopMenu menu)||menu.containerId!=request.get("menu").getAsInt()||!valid(menu)||!ready(menu))throw new IllegalArgumentException("商店已关闭或库存尚未同步");
            int quantity=request.get("quantity").getAsInt();var offer=menu.definition.offers().stream().filter(o->o.id().equals(request.get("offer").getAsString())).findFirst().orElseThrow(()->new IllegalArgumentException("商品已变化"));
            if(!affordable(menu,offer,quantity))throw new IllegalArgumentException("购买数量无效或费用不足");
            var sources=new ArrayList<Slot>();for(int i=0;i<36;i++){var stack=player.getInventory().getItem(i);sources.add(new Slot("inventory",i,stack.copy(),stack.copy()));}
            for(int i=0;i<bag.size();i++)sources.add(new Slot("bag",i,bag.get(i).copy(),bag.get(i).copy()));
            long goldCost=0;
            for(var cost:offer.costs()){
                long remaining=Math.multiplyExact(cost.count(),quantity);if(cost.gold()){goldCost=Math.addExact(goldCost,remaining);continue;}
                var required=cost.stack();for(var slot:sources)if(remaining>0&&ItemStack.isSameItemSameComponents(slot.after(),required)){int take=(int)Math.min(remaining,slot.after().getCount());slot.after().shrink(take);remaining-=take;}
                if(remaining>0)throw new IllegalArgumentException("道具费用已变化，请重试");
            }
            var edits=new JsonArray();for(var slot:sources)if(!ItemStack.matches(slot.before(),slot.after()))edits.add(InventoryEdits.change(player,slot.area(),slot.index(),slot.before(),slot.after()));request.add("inventoryChanges",edits);
            if(goldCost>0){var before=new JsonObject();var after=new JsonObject();long revision=profile.get().get("goldRevision").getAsLong();before.addProperty("gold",gold());before.addProperty("goldRevision",revision);after.addProperty("gold",Math.subtractExact(gold(),goldCost));after.addProperty("goldRevision",Math.incrementExact(revision));request.add("beforeBalances",before);request.add("balances",after);}
            var outputs=new JsonArray();var item=offer.stack();for(int remaining=quantity;remaining>0;){int count=Math.min(remaining,item.getMaxStackSize());outputs.add(InventoryEdits.encode(player,item.copyWithCount(count)));remaining-=count;}request.add("shopOutputs",outputs);
        }catch(RuntimeException failure){pending="";pendingSequence=0;throw failure;}
    }
}
