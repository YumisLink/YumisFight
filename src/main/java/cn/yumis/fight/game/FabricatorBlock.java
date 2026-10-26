package cn.yumis.fight.game;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.network.chat.Component;
public final class FabricatorBlock extends Block {
    public static final MapCodec<FabricatorBlock> CODEC=simpleCodec(FabricatorBlock::new);
    public FabricatorBlock(BlockBehaviour.Properties p){
        super(p);
    }
    @Override public MapCodec<? extends Block> codec(){
        return CODEC;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!level.isClientSide())player.openMenu(new SimpleMenuProvider((id,inv,p)->new FabricatorMenu(id,inv,pos),ModText.of("装备制作器")));
        return InteractionResult.SUCCESS;
    }
}
