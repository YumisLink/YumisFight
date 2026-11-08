package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.dungeons.content.Hamelin;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static cn.yumis.fight.modules.dungeons.api.ClassicEnemyKind.*;

/** Peter retains the same entity and room across three lives; revival never clears his mouse army. */
final class HamelinEncounterService extends ClassicEncounterService {
    private static final class Tower {final List<UUID> mice=new ArrayList<>();long until;}
    private final Map<UUID,Tower> towers=new HashMap<>();
    private final Map<UUID,UUID> towerMice=new HashMap<>();
    HamelinEncounterService(DungeonService owner,Journal journal,DungeonPorts.Players players,DungeonPorts.Targets targets){super(owner,journal,players,targets,Hamelin.ID,Hamelin.BOSS,Hamelin.WAVES,Hamelin.AREAS);}
    @Override protected void onSpawn(Dungeon d,DungeonEnemy e,Brain b,long now){
        if(ClassicEnemyKind.of(e)==HAMELIN_PETER&&e.boss()){
            Target t=targets.target(e);t.encounterLives=3;t.encounterReviveMillis=move(e,"revive").duration();persist(e,t,false);
        }
    }
    @Override protected ClassicMove choose(Dungeon d,DungeonEnemy e,Brain b,ServerPlayer target,double distance,long now){
        if(ClassicEnemyKind.of(e)!=HAMELIN_PETER)return super.choose(d,e,b,target,distance,now);
        String[] clips={"flute","rat_wave","mark","rat_tower","summon"};
        for(int i=0;i<clips.length;i++){ClassicMove m=move(e,clips[Math.floorMod(b.turn+i,clips.length)]);if(available(d,b,m,distance,now)){b.turn=Math.floorMod(b.turn+i+1,clips.length);return m;}}
        return null;
    }
    @Override protected boolean maintain(Dungeon d,DungeonEnemy e,Brain b,List<ServerPlayer> here,long now){
        if(towerMice.containsKey(e.getUUID())){e.getNavigation().stop();e.setNoGravity(true);return true;}
        Tower tower=towers.get(e.getUUID());if(tower==null)return false;
        if(now>=tower.until||tower.mice.stream().noneMatch(id->alive(entity(id)))){release(e,tower);return false;}
        e.getNavigation().stop();e.setNoGravity(true);e.setPos(b.home.x,81.8,b.home.z);immune(e,true);b.hard=true;
        if(now>=b.busy){ServerPlayer target=here.stream().filter(p->eligible(p,d)&&cn.yumis.fight.modules.dungeons.content.ClassicDungeonRooms.contains(d.cell,b.area,p.position())).findFirst().orElse(null);if(target!=null)play(d,e,b,move(e,"flute"),target,now);}
        return true;
    }
    private void release(DungeonEnemy e,Tower tower){
        for(UUID id:tower.mice){towerMice.remove(id);DungeonEnemy mouse=entity(id);if(mouse!=null){mouse.setNoGravity(false);mouse.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);}}
        towers.remove(e.getUUID());e.setNoGravity(false);e.setPos(e.getX(),80,e.getZ());if(targets.target(e).encounterReviveUntil==0)immune(e,false);
    }
    @Override protected void onAction(Dungeon d,DungeonEnemy e,Brain b,ClassicMove move,ServerPlayer target,long now){
        super.onAction(d,e,b,move,target,now);
        if(move.clip().equals("rat_tower")&&e.boss()){
            Tower tower=new Tower();tower.until=now+3500;towers.put(e.getUUID(),tower);
            List<DungeonEnemy> existing=children(e).stream().filter(a->Set.of(HAMELIN_GIANT_RAT,HAMELIN_MAN_EATING_RAT).contains(ClassicEnemyKind.of(a))).limit(3).toList();
            for(int i=0;i<3;i++){
                DungeonEnemy mouse=i<existing.size()?existing.get(i):child(d,e,HAMELIN_GIANT_RAT,b.home,0);mouse.setPos(b.home.x,80+i*.55,b.home.z);mouse.setNoGravity(true);
                mouse.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);tower.mice.add(mouse.getUUID());towerMice.put(mouse.getUUID(),e.getUUID());
            }
            immune(e,true);
        }else if(Set.of("summon","rat_wave").contains(move.clip())&&children(e).size()<7){
            int count=ClassicEnemyKind.of(e)==HAMELIN_PETER?3:2;
            for(int i=0;i<count;i++)child(d,e,i%2==0?HAMELIN_GIANT_RAT:HAMELIN_MAN_EATING_RAT,e.position().add((i-1)*2,0,2),e.boss()?0:15000);
        }
    }
    @Override protected boolean customAttack(Dungeon d,DungeonEnemy e,Brain b,ClassicMove move,ServerPlayer target,long now){
        if(!move.clip().equals("rat_wave"))return false;
        String group=UUID.randomUUID().toString();
        for(int i=0;i<6;i++){Vec3 direction=new Vec3(0,0,1).yRot((float)(i*Math.PI/3));Vec3 from=new Vec3(e.getX(),80.3,e.getZ());emit(d,e,b,move,"DART",from,from.add(direction.scale(move.range())),now,move.contact(0),1000,move.coefficient(),group);}
        return true;
    }
    @Override protected void onCancel(DungeonEnemy e,Brain b){Tower tower=towers.get(e.getUUID());if(tower!=null)release(e,tower);}
    @Override protected void onDeath(DungeonEnemy e,Brain b){towerMice.remove(e.getUUID());}
    @Override void close(Dungeon d){super.close(d);if(d.dungeon.equals(dungeon)){towers.keySet().removeIf(id->!brains.containsKey(id));towerMice.keySet().removeIf(id->!brains.containsKey(id));}}
}
