package cn.yumis.fight.modules.dungeons.api;

import net.minecraft.world.entity.LivingEntity;
import java.util.*;

/** World AI commits one set of life facts before projecting entities or health updates. */
public interface WorldTargets {
    Target target(LivingEntity entity);
    cn.yumis.fight.modules.combat.api.CombatPorts.Commit prepare(Map<LivingEntity,Target> changes);
    void commit(Map<LivingEntity,Target> changes);
    void release(Collection<? extends LivingEntity> entities);
    void releaseIds(Collection<UUID> actors);
}
