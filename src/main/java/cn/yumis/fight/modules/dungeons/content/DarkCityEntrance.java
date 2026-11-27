package cn.yumis.fight.modules.dungeons.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/** The road from a quarantined border station to the occupied capital beyond the city gate. */
public final class DarkCityEntrance {
    public static final String ID = "dark_city";
    private static final String VERSION_PREFIX = "DARK_CITY_LAYOUT_";
    static final String VERSION = VERSION_PREFIX + "5";
    static final DungeonLayouts.Bounds BOUNDS = new DungeonLayouts.Bounds(-112, -40, -42, 112, 80, 288);
    static final DungeonLayouts.Bounds ACTIVE_BOUNDS =
        new DungeonLayouts.Bounds(-86, -2, -6, 102, 49, TempleRoomSequence.MAX_Z);
    public static final int BOSS_AREA = 3;
    private static final int SPACING = TempleRoomSequence.SPACING;
    private static final Block STONE = Blocks.DEEPSLATE_BRICKS;
    private static final Block TRIM = Blocks.POLISHED_BLACKSTONE;
    private static final Block ROCK = Blocks.COBBLED_DEEPSLATE;
    private static final Block BLUE_STONE = Blocks.DYED_TERRACOTTA.cyan();
    private static final Block DARK_BLUE_STONE = Blocks.DYED_TERRACOTTA.blue();
    private static final Block PALE_STONE = Blocks.POLISHED_TUFF;
    private static final BlockState GRILLE = Blocks.IRON_BARS.defaultBlockState()
        .setValue(BlockStateProperties.EAST, true).setValue(BlockStateProperties.WEST, true);
    private static final BlockState SIDE_GRILLE = Blocks.IRON_BARS.defaultBlockState()
        .setValue(BlockStateProperties.NORTH, true).setValue(BlockStateProperties.SOUTH, true);

    public static final String[] AREAS = {"遗骨前哨", "护卫回廊", "重锤庭院", "暗黑城大门", "东侧转运场", "西侧守备庭院", "王城巡防台"};
    private static final SideYard EAST_FREIGHT = new SideYard(34, 16, 78, 48);
    private static final SideYard WEST_GARRISON = new SideYard(-85, 87, -42, 126);
    private static final SideYard EAST_WATCH = new SideYard(60, 106, 101, 146);
    private static final SideYard[] SIDE_YARDS = {EAST_FREIGHT, WEST_GARRISON, EAST_WATCH};
    private static final SideYard[] FOOTBRIDGES = {
        new SideYard(4, 33, 34, 39), new SideYard(-42, 113, -4, 119),
        new SideYard(20, 138, 60, 144), new SideYard(70, 48, 76, 106)
    };

    private record SideYard(int minX, int minZ, int maxX, int maxZ) {
        boolean contains(double x, double z) {
            return x >= minX + 1 && x < maxX && z >= minZ + 1 && z < maxZ;
        }

        boolean intersectsChunk(int x, int z) {
            return x <= maxX && x + 15 >= minX && z <= maxZ && z + 15 >= minZ;
        }
    }

    private DarkCityEntrance() {}

    public static boolean recorded(String layout) {
        return layout.contains(ID + "/" + VERSION_PREFIX);
    }

    public static String layoutKey() {
        return ID + "/" + VERSION;
    }

    public static int area(int wave) {
        return wave <= BOSS_AREA * 2 ? wave / 2 : wave - BOSS_AREA;
    }

    public static int firstWave(int area) {
        return area <= BOSS_AREA ? area * 2 : area + BOSS_AREA;
    }

    public static boolean contains(int cell, int area, Vec3 point) {
        return area <= BOSS_AREA ? TempleRoomSequence.contains(cell, area, point.x, point.z)
            : SIDE_YARDS[area - BOSS_AREA - 1].contains(point.x - cell * 512, point.z);
    }

    public static int arrivedArea(int cell, Vec3 point) {
        if (point.y < 79 || point.y >= 99) return -1;
        for (int area = 0; area < AREAS.length; area++) {
            if (contains(cell, area, point) && (area > BOSS_AREA || point.z >= area * SPACING + 6)) return area;
        }
        return -1;
    }

    public static Vec3 inside(int cell, int area, Vec3 point) {
        if (area > BOSS_AREA) {
            var yard = SIDE_YARDS[area - BOSS_AREA - 1];
            return new Vec3(cell * 512 + Math.clamp(point.x - cell * 512, yard.minX + 3, yard.maxX - 2), 80,
                Math.clamp(point.z, yard.minZ + 3, yard.maxZ - 2));
        }
        double half = area == 3 ? 17 : 13;
        return new Vec3(Math.clamp(point.x, cell * 512 - half, cell * 512 + half), 80,
            Math.clamp(point.z, area * SPACING + 3, area * SPACING + (area == 3 ? 36 : 29)));
    }

    public static Vec3 spawnPoint(int cell, int area, int slot) {
        int offset = (slot % 5 - 2) * 4;
        int row = slot / 5 * 7;
        if (area < BOSS_AREA) return new Vec3(cell * 512 + offset, 80, area * SPACING + 11 + row);
        if (area == BOSS_AREA) return new Vec3(cell * 512, 80, 140);
        var yard = SIDE_YARDS[area - BOSS_AREA - 1];
        return new Vec3(cell * 512 + (yard.minX + yard.maxX) / 2 + offset, 80,
            (yard.minZ + yard.maxZ) / 2 - 4 + row);
    }

    public static boolean keepsChunk(int chunkX, int chunkZ) {
        int x = chunkX * 16;
        int z = chunkZ * 16;
        if (x <= 23 && x + 15 >= -23 && z <= TempleRoomSequence.MAX_Z && z + 15 >= -6) return true;
        for (var yard : SIDE_YARDS) if (yard.intersectsChunk(x, z)) return true;
        for (var bridge : FOOTBRIDGES) if (bridge.intersectsChunk(x, z)) return true;
        return false;
    }

    public static void build(ServerLevel world, BlockPos origin) {
        var scene = new Scene(world, origin);
        scene.cavern();
        for (int room = 0; room <= BOSS_AREA; room++) {
            scene.room(room);
            if (room < BOSS_AREA) scene.passage(room);
        }
        scene.quarantine();
        scene.oldStreet();
        scene.serviceYard();
        scene.approach();
        scene.cityGate();
        scene.capital();
        scene.deepCity();
        scene.sideRegions();
    }

    private record Scene(ServerLevel world, BlockPos origin) {
        void cavern() {
            for (int z = BOUNDS.minZ(); z <= BOUNDS.maxZ(); z++) {
                for (int x = BOUNDS.minX(); x <= BOUNDS.maxX(); x++) {
                    int width = Math.abs(x);
                    int crown = 27 + (BOUNDS.maxX() - width) / 4 + Math.clamp((z + 30) / 8, 0, 17)
                        + (int) Math.round(3 * Math.sin((x + z) * .07));
                    int basin = Math.max(-39, -36 + (int) Math.round(3 * Math.sin(x * .044 + z * .022)
                        + 2 * Math.cos(z * .051)));
                    int floor = Math.max(basin, -9 - 2 * Math.max(0, width - 44)
                        - 2 * Math.max(0, Math.max(-6 - z, z - 228)));
                    Block rock = caveStone(x, z);
                    put(x, floor - 1, z, ROCK);
                    put(x, floor, z, rock);
                    put(x, crown, z, rock);
                    put(x, crown + 1, z, rock);
                    if (width == BOUNDS.maxX() || z == BOUNDS.minZ() || z == BOUNDS.maxZ()) {
                        fill(x, floor + 1, z, x, crown - 1, z, rock);
                    }
                }
            }
            for (int side : new int[]{-1, 1}) {
                for (int z = -25; z <= 268; z += 19) {
                    int x = side * (103 - Math.floorMod(z, 11));
                    rockStack(x, z, 24 + Math.floorMod(z, 13), 7);
                }
            }
        }

        Block caveStone(int x, int z) {
            int seam = Math.floorMod(x + z / 3, 17);
            return seam < 3 ? DARK_BLUE_STONE : seam < 7 ? BLUE_STONE : seam < 10 ? Blocks.DEEPSLATE : ROCK;
        }

        void room(int room) {
            int start = room * SPACING;
            int half = room == 3 ? 20 : 16;
            int end = start + 2 * half;
            fill(-half, -3, start, half, -3, end, ROCK);
            fill(-5, -8, start, 5, -4, end, STONE);
            for (int z = start + 2; z < end; z += 9) {
                for (int side : new int[]{-1, 1}) {
                    int x = side * (half - 2);
                    fill(x - 1, -8, z, x + 1, -4, z + 1, ROCK);
                }
            }
            fill(-half, -2, start, half, -2, end, STONE);
            fill(-half, 19, start, half, 19, end, Blocks.BARRIER);
            for (int x = -half; x <= half; x++) {
                for (int z = start; z <= end; z++) {
                    int grain = Math.floorMod(x * 53 + z * 31 + x * z * 11, 101);
                    int seam = Math.floorMod(x + z / 2 + z / 7 % 3, 13);
                    Block paving = grain < 14 ? Blocks.CRACKED_DEEPSLATE_TILES
                        : seam < 4 && grain < 80 ? DARK_BLUE_STONE : Blocks.POLISHED_DEEPSLATE;
                    if (room == 0 && Math.abs(x) > 5 && seam >= 7) paving = ROCK;
                    if (room >= 1 && Math.abs(x) == 4 && z % 7 < 5) paving = PALE_STONE;
                    put(x, -1, z, paving);
                    boolean door = Math.abs(x) <= 3
                        && ((z == start && room > 0) || (z == end && room < 3));
                    if ((Math.abs(x) == half || z == start || z == end) && !door) {
                        boolean exposed = room == 0 || Math.abs(x) == half && z % 40 > 18 && z % 40 < 23;
                        int wallTop = exposed ? 9 + Math.floorMod(x + z / 2, 4) : 11;
                        column(x, 0, z, wallTop, exposed ? caveStone(x, z) : STONE);
                        column(x, wallTop, z, 19 - wallTop, Blocks.BARRIER);
                        if (!exposed) {
                            put(x, 0, z, TRIM);
                            put(x, 4, z, BLUE_STONE);
                        }
                    }
                }
            }
            for (int x : new int[]{-9, 9}) {
                for (int z = start + 6; z < end - 3; z += 8) {
                    put(x, -1, z, room == 0 ? Blocks.OCHRE_FROGLIGHT : Blocks.SEA_LANTERN);
                }
            }
            for (int z = start + 8; z < end - 2; z += 10) {
                for (int x : new int[]{-11, 0, 11}) light(x, 3, z, room == 0 ? 13 : 14);
                for (int x : new int[]{1 - half, half - 1}) light(x, 7, z, 12);
            }
        }

        void passage(int room) {
            int end = room * SPACING + 32;
            fill(-3, -8, end + 1, 3, -3, end + 7, STONE);
            fill(-4, -2, end + 1, 4, -1, end + 7, TRIM);
            fill(-4, 19, end + 1, 4, 19, end + 7, Blocks.BARRIER);
            for (int x : new int[]{-4, 4}) {
                fill(x, 0, end + 1, x, 8, end + 7, STONE);
                fill(x, 9, end + 1, x, 18, end + 7, Blocks.BARRIER);
            }
            arch(end + 1, 4, 6, room == 0 ? ROCK : TRIM);
            arch(end + 7, 4, 6, TRIM);
            put(0, 7, end + 4, STONE);
            hangingLamp(0, 7, end + 4, room > 0);
            light(0, 4, end + 3, 13);
        }

        void quarantine() {
            // Travelers used the intake counter before taking the road west to Noire Ferra.
            fill(-30, -8, 18, -16, -3, 26, ROCK);
            fill(-30, -2, 18, -16, -1, 26, Blocks.CUT_SANDSTONE);
            fill(-30, 0, 18, -17, 6, 18, ROCK);
            fill(-30, 0, 26, -17, 6, 26, ROCK);
            fill(-30, 6, 18, -16, 6, 26, ROCK);
            fill(-30, 0, 19, -30, 5, 25, ROCK);
            sideOpening(-16, 0, 19, 25, 5, true);
            for (int z : new int[]{18, 26}) column(-16, 0, z, 7, Blocks.POLISHED_BASALT);
            fill(-16, 5, 18, -16, 5, 26, Blocks.STRIPPED_SPRUCE_LOG);
            sign(-15, 3, 18, Direction.EAST, "诺伊佩拉", "支路封锁");
            put(-21, -1, 22, Blocks.OCHRE_FROGLIGHT);
            fill(-28, 0, 19, -25, 0, 21, Blocks.SANDSTONE);
            put(-27, 1, 20, Blocks.RAW_GOLD_BLOCK);
            put(-26, 1, 20, Blocks.TUFF);
            put(-25, 0, 25, Blocks.BARREL);
            light(-19, 3, 22, 13);

            sideOpening(16, 0, 4, 15, 7, false);
            fill(17, -8, 4, 26, -3, 15, ROCK);
            fill(17, -2, 4, 26, -1, 15, Blocks.SPRUCE_PLANKS);
            fill(17, 7, 4, 26, 7, 15, Blocks.STRIPPED_SPRUCE_WOOD);
            fill(26, 0, 4, 26, 6, 15, ROCK);
            fill(17, 0, 4, 25, 6, 4, ROCK);
            fill(17, 0, 15, 25, 6, 15, ROCK);
            fill(16, 0, 5, 16, 0, 14, Blocks.SPRUCE_PLANKS);
            put(16, 1, 6, Blocks.FLOWER_POT);
            put(16, 1, 9, Blocks.LANTERN);
            put(22, 0, 5, Blocks.CAULDRON);
            put(24, 0, 8, Blocks.BARREL);
            put(24, 1, 8, Blocks.BARREL);
            sign(15, 2, 4, Direction.WEST, "入城检疫", "登记与物资转交");
            light(19, 3, 9, 15);

            for (int z : new int[]{18, 25}) {
                put(15, 0, z, Blocks.BONE_BLOCK);
                put(15, 1, z, Blocks.SKELETON_SKULL);
            }
        }

        void oldStreet() {
            streetHouse(-1, 43, 55, false);
            streetHouse(1, 45, 58, false);
            streetHouse(-1, 61, 70, true);
            sideOpening(16, 1, 62, 68, 10, true);
            for (int z : new int[]{43, 70}) {
                arch(z, 15, 9, TRIM);
                hangingLamp(0, 12, z, true);
            }
            // This window overlooks a lower row of homes along the service road.
            fill(17, -8, 60, 40, -6, 73, ROCK);
            for (int z : new int[]{60, 68}) {
                cityBuilding(31, -5, z, 40, 3, z + 5, Blocks.PURPUR_BLOCK);
            }
            fill(20, -1, 69, 30, -1, 71, Blocks.POLISHED_DEEPSLATE);
            fill(20, -5, 69, 21, -2, 71, STONE);
            fill(29, -5, 69, 30, -2, 71, STONE);
            put(29, 0, 70, Blocks.SOUL_LANTERN);
            light(21, 3, 65, 13);
        }

        void streetHouse(int side, int start, int end, boolean shuttered) {
            int near = side * 16;
            int rear = side * 25;
            fill(Math.min(near, rear), -8, start, Math.max(near, rear), -2, end, ROCK);
            sideOpening(near, 0, start + 1, start + 4, 6, true);
            if (!shuttered) {
                sideOpening(near, 1, start + 5, end - 1, 5, false);
                fill(near, 0, start + 5, near, 0, end - 1, Blocks.DARK_OAK_PLANKS);
            }
            sideOpening(near, 8, start + 5, end - 1, 11, true);
            for (int step = 1; step <= 9; step++) {
                int x = near + step * side;
                for (int z = start; z <= end; z++) {
                    put(x, -1, z, Blocks.SPRUCE_PLANKS);
                    put(x, 6, z, Blocks.DARK_OAK_PLANKS);
                    put(x, 12, z, TRIM);
                    if (x == rear || z == start || z == end) column(x, 0, z, 12, STONE);
                }
            }
            column(near, 0, start, 12, TRIM);
            column(near, 0, end, 12, TRIM);
            for (int z = start + 5; z < end; z++) {
                put(near + side, 0, z, Blocks.DARK_OAK_PLANKS);
                put(near + side, 5, z, Blocks.DYED_TERRACOTTA.purple());
            }
            put(near + side, 1, start + 6, Blocks.FLOWER_POT);
            put(near + side, 1, end - 2, Blocks.LANTERN);
            put(rear - side, 0, end - 1, Blocks.BARREL);
            put(rear - 2 * side, 0, end - 1, Blocks.BARREL);
            put(near + 2 * side, 7, start + 6, Blocks.LANTERN);
            put(near + 3 * side, 7, end - 1, Blocks.WOOL.white());
            put(near + 3 * side, 7, end - 2, Blocks.DYED_TERRACOTTA.purple());
            light(near + 3 * side, 3, start + 6, 15);
            light(near + 3 * side, 9, start + 7, 13);
        }

        void serviceYard() {
            sideOpening(-16, 1, 84, 101, 10, false);
            fill(-28, -8, 84, -16, -3, 101, ROCK);
            fill(-28, -2, 84, -16, -1, 101, STONE);
            fill(-28, 0, 84, -28, 8, 101, ROCK);
            fill(-28, 0, 84, -17, 8, 84, ROCK);
            fill(-28, 8, 84, -16, 8, 101, TRIM);
            fill(-26, 0, 86, -26, 0, 94, Blocks.POLISHED_BASALT);
            put(-25, 0, 90, Blocks.ANVIL);
            put(-25, 0, 93, Blocks.SMITHING_TABLE);
            put(-25, 0, 87, Blocks.BLAST_FURNACE);
            put(-26, 1, 89, Blocks.OCHRE_FROGLIGHT);
            fill(-23, 0, 97, -21, 0, 100, Blocks.BARREL);
            put(-23, 1, 100, Blocks.BARREL);
            put(-26, 0, 98, Blocks.CAULDRON);
            for (int z : new int[]{88, 97}) hangingLamp(-20, 8, z, false);
            light(-23, 3, 89, 15);
            light(-23, 3, 97, 14);
            column(-16, 0, 83, 11, TRIM);
            column(-16, 0, 102, 11, TRIM);
            sign(-15, 2, 83, Direction.EAST, "守军工坊", "马具与城防维修");

            sideOpening(16, 1, 90, 104, 11, true);
            fill(17, -8, 88, 37, -5, 104, ROCK);
            cityBuilding(27, -4, 90, 34, 7, 97, Blocks.COPPER_BLOCK.waxed().weathered());
            fill(18, -1, 91, 27, -1, 93, TRIM);
            fill(18, -1, 100, 32, -1, 103, TRIM);
            fill(32, -1, 94, 32, -1, 100, TRIM);
            for (int x : new int[]{19, 25, 32}) {
                fill(x, -4, 101, x, -2, 102, STONE);
            }
            fill(19, -4, 91, 20, -2, 92, STONE);
            fill(32, -4, 95, 32, -2, 96, STONE);
            for (int x = 21; x <= 30; x += 3) {
                put(x, 0, 102, Blocks.BARREL);
                put(x, -1, 101, Blocks.SEA_LANTERN);
            }
            light(23, 3, 97, 14);
        }

        void approach() {
            // Lanterns mark the guard road. The rough faces behind them belong to the cavern.
            beacon(-15, 10);
            beacon(15, 28);
            beacon(-15, 60);
            beacon(15, 83);
            beacon(-15, 108);
            for (int side : new int[]{-1, 1}) {
                buttress(side * 20, 27, 16);
                buttress(side * 26, 37, 23);
                buttress(side * 30, 78, 26);
                buttress(side * 33, 110, 32);
            }
            sideOpening(16, 1, 26, 30, 14, true);
            light(19, 5, 30, 13);
            pennant(-15, 5, 11, Blocks.WOOL.red());
            pennant(15, 5, 84, Blocks.WOOL.red());
        }

        void buttress(int x, int z, int height) {
            for (int y = -8; y < height; y++) {
                int half = y < 2 ? 4 : y < height / 2 ? 3 : 2;
                int offset = Math.floorDiv(y, 8);
                for (int dx = -half; dx <= half; dx++) {
                    for (int dz = -half; dz <= half; dz++) {
                        if (Math.abs(dx) + Math.abs(dz) > half + 1) continue;
                        int px = x + dx + offset;
                        put(px, y, z + dz, caveStone(px + y / 3, z + dz));
                    }
                }
            }
        }

        void rockStack(int x, int z, int top, int baseRadius) {
            for (int y = -40; y < top; y++) {
                int radius = Math.max(2, baseRadius - (y + 40) / 12);
                int bend = (y + 40) / 22 - 1;
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.abs(dx) + Math.abs(dz) > radius + 2) continue;
                        int px = x + dx + bend;
                        put(px, y, z + dz, caveStone(px + y / 4, z + dz));
                    }
                }
            }
        }

        void beacon(int x, int z) {
            column(x, 0, z, 2, PALE_STONE);
            fill(x, 2, z - 1, x, 6, z - 1, SIDE_GRILLE);
            fill(x, 2, z + 1, x, 6, z + 1, SIDE_GRILLE);
            column(x, 2, z, 4, Blocks.POLISHED_BASALT);
            put(x, 6, z, PALE_STONE);
            put(x, 7, z, Blocks.SEA_LANTERN);
            put(x, 8, z, Blocks.STAINED_GLASS.cyan());
            light(x - Integer.signum(x) * 2, 7, z, 15);
        }

        void pennant(int x, int top, int z, Block wool) {
            fill(x, top, z, x, top, z + 2, PALE_STONE);
            fill(x, top - 3, z, x, top - 1, z + 1, wool);
            fill(x, top - 2, z + 2, x, top - 1, z + 2, Blocks.WOOL.purple());
        }

        void cityGate() {
            for (int side : new int[]{-1, 1}) {
                int x = side * 19;
                int wall = side * 20;
                for (int z : new int[]{126, 138, 151}) {
                    fill(Math.min(x, wall), 0, z - 1, Math.max(x, wall), 3, z + 1, PALE_STONE);
                    column(wall, 4, z, 18, STONE);
                    beacon(x, z);
                    pennant(x, 13, z, Blocks.WOOL.purple());
                }
                sideOpening(wall, 1, 128, 136, 17, true);
                sideOpening(wall, 1, 140, 149, 17, true);
                ravine(side);
            }
            for (int x = -11; x <= 11; x++) {
                for (int z = 129; z <= 151; z++) {
                    int distance = x * x + (z - 140) * (z - 140);
                    if (distance >= 100 && distance <= 121) put(x, -1, z, Blocks.CHISELED_DEEPSLATE);
                }
            }
            for (int x = -19; x <= 19; x++) {
                int offset = Math.abs(x);
                int height = offset < 7 ? 19 - offset / 2 : offset >= 11 && offset <= 18 ? 11 : 0;
                int sill = offset < 6 ? 0 : 1;
                for (int y = sill; y < height; y++) {
                    put(x, y, 160, y <= sill + 1 || offset < 7 && y <= 4
                        ? GRILLE : Blocks.BARRIER.defaultBlockState());
                }
                if (height > 0) put(x, height, 160, PALE_STONE);
            }
            arch(159, 9, 15, PALE_STONE);
            for (int x : new int[]{-8, 8}) {
                fill(x - 1, 0, 158, x + 1, 2, 159, PALE_STONE);
                column(x, 3, 159, 14, BLUE_STONE);
                put(x, 10, 158, Blocks.SEA_LANTERN);
                put(x, 9, 158, PALE_STONE);
                fill(x, 11, 158, x, 15, 158, Blocks.WOOL.purple());
                light(x, 8, 155, 15);
                light(x * 2, 5, 156, 14);
            }
            for (int x : new int[]{-18, 18}) {
                fill(x - 4, -8, 161, x + 4, -2, 169, STONE);
                cityBuilding(x - 4, -1, 161, x + 4, 23, 169, BLUE_STONE);
            }
            fill(-14, 21, 161, 14, 23, 165, STONE);
            for (int x = -12; x <= 12; x += 4) put(x, 22, 160, PALE_STONE);
            light(0, 9, 155, 14);
            light(0, 18, 155, 15);
        }

        void ravine(int side) {
            // Guard galleries span a lower freight cut and join the gatehouse.
            int near = side * 23;
            int far = side * 37;
            fill(Math.min(near, far), -8, 120, Math.max(near, far), -7, 161, ROCK);
            int course = side * 29;
            fill(course, -6, 121, course, -6, 160, PALE_STONE);
            for (int z = 122; z < 160; z += 9) {
                put(course, -5, z, Blocks.SOUL_LANTERN);
                buttress(side * 38, z, 24 + z % 8);
                light(side * 34, 1, z, 15);
                light(side * 34, 10, z, 15);
                light(side * 36, 18, z + 2, 14);
            }
            int a = Math.min(side * 33, side * 36);
            int b = Math.max(side * 33, side * 36);
            fill(a, 6, 123, b, 7, 162, BLUE_STONE);
            for (int z = 125; z < 161; z += 10) {
                fill(a, -6, z, b, 5, z + 2, STONE);
                put(side * 33, 8, z + 3, Blocks.SOUL_LANTERN);
            }
            fill(Math.min(side * 21, b), 6, 160, Math.max(side * 21, a), 7, 163, PALE_STONE);
        }

        void capital() {
            // Freight road below, inhabited terraces beyond it, royal avenue above.
            fill(-41, -8, 161, 41, -6, 226, ROCK);
            fill(-5, -5, 161, 5, -1, 182, TRIM);
            for (int step = 0; step < 3; step++) {
                fill(-5, -5, 183 + step, 5, step, 183 + step, TRIM);
            }
            fill(-39, -5, 184, -6, 1, 225, STONE);
            fill(6, -5, 184, 39, 1, 225, STONE);
            fill(-5, -5, 186, 5, 1, 225, STONE);
            fill(-31, 2, 204, 31, 7, 225, STONE);
            fill(-5, 2, 186, 5, 2, 203, Blocks.POLISHED_DEEPSLATE);
            // The outer terraces take freight, while the inner terraces lead toward the royal road.
            cityBuilding(-36, -5, 168, -25, 4, 181, Blocks.DARK_OAK_PLANKS);
            cityBuilding(-24, -5, 173, -15, 6, 181, BLUE_STONE);
            cityBuilding(12, -5, 170, 21, 3, 180, Blocks.DARK_OAK_PLANKS);
            cityBuilding(26, -5, 165, 39, 6, 181, BLUE_STONE);
            cityBuilding(-34, 2, 187, -22, 12, 199, BLUE_STONE);
            cityBuilding(-20, 2, 190, -11, 17, 202, Blocks.PURPUR_BLOCK);
            cityBuilding(13, 2, 188, 23, 13, 201, Blocks.PURPUR_BLOCK);
            cityBuilding(27, 2, 193, 37, 18, 205, BLUE_STONE);
            cityBuilding(-25, 8, 211, -15, 26, 223, TRIM);
            cityBuilding(13, 8, 208, 23, 31, 222, BLUE_STONE);
            spire(-20, 27, 216, 3);
            spire(18, 32, 213, 4);
            fill(7, 18, 211, 14, 19, 215, PALE_STONE);
            fill(7, 20, 211, 14, 20, 211, GRILLE);
            fill(7, 20, 215, 14, 20, 215, GRILLE);
            for (int side : new int[]{-1, 1}) {
                buttress(side * 37, 216, 34);
                int channel = side * 9;
                fill(channel, -5, 161, channel, -5, 183, Blocks.STAINED_GLASS.blue());
                for (int z = 163; z <= 183; z += 5) {
                    put(channel, -6, z, Blocks.SEA_LANTERN);
                    if (z < 183) put(side * 4, 0, z, Blocks.SOUL_LANTERN);
                }
            }
            for (int step = 0; step < 6; step++) {
                fill(-5, 2, 203 + step, 5, 2 + step, 203 + step, TRIM);
            }
            cityBuilding(-8, 8, 209, 8, 31, 223, Blocks.PURPUR_BLOCK);
            arch(208, 5, 17, PALE_STONE);
            fill(-2, 9, 209, 2, 13, 209, Blocks.AIR);
            fill(-2, 14, 209, 2, 18, 209, Blocks.STAINED_GLASS.purple());
            fill(-6, 8, 209, 6, 8, 217, TRIM);
            fill(-2, 14, 210, 2, 18, 210, Blocks.SEA_LANTERN);
            put(-3, 9, 211, Blocks.SOUL_LANTERN);
            put(3, 9, 211, Blocks.SOUL_LANTERN);
            spire(0, 32, 215, 5);
            for (int z : new int[]{170, 181, 194, 204}) light(0, 5, z, 14);
            for (int x : new int[]{-13, 0, 13}) light(x, 18, 204, 15);
            light(0, 28, 204, 15);
        }

        void deepCity() {
            for (int side : new int[]{-1, 1}) {
                freightCut(side);
                guardCauseway(side);
                lowerQuarter(side);
                ridgeWall(side);
            }
            // The upper road climbs from the inner ward to the keep built into the rear cliff.
            fill(-49, -30, 239, 49, 11, 272, ROCK);
            for (int step = 0; step < 13; step++) {
                fill(-9, -13, 226 + step, 9, 7 + step / 3, 226 + step, PALE_STONE);
            }
            fill(-10, 11, 239, 10, 12, 252, PALE_STONE);
            for (int x : new int[]{-40, 40}) {
                cityBuilding(x - 8, 12, 244, x + 8, 37, 266, BLUE_STONE);
                spire(x, 41, 255, 6);
            }
            cityBuilding(-23, 12, 252, 23, 44, 273, TRIM);
            arch(251, 13, 28, PALE_STONE);
            fill(-6, 13, 252, 6, 23, 252, Blocks.AIR);
            fill(-6, 24, 252, 6, 30, 252, Blocks.STAINED_GLASS.blue());
            fill(-6, 24, 253, 6, 30, 253, Blocks.SEA_LANTERN);
            for (int x : new int[]{-13, 13}) spire(x, 47, 264, 5);
            for (int x = -40; x <= 40; x += 10) {
                light(x, 24, 243, 15);
                light(x, 40, 241, 14);
            }
        }

        void freightCut(int side) {
            for (int z = 25; z < 267; z++) {
                int center = 56 + (int) Math.round(8 * Math.sin(z * .036));
                int half = 3 + Math.floorMod(z / 29, 3);
                for (int offset = -half; offset <= half; offset++) {
                    int x = side * (center + offset);
                    fill(x, -38, z, x, -29, z, Blocks.AIR);
                    put(x, -39, z, ROCK);
                    put(x, -38, z, Math.floorMod(offset + z, 11) == 0 ? Blocks.SEA_LANTERN : BLUE_STONE);
                    put(x, -37, z, Blocks.STAINED_GLASS.blue());
                }
            }
            for (int z : new int[]{18, 52, 88, 119, 188, 224, 256}) {
                boolean behindYard = z == 119 || side < 0 && z == 88;
                int x = side * (behindYard ? 103 : 77);
                rockStack(x, z, 48 + z % 7, behindYard ? 8 : 11);
                light(x - side * 7, 18, z, 15);
                light(x - side * 7, 33, z + 3, 14);
            }
        }

        void guardCauseway(int side) {
            for (int distance = 24; distance <= 89; distance++) {
                int x = side * distance;
                int span = Math.floorMod(distance - 29, 14);
                int archY = 3 - Math.abs(span - 7);
                fill(x, 6, 158, x, 7, 162, PALE_STONE);
                fill(x, archY, 159, x, archY + 1, 161, STONE);
                put(x, 8, 158, span == 0 ? PALE_STONE.defaultBlockState() : GRILLE);
                put(x, 8, 162, span == 0 ? PALE_STONE.defaultBlockState() : GRILLE);
                if (span <= 1) fill(x, -39, 159, x, 5, 161, STONE);
                if (span == 7) {
                    put(x, 9, 158, Blocks.SOUL_LANTERN);
                    put(x, 9, 162, Blocks.SOUL_LANTERN);
                    light(x, 11, 160, 15);
                    light(x, -7, 157, 14);
                }
            }
        }

        void lowerQuarter(int side) {
            int first = side * 64;
            int end = side * 101;
            int west = Math.min(first, end);
            int east = Math.max(first, end);
            fill(west, -39, 169, east, -19, 230, ROCK);
            fill(west, -18, 171, east, -18, 228, PALE_STONE);
            int waterfront = side * 68;
            for (int z = 176; z <= 222; z += 13) {
                int x = side * (77 + z % 4);
                cityBuilding(x - 4, -17, z, x + 4, -6 + Math.floorMod(z, 6), z + 9, Blocks.DARK_OAK_PLANKS);
                put(waterfront, -17, z, Blocks.SOUL_LANTERN);
                light(waterfront, -12, z, 15);
            }
            int rise = side * 90;
            for (int z = 173; z < 213; z += 3) {
                int top = -17 + (z - 173) / 3;
                fill(rise - 3, -17, z, rise + 3, top, z + 2, STONE);
                fill(rise - 3, top, z, rise + 3, top, z + 2, PALE_STONE);
            }
            fill(rise - 9, -17, 213, rise + 9, -4, 234, ROCK);
            cityBuilding(rise - 7, -3, 215, rise + 7, 13, 230, BLUE_STONE);
            light(rise, 4, 212, 15);
        }

        void ridgeWall(int side) {
            int end = side * 91;
            int center = side * 81;
            fill(center - 8, -39, 145, center + 8, 6, 168, ROCK);
            cityBuilding(center - 7, 7, 148, center + 7, 29, 166, BLUE_STONE);
            spire(center, 33, 156, 4);
            fill(Math.min(side * 47, end), 10, 237, Math.max(side * 47, end), 14, 247, STONE);
            for (int distance = 51; distance <= 91; distance += 10) {
                int x = side * distance;
                fill(x - 2, -30, 238, x + 2, 9, 246, ROCK);
                fill(x - 3, 15, 240, x + 3, 23, 246, STONE);
                fill(x - 2, 24, 241, x + 2, 24, 245, BLUE_STONE);
                light(x, 16, 236, 15);
            }
            light(center - side * 6, 22, 145, 15);
        }

        void sideRegions() {
            for (var yard : SIDE_YARDS) yard(yard);
            for (var bridge : FOOTBRIDGES) footbridge(bridge);

            for (int x : new int[]{40, 71}) {
                fill(x, 0, 20, x + 2, 0, 23, Blocks.BARREL);
                put(x + 1, 1, 21, Blocks.BARREL);
                put(x, 0, 42, Blocks.CRAFTING_TABLE);
                put(x + 1, 0, 42, Blocks.BARREL);
            }
            for (int x : new int[]{-81, -47}) {
                fill(x, 0, 92, x + 1, 0, 94, Blocks.POLISHED_BASALT);
                put(x, 1, 92, Blocks.LANTERN);
                put(x, 0, 122, Blocks.ANVIL);
                put(x + 1, 0, 122, Blocks.SMITHING_TABLE);
            }
            for (int x : new int[]{64, 96}) {
                beacon(x, 110);
                beacon(x, 142);
                pennant(x, 13, 142, Blocks.WOOL.purple());
            }
        }

        void yard(SideYard yard) {
            fill(yard.minX, 0, yard.minZ, yard.maxX, 18, yard.maxZ, Blocks.AIR);
            fill(yard.minX, -3, yard.minZ, yard.maxX, -1, yard.maxZ, STONE);
            fill(yard.minX, 19, yard.minZ, yard.maxX, 19, yard.maxZ, Blocks.BARRIER);
            for (int x = yard.minX; x <= yard.maxX; x++) {
                for (int z = yard.minZ; z <= yard.maxZ; z++) {
                    int grain = Math.floorMod(x * 53 + z * 31 + x * z * 11, 101);
                    Block floor = grain < 15 ? Blocks.CRACKED_DEEPSLATE_TILES
                        : grain < 36 ? DARK_BLUE_STONE : Blocks.POLISHED_DEEPSLATE;
                    put(x, -1, z, floor);
                    if (x == yard.minX || x == yard.maxX) rail(x, z, SIDE_GRILLE);
                    else if (z == yard.minZ || z == yard.maxZ) rail(x, z, GRILLE);
                }
            }
            for (int x = yard.minX + 6; x < yard.maxX; x += 10) {
                for (int z = yard.minZ + 6; z < yard.maxZ; z += 10) {
                    fill(x, -39, z, x + 1, -4, z + 1, STONE);
                    light(x, 3, z, 15);
                }
            }
        }

        void footbridge(SideYard bridge) {
            boolean eastWest = bridge.maxX - bridge.minX > bridge.maxZ - bridge.minZ;
            fill(bridge.minX, -3, bridge.minZ, bridge.maxX, -1, bridge.maxZ, PALE_STONE);
            for (int x = bridge.minX; x <= bridge.maxX; x++) {
                for (int z = bridge.minZ; z <= bridge.maxZ; z++) {
                    boolean edge = eastWest ? z == bridge.minZ || z == bridge.maxZ : x == bridge.minX || x == bridge.maxX;
                    if (edge) {
                        rail(x, z, eastWest ? GRILLE : SIDE_GRILLE);
                    } else {
                        column(x, 0, z, 19, Blocks.AIR);
                    }
                    put(x, 19, z, Blocks.BARRIER);
                }
            }
            int min = eastWest ? bridge.minX : bridge.minZ;
            int max = eastWest ? bridge.maxX : bridge.maxZ;
            for (int i = min + 5; i < max; i += 10) {
                int x = eastWest ? i : (bridge.minX + bridge.maxX) / 2;
                int z = eastWest ? (bridge.minZ + bridge.maxZ) / 2 : i;
                fill(x, -39, z, x + 1, -4, z + 1, STONE);
                light(x, 3, z, 15);
            }
        }

        void rail(int x, int z, BlockState grille) {
            put(x, 0, z, PALE_STONE);
            put(x, 1, z, grille);
            column(x, 2, z, 17, Blocks.BARRIER);
        }

        void cityBuilding(int x1, int base, int z1, int x2, int top, int z2, Block accent) {
            fill(x1, base, z1, x2, base, z2, TRIM);
            for (int x : new int[]{x1, x2}) {
                fill(x, base + 1, z1, x, top - 1, z2, STONE);
                int inner = x == x1 ? x + 1 : x - 1;
                for (int y = base + 3; y + 2 < top; y += 7) {
                    for (int z = z1 + 3; z < z2 - 1; z += 5) {
                        put(x, y - 1, z, PALE_STONE);
                        put(x, y, z, Blocks.STAINED_GLASS.cyan());
                        put(x, y + 1, z, Blocks.STAINED_GLASS.blue());
                        put(inner, y, z, Blocks.SEA_LANTERN);
                    }
                }
            }
            fill(x1 + 1, base + 1, z2, x2 - 1, top - 1, z2, STONE);
            fill(x1 + 1, base + 1, z1, x2 - 1, top - 1, z1, STONE);
            for (int x = x1 + 2; x < x2 - 1; x += 4) {
                for (int y = base + 3; y + 2 < top; y += 7) {
                    put(x, y - 1, z1, PALE_STONE);
                    put(x, y, z1, Blocks.STAINED_GLASS.cyan());
                    put(x, y + 1, z1, Blocks.STAINED_GLASS.blue());
                    put(x, y + 2, z1, PALE_STONE);
                    put(x, y, z1 + 1, Blocks.SEA_LANTERN);
                }
            }
            fill(x1, top, z1, x2, top, z2, PALE_STONE);
            for (int inset = 0; inset < 3; inset++) {
                fill(x1 + inset, top + 1 + inset, z1 + inset,
                    x2 - inset, top + 1 + inset, z2 - inset, accent);
            }
        }

        void spire(int x, int y, int z, int radius) {
            for (int layer = 0; layer < radius; layer++) {
                int width = radius - layer;
                fill(x - width, y + layer, z - width, x + width, y + layer, z + width, TRIM);
            }
            put(x, y + radius, z, Blocks.AMETHYST_BLOCK);
            put(x, y + radius + 1, z, Blocks.PEARLESCENT_FROGLIGHT);
        }

        void sideOpening(int x, int low, int start, int end, int top, boolean grille) {
            for (int z = start; z <= end; z++) {
                for (int y = low; y < top; y++) {
                    put(x, y, z, grille && y < low + 3 ? SIDE_GRILLE : Blocks.BARRIER.defaultBlockState());
                }
                put(x, top, z, TRIM);
            }
        }

        void arch(int z, int half, int spring, Block material) {
            for (int x = -half; x <= half; x++) {
                int y = spring + (half - Math.abs(x)) / Math.max(1, half / 3);
                if (Math.abs(x) == half) column(x, 0, z, spring + 1, material);
                put(x, y, z, material);
                put(x, y + 1, z, material);
            }
        }

        void hangingLamp(int x, int beam, int z, boolean soul) {
            put(x, beam - 1, z, Blocks.IRON_CHAIN);
            put(x, beam - 2, z, (soul ? Blocks.SOUL_LANTERN : Blocks.LANTERN)
                .defaultBlockState().setValue(LanternBlock.HANGING, true));
        }

        void light(int x, int y, int z, int level) {
            BlockPos pos = origin.offset(x, y, z);
            if (world.getBlockState(pos).isAir()) {
                world.setBlock(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, level), Block.UPDATE_CLIENTS);
            }
        }

        void sign(int x, int y, int z, Direction facing, String title, String detail) {
            BlockPos pos = origin.offset(x, y, z);
            var state = Blocks.SPRUCE_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, facing);
            world.setBlock(pos, state, Block.UPDATE_CLIENTS);
            if (world.getBlockEntity(pos) instanceof SignBlockEntity sign) {
                var text = new SignText().setMessage(0, Component.literal(title).withColor(0xE8D39B))
                    .setMessage(1, Component.literal(detail).withColor(0xC1D3EA));
                sign.setText(text, true);
                sign.setWaxed(true);
                sign.setChanged();
                world.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
            }
        }

        void column(int x, int y, int z, int height, Block block) {
            fill(x, y, z, x, y + height - 1, z, block);
        }

        void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
            fill(x1, y1, z1, x2, y2, z2, block.defaultBlockState());
        }

        void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    for (int y = y1; y <= y2; y++) put(x, y, z, state);
                }
            }
        }

        void put(int x, int y, int z, Block block) {
            put(x, y, z, block.defaultBlockState());
        }

        void put(int x, int y, int z, BlockState state) {
            world.setBlock(origin.offset(x, y, z), state, Block.UPDATE_CLIENTS);
        }
    }
}
