package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.dungeons.content.GoblinCamp;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** All forest enemies are placed once on entry; restart recovery closes that instance. */
final class GoblinCampEncounterService {
    private static final List<String> CAMP_TYPES=List.of("goblin","goblin","goblin_thrower","goblin_captain");
    private static final class Run {
        final UUID boss;
        long nextAmbience;
        Run(UUID boss){this.boss=boss;}
    }
    private final DungeonService owner;
    private final Journal journal;
    private final Map<UUID,Run> runs=new HashMap<>();
    GoblinCampEncounterService(DungeonService owner,Journal journal){this.owner=owner;this.journal=journal;}
    static boolean handles(Dungeon dungeon){return dungeon.treasure==null&&dungeon.dungeon.equals("goblin_camp");}
    void initialize(Dungeon dungeon){
        if(runs.containsKey(dungeon.id))return;
        try{
            var origin=new BlockPos(dungeon.cell*512,80,0);
            int patrolCount=GoblinCamp.CAMPS.size()*CAMP_TYPES.size()*2;
            var patrols=GoblinCamp.patrolSpawns(owner.level(),origin,patrolCount,dungeon.id.getMostSignificantBits()^dungeon.id.getLeastSignificantBits());
            GoblinCampScenery.place(owner.level(),dungeon);
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
    }
    private void spawn(Dungeon dungeon,int index,BlockPos at,String region){
        int formation=index%CAMP_TYPES.size();
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
}
