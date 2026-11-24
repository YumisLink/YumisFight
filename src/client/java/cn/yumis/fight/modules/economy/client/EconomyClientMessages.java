package cn.yumis.fight.modules.economy.client;
import cn.yumis.fight.modules.presentation.api.client.*;
import cn.yumis.fight.modules.inventory.api.DimensionalMenu;
import cn.yumis.fight.architecture.CommandRouter;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ScreenEvent;
public final class EconomyClientMessages {
    private static JsonObject pending;private static int nextPart,fragmentIndex;private static StringBuilder fragments;
    private EconomyClientMessages(){}
    public static void register(CommandRouter<Void,JsonObject> router){
        router.register("economy","shopBag",(context,message)->ShopClient.bag(message));
        router.register("economy","shopResult",(context,message)->ShopClient.result(message));
        router.register("economy","jarState",(context,message)->JarClient.accept(message));
        router.register("economy","lootAcquired",(context,message)->AutoSellClient.acquired(message));
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Init.Post event)->{
            if(event.getScreen() instanceof AbstractContainerScreen<?> screen&&screen.getMenu() instanceof DimensionalMenu menu){
                int x=screen.getLeftPos()+76,y=screen.getTopPos()+menu.rows*18+174-23;
                event.addListener(Button.builder(Component.literal("出售物品"),b->SaleClient.open(true)).bounds(x,y,50,18).build(ThemedButton::new));
            }
        });
        router.register("economy","saleState",(context,j)->{
            if(!ClientUi.current().ready()||!ClientUi.current().session().equals(j.get("session").getAsString()))return;
            int part=j.get("part").getAsInt();
            if(part==0){pending=j.deepCopy();pending.add("rows",new JsonArray());nextPart=0;fragments=null;fragmentIndex=0;}
            if(pending==null||part!=nextPart||!pending.get("token").equals(j.get("token")))return;
            nextPart++;
            for(var raw:j.getAsJsonArray("rows")){var row=raw.getAsJsonObject();
                if(!row.has("fragment")){if(fragments!=null){pending=null;return;}pending.getAsJsonArray("rows").add(row.deepCopy());continue;}
                int index=row.get("index").getAsInt();if(index==0){if(fragments!=null){pending=null;return;}fragments=new StringBuilder();fragmentIndex=0;}
                if(fragments==null||index!=fragmentIndex++){pending=null;fragments=null;return;}fragments.append(row.get("fragment").getAsString());
                if(row.get("last").getAsBoolean()){pending.getAsJsonArray("rows").add(JsonParser.parseString(fragments.toString()).getAsJsonObject());fragments=null;}
            }
            if(j.get("done").getAsBoolean()){
                var complete=pending;pending=null;
                if(fragments==null){
                    if(complete.has("request"))AutoSellClient.ship(complete);
                    else SaleClient.accept(complete);
                }
                fragments=null;
            }
        });
    }
}
