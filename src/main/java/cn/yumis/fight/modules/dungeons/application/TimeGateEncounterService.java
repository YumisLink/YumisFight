package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.dungeons.content.TimeGate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static cn.yumis.fight.modules.dungeons.api.ClassicEnemyKind.*;

/** Six historical formations culminate in young Skasa, never the later special rift dungeon. */
final class TimeGateEncounterService extends ClassicEncounterService {
    private final Map<UUID,Long> sources=new HashMap<>();
    TimeGateEncounterService(DungeonService owner,Journal journal,DungeonPorts.Players players,DungeonPorts.Targets targets){super(owner,journal,players,targets,TimeGate.ID,TimeGate.BOSS,TimeGate.WAVES,TimeGate.AREAS);}
    @Override protected void onSpawn(Dungeon d,DungeonEnemy e,Brain b,long now){
        if(b.auxiliary)return;
        if(ClassicEnemyKind.of(e)==TIME_RIFT_SKELETON_KNIGHT)child(d,e,TIME_RIFT_DISEASE_SOURCE,e.position().add(5,0,2),0);
        if(ClassicEnemyKind.of(e)==TIME_RIFT_CERBERUS)child(d,e,TIME_RIFT_RESEARCH_CAGE,e.position().add(-5,0,2),0);
    }
    @Override protected boolean maintain(Dungeon d,DungeonEnemy e,Brain b,List<ServerPlayer> here,long now){
        if(ClassicEnemyKind.of(e)==TIME_RIFT_DISEASE_SOURCE&&now>=sources.getOrDefault(e.getUUID(),0L)){
            sources.put(e.getUUID(),now+2000);ClassicMove core=move(e,"core");Pending p=emit(d,e,b,core,"CIRCLE",e.position(),e.position().add(0,0,1),now,650,300,.3,UUID.randomUUID().toString());p.attack.radius=3.5;p.attack.magic=true;p.attack.element=3;publish(p);
        }
        if(ClassicEnemyKind.of(e)==TIME_RIFT_RESEARCH_CAGE){
            sources.putIfAbsent(e.getUUID(),now+3500);
            if(now>=sources.get(e.getUUID())){sources.put(e.getUUID(),now+4500);e.play("core",1450);if(children(e).size()<4)for(int i=0;i<2;i++)child(d,e,TIME_RIFT_GHOST_CAT,e.position().add((i*2-1)*2,0,2),10000);}
        }
        return false;
    }
    @Override protected void onAction(Dungeon d,DungeonEnemy e,Brain b,ClassicMove move,ServerPlayer target,long now){
        super.onAction(d,e,b,move,target,now);ClassicEnemyKind kind=ClassicEnemyKind.of(e);
        if(move.clip().equals("summon")&&children(e).size()<5){
            ClassicEnemyKind add=kind==TIME_RIFT_SKELETON_KNIGHT?TIME_RIFT_BLOOD_GHOUL:kind==TIME_RIFT_UTARA?TIME_RIFT_MINOTAUR_GUARD:TIME_RIFT_CHAOS_SPIRIT;
            for(int i=0;i<3;i++)child(d,e,add,e.position().add((i-1)*2.5,0,3),12000);
        }
        if(kind==TIME_RIFT_KANE&&move.clip().equals("aura")&&!b.auxiliary&&children(e).size()<2){
            for(int i=0;i<2;i++){var shadow=child(d,e,TIME_RIFT_KANE,e.position().add((i*2-1)*3,0,1),5000);immune(shadow,true);}
        }
    }
    @Override protected boolean customAttack(Dungeon d,DungeonEnemy e,Brain b,ClassicMove move,ServerPlayer target,long now){
        if(!move.clip().equals("mine"))return false;
        for(int i=1;i<=4;i++){Vec3 at=e.position().add(b.direction.scale(i*2.5));Pending p=emit(d,e,b,move,"CIRCLE",at,at.add(0,0,1),now,move.contact(0)+i*80,500,move.coefficient()/4,UUID.randomUUID().toString());p.attack.radius=1.8;p.attack.magic=true;p.attack.element=3;publish(p);}return true;
    }
    @Override protected void onDeath(DungeonEnemy e,Brain b){sources.remove(e.getUUID());}
    @Override void close(Dungeon d){super.close(d);if(d.dungeon.equals(dungeon))sources.keySet().removeIf(id->!brains.containsKey(id));}
}
