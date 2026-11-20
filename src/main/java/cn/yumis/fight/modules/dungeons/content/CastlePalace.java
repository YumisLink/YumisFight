package cn.yumis.fight.modules.dungeons.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Spatial contract for the flooded tower, sky sea, bridge and palace island. */
public final class CastlePalace {
    public static final String ID = "castle_palace";
    public static final String VERSION_PREFIX = "PALACE_SKY_SEA_";
    public static final String VERSION = VERSION_PREFIX + "3";
    public static final int DECK = 6, BRIDGE = 7, COURT = 8, BOSS = 9;
    public static final int SEA_LEVEL = 192;
    public static final DungeonLayouts.Bounds BOUNDS = new DungeonLayouts.Bounds(-128, 3, -128, 127, 169, 127);
    private static final int TOWER_Z = -78;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_KNOWN_SHAPE;
    private static final String MAP = "/data/yumis_dungeon_and_fight/dungeon_map/castle_palace.nbt";
    private static final List<String> ROOMS = List.of("前庭", "斧兵厅", "傀儡厅", "浮游回廊", "王宫长廊", "指挥厅",
        "出水平台", "跨海战台", "宫前广场", "城主大厅");

    public static String roomName(int room) {
        return ROOMS.get(Math.clamp(room, 0, BOSS));
    }

    public static boolean submerged(int room) {
        return room >= 0 && room < DECK;
    }

    public static int floorY(int room) {
        if (submerged(room)) return 97 + 16 * room;
        return room == BOSS ? 203 : 197;
    }

    public static Vec3 entry(int cell) {
        return worldPoint(cell, -26.5, floorY(0), -67.5);
    }

    public static Vec3 worldPoint(int cell, double x, double y, double z) {
        return new Vec3(cell * 512 + x, y, z);
    }

    public static boolean keepsChunk(int x, int z) {
        return x >= -3 && x <= 2 && z >= -7 && z <= -3
            || x >= -1 && x <= 0 && z >= -4 && z <= 2
            || x >= -4 && x <= 3 && z >= 2 && z <= 7;
    }

    public static boolean contains(int cell, int room, Vec3 point) {
        double x = point.x - cell * 512;
        double z = point.z;
        double y = point.y;
        int floor = floorY(room);
        if (y < floor - .3) return false;
        if (submerged(room)) {
            if (y >= floor + 9.4) return false;
            double r = Math.hypot(x, z - TOWER_Z);
            return room == 0 && r <= 21.4
                || z >= TOWER_Z + 7.9 && r >= 22.7 && r <= 31.1
                || room == 0 && x >= -15 && x <= -7 && z >= -64 && z <= -50;
        }
        return switch (room) {
            case DECK -> y < 208 && x >= -35 && x <= 4.9 && z >= -64.9 && z <= -55.2;
            case BRIDGE -> y < 209 && Math.abs(x) <= 11.6 && z >= -25.9 && z <= -4.1;
            case COURT -> y < 210 && Math.abs(x) <= 43 && z >= 32.1 && z <= 46.9;
            case BOSS -> y < 225 && Math.abs(x) <= 26 && z >= 47.9 && z <= 95.9;
            default -> false;
        };
    }

    public static boolean reached(int cell, int room, Vec3 point) {
        if (!contains(cell, room, point)) return false;
        double x = point.x - cell * 512;
        if (submerged(room)) return x < -19 && point.z <= -57;
        return switch (room) {
            case DECK -> x <= -20;
            case BRIDGE -> point.z < -18;
            case COURT -> point.z < 42 && Math.abs(x) < 18;
            case BOSS -> point.z < 61 && Math.abs(x) < 10;
            default -> false;
        };
    }

    public static Vec3 inside(int cell, int room, Vec3 point) {
        double x = point.x - cell * 512;
        double z = point.z;
        int floor = floorY(room);
        if (submerged(room)) {
            double y = Math.clamp(point.y, floor + .12, floor + 7.65);
            double r = Math.hypot(x, z - TOWER_Z);
            if (room == 0 && r < 22.5) {
                double scale = r <= 18.5 ? 1 : 18.5 / r;
                return worldPoint(cell, x * scale, y, TOWER_Z + (z - TOWER_Z) * scale);
            }
            double angle = Math.clamp(Math.atan2(z - TOWER_Z, x), .36, Math.PI - .36);
            return ringPoint(cell, angle, Math.clamp(r, 24.6, 29.3), y);
        }
        return switch (room) {
            case DECK -> worldPoint(cell, Math.clamp(x, -33, 3), floor, Math.clamp(z, -63, -57));
            case BRIDGE -> worldPoint(cell, Math.clamp(x, -10, 10), floor, Math.clamp(z, -24, -6));
            case COURT -> worldPoint(cell, Math.clamp(x, -41, 41), Math.clamp(point.y, floor, 203), Math.clamp(z, 33, 46));
            case BOSS -> worldPoint(cell, Math.clamp(x, -24, 24), floor, Math.clamp(z, 50, 94));
            default -> point;
        };
    }

    /** Short tangents follow the circular corridor rather than cutting across the atrium wall. */
    public static Vec3 swimStep(int cell, int room, Vec3 from, Vec3 desired) {
        double x = from.x - cell * 512;
        double z = from.z - TOWER_Z;
        double toX = desired.x - cell * 512;
        double toZ = desired.z - TOWER_Z;
        boolean fromHall = Math.hypot(x, z) < 22.6;
        boolean toHall = Math.hypot(toX, toZ) < 22.6;
        if (room == 0 && fromHall != toHall) {
            double doorwayY = Math.min(desired.y, floorY(room) + 4);
            return worldPoint(cell, -11.5, doorwayY, TOWER_Z + (fromHall ? 24 : 16.5));
        }
        if (room == 0 && fromHall) return desired;
        double angle = Math.atan2(z, x);
        double turn = Math.atan2(toZ, toX) - angle;
        if (Math.abs(turn) <= .17) return desired;
        return ringPoint(cell, angle + Math.copySign(.17, turn), 27, desired.y);
    }

    public static Vec3 ringPoint(int cell, double angle, double radius, double y) {
        return worldPoint(cell, Math.cos(angle) * radius, y, TOWER_Z + Math.sin(angle) * radius);
    }

    public static void build(ServerLevel world, BlockPos origin) {
        CompoundTag data;
        try (InputStream source = CastlePalace.class.getResourceAsStream(MAP)) {
            if (source == null) throw new IllegalStateException("缺少城主宫殿结构：" + MAP);
            data = NbtIo.readCompressed(source, NbtAccounter.unlimitedHeap());
        } catch (IOException failure) {
            throw new IllegalStateException("读取城主宫殿结构失败", failure);
        }
        var registry = world.registryAccess().lookupOrThrow(Registries.BLOCK);
        var palette = new ArrayList<BlockState>();
        for (var entry : data.getListOrEmpty("palette"))
            palette.add(NbtUtils.readBlockState(registry, (CompoundTag) entry));
        int[] runs = data.getIntArray("runs").orElseThrow();
        if (runs.length % 5 != 0 || palette.isEmpty()) throw new IllegalStateException("城主宫殿结构数据无效");
        var cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i < runs.length; i += 5) {
            int x = runs[i], y = runs[i + 1], z = runs[i + 2], width = runs[i + 3];
            BlockState state = palette.get(runs[i + 4]);
            if (width < 1 || x < BOUNDS.minX() || x + width - 1 > BOUNDS.maxX()
                || y < BOUNDS.minY() || y > BOUNDS.maxY() || z < BOUNDS.minZ() || z > BOUNDS.maxZ())
                throw new IllegalStateException("城主宫殿结构超出场次范围");
            for (int dx = 0; dx < width; dx++)
                world.setBlock(cursor.set(origin.getX() + x + dx, origin.getY() + y, origin.getZ() + z), state, FLAGS);
        }
    }

    public static void gate(ServerLevel world, BlockPos origin, int stage, boolean open) {
        if (submerged(stage)) {
            BlockState state = open ? Blocks.WATER.defaultBlockState() : Blocks.IRON_BARS.defaultBlockState()
                .setValue(BlockStateProperties.NORTH, true).setValue(BlockStateProperties.SOUTH, true)
                .setValue(BlockStateProperties.WATERLOGGED, true);
            for (int z = -69; z <= -66; z++)
                for (int y = floorY(stage); y < floorY(stage) + 5; y++)
                    world.setBlock(origin.offset(31, y - 80, z), state, FLAGS);
            return;
        }
        BlockState state = open ? Blocks.AIR.defaultBlockState() : Blocks.IRON_BARS.defaultBlockState()
            .setValue(BlockStateProperties.EAST, true).setValue(BlockStateProperties.WEST, true);
        if (stage == DECK || stage == BRIDGE) {
            int z = stage == DECK ? -54 : -2;
            for (int x = -4; x <= 4; x++)
                for (int y = 197; y <= 202; y++)
                    world.setBlock(origin.offset(x, y - 80, z), state, FLAGS);
        } else if (stage == COURT) {
            for (int x = -9; x <= 9; x++) {
                int height = 21 - Math.max(0, Math.abs(x) - 3);
                for (int i = 0; i < height; i++)
                    world.setBlock(origin.offset(x, floorY(BOSS) - 80 + i, 47), state, FLAGS);
            }
        }
    }

    private CastlePalace() {}
}
