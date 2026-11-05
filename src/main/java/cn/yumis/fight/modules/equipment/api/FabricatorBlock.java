package cn.yumis.fight.modules.equipment.api;
import cn.yumis.fight.modules.presentation.api.ModText;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;

public final class FabricatorBlock extends Block {
    public static final MapCodec<FabricatorBlock> CODEC=simpleCodec(FabricatorBlock::new);
    public FabricatorBlock(BlockBehaviour.Properties p){
        super(p);
    }
    @Override public MapCodec<? extends Block> codec(){
        return CODEC;
    }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return Block.box(1,0,1,15,15,15);}
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return getShape(state,level,pos,context);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(!level.isClientSide())player.openMenu(new SimpleMenuProvider((id,inv,p)->new FabricatorMenu(id,inv,pos),ModText.of("装备制作器")));
        return InteractionResult.SUCCESS;
    }
}
