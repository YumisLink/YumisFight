package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.modules.combat.api.CombatContentTypes;
import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.dungeons.content.DarkCityEntrance;
import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.network.Network;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;
import static cn.yumis.fight.modules.dungeons.api.DarkCityEnemyKind.*;
import static cn.yumis.fight.modules.dungeons.api.DarkCityMoves.*;

/** Server-owned defenders and world motion. Each participant owns their contact and damage result. */
public final class DarkCityEncounterService {
    private static final Gson JSON=new Gson();
    private static final DarkCityEnemyKind ARCHER=DarkCityEnemyKind.GUARD;
    private static final DarkCityEnemyKind[][] WAVES={
        {HEADLESS_GRAVEROBBER,HEADLESS_GRAVEROBBER,HEADLESS_GRAVEROBBER,HEADLESS_GRAVEROBBER,HEADLESS_GRAVEROBBER,HEADLESS_GRAVEROBBER,HEADLESS_GRAVEROBBER,HEADLESS_SKELETON,HEADLESS_SKELETON,HEADLESS_SKELETON},
        {HEADLESS_GRAVEROBBER,HEADLESS_GRAVEROBBER,HEADLESS_GRAVEROBBER,HEADLESS_GRAVEROBBER,HEADLESS_GRAVEROBBER,HEADLESS_SKELETON,HEADLESS_SKELETON,HEADLESS_SKELETON,ARCHER,ARCHER},
        {HEADLESS_GRAVEROBBER,HEADLESS_GRAVEROBBER,HEADLESS_GRAVEROBBER,HEADLESS_GRAVEROBBER,HEADLESS_SKELETON,HEADLESS_SKELETON,ARCHER,ARCHER,ARCHER,ARCHER},
        {HEADLESS_GRAVEROBBER,HEADLESS_GRAVEROBBER,HEADLESS_GRAVEROBBER,HEADLESS_SKELETON,HEADLESS_SKELETON,ARCHER,ARCHER,ARCHER,ARCHER,TINGO},
        {HEADLESS_GRAVEROBBER,HEADLESS_GRAVEROBBER,HEADLESS_GRAVEROBBER,HEADLESS_GRAVEROBBER,HEADLESS_SKELETON,HEADLESS_SKELETON,ARCHER,ARCHER,GOLIATH,GOLIATH},
        {HEADLESS_GRAVEROBBER,HEADLESS_GRAVEROBBER,HEADLESS_SKELETON,HEADLESS_SKELETON,ARCHER,ARCHER,GOLIATH,GOLIATH,GOLIATH,HUMPHREY}
    };
    private final DungeonService owner;
    private final Journal journal;
    private final DungeonPorts.Players players;
    private final DungeonPorts.Targets targets;
    private static final class Brain {
        final Vec3 home;final int area;final double knockback;
        final EnumMap<DarkCityMoves,Long> ready=new EnumMap<>(DarkCityMoves.class);
        long busy,resistantUntil;
        Brain(DungeonEnemy e,Dungeon d){area=DarkCityEntrance.area(d.wave);home=DarkCityEntrance.inside(d.cell,area,e.position());knockback=DarkCityEnemyKind.of(e)==GOLIATH?0:e.getAttribute(Attributes.KNOCKBACK_RESISTANCE).getBaseValue();}
        boolean ready(DarkCityMoves move,long now){return now>=ready.getOrDefault(move,0L);}
    }
    private static final class Pending {
        final GblAttack attack;final String group;final int area;long previous;
        Pending(GblAttack attack,String group,int area){this.attack=attack;this.group=group;this.area=area;previous=attack.release();}
    }
    private record Guard(long from,long until){}
    private final Map<UUID,Brain> brains=new HashMap<>();
    private final Map<String,Pending> attacks=new LinkedHashMap<>();
    private final Map<UUID,Guard> guards=new HashMap<>();
    private final Map<UUID,Integer> announced=new HashMap<>();
    private final Map<UUID,String> tracked=new HashMap<>();

    DarkCityEncounterService(DungeonService owner,Journal journal,DungeonPorts.Players players,DungeonPorts.Targets targets){this.owner=owner;this.journal=journal;this.players=players;this.targets=targets;}
    void spawn(Dungeon d){
        if(d.wave==6){
            DungeonEnemy boss=owner.spawn(d,HEADLESS_KNIGHT.id,false,true,d.cell*512,80,140);
            boss.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);return;
        }
        int area=DarkCityEntrance.area(d.wave);
        for(int i=0;i<WAVES[d.wave].length;i++){
            var kind=WAVES[d.wave][i];
            owner.spawn(d,kind.id,kind==TINGO||kind==HUMPHREY,false,d.cell*512+(i%5-2)*4,80,area*40+11+i/5*7);
        }
    }
    public boolean waitsAtGate(Dungeon d,List<ServerPlayer> here){
        if(d.wave%2!=1)return false;
        int next=(d.wave+1)/2;
        if(announced.getOrDefault(d.id,0)<next){announced.put(d.id,next);for(var p:here)players.message(p,"区域清场，前往"+DarkCityEntrance.AREAS[next]+"。");}
        return here.stream().noneMatch(p->DarkCityEntrance.contains(d.cell,next,p.position()));
    }
    public void brain(Dungeon d,DungeonEnemy e,List<ServerPlayer> here,long now){
        DarkCityEnemyKind kind=DarkCityEnemyKind.of(e);if(kind==null)return;
        Brain b=brains.get(e.getUUID());
        if(b==null){b=new Brain(e,d);brains.put(e.getUUID(),b);if(kind==TINGO)defense(e,false);if(kind==GOLIATH)e.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(b.knockback);}
        if(CombatContentTypes.controlled(e)){cancel(e.getUUID());return;}
        if(!DarkCityEntrance.contains(d.cell,b.area,e.position())){cancel(e.getUUID());e.getNavigation().moveTo(b.home.x,b.home.y,b.home.z,1);return;}
        final int area=b.area;
        ServerPlayer p=here.stream().filter(q->eligible(q,d.id.toString())&&DarkCityEntrance.contains(d.cell,area,q.position())).min(Comparator.comparingDouble(e::distanceToSqr)).orElse(null);
        if(p==null){cancel(e.getUUID());return;}
        if(now<b.busy){e.getNavigation().stop();return;}
        Vec3 direction=p.position().subtract(e.position()).multiply(1,0,1).normalize();if(direction.lengthSqr()<.001)direction=new Vec3(0,0,1);
        float yaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z));e.setYRot(yaw);e.yBodyRot=yaw;e.setYHeadRot(yaw);e.getLookControl().setLookAt(p);
        double distance=e.distanceTo(p);boolean ranged=kind==HEADLESS_SKELETON||kind==ARCHER;
        if(kind==ARCHER&&distance<4){Vec3 to=DarkCityEntrance.inside(d.cell,b.area,e.position().subtract(direction.scale(3)));e.getNavigation().moveTo(to.x,to.y,to.z,1);}
        else if(distance>(ranged?8:kind==HEADLESS_KNIGHT?4:2.3))e.getNavigation().moveTo(p,1);
        else e.getNavigation().stop();
        DarkCityMoves move=switch(kind){
            case HEADLESS_GRAVEROBBER -> available(d,b,CLAW,distance,now)?CLAW:null;
            case HEADLESS_SKELETON -> available(d,b,SKULL,distance,now)?SKULL:null;
            case GUARD -> available(d,b,ARROW,distance,now)?ARROW:null;
            case GOLIATH -> available(d,b,SMASH,distance,now)?SMASH:null;
            case TINGO -> distance<=8&&b.ready(DarkCityMoves.GUARD,now)?DarkCityMoves.GUARD:available(d,b,SHIELD_BASH,distance,now)?SHIELD_BASH:available(d,b,TINGO_SLASH,distance,now)?TINGO_SLASH:null;
            case HUMPHREY -> available(d,b,QUAKE,distance,now)?QUAKE:available(d,b,HAMMER,distance,now)?HAMMER:null;
            case HEADLESS_KNIGHT -> available(d,b,NIGHTMARE,distance,now)?NIGHTMARE:distance>=7&&available(d,b,CHARGE,distance,now)?CHARGE:available(d,b,SPIN,distance,now)?SPIN:available(d,b,KICK,distance,now)?KICK:available(d,b,SLASH,distance,now)?SLASH:null;
        };
        if(move!=null)play(d,e,b,move,direction,now);
    }
    private boolean eligible(ServerPlayer p,String instance){return p.level()==owner.level()&&owner.participant(p,instance)&&p.isAlive()&&!players.session(p).life.dead&&!p.isSpectator();}
    private boolean available(Dungeon d,Brain b,DarkCityMoves move,double distance,long now){
        return distance<=move.range&&b.ready(move,now)&&(!move.ranged()||attacks.values().stream().filter(a->a.attack.instance.equals(d.id.toString())&&a.area==b.area&&now<a.attack.end()&&DarkCityMoves.valueOf(a.attack.kind).ranged()).map(a->a.group).distinct().count()<RANGED_GROUP_LIMIT);
    }
    private void play(Dungeon d,DungeonEnemy e,Brain b,DarkCityMoves move,Vec3 direction,long now){
        e.getNavigation().stop();e.setAggressive(true);e.play(move.animation,move.duration());b.busy=now+move.duration();b.ready.put(move,now+move.cooldown);
        if(move==SMASH){b.resistantUntil=now+move.windup+move.active;e.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);}
        if(move==DarkCityMoves.GUARD)guards.put(e.getUUID(),new Guard(now+move.windup,now+move.windup+move.active));
        String group=UUID.randomUUID().toString();int count=move==NIGHTMARE?4:1;
        for(int i=0;i<count;i++){
            Vec3 heading=direction.yRot((float)(i*Math.PI/2));GblAttack a=new GblAttack();
            a.source=e.getUUID();a.instance=e.instance();a.kind=move.name();a.shape=move.shape;a.started=now;a.windup=move.windup;a.active=move.active;
            a.radius=move.range;a.width=move==CHARGE?e.getBbWidth()/2:move.width();a.halfAngle=Math.PI/3;a.coefficient=move.coefficient;a.magic=move.magic;a.element=move.element;
            a.origin(e.position().add(0,move.height(),0));
            Vec3 end=a.origin().add(heading.scale(move.range));
            if(move==CHARGE)end=DarkCityEntrance.inside(d.cell,b.area,end);
            a.ex=end.x;a.ey=end.y;a.ez=end.z;
            Pending pending=new Pending(a,group,b.area);attacks.put(a.id,pending);owner.broadcast(a.instance,"darkCityAttack",packet(pending));
        }
    }
    private JsonObject packet(Pending pending){
        JsonObject j=JSON.toJsonTree(pending.attack).getAsJsonObject();j.addProperty("group",pending.group);
        Entity raw=owner.level().getEntity(pending.attack.source);if(raw instanceof DungeonEnemy e)j.add("boss",JSON.toJsonTree(targets.target(e)));
        return j;
    }
    public void tick(List<ServerPlayer> online,long now){
        Set<UUID> present=new HashSet<>();
        for(var p:online)if(p.level()==owner.level()){
            var s=players.session(p);Dungeon d=owner.byId(s.life.instance);
            if(d!=null&&d.dungeon.equals("dark_city")&&d.status.equals("RUNNING")&&owner.participant(p,d.id.toString())){
                present.add(p.getUUID());String key=d.id+"/"+s.id+"/"+s.life.generation;
                if(!key.equals(tracked.get(p.getUUID())))track(p);
            }
        }
        tracked.keySet().retainAll(present);
        for(var pending:new ArrayList<>(attacks.values())){
            GblAttack a=pending.attack;Dungeon d=owner.byId(a.instance);Entity raw=owner.level().getEntity(a.source);
            if(!(raw instanceof DungeonEnemy e)||!alive(e)||d==null||!d.status.equals("RUNNING")||CombatContentTypes.controlled(e)){cancel(a.source);continue;}
            if(now<a.release())continue;
            long at=Math.min(now,a.end());Vec3 from=a.position(Math.max(a.release(),pending.previous)),to=a.position(at);
            if(a.shape.equals("CHARGE")){
                e.move(MoverType.SELF,to.subtract(e.position()));e.setDeltaMovement(Vec3.ZERO);
                if(e.position().subtract(to).lengthSqr()>.09){remove(pending);continue;}
            }else if(a.shape.equals("LINE")&&owner.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,e)).getType()!=HitResult.Type.MISS){remove(pending);continue;}
            pending.previous=at;
            // Natural expiry is already in the shared timeline. Only interruption needs a cancellation message.
            if(now>=a.end())attacks.remove(a.id,pending);
        }
        for(var entry:new ArrayList<>(guards.entrySet())){
            Entity raw=owner.level().getEntity(entry.getKey());Guard g=entry.getValue();
            if(!(raw instanceof DungeonEnemy e)||!alive(e)||CombatContentTypes.controlled(e)||now>=g.until){if(raw instanceof DungeonEnemy e)defense(e,false);guards.remove(entry.getKey());continue;}
            defense(e,now>=g.from);
        }
        for(var entry:new ArrayList<>(brains.entrySet())){
            Entity raw=owner.level().getEntity(entry.getKey());
            if(!(raw instanceof DungeonEnemy e)||!alive(e)){cancel(entry.getKey());brains.remove(entry.getKey());continue;}
            Brain b=entry.getValue();if(now>=b.busy)e.setAggressive(false);
            if(DarkCityEnemyKind.of(e)==GOLIATH&&now>=b.resistantUntil)e.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(b.knockback);
        }
    }
    private boolean alive(DungeonEnemy e){return e.isAlive()&&!targets.target(e).life.dead;}
    private void defense(DungeonEnemy e,boolean active){
        Target t=targets.target(e);double value=active?GUARD_DEFENSE_INCREASE:0;
        if(t.encounterDefenseIncrease!=value){
            t.encounterDefenseIncrease=value;
            try{journal.put("target/"+e.getUUID(),t);}catch(Exception failure){throw new IllegalStateException("仃高守势保存失败",failure);}
            e.setData(CombatContentTypes.TARGET,JSON.toJson(t));
            for(var p:owner.level().players())if(owner.participant(p,e.instance()))targets.context(p,e);
        }
        e.guarded(active);
    }
    private void remove(Pending pending){if(attacks.remove(pending.attack.id,pending)){pending.attack.remove=true;owner.broadcast(pending.attack.instance,"darkCityAttack",packet(pending));}}
    public void cancel(UUID id){
        for(var pending:new ArrayList<>(attacks.values()))if(pending.attack.source.equals(id))remove(pending);
        guards.remove(id);Entity raw=owner.level().getEntity(id);Brain b=brains.get(id);
        if(raw instanceof DungeonEnemy e&&DarkCityEnemyKind.of(e)!=null){
            if(DarkCityEnemyKind.of(e)==TINGO)defense(e,false);
            e.getNavigation().stop();e.setDeltaMovement(new Vec3(0,e.getDeltaMovement().y,0));e.setAggressive(false);e.play("",0);
            if(b!=null)e.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(b.knockback);
        }
        if(b!=null){b.busy=0;b.resistantUntil=0;}
    }
    public void died(UUID id){cancel(id);brains.remove(id);}
    public void track(ServerPlayer p){
        var s=players.session(p);Dungeon d=owner.byId(s.life.instance);
        if(d==null||!d.dungeon.equals("dark_city")||!d.status.equals("RUNNING")||!owner.participant(p,d.id.toString()))return;
        long now=System.currentTimeMillis();
        for(var pending:attacks.values())if(pending.attack.instance.equals(d.id.toString())&&now<pending.attack.end())Network.send(p,"darkCityAttack",packet(pending));
        tracked.put(p.getUUID(),d.id+"/"+s.id+"/"+s.life.generation);
    }
    public void close(Dungeon d){
        String instance=d.id.toString();
        for(var pending:new ArrayList<>(attacks.values()))if(pending.attack.instance.equals(instance))remove(pending);
        for(var entry:new ArrayList<>(brains.entrySet())){Entity raw=owner.level().getEntity(entry.getKey());if(raw instanceof DungeonEnemy e&&e.instance().equals(instance)){cancel(entry.getKey());brains.remove(entry.getKey());}}
        announced.remove(d.id);tracked.values().removeIf(key->key.startsWith(instance+"/"));
    }
}
