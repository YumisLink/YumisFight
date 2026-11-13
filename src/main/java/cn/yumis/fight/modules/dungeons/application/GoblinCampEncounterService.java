package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.dungeons.content.GoblinCamp;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Independent camps inside one existing dungeon instance; restart recovery closes that instance. */
final class GoblinCampEncounterService {
    private static final class Camp {
        int spawnedWaves;
        final Set<UUID> enemies=new HashSet<>();
    }
    private static final class Run {
        final List<Camp> camps=List.of(new Camp(),new Camp(),new Camp());
        UUID boss;
        long nextAmbience;
    }
    private final DungeonService owner;
    private final Journal journal;
    private final DungeonPorts.Targets targets;
    private final Map<UUID,Run> runs=new HashMap<>();
    GoblinCampEncounterService(DungeonService owner,Journal journal,DungeonPorts.Targets targets){this.owner=owner;this.journal=journal;this.targets=targets;}
    static boolean handles(Dungeon dungeon){return dungeon.treasure==null&&dungeon.dungeon.equals("goblin_camp");}
    void initialize(Dungeon dungeon){
        if(runs.containsKey(dungeon.id))return;
        GoblinCampScenery.place(owner.level(),dungeon);
        runs.put(dungeon.id,new Run());
    }
    void tick(Dungeon dungeon,List<ServerPlayer> players,long now)throws Exception{
        initialize(dungeon);
        Run run=runs.get(dungeon.id);
        boolean changed=dungeon.enemies.removeIf(this::dead);
        for(int i=0;i<run.camps.size();i++){
            Camp camp=run.camps.get(i);var area=GoblinCamp.CAMPS.get(i);
            camp.enemies.removeIf(this::dead);
            if(camp.spawnedWaves==0?approached(dungeon,area,players):camp.spawnedWaves==1&&camp.enemies.isEmpty()){
                spawnCamp(dungeon,area,camp,players);changed=true;
            }
        }
        if(run.boss==null&&approached(dungeon,GoblinCamp.BOSS,players)){
            var at=GoblinCamp.BOSS.at(new BlockPos(dungeon.cell*512,80,0),0,0,0);
            try{
                var boss=owner.spawn(dungeon,"goblin_shaman",false,true,at.getX()+.5,at.getY(),at.getZ()+.5);
                run.boss=boss.getUUID();dungeon.wave=6;
                for(var player:players)targets.context(player,boss);
            }catch(RuntimeException failure){throw abandon(dungeon,failure);}
            changed=true;
        }
        if(changed){journal.put("instance/"+dungeon.id,dungeon);owner.refreshCamp();}
        if(run.boss!=null&&dead(run.boss)&&dungeon.status.equals("RUNNING"))owner.completeCamp(dungeon,players);
        if(now>=run.nextAmbience){run.nextAmbience=now+2500;GoblinCampScenery.ambience(owner.level(),dungeon,players);}
    }
    private void spawnCamp(Dungeon dungeon,GoblinCamp.Area area,Camp camp,List<ServerPlayer> players){
        var positions=area.spawns(new BlockPos(dungeon.cell*512,80,0));
        String[] types={"goblin","goblin","goblin_thrower","goblin_captain"};
        try{
            for(int i=0;i<types.length;i++){
                var at=positions.get(i);
                var enemy=owner.spawn(dungeon,types[i],i==3,false,at.getX()+.5,at.getY(),at.getZ()+.5);
                enemy.getPersistentData().putString("ydnf_camp_region",area.id());
                camp.enemies.add(enemy.getUUID());
                for(var player:players)targets.context(player,enemy);
            }
            camp.spawnedWaves++;
        }catch(RuntimeException failure){throw abandon(dungeon,failure);}
    }
    private RuntimeException abandon(Dungeon dungeon,RuntimeException failure){
        try{owner.close(dungeon);}catch(Exception cleanup){failure.addSuppressed(cleanup);}
        return failure;
    }
    private boolean approached(Dungeon dungeon,GoblinCamp.Area area,List<ServerPlayer> players){
        return players.stream().anyMatch(p->p.isAlive()&&!p.isSpectator()&&Math.abs(p.getY()-80)<8&&area.contains(p.getX()-dungeon.cell*512,p.getZ(),3));
    }
    private boolean dead(UUID id){Target target=journal.get("target/"+id,Target.class,null);return target!=null&&target.life.dead;}
    void died(Dungeon dungeon,UUID id,List<ServerPlayer> players)throws Exception{
        Run run=runs.get(dungeon.id);
        if(run!=null&&id.equals(run.boss)&&dead(id)&&dungeon.status.equals("RUNNING"))owner.completeCamp(dungeon,players);
    }
    void close(Dungeon dungeon){runs.remove(dungeon.id);GoblinCampScenery.clear(owner.level(),dungeon.cell);}
    void recover(){runs.clear();GoblinCampScenery.clear(owner.level(),null);}
}
