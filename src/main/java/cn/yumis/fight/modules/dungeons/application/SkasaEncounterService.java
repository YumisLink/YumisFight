package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.combat.api.CombatContentTypes;
import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.dungeons.content.SkasaLair;
import cn.yumis.fight.network.Network;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;
import static cn.yumis.fight.modules.dungeons.api.SkasaEnemyKind.*;
import static cn.yumis.fight.modules.dungeons.api.SkasaMoves.*;

/** Snow-mountain content; health, contacts, progression and rewards retain their existing owners. */
public final class SkasaEncounterService {
    private static final Gson JSON=new Gson();
    private static final String ENVIRONMENT="skasa_environment_source";
    private static final SkasaMoves[] ROTATION={CLAW,SLAM,ROAR,CLAW,VORTEX,CLAW,TRIPLE,BREATH};
    private static final SkasaEnemyKind[][] WAVES={
        {MALE_WARRIOR,MALE_WARRIOR,MALE_WARRIOR,FEMALE_WARRIOR,FEMALE_WARRIOR,FEMALE_WARRIOR},
        {NASRA,MALE_WARRIOR,MALE_WARRIOR,MALE_WARRIOR,FEMALE_WARRIOR,FEMALE_WARRIOR},
        {ICE_SPIDER,ICE_SPIDER,ICE_SPIDER,ICE_SPIDER,ICE_SPIDER,ICE_SPIDER},
        {ICE_SPIDER,ICE_SPIDER,ICE_SPIDER,ICE_SPIDER,HATCHLING,HATCHLING},
        {HATCHLING,HATCHLING,HATCHLING,HATCHLING,HATCHLING,HATCHLING},
        {HATCHLING,HATCHLING,HATCHLING,HATCHLING,HATCHLING,ICE_SPIDER,ICE_SPIDER,ICE_SPIDER}
    };
    private static final class Brain {
        final Vec3 home;final int area;final String instance;
        final EnumMap<SkasaMoves,Long> ready=new EnumMap<>(SkasaMoves.class);
        long busy;int turn,clawSide=-1,groundSide=-1;
        Brain(DungeonEnemy enemy,Dungeon dungeon){home=enemy.position();area=SkasaLair.area(dungeon.wave);instance=enemy.instance();}
        boolean ready(SkasaMoves move,long now){return now>=ready.getOrDefault(move,0L);}
    }
    private static final class Pending {
        final SkasaAttack attack;
        long previous;
        Pending(SkasaAttack attack){this.attack=attack;previous=attack.release();}
    }
    private final DungeonService owner;
    private final Journal journal;
    private final DungeonPorts.Players players;
    private final DungeonPorts.Targets targets;
    private final Map<UUID,Brain> brains=new HashMap<>();
    private final Map<String,Pending> attacks=new LinkedHashMap<>();
    private final Map<String,UUID> emitters=new HashMap<>();
    private final Map<UUID,Integer> announced=new HashMap<>();
    private final Map<UUID,String> tracked=new HashMap<>();

    SkasaEncounterService(DungeonService owner,Journal journal,DungeonPorts.Players players,DungeonPorts.Targets targets){
        this.owner=owner;this.journal=journal;this.players=players;this.targets=targets;
    }
    void spawn(Dungeon d){
        try{
            DungeonEnemy emitter=emitter(d);
            if(d.wave==6){
                DungeonEnemy dragon=owner.spawn(d,ICE_DRAGON.id,false,true,d.cell*512,80,148);
                anchor(dragon,d.cell);return;
            }
            int area=SkasaLair.area(d.wave);
            for(int i=0;i<WAVES[d.wave].length;i++){
                var kind=WAVES[d.wave][i];
                owner.spawn(d,kind.id,kind==NASRA,false,d.cell*512+(i%4-1.5)*4,80,area*40+11+i/4*7);
            }
            if(d.wave==3||d.wave==5){
                SkasaAttack a=attack(d,emitter,ROUTE_BREATH,new Vec3(d.cell*512,80,area*40+30),new Vec3(0,0,-1),System.currentTimeMillis());
                add(a);
            }
        }catch(Exception failure){throw new IllegalStateException("斯卡萨遭遇生成失败",failure);}
    }
    private DungeonEnemy emitter(Dungeon d)throws Exception{
        UUID id=emitters.get(d.id.toString());
        if(id!=null&&owner.level().getEntity(id) instanceof DungeonEnemy existing)return existing;
        DungeonEnemy e=owner.spawn(d,ICE_DRAGON.id,false,true,d.cell*512,104,160);
        // It is only a signed-contact source, never a wave enemy, target or reward-bearing kill.
        d.enemies.remove(e.getUUID());e.getPersistentData().putBoolean(ENVIRONMENT,true);
        e.scripted();e.play("spent",Long.MAX_VALUE/4);e.setInvisible(true);e.setCustomNameVisible(false);
        e.noPhysics=true;e.setNoGravity(true);e.setInvulnerable(true);e.setDeltaMovement(Vec3.ZERO);
        Target t=targets.target(e);t.noRewards=true;t.damageImmune=true;t.life.revision++;
        journal.begin();try{journal.put("target/"+e.getUUID(),t);journal.put("life/"+e.getUUID(),t.life);journal.finish();}catch(Exception failure){journal.abort();throw failure;}
        e.setData(CombatContentTypes.TARGET,JSON.toJson(t));emitters.put(d.id.toString(),e.getUUID());return e;
    }
    public boolean waitsAtGate(Dungeon d,List<ServerPlayer> here){
        if(d.wave%2!=1)return false;
        int area=(d.wave+1)/2;
        if(announced.getOrDefault(d.id,0)<area){announced.put(d.id,area);for(var p:here)players.message(p,"区域清场，前往"+SkasaLair.AREAS[area]+"。");}
        return here.stream().noneMatch(p->SkasaLair.contains(d.cell,area,p.position()));
    }
    private boolean eligible(ServerPlayer p,String instance){
        return p.level()==owner.level()&&owner.participant(p,instance)&&p.isAlive()&&!players.session(p).life.dead&&!p.isSpectator()&&!p.getAbilities().invulnerable;
    }
    private boolean alive(DungeonEnemy e){return e.isAlive()&&!targets.target(e).life.dead;}
    private void anchor(DungeonEnemy e,int cell){
        e.getNavigation().stop();e.setDeltaMovement(Vec3.ZERO);e.setPos(cell*512,80,148);
        e.setYRot(180);e.yBodyRot=180;e.setYHeadRot(180);
    }
    public void brain(Dungeon d,DungeonEnemy e,List<ServerPlayer> here,long now){
        var kind=SkasaEnemyKind.of(e);if(kind==null||e.getPersistentData().getBooleanOr(ENVIRONMENT,false))return;
        Brain b=brains.computeIfAbsent(e.getUUID(),id->new Brain(e,d));
        if(kind==ICE_DRAGON)anchor(e,d.cell);
        if(CombatContentTypes.controlled(e)){cancel(e.getUUID());return;}
        if(!SkasaLair.contains(d.cell,b.area,e.position())){cancel(e.getUUID());e.getNavigation().moveTo(b.home.x,b.home.y,b.home.z,1);return;}
        ServerPlayer p=here.stream().filter(q->eligible(q,e.instance())&&SkasaLair.contains(d.cell,b.area,q.position())).min(Comparator.comparingDouble(e::distanceToSqr)).orElse(null);
        if(p==null){e.getNavigation().stop();return;}
        if(now<b.busy){e.getNavigation().stop();return;}
        if(kind==ICE_DRAGON){
            SkasaMoves move=ROTATION[b.turn%ROTATION.length];
            if(!b.ready(move,now))return;
            Vec3 origin=e.position();
            if(move==ROAR||move==BREATH)origin=origin.add(0,0,-2);
            if(move==VORTEX)origin=origin.add(0,0,-5);
            SkasaAttack a=attack(d,e,move,origin,new Vec3(0,0,-1),now);
            a.side=move==CLAW?b.clawSide:b.groundSide;
            if(move==CLAW)b.clawSide=-b.clawSide;
            if(move==SLAM||move==TRIPLE)b.groundSide=-b.groundSide;
            play(e,b,a,now);b.turn++;return;
        }
        Vec3 direction=p.position().subtract(e.position()).multiply(1,0,1).normalize();
        if(direction.lengthSqr()<.001)direction=new Vec3(0,0,1);
        float yaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z));e.setYRot(yaw);e.yBodyRot=yaw;e.getLookControl().setLookAt(p);
        double distance=e.distanceTo(p);boolean ranged=kind==ICE_SPIDER;
        if(ranged&&distance<3){
            Vec3 to=SkasaLair.inside(d.cell,b.area,e.position().subtract(direction.scale(2)));e.getNavigation().moveTo(to.x,to.y,to.z,1);
        }else if(distance>(ranged?8:2))e.getNavigation().moveTo(p,kind==FEMALE_WARRIOR||kind==NASRA?1.15:1);
        else e.getNavigation().stop();
        SkasaMoves move=switch(kind){
            case MALE_WARRIOR -> PUNCH;
            case FEMALE_WARRIOR -> KICK;
            case NASRA -> distance<=NASRA_CYCLONE.range&&b.ready(NASRA_CYCLONE,now)?NASRA_CYCLONE:NASRA_KICK;
            case ICE_SPIDER -> distance<=SPIDER_BITE.range&&b.ready(SPIDER_BITE,now)?SPIDER_BITE:ICE_LINE;
            case HATCHLING -> HATCHLING_CHARGE;
            default -> throw new IllegalStateException();
        };
        if(distance>move.range||!b.ready(move,now))return;
        if(kind==HATCHLING){
            Vec3 to=SkasaLair.inside(d.cell,b.area,e.position().subtract(direction.scale(2)));
            if(owner.level().noCollision(e,e.getBoundingBox().move(to.subtract(e.position()))))e.move(MoverType.SELF,to.subtract(e.position()));
        }
        play(e,b,attack(d,e,move,e.position(),direction,now),now);
    }
    private SkasaAttack attack(Dungeon d,DungeonEnemy e,SkasaMoves move,Vec3 origin,Vec3 direction,long now){
        SkasaAttack a=new SkasaAttack();a.source=e.getUUID();a.instance=e.instance();a.kind=move.name();a.cell=d.cell;
        a.area=SkasaLair.area(d.wave);a.wave=d.wave;a.started=now;a.x=origin.x;a.y=origin.y;a.z=origin.z;a.dx=direction.x;a.dz=direction.z;return a;
    }
    private void play(DungeonEnemy e,Brain b,SkasaAttack a,long now){
        SkasaMoves move=a.move();e.getNavigation().stop();e.setAggressive(true);e.play(move.animation(a.side),move.duration());
        b.busy=now+move.duration();b.ready.put(move,now+move.cooldown);add(a);
    }
    private void add(SkasaAttack a){attacks.put(a.id,new Pending(a));owner.broadcast(a.instance,"skasaAttack",packet(a,false));}
    private JsonObject packet(SkasaAttack a,boolean cancelled){var frame=JSON.toJsonTree(a).getAsJsonObject();frame.addProperty("cancelled",cancelled);frame.addProperty("contactUntil",System.currentTimeMillis());var raw=owner.level().getEntity(a.source);if(raw instanceof LivingEntity source)frame.add("boss",JSON.toJsonTree(targets.target(source)));return frame;}
    private void remove(SkasaAttack a){remove(a,true);}
    private void remove(SkasaAttack a,boolean cancelled){if(attacks.remove(a.id)!=null){a.remove=true;owner.broadcast(a.instance,"skasaAttack",packet(a,cancelled));}}
    public void tick(List<ServerPlayer> online,long now){
        Set<UUID> present=new HashSet<>();
        for(var p:online)if(p.level()==owner.level()){
            var s=players.session(p);Dungeon d=owner.byId(s.life.instance);
            if(d!=null&&d.dungeon.equals("skasa")&&d.status.equals("RUNNING")&&owner.participant(p,d.id.toString())){
                present.add(p.getUUID());String key=d.id+"/"+s.id+"/"+s.life.generation;
                if(!key.equals(tracked.get(p.getUUID())))track(p);
            }
        }
        tracked.keySet().retainAll(present);
        for(var pending:new ArrayList<>(attacks.values())){
            SkasaAttack a=pending.attack;SkasaMoves move=a.move();Dungeon d=owner.byId(a.instance);Entity raw=owner.level().getEntity(a.source);
            if(!(raw instanceof DungeonEnemy e)||!alive(e)||d==null||!d.status.equals("RUNNING")||CombatContentTypes.controlled(e)||(move==ROUTE_BREATH&&d.wave!=a.wave)){
                remove(a);continue;
            }
            if(now<a.release())continue;
            if(now>a.end()&&!move.travels()){remove(a,false);continue;}
            if(move.movesBody()){
                Vec3 to=SkasaLair.inside(d.cell,a.area,a.position(now));e.move(MoverType.SELF,to.subtract(e.position()));e.setDeltaMovement(Vec3.ZERO);
                if(e.position().subtract(to).horizontalDistanceSqr()>.25){remove(a);continue;}
            }
            if(move==ICE_LINE){
                Vec3 from=a.position(pending.previous).add(0,.7,0),to=a.position(now).add(0,.7,0);
                if(owner.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,e)).getType()!=HitResult.Type.MISS){remove(a,false);continue;}
            }
            pending.previous=now;
            if(now>=a.end())remove(a,false);
        }
        for(var entry:new ArrayList<>(brains.entrySet())){
            Entity raw=owner.level().getEntity(entry.getKey());
            if(!(raw instanceof DungeonEnemy e)||!alive(e)){brains.remove(entry.getKey());continue;}
            if(SkasaEnemyKind.of(e)==ICE_DRAGON){Dungeon d=owner.byId(e.instance());if(d!=null)anchor(e,d.cell);}
            if(now>=entry.getValue().busy)e.setAggressive(false);
        }
    }
    public void cancel(UUID id){
        for(var pending:new ArrayList<>(attacks.values()))if(pending.attack.source.equals(id))remove(pending.attack);
        Entity raw=owner.level().getEntity(id);
        if(raw instanceof DungeonEnemy e&&SkasaEnemyKind.of(e)!=null&&!e.getPersistentData().getBooleanOr(ENVIRONMENT,false)){
            e.getNavigation().stop();e.setAggressive(false);e.setDeltaMovement(Vec3.ZERO);e.play("",0);
        }
        Brain b=brains.get(id);if(b!=null)b.busy=0;
    }
    public void died(UUID id){cancel(id);brains.remove(id);}
    public void track(ServerPlayer p){
        var s=players.session(p);Dungeon d=owner.byId(s.life.instance);
        if(d==null||!d.dungeon.equals("skasa")||!d.status.equals("RUNNING")||!owner.participant(p,d.id.toString()))return;
        for(var pending:attacks.values())if(pending.attack.instance.equals(d.id.toString()))Network.send(p,"skasaAttack",packet(pending.attack,false));
        tracked.put(p.getUUID(),d.id+"/"+s.id+"/"+s.life.generation);
    }
    public void close(Dungeon d){
        String instance=d.id.toString();
        for(var pending:new ArrayList<>(attacks.values()))if(pending.attack.instance.equals(instance))remove(pending.attack);
        for(var entry:new ArrayList<>(brains.entrySet()))if(entry.getValue().instance.equals(instance)){cancel(entry.getKey());brains.remove(entry.getKey());}
        // DungeonService clears all cell entities after invalidating contacts, including this source.
        emitters.remove(instance);announced.remove(d.id);tracked.values().removeIf(key->key.startsWith(instance+"/"));
    }
}
