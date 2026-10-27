package cn.yumis.fight.industry;

import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.Level;

/** Coolant is a tank fluid in v1; standard tank interaction handles filling and draining. */
public final class TankBucketItem extends BucketItem {
    public TankBucketItem(Properties p){super(IndustrialContent.COOLANT.get(),p);}
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){return InteractionResult.PASS;}
}
