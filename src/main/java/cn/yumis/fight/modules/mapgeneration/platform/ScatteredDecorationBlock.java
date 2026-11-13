package cn.yumis.fight.modules.mapgeneration.platform;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Persistent scatter layouts; empty hand cycles layout, sneak + empty hand rotates. */
public final class ScatteredDecorationBlock extends DecorationBlock {
    public static final IntegerProperty VARIANT=IntegerProperty.create("variant",0,DecorationDefinitions.SCATTER_LAYOUTS-1);
    public static final MapCodec<ScatteredDecorationBlock> CODEC=RecordCodecBuilder.mapCodec(instance->instance.group(
            Codec.STRING.fieldOf("decoration").forGetter(block->block.decoration),propertiesCodec()).apply(instance,ScatteredDecorationBlock::new));
    public ScatteredDecorationBlock(String decoration,Properties properties){super(decoration,properties);registerDefaultState(defaultBlockState().setValue(VARIANT,0));}
    @Override protected MapCodec<ScatteredDecorationBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(VARIANT);}
    @Override protected int variant(BlockState state){return state.getValue(VARIANT);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return super.getStateForPlacement(context).setValue(VARIANT,Math.floorMod(Mth.getSeed(context.getClickedPos()),DecorationDefinitions.SCATTER_LAYOUTS));}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(player.isShiftKeyDown())return super.useWithoutItem(state,level,pos,player,hit);
        if(!player.mayBuild())return InteractionResult.PASS;
        if(!level.isClientSide())level.setBlock(pos,state.cycle(VARIANT),Block.UPDATE_ALL);
        return InteractionResult.SUCCESS;
    }
}
