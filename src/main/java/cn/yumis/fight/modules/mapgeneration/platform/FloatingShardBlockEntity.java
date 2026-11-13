package cn.yumis.fight.modules.mapgeneration.platform;

import cn.yumis.fight.modules.mapgeneration.api.WorldDecorations;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** No ticker or mutable inventory: animation is derived from block state and world time. */
public final class FloatingShardBlockEntity extends BlockEntity {
    public FloatingShardBlockEntity(BlockPos pos,BlockState state){super(WorldDecorations.FLOATING_SHARD_ENTITY.get(),pos,state);}
}
