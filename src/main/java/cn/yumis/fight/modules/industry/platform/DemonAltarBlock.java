package cn.yumis.fight.modules.industry.platform;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class DemonAltarBlock extends BaseEntityBlock {
    public static final String ID = "demon_altar";
    public static final double SURFACE = 12.0 / 16;

    private static final MapCodec<DemonAltarBlock> CODEC = simpleCodec(DemonAltarBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(
        box(1, 0, 1, 15, 2, 15), box(2, 2, 2, 14, 4, 14), box(4, 4, 4, 12, 7, 12),
        box(3, 7, 3, 13, 9, 13), box(1, 9, 1, 15, 11, 15), box(2, 11, 2, 14, 12, 14)
    );

    public DemonAltarBlock(Properties properties) {
        super(properties);
    }

    @Override protected MapCodec<DemonAltarBlock> codec() { return CODEC; }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DemonAltarBlockEntity(pos, state);
    }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, IndustrialContent.DEMON_ALTAR_BE.get(), DemonAltarBlockEntity::tick);
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                          InsideBlockEffectApplier effects, boolean precise) {
        if (!level.isClientSide() && entity instanceof ItemEntity drop && drop.onGround()
            && (drop.tickCount + drop.getId()) % 5 == 0 && Math.abs(drop.getY() - pos.getY() - SURFACE) < .04
            && drop.getX() >= pos.getX() + .125 && drop.getX() <= pos.getX() + .875
            && drop.getZ() >= pos.getZ() + .125 && drop.getZ() <= pos.getZ() + .875
            && level.getBlockEntity(pos) instanceof DemonAltarBlockEntity altar) {
            altar.acceptDroppedOffering(drop);
        }
    }

    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                     Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (stack.getItem() instanceof ManaWand) return InteractionResult.PASS;
        boolean top = hit.getDirection() == Direction.UP;
        if (!top && IndustrialContent.crystalTier(stack) == 0 && !cn.yumis.fight.modules.industry.api.IndustrialRituals.token(stack)) return InteractionResult.PASS;
        return interact(level, pos, player, hand, top);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return interact(level, pos, player, InteractionHand.MAIN_HAND, hit.getDirection() == Direction.UP);
    }

    private static InteractionResult interact(Level level, BlockPos pos, Player player, InteractionHand hand, boolean top) {
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof DemonAltarBlockEntity altar) {
            altar.interact(server, hand, top);
        }
        return InteractionResult.SUCCESS;
    }
}
