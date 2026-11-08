package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.dungeons.content.SeaTrain;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static cn.yumis.fight.modules.dungeons.api.ClassicEnemyKind.*;

/** The two classic train rosters share one route; Mobeni and Kongkongyi precede Bodir. */
final class SeaTrainEncounterService extends ClassicEncounterService {
    private record Totems(List<UUID> ids,long detonate){}
    private final Map<UUID,Totems> totems=new HashMap<>();
    private final Map<UUID,Long> counters=new HashMap<>();
    SeaTrainEncounterService(DungeonService owner,Journal journal,DungeonPorts.Players players,DungeonPorts.Targets targets){super(owner,journal,players,targets,SeaTrain.ID,SeaTrain.BOSS,SeaTrain.WAVES,SeaTrain.AREAS);}
    @Override protected boolean maintain(Dungeon d,DungeonEnemy e,Brain b,List<ServerPlayer> here,long now){
        Totems state=totems.get(e.getUUID());
        if(state!=null){
            boolean alive=state.ids.stream().anyMatch(id->alive(entity(id)));defense(e,alive);
            if(!alive){totems.remove(e.getUUID());defense(e,false);announce(d,"鳄鱼图腾已被破坏，周期反制解除。");}
            else if(now>=state.detonate){
                ClassicMove blast=ClassicMove.find(SEA_TRAIN_CROCODILE_TOTEM,"explode");
                for(UUID id:state.ids){DungeonEnemy pillar=entity(id);if(!alive(pillar))continue;Brain pb=brains.get(id);
                    Pending p=emit(d,pillar,pb,blast,"CIRCLE",pillar.position(),pillar.position().add(0,0,1),now,200,200,1,UUID.randomUUID().toString());p.retired=true;publish(p);retire(pillar);
                }
                totems.remove(e.getUUID());defense(e,false);
            }
        }
        if(ClassicEnemyKind.of(e)==SEA_TRAIN_MOBENI&&!b.auxiliary&&now>=counters.getOrDefault(e.getUUID(),now+1)&&now>=b.busy){
            counters.put(e.getUUID(),now+12000);ServerPlayer target=here.stream().filter(p->eligible(p,d)).findFirst().orElse(null);if(target!=null)play(d,e,b,move(e,"cooldown"),target,now);
        }
        counters.putIfAbsent(e.getUUID(),now+7000);return false;
    }
    @Override protected ClassicMove choose(Dungeon d,DungeonEnemy e,Brain b,ServerPlayer target,double distance,long now){
        var moves=ClassicMove.forActor(ClassicEnemyKind.of(e));
        for(int i=0;i<moves.size();i++){
            ClassicMove m=moves.get(Math.floorMod(b.turn+i,moves.size()));
            if(m.clip().equals("cooldown"))continue;
            if(m.clip().equals("heal")&&nearby(e,7).stream().noneMatch(a->targets.target(a).life.q<.95))continue;
            if(available(d,b,m,distance,now)){b.turn=Math.floorMod(b.turn+i+1,moves.size());return m;}
        }
        return null;
    }
    @Override protected void onAction(Dungeon d,DungeonEnemy e,Brain b,ClassicMove move,ServerPlayer target,long now){
        super.onAction(d,e,b,move,target,now);ClassicEnemyKind kind=ClassicEnemyKind.of(e);
        if(move.clip().equals("summon")&&children(e).size()<5&&!(b.auxiliary&&Set.of(SEA_TRAIN_BODIR,SEA_TRAIN_MASACCIO).contains(kind))){
            if(kind==SEA_TRAIN_MOBENI)for(int i=0;i<3;i++)child(d,e,SEA_TRAIN_TOOTHPICK_BIRD,e.position().add((i-1)*2,0,2),9000);
            else if(kind==SEA_TRAIN_KONGKONGYI)for(int i=0;i<2;i++)child(d,e,SEA_TRAIN_JELLYFISH,b.destination.add((i*2-1)*2,0,0),9000);
            else if(kind==SEA_TRAIN_BODIR&&!b.auxiliary)for(int i=0;i<2;i++){var clone=child(d,e,SEA_TRAIN_BODIR,e.position().add((i*2-1)*3,0,2),8500);clone.play("fire",move.duration());}
            else if(kind==SEA_TRAIN_MASACCIO&&!b.auxiliary)for(int i=0;i<2;i++)child(d,e,SEA_TRAIN_MASACCIO,e.position().add((i*2-1)*3,0,0),6500);
            else for(int i=0;i<3;i++)child(d,e,SEA_TRAIN_MALE_MERMAN,e.position().add((i-1)*2,0,3),7000);
        }
        if(Set.of("totem","cooldown").contains(move.clip())&&!totems.containsKey(e.getUUID())){
            List<UUID> ids=new ArrayList<>();for(int i=0;i<3;i++)ids.add(child(d,e,SEA_TRAIN_CROCODILE_TOTEM,b.destination.add((i-1)*3,0,2),8000).getUUID());
            totems.put(e.getUUID(),new Totems(List.copyOf(ids),now+4000));announce(d,"莫贝尼的反制图腾正在蓄势，破坏图腾可阻止爆发。");
        }
        if(move.clip().equals("fall_bomb")){
            for(int i=0;i<4;i++){Vec3 direction=new Vec3(0,0,1).yRot((float)(i*Math.PI/2));Pending p=emit(d,e,b,move,"BOMB",e.position().add(0,2,0),e.position().add(direction.scale(5)),now,200,650,.3,UUID.randomUUID().toString());p.attack.magic=true;p.attack.element=1;publish(p);}
        }
    }
    @Override protected void decorate(Pending p,DungeonEnemy e){
        if(ClassicEnemyKind.of(e)==SEA_TRAIN_BODIR&&e.getPersistentData().getBooleanOr("ydnf_classic_aux",false))p.attack.element=1;
    }
    @Override protected void onDeath(DungeonEnemy e,Brain b){totems.remove(e.getUUID());counters.remove(e.getUUID());}
    @Override void close(Dungeon d){super.close(d);if(d.dungeon.equals(dungeon)){totems.keySet().removeIf(id->!brains.containsKey(id));counters.keySet().removeIf(id->!brains.containsKey(id));}}
}
