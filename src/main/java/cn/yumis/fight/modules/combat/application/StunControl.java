package cn.yumis.fight.modules.combat.application;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
/** Wall-clock statuses survive unload; each mob restores its own original AI setting. */
public final class StunControl {
    private static final String PREVIOUS_AI="ydnf_control_previous_no_ai";
    public void tick(MinecraftServer server,java.util.function.Function<LivingEntity,cn.yumis.fight.modules.combat.api.Life> lives){
        long now=System.currentTimeMillis();
        for(ServerLevel level:server.getAllLevels())for(Entity e:level.getAllEntities())if(e instanceof LivingEntity living){
            cn.yumis.fight.modules.combat.api.Life life=null;
            for(var kind:cn.yumis.fight.modules.combat.api.ElementAccumulation.Kind.values())if(living.hasEffect(cn.yumis.fight.modules.combat.api.CombatContentTypes.elementalEffect(kind))){
                if(life==null)life=lives.apply(living);
                if(!life.elemental.active(kind,life,now))living.removeEffect(cn.yumis.fight.modules.combat.api.CombatContentTypes.elementalEffect(kind));
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
