package cn.yumis.fight.modules.dungeons.content;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Four level rooms with the same corridor gates, shared by the enclosed dungeon layouts. */
final class TempleRoomSequence {
    static final int SPACING = 40;
    static final int MAX_Z = 166;

    private TempleRoomSequence() {}

    static boolean contains(int cell, int room, double x, double z) {
        int start = room * SPACING;
        return Math.abs(x - cell * 512) <= (room == 3 ? 19 : 15)
            && z >= start + 1 && z <= start + (room == 3 ? 39 : 31);
    }

    static void gate(ServerLevel world, BlockPos origin, int room, boolean open) {
        int z = room * SPACING + 32;
        var floor = open ? Blocks.AIR.defaultBlockState() : Blocks.IRON_BARS.defaultBlockState()
            .setValue(BlockStateProperties.EAST, true).setValue(BlockStateProperties.WEST, true);
        for (int x = -3; x <= 3; x++) {
            for (int y = 0; y < 19; y++) {
                world.setBlock(origin.offset(x, y, z), y < 6 ? floor : Blocks.BARRIER.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }
}
