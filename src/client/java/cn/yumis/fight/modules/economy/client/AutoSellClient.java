package cn.yumis.fight.modules.economy.client;

import cn.yumis.fight.modules.economy.api.AutoSellRule;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.modules.inventory.api.MenuHooks;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import com.google.gson.JsonArray;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/** 监视本机普通与次元背包；命中配置规则的新获得装备走既有报价出售链路（客户端权威）。 */
public final class AutoSellClient {
    private static final Set<String> SEEN=new HashSet<>();
    private static final Set<String> PENDING=new LinkedHashSet<>();
    private static boolean seeded,requested;
    private static long requestedAt,revision=SaleClient.revision();
    private AutoSellClient(){}
    public static void tick(){
        var player=Minecraft.getInstance().player;
        if(player==null||!ClientUi.current().ready()){SEEN.clear();PENDING.clear();seeded=false;requested=false;return;}
        boolean active=AutoSellSettings.enabled()&&!AutoSellSettings.rules().isEmpty();
        if(!seeded){scan(player,false);seeded=true;}
        else scan(player,active);
        long current=SaleClient.revision();
        if(current!=revision){
            revision=current;
            if(requested){requested=false;ship();}
        }
        if(PENDING.isEmpty())return;
        if(requested){
            if(ClientUi.now()-requestedAt>=10000)requested=false;
            return;
        }
        if(player.containerMenu!=player.inventoryMenu)return; // saleOpen 会关闭打开中的容器，先等待
        requested=true;requestedAt=ClientUi.now();SaleClient.request();
    }
    private static void scan(LocalPlayer player,boolean active){
        for(int slot=0;slot<36;slot++)observe(player.getInventory().getItem(slot),active);
        var extra=MenuHooks.extra(player);
        for(int slot=0;slot<extra.bagLimit();slot++)observe(extra.bag.getItem(slot),active);
    }
    private static void observe(ItemStack stack,boolean active){
        var gear=EquipmentItems.gear(stack);
        if(gear==null||gear.instance==null||gear.rarity==null||gear.creativeTemplate)return;
        if(gear.level<AutoSellRule.LEVEL_MIN||gear.level>AutoSellRule.LEVEL_MAX)return;
        String id=gear.instance.toString();
        if(!SEEN.add(id))return;
        if(active&&AutoSellRule.matches(AutoSellSettings.rules(),gear.rarity,gear.level))PENDING.add(id);
    }
    private static void ship(){
        var player=Minecraft.getInstance().player;
        if(player==null)return;
        if(player.containerMenu!=player.inventoryMenu){requested=false;return;} // 容器关闭后再重试
        var quote=SaleClient.snapshot();
        if(quote==null){PENDING.clear();return;}
        var keys=new JsonArray();
        for(var raw:quote.getAsJsonArray("rows")){
            var row=raw.getAsJsonObject();
            if(!row.has("equipment"))continue;
            if(PENDING.remove(row.get("equipment").getAsString())&&row.get("allowed").getAsBoolean()&&row.has("price"))keys.add(row.get("key").getAsString());
        }
        PENDING.clear(); // 未出现在报价中的装备已不在背包，不重试
        if(keys.isEmpty())return;
        var request=ClientUi.current().operation("sell");
        request.addProperty("session",ClientUi.current().session());
        request.addProperty("menu",quote.get("menu").getAsInt());
        request.addProperty("token",quote.get("token").getAsString());
        request.add("items",keys);
        ClientUi.current().send("sell",request);
    }
}
