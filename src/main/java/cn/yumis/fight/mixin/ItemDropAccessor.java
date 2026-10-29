package cn.yumis.fight.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemEntity.class)
public interface ItemDropAccessor {
    @Accessor("pickupDelay") int ydnf$pickupDelay();
    @Accessor("thrower") EntityReference<Entity> ydnf$thrower();
    @Accessor("thrower") void ydnf$thrower(EntityReference<Entity> thrower);
}
