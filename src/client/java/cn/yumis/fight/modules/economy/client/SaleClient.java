package cn.yumis.fight.modules.economy.client;

import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.MenuInput;
import com.google.gson.JsonObject;

/** Complete, session-bound sale snapshots; choosing slots never sends a request. */
public final class SaleClient {
    private static JsonObject snapshot;
    private static long revision;
    private static boolean bag;
    private SaleClient(){}
    public static void open(boolean dimensional){
        bag=dimensional;
        var player=net.minecraft.client.Minecraft.getInstance().player;
        if(player!=null&&player.containerMenu!=player.inventoryMenu)player.closeContainer();
        MenuInput.open("sale");
    }
    static boolean bag(){return bag;}
    static void bag(boolean value){bag=value;}
    static JsonObject snapshot(){return snapshot!=null&&ClientUi.current().ready()&&snapshot.get("session").getAsString().equals(ClientUi.current().session())?snapshot:null;}
    static long revision(){return revision;}
    static void accept(JsonObject state){snapshot=state;revision++;}
    static void request(){var request=ClientUi.current().operation("saleOpen");request.addProperty("session",ClientUi.current().session());ClientUi.current().send("saleOpen",request);}
}
