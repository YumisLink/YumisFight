package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.modules.combat.api.CombatContentTypes;
import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.dungeons.content.BehemothForbidden;
import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.network.Network;
import com.google.gson.Gson;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;
import static cn.yumis.fight.modules.dungeons.api.ForbiddenEnemyKind.*;
import static cn.yumis.fight.modules.dungeons.api.ForbiddenMoves.*;

/** Forbidden temple content. Health, signed contacts and rewards retain the established owners. */
public final class ForbiddenEncounterService {
    private static final Gson JSON=new Gson();
    private static final String REINFORCEMENT="forbidden_reinforcement",SPENT="forbidden_spent";
    private final DungeonService owner;
    private final Journal journal;
    private final DungeonPorts.Players players;
    private final DungeonPorts.Targets targets;
    private static final class Brain {
        final Vec3 home;final int area;
        final EnumMap<ForbiddenMoves,Long> ready=new EnumMap<>(ForbiddenMoves.class);
        long busy;boolean opened;
        Brain(DungeonEnemy e,Dungeon d){home=e.position();area=BehemothForbidden.area(d.wave);}
        boolean ready(ForbiddenMoves move,long now){return now>=ready.getOrDefault(move,0L);}
    }
    private static final class Pending {
        final GblAttack attack;final Set<UUID> struck=new HashSet<>();long previous;
        Pending(GblAttack attack){this.attack=attack;previous=attack.release();}
    }
    private record Ritual(UUID source,String instance,int area,ForbiddenMoves move,long release){}
    private record Barrier(UUID source,UUID core,String instance,long until){}
    private record Retired(String instance,long expires){}
    private final Map<UUID,Brain> brains=new HashMap<>();
    private final Map<String,Pending> attacks=new LinkedHashMap<>();
    private final Map<UUID,Ritual> rituals=new HashMap<>();
    private final Map<UUID,Barrier> barriers=new HashMap<>();
    private final Map<UUID,Retired> retired=new HashMap<>();
    private final Map<UUID,Integer> announced=new HashMap<>();
    private final Map<UUID,String> tracked=new HashMap<>();

    ForbiddenEncounterService(DungeonService owner,Journal journal,DungeonPorts.Players players,DungeonPorts.Targets targets){this.owner=owner;this.journal=journal;this.players=players;this.targets=targets;}
    void spawn(Dungeon d){
        ForbiddenEnemyKind[][] waves={
            {CULTIST,CULTIST,CULTIST,CULTIST,FEMALE_CULTIST,FEMALE_CULTIST},
            {CULTIST,CULTIST,CULTIST,FEMALE_CULTIST,FEMALE_CULTIST,DEACON,DEACON,DEACON},
            {CULTIST,CULTIST,CULTIST,FEMALE_CULTIST,FEMALE_CULTIST,FEMALE_CULTIST,DEACON,DEACON},
            {CULTIST,CULTIST,FEMALE_CULTIST,FEMALE_CULTIST,DEACON,DEACON,PRIEST,PRIEST},
            {CULTIST,CULTIST,FEMALE_CULTIST,FEMALE_CULTIST,DEACON,DEACON,DEACON,BISHOP},
            {CULTIST,CULTIST,FEMALE_CULTIST,FEMALE_CULTIST,DEACON,DEACON,PRIEST,BISHOP}
        };
        if(d.wave==6){owner.spawn(d,MARCELLUS.id,false,true,d.cell*512,80,140);return;}
        int area=BehemothForbidden.area(d.wave);
        for(int i=0;i<waves[d.wave].length;i++){
            var kind=waves[d.wave][i];
            owner.spawn(d,kind.id,kind==BISHOP,false,d.cell*512+(i%4-1.5)*4,80,area*40+11+i/4*7);
        }
    }
    public boolean waitsAtGate(Dungeon d,List<ServerPlayer> here){
        if(d.wave%2!=1)return false;
        int area=(d.wave+1)/2;
        if(announced.getOrDefault(d.id,0)<area){announced.put(d.id,area);for(var p:here)players.message(p,"区域清场，前往"+BehemothForbidden.AREAS[area]+"。");}
        return here.stream().noneMatch(p->BehemothForbidden.contains(d.cell,area,p.position()));
    }
    public void brain(Dungeon d,DungeonEnemy e,List<ServerPlayer> here,long now)throws Exception{
        var kind=ForbiddenEnemyKind.of(e);
        if(kind==null||kind==BARRIER_CORE||e.getPersistentData().getBooleanOr(SPENT,false))return;
        Brain b=brains.computeIfAbsent(e.getUUID(),id->new Brain(e,d));
        if(CombatContentTypes.controlled(e)){cancel(e.getUUID());return;}
        if(!BehemothForbidden.contains(d.cell,b.area,e.position())){cancel(e.getUUID());e.getNavigation().moveTo(b.home.x,b.home.y,b.home.z,1);return;}
        ServerPlayer p=here.stream().filter(q->eligible(q,d.id.toString())&&BehemothForbidden.contains(d.cell,b.area,q.position())).min(Comparator.comparingDouble(e::distanceToSqr)).orElse(null);
        if(p==null){e.getNavigation().stop();return;}
        if(kind==CULTIST&&here.stream().anyMatch(q->eligible(q,d.id.toString())&&e.getBoundingBox().inflate(.05).intersects(q.getBoundingBox()))){explode(d,e,here,now);return;}
        if(now<b.busy){e.getNavigation().stop();return;}
        Vec3 direction=p.position().subtract(e.position()).multiply(1,0,1).normalize();if(direction.lengthSqr()<.001)direction=new Vec3(0,0,1);
        float yaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z));e.setYRot(yaw);e.yBodyRot=yaw;e.getLookControl().setLookAt(p);
        double distance=e.distanceTo(p);boolean ranged=kind==FEMALE_CULTIST||kind==BISHOP;
        if(ranged&&distance<4){Vec3 to=BehemothForbidden.inside(d.cell,b.area,e.position().subtract(direction.scale(3)));e.getNavigation().moveTo(to.x,to.y,to.z,1);}
        else if(distance>(ranged?8:kind==MARCELLUS?5:kind==PRIEST?3:1.9))e.getNavigation().moveTo(p,1);
        else e.getNavigation().stop();
        if(distance>18)return;
        switch(kind){
            case CULTIST -> { /* Contact, not an attack timer, consumes this enemy. */ }
            case FEMALE_CULTIST -> {if(distance<=KNIFE.range&&b.ready(KNIFE,now)){play(e,b,KNIFE,now);projectiles(e,p,KNIFE,1,now);}}
            case DEACON -> {if(distance<=STAB.range&&b.ready(STAB,now)){play(e,b,STAB,now);fan(e,p,STAB,now);}}
            case PRIEST -> {
                if(canSummon(e,b,PRIEST_SUMMON,now)){ritual(d,e,b,PRIEST_SUMMON,now);}
                else if(distance<=PRIEST_WAVE.range&&b.ready(PRIEST_WAVE,now)){play(e,b,PRIEST_WAVE,now);wave(e,PRIEST_WAVE,now);}
            }
            case BISHOP -> {
                if(canSummon(e,b,BISHOP_SUMMON,now)){ritual(d,e,b,BISHOP_SUMMON,now);}
                else if(distance<=DARK_KNIVES.range&&b.ready(DARK_KNIVES,now)){play(e,b,DARK_KNIVES,now);projectiles(e,p,DARK_KNIVES,3,now);}
            }
            case MARCELLUS -> {
                if(!b.opened){if(distance>BOSS_KNIVES.range)return;play(e,b,BOSS_KNIVES,now);projectiles(e,p,BOSS_KNIVES,3,now);b.opened=true;}
                else if(b.ready(BARRIER,now)&&!barriers.containsKey(e.getUUID()))ritual(d,e,b,BARRIER,now);
                else if(canSummon(e,b,BOSS_SUMMON,now))ritual(d,e,b,BOSS_SUMMON,now);
                else if(distance<=BOSS_WAVE.range&&b.ready(BOSS_WAVE,now)){play(e,b,BOSS_WAVE,now);wave(e,BOSS_WAVE,now);}
                else if(distance<=BOSS_STAB.range&&b.ready(BOSS_STAB,now)){play(e,b,BOSS_STAB,now);fan(e,p,BOSS_STAB,now);}
                else if(distance<=BOSS_KNIVES.range&&b.ready(BOSS_KNIVES,now)){play(e,b,BOSS_KNIVES,now);projectiles(e,p,BOSS_KNIVES,3,now);}
            }
            case BARRIER_CORE -> { }
        }
    }
    private boolean eligible(ServerPlayer p,String instance){return p.level()==owner.level()&&owner.participant(p,instance)&&p.isAlive()&&!players.session(p).life.dead&&!p.isSpectator()&&!p.getAbilities().invulnerable;}
    private void play(DungeonEnemy e,Brain b,ForbiddenMoves move,long now){
        e.getNavigation().stop();e.setAggressive(true);e.play(move.animation,move.duration());b.busy=now+move.duration();b.ready.put(move,now+move.cooldown);
    }
    private GblAttack attack(DungeonEnemy e,ForbiddenMoves move,String shape,long now){
        GblAttack a=new GblAttack();a.source=e.getUUID();a.instance=e.instance();a.kind=move.name();a.shape=shape;a.started=now;a.windup=move.windup;a.active=move.active;a.radius=move.range;a.coefficient=move.coefficient;a.magic=move.magic;a.element=move.element;a.origin(e.position());destination(a,e.position());return a;
    }
    private static void destination(GblAttack a,Vec3 p){a.ex=p.x;a.ey=p.y;a.ez=p.z;}
    private void add(GblAttack a){attacks.put(a.id,new Pending(a));owner.broadcast(a.instance,"forbiddenAttack",a);}
    private void fan(DungeonEnemy e,ServerPlayer p,ForbiddenMoves move,long now){var a=attack(e,move,"FAN",now);a.halfAngle=Math.PI/3;destination(a,p.position());add(a);}
    private void wave(DungeonEnemy e,ForbiddenMoves move,long now){var a=attack(e,move,"WAVE",now);a.width=1.15;add(a);}
    private void projectiles(DungeonEnemy e,ServerPlayer p,ForbiddenMoves move,int count,long now){
        Vec3 origin=e.position().add(0,e.getBbHeight()*.65,0),direction=p.position().add(0,.8,0).subtract(origin).normalize();
        for(int i=0;i<count;i++){var a=attack(e,move,"LINE",now);a.origin(origin);a.width=.25;destination(a,origin.add(direction.yRot((float)((i-(count-1)*.5)*.09)).scale(move.range)));add(a);}
    }
    private long summoned(String instance){
        long count=0;for(Entity raw:owner.level().getAllEntities())if(raw instanceof DungeonEnemy e&&e.instance().equals(instance)&&e.isAlive()&&e.getPersistentData().getBooleanOr(REINFORCEMENT,false)&&!e.getPersistentData().getBooleanOr(SPENT,false)&&!targets.target(e).life.dead)count++;return count;
    }
    private boolean canSummon(DungeonEnemy e,Brain b,ForbiddenMoves move,long now){return !e.getPersistentData().getBooleanOr(REINFORCEMENT,false)&&b.ready(move,now)&&summoned(e.instance())<SUMMON_LIMIT;}
    private void ritual(Dungeon d,DungeonEnemy e,Brain b,ForbiddenMoves move,long now){
        play(e,b,move,now);rituals.put(e.getUUID(),new Ritual(e.getUUID(),e.instance(),b.area,move,now+move.windup));
        var visual=attack(e,move,move==BARRIER?"BARRIER":"SUMMON",now);visual.radius=move==BARRIER?1.6:2.5;visual.active=move.recovery;add(visual);
    }
    private void saveTarget(DungeonEnemy e,Target target)throws Exception{
        journal.begin();try{journal.put("target/"+e.getUUID(),target);journal.put("life/"+e.getUUID(),target.life);journal.finish();}catch(Exception failure){journal.abort();throw failure;}
        e.setData(CombatContentTypes.TARGET,JSON.toJson(target));
        for(var p:owner.level().players())if(owner.participant(p,e.instance()))targets.context(p,e);
    }
    private void immune(DungeonEnemy boss,boolean value)throws Exception{
        Target t=targets.target(boss);if(t.damageImmune==value)return;t.damageImmune=value;t.life.revision++;saveTarget(boss,t);
    }
    private DungeonEnemy auxiliary(Dungeon d,DungeonEnemy source,ForbiddenEnemyKind kind,Vec3 position,boolean reinforcement)throws Exception{
        DungeonEnemy e=owner.spawn(d,kind.id,false,false,position.x,position.y,position.z);
        e.getPersistentData().putString("ydnf_parent",source.getStringUUID());e.getPersistentData().putBoolean(REINFORCEMENT,reinforcement);
        Target t=targets.target(e);t.noRewards=true;
        if(kind==BARRIER_CORE){t.speciesHealth=CORE_HEALTH;e.noPhysics=true;e.setNoGravity(true);e.getNavigation().stop();e.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);}
        t.life.revision++;saveTarget(e,t);return e;
    }
    private void release(Ritual r,DungeonEnemy source,Dungeon d,long now)throws Exception{
        if(r.move==BARRIER){
            DungeonEnemy core=auxiliary(d,source,BARRIER_CORE,source.position(),false);
            barriers.put(source.getUUID(),new Barrier(source.getUUID(),core.getUUID(),source.instance(),now+BARRIER_LIFETIME));
            immune(source,true);
        }else{
            ForbiddenEnemyKind[] kinds=r.move==PRIEST_SUMMON?new ForbiddenEnemyKind[]{CULTIST,CULTIST}:r.move==BISHOP_SUMMON?new ForbiddenEnemyKind[]{CULTIST,CULTIST,FEMALE_CULTIST}:new ForbiddenEnemyKind[]{CULTIST,CULTIST,FEMALE_CULTIST,FEMALE_CULTIST};
            int slots=(int)Math.max(0,SUMMON_LIMIT-summoned(source.instance()));
            for(int i=0;i<Math.min(slots,kinds.length);i++){
                double angle=2*Math.PI*i/kinds.length;Vec3 to=BehemothForbidden.inside(d.cell,r.area,source.position().add(Math.cos(angle)*3,0,Math.sin(angle)*3));
                if(!owner.level().noCollision(source,source.getBoundingBox().move(to.subtract(source.position()))))continue;
                auxiliary(d,source,kinds[i],to,true);
            }
        }
        journal.put("instance/"+d.id,d);
    }
    private void explode(Dungeon d,DungeonEnemy e,List<ServerPlayer> here,long now)throws Exception{
        if(e.getPersistentData().getBooleanOr(SPENT,false))return;
        GblAttack a=attack(e,EXPLOSION,"EXPLOSION",now);
        // Retire before issuing any contact: a second player/tick cannot consume this source again.
        Target t=targets.target(e);t.noRewards=true;t.life.dead=true;t.life.q=0;t.life.revision++;
        saveTarget(e,t);e.getPersistentData().putBoolean(SPENT,true);e.play("spent",3000);e.scripted();e.getNavigation().stop();e.setDeltaMovement(Vec3.ZERO);e.noPhysics=true;e.setNoGravity(true);e.setInvisible(true);e.setCustomNameVisible(false);
        d.enemies.remove(e.getUUID());journal.put("instance/"+d.id,d);
        retired.put(e.getUUID(),new Retired(e.instance(),now+3000));
        owner.broadcast(a.instance,"forbiddenAttack",a);
        for(var p:here)if(eligible(p,a.instance)&&Math.abs(p.getY()-a.y)<2.6&&p.position().subtract(a.origin()).horizontalDistanceSqr()<=Math.pow(a.radius+.3,2)&&visible(e,a,p))owner.contacts.issue(a.id,e,p,a.kind,a.coefficient,a.magic,a.element,now);
        brains.remove(e.getUUID());
    }
    public void tick(List<ServerPlayer> online,long now)throws Exception{
        // Covers Hell+ enrollment/reconnect as well as the ordinary join hook, without another lifecycle branch.
        Set<UUID> present=new HashSet<>();
        for(var p:online)if(p.level()==owner.level()){
            var session=players.session(p);Dungeon d=owner.byId(session.life.instance);
            if(d!=null&&d.dungeon.equals("behemoth_forbidden")&&d.status.equals("RUNNING")&&owner.participant(p,d.id.toString())){
                present.add(p.getUUID());String key=d.id+"/"+session.id+"/"+session.life.generation;
                if(!key.equals(tracked.get(p.getUUID())))track(p);
            }
        }
        tracked.keySet().retainAll(present);
        // Keep spent entities only as receipt sources, never as live encounter enemies.
        for(var entry:new ArrayList<>(retired.entrySet()))if(now>=entry.getValue().expires||!owner.contacts.pending(entry.getKey())){Entity e=owner.level().getEntity(entry.getKey());if(e!=null)e.discard();retired.remove(entry.getKey());}
        for(var r:new ArrayList<>(rituals.values())){
            Entity raw=owner.level().getEntity(r.source);Dungeon d=owner.byId(r.instance);
            if(!(raw instanceof DungeonEnemy e)||!alive(e)||d==null||!d.status.equals("RUNNING")||CombatContentTypes.controlled(e)){cancel(r.source);continue;}
            if(now>=r.release){release(r,e,d,now);rituals.remove(r.source,r);}
        }
        for(var barrier:new ArrayList<>(barriers.values())){
            Entity raw=owner.level().getEntity(barrier.source),coreRaw=owner.level().getEntity(barrier.core);Dungeon d=owner.byId(barrier.instance);
            if(!(raw instanceof DungeonEnemy boss)||!alive(boss)||!(coreRaw instanceof DungeonEnemy core)||!alive(core)||now>=barrier.until||d==null||!d.status.equals("RUNNING")){clearBarrier(barrier);continue;}
            core.noPhysics=true;core.setNoGravity(true);core.setDeltaMovement(Vec3.ZERO);core.setPos(boss.getX(),boss.getY(),boss.getZ());core.setYRot(boss.getYRot());core.yBodyRot=boss.yBodyRot;
        }
        for(var pending:new ArrayList<>(attacks.values())){
            GblAttack a=pending.attack;Entity raw=owner.level().getEntity(a.source);Dungeon d=owner.byId(a.instance);
            if(!(raw instanceof DungeonEnemy e)||!alive(e)||d==null||!d.status.equals("RUNNING")||CombatContentTypes.controlled(e)){remove(a);continue;}
            if(now<a.release())continue;
            Vec3 from=a.position(Math.max(a.release(),pending.previous)),to=a.position(now);boolean blocked=false;
            if(a.shape.equals("LINE")){var hit=owner.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,e));blocked=hit.getType()!=HitResult.Type.MISS;if(blocked)to=hit.getLocation();}
            if(a.coefficient>0)for(var p:online)if(eligible(p,a.instance)&&!pending.struck.contains(p.getUUID())&&contains(a,p,from,to,pending.previous,now)&&visible(e,a,p)){
                pending.struck.add(p.getUUID());owner.contacts.issue(a.id,e,p,a.kind,a.coefficient,a.magic,a.element,now);
            }
            pending.previous=now;if(blocked||now>=a.end())remove(a);
        }
        for(var entry:new ArrayList<>(brains.entrySet())){
            Entity raw=owner.level().getEntity(entry.getKey());if(!(raw instanceof DungeonEnemy e)||!alive(e)){brains.remove(entry.getKey());continue;}
            if(now>=entry.getValue().busy)e.setAggressive(false);
        }
    }
    private boolean alive(DungeonEnemy e){return e.isAlive()&&!targets.target(e).life.dead;}
    private boolean contains(GblAttack a,ServerPlayer p,Vec3 from,Vec3 to,long previous,long now){
        if(a.shape.equals("LINE"))return p.getBoundingBox().inflate(a.width+.15).contains(from)||p.getBoundingBox().inflate(a.width+.15).clip(from,to).isPresent();
        Vec3 offset=p.position().subtract(a.origin());if(Math.abs(offset.y)>(a.shape.equals("WAVE")?1.05:2.6))return false;
        double distance=offset.horizontalDistance();
        if(a.shape.equals("WAVE"))return distance>=Math.max(0,a.waveRadius(previous)-a.width*.5-.3)&&distance<=a.waveRadius(now)+a.width*.5+.3;
        if(distance>a.radius+.3)return false;
        Vec3 direction=a.destination().subtract(a.origin()).multiply(1,0,1).normalize();
        return !a.shape.equals("FAN")||distance<.4||direction.lengthSqr()<.001||offset.multiply(1,0,1).normalize().dot(direction)>=Math.cos(a.halfAngle);
    }
    private boolean visible(DungeonEnemy e,GblAttack a,ServerPlayer p){return a.shape.equals("LINE")||owner.level().clip(new ClipContext(a.origin().add(0,.5,0),p.position().add(0,.65,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,e)).getType()==HitResult.Type.MISS;}
    private void remove(GblAttack a){if(attacks.remove(a.id)!=null){a.remove=true;owner.broadcast(a.instance,"forbiddenAttack",a);}}
    private void clearBarrier(Barrier b)throws Exception{
        Entity raw=owner.level().getEntity(b.source);if(raw instanceof DungeonEnemy boss)immune(boss,false);
        Entity core=owner.level().getEntity(b.core);
        if(core instanceof DungeonEnemy e){Target t=targets.target(e);if(!t.life.dead){t.life.dead=true;t.life.q=0;t.life.revision++;saveTarget(e,t);}e.discard();}
        barriers.remove(b.source,b);
    }
    public void died(UUID id)throws Exception{
        for(var b:new ArrayList<>(barriers.values()))if(b.source.equals(id)||b.core.equals(id))clearBarrier(b);
        cancel(id);brains.remove(id);
    }
    boolean retainsSource(UUID id){return retired.containsKey(id);}
    public void cancel(UUID id){
        rituals.remove(id);for(var pending:new ArrayList<>(attacks.values()))if(pending.attack.source.equals(id))remove(pending.attack);
        Entity raw=owner.level().getEntity(id);if(raw instanceof DungeonEnemy e&&ForbiddenEnemyKind.of(e)!=null&&!e.getPersistentData().getBooleanOr(SPENT,false)){e.getNavigation().stop();e.setAggressive(false);e.play("",0);}
        Brain b=brains.get(id);if(b!=null)b.busy=0;
    }
    public void track(ServerPlayer p){
        var session=players.session(p);Dungeon d=owner.byId(session.life.instance);
        if(d==null||!d.dungeon.equals("behemoth_forbidden")||!d.status.equals("RUNNING")||!owner.participant(p,d.id.toString()))return;
        for(var pending:attacks.values())if(pending.attack.instance.equals(d.id.toString()))Network.send(p,"forbiddenAttack",pending.attack);
        tracked.put(p.getUUID(),d.id+"/"+session.id+"/"+session.life.generation);
    }
    public void close(Dungeon d)throws Exception{
        String instance=d.id.toString();
        for(var b:new ArrayList<>(barriers.values()))if(b.instance.equals(instance))clearBarrier(b);
        for(var r:new ArrayList<>(rituals.values()))if(r.instance.equals(instance))cancel(r.source);
        for(var p:new ArrayList<>(attacks.values()))if(p.attack.instance.equals(instance))remove(p.attack);
        for(var entry:new ArrayList<>(brains.entrySet())){Entity e=owner.level().getEntity(entry.getKey());if(e instanceof DungeonEnemy mob&&mob.instance().equals(instance)){cancel(entry.getKey());brains.remove(entry.getKey());}}
        for(var entry:new ArrayList<>(retired.entrySet()))if(entry.getValue().instance.equals(instance)){Entity e=owner.level().getEntity(entry.getKey());if(e!=null)e.discard();retired.remove(entry.getKey());}
        announced.remove(d.id);
        tracked.values().removeIf(key->key.startsWith(instance+"/"));
    }
}
