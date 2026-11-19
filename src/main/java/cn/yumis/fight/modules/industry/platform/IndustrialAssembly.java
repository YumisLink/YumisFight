package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.modules.industry.api.DeviceType;
import cn.yumis.fight.modules.industry.api.IndustrialTechnology;
import com.google.gson.JsonArray;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The solid cells and reserved space of the two factory-built multiblocks. */
public final class IndustrialAssembly {
    static final int PLACEMENT_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    record Part(int index, BlockPos offset, VoxelShape[] shapes) {
        BlockPos position(BlockPos base, Direction facing) {
            return base.relative(facing.getClockWise(), offset.getX())
                .relative(facing.getOpposite(), offset.getZ()).above(offset.getY());
        }

        BlockPos controller(BlockPos pos, Direction facing) {
            return pos.relative(facing.getClockWise(), -offset.getX())
                .relative(facing.getOpposite(), -offset.getZ()).below(offset.getY());
        }

        BlockState state(Direction facing) {
            return IndustrialContent.ASSEMBLY_PART.get().defaultBlockState()
                .setValue(IndustrialAssemblyPartBlock.PART, index).setValue(MachineBlock.FACING, facing);
        }
    }

    private static final List<Part> PARTS = new ArrayList<>();
    private static final IndustrialAssembly BUDDY = new IndustrialAssembly(DeviceType.BUDDY_GENERATOR);
    private static final IndustrialAssembly DRILL = new IndustrialAssembly(DeviceType.OIL_DRILL);
    private static final Map<DeviceType, IndustrialAssembly> DEVICES = Map.of(
        DeviceType.BUDDY_SHELL, BUDDY, DeviceType.BUDDY_GENERATOR, BUDDY, DeviceType.OIL_DRILL, DRILL);

    private final BlockPos size;
    private final BlockPos anchor;
    private final List<Part> parts;

    private IndustrialAssembly(DeviceType device) {
        var definition = IndustrialTechnology.spec(device.id).getAsJsonObject("assembly");
        size = position(definition.getAsJsonArray("size"));
        anchor = position(definition.getAsJsonArray("anchor"));
        List<Part> cells = new ArrayList<>();
        for (var entry : definition.getAsJsonArray("parts")) {
            var cell = entry.getAsJsonObject();
            BlockPos offset = position(cell.getAsJsonArray("cell")).subtract(anchor);
            var part = new Part(PARTS.size(), offset, IndustrialShapes.rotations(cell.getAsJsonArray("boxes")));
            cells.add(part);
            PARTS.add(part);
        }
        parts = List.copyOf(cells);
    }

    private static BlockPos position(JsonArray vector) {
        return new BlockPos(vector.get(0).getAsInt(), vector.get(1).getAsInt(), vector.get(2).getAsInt());
    }

    static IndustrialAssembly of(DeviceType type) { return DEVICES.get(type); }
    static int partCount() { return PARTS.size(); }
    static Part part(int index) { return PARTS.get(index); }
    boolean owns(Part part) { return parts.contains(part); }
    int layerSize() { return Math.max(size.getX(), Math.max(size.getY(), size.getZ())); }

    String sizeText() {
        return size.getX() + "格宽、" + size.getZ() + "格深、" + size.getY() + "格高";
    }

    boolean canPlace(BlockPlaceContext context, Direction facing) {
        Level level = context.getLevel();
        BlockPos base = context.getClickedPos();
        var player = context.getPlayer();
        for (Part part : parts) {
            BlockPos pos = part.position(base, facing);
            if (!level.isInValidBounds(pos) || !level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
                || !level.getBlockState(pos).isAir()
                || player != null && (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand()))
                || !level.isUnobstructed(part.state(facing), pos, CollisionContext.empty())) return false;
        }
        return true;
    }

    boolean assemble(ServerLevel level, BlockPos base, Direction facing) {
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            BlockPos pos = part.position(base, facing);
            if (!level.getBlockState(pos).isAir()
                || !level.setBlock(pos, part.state(facing), PLACEMENT_FLAGS)) {
                for (Part placed : parts.subList(0, i)) {
                    level.setBlock(placed.position(base, facing), Blocks.AIR.defaultBlockState(), PLACEMENT_FLAGS);
                }
                return false;
            }
        }
        return true;
    }

    void removeParts(ServerLevel level, BlockPos base, Direction facing) {
        for (Part part : parts) {
            BlockPos pos = part.position(base, facing);
            if (level.getBlockState(pos) == part.state(facing)) level.removeBlock(pos, false);
        }
    }

    List<IndustrialStructures.Cell> cells(MachineBlockEntity be) {
        BlockPos base = be.getBlockPos();
        Direction facing = be.getBlockState().getValue(MachineBlock.FACING);
        List<IndustrialStructures.Cell> cells = new ArrayList<>(parts.size() + 1);
        cells.add(new IndustrialStructures.Cell(base, anchor.getX(), anchor.getY(), anchor.getZ(), false, true));
        for (Part part : parts) {
            BlockPos local = part.offset.offset(anchor);
            cells.add(new IndustrialStructures.Cell(part.position(base, facing), local.getX(), local.getY(),
                local.getZ(), part.shapes[0].isEmpty(), false));
        }
        return cells;
    }

    List<BlockPos> missing(MachineBlockEntity be) {
        Direction facing = be.getBlockState().getValue(MachineBlock.FACING);
        List<BlockPos> missing = new ArrayList<>();
        for (Part part : parts) {
            BlockPos pos = part.position(be.getBlockPos(), facing);
            if (!be.getLevel().hasChunkAt(pos) || be.getLevel().getBlockState(pos) != part.state(facing)) missing.add(pos);
        }
        return missing;
    }

    public static AABB renderBounds(MachineBlockEntity be) {
        IndustrialAssembly assembly = of(be.device());
        if (assembly == null) return null;
        BlockPos base = be.getBlockPos();
        Direction facing = be.getBlockState().getValue(MachineBlock.FACING);
        AABB bounds = new AABB(base);
        for (Part part : assembly.parts) bounds = bounds.minmax(new AABB(part.position(base, facing)));
        return bounds;
    }
}
