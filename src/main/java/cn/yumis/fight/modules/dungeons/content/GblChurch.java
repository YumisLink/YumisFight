package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.GblEnemyKind;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import static cn.yumis.fight.modules.dungeons.api.GblEnemyKind.*;

public final class GblChurch {
    public static final String ID = "gbl_church";
    public static final String VERSION_PREFIX = "GBL_WHALE_";
    public static final String VERSION = VERSION_PREFIX + "3";
    public static final DungeonLayouts.Bounds BOUNDS = new DungeonLayouts.Bounds(-110, -64, -77, 110, 56, 253);
    public static final DungeonLayouts.Bounds ACTIVE_BOUNDS = new DungeonLayouts.Bounds(-36, -5, -11, 35, 24, 234);
    private static final String SCENERY_TAG = "ydnf_gbl_church_scenery";
    private static final Identifier SKY_CLOUDS = Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight", "dungeons/gbl_church/sky_clouds");
    private static final BlockPos SKY_CLOUD_ORIGIN = new BlockPos(BOUNDS.minX(), -12, BOUNDS.minZ());

    private static final List<BlockPos> TILE_ORIGINS = List.of(
        new BlockPos(-53, -42, -68), new BlockPos(-34, -37, -49), new BlockPos(-101, -44, -21),
        new BlockPos(-43, -50, 7), new BlockPos(-104, -56, 35), new BlockPos(-84, -59, 63),
        new BlockPos(-55, -60, 91), new BlockPos(-109, -59, 119), new BlockPos(-106, -55, 147),
        new BlockPos(-95, -49, 175), new BlockPos(-85, -47, 203), new BlockPos(-55, -47, 231)
    );

    private record Passage(int x, int first, int last, int startHeight, int step, int... stairs) {
        double surface(double z) {
            int level = startHeight;
            for (int stair : stairs) {
                if (z < stair) break;
                if (z < stair + 1) {
                    boolean high = step > 0 ? z - stair >= .5 : z - stair < .5;
                    return level + (step < 0 ? -1 : 0) + (high ? 1 : .5);
                }
                level += step;
            }
            return level;
        }
    }

    private static final List<Passage> PASSAGES = List.of(
        new Passage(-8, 30, 41, 80, 1, 33, 34, 35),
        new Passage(0, 77, 84, 83, -1, 78, 79, 80, 82, 83),
        new Passage(0, 118, 132, 78, 1, 120, 121, 122, 126, 127, 128, 130, 131),
        new Passage(10, 168, 190, 86, 1, 175, 176, 177, 183, 184)
    );

    public record Spawn(GblEnemyKind kind, int x, int y, int z) {
        public boolean boss() { return kind == ASOR; }
        public boolean elite() { return kind == FALLEN_PRIEST; }
    }

    public static final List<Spawn> SPAWNS = List.of(
        new Spawn(CULTIST, -5, 80, 11), new Spawn(CULTIST, 3, 80, 12), new Spawn(CULTIST, 8, 80, 15),
        new Spawn(FEMALE_CULTIST, 0, 80, 19), new Spawn(FEMALE_CULTIST, 7, 80, 21), new Spawn(DRAGON_CANNON, 3, 80, 24),
        new Spawn(CULTIST, -12, 83, 50), new Spawn(CULTIST, -5, 83, 54), new Spawn(FEMALE_CULTIST, 1, 83, 57),
        new Spawn(YASHA, -12, 83, 58), new Spawn(YASHA, -7, 83, 63), new Spawn(YASHA, -5, 83, 69),
        new Spawn(CULTIST, 4, 78, 94), new Spawn(FEMALE_CULTIST, 14, 78, 93), new Spawn(YASHA, 5, 78, 100),
        new Spawn(YASHA, 11, 78, 104), new Spawn(YASHA, 5, 78, 107), new Spawn(FALLEN_PRIEST, 12, 78, 110),
        new Spawn(CULTIST, -1, 86, 147), new Spawn(FEMALE_CULTIST, -4, 86, 156), new Spawn(FEMALE_CULTIST, 15, 86, 153),
        new Spawn(DRAGON_CANNON, 1, 86, 144), new Spawn(DRAGON_CANNON, 7, 86, 146), new Spawn(DRAGON_CANNON, 15, 86, 157),
        new Spawn(CULTIST, 1, 91, 202), new Spawn(CULTIST, 5, 91, 203), new Spawn(FEMALE_CULTIST, 13, 91, 200),
        new Spawn(FEMALE_CULTIST, 17, 91, 203), new Spawn(YASHA, 1, 91, 215), new Spawn(FALLEN_PRIEST, 18, 91, 214),
        new Spawn(ASOR, 10, 91, 216)
    );

    private GblChurch() {}

    public static void build(ServerLevel world, BlockPos origin) {
        for (int index = 0; index < TILE_ORIGINS.size(); index++) {
            String tile = index < 10 ? "0" + index : Integer.toString(index);
            var id = Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight", "dungeons/gbl_church/back_" + tile);
            var structure = world.getStructureManager().get(id)
                .orElseThrow(() -> new IllegalStateException("缺少 GBL 鲸背结构：" + id));
            BlockPos at = origin.offset(TILE_ORIGINS.get(index));
            var settings = new StructurePlaceSettings().setIgnoreEntities(false).setKnownShape(true);
            if (!structure.placeInWorld(world, at, at, settings, world.getRandom(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS)) {
                throw new IllegalStateException("无法放置 GBL 鲸背结构：" + id);
            }
        }
        placeSkyClouds(world, origin);
    }

    private static void placeSkyClouds(ServerLevel world, BlockPos origin) {
        var structure = world.getStructureManager().get(SKY_CLOUDS)
            .orElseThrow(() -> new IllegalStateException("缺少 GBL 云层结构：" + SKY_CLOUDS));
        var settings = new StructurePlaceSettings();
        for (var block : structure.filterBlocks(origin.offset(SKY_CLOUD_ORIGIN), settings, Blocks.STAINED_GLASS.white())) {
            BlockPos pos = block.pos();
            if (world.getBlockState(pos).isAir() && !world.setBlock(pos, block.state(), Block.UPDATE_CLIENTS)) {
                throw new IllegalStateException("无法放置 GBL 云层方块：" + pos);
            }
        }
    }

    public static void clearScenery(ServerLevel world, BlockPos origin) {
        var bounds = AABB.encapsulatingFullBlocks(origin.offset(BOUNDS.minX(), BOUNDS.minY(), BOUNDS.minZ()),
            origin.offset(BOUNDS.maxX(), BOUNDS.maxY(), BOUNDS.maxZ()));
        for (Entity entity : world.getEntities((Entity)null, bounds, e -> e.entityTags().contains(SCENERY_TAG))) {
            entity.discard();
        }
    }

    public static Vec3 entry(int cell) {
        return new Vec3(cell * 512 - 30.5, 80, -6.5);
    }

    public static String area(double z) {
        if (z < 36) return "神殿外围";
        if (z < 81) return "树精丛林";
        if (z < 125) return "炼狱";
        if (z < 181) return "极昼";
        return "GBL 美神殿";
    }

    private static double surface(double z) {
        for (Passage passage : PASSAGES) {
            if (z >= passage.first && z < passage.last + 1) return passage.surface(z);
        }
        if (z < 42) return 80;
        if (z < 85) return 83;
        if (z < 133) return 78;
        return z < 191 ? 86 : 91;
    }

    public static Vec3 route(int cell, Vec3 from, Vec3 target) {
        boolean forward = target.z > from.z;
        if (Math.abs(target.z - from.z) < 8) return target;
        for (Passage passage : forward ? PASSAGES : PASSAGES.reversed()) {
            if (forward && (from.z >= passage.last + 1 || target.z < passage.first)) continue;
            if (!forward && (from.z < passage.first || target.z >= passage.last + 1)) continue;
            double next = forward
                ? (from.z < passage.first ? passage.first + .5 : Math.min(passage.last + 1.5, from.z + 8))
                : (from.z >= passage.last + 1 ? passage.last + .5 : Math.max(passage.first - .5, from.z - 8));
            return new Vec3(cell * 512 + passage.x + .5, Math.ceil(surface(next)), next);
        }
        return target;
    }

    public static Vec3 ground(ServerLevel world, Vec3 point, Vec3 fallback) {
        double foot = surface(point.z);
        var pos = BlockPos.containing(point.x, foot - .01, point.z);
        if (!world.hasChunkAt(pos) || world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()) return fallback;
        var standing = new AABB(point.x - .25, foot + .001, point.z - .25,
            point.x + .25, foot + 1.7, point.z + .25);
        return world.getBlockCollisions(null, standing).iterator().hasNext() ? fallback : new Vec3(point.x, foot, point.z);
    }
}
