package cn.yumis.fight.server;

import cn.yumis.fight.network.Network;
import cn.yumis.fight.rules.*;
import cn.yumis.fight.rules.Models.*;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

public final class DamageMeterService {
    private final ServerRuntime runtime;
    public DamageMeterService(ServerRuntime runtime){this.runtime=runtime;}
    private DamageMeter load(Dungeon dungeon){return runtime.journal.get("damageMeter/"+dungeon.id,DamageMeter.class,new DamageMeter());}
    public void join(Dungeon dungeon,ServerPlayer player)throws Exception{
        DamageMeter meter=load(dungeon);var entry=new DamageMeter.Entry();entry.name=player.getScoreboardName();meter.players.put(player.getStringUUID(),entry);runtime.journal.put("damageMeter/"+dungeon.id,meter);
    }
    public void leave(Dungeon dungeon,ServerPlayer player)throws Exception{
        DamageMeter meter=load(dungeon);meter.players.remove(player.getStringUUID());runtime.journal.put("damageMeter/"+dungeon.id,meter);Network.send(player,"damageMeter",new JsonObject());broadcast(dungeon);
    }
    /** Called inside the same WAL batch as the receipt, never from a visual or client total. */
    public void record(ServerPlayer player,Target target,double fraction,long time)throws Exception{
        Dungeon dungeon=runtime.dungeons.current(player.getUUID());if(dungeon==null||!dungeon.status.equals("RUNNING"))return;
        if(target!=null&&!target.instance.equals(dungeon.id.toString()))return;
        DamageMeter meter=load(dungeon);if(meter.frozenAt>0)return;
        var entry=meter.players.computeIfAbsent(player.getStringUUID(),id->new DamageMeter.Entry());entry.name=player.getScoreboardName();entry.event(time);
        if(target!=null&&fraction>0)entry.damage.merge(target.originalLevel,fraction*target.health(target.originalLevel),Double::sum);
        runtime.journal.put("damageMeter/"+dungeon.id,meter);
    }
    public void freeze(Dungeon dungeon)throws Exception{DamageMeter meter=load(dungeon);if(meter.frozenAt==0){meter.frozenAt=System.currentTimeMillis();runtime.journal.put("damageMeter/"+dungeon.id,meter);}broadcast(dungeon);}
    public void broadcast(Dungeon dungeon){for(var player:runtime.server.getPlayerList().getPlayers())if(runtime.dungeons.participant(player,dungeon.id.toString()))send(player,dungeon);}
    public void send(ServerPlayer viewer,Dungeon dungeon){
        JsonObject result=new JsonObject();if(dungeon==null){Network.send(viewer,"damageMeter",result);return;}
        DamageMeter meter=load(dungeon);long now=meter.frozenAt>0?meter.frozenAt:System.currentTimeMillis();
        var entries=new ArrayList<>(meter.players.entrySet());entries.removeIf(e->dungeon.withdrawn.contains(UUID.fromString(e.getKey()))||runtime.server.getPlayerList().getPlayer(UUID.fromString(e.getKey()))==null);entries.sort(Comparator.<Map.Entry<String,DamageMeter.Entry>>comparingDouble(e->e.getValue().canonical()).reversed().thenComparing(Map.Entry::getKey));
        double total=entries.stream().mapToDouble(e->e.getValue().canonical()).sum(),max=entries.isEmpty()?0:entries.getFirst().getValue().canonical();
        JsonArray rows=new JsonArray();for(var pair:entries){var entry=pair.getValue();JsonObject row=new JsonObject();double damage=entry.displayed(runtime.session(viewer).progress.level,dungeon.sync);long elapsed=entry.elapsed(now);row.addProperty("name",entry.name);row.addProperty("damage",damage);row.addProperty("dps",elapsed>0?damage/(elapsed/1000d):0);row.addProperty("share",total>0?entry.canonical()/total:0);row.addProperty("bar",max>0?entry.canonical()/max:0);rows.add(row);}
        result.addProperty("instance",dungeon.id.toString());result.addProperty("completed",meter.frozenAt>0);result.add("rows",rows);Network.send(viewer,"damageMeter",result);
    }
}
