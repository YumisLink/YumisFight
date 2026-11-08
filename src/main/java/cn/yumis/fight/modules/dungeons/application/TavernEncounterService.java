package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.dungeons.content.MoonlightTavern;
import cn.yumis.fight.modules.presentation.api.ModText;
import cn.yumis.fight.modules.presentation.api.ModText;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import java.util.*;
import static cn.yumis.fight.modules.dungeons.api.ClassicEnemyKind.*;

/** Street fighters, fixed swordmaster skills and the brawler's net/brick/grab sequences. */
final class TavernEncounterService extends ClassicEncounterService {
    private static final class Temper {long hideAt;boolean enraged;}
    private final Map<UUID,Temper> tempers=new HashMap<>();
    TavernEncounterService(DungeonService owner,Journal journal,DungeonPorts.Players players,DungeonPorts.Targets targets){super(owner,journal,players,targets,MoonlightTavern.ID,MoonlightTavern.BOSS,MoonlightTavern.WAVES,MoonlightTavern.AREAS);}
    @Override protected void onSpawn(Dungeon d,DungeonEnemy e,Brain b,long now){Temper t=new Temper();t.hideAt=now+3000;tempers.put(e.getUUID(),t);}
    @Override protected boolean maintain(Dungeon d,DungeonEnemy e,Brain b,List<ServerPlayer> here,long now){
        Temper state=tempers.computeIfAbsent(e.getUUID(),id->new Temper());ClassicEnemyKind kind=ClassicEnemyKind.of(e);
        if(kind==MOONLIGHT_TAVERN_CHANG_SANLANG&&e.boss()&&!state.enraged&&targets.target(e).life.q<=.3){
            state.enraged=true;e.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);e.guarded(true);announce(d,"长三郎进入霸体架势，留意投网与砖击。");
        }
        if((kind==MOONLIGHT_TAVERN_PICKPOCKET||kind==MOONLIGHT_TAVERN_POISON_NIGHTBLADE)&&!b.auxiliary&&now>=state.hideAt&&now>=b.busy){e.setInvisible(true);state.hideAt=now+7000;}
        return false;
    }
    @Override protected void onStart(Dungeon d,DungeonEnemy e,Brain b,ClassicMove move,ServerPlayer target,long now){
        if(Set.of("stealth_stab","poison_cut","phantom_strike").contains(move.clip()))e.setInvisible(false);
    }
    @Override protected ClassicMove choose(Dungeon d,DungeonEnemy e,Brain b,ServerPlayer target,double distance,long now){
        if(ClassicEnemyKind.of(e)==MOONLIGHT_TAVERN_CHANG_SANLANG){
            String[] combo={"throw_net","brick_strike","grab_burst","mount_punch","suplex"};
            for(int i=0;i<combo.length;i++){ClassicMove m=move(e,combo[Math.floorMod(b.turn+i,combo.length)]);if(available(d,b,m,distance,now)){b.turn=Math.floorMod(b.turn+i+1,combo.length);return m;}}
            return null;
        }
        return super.choose(d,e,b,target,distance,now);
    }
    @Override protected void onAction(Dungeon d,DungeonEnemy e,Brain b,ClassicMove move,ServerPlayer target,long now){
        super.onAction(d,e,b,move,target,now);
        if(move.clip().equals("phantom_strike")&&!b.auxiliary&&children(e).size()<2){
            var shadow=child(d,e,MOONLIGHT_TAVERN_POISON_NIGHTBLADE,e.position().add(b.direction.yRot((float)(Math.PI/2)).scale(2)),2400);
            shadow.setCustomName(ModText.of("毒夜刃的残影"));immune(shadow,true);
        }
    }
    @Override protected boolean customAttack(Dungeon d,DungeonEnemy e,Brain b,ClassicMove move,ServerPlayer target,long now){
        if(!Set.of("duck_dash","sway").contains(move.clip()))return false;
        var direction=move.clip().equals("sway")?b.direction.yRot((float)(b.turn%2==0?Math.PI/2:-Math.PI/2)):b.direction;
        var to=cn.yumis.fight.modules.dungeons.content.ClassicDungeonRooms.inside(d.cell,b.area,e.position().add(direction.scale(move.clip().equals("sway")?2.2:4)));
        emit(d,e,b,move,"DASH",e.position(),to,now,move.windup(),450,0,UUID.randomUUID().toString());return true;
    }
    @Override protected void onDeath(DungeonEnemy e,Brain b){tempers.remove(e.getUUID());}
    @Override void close(Dungeon d){super.close(d);if(d.dungeon.equals(dungeon))tempers.keySet().removeIf(id->!brains.containsKey(id));}
}
