package cn.yumis.fight.modules.mapgeneration.platform;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

/** Food uses the native consumption flow; sneaking retains ordinary block placement. */
public final class EdibleDecorationItem extends BlockItem {
    public EdibleDecorationItem(Block block,Properties properties){super(block,properties);}
    @Override public InteractionResult useOn(UseOnContext context){
        if(context.getPlayer()==null||context.isSecondaryUseActive())return super.useOn(context);
        return use(context.getLevel(),context.getPlayer(),context.getHand());
    }
}
