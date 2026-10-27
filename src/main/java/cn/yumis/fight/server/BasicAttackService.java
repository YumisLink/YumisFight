package cn.yumis.fight.server;

import cn.yumis.fight.rules.*;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** An attack cycle survives weapon switching. Each slash can damage a target only once. */
public final class BasicAttackService {
    private record Cycle(UUID player,String session,long generation,long start,double interval,int slashes,Set<String> hits){}
    private final Map<UUID,Double> next=new HashMap<>();
    private final Map<String,Cycle> cycles=new HashMap<>();
    private final ServerRuntime runtime;
    public BasicAttackService(ServerRuntime runtime){this.runtime=runtime;}
    public void start(ServerPlayer player,JsonObject j){
        var session=runtime.session(player);long now=System.currentTimeMillis();
        String id=j.get("id").getAsString();
        if(cycles.containsKey(id))throw new IllegalArgumentException("普攻周期已使用");
        var gear=cn.yumis.fight.game.GameItems.gear(player.getMainHandItem());
        if(gear==null||gear.slot!=Rules.Slot.WEAPON||!gear.valid(session.progress)||session.life.dead||player.hasEffect(cn.yumis.fight.FightMod.STUN))throw new IllegalArgumentException("当前不能普通攻击");
        double ready=next.getOrDefault(player.getUUID(),0d);
        if(now+50<ready)throw new IllegalArgumentException("普通攻击冷却中");
        var stats=runtime.stats(player);double interval=WeaponRules.interval(session.progress,stats);
        next.put(player.getUUID(),Math.max(now,ready)+interval);
        int slashes=WeaponRules.mastery(session.progress,stats)&&stats.weaponType.equals("LIGHTSABER")?2:1;
        cycles.put(id,new Cycle(player.getUUID(),session.id.toString(),session.life.generation,now,interval,slashes,new HashSet<>()));
        cycles.entrySet().removeIf(e->now-e.getValue().start>30000);
    }
    public void validate(ServerPlayer player,JsonObject j){
        if(!j.has("basicCycle"))return;
        Cycle c=cycles.get(j.get("basicCycle").getAsString());var s=runtime.session(player);
        int slash=j.get("basicSlash").getAsInt();long now=System.currentTimeMillis();
        if(c==null||!c.player.equals(player.getUUID())||!c.session.equals(s.id.toString())||c.generation!=s.life.generation||slash<0||slash>=c.slashes||now+50<c.start+slash*c.interval/2||now>c.start+c.interval+2000||c.hits.contains(slash+"/"+j.get("target").getAsString()))throw new IllegalArgumentException("普通攻击周期无效");
    }
    public void committed(JsonObject j){if(j.has("basicCycle")){Cycle c=cycles.get(j.get("basicCycle").getAsString());if(c!=null)c.hits.add(j.get("basicSlash").getAsInt()+"/"+j.get("target").getAsString());}}
}
