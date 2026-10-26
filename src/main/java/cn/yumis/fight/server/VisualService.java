package cn.yumis.fight.server;
import cn.yumis.fight.network.Network;
import static cn.yumis.fight.rules.Models.JSON;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
/** Visual sessions are ephemeral; terminal tombstones outlive every supported effect. */
public final class VisualService {
    private record Active(UUID owner,String session,long generation,JsonObject data,long expires){
    }
    private final Map<String,Active> active=new HashMap<>();
    private final Map<String,Long> ended=new HashMap<>();
    private final ServerRuntime runtime;
    public static void resetGravity(ServerPlayer player){
        var data=player.getPersistentData();
        if(data.getBooleanOr("ydnf_storm_active",false)){
            player.setNoGravity(data.getBooleanOr("ydnf_storm_previous_gravity",false));
            data.remove("ydnf_storm_active");
            data.remove("ydnf_storm_previous_gravity");
        }
    }
    public VisualService(ServerRuntime runtime){
        this.runtime=runtime;
    }
    public void receive(ServerPlayer player,JsonObject input){
        var session=runtime.session(player);
        if(!input.has("session")||!session.id.toString().equals(input.get("session").getAsString())||input.get("generation").getAsLong()!=session.life.generation)return;
        String id=input.get("id").getAsString(),phase=input.get("phase").getAsString();
        String key=player.getUUID()+"/"+session.id+"/"+id;
        long now=System.currentTimeMillis();
        if(ended.containsKey(key))return;
        JsonObject j=input.deepCopy();
        j.addProperty("caster",player.getStringUUID());
        j.addProperty("dimension",player.level().dimension().identifier().toString());
        j.addProperty("instance",session.life.instance);
        if(phase.equals("END")||phase.equals("CANCEL")){
            Active old=active.remove(key);
            if(old!=null&&old.data.get("kind").getAsString().equals("STORM"))resetGravity(player);
            ended.put(key,now+15000);
        }
        else if(phase.equals("START")){
            if(active.containsKey(key))return;
            long started=input.get("startedAt").getAsLong();
            long duration=input.has("duration")?Math.clamp(input.get("duration").getAsLong(),0,40000):2000;
            if(input.get("kind").getAsString().equals("STORM")){
                var data=player.getPersistentData();
                if(!data.getBooleanOr("ydnf_storm_active",false))data.putBoolean("ydnf_storm_previous_gravity",player.isNoGravity());
                data.putBoolean("ydnf_storm_active",true);
                player.setNoGravity(true);
                player.fallDistance=0;
            }
            active.put(key,new Active(player.getUUID(),session.id.toString(),session.life.generation,j,Math.min(now+duration,started+duration)));
        }
        else if(!active.containsKey(key))return;
        PacketDistributor.sendToPlayersTrackingEntity(player,new Network.Snapshot("visual",JSON.toJson(j)));
    }
    public void track(ServerPlayer observer,ServerPlayer caster){
        for(Active cast:active.values())if(cast.owner.equals(caster.getUUID())&&cast.expires>System.currentTimeMillis())Network.send(observer,"visual",cast.data);
    }
    public void tick(){
        long now=System.currentTimeMillis();
        ended.entrySet().removeIf(e->e.getValue()<now);
        for(var entry:new ArrayList<>(active.entrySet())){
            Active a=entry.getValue();
            ServerPlayer player=runtime.server.getPlayerList().getPlayer(a.owner);
            boolean valid=player!=null&&a.expires>now;
            if(valid){
                var session=runtime.session(player);
                valid=session.id.toString().equals(a.session)&&session.life.generation==a.generation&&!session.life.dead&&player.level().dimension().identifier().toString().equals(a.data.get("dimension").getAsString());
            }
            if(!valid){
                JsonObject cancel=a.data.deepCopy();
                cancel.addProperty("phase","CANCEL");
                for(ServerPlayer observer:runtime.server.getPlayerList().getPlayers())Network.send(observer,"visual",cancel);
                if(player!=null&&a.data.get("kind").getAsString().equals("STORM"))resetGravity(player);
                active.remove(entry.getKey());
                ended.put(entry.getKey(),now+15000);
            }
        }
    }
}
