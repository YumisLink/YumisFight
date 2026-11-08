package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.combat.api.*;
import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.dungeons.content.ClassicDungeonRooms;
import cn.yumis.fight.network.Network;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;

/** Shared scheduling for these eight content sets. Dungeon-specific choices and gimmicks stay in their own services. */
abstract class ClassicEncounterService {
    protected static final Gson JSON=new Gson();
    protected final DungeonService owner;
    protected final Journal journal;
    protected final DungeonPorts.Players players;
    protected final DungeonPorts.Targets targets;
    final String dungeon;
    private final ClassicEnemyKind boss;
    private final ClassicEnemyKind[][] waves;
    private final String[] areas;
    protected static final class Brain {
        final String instance;final int area;final Vec3 home;
        final Map<String,Long> ready=new HashMap<>();
        long busy,effectAt,expires,guardUntil,hideAt,hiddenUntil,airFrom,airUntil;int turn;boolean auxiliary,effectApplied,hard;
        ClassicMove casting;UUID locked;Vec3 destination=Vec3.ZERO,direction=new Vec3(0,0,1);
        Brain(Dungeon d,DungeonEnemy e){instance=e.instance();area=e.getPersistentData().getIntOr("ydnf_classic_area",Math.min(6,d.wave));home=e.position();auxiliary=e.getPersistentData().getBooleanOr("ydnf_classic_aux",false);busy=System.currentTimeMillis()+Math.floorMod(e.getUUID().hashCode(),700);}
        boolean ready(ClassicMove move,long now){return move!=null&&now>=ready.getOrDefault(move.id(),0L);}
    }
    protected static final class Pending {
        final PalaceAttack attack;final ClassicMove move;final Target source;final int cell,area;
        double arc,enemyFraction;UUID lockedPlayer;boolean retired,unstoppable,published;long previous;
        Pending(PalaceAttack a,ClassicMove m,Target source,int cell,int area){attack=a;move=m;this.source=source;this.cell=cell;this.area=area;previous=a.release();}
        Vec3 position(long now){Vec3 p=attack.position(now);return arc>0?p.add(0,arc*Math.sin(Math.PI*attack.progress(now)),0):p;}
    }
    protected final Map<UUID,Brain> brains=new LinkedHashMap<>();
    protected final Map<String,Pending> attacks=new LinkedHashMap<>();
    private final Map<UUID,String> tracked=new HashMap<>();
    private final Map<UUID,Integer> announced=new HashMap<>();

    ClassicEncounterService(DungeonService owner,Journal journal,DungeonPorts.Players players,DungeonPorts.Targets targets,
                            String dungeon,ClassicEnemyKind boss,ClassicEnemyKind[][] waves,String[] areas){
        this.owner=owner;this.journal=journal;this.players=players;this.targets=targets;this.dungeon=dungeon;this.boss=boss;this.waves=waves;this.areas=areas;
    }
    final void spawn(Dungeon d){
        if(d.wave==6){DungeonEnemy e=owner.spawn(d,boss.id,false,true,d.cell*512,80,6*40+21);initialize(d,e);return;}
        var pack=waves[d.wave];
        if(pack.length!=10)throw new IllegalStateException("Classic pack must contain one elite and nine ordinary enemies");
        for(int i=0;i<pack.length;i++){
            double x=i==0?0:(i-1)%5*4-8,z=d.wave*40+(i==0?25:11+(i-1)/5*7);
            DungeonEnemy e=owner.spawn(d,pack[i].id,i==0,false,d.cell*512+x,80,z);initialize(d,e);
        }
    }
    final boolean waitsAtGate(Dungeon d,List<ServerPlayer> here){
        int next=d.wave+1;
        if(next>6)return false;
        if(announced.getOrDefault(d.id,0)<next){announced.put(d.id,next);for(var p:here)players.message(p,"区域清场，前往"+areas[next]+"。");}
        return here.stream().noneMatch(p->ClassicDungeonRooms.contains(d.cell,next,p.position()));
    }
    protected final Brain initialize(Dungeon d,DungeonEnemy e){
        Brain b=brains.get(e.getUUID());if(b!=null)return b;
        b=new Brain(d,e);brains.put(e.getUUID(),b);e.getPersistentData().putInt("ydnf_classic_area",b.area);
        ClassicEnemyKind kind=ClassicEnemyKind.of(e);
        if(kind.stationary||kind.passive)e.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        if(kind.flying){e.setNoGravity(true);e.setPos(e.getX(),80.35,e.getZ());}
        if(kind.passive)immune(e,true);
        onSpawn(d,e,b,System.currentTimeMillis());return b;
    }
    protected void onSpawn(Dungeon d,DungeonEnemy e,Brain b,long now){}
    protected boolean maintain(Dungeon d,DungeonEnemy e,Brain b,List<ServerPlayer> here,long now){return false;}
    protected void onStart(Dungeon d,DungeonEnemy e,Brain b,ClassicMove move,ServerPlayer locked,long now){}
    protected boolean customAttack(Dungeon d,DungeonEnemy e,Brain b,ClassicMove move,ServerPlayer locked,long now){return false;}
    protected void decorate(Pending pending,DungeonEnemy source){}
    protected void onAction(Dungeon d,DungeonEnemy e,Brain b,ClassicMove move,ServerPlayer locked,long now){
        switch(move.effect()){
            case "guard","aura" -> {defense(e,true);b.guardUntil=now+2000;}
            case "heal" -> {for(var ally:nearby(e,7))heal(ally,1/30d);}
            case "fall_heal" -> heal(e,1/30d);
            case "teleport" -> {Vec3 at=ClassicDungeonRooms.inside(d.cell,b.area,b.destination.add(b.direction.scale(-3)));e.setPos(at.x,ClassicEnemyKind.of(e).flying?80.35:80,at.z);}
            case "inhale" -> heal(e,1/30d);
            case "takeoff" -> {b.airFrom=now;b.airUntil=now+3000;e.setNoGravity(true);}
        }
        if(move.clip().equals("explode"))selfDestruct(e);
    }
    protected void onDeath(DungeonEnemy e,Brain b){}
    protected void onCancel(DungeonEnemy e,Brain b){}

    final void brain(Dungeon d,DungeonEnemy e,List<ServerPlayer> here,long now){
        ClassicEnemyKind kind=ClassicEnemyKind.of(e);if(kind==null||!kind.dungeon.equals(dungeon))return;
        Brain b=initialize(d,e);Target t=targets.target(e);
        if(b.guardUntil>0&&now>=b.guardUntil){b.guardUntil=0;defense(e,false);}
        if(b.hideAt>0&&now>=b.hideAt){b.hideAt=0;immune(e,true);e.setInvisible(true);}
        if(b.hiddenUntil>0&&now>=b.hiddenUntil){b.hiddenUntil=0;e.setInvisible(false);immune(e,false);Vec3 at=ClassicDungeonRooms.inside(d.cell,b.area,b.destination);e.setPos(at.x,80,at.z);}
        if(b.airUntil>0){
            double phase=Math.clamp((now-b.airFrom)/(double)Math.max(1,b.airUntil-b.airFrom),0,1);e.setPos(e.getX(),80+4*Math.sin(Math.PI*phase),e.getZ());
            if(now>=b.airUntil){b.airUntil=0;e.setNoGravity(kind.flying);e.setPos(e.getX(),kind.flying?80.35:80,e.getZ());}
        }
        if(t.encounterReviveUntil>0){
            if(!e.move().equals("revive")){interrupt(e.getUUID());e.play("revive",t.encounterReviveMillis);}
            e.getNavigation().stop();
            if(now>=t.encounterReviveUntil){
                t.encounterReviveUntil=0;t.damageImmune=false;t.life.dead=false;t.life.q=1;t.life.generation++;
                t.life.revision++;t.life.elemental=new ElementAccumulation.State();e.removeAllEffects();persist(e,t,true);e.play("",0);b.busy=now+400;
            }
            return;
        }
        boolean held=maintain(d,e,b,here,now);
        if(kind.passive||kind.mechanism()||e.getPersistentData().getBooleanOr("ydnf_classic_tree",false))return;
        if(CombatContentTypes.controlled(e)&&!b.hard){cancel(e.getUUID());return;}
        if(!ClassicDungeonRooms.contains(d.cell,b.area,e.position())){
            cancel(e.getUUID());Vec3 home=ClassicDungeonRooms.inside(d.cell,b.area,b.home);
            if(e.getY()<78||e.getY()>98)e.setPos(home.x,kind.flying?80.35:80,home.z);
            else e.getNavigation().moveTo(home.x,home.y,home.z,1);
            return;
        }
        if(b.casting!=null&&now>=b.effectAt&&!b.effectApplied){
            b.effectApplied=true;ServerPlayer locked=b.locked==null?null:owner.level().getServer().getPlayerList().getPlayer(b.locked);
            onAction(d,e,b,b.casting,locked,now);
        }
        if(held)return;
        if(now<b.busy){e.getNavigation().stop();return;}
        b.casting=null;b.hard=false;e.setAggressive(false);
        ServerPlayer target=here.stream().filter(p->eligible(p,d)&&ClassicDungeonRooms.contains(d.cell,b.area,p.position())).min(Comparator.comparingDouble(e::distanceToSqr)).orElse(null);
        if(target==null){cancel(e.getUUID());return;}
        Vec3 direction=target.position().subtract(e.position()).multiply(1,0,1).normalize();if(direction.lengthSqr()<.001)direction=new Vec3(0,0,1);
        if(!kind.model.equals("tank_bunker")){float yaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z));e.setYRot(yaw);e.yBodyRot=yaw;e.setYHeadRot(yaw);e.getLookControl().setLookAt(target);}
        double distance=e.distanceTo(target);List<ClassicMove> moves=ClassicMove.forActor(kind);
        boolean ranged=moves.stream().anyMatch(ClassicMove::ranged);double reach=ranged?Math.min(9,moves.stream().filter(ClassicMove::ranged).mapToDouble(ClassicMove::engageRange).max().orElse(10)-1):2.5;
        if(!kind.stationary){
            if(ranged&&distance<3.5){Vec3 to=ClassicDungeonRooms.inside(d.cell,b.area,e.position().subtract(direction.scale(3)));e.getNavigation().moveTo(to.x,to.y,to.z,1);}
            else if(distance>reach)e.getNavigation().moveTo(target,1);else e.getNavigation().stop();
        }
        ClassicMove move=choose(d,e,b,target,distance,now);
        if(move!=null)play(d,e,b,move,target,now);
    }
    protected boolean eligible(ServerPlayer p,Dungeon d){return p.level()==owner.level()&&owner.participant(p,d.id.toString())&&p.isAlive()&&!players.session(p).life.dead&&!p.isSpectator();}
    protected ClassicMove choose(Dungeon d,DungeonEnemy e,Brain b,ServerPlayer target,double distance,long now){
        var moves=ClassicMove.forActor(ClassicEnemyKind.of(e));
        for(int i=0;i<moves.size();i++){
            ClassicMove m=moves.get(Math.floorMod(b.turn+i,moves.size()));
            if(available(d,b,m,distance,now)){b.turn=Math.floorMod(b.turn+i+1,moves.size());return m;}
        }
        return null;
    }
    protected final boolean available(Dungeon d,Brain b,ClassicMove move,double distance,long now){
        if(!b.ready(move,now)||distance>move.engageRange()+.5)return false;
        return !move.ranged()||attacks.values().stream().filter(p->p.attack.instance.equals(d.id.toString())&&p.area==b.area&&p.move.ranged()&&now<p.attack.end()).map(p->p.attack.group.split("/pulse/")[0]).distinct().count()<2;
    }
    protected final ClassicMove move(DungeonEnemy e,String clip){return ClassicMove.find(ClassicEnemyKind.of(e),clip);}
    protected final void play(Dungeon d,DungeonEnemy e,Brain b,ClassicMove move,ServerPlayer target,long now){
        if(move==null)return;
        e.getNavigation().stop();e.setAggressive(true);e.play(move.clip(),move.duration());
        b.casting=move;b.busy=now+move.duration();b.effectAt=now+move.contact(0);b.effectApplied=false;b.locked=target==null?null:target.getUUID();
        b.ready.put(move.id(),now+move.cooldown());
        Vec3 goal=target==null?e.position().add(0,0,8):target.position();
        b.direction=goal.subtract(e.position()).multiply(1,0,1).normalize();if(b.direction.lengthSqr()<.001)b.direction=new Vec3(0,0,1);
        b.destination=ClassicDungeonRooms.inside(d.cell,b.area,goal);
        if(move.effect().equals("burrow")){b.hideAt=now+Math.round(move.duration()*.30);b.hiddenUntil=now+Math.round(move.duration()*.69);}
        onStart(d,e,b,move,target,now);
        if(customAttack(d,e,b,move,target,now))return;
        if(move.shape().equals("NONE")){
            emit(d,e,b,move,"NONE",e.position(),e.position(),now,move.windup(),Math.max(1,move.duration()-move.windup()),0,UUID.randomUUID().toString());
            return;
        }
        String group=UUID.randomUUID().toString();double[] phases=move.contacts();
        for(int i=0;i<phases.length;i++){
            Vec3 from=new Vec3(e.getX(),80,e.getZ()),to=from.add(b.direction.scale(move.range()));
            long delay=move.contact(i),active=150;
            switch(move.shape()){
                case "DART" -> {from=e.position().add(0,.9,0);Vec3 aim=b.destination.add(0,.8,0).subtract(from).normalize();to=from.add(aim.scale(move.range()));active=1000;}
                case "BOMB" -> {from=e.position().add(0,1.2,0);to=b.destination.add(0,.15,0);active=900;}
                case "DASH" -> {to=ClassicDungeonRooms.inside(d.cell,b.area,e.position().add(b.direction.scale(move.range())));delay=move.windup();active=Math.max(200,Math.round(move.duration()*.39));}
                case "WAVE" -> active=650;
                case "CIRCLE" -> {if(move.targetsGround())from=b.destination;to=from.add(b.direction);if(move.clip().equals("inhale"))active=Math.max(150,move.duration()-delay);}
                default -> { }
            }
            Pending pending=emit(d,e,b,move,move.shape(),from,to,now,delay,active,move.coefficient()/phases.length,group+"/pulse/"+i);
            if(move.shape().equals("DASH")&&Set.of("jump","approach_hop","transform_pounce").contains(move.clip())){pending.arc=2.7;publish(pending);}
            if(move.clip().equals("mark")&&target!=null){pending.lockedPlayer=target.getUUID();publish(pending);}
        }
    }
    protected final Pending emit(Dungeon d,DungeonEnemy e,Brain b,ClassicMove move,String shape,Vec3 from,Vec3 to,long now,long delay,long active,double coefficient,String group){
        PalaceAttack a=new PalaceAttack();a.instance=e.instance();a.source=e.getUUID();a.kind=move.id();a.group=group;a.shape=shape;
        a.started=now;a.windup=delay;a.active=active;a.origin(from);a.destination(to);a.radius=move.range();a.width=move.width();
        a.halfAngle=Math.PI/3;a.coefficient=coefficient;a.magic=move.magic();a.element=move.element();
        Pending p=new Pending(a,move,JSON.fromJson(JSON.toJson(targets.target(e)),Target.class),d.cell,b.area);p.unstoppable=b.hard;decorate(p,e);attacks.put(a.id,p);return p;
    }
    private JsonObject packet(Pending p){
        JsonObject j=JSON.toJsonTree(p.attack).getAsJsonObject();j.add("boss",JSON.toJsonTree(p.source));j.addProperty("cell",p.cell);j.addProperty("area",p.area);
        j.addProperty("arc",p.arc);j.addProperty("retired",p.retired);j.addProperty("unstoppable",p.unstoppable);if(p.lockedPlayer!=null)j.addProperty("lockedPlayer",p.lockedPlayer.toString());
        if(p.enemyFraction>0)j.addProperty("enemyFraction",p.enemyFraction);return j;
    }
    protected final void publish(Pending p){p.published=true;owner.broadcast(p.attack.instance,"classicAttack",packet(p));}
    protected final void remove(Pending p,boolean cancelled,long at){
        if(!attacks.remove(p.attack.id,p))return;p.attack.remove=true;p.attack.cancelled=cancelled;JsonObject j=packet(p);j.addProperty("contactUntil",at);owner.broadcast(p.attack.instance,"classicAttack",j);
    }
    protected final DungeonEnemy child(Dungeon d,DungeonEnemy parent,ClassicEnemyKind kind,Vec3 at,long lifetime){
        Brain pb=initialize(d,parent);Vec3 inside=ClassicDungeonRooms.inside(d.cell,pb.area,at);
        DungeonEnemy e=owner.spawn(d,kind.id,false,false,inside.x,80,inside.z);
        e.getPersistentData().putString("ydnf_parent",parent.getStringUUID());e.getPersistentData().putBoolean("ydnf_classic_aux",true);e.getPersistentData().putInt("ydnf_classic_area",pb.area);
        d.enemies.remove(e.getUUID());try{journal.put("instance/"+d.id,d);}catch(Exception failure){throw new IllegalStateException(failure);}
        Target t=targets.target(e);t.noRewards=true;persist(e,t,false);Brain b=initialize(d,e);b.auxiliary=true;b.expires=lifetime>0?System.currentTimeMillis()+lifetime:0;
        for(var p:owner.level().players())if(owner.participant(p,e.instance()))targets.context(p,e);return e;
    }
    protected final List<DungeonEnemy> children(DungeonEnemy e){
        List<DungeonEnemy> result=new ArrayList<>();for(Entity raw:owner.level().getAllEntities())if(raw instanceof DungeonEnemy child&&child.isAlive()&&!targets.target(child).life.dead&&child.instance().equals(e.instance())&&child.getPersistentData().getStringOr("ydnf_parent","").equals(e.getStringUUID()))result.add(child);return result;
    }
    protected final void retire(DungeonEnemy e){
        interrupt(e.getUUID());brains.remove(e.getUUID());Target t=targets.target(e);
        if(!t.life.dead){t.noRewards=true;t.life.dead=true;t.life.q=0;t.life.revision++;persist(e,t,true);}e.discard();
    }
    protected final void immune(DungeonEnemy e,boolean immune){Target t=targets.target(e);if(t.damageImmune!=immune){t.damageImmune=immune;persist(e,t,false);}}
    protected final void defense(DungeonEnemy e,boolean guarded){Target t=targets.target(e);double value=guarded?.5:0;if(t.encounterDefenseIncrease!=value){t.encounterDefenseIncrease=value;persist(e,t,false);}e.guarded(guarded);}
    protected final void heal(DungeonEnemy e,double fraction){Target t=targets.target(e);if(t.life.dead||t.encounterReviveUntil>0||t.life.q>=1||fraction<=0)return;t.life.q=Math.min(1,t.life.q+fraction);t.life.revision++;persist(e,t,true);}
    protected final void persist(DungeonEnemy e,Target t,boolean life){
        try{journal.begin();journal.put("target/"+e.getUUID(),t);if(life)journal.put("life/"+e.getUUID(),t.life);journal.finish();}
        catch(Exception failure){journal.abort();throw new IllegalStateException("Classic encounter state could not be saved",failure);}
        e.setData(CombatContentTypes.TARGET,JSON.toJson(t));if(life)e.setHealth((float)Math.max(.001,e.getMaxHealth()*t.life.q));
        for(var p:owner.level().players())if(owner.participant(p,e.instance()))targets.context(p,e);
    }
    protected final boolean alive(DungeonEnemy e){return e!=null&&e.isAlive()&&!targets.target(e).life.dead;}
    protected final List<DungeonEnemy> nearby(DungeonEnemy e,double range){List<DungeonEnemy> result=new ArrayList<>();for(Entity raw:owner.level().getAllEntities())if(raw instanceof DungeonEnemy other&&alive(other)&&other.instance().equals(e.instance())&&e.distanceToSqr(other)<=range*range)result.add(other);return result;}
    protected final void selfDestruct(DungeonEnemy e){
        for(var p:attacks.values())if(p.attack.source.equals(e.getUUID())){p.retired=true;publish(p);}
        Target t=targets.target(e);t.noRewards=true;t.life.q=0;t.life.dead=true;t.life.revision++;persist(e,t,true);
    }
    protected final DungeonEnemy entity(UUID id){Entity raw=id==null?null:owner.level().getEntity(id);return raw instanceof DungeonEnemy e?e:null;}
    protected final void announce(Dungeon d,String text){for(var p:owner.level().players())if(owner.participant(p,d.id.toString()))players.message(p,text);}
    final void tick(List<ServerPlayer> online,long now){
        Set<UUID> present=new HashSet<>();
        for(var p:online)if(p.level()==owner.level()){
            var s=players.session(p);Dungeon d=owner.byId(s.life.instance);
            if(d!=null&&d.dungeon.equals(dungeon)&&d.status.equals("RUNNING")&&owner.participant(p,d.id.toString())){
                present.add(p.getUUID());String key=d.id+"/"+s.id+"/"+s.life.generation;if(!key.equals(tracked.get(p.getUUID())))track(p);
            }
        }
        tracked.keySet().retainAll(present);
        for(var entry:new ArrayList<>(brains.entrySet())){
            DungeonEnemy e=entity(entry.getKey());Brain b=entry.getValue();Dungeon d=owner.byId(b.instance);
            if(!alive(e)){if(e!=null)cancel(e.getUUID());brains.remove(entry.getKey());continue;}
            if(d==null||!d.status.equals("RUNNING")||d.wave!=b.area||b.expires>0&&now>=b.expires){retire(e);continue;}
            if(b.auxiliary){String parent=e.getPersistentData().getStringOr("ydnf_parent","");if(!parent.isEmpty()&&!alive(entity(UUID.fromString(parent)))){retire(e);continue;}}
            if(b.auxiliary)brain(d,e,online.stream().filter(p->eligible(p,d)).toList(),now);
        }
        for(var p:new ArrayList<>(attacks.values())){
            var a=p.attack;Dungeon d=owner.byId(a.instance);DungeonEnemy e=entity(a.source);Brain b=brains.get(a.source);
            if(d==null||!d.status.equals("RUNNING")||d.wave!=p.area||!p.retired&&(!alive(e)||CombatContentTypes.controlled(e)&&(b==null||!b.hard))){remove(p,true,now);continue;}
            if(!p.published)publish(p);
            if(now<a.release())continue;long at=Math.min(now,a.end());
            if(a.shape.equals("DASH")&&e!=null){
                e.setNoGravity(p.arc>0||ClassicEnemyKind.of(e).flying);Vec3 wanted=p.position(at);e.move(MoverType.SELF,wanted.subtract(e.position()));e.setDeltaMovement(Vec3.ZERO);
                if(e.position().distanceToSqr(wanted)>.16){remove(p,true,at);e.setNoGravity(ClassicEnemyKind.of(e).flying);continue;}
            }
            if(a.shape.equals("DART")||a.shape.equals("BOMB")){
                for(long time=p.previous;time<at;){long next=Math.min(at,time+25);var wall=owner.level().clip(new ClipContext(p.position(time),p.position(next),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,e));
                    if(wall.getType()!=HitResult.Type.MISS){if(a.shape.equals("BOMB")){a.destination(wall.getLocation());a.active=Math.max(1,next-a.release());}remove(p,false,next);break;}time=next;}
            }
            p.previous=at;
            if(now>=a.end()){attacks.remove(a.id);if(a.shape.equals("DASH")&&e!=null)e.setNoGravity(ClassicEnemyKind.of(e).flying);}
        }
    }
    final void track(ServerPlayer p){
        var s=players.session(p);Dungeon d=owner.byId(s.life.instance);if(d==null||!d.dungeon.equals(dungeon)||!d.status.equals("RUNNING")||!owner.participant(p,d.id.toString()))return;
        long now=System.currentTimeMillis();for(var a:attacks.values())if(a.attack.instance.equals(d.id.toString())&&now<a.attack.end())Network.send(p,"classicAttack",packet(a));
        tracked.put(p.getUUID(),d.id+"/"+s.id+"/"+s.life.generation);
    }
    void cancel(UUID id){
        Brain b=brains.get(id);DungeonEnemy e=entity(id);
        if(b!=null&&b.hard&&alive(e)&&CombatContentTypes.controlled(e))return;
        interrupt(id);
    }
    protected final void interrupt(UUID id){
        for(var p:new ArrayList<>(attacks.values()))if(p.attack.source.equals(id)&&!p.retired)remove(p,true,System.currentTimeMillis());
        Brain b=brains.get(id);DungeonEnemy e=entity(id);if(b==null||e==null)return;
        onCancel(e,b);
        if(b.guardUntil>0){defense(e,false);b.guardUntil=0;}
        if(b.hiddenUntil>0){e.setInvisible(false);if(targets.target(e).encounterReviveUntil==0)immune(e,false);b.hiddenUntil=0;}
        b.hideAt=0;b.airUntil=0;b.casting=null;b.effectApplied=true;b.hard=false;b.busy=0;e.getNavigation().stop();e.setDeltaMovement(Vec3.ZERO);e.setNoGravity(ClassicEnemyKind.of(e).flying);e.setAggressive(false);e.play("",0);
    }
    final void died(UUID id){DungeonEnemy e=entity(id);Brain b=brains.get(id);if(e!=null&&b!=null)onDeath(e,b);interrupt(id);brains.remove(id);}
    void close(Dungeon d){
        if(!d.dungeon.equals(dungeon))return;for(var a:new ArrayList<>(attacks.values()))if(a.attack.instance.equals(d.id.toString()))remove(a,true,System.currentTimeMillis());
        for(var entry:new ArrayList<>(brains.entrySet()))if(entry.getValue().instance.equals(d.id.toString())){interrupt(entry.getKey());brains.remove(entry.getKey());}
        tracked.values().removeIf(key->key.startsWith(d.id+"/"));announced.remove(d.id);
    }
}
