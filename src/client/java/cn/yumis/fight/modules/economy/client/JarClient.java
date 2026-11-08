package cn.yumis.fight.modules.economy.client;

import cn.yumis.fight.modules.economy.api.JarDefinitions;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.presentation.api.client.ThemedScreen;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ScreenEvent;
import java.util.*;

/** One frozen display snapshot. The owner's stored winner is never replaced by a display roll. */
public final class JarClient {
    static final class View {
        final String id,name,dimension,session;
        final long startedAt,duration;
        final ItemStack winner;
        final List<ItemStack> strip;
        final int winnerIndex;
        boolean complete,dropped;
        View(JsonObject data){
            var player=Objects.requireNonNull(Minecraft.getInstance().player);
            id=data.get("id").getAsString();name=data.get("name").getAsString();dimension=data.get("dimension").getAsString();session=data.get("session").getAsString();startedAt=data.get("startedAt").getAsLong();duration=data.get("duration").getAsLong();
            winner=ItemStack.OPTIONAL_CODEC.parse(player.registryAccess().createSerializationContext(JsonOps.INSTANCE),data.get("reward")).getOrThrow();
            var random=new Random(data.get("seed").getAsLong());var pool=new ArrayList<ItemStack>();
            if(data.has("entries"))for(var value:data.getAsJsonArray("entries"))pool.add(JarDefinitions.create(new Gson().fromJson(value,JarDefinitions.Entry.class),random,"jar/preview"));
            if(pool.isEmpty())pool.add(winner);
            winnerIndex=88+Math.clamp(data.get("offset").getAsInt(),-3,3);
            var cards=new ArrayList<ItemStack>();for(int i=0;i<winnerIndex+9;i++)cards.add(i==winnerIndex?winner:pool.get(random.nextInt(pool.size())));
            strip=List.copyOf(cards);dropped=data.get("dropped").getAsBoolean();
        }
        double progress(){return complete?1:Math.clamp((ClientUi.current().serverNow()-startedAt)/(double)duration,0,1);}
    }
    private static View view;
    private static JsonObject parts,deferred;
    private static int nextPart;
    private static String dismissed="";
    private static long nextStatus;
    private JarClient(){}
    static View view(){return view;}
    public static boolean blocking(){return view!=null;}
    public static void accept(JsonObject message){
        if(message.get("status").getAsString().equals("rolling")){
            int part=message.get("part").getAsInt();
            if(part==0){parts=message.deepCopy();parts.add("entries",new JsonArray());nextPart=0;}
            if(parts==null||part!=nextPart||!parts.get("id").equals(message.get("id"))||!parts.get("session").equals(message.get("session")))return;
            nextPart++;message.getAsJsonArray("entries").forEach(row->parts.getAsJsonArray("entries").add(row.deepCopy()));
            if(!message.get("done").getAsBoolean())return;
            deferred=parts;parts=null;
        }else{deferred=message.deepCopy();parts=null;}
        applyDeferred();
    }
    private static void applyDeferred(){
        var mc=Minecraft.getInstance();if(deferred==null||mc.player==null||mc.level==null||!ClientUi.current().ready())return;
        var message=deferred;deferred=null;
        if(!ClientUi.current().session().equals(message.get("session").getAsString()))return;
        String status=message.get("status").getAsString();
        if(status.equals("idle")||status.equals("interrupted")){clear();return;}
        String id=message.get("id").getAsString();if(id.equals(dismissed))return;
        if(view==null||!view.id.equals(id))view=new View(message);
        if(status.equals("complete")){view.complete=true;view.dropped=message.get("dropped").getAsBoolean();}
        if(mc.player.isDeadOrDying()||!view.dimension.equals(mc.level.dimension().identifier().toString())){clear();return;}
        if(!(mc.gui.screen() instanceof JarScreen))mc.gui.setScreen(new JarScreen());
    }
    public static void tick(){
        applyDeferred();if(view==null)return;var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.player.isDeadOrDying()||!ClientUi.current().session().equals(view.session)||!view.dimension.equals(mc.level.dimension().identifier().toString())){clear();return;}
        if(!view.complete&&view.progress()>=1&&ClientUi.now()>=nextStatus){nextStatus=ClientUi.now()+1000;var request=ClientUi.current().operation("jarFinish");request.addProperty("session",view.session);request.addProperty("id",view.id);ClientUi.current().send("jarFinish",request);}
    }
    public static void opening(ScreenEvent.Opening event){
        if(!blocking()||event.getNewScreen() instanceof JarScreen)return;
        // Death, disconnect and dimension-loading screens remain free to replace this screen.
        if(event.getNewScreen()==null||event.getNewScreen() instanceof ThemedScreen||event.getNewScreen() instanceof AbstractContainerScreen<?>)event.setCanceled(true);
        else clear();
    }
    static void dismiss(){
        if(view==null||!view.complete)return;
        var request=ClientUi.current().operation("jarDismiss");request.addProperty("session",view.session);request.addProperty("id",view.id);ClientUi.current().send("jarDismiss",request);dismissed=view.id;clear();
    }
    public static void clear(){
        view=null;parts=null;deferred=null;nextPart=0;nextStatus=0;
        var mc=Minecraft.getInstance();if(mc.gui.screen() instanceof JarScreen)mc.gui.setScreen(null);
    }
}
