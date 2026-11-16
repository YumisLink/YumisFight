package cn.yumis.fight.modules.arkguard.application;

import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.arkguard.api.ArkGuardRing;
import cn.yumis.fight.modules.dungeons.api.ReturnPoint;
import cn.yumis.fight.modules.mapgeneration.api.MapGeneration;
import cn.yumis.fight.modules.npc.api.NpcInteractionComponent;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import java.util.function.*;

/** Receives personal attendance choices; the shared scene, presence and display are world facts. */
public final class ArkGuardService {
    private static final String RETURNS="arkGuard/return/";
    private final MinecraftServer server;
    private final Journal journal;
    private final Function<ServerPlayer,String> session;
    private final BiConsumer<ServerPlayer,JsonObject> send;
    private final BiConsumer<ServerPlayer,String> message;
    private final Set<UUID> waiting=new LinkedHashSet<>();
    private final Map<UUID,Boolean> attendance=new LinkedHashMap<>();
    private final Map<UUID,JsonObject> sent=new HashMap<>();
    private final ParticipationPillar pillar=new ParticipationPillar();
    private boolean prepared;
    private long ticks;

    public ArkGuardService(MinecraftServer server,Journal journal,Function<ServerPlayer,String> session,
                           BiConsumer<ServerPlayer,JsonObject> send,BiConsumer<ServerPlayer,String> message){
        this.server=server;this.journal=journal;this.session=session;this.send=send;this.message=message;
        NpcInteractionComponent.unregister("ark_guard");
        NpcInteractionComponent.register("ark_guard",(player,host,action)->{
            if(player.hasDisconnected()||server.getPlayerList().getPlayer(player.getUUID())!=player
                ||!ArkGuardRing.contains(player.level(),player.blockPosition())||!ArkGuardRing.contains(host.level(),host.blockPosition())
                ||!NpcInteractionComponent.id(host).equals("ark_guard_guide"))return;
            try{
                switch(action.value()){
                    case "join"->choose(player,true);
                    case "withdraw"->choose(player,false);
                    case "leave"->leave(player);
                    default->throw new IllegalArgumentException("未知的方舟选项");
                }
            }catch(Exception failure){message.accept(player,failure.getMessage());}
        });
    }
    public void request(ServerPlayer player,JsonObject request)throws Exception{
        if(!request.has("session")||!session.apply(player).equals(request.get("session").getAsString()))return;
        switch(request.get("kind").getAsString()){
            case "status"->{sent.remove(player.getUUID());publish(player);}
            case "enter"->{
                if(inside(player)){publish(player);return;}
                waiting.add(player.getUUID());publish(player);
            }
            case "leave"->leave(player);
            default->throw new IllegalArgumentException("未知的方舟请求");
        }
    }
    private static boolean inside(ServerPlayer player){return ArkGuardRing.contains(player.level(),player.blockPosition());}
    private ServerLevel level(){
        var level=server.getLevel(ArkGuardRing.DIMENSION);
        if(level==null)throw new IllegalStateException("方舟场景维度尚未加载");
        return level;
    }
    public void tick(){
        if(++ticks%10!=0)return;
        waiting.removeIf(id->server.getPlayerList().getPlayer(id)==null);
        boolean occupied=server.getPlayerList().getPlayers().stream().anyMatch(ArkGuardService::inside);
        if(!prepared&&(!waiting.isEmpty()||occupied)){
            try{prepared=MapGeneration.prepare(level(),ArkGuardRing.BLUEPRINT,ArkGuardRing.ORIGIN);}
            catch(Exception failure){
                var failed=List.copyOf(waiting);waiting.clear();
                for(UUID id:failed){var player=server.getPlayerList().getPlayer(id);if(player!=null){message.accept(player,"方舟地图未完成："+failure.getMessage());publish(player);}}
                return;
            }
        }
        if(prepared){
            for(UUID id:List.copyOf(waiting)){
                waiting.remove(id);var player=server.getPlayerList().getPlayer(id);
                if(player==null)continue;
                try{enter(player);}catch(Exception failure){message.accept(player,failure.getMessage());publish(player);}
            }
        }
        Set<UUID> present=new HashSet<>();
        for(var player:server.getPlayerList().getPlayers())if(inside(player)){present.add(player.getUUID());attendance.putIfAbsent(player.getUUID(),false);}
        attendance.keySet().removeIf(id->!present.contains(id));
        for(var player:server.getPlayerList().getPlayers())if(present.contains(player.getUUID())||sent.containsKey(player.getUUID())||waiting.contains(player.getUUID()))publish(player);
        if(prepared&&!attendance.isEmpty())refreshPillar();else pillar.clear();
    }
    private void enter(ServerPlayer player)throws Exception{
        if(!inside(player)){
            var point=new ReturnPoint();point.dimension=player.level().dimension().identifier().toString();
            point.x=player.getX();point.y=player.getY();point.z=player.getZ();point.yaw=player.getYRot();point.pitch=player.getXRot();
            journal.put(RETURNS+player.getUUID(),point);
            var origin=ArkGuardRing.ORIGIN;
            if(!player.teleportTo(level(),origin.getX()+.5,origin.getY(),origin.getZ()+5.5,Set.of(),180,0,true))throw new IllegalStateException("进入方舟失败");
        }
        attendance.putIfAbsent(player.getUUID(),false);publish(player);
        message.accept(player,"已进入方舟守卫圈，请与中央引导员交谈选择参加。");
    }
    private void choose(ServerPlayer player,boolean participate){
        attendance.put(player.getUUID(),participate);publish(player);refreshPillar();
        message.accept(player,participate?"已参加方舟守卫圈。":"已取消参加方舟守卫圈。");
    }
    private void leave(ServerPlayer player)throws Exception{
        waiting.remove(player.getUUID());
        var point=journal.get(RETURNS+player.getUUID(),ReturnPoint.class,null);
        if(inside(player)||point!=null){
            ServerLevel target=point==null?server.overworld():server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(point.dimension)));
            if(target==null)target=server.overworld();
            var spawn=target.getRespawnData().pos();
            double x=point==null?spawn.getX()+.5:point.x,y=point==null?spawn.getY():point.y,z=point==null?spawn.getZ()+.5:point.z;
            if(!player.teleportTo(target,x,Math.max(target.getMinY()+1,y),z,Set.of(),point==null?0:point.yaw,point==null?0:point.pitch,true))throw new IllegalStateException("离开方舟失败");
            journal.put(RETURNS+player.getUUID(),JsonNull.INSTANCE);
        }
        attendance.remove(player.getUUID());publish(player);
        if(prepared)refreshPillar();
    }
    private void publish(ServerPlayer player){
        var state=new JsonObject();state.addProperty("session",session.apply(player));state.addProperty("inside",inside(player));
        state.addProperty("returnAvailable",!journal.get(RETURNS+player.getUUID()).isJsonNull());
        state.addProperty("waiting",waiting.contains(player.getUUID()));state.addProperty("participating",attendance.getOrDefault(player.getUUID(),false));
        if(!state.equals(sent.get(player.getUUID()))){send.accept(player,state);sent.put(player.getUUID(),state.deepCopy());}
    }
    private void refreshPillar(){
        List<Component> rows=new ArrayList<>();int participating=0;
        for(var entry:attendance.entrySet()){
            var player=server.getPlayerList().getPlayer(entry.getKey());if(player==null||!inside(player))continue;
            if(entry.getValue())participating++;
            rows.add(Component.literal(player.getScoreboardName()+"  "+(entry.getValue()?"已参加":"未参加")).withColor(entry.getValue()?0x80E6A6:0xC4C9CC));
        }
        pillar.update(level(),rows,participating,ticks);
    }
    public void logout(ServerPlayer player){waiting.remove(player.getUUID());attendance.remove(player.getUUID());sent.remove(player.getUUID());}
    public void close(){pillar.clear();waiting.clear();attendance.clear();sent.clear();NpcInteractionComponent.unregister("ark_guard");}
}
