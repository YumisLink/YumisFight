package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.dungeons.content.Grandine;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import java.util.*;
import static cn.yumis.fight.modules.dungeons.api.ClassicEnemyKind.*;

/** Generator protection, untargetable summoning phantoms and Furman's exposed interrupt core. */
final class GrandineEncounterService extends ClassicEncounterService {
    private record Core(UUID entity,long until){}
    private final Map<UUID,UUID> generators=new HashMap<>();
    private final Map<UUID,Core> cores=new HashMap<>();
    private final Map<UUID,Long> phantoms=new HashMap<>();
    GrandineEncounterService(DungeonService owner,Journal journal,DungeonPorts.Players players,DungeonPorts.Targets targets){super(owner,journal,players,targets,Grandine.ID,Grandine.BOSS,Grandine.WAVES,Grandine.AREAS);}
    @Override protected void onSpawn(Dungeon d,DungeonEnemy e,Brain b,long now){
        if(ClassicEnemyKind.of(e)==GRANDI_POTURA&&!b.auxiliary){DungeonEnemy generator=child(d,e,GRANDI_GENERATOR,e.position().add(5,0,0),0);generators.put(e.getUUID(),generator.getUUID());immune(e,true);e.guarded(true);}
        if(ClassicEnemyKind.of(e)==GRANDI_FURMAN_PHANTOM){phantoms.put(e.getUUID(),now+800);e.play("summon",1800);}
    }
    @Override protected boolean maintain(Dungeon d,DungeonEnemy e,Brain b,List<ServerPlayer> here,long now){
        UUID generator=generators.get(e.getUUID());if(generator!=null&&!alive(entity(generator))){generators.remove(e.getUUID());immune(e,false);e.guarded(false);e.play("hurt",500);b.busy=now+700;announce(d,"供能发电机已破坏，波图拉失去保护。");}
        Core core=cores.get(e.getUUID());
        if(core!=null){
            DungeonEnemy device=entity(core.entity);
            if(!alive(device)){interrupt(e.getUUID());e.play("hurt",500);b.busy=now+1200;announce(d,"核心已击破，粉碎时空被打断。");}
            else if(now>=core.until){cores.remove(e.getUUID());retire(device);b.hard=false;e.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);}
        }
        if(ClassicEnemyKind.of(e)==GRANDI_FURMAN_PHANTOM&&now>=phantoms.getOrDefault(e.getUUID(),Long.MAX_VALUE)){
            phantoms.put(e.getUUID(),now+6000);e.play("summon",1800);
            if(children(e).size()<4)for(int i=0;i<3;i++)child(d,e,i%2==0?GRANDI_ASSAULT_GRANITE:GRANDI_FLYING_GRANITE,e.position().add((i-1)*2,0,3),9000);
        }
        return false;
    }
    @Override protected void onStart(Dungeon d,DungeonEnemy e,Brain b,ClassicMove move,ServerPlayer target,long now){
        if(move.clip().equals("quake")){
            DungeonEnemy core=child(d,e,GRANDI_VOID_CORE,e.position().add(b.direction.scale(3)),move.duration()+500);
            core.play("core",move.duration());cores.put(e.getUUID(),new Core(core.getUUID(),now+move.duration()));b.hard=true;e.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            announce(d,"弗曼开始粉碎时空，击破暴露核心可打断。");
        }
    }
    @Override protected void onAction(Dungeon d,DungeonEnemy e,Brain b,ClassicMove move,ServerPlayer target,long now){
        super.onAction(d,e,b,move,target,now);
        if(move.clip().equals("summon")&&ClassicEnemyKind.of(e)==GRANDI_FURMAN&&children(e).stream().noneMatch(a->ClassicEnemyKind.of(a)==GRANDI_FURMAN_PHANTOM))child(d,e,GRANDI_FURMAN_PHANTOM,e.position().add(5,0,-3),14000);
    }
    @Override protected boolean customAttack(Dungeon d,DungeonEnemy e,Brain b,ClassicMove move,ServerPlayer target,long now){
        if(ClassicEnemyKind.of(e)!=GRANDI_MAGMA_SLIME||!move.clip().equals("burrow"))return false;
        Pending p=emit(d,e,b,move,"CIRCLE",b.destination,b.destination.add(0,0,1),now,Math.round(move.duration()*.82),200,1,UUID.randomUUID().toString());p.attack.radius=2.4;p.attack.element=1;p.attack.magic=true;publish(p);return true;
    }
    @Override protected void onCancel(DungeonEnemy e,Brain b){Core core=cores.remove(e.getUUID());if(core!=null){DungeonEnemy device=entity(core.entity);if(alive(device))retire(device);e.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);}}
    @Override protected void onDeath(DungeonEnemy e,Brain b){generators.remove(e.getUUID());phantoms.remove(e.getUUID());}
    @Override void close(Dungeon d){super.close(d);if(d.dungeon.equals(dungeon)){generators.keySet().removeIf(id->!brains.containsKey(id));cores.keySet().removeIf(id->!brains.containsKey(id));phantoms.keySet().removeIf(id->!brains.containsKey(id));}}
}
