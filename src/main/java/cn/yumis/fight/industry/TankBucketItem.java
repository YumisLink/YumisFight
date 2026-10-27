package cn.yumis.fight.industry;

import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.Level;

/** Standard bucket placement and source pickup; coolant does not regenerate sources. */
public final class TankBucketItem extends BucketItem {
    public TankBucketItem(Properties p){super(IndustrialContent.COOLANT.get(),p);}

}
