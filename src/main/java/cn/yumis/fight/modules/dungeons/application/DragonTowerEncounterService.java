package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.combat.api.CombatContentTypes;
import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.dungeons.content.DragonTower;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Each landing activates once, independently of deaths or the order players visit the tower. */
final class DragonTowerEncounterService {
    private static final String[][] FORMATIONS={
        {"dragonman","dragonman","wyvern","blue_dragonman"},
        {"dragonman","wyvern","wyvern","blue_dragonman"},
        {"dragonman","wyvern","blue_wyvern","blue_dragonman"},
        {"blue_dragonman","blue_wyvern","blue_wyvern","dragonman"},
        {"dragonman","blue_dragonman","blue_wyvern","blue_wyvern"},
        {"wyvern","blue_wyvern","blue_dragonman","dragonman"}
    };
    private static final class Run {
        final BitSet activated=new BitSet(DragonTower.LEVELS);
        final Map<UUID,Flight> flights=new HashMap<>();
        final Map<UUID,Pursuit> pursuits=new HashMap<>();
        UUID boss;
        long ambience;
    }
    private record Saved(String layout,int activated,UUID boss){}
    private static final class Flight {
        final FlyingPathNavigation navigation;
        Path path;
        Vec3 goal=Vec3.ZERO;
        long nextPath;
        Flight(DungeonEnemy enemy,DungeonService owner){navigation=new FlyingPathNavigation(enemy,owner.level());}
    }
    private final class Pursuit extends Goal {
        final Dungeon dungeon;
        final DungeonEnemy enemy;
        ServerPlayer target;
        Pursuit(Dungeon dungeon,DungeonEnemy enemy){this.dungeon=dungeon;this.enemy=enemy;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
        @Override public boolean canUse(){return target!=null&&target.isAlive()&&target.level()==enemy.level()&&!playerAccess.session(target).life.dead&&!CombatContentTypes.controlled(enemy)
                &&(Math.abs(enemy.getY()-target.getY())>=3||enemy.distanceTo(target)>=18);}
        @Override public boolean canContinueToUse(){return canUse();}
        @Override public void tick(){enemy.getLookControl().setLookAt(target);navigate(dungeon,enemy,target);}
        @Override public void stop(){enemy.getNavigation().stop();}
    }
    private final DungeonService owner;
    private final Journal journal;
    private final DungeonPorts.Players playerAccess;
    private final Map<UUID,Run> runs=new HashMap<>();
    DragonTowerEncounterService(DungeonService owner,Journal journal,DungeonPorts.Players players){this.owner=owner;this.journal=journal;this.playerAccess=players;}
    static boolean handles(Dungeon dungeon){return dungeon.treasure==null&&dungeon.dungeon.equals("dragon_tower");}
    void initialize(Dungeon dungeon){
        if(runs.containsKey(dungeon.id))return;
        try{
            Saved saved=journal.get("dragonTowerRun/"+dungeon.id,Saved.class,null);Run run=new Run();
            if(saved!=null){
                if(!DragonTower.VERSION.equals(saved.layout)||saved.activated<0||saved.activated>=(1<<DragonTower.LEVELS)||((saved.activated&(1<<6))!=0&&saved.boss==null))throw new IllegalStateException("龙塔场次布局已经失效，请重新入场");
                run.activated.or(BitSet.valueOf(new long[]{saved.activated}));run.boss=saved.boss;
            }else{
                if(dungeon.wave!=0||!dungeon.enemies.isEmpty())throw new IllegalStateException("旧龙塔场次已经停用，请重新入场");
                journal.put("dragonTowerRun/"+dungeon.id,new Saved(DragonTower.VERSION,0,null));
            }
            DragonTowerScenery.place(owner.level(),dungeon);runs.put(dungeon.id,run);
        }catch(Exception failure){throw abandon(dungeon,failure);}
    }
    void tick(Dungeon dungeon,List<ServerPlayer> players,long now)throws Exception{
        initialize(dungeon);Run run=runs.get(dungeon.id);boolean changed=false;
        for(int floor=0;floor<DragonTower.LEVELS;floor++){
            if(run.activated.get(floor))continue;
            int index=floor;
            boolean arrived=players.stream().filter(Entity::isAlive).filter(p->!p.isSpectator()&&!playerAccess.session(p).life.dead).anyMatch(p->DragonTower.arrives(index,local(dungeon,p.position())));
            if(!arrived)continue;
            try{
                var positions=DragonTower.spawns(floor);
                if(floor==6){Vec3 p=world(dungeon,positions.getFirst());run.boss=owner.spawn(dungeon,"lucas",false,true,p.x,p.y,p.z).getUUID();}
                else for(int i=0;i<FORMATIONS[floor].length;i++){
                    Vec3 p=world(dungeon,positions.get(i));
                    var enemy=owner.spawn(dungeon,FORMATIONS[floor][i],i==3,false,p.x,p.y,p.z);
                    enemy.getPersistentData().putInt("ydnf_dragon_floor",floor);
                }
                run.activated.set(floor);dungeon.wave=Math.max(dungeon.wave,floor);changed=true;
            }catch(RuntimeException failure){throw abandon(dungeon,failure);}
        }
        changed|=dungeon.enemies.removeIf(this::dead);
        if(changed){
            journal.begin();
            try{
                long[] bits=run.activated.toLongArray();
                journal.put("dragonTowerRun/"+dungeon.id,new Saved(DragonTower.VERSION,bits.length==0?0:(int)bits[0],run.boss));
                journal.put("instance/"+dungeon.id,dungeon);journal.finish();
            }catch(Exception failure){journal.abort();throw abandon(dungeon,failure);}
            owner.refreshCamp();
        }
        if(run.boss!=null&&dead(run.boss)&&dungeon.status.equals("RUNNING"))owner.completeTower(dungeon,players);
        if(now>=run.ambience){run.ambience=now+2500;DragonTowerScenery.ambience(owner.level(),dungeon,players);}
    }
    private boolean dead(UUID id){Target target=journal.get("target/"+id,Target.class,null);return target!=null&&target.life.dead;}
    void died(Dungeon dungeon,UUID id,List<ServerPlayer> players)throws Exception{
        Run run=runs.get(dungeon.id);
        if(run!=null&&id.equals(run.boss)&&dead(id)&&dungeon.status.equals("RUNNING"))owner.completeTower(dungeon,players);
    }
    /** Preserve native melee goals; provide short, reachable waypoints instead of a path through six floors. */
    boolean pursue(Dungeon dungeon,DungeonEnemy enemy,ServerPlayer target){
        if(!handles(dungeon)||CombatContentTypes.controlled(enemy))return false;
        if(!enemy.boss()){
            Run run=runs.get(dungeon.id);if(run==null)return false;
            Pursuit goal=run.pursuits.computeIfAbsent(enemy.getUUID(),ignored->{var created=new Pursuit(dungeon,enemy);enemy.movementGoal(created);return created;});
            goal.target=target;enemy.setTarget(target);
        }
        if(Math.abs(enemy.getY()-target.getY())<3&&enemy.distanceTo(target)<18)return false;
        navigate(dungeon,enemy,target);return true;
    }
    private void navigate(Dungeon dungeon,DungeonEnemy enemy,ServerPlayer target){
        if(enemy.tickCount%10!=0&&!enemy.getNavigation().isDone())return;
        Vec3 waypoint=world(dungeon,DragonTower.pursuitWaypoint(local(dungeon,enemy.position()),local(dungeon,target.position())));
        Vec3 delta=waypoint.subtract(enemy.position());
        if(delta.length()>14)waypoint=enemy.position().add(delta.normalize().scale(14));
        enemy.getNavigation().moveTo(waypoint.x,waypoint.y+.1,waypoint.z,1);
    }
    void fly(Dungeon dungeon,DungeonEnemy enemy,ServerPlayer target,long now){
        Vec3 destination=target.position().add(0,.7,0);
        boolean direct=owner.level().clip(new ClipContext(enemy.getEyePosition(),target.getEyePosition(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,enemy)).getType()==HitResult.Type.MISS
                &&owner.level().noCollision(enemy,enemy.getBoundingBox().expandTowards(destination.subtract(enemy.position())));
        Run run=runs.get(dungeon.id);
        if(!direct&&run!=null){
            destination=world(dungeon,DragonTower.pursuitWaypoint(local(dungeon,enemy.position()),local(dungeon,target.position()))).add(0,.75,0);
            Flight flight=run.flights.computeIfAbsent(enemy.getUUID(),ignored->new Flight(enemy,owner));
            if(now>=flight.nextPath||flight.goal.distanceToSqr(destination)>9){
                flight.nextPath=now+900;flight.goal=destination;
                flight.path=flight.navigation.createPath(BlockPos.containing(destination),0);
            }
            if(flight.path!=null){
                while(!flight.path.isDone()&&flight.path.getNextEntityPos(enemy).distanceToSqr(enemy.position())<.7)flight.path.advance();
                if(!flight.path.isDone())destination=flight.path.getNextEntityPos(enemy);
            }
        }
        Vec3 delta=destination.subtract(enemy.position());
        double speed=enemy.distanceTo(target)>2?.14:0;
        enemy.setDeltaMovement(delta.lengthSqr()<.01?Vec3.ZERO:delta.normalize().scale(speed).add(0,Math.sin(now/500d)*.004,0));
    }
    private static Vec3 local(Dungeon dungeon,Vec3 p){return p.subtract(dungeon.cell*512+.5,80,.5);}
    private static Vec3 world(Dungeon dungeon,Vec3 p){return p.add(dungeon.cell*512+.5,80,.5);}
    private RuntimeException abandon(Dungeon dungeon,Exception failure){
        RuntimeException result=failure instanceof RuntimeException runtime?runtime:new IllegalStateException("龙塔场次保存失败",failure);
        try{owner.close(dungeon);}catch(Exception cleanup){result.addSuppressed(cleanup);}return result;
    }
    void close(Dungeon dungeon)throws Exception{runs.remove(dungeon.id);DragonTowerScenery.clear(owner.level(),dungeon.cell);journal.put("dragonTowerRun/"+dungeon.id,null);}
}
