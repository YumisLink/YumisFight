package cn.yumis.fight.modules.inventory.client;
import cn.yumis.fight.modules.inventory.api.MenuHooks;
import cn.yumis.fight.modules.inventory.api.DimensionalMenu;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import java.util.*;
public final class InventoryClientMessages {
    private InventoryClientMessages(){}
    public static void register(cn.yumis.fight.architecture.CommandRouter<Void,JsonObject> router){
        router.register("inventory","storage_ui",(context,j)->{if(Minecraft.getInstance().player!=null&&Minecraft.getInstance().player.containerMenu instanceof DimensionalMenu menu&&j.get("menu").getAsInt()==menu.containerId&&j.get("tier").getAsInt()==menu.extra.bagTier)menu.preview=j;});
    }
    public static void applyEquipment(String raw){
        try{
            if(raw.isEmpty()||Minecraft.getInstance().player==null)return;
            JsonObject j=JsonParser.parseString(raw).getAsJsonObject();
            var p=Minecraft.getInstance().player;
            var context=p.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            var extra=MenuHooks.extra(p);
            if(j.has("bagTier")){extra.bagTier=j.get("bagTier").getAsInt();extra.bagSchema=j.get("bagSchema").getAsInt();extra.bagPage=j.get("bagPage").getAsInt();}
            if(j.has("bag")){JsonArray bag=j.getAsJsonArray("bag");extra.bag.clearContent();for(int i=0;i<Math.min(extra.bagLimit(),bag.size());i++)extra.bag.setItem(i,ItemStack.OPTIONAL_CODEC.parse(context,bag.get(i)).getOrThrow());}
        }
        catch(RuntimeException e){
            cn.yumis.fight.modules.presentation.api.client.ClientUi.current().message("装备状态解码失败："+e.getMessage());
        }
    }
}
