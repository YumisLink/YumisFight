package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.combat.api.CombatContentTypes;
import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.dungeons.content.PursuitAnnihilation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static cn.yumis.fight.modules.dungeons.api.ClassicEnemyKind.*;

/** Gizel's repair crew, split emitters and a breakable, single-use enemy-side time rewind. */
final class PursuitEncounterService extends ClassicEncounterService {
    private static final class Machine {Vec3 initial;double health;UUID clock;long restoreAt;boolean used,scheduled;}
    private final Map<UUID,Machine> machines=new HashMap<>();
    private final Map<UUID,UUID> repairing=new HashMap<>();
    private record Fragments(List<UUID> ids,long until){}
    private final Map<UUID,Fragments> fragments=new HashMap<>();
    PursuitEncounterService(DungeonService owner,Journal journal,DungeonPorts.Players players,DungeonPorts.Targets targets){super(owner,journal,players,targets,PursuitAnnihilation.ID,PursuitAnnihilation.BOSS,PursuitAnnihilation.WAVES,PursuitAnnihilation.AREAS);}
    @Override protected void onSpawn(Dungeon d,DungeonEnemy e,Brain b,long now){
        if(ClassicEnemyKind.of(e)==PURSUIT_GIZEL&&e.boss()){Machine m=new Machine();m.initial=e.position();m.health=targets.target(e).life.q;machines.put(e.getUUID(),m);}
        if(ClassicEnemyKind.of(e)==PURSUIT_TANK_BUNKER){e.setYRot(180);e.yBodyRot=180;e.setYHeadRot(180);}
    }
    @Override protected boolean maintain(Dungeon d,DungeonEnemy e,Brain b,List<ServerPlayer> here,long now){
        ClassicEnemyKind kind=ClassicEnemyKind.of(e);
        if(kind==PURSUIT_SPARK_GAMMA){
            if(e.getPersistentData().getBooleanOr("ydnf_spark_fragment",false)){
                e.setNoGravity(true);e.getNavigation().stop();ServerPlayer target=here.stream().filter(p->eligible(p,d)).min(Comparator.comparingDouble(e::distanceToSqr)).orElse(null);
                if(target!=null&&now>=b.busy&&b.ready(move(e,"bombard"),now))play(d,e,b,move(e,"bombard"),target,now);return true;
            }
            Fragments pieces=fragments.get(e.getUUID());
            if(pieces!=null){
                if(now>=pieces.until||pieces.ids.stream().noneMatch(id->alive(entity(id)))){fragments.remove(e.getUUID());e.setInvisible(false);immune(e,false);b.busy=Math.max(b.busy,now+400);}
                else {e.getNavigation().stop();return true;}
            }
        }
        if(kind==PURSUIT_ENGINEER){
            DungeonEnemy ally=nearby(e,13).stream().filter(a->a!=e&&Set.of(PURSUIT_GIZEL,PURSUIT_MODIFIED_SOLDIER,PURSUIT_REINFORCED_SOLDIER).contains(ClassicEnemyKind.of(a))&&targets.target(a).life.q<1).min(Comparator.comparingDouble(e::distanceToSqr)).orElse(null);
            if(ally!=null){repairing.put(e.getUUID(),ally.getUUID());e.getLookControl().setLookAt(ally);if(e.distanceTo(ally)>3){e.getNavigation().moveTo(ally,1);return true;}if(now>=b.busy&&b.ready(move(e,"repair"),now))play(d,e,b,move(e,"repair"),null,now);return true;}
        }
        Machine m=machines.get(e.getUUID());if(m==null)return false;
        boolean shield=children(e).stream().anyMatch(a->ClassicEnemyKind.of(a)==PURSUIT_SPARK_BETA);defense(e,shield);
        if(m.clock!=null){
            DungeonEnemy clock=entity(m.clock);
            if(!alive(clock)){m.clock=null;announce(d,"时间机器已被破坏，回档中断。");}
            else if(now>=m.restoreAt){
                m.clock=null;retire(clock);cancel(e.getUUID());Target t=targets.target(e);t.life.q=m.health;t.life.dead=false;t.life.generation++;t.life.revision++;persist(e,t,true);
                e.setPos(m.initial.x,m.initial.y,m.initial.z);b.ready.clear();ClassicMove reset=move(e,"repair_stance");e.play(reset.clip(),reset.duration());b.busy=now+reset.duration();
                announce(d,"时间机器已回档吉赛尔的生命、位置和招式冷却。");
            }
        }
        if(!m.used&&!m.scheduled&&targets.target(e).life.q<=.5&&now>=b.busy&&b.ready(move(e,"time_call"),now)&&!CombatContentTypes.controlled(e)){
            m.scheduled=true;ServerPlayer target=here.stream().filter(p->eligible(p,d)).findFirst().orElse(null);play(d,e,b,move(e,"time_call"),target,now);
        }
        return false;
    }
    @Override protected ClassicMove choose(Dungeon d,DungeonEnemy e,Brain b,ServerPlayer target,double distance,long now){
        if(ClassicEnemyKind.of(e)==PURSUIT_TANK_BUNKER&&target.getZ()>e.getZ()+.5)return null;
        if(ClassicEnemyKind.of(e)==PURSUIT_GIZEL){
            String[] order={"saw","missile","charge","support_call"};
            for(int i=0;i<order.length;i++){ClassicMove m=move(e,order[Math.floorMod(b.turn+i,order.length)]);if(available(d,b,m,distance,now)){b.turn=Math.floorMod(b.turn+i+1,order.length);return m;}}return null;
        }
        return super.choose(d,e,b,target,distance,now);
    }
    @Override protected void onAction(Dungeon d,DungeonEnemy e,Brain b,ClassicMove move,ServerPlayer target,long now){
        super.onAction(d,e,b,move,target,now);
        switch(move.clip()){
            case "summon" -> {if(ClassicEnemyKind.of(e)==PURSUIT_KAMOKA&&!b.auxiliary&&children(e).size()<2)for(int i=0;i<2;i++)child(d,e,PURSUIT_KAMOKA,e.position().add((i*2-1)*3,0,2),6500);}
            case "repair" -> {DungeonEnemy ally=entity(repairing.get(e.getUUID()));if(alive(ally)&&e.distanceTo(ally)<=4)heal(ally,1/30d);}
            case "support_call" -> {
                if(children(e).size()<7){child(d,e,PURSUIT_ENGINEER,e.position().add(-3,0,-2),20000);child(d,e,PURSUIT_SPARK_BETA,e.position().add(3,0,-2),18000);child(d,e,PURSUIT_SPARK_GAMMA,e.position().add(0,0,3),18000);}
                if(children(e).size()<10)for(int i=0;i<3;i++)child(d,e,PURSUIT_MECHANICAL_MOUSE,e.position().add((i-1)*2,0,3),12000);
            }
            case "time_call" -> {
                Machine m=machines.get(e.getUUID());if(m!=null&&!m.used){m.used=true;m.scheduled=false;DungeonEnemy clock=child(d,e,PURSUIT_TIME_MACHINE,e.position().add(4,0,-3),10000);m.clock=clock.getUUID();m.restoreAt=now+ClassicMove.find(PURSUIT_TIME_MACHINE,"time").duration();clock.play("time",m.restoreAt-now);announce(d,"吉赛尔正在启动时间机器，优先破坏机器。");}
            }
            case "split" -> {
                if(!e.getPersistentData().getBooleanOr("ydnf_spark_fragment",false)&&!fragments.containsKey(e.getUUID())){
                    List<UUID> ids=new ArrayList<>();
                    for(int i=0;i<4;i++){DungeonEnemy piece=child(d,e,PURSUIT_SPARK_GAMMA,e.position().add((i%2*2-1)*3,0,(i/2*2-1)*3),6200);piece.getPersistentData().putBoolean("ydnf_spark_fragment",true);piece.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE).setBaseValue(.35);piece.setPos(piece.getX(),81,piece.getZ());piece.setNoGravity(true);ids.add(piece.getUUID());}
                    fragments.put(e.getUUID(),new Fragments(List.copyOf(ids),now+6000));immune(e,true);e.setInvisible(true);
                }
            }
        }
    }
    @Override protected boolean customAttack(Dungeon d,DungeonEnemy e,Brain b,ClassicMove move,ServerPlayer target,long now){
        if(!move.clip().equals("bombard"))return false;
        String group=UUID.randomUUID().toString();for(int i=0;i<4;i++){Vec3 direction=new Vec3(0,0,1).yRot((float)(i*Math.PI/2));Vec3 from=e.position().add(direction.scale(1.5)).add(0,1,0);emit(d,e,b,move,"BOMB",from,b.destination.add(direction.scale(2)),now,move.contact(0)+i*120,850,move.coefficient()/4,group+"/pulse/"+i);}return true;
    }
    @Override protected void onCancel(DungeonEnemy e,Brain b){Machine m=machines.get(e.getUUID());if(m!=null&&!m.used)m.scheduled=false;}
    @Override protected void onDeath(DungeonEnemy e,Brain b){machines.remove(e.getUUID());repairing.remove(e.getUUID());fragments.remove(e.getUUID());}
    @Override void close(Dungeon d){super.close(d);if(d.dungeon.equals(dungeon)){machines.keySet().removeIf(id->!brains.containsKey(id));repairing.keySet().removeIf(id->!brains.containsKey(id));fragments.keySet().removeIf(id->!brains.containsKey(id));}}
}
