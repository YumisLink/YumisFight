package cn.yumis.fight.modules.industry.platform;

import net.minecraft.world.*;

import net.minecraft.world.item.BucketItem;

/** Standard bucket placement and source pickup; coolant does not regenerate sources. */
public final class TankBucketItem extends BucketItem {
    public TankBucketItem(Properties p){super(IndustrialContent.COOLANT.get(),p);}

}
