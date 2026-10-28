package cn.yumis.fight.modules.industry.client;
import cn.yumis.fight.modules.industry.platform.IndustrialMenu;
import cn.yumis.fight.modules.industry.client.IndustrialVisuals;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import java.util.*;
public final class IndustryClientMessages {
    private IndustryClientMessages(){}
    public static void register(cn.yumis.fight.architecture.CommandRouter<Void,JsonObject> router){
        router.register("industry","industrial_lines",(context,j)->{IndustrialVisuals.receive(j);});
        router.register("industry","industrial_ui",(context,j)->{if(Minecraft.getInstance().player!=null&&Minecraft.getInstance().player.containerMenu instanceof cn.yumis.fight.modules.industry.platform.IndustrialMenu menu&&menu.containerId==j.get("menu").getAsInt())menu.info=j;});
    }
}
