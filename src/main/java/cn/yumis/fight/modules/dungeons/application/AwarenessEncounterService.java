package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.dungeons.content.MysteriousAwareness;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static cn.yumis.fight.modules.dungeons.api.ClassicEnemyKind.*;

/** Puppet protection, a breakable magic instrument, Daru's possession and a fixed sword-soul mirror pool. */
final class AwarenessEncounterService extends ClassicEncounterService {
    private static final Set<ClassicEnemyKind> PUPPETS=Set.of(MYSTERIOUS_AWARENESS_AXE_PUPPET,MYSTERIOUS_AWARENESS_WHIP_PUPPET,MYSTERIOUS_AWARENESS_SWORD_PUPPET,MYSTERIOUS_AWARENESS_FIGHTER_PUPPET);
    private static final class Song {UUID instrument;boolean shield,instrumentBroken;long instrumentReady,fireUntil;}
    private static final class Link {UUID victim;long until;double q;}
    private final Map<UUID,Song> songs=new HashMap<>();
    private final Map<UUID,Link> links=new HashMap<>();
    private final Map<UUID,Long> instrumentShots=new HashMap<>();
    private final Map<UUID,Double> walking=new HashMap<>();
    AwarenessEncounterService(DungeonService owner,Journal journal,DungeonPorts.Players players,DungeonPorts.Targets targets){super(owner,journal,players,targets,MysteriousAwareness.ID,MysteriousAwareness.BOSS,MysteriousAwareness.WAVES,MysteriousAwareness.AREAS);}
    @Override protected void onSpawn(Dungeon d,DungeonEnemy e,Brain b,long now){
        if(ClassicEnemyKind.of(e)==MYSTERIOUS_AWARENESS_IRIS&&e.boss())songs.put(e.getUUID(),new Song());
        walking.put(e.getUUID(),e.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue());
    }
    @Override protected boolean maintain(Dungeon d,DungeonEnemy e,Brain b,List<ServerPlayer> here,long now){
        ClassicEnemyKind kind=ClassicEnemyKind.of(e);
        if(kind==MYSTERIOUS_AWARENESS_SPACE_SOLDIER||kind==MYSTERIOUS_AWARENESS_TIME_SOLDIER){
            long count=nearby(e,4).stream().filter(a->Set.of(MYSTERIOUS_AWARENESS_SPACE_SOLDIER,MYSTERIOUS_AWARENESS_TIME_SOLDIER).contains(ClassicEnemyKind.of(a))).count();
            e.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(walking.getOrDefault(e.getUUID(),.23)*(count>=3?1.3:1));e.guarded(count>=3);
        }
        Song song=songs.get(e.getUUID());
        if(song!=null){
            if(song.shield&&children(e).stream().noneMatch(a->PUPPETS.contains(ClassicEnemyKind.of(a)))){song.shield=false;immune(e,false);cancel(e.getUUID());e.play("hurt",500);b.busy=now+1000;announce(d,"战斗人偶已清除，艾丽丝的协奏保护被打断。");}
            if(song.instrument!=null&&!alive(entity(song.instrument))){song.instrument=null;song.instrumentReady=now+10000;if(song.instrumentBroken){cancel(e.getUUID());e.play("hurt",500);b.busy=now+1000;announce(d,"魔幻玛丽蕾特被击破，当前乐章中断。");}song.instrumentBroken=false;}
        }
        if(kind==MYSTERIOUS_AWARENESS_MARILETE){
            ServerPlayer target=here.stream().filter(p->eligible(p,d)).min(Comparator.comparingDouble(e::distanceToSqr)).orElse(null);
            if(target!=null){
                Vec3 next=cn.yumis.fight.modules.dungeons.content.ClassicDungeonRooms.inside(d.cell,b.area,target.position().add(2*Math.sin(now/800d),0,2*Math.cos(now/800d)));
                e.setNoGravity(true);e.setPos(e.getX()+(next.x-e.getX())*.025,81.4,e.getZ()+(next.z-e.getZ())*.025);
                if(now>=instrumentShots.getOrDefault(e.getUUID(),0L)){instrumentShots.put(e.getUUID(),now+1600);DungeonEnemy parent=entity(UUID.fromString(e.getPersistentData().getStringOr("ydnf_parent",e.getStringUUID())));ClassicMove ice=parent==null?null:move(parent,"ice");if(ice!=null)emit(d,e,b,ice,"DART",e.position(),target.position().add(0,.7,0),now,500,900,.7,UUID.randomUUID().toString());}
            }
            return true;
        }
        Link link=links.get(e.getUUID());
        if(link!=null){
            ServerPlayer victim=owner.level().getServer().getPlayerList().getPlayer(link.victim);
            if(victim==null||!eligible(victim,d)||now>=link.until||!cn.yumis.fight.modules.dungeons.content.ClassicDungeonRooms.contains(d.cell,b.area,victim.position()))links.remove(e.getUUID());
            else {double q=targets.target(e).life.q;if(q<link.q)reflect(d,e,b,link,link.q-q,now,false);link.q=q;Vec3 at=victim.position().add(.8,0,.3);e.setPos(at.x,at.y,at.z);}
        }
        return false;
    }
    @Override protected void onStart(Dungeon d,DungeonEnemy e,Brain b,ClassicMove move,ServerPlayer target,long now){
        Song song=songs.get(e.getUUID());if(song==null)return;
        if(Set.of("music","instrument","ice","fire","thunder").contains(move.clip())&&song.instrument==null&&now>=song.instrumentReady){
            DungeonEnemy instrument=child(d,e,MYSTERIOUS_AWARENESS_MARILETE,e.position().add(2,0,1),10000);instrument.setNoGravity(true);instrument.setPos(instrument.getX(),81.4,instrument.getZ());instrument.play("instrument",ClassicMove.find(MYSTERIOUS_AWARENESS_MARILETE,"instrument").duration());song.instrument=instrument.getUUID();
        }
        if(move.clip().equals("fire"))song.fireUntil=now+6000;
    }
    @Override protected void onAction(Dungeon d,DungeonEnemy e,Brain b,ClassicMove move,ServerPlayer target,long now){
        super.onAction(d,e,b,move,target,now);
        if(move.clip().equals("puppet")&&e.boss()){
            Song song=songs.get(e.getUUID());List<DungeonEnemy> old=children(e).stream().filter(a->PUPPETS.contains(ClassicEnemyKind.of(a))).toList();
            if(old.isEmpty())for(int i=0;i<4;i++){ClassicEnemyKind kind=new ClassicEnemyKind[]{MYSTERIOUS_AWARENESS_AXE_PUPPET,MYSTERIOUS_AWARENESS_WHIP_PUPPET,MYSTERIOUS_AWARENESS_SWORD_PUPPET,MYSTERIOUS_AWARENESS_FIGHTER_PUPPET}[i];child(d,e,kind,e.position().add((i-1.5)*2,0,3),0);}
            else for(int i=0;i<old.size();i++){Vec3 at=cn.yumis.fight.modules.dungeons.content.ClassicDungeonRooms.inside(d.cell,b.area,e.position().add((i-(old.size()-1)/2d)*2,0,3));old.get(i).setPos(at.x,80,at.z);}
            if(song!=null){song.shield=true;immune(e,true);announce(d,"艾丽丝展开协奏保护，先击破战斗人偶。");}
        }
        if(move.clip().equals("possession")&&target!=null){Link link=new Link();link.victim=target.getUUID();link.until=now+5000;link.q=targets.target(e).life.q;links.put(e.getUUID(),link);}
    }
    private void reflect(Dungeon d,DungeonEnemy e,Brain b,Link link,double fraction,long now,boolean retired){
        ClassicMove m=move(e,"possession");Pending p=emit(d,e,b,m,"CIRCLE",e.position(),e.position().add(0,0,1),now,0,150,1,UUID.randomUUID().toString());p.enemyFraction=fraction;p.lockedPlayer=link.victim;p.retired=retired;p.attack.radius=3;publish(p);
    }
    @Override protected boolean customAttack(Dungeon d,DungeonEnemy e,Brain b,ClassicMove move,ServerPlayer target,long now){
        if(ClassicEnemyKind.of(e)!=MYSTERIOUS_AWARENESS_IRIS||!move.clip().equals("fire"))return false;
        double[] phases=move.contacts();for(int i=0;i<phases.length;i++){Vec3 at=b.destination.add((i-1)*2,0,0);Pending p=emit(d,e,b,move,"BOMB",e.position().add(0,3,0),at,now,move.contact(i),700,move.coefficient()/phases.length,UUID.randomUUID().toString());p.attack.radius=2.6;publish(p);}return true;
    }
    @Override protected void decorate(Pending p,DungeonEnemy e){
        String parent=e.getPersistentData().getStringOr("ydnf_parent","");if(parent.isEmpty())return;
        Song song=songs.get(UUID.fromString(parent));if(song!=null&&System.currentTimeMillis()<song.fireUntil&&PUPPETS.contains(ClassicEnemyKind.of(e)))p.attack.element=1;
    }
    @Override protected void onCancel(DungeonEnemy e,Brain b){links.remove(e.getUUID());}
    @Override protected void onDeath(DungeonEnemy e,Brain b){
        if(ClassicEnemyKind.of(e)==MYSTERIOUS_AWARENESS_MARILETE){String parent=e.getPersistentData().getStringOr("ydnf_parent","");Song song=parent.isEmpty()?null:songs.get(UUID.fromString(parent));if(song!=null&&e.getUUID().equals(song.instrument))song.instrumentBroken=true;}
        Link link=links.remove(e.getUUID());if(link!=null&&link.q>0){Dungeon d=owner.byId(e.instance());if(d!=null)reflect(d,e,b,link,link.q,System.currentTimeMillis(),true);}songs.remove(e.getUUID());walking.remove(e.getUUID());instrumentShots.remove(e.getUUID());
    }
    @Override void close(Dungeon d){super.close(d);if(d.dungeon.equals(dungeon)){songs.keySet().removeIf(id->!brains.containsKey(id));links.keySet().removeIf(id->!brains.containsKey(id));walking.keySet().removeIf(id->!brains.containsKey(id));instrumentShots.keySet().removeIf(id->!brains.containsKey(id));}}
}
