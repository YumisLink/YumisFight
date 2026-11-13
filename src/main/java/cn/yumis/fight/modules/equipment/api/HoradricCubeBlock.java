package cn.yumis.fight.modules.equipment.api;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class HoradricCubeBlock extends Block {
    public static final MapCodec<HoradricCubeBlock> CODEC=simpleCodec(HoradricCubeBlock::new);
    public HoradricCubeBlock(Properties properties){super(properties);}
    @Override public MapCodec<? extends Block> codec(){return CODEC;}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!level.isClientSide())player.openMenu(new SimpleMenuProvider((id,inventory,p)->new HoradricCubeMenu(id,inventory,pos),cn.yumis.fight.modules.presentation.api.ModText.of("赫拉迪姆方块")));
        return InteractionResult.SUCCESS;
    }
}
