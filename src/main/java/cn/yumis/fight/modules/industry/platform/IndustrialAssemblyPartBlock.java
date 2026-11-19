package cn.yumis.fight.modules.industry.platform;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/** A reserved machine cell. The controller alone owns the inventory, menu and drop. */
final class IndustrialAssemblyPartBlock extends Block {
    static final IntegerProperty PART = IntegerProperty.create("part", 0, IndustrialAssembly.partCount() - 1);

    IndustrialAssemblyPartBlock(Properties properties) { super(properties); }

    @Override protected MapCodec<? extends Block> codec() { return simpleCodec(IndustrialAssemblyPartBlock::new); }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, MachineBlock.FACING);
    }

    private static BlockPos controller(BlockGetter level, BlockPos pos, BlockState state) {
        var part = IndustrialAssembly.part(state.getValue(PART));
        var facing = state.getValue(MachineBlock.FACING);
        BlockPos base = part.controller(pos, facing);
        BlockState controller = level.getBlockState(base);
        if (controller.getBlock() instanceof MachineBlock machine && controller.getValue(MachineBlock.FACING) == facing) {
            var assembly = IndustrialAssembly.of(machine.initial.device());
            if (assembly != null && assembly.owns(part)) return base;
        }
        return pos;
    }

    static BlockPos base(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof IndustrialAssemblyPartBlock ? controller(level, pos, state) : pos;
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return IndustrialShapes.facing(IndustrialAssembly.part(state.getValue(PART)).shapes(), state.getValue(MachineBlock.FACING));
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) { return List.of(); }

    @Override public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData, Player player) {
        BlockPos base = controller(level, pos, state);
        return base.equals(pos) ? ItemStack.EMPTY : new ItemStack(level.getBlockState(base).getBlock().asItem());
    }

    @Override protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
        if (level.getBlockState(pos).is(this) || level.restoringBlockSnapshots) return;
        BlockPos base = controller(level, pos, state);
        if (!base.equals(pos)) level.destroyBlock(base, true);
    }

    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel server && player.isCreative()) {
            BlockPos base = controller(server, pos, state);
            if (!base.equals(pos)) server.destroyBlock(base, false, player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos base = controller(level, pos, state);
        return base.equals(pos) ? InteractionResult.PASS : level.getBlockState(base).useWithoutItem(level, player, hit.withPosition(base));
    }

    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                    Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos base = controller(level, pos, state);
        return base.equals(pos) ? InteractionResult.PASS : level.getBlockState(base).useItemOn(stack, level, player, hand, hit.withPosition(base));
    }
}
