package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.dungeons.content.GoblinCamp;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** All forest enemies are placed once on entry; restart recovery closes that instance. */
final class GoblinCampEncounterService {
    private static final List<String> CAMP_TYPES=List.of("goblin","goblin","goblin_thrower","goblin_captain");
    /** Keno phase thresholds and fury rules, approved 2026-11-22: P1 probes, P2 storms plus summons, P3 relentless. */
    private static final double PHASE_2=.67,PHASE_3=.33,RAGE_MAXIMUM=10;
    private static final com.google.gson.Gson JSON=new com.google.gson.GsonBuilder().disableHtmlEscaping().create();
    private static final java.util.Random RANDOM=new java.util.Random();
    private static final class Run {
        final UUID boss;
        long nextAmbience;
        Run(UUID boss){this.boss=boss;}
    }
    private static final class Keno {
        int stage;
        long busyUntil,transitionUntil,meleeSince,lastCombat,followupAt;
        boolean followup,claimedImmune;
        double lastRevision=-1;
        final Map<String,Long> cooldowns=new HashMap<>();
        final List<Probe> probes=new ArrayList<>();
        final List<Object[]> schedule=new ArrayList<>();
    }
    /** A cast whose landed hit feeds the boss's own fury; the boss is server-owned, so geometry is checked server-side. */
    private record Probe(long at,double x,double y,double z,double radius,double dx,double dz,double halfAngle,boolean fan){}
    private final DungeonService owner;
    private final Journal journal;
    private final Map<UUID,Run> runs=new HashMap<>();
    private final Map<UUID,Keno> keno=new HashMap<>();
    GoblinCampEncounterService(DungeonService owner,Journal journal){this.owner=owner;this.journal=journal;}
    static boolean handles(Dungeon dungeon){return dungeon.treasure==null&&dungeon.dungeon.equals("goblin_camp");}
    void initialize(Dungeon dungeon){
        if(runs.containsKey(dungeon.id))return;
        try{
            var origin=new BlockPos(dungeon.cell*512,80,0);
            int patrolCount=GoblinCamp.CAMPS.size()*CAMP_TYPES.size()*2;
            var patrols=GoblinCamp.patrolSpawns(owner.level(),origin,patrolCount,dungeon.id.getMostSignificantBits()^dungeon.id.getLeastSignificantBits());
            GoblinCampScenery.place(owner.level(),dungeon);
            var abyssSpawns=new ArrayList<Vec3>();
            for(var area:GoblinCamp.CAMPS){
                var positions=area.spawns(origin);
                for(int i=0;i<CAMP_TYPES.size()-1;i++)abyssSpawns.add(Vec3.atBottomCenterOf(positions.get(i)));
            }
            for(int i=0;i<patrols.size();i++)if(i%CAMP_TYPES.size()!=3)abyssSpawns.add(Vec3.atBottomCenterOf(patrols.get(i)));
            owner.abyss.plan(dungeon,abyssSpawns);
            for(var area:GoblinCamp.CAMPS){
                var positions=area.spawns(origin);
                for(int i=0;i<CAMP_TYPES.size();i++)spawn(dungeon,i,positions.get(i),area.id());
            }
            for(int i=0;i<patrols.size();i++)spawn(dungeon,i,patrols.get(i),"forest");
            var at=GoblinCamp.BOSS.at(origin,0,0,0);
            var boss=owner.spawn(dungeon,"goblin_shaman",false,true,at.getX()+.5,at.getY(),at.getZ()+.5);
            dungeon.wave=6;
            runs.put(dungeon.id,new Run(boss.getUUID()));
        }catch(RuntimeException failure){throw abandon(dungeon,failure);}
    }
    void tick(Dungeon dungeon,List<ServerPlayer> players,long now)throws Exception{
        initialize(dungeon);
        Run run=runs.get(dungeon.id);
        boolean changed=dungeon.enemies.removeIf(this::dead);
        if(changed){journal.put("instance/"+dungeon.id,dungeon);owner.refreshCamp();}
        if(dead(run.boss)&&dungeon.status.equals("RUNNING"))owner.completeCamp(dungeon,players);
        if(now>=run.nextAmbience){run.nextAmbience=now+2500;GoblinCampScenery.ambience(owner.level(),dungeon,players);}
        keno.keySet().removeIf(id->owner.level().getEntity(id)==null);
    }
    private void spawn(Dungeon dungeon,int index,BlockPos at,String region){
        int formation=index%CAMP_TYPES.size();
        if(formation!=3&&owner.abyss.replaces(dungeon,Vec3.atBottomCenterOf(at)))return;
        var enemy=owner.spawn(dungeon,CAMP_TYPES.get(formation),formation==3,false,at.getX()+.5,at.getY(),at.getZ()+.5);
        enemy.getPersistentData().putString("ydnf_camp_region",region);
    }
    private RuntimeException abandon(Dungeon dungeon,RuntimeException failure){
        try{owner.close(dungeon);}catch(Exception cleanup){failure.addSuppressed(cleanup);}
        return failure;
    }
    private boolean dead(UUID id){Target target=journal.get("target/"+id,Target.class,null);return target!=null&&target.life.dead;}
    void died(Dungeon dungeon,UUID id,List<ServerPlayer> players)throws Exception{
        Run run=runs.get(dungeon.id);
        if(run!=null&&id.equals(run.boss)&&dead(id)&&dungeon.status.equals("RUNNING"))owner.completeCamp(dungeon,players);
    }
    void close(Dungeon dungeon){runs.remove(dungeon.id);GoblinCampScenery.clear(owner.level(),dungeon.cell);}

    // ---- 落雷凯诺：距离带 × 独立冷却 × 概率决策、三阶段演出与怒气 ----
    /** Serves the camp boss, the treasure guardians, and any future goblin_shaman through the shared enemy brain registry. */
    void kenoBrain(Dungeon dungeon,DungeonEnemy e,List<ServerPlayer> players,long now)throws Exception{
        if(cn.yumis.fight.modules.combat.api.CombatContentTypes.controlled(e))return;
        Target t=owner.targets().target(e);
        if(t==null||t.life.dead)return;
        Keno k=keno.computeIfAbsent(e.getUUID(),id->new Keno());
        for(Object[] action:new ArrayList<>(k.schedule))if(now>=(long)action[0]){k.schedule.remove(action);((Runnable)action[1]).run();}
        if(t.life.revision!=k.lastRevision){
            if(k.lastRevision>=0&&t.life.revision>k.lastRevision)
                addRage(e,t,players,(int)Math.min(RAGE_MAXIMUM-t.rage,t.life.revision-k.lastRevision));
            k.lastRevision=t.life.revision;k.lastCombat=now;
        }
        ServerPlayer p=nearest(e,players);
        if(p!=null&&e.distanceTo(p)<24)k.lastCombat=now;
        if(now-k.lastCombat>=2000&&t.rage>0){t.rage--;k.lastCombat=now;sync(e,t,players);}
        for(Probe probe:new ArrayList<>(k.probes)){
            if(now<probe.at())continue;
            k.probes.remove(probe);
            if(players.stream().filter(Entity::isAlive).anyMatch(player->connected(probe,player)))addRage(e,t,players,1);
        }
        int stage=t.life.q<=PHASE_3?3:t.life.q<=PHASE_2?2:1;
        if(k.stage==0)k.stage=stage;
        else if(stage>k.stage){transition(dungeon,e,t,k,stage,players,now);return;}
        if(now<k.transitionUntil){e.getNavigation().stop();return;}
        if(k.claimedImmune){k.claimedImmune=false;t.damageImmune=false;sync(e,t,players);}
        if(k.followup&&now>=k.followupAt){k.followup=false;lightning(e,nearest(e,players),k,now,400);return;}
        if(p!=null)e.getLookControl().setLookAt(p);
        if(k.busyUntil>now||p==null)return;
        double distance=e.distanceTo(p);
        if(distance>8)e.getNavigation().moveTo(p,1);
        else e.getNavigation().stop();
        if(distance<3.5){if(k.meleeSince==0)k.meleeSince=now;}
        else k.meleeSince=0;
        if(k.stage>=2&&k.meleeSince>0&&now-k.meleeSince>=3000&&ready(k,"BLINK",now)){blink(e,p,k,now);return;}
        if(k.stage>=3&&t.rage>=RAGE_MAXIMUM&&ready(k,"ULTIMATE",now)){ultimate(e,p,t,k,players,now);return;}
        String kind=distance<3.5?weighted(k,now,"BASIC","45","FAN","30","CLAW","25")
            :distance<=8?weighted(k,now,"FAN","40","RING","25","LIGHTNING","20")
            :distance<12?"LIGHTNING":null;
        if(kind!=null)cast(e,p,k,now,kind);
    }
    private ServerPlayer nearest(DungeonEnemy e,List<ServerPlayer> players){
        return players.stream().filter(Entity::isAlive).min(Comparator.comparingDouble(e::distanceToSqr)).orElse(null);
    }
    private boolean ready(Keno k,String kind,long now){return k.cooldowns.getOrDefault(kind,0L)<=now;}
    private String weighted(Keno k,long now,String... table){
        int total=0;
        List<String> kinds=new ArrayList<>();
        List<Integer> weights=new ArrayList<>();
        for(int i=0;i+1<table.length;i+=2)if(ready(k,table[i],now)){int weight=Integer.parseInt(table[i+1]);total+=weight;kinds.add(table[i]);weights.add(weight);}
        if(total==0)return null;
        int roll=RANDOM.nextInt(total);
        for(int i=0;i<kinds.size();i++){roll-=weights.get(i);if(roll<0)return kinds.get(i);}
        return kinds.getLast();
    }
    private long cooldown(Keno k,String kind,long base,long now){
        long scaled=k.stage>=3?Math.round(base*.8):base;
        k.cooldowns.put(kind,now+scaled*1000);
        return scaled*1000;
    }
    private void cast(DungeonEnemy e,ServerPlayer p,Keno k,long now,String kind){
        switch(kind){
            case "LIGHTNING"->{cooldown(k,"LIGHTNING",k.stage>=2?8:10,now);strike(e,p,k,now,"LIGHTNING","lightning_cast","CIRCLE",true,2,Math.PI,1200,200,1000,1.4,true);}
            case "FAN"->{cooldown(k,"FAN",6,now);strike(e,p,k,now,"FAN","fan_swipe","FAN",false,6,Math.PI/4,800,200,800,1.2,true);}
            case "BASIC"->{cooldown(k,"BASIC",2,now);strike(e,p,k,now,"BASIC","basic_strike","FAN",false,3,Math.PI/3,300,200,400,1,false);}
            case "RING"->{cooldown(k,"RING",8,now);strike(e,p,k,now,"RING","ring_burst","CIRCLE",false,5.5,Math.PI,900,200,700,1.3,true);}
            case "CLAW"->{cooldown(k,"CLAW",6,now);claw(e,p,k,now,k.stage>=3?.9:.7);}
            default->{return;}
        }
    }
    /** Standard one-shot action; keeps the bossAction contract the client already judges locally. */
    private void strike(DungeonEnemy e,ServerPlayer p,Keno k,long now,String kind,String move,String shape,boolean atPlayer,double radius,double halfAngle,long windup,long active,long recovery,double coefficient,boolean magic){
        JsonObject a=action(e,p,UUID.randomUUID(),kind,shape,atPlayer,radius,halfAngle,windup,active,recovery,coefficient,magic,now);
        long total=windup+active+recovery;
        owner.broadcast(e.instance(),"bossAction",a);
        e.play(move,total);
        k.busyUntil=now+total;
        aggressive(e,k,now,total);
        k.probes.add(new Probe(now+windup+active/2,a.get("x").getAsDouble(),a.get("y").getAsDouble(),a.get("z").getAsDouble(),radius,a.get("dx").getAsDouble(),a.get("dz").getAsDouble(),halfAngle,shape.equals("FAN")));
    }
    private void claw(DungeonEnemy e,ServerPlayer p,Keno k,long now,double coefficient){
        Vec3 dir=aim(e,p);
        for(int i=0;i<3;i++)
            owner.broadcast(e.instance(),"bossAction",action(e,p,UUID.randomUUID(),"CLAW","FAN",false,4.5,Math.PI/3,400+i*500,200,i==2?600:0,coefficient,false,now));
        e.play("claw_combo",2200);
        k.busyUntil=now+2200;
        aggressive(e,k,now,2200);
        k.probes.add(new Probe(now+500,e.getX(),e.getY(),e.getZ(),4.5,dir.x,dir.z,Math.PI/3,true));
    }
    /** Furious storm: circles rain on the player's live position, one per 600 ms, so staying still is punished. */
    private void ultimate(DungeonEnemy e,ServerPlayer p,Target t,Keno k,List<ServerPlayer> players,long now)throws Exception{
        boolean stage3=k.stage>=3;
        int circles=stage3?4:3;
        double radius=stage3?5:4,coefficient=stage3?2.6:1.8;
        cooldown(k,"ULTIMATE",25,now);
        t.rage=0;sync(e,t,players);
        long duration=800+(circles-1)*600+200+600;
        e.play("ultimate_storm",duration);
        k.busyUntil=now+duration;
        aggressive(e,k,now,duration);
        for(int i=0;i<circles;i++){
            long at=now+800+i*600;
            k.schedule.add(new Object[]{at,(Runnable)()->{
                ServerPlayer target=nearest(e,players);
                if(target==null)return;
                long fire=System.currentTimeMillis();
                owner.broadcast(e.instance(),"bossAction",action(e,target,UUID.randomUUID(),"ULTIMATE","CIRCLE",true,radius,Math.PI,300,200,0,coefficient,true,fire));
                k.probes.add(new Probe(fire+400,target.getX(),target.getY(),target.getZ(),radius,0,0,Math.PI,false));
            }});
        }
    }
    private void lightning(DungeonEnemy e,ServerPlayer p,Keno k,long now,long windup){
        if(p==null)return;
        cooldown(k,"LIGHTNING",k.stage>=2?8:10,now);
        strike(e,p,k,now,"LIGHTNING","lightning_cast","CIRCLE",true,2,Math.PI,windup,200,1000,1.4,true);
    }
    /** Reactive escape from melee pressure: reappear behind the player, then strike with a shortened lead. */
    private void blink(DungeonEnemy e,ServerPlayer p,Keno k,long now){
        cooldown(k,"BLINK",12,now);
        Vec3 flat=p.getLookAngle();flat=new Vec3(flat.x,0,flat.z);
        if(flat.lengthSqr()<.01)flat=new Vec3(0,0,1);
        Vec3 from=e.position(),destination=p.position().subtract(flat.normalize().scale(6));
        e.getNavigation().stop();
        e.setPos(destination.x,p.getY(),destination.z);
        e.play("blink_step",500);
        k.busyUntil=now+500;
        k.followup=true;k.followupAt=now+400;
        var level=owner.level();
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,from.x,from.y+1,from.z,20,.6,1,.6,.02);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,destination.x,p.getY()+1,destination.z,20,.6,1,.6,.02);
    }
    /** Phase change: an invulnerable performance, and at P2 the storm ritual absorbs the legacy mid-fight summons. */
    private void transition(Dungeon dungeon,DungeonEnemy e,Target t,Keno k,int stage,List<ServerPlayer> players,long now)throws Exception{
        k.stage=stage;
        long millis=stage==2?2200:2400;
        k.transitionUntil=now+millis;
        k.busyUntil=now+millis;
        k.meleeSince=0;
        if(!t.damageImmune){t.damageImmune=true;k.claimedImmune=true;}
        sync(e,t,players);
        e.getNavigation().stop();
        e.play(stage==2?"summon_ritual":"phase3_transition",millis);
        if(stage==2&&!e.getPersistentData().getBooleanOr("ydnf_keno_summoned",false)){
            e.getPersistentData().putBoolean("ydnf_keno_summoned",true);
            owner.spawn(dungeon,"goblin",false,false,e.getX()-2,e.getY(),e.getZ());
            owner.spawn(dungeon,"goblin",false,false,e.getX()+2,e.getY(),e.getZ());
            owner.spawn(dungeon,"goblin_captain",true,false,e.getX(),e.getY(),e.getZ()+2.5);
            owner.refreshCamp();
        }
    }
    private void aggressive(DungeonEnemy e,Keno k,long now,long duration){
        e.setAggressive(true);
        k.schedule.add(new Object[]{now+duration,(Runnable)()->e.setAggressive(false)});
    }
    private JsonObject action(DungeonEnemy e,ServerPlayer p,UUID id,String kind,String shape,boolean atPlayer,double radius,double halfAngle,long windup,long active,long recovery,double coefficient,boolean magic,long startedAt){
        JsonObject a=new JsonObject();
        Vec3 dir=aim(e,p);
        a.addProperty("id",id.toString());
        a.add("boss",JSON.toJsonTree(owner.targets().target(e)));
        a.addProperty("kind",kind);
        a.addProperty("shape",shape);
        a.addProperty("width",2);
        a.addProperty("x",atPlayer?p.getX():e.getX());
        a.addProperty("y",atPlayer?p.getY():e.getY());
        a.addProperty("z",atPlayer?p.getZ():e.getZ());
        a.addProperty("dx",dir.x);
        a.addProperty("dz",dir.z);
        a.addProperty("radius",radius);
        a.addProperty("halfAngle",halfAngle);
        a.addProperty("windupTicks",windup/50);
        a.addProperty("activeTicks",active/50);
        a.addProperty("recoveryTicks",recovery/50);
        a.addProperty("coefficient",coefficient);
        a.addProperty("magic",magic);
        a.addProperty("element",magic?0:-1);
        a.addProperty("startedAt",startedAt);
        return a;
    }
    private Vec3 aim(DungeonEnemy e,ServerPlayer p){
        Vec3 flat=p.position().subtract(e.position());flat=new Vec3(flat.x,0,flat.z);
        return flat.lengthSqr()<.01?new Vec3(0,0,1):flat.normalize();
    }
    private void addRage(DungeonEnemy e,Target t,List<ServerPlayer> players,int amount)throws Exception{
        if(amount<=0||t.rage>=RAGE_MAXIMUM)return;
        t.rage=Math.min(RAGE_MAXIMUM,t.rage+amount);
        sync(e,t,players);
    }
    private boolean connected(Probe probe,ServerPlayer p){
        double dx=p.getX()-probe.x(),dz=p.getZ()-probe.z();
        double horizontal=Math.sqrt(dx*dx+dz*dz);
        if(horizontal>probe.radius()+.3||Math.abs(p.getY()-probe.y())>3)return false;
        if(!probe.fan()||horizontal<.01)return true;
        return (dx*probe.dx()+dz*probe.dz())/horizontal>=Math.cos(probe.halfAngle());
    }
    private void sync(DungeonEnemy e,Target t,List<ServerPlayer> players)throws Exception{
        try{
            journal.begin();
            journal.put("target/"+e.getUUID(),t);
            journal.finish();
        }catch(Exception failure){
            journal.abort();
            throw new IllegalStateException("Keno encounter state could not be saved",failure);
        }
        e.setData(cn.yumis.fight.modules.combat.api.CombatContentTypes.TARGET,JSON.toJson(t));
        for(ServerPlayer observer:players)owner.targets().context(observer,e);
    }
}
