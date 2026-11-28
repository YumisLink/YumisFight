package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.Dungeon;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;

/** Circular arenas (combat radius 32) covering all 6 Lv.10-35 dungeon themes plus Region 5 Golden Palace. */
public final class TreasureArena {
    public static final double COMBAT_RADIUS = 31.5;
    public static final double OVERWORLD_RADIUS = 60.0;

    public static Vec3 center(int cell) {
        return new Vec3(cell * 512 + 0.5, 80.0, 0.5);
    }

    public static boolean contains(int cell, Vec3 pos) {
        double dx = pos.x - (cell * 512 + 0.5);
        double dz = pos.z - 0.5;
        return dx * dx + dz * dz <= 33.0 * 33.0 && pos.y >= 77.0 && pos.y <= 104.0;
    }

    public static Vec3 inside(int cell, Vec3 pos) {
        double cx = cell * 512 + 0.5, cz = 0.5;
        double dx = pos.x - cx, dz = pos.z - cz;
        double dist = Math.hypot(dx, dz);
        double max = COMBAT_RADIUS;
        if (dist > max && dist > 1e-6) {
            dx = dx / dist * max;
            dz = dz / dist * max;
        }
        return new Vec3(cx + dx, 80.0, cz + dz);
    }

    public static boolean containsFor(Dungeon d, Vec3 pos) {
        if (d == null || d.treasure == null) return false;
        if (d.treasure.region == 0) {
            double dx = pos.x - (d.treasure.mapX + 0.5);
            double dz = pos.z - (d.treasure.mapZ + 0.5);
            return dx * dx + dz * dz <= OVERWORLD_RADIUS * OVERWORLD_RADIUS;
        }
        return contains(d.cell, pos);
    }

    public static Vec3 insideFor(Dungeon d, Vec3 pos) {
        if (d == null || d.treasure == null) return pos;
        if (d.treasure.region == 0) {
            double cx = d.treasure.mapX + 0.5, cz = d.treasure.mapZ + 0.5;
            double dx = pos.x - cx, dz = pos.z - cz;
            double dist = Math.hypot(dx, dz);
            double max = OVERWORLD_RADIUS - 2.0;
            if (dist > max && dist > 1e-6) {
                dx = dx / dist * max;
                dz = dz / dist * max;
            }
            return new Vec3(cx + dx, pos.y, cz + dz);
        }
        return inside(d.cell, pos);
    }

    public static void build(ServerLevel world, BlockPos o, String theme) {
        if ("palace".equals(theme) || "forest".equals(theme) || "tower".equals(theme)) {
            buildClassic(world, o, theme);
            return;
        }
        switch (theme) {
            case "palace_sky" -> buildThemed(world, o,
                Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BRICKS, Blocks.GOLD_BLOCK,
                Blocks.CHISELED_QUARTZ_BLOCK, Blocks.QUARTZ_PILLAR, Blocks.SEA_LANTERN, Blocks.PRISMARINE_BRICKS,
                Blocks.CHISELED_QUARTZ_BLOCK, true);
            case "gbl" -> buildThemed(world, o,
                Blocks.DARK_PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.PURPUR_BLOCK,
                Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.PURPUR_PILLAR, Blocks.SEA_LANTERN, Blocks.AMETHYST_BLOCK,
                Blocks.PURPUR_PILLAR, false);
            case "forbidden" -> buildThemed(world, o,
                Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.DEEPSLATE_TILES, Blocks.CRYING_OBSIDIAN,
                Blocks.BLACKSTONE, Blocks.OBSIDIAN, Blocks.SHROOMLIGHT, Blocks.CRIMSON_NYLIUM,
                Blocks.CHISELED_POLISHED_BLACKSTONE, false);
            case "dark_city" -> buildThemed(world, o,
                Blocks.DEEPSLATE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.POLISHED_DEEPSLATE,
                Blocks.COBBLED_DEEPSLATE, Blocks.BONE_BLOCK, Blocks.SOUL_LANTERN, Blocks.SOUL_SOIL,
                Blocks.CHISELED_DEEPSLATE, false);
            default -> buildClassic(world, o, "forest");
        }
    }

    private static void buildThemed(ServerLevel world, BlockPos o,
                                    Block floorPrimary, Block floorAlt, Block floorTrim,
                                    Block rimWall, Block pillar, Block light, Block accent,
                                    Block portalFrame, boolean skyArch) {
        for (int x = -43; x <= 43; x++) {
            for (int z = -43; z <= 43; z++) {
                int r = x * x + z * z;
                if (r > 43 * 43) continue;
                for (int y = -2; y <= 22; y++) put(world, o, x, y, z, Blocks.AIR);
                put(world, o, x, -2, z, Blocks.DEEPSLATE_BRICKS);
                Block floor = (Math.abs(x) <= 1 || Math.abs(z) <= 1 || Math.abs(Math.sqrt(r) - 16) < 0.65)
                    ? floorTrim
                    : ((Math.floorMod(x, 6) == 0 || Math.floorMod(z, 6) == 0) ? floorAlt : floorPrimary);
                if (r < 25 && (x == 0 || z == 0)) floor = light;
                put(world, o, x, -1, z, floor);
                if (r >= 33 * 33) {
                    for (int y = 0; y <= 16; y++) {
                        put(world, o, x, y, z, y < 2 ? rimWall : Blocks.BARRIER);
                    }
                }
                put(world, o, x, 22, z, Blocks.BARRIER);
            }
        }
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            int x = (int) Math.round(Math.cos(a) * 36);
            int z = (int) Math.round(Math.sin(a) * 36);
            for (int y = 0; y < 12; y++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block b = (y == 0 || y == 11) ? rimWall : (dx == 0 && dz == 0 && y % 4 == 2 ? accent : pillar);
                        put(world, o, x + dx, y, z + dz, b);
                    }
                }
            }
            put(world, o, x, 12, z, light);
            for (int step = -2; step <= 2; step++) {
                put(world, o, x + step, 13, z, skyArch ? floorTrim : rimWall);
            }
        }
        for (int side : new int[]{-1, 1}) {
            for (int y = 0; y <= 6; y++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (y == 6 || Math.abs(dz) == 3) put(world, o, side * 29, y, dz, portalFrame);
                }
            }
            put(world, o, side * 29, 0, 0, light);
        }
    }

    /** Original three circular arenas (forest, tower, golden palace) kept verbatim. */
    private static void buildClassic(ServerLevel world, BlockPos o, String theme) {
        boolean gold = theme.equals("palace"), forest = theme.equals("forest");
        for (int x = -43; x <= 43; x++) for (int z = -43; z <= 43; z++) {
            int r = x * x + z * z;
            if (r > 43 * 43) continue;
            for (int y = -2; y <= 22; y++) put(world, o, x, y, z, Blocks.AIR);
            put(world, o, x, -2, z, Blocks.STONE);
            Block floor = forest ? (r % 13 == 0 ? Blocks.COARSE_DIRT : Blocks.MOSS_BLOCK)
                : gold ? (Math.abs(x) <= 2 || Math.abs(z) <= 2 ? Blocks.GOLD_BLOCK : ((x + z) % 8 == 0 ? Blocks.CHISELED_QUARTZ_BLOCK : Blocks.SMOOTH_QUARTZ))
                : ((x % 8 == 0 || z % 8 == 0) ? Blocks.POLISHED_DIORITE : Blocks.SMOOTH_QUARTZ);
            put(world, o, x, -1, z, floor);
            if (r >= 33 * 33) {
                for (int y = 0; y <= 16; y++) put(world, o, x, y, z, y < 2 ? (forest ? Blocks.MOSSY_STONE_BRICKS : gold ? Blocks.GOLD_BLOCK : Blocks.STONE_BRICKS) : Blocks.BARRIER);
            }
            put(world, o, x, 22, z, Blocks.BARRIER);
        }
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            int x = (int) Math.round(Math.cos(a) * 36), z = (int) Math.round(Math.sin(a) * 36);
            for (int y = 0; y < 12; y++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                put(world, o, x + dx, y, z + dz, forest ? Blocks.OAK_LOG : gold && (y == 0 || y == 10) ? Blocks.GOLD_BLOCK : Blocks.QUARTZ_PILLAR);
            if (forest) {
                for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) for (int y = 10; y <= 15; y++)
                    if (dx * dx + dz * dz + (y - 12) * (y - 12) < 22)
                        world.setBlock(o.offset(x + dx, y, z + dz), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true), 2);
            } else {
                put(world, o, x, 12, z, gold ? Blocks.GLOWSTONE : Blocks.SEA_LANTERN);
                for (int step = -3; step <= 3; step++) put(world, o, x + step, 13, z, gold ? Blocks.GOLD_BLOCK : Blocks.QUARTZ_BLOCK);
            }
        }
        if (gold) {
            for (int x = -6; x <= 6; x++) for (int z = 18; z <= 26; z++) put(world, o, x, 0, z, Blocks.CHISELED_QUARTZ_BLOCK);
            for (int y = 1; y <= 7; y++) for (int x = -3; x <= 3; x++) put(world, o, x, y, 25, y == 7 || Math.abs(x) == 3 ? Blocks.GOLD_BLOCK : Blocks.REDSTONE_BLOCK);
            for (int x = -2; x <= 2; x++) for (int z = 21; z <= 24; z++) put(world, o, x, 1, z, Blocks.GOLD_BLOCK);
            for (int z = -27; z <= 20; z++) for (int x = -2; x <= 2; x++) put(world, o, x, 0, z, Blocks.CARPET.red());
        }
        for (int side : new int[]{-1, 1}) {
            for (int y = 0; y <= 6; y++) for (int dz = -3; dz <= 3; dz++)
                if (y == 6 || Math.abs(dz) == 3) put(world, o, side * 29, y, dz, gold ? Blocks.GOLD_BLOCK : forest ? Blocks.MOSSY_STONE_BRICKS : Blocks.CHISELED_QUARTZ_BLOCK);
            put(world, o, side * 29, 0, 0, Blocks.SEA_LANTERN);
        }
    }

    private static void put(ServerLevel w, BlockPos o, int x, int y, int z, Block b) {
        w.setBlock(o.offset(x, y, z), b.defaultBlockState(), 2);
    }

    private TreasureArena() {}
}
