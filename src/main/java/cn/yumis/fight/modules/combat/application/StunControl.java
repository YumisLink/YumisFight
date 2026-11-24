package cn.yumis.fight.modules.combat.application;
import cn.yumis.fight.modules.combat.api.CombatContentTypes;
import cn.yumis.fight.modules.combat.api.ElementAccumulation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
/** Wall-clock statuses survive unload; each mob restores its own original AI setting. */
public final class StunControl {
    private static final String PREVIOUS_AI="ydnf_control_previous_no_ai";
    public void tick(MinecraftServer server,java.util.function.Function<LivingEntity,cn.yumis.fight.modules.combat.api.Life> lives){
        long now=System.currentTimeMillis();
        for(ServerLevel level:server.getAllLevels())for(Entity e:level.getAllEntities())if(e instanceof LivingEntity living&&!(living instanceof net.minecraft.world.entity.player.Player)){
            cn.yumis.fight.modules.combat.api.Life life=null;
            for(ElementAccumulation.Kind kind:ElementAccumulation.Kind.values()){
                var effect=CombatContentTypes.elementalEffect(kind);
                boolean has=living.hasEffect(effect),chill=kind==ElementAccumulation.Kind.FREEZE&&living.hasEffect(CombatContentTypes.CHILL);
                if(!has&&!chill)continue;
                if(life==null)life=lives.apply(living);
                boolean active=life.elemental.active(kind,life,now),strong=active&&life.elemental.strong[kind.ordinal()];
                if(has&&(!active||kind==ElementAccumulation.Kind.FREEZE&&strong))living.removeEffect(effect);
                if(chill&&!strong)living.removeEffect(CombatContentTypes.CHILL);
            }
            if(!(living instanceof Mob mob))continue;
            boolean stun=cn.yumis.fight.modules.combat.api.CombatContentTypes.controlled(mob);
            var data=mob.getPersistentData();
            if(stun){
                if(!data.contains(PREVIOUS_AI))data.putBoolean(PREVIOUS_AI,mob.isNoAi());
                mob.setNoAi(true);
                mob.getNavigation().stop();
                if(mob.hasEffect(cn.yumis.fight.modules.combat.api.CombatContentTypes.FREEZE))mob.setDeltaMovement(0,Math.min(0,mob.getDeltaMovement().y),0);
            }
            else if(data.contains(PREVIOUS_AI)){mob.setNoAi(data.getBooleanOr(PREVIOUS_AI,false));data.remove(PREVIOUS_AI);}
        }
    }
}
