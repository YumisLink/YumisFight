package cn.yumis.fight.modules.mapgeneration.platform;

import cn.yumis.fight.modules.mapgeneration.api.WorldDecorations;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Map;

/** One material places either stone color, with the ordinary BlockItem placement transaction. */
public final class PebbleItem extends BlockItem {
    public PebbleItem(Block block,Properties properties){super(block,properties);}
    @Override protected BlockState getPlacementState(BlockPlaceContext context){
        var block=context.isSecondaryUseActive()?WorldDecorations.block("dark_pebbles"):getBlock();
        var state=block.getStateForPlacement(context);
        return state!=null&&canPlace(context,state)?state:null;
    }
    @Override public void registerBlocks(Map<Block,Item> map,Item item){super.registerBlocks(map,item);map.put(WorldDecorations.block("dark_pebbles"),item);}
}
