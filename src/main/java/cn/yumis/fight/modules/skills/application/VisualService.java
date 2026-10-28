package cn.yumis.fight.modules.skills.application;

import cn.yumis.fight.network.Network;
import cn.yumis.fight.modules.skills.api.SkillVisualRules;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
/** Visual sessions are ephemeral; terminal tombstones outlive every supported effect. */
public class VisualService {
    private static final com.google.gson.Gson JSON=new com.google.gson.GsonBuilder().disableHtmlEscaping().create();

    private record Active(UUID owner,String session,long generation,JsonObject data,long expires){
    }
    private final Map<String,Active> active=new HashMap<>();
    private final Map<String,Long> ended=new HashMap<>();
    private final net.minecraft.server.MinecraftServer server;
    private final java.util.function.Function<ServerPlayer,cn.yumis.fight.modules.combat.api.CombatSession> sessions;
    public static void resetGravity(ServerPlayer player){SkillVisualRules.restore(player);}
    public VisualService(net.minecraft.server.MinecraftServer server,java.util.function.Function<ServerPlayer,cn.yumis.fight.modules.combat.api.CombatSession> sessions){this.server=server;this.sessions=sessions;}
    public void receive(ServerPlayer player,JsonObject input){
        var session=sessions.apply(player);
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
            if(phase.equals("END")&&cn.yumis.fight.modules.skills.api.CareerSkills.persistent(input.get("kind").getAsString())&&!(input.has("terminal")&&input.get("terminal").getAsBoolean())){
                PacketDistributor.sendToPlayersTrackingEntity(player,new Network.Snapshot("visual",JSON.toJson(j)));return;
            }
            Active old=active.remove(key);
            if(old!=null)SkillVisualRules.end(player,old.data.get("kind").getAsString());
            ended.put(key,now+15000);
        }
        else if(phase.equals("START")){
            if(active.containsKey(key))return;
            long started=input.get("startedAt").getAsLong();
            long duration=input.has("duration")?Math.clamp(input.get("duration").getAsLong(),0,40000):2000;
            SkillVisualRules.start(player,input.get("kind").getAsString());
            active.put(key,new Active(player.getUUID(),session.id.toString(),session.life.generation,j,Math.min(now+duration,started+duration)));
        }
        else if(!active.containsKey(key))return;
        PacketDistributor.sendToPlayersTrackingEntity(player,new Network.Snapshot("visual",JSON.toJson(j)));
    }
    public void track(ServerPlayer observer,ServerPlayer caster){
        for(Active cast:active.values())if(cast.owner.equals(caster.getUUID())&&cast.expires>System.currentTimeMillis())Network.send(observer,"visual",cast.data);
    }
    public boolean movementLocked(ServerPlayer player){
        var session=sessions.apply(player);long now=System.currentTimeMillis();
        return active.values().stream().anyMatch(a->a.owner.equals(player.getUUID())&&a.session.equals(session.id.toString())&&a.generation==session.life.generation&&a.expires>now&&!cn.yumis.fight.modules.skills.api.CareerSkills.persistent(a.data.get("kind").getAsString()));
    }
    public boolean invulnerable(ServerPlayer player){
        var session=sessions.apply(player);long now=System.currentTimeMillis();
        return active.values().stream().anyMatch(a->a.owner.equals(player.getUUID())&&a.session.equals(session.id.toString())&&a.generation==session.life.generation&&a.expires>now&&SkillVisualRules.policy(a.data.get("kind").getAsString()).invulnerable());
    }
    public void tick(){
        long now=System.currentTimeMillis();
        ended.entrySet().removeIf(e->e.getValue()<now);
        for(var entry:new ArrayList<>(active.entrySet())){
            Active a=entry.getValue();
            ServerPlayer player=server.getPlayerList().getPlayer(a.owner);
            boolean valid=player!=null&&a.expires>now;
            if(valid){
                var session=sessions.apply(player);
                valid=session.id.toString().equals(a.session)&&session.life.generation==a.generation&&!session.life.dead&&player.level().dimension().identifier().toString().equals(a.data.get("dimension").getAsString());
            }
            if(!valid){
                JsonObject cancel=a.data.deepCopy();
                cancel.addProperty("phase","CANCEL");
                for(ServerPlayer observer:server.getPlayerList().getPlayers())Network.send(observer,"visual",cancel);
                if(player!=null)SkillVisualRules.end(player,a.data.get("kind").getAsString());
                active.remove(entry.getKey());
                ended.put(entry.getKey(),now+15000);
            }
        }
    }
}
