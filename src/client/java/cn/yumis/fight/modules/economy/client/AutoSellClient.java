package cn.yumis.fight.modules.economy.client;

import cn.yumis.fight.modules.economy.api.AutoSellRule;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.LinkedHashSet;
import java.util.Set;

/** 按入包事实筛选新战利品，再通过既有报价和库存事务出售。 */
public final class AutoSellClient {
    private static final Set<String> QUEUED=new LinkedHashSet<>();
    private static final Set<String> IN_FLIGHT=new LinkedHashSet<>();
    private static String requested="";
    private static long requestedAt;
    private record PendingSale(String operation,JsonObject quote,Set<String> equipment) {}
    private static PendingSale pendingSale;
    private AutoSellClient(){}

    public static void clear(){
        clearQuotes();
        pendingSale=null;
    }
    private static void clearQuotes(){
        QUEUED.clear();
        IN_FLIGHT.clear();
        requested="";
    }
    private static boolean active(){return AutoSellSettings.enabled()&&!AutoSellSettings.rules().isEmpty();}
    private static boolean idle(LocalPlayer player){
        return player!=null&&player.containerMenu==player.inventoryMenu&&!SaleClient.manualOpen();
    }
    static void acquired(JsonObject j){
        var ui=ClientUi.current();
        if(!ui.ready()||!ui.session().equals(j.get("session").getAsString())||!active())return;
        int level=j.get("level").getAsInt();
        if(level<AutoSellRule.LEVEL_MIN||level>AutoSellRule.LEVEL_MAX)return;
        var rarity=Rarity.valueOf(j.get("rarity").getAsString());
        if(AutoSellRule.matches(AutoSellSettings.rules(),rarity,level))QUEUED.add(j.get("instance").getAsString());
    }
    public static void tick(){
        var player=Minecraft.getInstance().player;
        if(player==null||!ClientUi.current().ready()){clear();return;}
        if(!active()){clearQuotes();return;}
        if(!requested.isEmpty()&&ClientUi.now()-requestedAt>=10000){
            requested="";
            requeue();
        }
        if(!requested.isEmpty()||pendingSale!=null||QUEUED.isEmpty()||!idle(player))return;
        var request=ClientUi.current().operation("saleOpen");
        request.addProperty("automatic",true);
        IN_FLIGHT.addAll(QUEUED);
        QUEUED.clear();
        requested=request.get("operation").getAsString();
        requestedAt=ClientUi.now();
        ClientUi.current().send("saleOpen",request);
    }
    private static void requeue(){
        QUEUED.addAll(IN_FLIGHT);
        IN_FLIGHT.clear();
    }
    static boolean mainHand(LocalPlayer player,JsonObject row){
        return !row.get("bag").getAsBoolean()&&row.get("slot").getAsInt()==player.getInventory().getSelectedSlot();
    }
    static void ship(JsonObject quote){
        if(requested.isEmpty()||!requested.equals(quote.get("request").getAsString()))return;
        requested="";
        if(!active()){clearQuotes();return;}
        var player=Minecraft.getInstance().player;
        if(!idle(player)){requeue();return;}
        EconomyActions.decorate(quote);
        var keys=new JsonArray();
        Set<String> equipment=new LinkedHashSet<>();
        for(var raw:quote.getAsJsonArray("rows")){
            var row=raw.getAsJsonObject();
            if(!row.has("equipment"))continue;
            String id=row.get("equipment").getAsString();
            if(IN_FLIGHT.remove(id)&&!mainHand(player,row)&&row.get("allowed").getAsBoolean()&&row.has("price")){
                equipment.add(id);
                keys.add(row.get("key").getAsString());
            }
        }
        IN_FLIGHT.clear();
        if(keys.isEmpty())return;
        var request=ClientUi.current().operation("sell");
        request.addProperty("automatic",true);
        request.addProperty("menu",quote.get("menu").getAsInt());
        request.addProperty("token",quote.get("token").getAsString());
        request.add("items",keys);
        pendingSale=new PendingSale(request.get("operation").getAsString(),quote,equipment);
        ClientUi.current().send("sell",request);
    }
    static JsonObject prepareSale(JsonObject request,LocalPlayer player){
        var sale=pendingSale;
        if(sale==null||!sale.operation().equals(request.get("operation").getAsString()))return null;
        if(!active())return null;
        if(!idle(player)){
            QUEUED.addAll(sale.equipment());
            return null;
        }
        return sale.quote();
    }
    public static void finished(JsonObject request){
        if(pendingSale!=null&&pendingSale.operation().equals(request.get("operation").getAsString()))pendingSale=null;
    }
}
