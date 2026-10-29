package cn.yumis.fight.modules.industry.client;
import cn.yumis.fight.modules.industry.platform.IndustrialMenu;
import cn.yumis.fight.modules.industry.client.IndustrialVisuals;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import java.util.*;
public final class IndustryClientMessages {
    private record Parts(String[] text,String hash,long created){}
    private static final Map<String,Parts> PREPARING=new LinkedHashMap<>();
    private IndustryClientMessages(){}
    public static void register(cn.yumis.fight.architecture.CommandRouter<Void,JsonObject> router){
        router.register("industry","industrial_prepare",(context,j)->{
            long now=net.minecraft.util.Util.getMillis();PREPARING.values().removeIf(p->now-p.created>10000);String token=j.get("token").getAsString();int count=j.get("count").getAsInt(),at=j.get("part").getAsInt();if(count<1||count>1024||at<0||at>=count)return;
            if(PREPARING.size()>=8&&!PREPARING.containsKey(token))PREPARING.remove(PREPARING.keySet().iterator().next());
            Parts parts=PREPARING.computeIfAbsent(token,k->new Parts(new String[count],j.get("hash").getAsString(),now));if(parts.text.length!=count||!parts.hash.equals(j.get("hash").getAsString()))return;parts.text[at]=j.get("text").getAsString();
            if(Arrays.stream(parts.text).anyMatch(Objects::isNull))return;String raw=String.join("",parts.text);PREPARING.remove(token);if(!cn.yumis.fight.modules.industry.platform.IndustrialOpening.digest(raw).equals(parts.hash))return;
            IndustrialMenu.initial(token,JsonParser.parseString(raw).getAsJsonObject());JsonObject ready=new JsonObject();ready.addProperty("token",token);cn.yumis.fight.modules.presentation.api.client.ClientUi.current().send("industrialReady",ready);
        });
        router.register("industry","industrial_lines",(context,j)->{IndustrialVisuals.receive(j);});
        router.register("industry","industrial_ui",(context,j)->{if(Minecraft.getInstance().player!=null&&Minecraft.getInstance().player.containerMenu instanceof cn.yumis.fight.modules.industry.platform.IndustrialMenu menu&&menu.containerId==j.get("menu").getAsInt())menu.info=j;});
    }
}
