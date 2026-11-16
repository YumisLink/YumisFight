package cn.yumis.fight.modules.industry.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/** Crop positions and previews share the same single-layer harvest area. */
public final class HarvestArea {
    private HarvestArea(){}
    public static int width(int tier){return IndustrialRules.current().harvestSizes[tier-1];}
    public static BlockPos crop(BlockPos crystal,int tier,int cursor){
        int width=width(tier),at=Math.floorMod(cursor,width*width);
        return crystal.below().offset(at%width-width/2,0,at/width-width/2);
    }
    public static AABB bounds(BlockPos crystal,int tier){
        int width=width(tier);BlockPos first=crop(crystal,tier,0);
        return new AABB(first.getX(),first.getY(),first.getZ(),first.getX()+width,first.getY()+1,first.getZ()+width);
    }
}
