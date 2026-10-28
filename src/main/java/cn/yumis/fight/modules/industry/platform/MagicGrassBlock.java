package cn.yumis.fight.modules.industry.platform;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.*;
import java.util.List;

/** Not bonemealable; no survival self-drop, so the same plant cannot be replanted for energy. */
public final class MagicGrassBlock extends VegetationBlock {
    public MagicGrassBlock(BlockBehaviour.Properties p){super(p);}
    @Override protected MapCodec<? extends VegetationBlock> codec(){return simpleCodec(MagicGrassBlock::new);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return Block.column(12,0,13);}
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder params){return List.of(ResourceBlock.crystals(params,1));}
}
