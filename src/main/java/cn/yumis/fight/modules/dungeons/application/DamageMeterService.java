package cn.yumis.fight.modules.dungeons.application;
import cn.yumis.fight.modules.dungeons.api.DamageMeter;
import cn.yumis.fight.foundation.Journal;
import java.util.function.*;
import net.minecraft.server.MinecraftServer;

import cn.yumis.fight.network.Network;

import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.dungeons.api.Dungeon;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

public class DamageMeterService {
    private final Map<UUID,JsonObject> sent=new HashMap<>();
    public void forget(ServerPlayer player){sent.remove(player.getUUID());}
    private void emit(ServerPlayer player,JsonObject value){if(!value.equals(sent.get(player.getUUID()))){sent.put(player.getUUID(),value.deepCopy());Network.send(player,"damageMeter",value);}}
    private final Journal journal;
    private final MinecraftServer server;
    private final Function<UUID,Dungeon> current;
    private final BiPredicate<ServerPlayer,String> participant;
    private final ToIntFunction<ServerPlayer> level;
    private final Supplier<cn.yumis.fight.modules.combat.api.CombatSettings> timing;
    public DamageMeterService(Journal journal,MinecraftServer server,Function<UUID,Dungeon> current,BiPredicate<ServerPlayer,String> participant,ToIntFunction<ServerPlayer> level,Supplier<cn.yumis.fight.modules.combat.api.CombatSettings> timing){this.journal=journal;this.server=server;this.current=current;this.participant=participant;this.level=level;this.timing=timing;}
    private DamageMeter load(Dungeon dungeon){return journal.get("damageMeter/"+dungeon.id,DamageMeter.class,new DamageMeter());}
    public void join(Dungeon dungeon,ServerPlayer player)throws Exception{
        DamageMeter meter=load(dungeon);var entry=new DamageMeter.Entry();entry.name=player.getScoreboardName();meter.players.put(player.getStringUUID(),entry);journal.put("damageMeter/"+dungeon.id,meter);
    }
    public void leave(Dungeon dungeon,ServerPlayer player)throws Exception{
        DamageMeter meter=load(dungeon);meter.players.remove(player.getStringUUID());journal.put("damageMeter/"+dungeon.id,meter);Network.send(player,"damageMeter",new JsonObject());broadcast(dungeon);
    }
    /** Called inside the same WAL batch as the receipt, never from a visual or client total. */
    public void record(ServerPlayer player,Target target,double fraction,long time)throws Exception{
        Dungeon dungeon=current.apply(player.getUUID());if(dungeon==null||!dungeon.status.equals("RUNNING"))return;
        if(target!=null&&!target.instance.equals(dungeon.id.toString()))return;
        DamageMeter meter=load(dungeon);if(meter.frozenAt>0)return;
        var entry=meter.players.computeIfAbsent(player.getStringUUID(),id->new DamageMeter.Entry());entry.name=player.getScoreboardName();entry.event(time,timing.get().delayMillis());
        if(target!=null&&fraction>0)entry.damage.merge(target.originalLevel,fraction*target.health(target.originalLevel),Double::sum);
        journal.put("damageMeter/"+dungeon.id,meter);
    }
    public void freeze(Dungeon dungeon)throws Exception{DamageMeter meter=load(dungeon);if(meter.frozenAt==0){meter.frozenAt=System.currentTimeMillis();journal.put("damageMeter/"+dungeon.id,meter);}broadcast(dungeon);}
    public void broadcast(Dungeon dungeon){for(var player:server.getPlayerList().getPlayers())if(participant.test(player,dungeon.id.toString()))send(player,dungeon);}
    public void send(ServerPlayer viewer,Dungeon dungeon){
        JsonObject result=new JsonObject();if(dungeon==null){emit(viewer,result);return;}
        DamageMeter meter=load(dungeon);long now=meter.frozenAt>0?meter.frozenAt:System.currentTimeMillis();
        int viewerLevel=level.applyAsInt(viewer);boolean sync=dungeon.syncFor(viewer.getUUID());
        var entries=new ArrayList<>(meter.players.entrySet());entries.removeIf(e->dungeon.withdrawn.contains(UUID.fromString(e.getKey()))||server.getPlayerList().getPlayer(UUID.fromString(e.getKey()))==null);entries.sort(Comparator.<Map.Entry<String,DamageMeter.Entry>>comparingDouble(e->e.getValue().displayed(viewerLevel,sync)).reversed().thenComparing(Map.Entry::getKey));
        double total=entries.stream().mapToDouble(e->e.getValue().displayed(viewerLevel,sync)).sum(),max=entries.isEmpty()?0:entries.getFirst().getValue().displayed(viewerLevel,sync);
        JsonArray rows=new JsonArray();for(var pair:entries){var entry=pair.getValue();JsonObject row=new JsonObject();double damage=entry.displayed(viewerLevel,sync);long elapsed=entry.elapsed(now);row.addProperty("name",entry.name);row.addProperty("damage",damage);row.addProperty("dps",elapsed>0?damage/(elapsed/1000d):0);row.addProperty("share",total>0?damage/total:0);row.addProperty("bar",max>0?damage/max:0);rows.add(row);}
        result.addProperty("instance",dungeon.id.toString());result.addProperty("completed",meter.frozenAt>0);result.add("rows",rows);emit(viewer,result);
    }
}
