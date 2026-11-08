package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.modules.combat.api.CombatContentTypes;
import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.dungeons.content.CastlePalace;
import cn.yumis.fight.network.Network;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;
import com.google.gson.*;

/** Palace content AI, real motion and frozen telegraphs; the recipient decides its own contacts. */
public final class PalaceEncounterService {
    private static final Gson JSON=new Gson();
    private final DungeonPorts.Targets targets;
    private static final class Brain {
        final Vec3 home;final int room;
        long busy,next,heavy,storm;int step;
        Brain(DungeonEnemy e,Dungeon d){home=e.position();room=d.wave;busy=e.moveAt()+e.moveDuration();next=busy+Math.floorMod(e.getUUID().hashCode(),450);}
    }
    private static final class Pending {
        final PalaceAttack attack;long previous;
        Pending(PalaceAttack attack){this.attack=attack;previous=attack.release();}
    }
    private record Motion(UUID source,Vec3 from,Vec3 to,long start,long end,double arc,String kind){}
    private final DungeonService owner;
    private final DungeonPorts.Players players;
    private final Map<UUID,Brain> brains=new HashMap<>();
    private final Map<String,Pending> attacks=new LinkedHashMap<>();
    private final Map<UUID,Motion> motions=new HashMap<>();
    PalaceEncounterService(DungeonService owner,DungeonPorts.Players players,DungeonPorts.Targets targets){this.owner=owner;this.players=players;this.targets=targets;}

    void spawn(Dungeon d){
        String e=PalaceEnemyKind.EXPELLER.id,a=PalaceEnemyKind.AXE_EXPELLER.id,c=PalaceEnemyKind.COMMANDER.id,g=PalaceEnemyKind.GOLEM.id;
        String[][] rooms={{e,e,e,e,e,e,a,a},{e,e,e,e,a,a,a,a},{c,c,c,c,g,g,g,g},{e,e,c,c,g,g,g,g},{e,e,a,a,c,c,g,g},{e,e,a,a,g,g,PalaceEnemyKind.AXION.id,PalaceEnemyKind.IAE.id}};
        if(d.wave==6){owner.spawn(d,PalaceEnemyKind.SIEGHART.id,false,true,d.cell*512,80,234);return;}
        for(int i=0;i<8;i++)owner.spawn(d,rooms[d.wave][i],d.wave==5&&i>=6,false,d.cell*512+(i%4-1.5)*4,80,d.wave*CastlePalace.SPACING+11+(i/4)*7);
    }
    public void brain(Dungeon d,DungeonEnemy e,List<ServerPlayer> here,long now){
        PalaceEnemyKind kind=PalaceEnemyKind.of(e);if(kind==null)return;
        Brain b=brains.computeIfAbsent(e.getUUID(),id->new Brain(e,d));
        if(CombatContentTypes.controlled(e)){cancel(e.getUUID());return;}
        if(now<b.busy){e.getNavigation().stop();return;}
        if(!CastlePalace.contains(d.cell,b.room,e.position())){cancel(e.getUUID());move(e,kind,b.home,now);return;}
        ServerPlayer p=here.stream().filter(q->q.level()==e.level()&&q.isAlive()&&!players.session(q).life.dead&&!q.isSpectator()&&!q.getAbilities().invulnerable&&CastlePalace.contains(d.cell,b.room,q.position())).min(Comparator.comparingDouble(e::distanceToSqr)).orElse(null);
        if(p==null){if(e.position().subtract(b.home).horizontalDistanceSqr()>4)move(e,kind,b.home,now);else e.getNavigation().stop();return;}
        Vec3 direction=p.position().subtract(e.position()).multiply(1,0,1).normalize();if(direction.lengthSqr()<.001)direction=new Vec3(0,0,1);
        float yaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z));e.setYRot(yaw);e.yBodyRot=yaw;e.getLookControl().setLookAt(p);
        double distance=e.distanceTo(p);boolean ranged=kind==PalaceEnemyKind.COMMANDER||kind==PalaceEnemyKind.IAE;
        if(ranged&&distance<4)move(e,kind,CastlePalace.inside(d.cell,b.room,e.position().subtract(direction.scale(3))),now);
        else if(distance>(ranged?8:2.6))move(e,kind,CastlePalace.inside(d.cell,b.room,p.position()),now);
        else e.getNavigation().stop();
        if(now<b.next||distance>22)return;
        switch(kind){
            case EXPELLER, AXION -> {
                if(distance>3&&distance<9&&now>=b.heavy){play(e,b,"thrust",1700,now);Vec3 to=clipMove(e,CastlePalace.inside(d.cell,b.room,e.position().add(direction.scale(Math.min(6,distance)))));motion(e,to,650,450,0,"DASH",now);var hit=attack(e,"THRUST","DASH",650,450,1,1.5,false,-1,now);hit.destination(to);hit.width=1.5;add(hit);b.heavy=now+6000;}
                else if(distance<3.4){play(e,b,"slash",1200,now);fan(e,p,"SWORD",450,180,3.3,Math.PI/3,1,false,-1,now);}
            }
            case AXE_EXPELLER -> {
                if(distance<5&&now>=b.heavy){play(e,b,"cleave",2100,now);var hit=attack(e,"AXE_CLEAVE","RECT",950,200,5,1.5,false,-1,now);hit.destination(e.position().add(direction.scale(5)));hit.width=2.5;add(hit);b.heavy=now+6000;}
                else if(distance<3.8){play(e,b,"sweep",1500,now);fan(e,p,"AXE_SWEEP",650,200,3.7,Math.PI/2,1,false,-1,now);}
            }
            case COMMANDER -> {
                if(distance<3){play(e,b,"clamp",1250,now);fan(e,p,"CLAMP",450,180,3,Math.PI/2,1,false,-1,now);}
                else if(distance<13){play(e,b,"pillar",1900,now);var hit=attack(e,"STONE_PILLAR","CIRCLE",950,200,1.7,1.5,true,-1,now);hit.origin(CastlePalace.inside(d.cell,b.room,p.position()));hit.destination(hit.origin());add(hit);b.next=now+3500;}
            }
            case GOLEM -> {
                if(distance<8&&now>=b.heavy){play(e,b,"slam",2100,now);Vec3 delta=p.position().subtract(e.position()).multiply(1,0,1);if(delta.length()>4)delta=delta.normalize().scale(4);Vec3 to=clipMove(e,CastlePalace.inside(d.cell,b.room,e.position().add(delta))).add(0,.45,0);motion(e,to,500,700,2.3,"LEAP",now);var hit=attack(e,"GOLEM_SLAM","CIRCLE",1200,200,2.8,1.5,false,-1,now);hit.origin(to);hit.destination(to);add(hit);b.heavy=now+6000;}
                else if(distance<3.5){play(e,b,"punch",1400,now);fan(e,p,"STONE_FIST",550,180,3.4,Math.PI/3,1,false,-1,now);}
            }
            case IAE -> {
                if(b.step++%2==0){play(e,b,"darts",1800,now);String group=UUID.randomUUID().toString();Vec3 from=e.position().add(0,.9,0);
                    for(int i=0;i<4;i++){var hit=attack(e,"IAE_DART","DART",650,950,.15,1,false,-1,now);hit.group=group;hit.origin(from);hit.destination(from.add(direction.yRot((float)(i*Math.PI/2)).scale(14)));hit.width=.16;add(hit);}}
                else{play(e,b,"bomb",2300,now);var hit=attack(e,"IAE_BOMB","BOMB",750,1000,2.5,1.5,true,1,now);hit.origin(e.position().add(0,1,0));hit.destination(CastlePalace.inside(d.cell,b.room,p.position()).add(0,.2,0));add(hit);}
                b.next=b.busy+900;
            }
            case SIEGHART -> {
                if(now>=b.storm){storm(d,e,b,here,now);return;}
                int step=b.step++%3;
                if(step==0&&distance<4){play(e,b,"light_sword",1400,now);fan(e,p,"LIGHT_SWORD",500,200,4,Math.PI/3,1,false,0,now);}
                else if(step==2&&distance<6){play(e,b,"nova",2100,now);var hit=attack(e,"LIGHT_NOVA","WAVE",900,650,5.5,1.5,true,0,now);hit.width=.65;add(hit);}
                else{play(e,b,"beam",2300,now);for(int i=0;i<2;i++){var hit=attack(e,"LIGHT_BEAM","RECT",1000+i*250,130,20,.75,true,0,now);hit.width=2.6;hit.destination(e.position().add(direction.scale(20)));add(hit);}}
                b.next=b.busy+600;
            }
        }
    }
    private void storm(Dungeon d,DungeonEnemy e,Brain b,List<ServerPlayer> here,long now){
        play(e,b,"rise",4000,now);e.play("rise",500);b.storm=now+15000;b.next=now+4700;
        motion(e,e.position(),0,4000,6,"FLIGHT",now);
        List<ServerPlayer> candidates=here.stream().filter(p->p.isAlive()&&!players.session(p).life.dead&&CastlePalace.contains(d.cell,6,p.position())).toList();
        for(int i=0;i<6;i++){
            Vec3 anchor=candidates.isEmpty()?b.home:candidates.get(i%candidates.size()).position();double angle=i*Math.PI/3;
            Vec3 point=CastlePalace.inside(d.cell,6,anchor.add(Math.cos(angle)*(i%2==0?0:3),0,Math.sin(angle)*(i%2==0?0:3)));
            var hit=attack(e,"LIGHTNING","CIRCLE",600,180,1.6,.25,true,0,now+400+i*400);hit.origin(point);hit.destination(point);add(hit);
        }
    }
    private void play(DungeonEnemy e,Brain b,String move,long duration,long now){e.getNavigation().stop();e.setDeltaMovement(Vec3.ZERO);e.play(move,duration);e.setAggressive(true);b.busy=now+duration;b.next=b.busy+500;}
    private PalaceAttack attack(DungeonEnemy e,String kind,String shape,long windup,long active,double radius,double coefficient,boolean magic,int element,long now){
        PalaceAttack a=new PalaceAttack();a.source=e.getUUID();a.instance=e.instance();a.kind=kind;a.shape=shape;a.started=now;a.windup=windup;a.active=active;a.radius=radius;a.coefficient=coefficient;a.magic=magic;a.element=element;a.origin(e.position());a.destination(e.position());return a;
    }
    private void fan(DungeonEnemy e,ServerPlayer p,String kind,long windup,long active,double radius,double halfAngle,double coefficient,boolean magic,int element,long now){var hit=attack(e,kind,"FAN",windup,active,radius,coefficient,magic,element,now);hit.halfAngle=halfAngle;hit.destination(p.position());add(hit);}
    private void add(PalaceAttack attack){if(attack.group.isEmpty())attack.group=attack.id;attacks.put(attack.id,new Pending(attack));owner.broadcast(attack.instance,"palaceAttack",packet(attack));}
    private void motion(DungeonEnemy e,Vec3 to,long delay,long duration,double arc,String kind,long now){motions.put(e.getUUID(),new Motion(e.getUUID(),e.position(),to,now+delay,now+delay+duration,arc,kind));}
    private Vec3 clipMove(DungeonEnemy e,Vec3 to){Vec3 from=e.position().add(0,.8,0);var hit=owner.level().clip(new ClipContext(from,to.add(0,.8,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,e));if(hit.getType()==HitResult.Type.MISS)return to;Vec3 direction=to.subtract(e.position()).multiply(1,0,1).normalize();return hit.getLocation().subtract(0,.8,0).subtract(direction.scale(e.getBbWidth()*.6));}
    private void move(DungeonEnemy e,PalaceEnemyKind kind,Vec3 to,long now){
        if(kind!=PalaceEnemyKind.GOLEM){e.getNavigation().moveTo(to.x,to.y,to.z,1);return;}
        e.setNoGravity(true);e.setDeltaMovement(Vec3.ZERO);Vec3 delta=to.subtract(e.position()).multiply(1,0,1);if(delta.length()>.14)delta=delta.normalize().scale(.14);
        double y=80.45+Math.sin(now/650d)*.08;e.move(MoverType.SELF,delta.add(0,Math.clamp(y-e.getY(),-.12,.12),0));
    }
    public void tick(List<ServerPlayer> online,long now){
        for(Motion m:new ArrayList<>(motions.values())){
            Entity raw=owner.level().getEntity(m.source);if(!(raw instanceof DungeonEnemy e)||!e.isAlive()){motions.remove(m.source);continue;}
            if(now<m.start)continue;
            double t=Math.clamp((now-m.start)/(double)Math.max(1,m.end-m.start),0,1);
            double height=m.kind.equals("FLIGHT")?6*Math.min(1,Math.min((now-m.start)/500d,(m.end-now)/500d)):m.arc*Math.sin(Math.PI*t);
            Vec3 desired=m.from.lerp(m.to,t).add(0,Math.max(0,height),0);
            e.setNoGravity(true);e.setDeltaMovement(Vec3.ZERO);e.fallDistance=0;e.move(MoverType.SELF,desired.subtract(e.position()));
            if(e.position().distanceToSqr(desired)>.35){cancel(e.getUUID());continue;}
            if(m.kind.equals("FLIGHT")){String clip=now<m.start+500?"rise":now<m.end-500?"storm":"land";if(!e.move().equals(clip))e.play(clip,clip.equals("storm")?3000:500);}
            if(now>=m.end){motions.remove(m.source);e.setNoGravity(PalaceEnemyKind.of(e)==PalaceEnemyKind.GOLEM);}
        }
        for(Pending pending:new ArrayList<>(attacks.values())){
            PalaceAttack a=pending.attack;Entity raw=owner.level().getEntity(a.source);Dungeon d=owner.byId(a.instance);
            if(!(raw instanceof DungeonEnemy e)||!e.isAlive()||d==null||!d.status.equals("RUNNING")||CombatContentTypes.controlled(e)){remove(a,true);continue;}
            if(now<a.release())continue;
            Brain b=brains.get(e.getUUID());if(b==null){remove(a,true);continue;}
            Vec3 from=a.position(Math.min(a.end(),Math.max(a.release(),pending.previous))),to=a.position(Math.min(a.end(),now));boolean blocked=false;
            if(a.shape.equals("DART")||a.shape.equals("BOMB")){
                // Subdivide the curved flight too, so a delayed tick cannot tunnel through a column.
                long start=Math.min(a.end(),Math.max(a.release(),pending.previous)),end=Math.min(a.end(),now);
                for(long time=start;time<end;){long next=Math.min(end,time+25);Vec3 p=a.position(time),q=a.position(next);var wall=owner.level().clip(new ClipContext(p,q,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,e));if(wall.getType()!=HitResult.Type.MISS){to=wall.getLocation();blocked=true;break;}time=next;}
            }
            if(blocked&&a.shape.equals("BOMB")){a.destination(to);a.active=Math.max(1,now-a.release());owner.broadcast(a.instance,"palaceAttack",packet(a));}
            pending.previous=now;if(blocked||now>=a.end())remove(a,false);
        }
        for(var entry:new ArrayList<>(brains.entrySet())){Entity raw=owner.level().getEntity(entry.getKey());if(!(raw instanceof DungeonEnemy e)||!e.isAlive()){brains.remove(entry.getKey());continue;}if(now>=entry.getValue().busy&&e.isAggressive())e.setAggressive(false);}
    }
    private JsonObject packet(PalaceAttack a){var frame=JSON.toJsonTree(a).getAsJsonObject();var dungeon=owner.byId(a.instance);var brain=brains.get(a.source);if(dungeon!=null){frame.addProperty("cell",dungeon.cell);frame.addProperty("area",brain==null?dungeon.wave:brain.room);}frame.addProperty("contactUntil",System.currentTimeMillis());var raw=owner.level().getEntity(a.source);if(raw instanceof LivingEntity source)frame.add("boss",JSON.toJsonTree(targets.target(source)));return frame;}
    private void remove(PalaceAttack a,boolean cancelled){if(attacks.remove(a.id)==null)return;a.remove=true;a.cancelled=cancelled;owner.broadcast(a.instance,"palaceAttack",packet(a));}
    public void track(ServerPlayer p){for(Pending pending:attacks.values())if(owner.participant(p,pending.attack.instance))Network.send(p,"palaceAttack",packet(pending.attack));}
    public void cancel(UUID source){
        for(Pending pending:new ArrayList<>(attacks.values()))if(pending.attack.source.equals(source))remove(pending.attack,true);
        Entity raw=owner.level().getEntity(source);Motion old=motions.get(source);
        if(raw instanceof DungeonEnemy e&&PalaceEnemyKind.of(e)!=null){
            if(old!=null&&!old.kind.equals("SETTLE")){motions.remove(source);if(e.isAlive()&&e.getY()>80.6)motion(e,new Vec3(e.getX(),80+(PalaceEnemyKind.of(e)==PalaceEnemyKind.GOLEM?.45:0),e.getZ()),0,400,0,"SETTLE",System.currentTimeMillis());}
            e.setNoGravity(PalaceEnemyKind.of(e)==PalaceEnemyKind.GOLEM||motions.containsKey(source));e.setAggressive(false);e.setDeltaMovement(Vec3.ZERO);e.fallDistance=0;
            if(!e.move().isEmpty())e.play("",0);
        }
        Brain b=brains.get(source);if(b!=null){b.busy=0;b.next=Math.max(b.next,System.currentTimeMillis()+400);}
    }
    public void close(Dungeon d){
        for(UUID id:new ArrayList<>(brains.keySet())){Entity raw=owner.level().getEntity(id);if(raw instanceof DungeonEnemy e&&e.instance().equals(d.id.toString())){cancel(id);brains.remove(id);motions.remove(id);}}
        for(Pending pending:new ArrayList<>(attacks.values()))if(pending.attack.instance.equals(d.id.toString()))remove(pending.attack,true);
    }
}
