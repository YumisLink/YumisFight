package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.dungeons.content.WitchForest;
import cn.yumis.fight.modules.presentation.api.ModText;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static cn.yumis.fight.modules.dungeons.api.ClassicEnemyKind.*;

/** Classic witch spells and familiar wake-ups, plus the five explicitly approved original forest move sets. */
final class ForestEncounterService extends ClassicEncounterService {
    private static final class Familiars {int turn;long stormReady;}
    private record Thicket(List<UUID> posts,Pending field){}
    private final Map<UUID,Familiars> familiars=new HashMap<>();
    private final Map<UUID,Long> awakening=new HashMap<>();
    private final Map<UUID,Thicket> thickets=new HashMap<>();
    ForestEncounterService(DungeonService owner,Journal journal,DungeonPorts.Players players,DungeonPorts.Targets targets){super(owner,journal,players,targets,WitchForest.ID,WitchForest.BOSS,WitchForest.WAVES,WitchForest.AREAS);}
    @Override protected void onSpawn(Dungeon d,DungeonEnemy e,Brain b,long now){
        if(ClassicEnemyKind.of(e)==CENTRAL_PARK_BLUE_WITCH&&e.boss()){
            familiars.put(e.getUUID(),new Familiars());child(d,e,CENTRAL_PARK_CRAZY_LION_CAT,e.position().add(-5,0,-2),0);child(d,e,CENTRAL_PARK_TOTTO_PUPPY,e.position().add(5,0,-2),0);
        }
    }
    @Override protected boolean maintain(Dungeon d,DungeonEnemy e,Brain b,List<ServerPlayer> here,long now){
        if(e.getPersistentData().getBooleanOr("ydnf_classic_tree",false)){e.getNavigation().stop();if(!e.move().equals("wood_cage"))e.play("wood_cage",Math.max(1,b.expires-now));return true;}
        Long wake=awakening.get(e.getUUID());if(wake!=null){if(now<wake){e.getNavigation().stop();return true;}awakening.remove(e.getUUID());immune(e,false);}
        if(familiars.containsKey(e.getUUID()))immune(e,children(e).stream().anyMatch(a->Set.of(CENTRAL_PARK_CRAZY_LION,CENTRAL_PARK_TOTTO).contains(ClassicEnemyKind.of(a))));
        Thicket thicket=thickets.get(e.getUUID());if(thicket!=null&&thicket.posts.stream().noneMatch(id->alive(entity(id)))){remove(thicket.field,true,now);thickets.remove(e.getUUID());}
        return false;
    }
    @Override protected ClassicMove choose(Dungeon d,DungeonEnemy e,Brain b,ServerPlayer target,double distance,long now){
        Familiars state=familiars.get(e.getUUID());if(state==null)return super.choose(d,e,b,target,distance,now);
        var moves=ClassicMove.forActor(ClassicEnemyKind.of(e));
        for(int i=0;i<moves.size();i++){
            ClassicMove m=moves.get(Math.floorMod(b.turn+i,moves.size()));
            if(Set.of("ice","thunder").contains(m.clip())&&(targets.target(e).life.q>.5||now<state.stormReady))continue;
            if(m.clip().equals("summon")&&children(e).stream().filter(a->Set.of(CENTRAL_PARK_CRAZY_LION,CENTRAL_PARK_TOTTO).contains(ClassicEnemyKind.of(a))).count()>=2)continue;
            if(available(d,b,m,distance,now)){b.turn=Math.floorMod(b.turn+i+1,moves.size());return m;}
        }
        return null;
    }
    @Override protected void onStart(Dungeon d,DungeonEnemy e,Brain b,ClassicMove move,ServerPlayer target,long now){
        Familiars state=familiars.get(e.getUUID());if(state!=null&&Set.of("ice","thunder").contains(move.clip()))state.stormReady=now+12000;
    }
    @Override protected void onAction(Dungeon d,DungeonEnemy e,Brain b,ClassicMove move,ServerPlayer target,long now){
        super.onAction(d,e,b,move,target,now);Familiars state=familiars.get(e.getUUID());
        if(state!=null&&move.clip().equals("summon")){
            ClassicEnemyKind sleeping=state.turn++%2==0?CENTRAL_PARK_CRAZY_LION_CAT:CENTRAL_PARK_TOTTO_PUPPY;
            DungeonEnemy pet=children(e).stream().filter(a->ClassicEnemyKind.of(a)==sleeping).findFirst().orElse(null);
            if(pet!=null){Vec3 at=pet.position();retire(pet);ClassicEnemyKind awakened=sleeping==CENTRAL_PARK_CRAZY_LION_CAT?CENTRAL_PARK_CRAZY_LION:CENTRAL_PARK_TOTTO;
                DungeonEnemy fighter=child(d,e,awakened,at,0);fighter.play("transform_in",1000);awakening.put(fighter.getUUID(),now+1000);immune(fighter,true);immune(e,true);announce(d,sleeping==CENTRAL_PARK_CRAZY_LION_CAT?"蓝色魔女唤醒了疯狂莱恩。":"蓝色魔女唤醒了小狗坨坨。");}
        }
        if(move.clip().equals("tree_cage")){
            Thicket old=thickets.remove(e.getUUID());if(old!=null){remove(old.field,true,now);for(UUID id:old.posts){DungeonEnemy post=entity(id);if(alive(post))retire(post);}}
            List<UUID> posts=new ArrayList<>();
            for(int i=0;i<4;i++){Vec3 offset=new Vec3(0,0,3).yRot((float)(i*Math.PI/2));DungeonEnemy post=child(d,e,CENTRAL_PARK_HUNTING_GRASS,b.destination.add(offset),6500);post.getPersistentData().putBoolean("ydnf_classic_tree",true);post.setInvisible(true);post.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);post.setCustomName(ModText.of("荆棘木柱"));post.play("wood_cage",6500);posts.add(post.getUUID());}
            Pending field=emit(d,e,b,move,"CIRCLE",b.destination,b.destination.add(0,0,1),now,350,6000,0,UUID.randomUUID().toString());field.attack.radius=3.2;publish(field);thickets.put(e.getUUID(),new Thicket(List.copyOf(posts),field));
        }
    }
    @Override protected boolean customAttack(Dungeon d,DungeonEnemy e,Brain b,ClassicMove move,ServerPlayer target,long now){
        if(ClassicEnemyKind.of(e)==CENTRAL_PARK_BLUE_WITCH&&move.clip().equals("throw")){
            String group=UUID.randomUUID().toString();for(int i=0;i<3;i++){Pending p=emit(d,e,b,move,"BOMB",e.position().add(0,1.2,0),b.destination.add((i-1)*2,0,0),now,move.contact(0),850,move.coefficient(),group);p.attack.radius=1.8;publish(p);}return true;
        }
        if(ClassicEnemyKind.of(e)==CENTRAL_PARK_BLUE_WITCH&&move.clip().equals("ice")){
            for(int i=0;i<6;i++){Vec3 offset=new Vec3(0,0,2.5).yRot((float)(i*Math.PI/3));Pending p=emit(d,e,b,move,"CIRCLE",b.destination.add(offset),b.destination.add(offset).add(0,0,1),now,move.contact(0)+i*400,700,move.coefficient()/6,UUID.randomUUID().toString());p.attack.radius=2.5;p.attack.magic=true;p.attack.element=i%3==0?0:i%3==1?1:2;publish(p);}return true;
        }
        if(move.clip().equals("wood_spikes")){
            String group=UUID.randomUUID().toString();for(int i=0;i<4;i++){Vec3 from=e.position(),direction=new Vec3(0,0,1).yRot((float)(i*Math.PI/2));emit(d,e,b,move,"RECT",from,from.add(direction.scale(move.range())),now,move.contact(0),200,move.coefficient(),group);}return true;
        }
        return false;
    }
    @Override protected void onDeath(DungeonEnemy e,Brain b){
        ClassicEnemyKind kind=ClassicEnemyKind.of(e);
        if(b.auxiliary&&Set.of(CENTRAL_PARK_CRAZY_LION,CENTRAL_PARK_TOTTO).contains(kind)){
            String raw=e.getPersistentData().getStringOr("ydnf_parent","");DungeonEnemy witch=raw.isEmpty()?null:entity(UUID.fromString(raw));Dungeon d=owner.byId(e.instance());
            if(alive(witch)&&d!=null)child(d,witch,kind==CENTRAL_PARK_CRAZY_LION?CENTRAL_PARK_CRAZY_LION_CAT:CENTRAL_PARK_TOTTO_PUPPY,e.position(),0);
        }
        awakening.remove(e.getUUID());familiars.remove(e.getUUID());
    }
    @Override protected void onCancel(DungeonEnemy e,Brain b){Thicket old=thickets.remove(e.getUUID());if(old!=null)remove(old.field,true,System.currentTimeMillis());}
    @Override void close(Dungeon d){super.close(d);if(d.dungeon.equals(dungeon)){familiars.keySet().removeIf(id->!brains.containsKey(id));awakening.keySet().removeIf(id->!brains.containsKey(id));thickets.keySet().removeIf(id->!brains.containsKey(id));}}
}
