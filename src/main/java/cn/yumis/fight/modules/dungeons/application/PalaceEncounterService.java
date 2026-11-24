package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.combat.api.ActionPhase;
import cn.yumis.fight.modules.combat.api.CombatContentTypes;
import cn.yumis.fight.modules.combat.api.StatusLeases;
import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.dungeons.content.CastlePalace;
import cn.yumis.fight.network.Network;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;
import com.google.gson.*;

import static cn.yumis.fight.modules.dungeons.api.PalaceEnemyKind.*;

/** Palace content AI, real motion and frozen telegraphs; the recipient decides its own contacts. */
public final class PalaceEncounterService {
    private static final Gson JSON=new Gson();
    private static final String ROOM_TAG = "ydnf_palace_room";
    private static final List<List<PalaceEnemyKind>> SQUADS = List.of(
        List.of(EXPELLER, EXPELLER, AXE_EXPELLER),
        List.of(EXPELLER, AXE_EXPELLER, AXE_EXPELLER),
        List.of(COMMANDER, GOLEM, GOLEM, GOLEM),
        List.of(EXPELLER, COMMANDER, GOLEM, GOLEM),
        List.of(EXPELLER, AXE_EXPELLER, COMMANDER, GOLEM),
        List.of(EXPELLER, AXE_EXPELLER, GOLEM, AXION),
        List.of(EXPELLER, EXPELLER, AXE_EXPELLER, COMMANDER, GOLEM, EXPELLER, AXE_EXPELLER, GOLEM),
        List.of(EXPELLER, GOLEM, AXE_EXPELLER, EXPELLER, COMMANDER, GOLEM, EXPELLER, IAE),
        List.of(EXPELLER, AXE_EXPELLER, COMMANDER, EXPELLER, GOLEM, COMMANDER, EXPELLER, AXE_EXPELLER, COMMANDER, EXPELLER),
        List.of(SIEGHART));
    private static final List<List<Vec3>> DRY_SPAWNS = List.of(
        List.of(new Vec3(-30.5, 197, -60.5), new Vec3(-26.5, 197, -61.5), new Vec3(-22.5, 197, -58.5),
            new Vec3(-18.5, 197, -61.5), new Vec3(-13.5, 197, -58.5), new Vec3(-9.5, 197, -62.5),
            new Vec3(-5.5, 197, -59.5), new Vec3(-.5, 197, -56.5)),
        List.of(new Vec3(-7.5, 197, -22.5), new Vec3(-2.5, 197, -21.5), new Vec3(3.5, 197, -21.5),
            new Vec3(7.5, 197, -23.5), new Vec3(-8.5, 197, -15.5), new Vec3(8.5, 197, -15.5),
            new Vec3(6.5, 197, -9.5), new Vec3(.5, 197, -11.5)),
        List.of(new Vec3(-7.5, 197, 34.5), new Vec3(8.5, 197, 35.5), new Vec3(-16.5, 197, 37.5),
            new Vec3(16.5, 197, 35.5), new Vec3(-22.5, 197, 41.5), new Vec3(22.5, 197, 39.5),
            new Vec3(-30.5, 197, 37.5), new Vec3(30.5, 197, 39.5), new Vec3(-36.5, 197, 42.5),
            new Vec3(36.5, 197, 42.5)),
        List.of(new Vec3(.5, 203, 74.5)));
    private final DungeonPorts.Targets targets;
    private static final class Brain {
        final Vec3 home;final int room;
        final StatusLeases leases=new StatusLeases();
        ActionPhase phase=ActionPhase.IDLE;
        long busy,next,heavy,storm;int step;
        Brain(DungeonEnemy e,int room){home=e.position();this.room=room;busy=e.moveAt()+e.moveDuration();next=busy+Math.floorMod(e.getUUID().hashCode(),450);}
    }
    private static final class Pending {
        final PalaceAttack attack;long previous;
        Pending(PalaceAttack attack){this.attack=attack;previous=attack.release();}
    }
    private record Motion(UUID source,Vec3 from,Vec3 to,long start,long end,double arc,String kind){}
    private final DungeonService owner;
    private final Journal journal;
    private final DungeonPorts.Players players;
    private final Map<UUID,Brain> brains=new HashMap<>();
    private final Map<String,Pending> attacks=new LinkedHashMap<>();
    private final Map<UUID,Motion> motions=new HashMap<>();
    PalaceEncounterService(DungeonService owner,Journal journal,DungeonPorts.Players players,DungeonPorts.Targets targets){this.owner=owner;this.journal=journal;this.players=players;this.targets=targets;}

    void breathe(List<ServerPlayer> here) {
        for (ServerPlayer player : here) {
            if (!player.isInWater() || player.getY() > CastlePalace.SEA_LEVEL + 1) continue;
            var current = player.getEffect(MobEffects.WATER_BREATHING);
            if (current == null || !current.isInfiniteDuration() && current.getDuration() <= 30)
                player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 70, 0, true, false, false));
        }
    }

    private boolean canFight(ServerPlayer player) {
        return player.isAlive() && !players.session(player).life.dead
            && !player.isSpectator() && !player.getAbilities().invulnerable;
    }

    boolean spawnArrivals(Dungeon d, List<ServerPlayer> here) throws Exception {
        if (d.palaceRooms.size() == CastlePalace.ROOM_COUNT) return false;
        var arrived = new LinkedHashSet<Integer>();
        for (ServerPlayer player : here) {
            if (!canFight(player)) continue;
            int room = CastlePalace.roomAt(d.cell, player.position());
            if (room >= 0 && !d.palaceRooms.contains(room)) arrived.add(room);
        }
        if (arrived.isEmpty()) return false;

        planAbyss(d);
        var existing = Set.copyOf(d.enemies);
        journal.begin();
        try {
            for (int room : arrived) spawn(d, room);
            d.palaceRooms.addAll(arrived);
            journal.put("instance/" + d.id, d);
            journal.finish();
        } catch (Exception failure) {
            journal.abort();
            d.palaceRooms.removeAll(arrived);
            owner.abyss.rollbackPalaceSpawn(d);
            d.enemies.removeIf(id -> {
                if (existing.contains(id)) return false;
                Entity enemy = owner.level().getEntity(id);
                if (enemy != null) enemy.discard();
                return true;
            });
            throw failure;
        }
        return true;
    }

    void planAbyss(Dungeon dungeon) {
        var slots = new ArrayList<Vec3>();
        for (int room = CastlePalace.DECK; room < CastlePalace.BOSS; room++) {
            var squad = SQUADS.get(room);
            for (int i = 0; i < squad.size(); i++) {
                if (squad.get(i) != AXION && squad.get(i) != IAE) slots.add(spawnPoint(dungeon.cell, room, i));
            }
        }
        owner.abyss.plan(dungeon, slots);
    }

    boolean finishIfDefeated(Dungeon d, List<ServerPlayer> here) throws Exception {
        if (!d.status.equals("RUNNING") || d.palaceRooms.size() != CastlePalace.ROOM_COUNT
            || !d.enemies.stream().allMatch(owner::defeated)) return false;
        owner.completeOpenDungeon(d, here, "地下城通关！可拾取战利品或返回。");
        return true;
    }

    private void spawn(Dungeon d, int room) {
        List<PalaceEnemyKind> squad = SQUADS.get(room);
        boolean submerged = CastlePalace.submerged(room);
        for (int i = 0; i < squad.size(); i++) {
            PalaceEnemyKind kind = squad.get(i);
            Vec3 position = spawnPoint(d.cell, room, i);
            if(!submerged&&kind!=AXION&&kind!=IAE&&kind!=SIEGHART&&owner.abyss.replaces(d,position))continue;
            DungeonEnemy enemy = owner.spawn(d, kind.id, kind == AXION || kind == IAE, kind == SIEGHART,
                position.x, position.y, position.z);
            enemy.getPersistentData().putInt(ROOM_TAG, room);
            if (submerged) {
                enemy.setNoGravity(true);
                enemy.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, -1, 0, true, false, false));
            }
        }
    }

    static Vec3 spawnPoint(int cell, int room, int i) {
        if (!CastlePalace.submerged(room)) {
            Vec3 anchor = DRY_SPAWNS.get(room - CastlePalace.DECK).get(i);
            return CastlePalace.worldPoint(cell, anchor.x, anchor.y, anchor.z);
        }
        if (room == 0 && i == 0) return CastlePalace.worldPoint(cell, -7.5, 98.3, -78.5);
        return CastlePalace.ringPoint(cell, Math.toRadians(127 - i * 27), 27.4, CastlePalace.floorY(room) + .65);
    }
    public void brain(Dungeon d,DungeonEnemy e,List<ServerPlayer> here,long now){
        PalaceEnemyKind kind=PalaceEnemyKind.of(e);
        int room=e.getPersistentData().getIntOr(ROOM_TAG,-1);
        if(kind==null||room<0||room>=CastlePalace.ROOM_COUNT)return;
        Brain b=brains.computeIfAbsent(e.getUUID(),id->new Brain(e,room));
        if(CombatContentTypes.controlled(e)){cancel(e.getUUID());return;}
        if(now<b.busy){e.getNavigation().stop();return;}
        if(!CastlePalace.contains(d.cell,b.room,e.position())){
            cancel(e.getUUID());
            motions.remove(e.getUUID());
            e.getNavigation().stop();
            e.setPos(b.home);
            return;
        }
        ServerPlayer p=here.stream().filter(q->q.level()==e.level()&&canFight(q)&&CastlePalace.contains(d.cell,b.room,q.position())).min(Comparator.comparingDouble(e::distanceToSqr)).orElse(null);
        if(p==null){if(e.position().distanceToSqr(b.home)>4)move(d,e,b,kind,b.home,now);else e.getNavigation().stop();return;}
        Vec3 direction=p.position().subtract(e.position()).multiply(1,0,1).normalize();if(direction.lengthSqr()<.001)direction=new Vec3(0,0,1);
        float yaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z));e.setYRot(yaw);e.yBodyRot=yaw;e.getLookControl().setLookAt(p);
        double distance=e.distanceTo(p);boolean ranged=kind==PalaceEnemyKind.COMMANDER||kind==PalaceEnemyKind.IAE;
        if(ranged&&distance<4)move(d,e,b,kind,CastlePalace.inside(d.cell,b.room,e.position().subtract(direction.scale(3))),now);
        else if(distance>(ranged?8:2.6))move(d,e,b,kind,CastlePalace.inside(d.cell,b.room,p.position()),now);
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
                    double range=cn.yumis.fight.modules.combat.api.ProjectileSpawn.extendedRange(14);
                    for(int i=0;i<4;i++){var hit=attack(e,"IAE_DART","DART",650,Math.round(950*range/14),.15,1,false,-1,now);hit.group=group;hit.origin(from);hit.destination(from.add(direction.yRot((float)(i*Math.PI/2)).scale(range)));hit.width=.16;add(hit);}}
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
        List<ServerPlayer> candidates=here.stream().filter(p->canFight(p)&&CastlePalace.contains(d.cell,b.room,p.position())).toList();
        for(int i=0;i<6;i++){
            Vec3 anchor=candidates.isEmpty()?b.home:candidates.get(i%candidates.size()).position();double angle=i*Math.PI/3;
            Vec3 point=CastlePalace.inside(d.cell,b.room,anchor.add(Math.cos(angle)*(i%2==0?0:3),0,Math.sin(angle)*(i%2==0?0:3)));
            var hit=attack(e,"LIGHTNING","CIRCLE",600,180,1.6,.25,true,0,now+400+i*400);hit.origin(point);hit.destination(point);add(hit);
        }
    }
    private void play(DungeonEnemy e,Brain b,String move,long duration,long now){duration=targets.target(e).actionTiming(now).duration(duration);e.getNavigation().stop();e.setDeltaMovement(Vec3.ZERO);e.play(move,duration);e.setAggressive(true);b.busy=now+duration;b.next=b.busy+500;}
    private PalaceAttack attack(DungeonEnemy e,String kind,String shape,long windup,long active,double radius,double coefficient,boolean magic,int element,long now){
        var timing=targets.target(e).actionTiming(now);
        PalaceAttack a=new PalaceAttack();a.source=e.getUUID();a.instance=e.instance();a.kind=kind;a.shape=shape;a.started=now;a.windup=timing.windup(windup);a.active=timing.active(active);a.radius=radius;a.coefficient=coefficient;a.magic=magic;a.element=element;a.origin(e.position());a.destination(e.position());return a;
    }
    private void fan(DungeonEnemy e,ServerPlayer p,String kind,long windup,long active,double radius,double halfAngle,double coefficient,boolean magic,int element,long now){var hit=attack(e,kind,"FAN",windup,active,radius,coefficient,magic,element,now);hit.halfAngle=halfAngle;hit.destination(p.position());add(hit);}
    private void add(PalaceAttack attack){if(attack.group.isEmpty())attack.group=attack.id;attacks.put(attack.id,new Pending(attack));owner.broadcast(attack.instance,"palaceAttack",packet(attack));}
    private void motion(DungeonEnemy e,Vec3 to,long delay,long duration,double arc,String kind,long now){motions.put(e.getUUID(),new Motion(e.getUUID(),e.position(),to,now+delay,now+delay+duration,arc,kind));}
    private Vec3 clipMove(DungeonEnemy e,Vec3 to){Vec3 from=e.position().add(0,.8,0);var hit=owner.level().clip(new ClipContext(from,to.add(0,.8,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,e));if(hit.getType()==HitResult.Type.MISS)return to;Vec3 direction=to.subtract(e.position()).multiply(1,0,1).normalize();return hit.getLocation().subtract(0,.8,0).subtract(direction.scale(e.getBbWidth()*.6));}
    private void move(Dungeon d,DungeonEnemy e,Brain brain,PalaceEnemyKind kind,Vec3 to,long now){
        if (CastlePalace.submerged(brain.room)) {
            e.getNavigation().stop();
            e.setNoGravity(true);
            e.setDeltaMovement(Vec3.ZERO);
            Vec3 step = CastlePalace.swimStep(d.cell, brain.room, e.position(), to).subtract(e.position());
            if (step.lengthSqr() > .0324) step = step.normalize().scale(.18);
            e.move(MoverType.SELF, step);
            return;
        }
        if(kind!=PalaceEnemyKind.GOLEM){e.getNavigation().moveTo(to.x,to.y,to.z,1);return;}
        e.setNoGravity(true);e.setDeltaMovement(Vec3.ZERO);Vec3 delta=to.subtract(e.position()).multiply(1,0,1);if(delta.length()>.14)delta=delta.normalize().scale(.14);
        double y=to.y+.45+Math.sin(now/650d)*.08;e.move(MoverType.SELF,delta.add(0,Math.clamp(y-e.getY(),-.12,.12),0));
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
            if(now>=m.end){motions.remove(m.source);e.setNoGravity(floatWithoutMotion(e,brains.get(m.source)));}
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
    private JsonObject packet(PalaceAttack a){
        var frame=JSON.toJsonTree(a).getAsJsonObject();
        var dungeon=owner.byId(a.instance);
        var brain=brains.get(a.source);
        if(dungeon!=null)frame.addProperty("cell",dungeon.cell);
        if(brain!=null)frame.addProperty("area",brain.room);
        frame.addProperty("contactUntil",System.currentTimeMillis());
        var raw=owner.level().getEntity(a.source);
        if(raw instanceof LivingEntity source)frame.add("boss",JSON.toJsonTree(targets.target(source)));
        return frame;
    }
    private void remove(PalaceAttack a,boolean cancelled){if(attacks.remove(a.id)==null)return;a.remove=true;a.cancelled=cancelled;owner.broadcast(a.instance,"palaceAttack",packet(a));}
    public void track(ServerPlayer p){for(Pending pending:attacks.values())if(owner.participant(p,pending.attack.instance))Network.send(p,"palaceAttack",packet(pending.attack));}
    public void cancel(UUID source){
        for(Pending pending:new ArrayList<>(attacks.values()))if(pending.attack.source.equals(source))remove(pending.attack,true);
        Entity raw=owner.level().getEntity(source);Motion old=motions.get(source);
        Brain b=brains.get(source);
        if(raw instanceof DungeonEnemy e&&PalaceEnemyKind.of(e)!=null){
            if(old!=null&&!old.kind.equals("SETTLE")){
                motions.remove(source);
                if(e.isAlive()&&b!=null&&!CastlePalace.submerged(b.room)&&e.getY()>b.home.y+.6)
                    motion(e,new Vec3(e.getX(),b.home.y,e.getZ()),0,400,0,"SETTLE",System.currentTimeMillis());
            }
            e.setNoGravity(floatWithoutMotion(e,b)||motions.containsKey(source));e.setAggressive(false);e.setDeltaMovement(Vec3.ZERO);e.fallDistance=0;
            if(!e.move().isEmpty())e.play("",0);
        }
        if(b!=null){b.phase=ActionPhase.ABANDONED;b.leases.clear();b.busy=0;b.next=Math.max(b.next,System.currentTimeMillis()+400);}
    }
    private static boolean floatWithoutMotion(DungeonEnemy enemy,Brain brain){
        return PalaceEnemyKind.of(enemy)==GOLEM || brain!=null&&CastlePalace.submerged(brain.room);
    }
    public void close(Dungeon d){
        for(UUID id:new ArrayList<>(brains.keySet())){Entity raw=owner.level().getEntity(id);if(raw instanceof DungeonEnemy e&&e.instance().equals(d.id.toString())){cancel(id);brains.remove(id);motions.remove(id);}}
        for(Pending pending:new ArrayList<>(attacks.values()))if(pending.attack.instance.equals(d.id.toString()))remove(pending.attack,true);
    }
}
