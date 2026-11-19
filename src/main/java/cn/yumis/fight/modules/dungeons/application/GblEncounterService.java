package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.modules.combat.api.*;
import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.dungeons.content.GblChurch;
import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.network.Network;
import com.google.gson.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;

/** GBL content AI, fixed telegraphs and world trajectories. Each recipient owns its contact and health calculation. */
public final class GblEncounterService {
    private static final Gson JSON=new Gson();
    private final DungeonService owner;
    private final Journal journal;
    private final DungeonPorts.Players players;
    private final DungeonPorts.Targets targets;
    private static final class Brain {
        final Vec3 home;
        UUID target;
        long busy,next,auraReady,slamReady,pathAt;
        int step;
        Brain(DungeonEnemy enemy){home=enemy.position();}
    }
    private static final class Pending {
        final GblAttack attack;long previous;
        Pending(GblAttack attack){this.attack=attack;previous=attack.release();}
    }
    private record Leap(UUID source,Vec3 from,Vec3 to,long start,long end,double height){}
    private record Aura(UUID source,String instance,long from,long until){}
    private final Map<UUID,Brain> brains=new HashMap<>();
    private final Map<String,Pending> attacks=new LinkedHashMap<>();
    private final Map<UUID,Leap> leaps=new HashMap<>();
    private final Map<UUID,Aura> auras=new HashMap<>();
    private final Map<UUID,UUID> bosses=new HashMap<>();
    private final Set<UUID> protectedEnemies=new HashSet<>();

    GblEncounterService(DungeonService owner,Journal journal,DungeonPorts.Players players,DungeonPorts.Targets targets){this.owner=owner;this.journal=journal;this.players=players;this.targets=targets;}
    static boolean handles(Dungeon d){return d.treasure==null&&d.practice==null&&GblChurch.ID.equals(d.dungeon);}

    void initialize(Dungeon d){
        if(bosses.containsKey(d.id))return;
        try{
            for(var mob:GblChurch.SPAWNS){
                var enemy=owner.spawn(d,mob.kind().id,mob.elite(),mob.boss(),d.cell*512+mob.x()+.5,mob.y(),mob.z()+.5);
                var follow=enemy.getAttribute(Attributes.FOLLOW_RANGE);
                if(follow!=null)follow.setBaseValue(56);
                if(mob.boss())bosses.put(d.id,enemy.getUUID());
            }
        }catch(RuntimeException failure){
            try{owner.close(d);}catch(Exception cleanup){failure.addSuppressed(cleanup);}
            throw failure;
        }
    }

    boolean finishIfDefeated(Dungeon d,List<ServerPlayer> here)throws Exception{
        UUID boss=bosses.get(d.id);
        if(boss==null||!d.status.equals("RUNNING"))return false;
        Target target=journal.get("target/"+boss,Target.class,null);
        if(target==null||!target.life.dead)return false;
        owner.completeOpenDungeon(d,here,"阿索尔已被击败！可拾取战利品或离开地下城。");
        for(UUID id:d.enemies){
            if(owner.level().getEntity(id) instanceof DungeonEnemy enemy)enemy.getNavigation().stop();
            cancel(id);
        }
        return true;
    }
    public void brain(Dungeon d,DungeonEnemy e,List<ServerPlayer> here,long now){
        var kind=GblEnemyKind.of(e);if(kind==null)return;
        Brain state=brains.computeIfAbsent(e.getUUID(),id->new Brain(e));
        if(CombatContentTypes.controlled(e)){cancel(e.getUUID());return;}
        if(now<state.busy){e.getNavigation().stop();return;}
        ServerPlayer p=here.stream().filter(q->q.getUUID().equals(state.target)&&eligible(q)).findFirst().orElse(null);
        if(p==null){
            p=here.stream().filter(q->eligible(q)&&e.distanceToSqr(q)<=18*18&&e.hasLineOfSight(q))
                .min(Comparator.comparingDouble(e::distanceToSqr)).orElse(null);
            state.target=p==null?null:p.getUUID();
        }
        if(p==null){
            if(e.position().distanceToSqr(state.home)>4)move(d,e,state,state.home,1,now);
            else e.getNavigation().stop();
            return;
        }
        e.getLookControl().setLookAt(p);double distance=e.distanceTo(p);
        boolean seen=e.hasLineOfSight(p);
        Vec3 direction=p.position().subtract(e.position()).multiply(1,0,1).normalize();
        if(direction.lengthSqr()<.001)direction=new Vec3(0,0,1);
        float yaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z));e.setYRot(yaw);e.yBodyRot=yaw;
        boolean ranged=kind==GblEnemyKind.FEMALE_CULTIST||kind==GblEnemyKind.FALLEN_PRIEST;
        double range=kind==GblEnemyKind.DRAGON_CANNON?12:ranged?8:kind==GblEnemyKind.ASOR?7:1.9;
        if(!seen||distance>range){
            move(d,e,state,p.position(),kind==GblEnemyKind.YASHA?1.1:kind==GblEnemyKind.DRAGON_CANNON?.83:1,now);
        }else if(ranged&&distance<4){
            move(d,e,state,ground(e.position().subtract(direction.scale(3)),e.position()),1,now);
        }else{
            e.getNavigation().stop();
            if(kind==GblEnemyKind.DRAGON_CANNON)e.setDeltaMovement(Vec3.ZERO);
        }
        if(kind==GblEnemyKind.DRAGON_CANNON&&seen&&distance<=range&&now<state.next&&!e.move().equals("reload"))e.play("reload",state.next-now);
        if(now<state.next||!seen||distance>18||kind==GblEnemyKind.DRAGON_CANNON&&distance>range)return;
        switch(kind){
            case CULTIST -> {if(distance<2.4){play(e,state,"stab",1250,now);sector(e,p,350,200,2.5,Math.PI/3,1);}}
            case FEMALE_CULTIST -> {if(distance<13){play(e,state,"throw",1400,now);projectile(e,p,"KNIFE",450,700,13,1,false,-1,0);}}
            case DRAGON_CANNON -> {
                boolean lob=state.step++%2==1;play(e,state,lob?"lob_shot":"straight_shot",lob?2200:1800,now);
                if(lob){var a=attack(e,"CANNON_LOB","LOB",700,1100,1.9,1.3,false,-1);a.origin(e.position().add(0,1,0));setDestination(a,ground(p.position(),p.position()));add(a);}
                else projectile(e,p,"CANNONBALL",600,800,15,1.2,false,-1,0);
                state.next=state.busy+1800;
            }
            case YASHA -> {
                if(distance>3&&distance<8&&state.step++%2==0){play(e,state,"pounce",1700,now);leap(e,p,450,700,1.1);impact(e,"POUNCE",1150,180,landing(e),2,1.35,false);}
                else if(distance<3){play(e,state,"claw",1450,now);sector(e,p,440,150,2.8,Math.PI/2,.6);sector(e,p,850,150,2.8,Math.PI/2,.6);}
            }
            case FALLEN_PRIEST -> {
                if(now>=state.auraReady){play(e,state,"defense_aura",2400,now);auras.put(e.getUUID(),new Aura(e.getUUID(),e.instance(),now+500,now+4500));state.auraReady=now+12000;}
                else if(distance<3.5&&now>=state.slamReady){play(e,state,"jump_slam",2000,now);leap(e,e.position(),450,800,1);impact(e,"PRIEST_SLAM",1300,200,e.position(),3.5,1.2,true);state.slamReady=now+7000;}
                else if(distance<13){play(e,state,"triple_needles",2000,now);for(int i=0;i<3;i++)projectile(e,p,"NEEDLE",480+i*300,650,13,.5,false,-1,(i-1)*.09);}
            }
            case ASOR -> {
                int move=state.step++%3;
                if(move==0){play(e,state,"needle_burst",2200,now);for(int i=0;i<6;i++)projectile(e,p,"ASOR_NEEDLE",530+i*165,650,15,.4,false,-1,(i-2.5)*.065);}
                else if(move==1){play(e,state,"shockwave",2300,now);leap(e,e.position(),550,950,1.4);var a=attack(e,"ASOR_WAVE","WAVE",1500,800,8,1.6,true,-1);a.origin(e.position());a.width=1.15;add(a);}
                else{
                    play(e,state,"goddess_spears",3000,now);Vec3 side=new Vec3(direction.z,0,-direction.x);
                    for(int i=0;i<5;i++){
                        Vec3 to=ground(p.position().add(side.scale((i-2)*2.1)),null);
                        if(to==null)continue;
                        var a=attack(e,"GODDESS_SPEAR","SPEAR",1150+i*200,180,1.4,.85,true,0);
                        a.origin(to);setDestination(a,to);add(a);
                    }
                }
                state.next=state.busy+700;
            }
        }
    }
    private void play(DungeonEnemy e,Brain b,String clip,long duration,long now){e.getNavigation().stop();e.play(clip,duration);e.setAggressive(true);b.busy=now+duration;b.next=b.busy+450;}
    private GblAttack attack(DungeonEnemy e,String kind,String shape,long windup,long active,double radius,double coefficient,boolean magic,int element){
        GblAttack a=new GblAttack();a.source=e.getUUID();a.instance=e.instance();a.kind=kind;a.shape=shape;a.started=System.currentTimeMillis();a.windup=windup;a.active=active;a.radius=radius;a.coefficient=coefficient;a.magic=magic;a.element=element;
        a.origin(e.position());setDestination(a,e.position());return a;
    }
    private static void setDestination(GblAttack a,Vec3 to){a.ex=to.x;a.ey=to.y;a.ez=to.z;}
    private void sector(DungeonEnemy e,ServerPlayer p,long windup,long active,double radius,double angle,double coefficient){var a=attack(e,"CLAW","FAN",windup,active,radius,coefficient,false,-1);a.halfAngle=angle;setDestination(a,p.position());add(a);}
    private void projectile(DungeonEnemy e,ServerPlayer p,String kind,long windup,long active,double range,double coefficient,boolean magic,int element,double angle){
        Vec3 from=e.position().add(0,e.getBbHeight()*.65,0),dir=p.position().add(0,.8,0).subtract(from).normalize().yRot((float)angle);
        var a=attack(e,kind,"LINE",windup,active,.3,coefficient,magic,element);a.origin(from);setDestination(a,from.add(dir.scale(range)));a.width=kind.equals("CANNONBALL")?.55:.25;add(a);
    }
    private void impact(DungeonEnemy e,String kind,long windup,long active,Vec3 point,double radius,double coefficient,boolean magic){var a=attack(e,kind,"IMPACT",windup,active,radius,coefficient,magic,-1);a.origin(point);setDestination(a,point);add(a);}
    private void add(GblAttack a){attacks.put(a.id,new Pending(a));owner.broadcast(a.instance,"gblAttack",packet(a,false));}
    private boolean eligible(ServerPlayer p){return p.isAlive()&&!p.isSpectator()&&!p.getAbilities().invulnerable&&!players.session(p).life.dead;}
    private void move(Dungeon d,DungeonEnemy enemy,Brain state,Vec3 target,double speed,long now){
        if(now<state.pathAt)return;
        Vec3 next=GblChurch.route(d.cell,enemy.position(),target);
        enemy.getNavigation().moveTo(next.x,next.y,next.z,speed);
        state.pathAt=now+300;
    }
    private Vec3 ground(Vec3 point,Vec3 fallback){return GblChurch.ground(owner.level(),point,fallback);}
    private void leap(DungeonEnemy e,ServerPlayer p,long delay,long duration,double height){Vec3 delta=p.position().subtract(e.position()).multiply(1,0,1);if(delta.length()>5)delta=delta.normalize().scale(5);leap(e,e.position().add(delta),delay,duration,height);}
    private void leap(DungeonEnemy e,Vec3 destination,long delay,long duration,double height){long start=System.currentTimeMillis()+delay;Vec3 to=ground(destination,e.position());leaps.put(e.getUUID(),new Leap(e.getUUID(),e.position(),to,start,start+duration,height));}
    private Vec3 landing(DungeonEnemy e){Leap leap=leaps.get(e.getUUID());return leap==null?e.position():leap.to;}

    public void tick(List<ServerPlayer> online,long now)throws Exception{
        for(var leap:new ArrayList<>(leaps.values())){
            Entity raw=owner.level().getEntity(leap.source);if(!(raw instanceof DungeonEnemy e)||!e.isAlive()){leaps.remove(leap.source);continue;}
            if(now<leap.start)continue;
            double t=Math.clamp((now-leap.start)/(double)(leap.end-leap.start),0,1);Vec3 desired=leap.from.lerp(leap.to,t).add(0,leap.height*Math.sin(Math.PI*t),0);
            e.setNoGravity(true);e.setDeltaMovement(Vec3.ZERO);e.move(MoverType.SELF,desired.subtract(e.position()));
            if(e.position().distanceToSqr(desired)>.8){cancel(e.getUUID());continue;}
            if(now>=leap.end){e.setNoGravity(false);leaps.remove(leap.source);}
        }
        for(Pending pending:new ArrayList<>(attacks.values())){
            GblAttack a=pending.attack;Entity raw=owner.level().getEntity(a.source);Dungeon d=owner.byId(a.instance);
            if(!(raw instanceof DungeonEnemy e)||!e.isAlive()||d==null||!d.status.equals("RUNNING")||CombatContentTypes.controlled(e)){remove(a);continue;}
            if(now<a.release())continue;
            Vec3 from=a.position(Math.max(a.release(),pending.previous)),to=a.position(now);
            boolean blocked=false;
            if(a.shape.equals("LINE")||a.shape.equals("LOB")){
                BlockHitResult hit=owner.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,e));
                blocked=hit.getType()!=HitResult.Type.MISS;
                if(blocked){to=hit.getLocation();if(a.shape.equals("LOB")){setDestination(a,to);a.active=Math.max(1,now-a.release());}}
            }
            pending.previous=now;
            if(blocked||now>=a.end())remove(a,false);
        }
        refreshAuras(online,now);
        for(var entry:new ArrayList<>(brains.entrySet())){Entity raw=owner.level().getEntity(entry.getKey());if(!(raw instanceof DungeonEnemy e)||!e.isAlive()){brains.remove(entry.getKey());continue;}if(now>=entry.getValue().busy&&e.isAggressive())e.setAggressive(false);}
    }
    private JsonObject packet(GblAttack a,boolean cancelled){var frame=JSON.toJsonTree(a).getAsJsonObject();frame.addProperty("cancelled",cancelled);frame.addProperty("contactUntil",System.currentTimeMillis());var raw=owner.level().getEntity(a.source);if(raw instanceof LivingEntity source)frame.add("boss",JSON.toJsonTree(targets.target(source)));return frame;}
    private void remove(GblAttack a){remove(a,true);}
    private void remove(GblAttack a,boolean cancelled){if(attacks.remove(a.id)!=null){a.remove=true;owner.broadcast(a.instance,"gblAttack",packet(a,cancelled));}}
    private void refreshAuras(List<ServerPlayer> online,long now)throws Exception{
        auras.values().removeIf(a->{var e=owner.level().getEntity(a.source);return now>=a.until||!(e instanceof DungeonEnemy mob)||!mob.isAlive()||CombatContentTypes.controlled(mob)||owner.byId(a.instance)==null||!owner.byId(a.instance).status.equals("RUNNING");});
        Set<UUID> affected=new HashSet<>(protectedEnemies);
        for(var a:auras.values())if(now>=a.from)for(Entity e:owner.level().getAllEntities())if(e instanceof DungeonEnemy mob&&mob.instance().equals(a.instance)&&mob.isAlive()&&GblEnemyKind.of(mob)!=null)affected.add(mob.getUUID());
        for(UUID id:affected){Entity raw=owner.level().getEntity(id);if(!(raw instanceof DungeonEnemy mob)){protectedEnemies.remove(id);continue;}
            boolean protectedNow=auras.values().stream().anyMatch(a->{Entity source=owner.level().getEntity(a.source);return now>=a.from&&a.instance.equals(mob.instance())&&source!=null&&source.distanceToSqr(mob)<=36;});
            Target t=targets.target(mob);double value=protectedNow?.5:0;
            if(t.encounterDefenseIncrease!=value){t.encounterDefenseIncrease=value;journal.put("target/"+id,t);mob.setData(CombatContentTypes.TARGET,JSON.toJson(t));for(var p:online)if(owner.participant(p,mob.instance()))targets.context(p,mob);}
            mob.guarded(protectedNow);if(protectedNow)protectedEnemies.add(id);else protectedEnemies.remove(id);
        }
    }
    public void track(ServerPlayer p){for(var pending:attacks.values())if(owner.participant(p,pending.attack.instance))Network.send(p,"gblAttack",packet(pending.attack,false));}
    public void cancel(UUID id){
        for(var pending:new ArrayList<>(attacks.values()))if(pending.attack.source.equals(id))remove(pending.attack);
        leaps.remove(id);auras.remove(id);Entity raw=owner.level().getEntity(id);
        if(raw instanceof DungeonEnemy e&&GblEnemyKind.of(e)!=null){e.setNoGravity(false);e.setAggressive(false);e.play("",0);}
        Brain b=brains.get(id);if(b!=null){b.busy=0;b.next=Math.max(b.next,System.currentTimeMillis()+300);}
    }
    public void close(Dungeon d){
        for(UUID id:new ArrayList<>(brains.keySet())){Entity e=owner.level().getEntity(id);if(e instanceof DungeonEnemy mob&&mob.instance().equals(d.id.toString())){cancel(id);brains.remove(id);protectedEnemies.remove(id);}}
        bosses.remove(d.id);
    }
}
