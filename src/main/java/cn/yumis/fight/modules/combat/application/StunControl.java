package cn.yumis.fight.modules.combat.application;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import java.util.*;
/** Restores each mob's own pre-stun AI setting when the status ends. */
public final class StunControl {
    private final Map<UUID,Boolean> stunned=new HashMap<>();
    public void tick(MinecraftServer server){
        for(ServerLevel level:server.getAllLevels())for(Entity e:level.getAllEntities())if(e instanceof Mob mob){
            boolean stun=mob.hasEffect(cn.yumis.fight.modules.combat.api.CombatContentTypes.STUN);
            if(stun&&!stunned.containsKey(e.getUUID())){
                stunned.put(e.getUUID(),mob.isNoAi());
                mob.setNoAi(true);
                mob.getNavigation().stop();
            }
            else if(!stun&&stunned.containsKey(e.getUUID()))mob.setNoAi(stunned.remove(e.getUUID()));
        }
    }
}
