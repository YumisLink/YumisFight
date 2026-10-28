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
    private static JsonObject pending;private static int nextPart;
    private EconomyClientMessages(){}
    public static void register(CommandRouter<Void,JsonObject> router){
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Init.Post event)->{
            if(event.getScreen() instanceof InventoryScreen||event.getScreen() instanceof AbstractContainerScreen<?> s&&s.getMenu() instanceof DimensionalMenu){
                int x=Math.max(2,event.getScreen().width/2-88),y=Math.max(2,event.getScreen().height-25);
                event.addListener(Button.builder(Component.literal("出售装备"),b->{var request=ClientUi.current().operation("saleOpen");request.addProperty("session",ClientUi.current().session());ClientUi.current().send("saleOpen",request);}).bounds(x,y,92,20).build(ThemedButton::new));
            }
        });
        router.register("economy","saleState",(context,j)->{
            if(!ClientUi.current().ready()||!ClientUi.current().session().equals(j.get("session").getAsString()))return;
            int part=j.get("part").getAsInt();
            if(part==0){pending=j.deepCopy();pending.add("rows",new JsonArray());nextPart=0;}
            if(pending==null||part!=nextPart||!pending.get("token").equals(j.get("token")))return;
            nextPart++;j.getAsJsonArray("rows").forEach(row->pending.getAsJsonArray("rows").add(row.deepCopy()));
            if(j.get("done").getAsBoolean()){Minecraft.getInstance().gui.setScreen(new SaleScreen(pending));pending=null;}
        });
    }
}
