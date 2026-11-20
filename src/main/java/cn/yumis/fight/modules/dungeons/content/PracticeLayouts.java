package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.ClassicEnemyKind;
import cn.yumis.fight.modules.dungeons.api.DarkCityEnemyKind;
import cn.yumis.fight.modules.dungeons.api.ForbiddenEnemyKind;
import cn.yumis.fight.modules.dungeons.api.GblEnemyKind;
import cn.yumis.fight.modules.dungeons.api.PalaceEnemyKind;
import cn.yumis.fight.modules.dungeons.api.PracticeRoom;
import cn.yumis.fight.modules.dungeons.api.SkasaEnemyKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.RailShape;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Independent floor plans and built-in blockwork for the public practice rooms. */
public final class PracticeLayouts {
    private static final int CENTER_Z = 29;
    private static final int COMBAT_START = 11;
    private static final int EDGE_X = 26;
    private static final int END_Z = 52;
    private static final DungeonLayouts.Bounds BOUNDS = new DungeonLayouts.Bounds(-EDGE_X, -3, 0, EDGE_X, 16, END_Z);
    private static final Map<String, Arena> ARENAS = createArenas();

    @FunctionalInterface
    private interface FloorPlan { boolean contains(int x, int z); }

    private record Palette(Block floor, Block inlay, Block wall) {}

    public static final class Arena {
        private final FloorPlan plan;
        private final int height;
        private final Palette palette;
        private final Consumer<Builder> decorations;
        private final List<Spawn> spawns;

        private Arena(FloorPlan plan, int height, Palette palette, Consumer<Builder> decorations, List<Spawn> spawns) {
            this.plan = plan;
            this.height = height;
            this.palette = palette;
            this.decorations = decorations;
            this.spawns = spawns;
        }

        public boolean combatFloor(int x, int z) {
            return z >= COMBAT_START && z <= END_Z - 2
                && (plan.contains(x, z - CENTER_Z) || z <= CENTER_Z && Math.abs(x) <= 3);
        }

        private boolean floor(int x, int z) {
            return combatFloor(x, z) || z >= 1 && z < COMBAT_START
                && (z <= 5 && Math.abs(x) <= 5 || x >= -5 && x <= -1 || z >= 8 && Math.abs(x) <= 3);
        }

        public List<Spawn> spawns() { return spawns; }
    }

    public record Spawn(BlockPos pos, String enemy) {}

    private PracticeLayouts() {}

    public static boolean contains(double x,double y,double z) {
        return x>=BOUNDS.minX()&&x<BOUNDS.maxX()+1&&y>=BOUNDS.minY()&&y<BOUNDS.maxY()+1
            &&z>=BOUNDS.minZ()&&z<BOUNDS.maxZ()+1;
    }

    public static Arena require(String id) {
        var arena = ARENAS.get(id);
        if (arena == null) throw new IllegalArgumentException("该练功房尚未配置场地：" + id);
        return arena;
    }

    static void register(Map<String, DungeonLayouts.Layout> layouts) {
        ARENAS.forEach((id, arena) -> layouts.put(id, new DungeonLayouts.Layout("PRACTICE_1", BOUNDS, 0, 1,
            (world, origin) -> build(arena, world, origin), (world, origin, gate, open) -> {})));
    }

    private static Map<String, Arena> createArenas() {
        var rooms = new LinkedHashMap<String, Arena>();
        rooms.put(PracticeRoom.MOB.id(), arena(
            (x,z) -> Math.abs(x)<=19 && Math.abs(z)<=19 && Math.abs(x)+Math.abs(z)<=32, 12,
            Blocks.POLISHED_GRANITE, Blocks.SMOOTH_QUARTZ, Blocks.TUFF_BRICKS, PracticeLayouts::martialHall,
            // The enemy marker stays empty: the encounter service draws each mob from PracticeMobs per spawn.
            "", -11,18, 11,18, -11,40, 11,40));
        rooms.put(PracticeRoom.GOLD.id(), arena(
            (x,z) -> Math.abs(x)<=19 && Math.abs(z)<=19, 12,
            Blocks.POLISHED_ANDESITE, Blocks.CUT_COPPER.waxed().unaffected(), Blocks.DEEPSLATE_BRICKS, PracticeLayouts::mint,
            "goblin_captain", -11,18, 11,18, -11,40, 11,40));
        rooms.put(PracticeRoom.CURRENCY.id(), arena(
            (x,z) -> Math.abs(x)<=22 && Math.abs(z)<=13 || Math.abs(x)<=11 && Math.abs(z)<=18, 11,
            Blocks.POLISHED_DEEPSLATE, Blocks.POLISHED_BLACKSTONE, Blocks.DEEPSLATE_BRICKS, PracticeLayouts::engraving,
            ClassicEnemyKind.MYSTERIOUS_AWARENESS_SPACE_SOLDIER.id, -14,22, 14,22, -14,36, 14,36));

        material(rooms, "goblin_camp", arena(
            (x,z) -> Math.abs(x)<=19 && Math.abs(z)<=17 && (z>=-9 || Math.abs(x)<=12), 9,
            Blocks.ANDESITE, Blocks.SPRUCE_PLANKS, Blocks.TUFF_BRICKS, PracticeLayouts::goblinFoundry,
            "goblin", -8,17, 8,17, -11,39, 11,39));
        material(rooms, "dragon_tower", arena(
            (x,z) -> Math.abs(x)<=19 && Math.abs(z)<=19 && Math.abs(x)+Math.abs(z)<=30, 14,
            Blocks.POLISHED_DIORITE, Blocks.CUT_COPPER.waxed().unaffected(), Blocks.TUFF_BRICKS, PracticeLayouts::towerLift,
            "dragonman", -10,18, 10,18, -10,41, 10,41));
        material(rooms, "castle_palace", arena(
            (x,z) -> Math.abs(x)<=24 && Math.abs(z)<=12 || Math.abs(x)<=8 && Math.abs(z)<=17, 12,
            Blocks.POLISHED_ANDESITE, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BRICKS, PracticeLayouts::palaceGallery,
            PalaceEnemyKind.EXPELLER.id, -16,21, 16,21, -16,37, 16,37));
        material(rooms, "gbl_church", arena(
            (x,z) -> Math.abs(x)<=8 && Math.abs(z)<=19 || Math.abs(x)<=22 && Math.abs(z)<=9, 13,
            Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE, Blocks.DEEPSLATE_BRICKS, PracticeLayouts::reliquary,
            GblEnemyKind.CULTIST.id, -14,27, 14,27, -4,17, 4,41));
        material(rooms, "behemoth_forbidden", arena(
            (x,z) -> z>=-12 && ellipse(x,z-1,23,18), 11,
            Blocks.TUFF, Blocks.POLISHED_BASALT, Blocks.DEEPSLATE_BRICKS, PracticeLayouts::ribCavern,
            ForbiddenEnemyKind.CULTIST.id, -9,21, 9,21, -12,37, 12,37));
        material(rooms, "dark_city", arena(
            (x,z) -> Math.abs(x)<=24 && (Math.abs(x)>=8 && Math.abs(z)<=13 || Math.abs(z)<=5), 10,
            Blocks.POLISHED_BLACKSTONE, Blocks.CHISELED_DEEPSLATE, Blocks.BLACKSTONE, PracticeLayouts::boneVault,
            DarkCityEnemyKind.HEADLESS_SKELETON.id, -16,21, 16,21, -16,37, 16,37));
        material(rooms, "skasa", arena(
            (x,z) -> Math.abs(z)<=18 && ellipse(x,z,16+z/3,19), 12,
            Blocks.POLISHED_DIORITE, Blocks.CALCITE, Blocks.PACKED_ICE, PracticeLayouts::iceCave,
            SkasaEnemyKind.MALE_WARRIOR.id, -7,21, 7,21, -11,37, 11,37));
        material(rooms, "moonlight_tavern", arena(
            (x,z) -> Math.abs(z)<=16 && x>=-18+z/4 && x<=18+z/3, 9,
            Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS, Blocks.MUD_BRICKS, PracticeLayouts::tavernCellar,
            ClassicEnemyKind.MOONLIGHT_TAVERN_CITY_THUG.id, -10,18, 9,18, -9,40, 13,40));
        material(rooms, "hamelin", arena(
            (x,z) -> x>=-22 && x<=8 && z>=-15 && z<=-2 || Math.abs(x)<=11 && Math.abs(z)<=6
                || x>=-8 && x<=22 && z>=2 && z<=17, 9,
            Blocks.MUD_BRICKS, Blocks.COBBLED_DEEPSLATE, Blocks.MOSSY_COBBLESTONE, PracticeLayouts::crookedCourts,
            ClassicEnemyKind.HAMELIN_MAD_THIEF.id, -14,20, 4,20, -4,39, 14,39));
        material(rooms, "pursuit", arena(
            (x,z) -> z>=-14 && z<=18 && Math.abs(x)<=(z<2?15:25-(z-2)), 10,
            Blocks.SMOOTH_STONE, Blocks.IRON_BLOCK, Blocks.DEEPSLATE_BRICKS, PracticeLayouts::armory,
            ClassicEnemyKind.PURSUIT_CARTEL_SOLDIER.id, -10,18, 10,18, -12,36, 12,36));
        material(rooms, "sea_train", arena(
            (x,z) -> Math.abs(x)<=19 && z>=-18 && z<=20, 11,
            Blocks.POLISHED_ANDESITE, Blocks.DARK_PRISMARINE, Blocks.SPRUCE_PLANKS, PracticeLayouts::freightPlatforms,
            ClassicEnemyKind.SEA_TRAIN_CROC_SPEARMAN.id, -10,18, 10,18, -10,41, 10,41));
        material(rooms, "time_rift", arena(
            (x,z) -> Math.abs(x)<=21 && Math.abs(z)<=18
                && (Math.abs(x)>=9 || Math.abs(z)>=9 || Math.abs(x)<=3 || Math.abs(z)<=3), 13,
            Blocks.POLISHED_BLACKSTONE, Blocks.CHISELED_TUFF_BRICKS, Blocks.DEEPSLATE_TILES, PracticeLayouts::timeGates,
            ClassicEnemyKind.TIME_RIFT_IMPERIAL_SOLDIER.id, -13,19, 13,19, -13,39, 13,39));
        material(rooms, "mysterious_awareness", arena(
            (x,z) -> z>=-15 && z<=17 && Math.abs(x)<=10+(z+15)/2 && ellipse(x,z+6,30,26), 12,
            Blocks.SMOOTH_QUARTZ, Blocks.PURPUR_BLOCK, Blocks.QUARTZ_BRICKS, PracticeLayouts::memoryArchive,
            ClassicEnemyKind.MYSTERIOUS_AWARENESS_AXE_PUPPET.id, -8,21, 8,21, -14,40, 14,40));
        material(rooms, "grandi", arena(
            (x,z) -> Math.abs(x)<=23 && Math.abs(z)<=16 && (Math.abs(x)>=9 || Math.abs(z)<=6), 12,
            Blocks.POLISHED_DEEPSLATE, Blocks.CUT_COPPER.waxed().unaffected(), Blocks.DEEPSLATE_BRICKS, PracticeLayouts::generatorHall,
            ClassicEnemyKind.GRANDI_ASSAULT_GRANITE.id, -16,20, 16,20, -16,38, 16,38));
        material(rooms, "central_park", arena(
            (x,z) -> ellipse(x+8,z+5,14,15) || ellipse(x-8,z-6,14,15), 11,
            Blocks.TUFF, Blocks.MOSSY_STONE_BRICKS, Blocks.ROOTED_DIRT, PracticeLayouts::rootCaverns,
            ClassicEnemyKind.CENTRAL_PARK_GIZA_GUARD.id, -12,18, 1,25, -3,34, 13,41));
        return Map.copyOf(rooms);
    }

    private static void material(Map<String, Arena> rooms, String dungeon, Arena arena) {
        rooms.put(PracticeRoom.materialId(dungeon), arena);
    }

    private static Arena arena(FloorPlan floor, int height, Block paving, Block inlay, Block wall,
                                Consumer<Builder> details, String enemy, int... clusters) {
        var spawns = new ArrayList<Spawn>(PracticeRoom.WAVE_SIZE);
        for (int i = 0; i < clusters.length; i += 2) {
            for (int dx : new int[]{-2,0,2}) {
                spawns.add(new Spawn(new BlockPos(clusters[i]+dx,0,clusters[i+1]),enemy));
            }
        }
        return new Arena(floor,height,new Palette(paving,inlay,wall),details,List.copyOf(spawns));
    }

    private static boolean ellipse(int x, int z, int width, int depth) {
        return (double)x*x/(width*width)+(double)z*z/(depth*depth)<=1;
    }

    private static void build(Arena arena, ServerLevel world, BlockPos origin) {
        var b = new Builder(world,origin);
        for (int z=0; z<=END_Z; z++) for (int x=-EDGE_X; x<=EDGE_X; x++) {
            boolean inside = arena.floor(x,z);
            boolean edge = arena.floor(x-1,z) || arena.floor(x+1,z) || arena.floor(x,z-1) || arena.floor(x,z+1);
            if (!inside && !edge) continue;
            int roof = z <= 8 ? 5 : arena.height;
            if (inside) {
                b.put(x,-2,z,Blocks.DEEPSLATE_BRICKS);
                boolean lit = x%5==0 && z%5==0 || (x+z)%4==0
                    && (!arena.floor(x-1,z) || !arena.floor(x+1,z) || !arena.floor(x,z-1) || !arena.floor(x,z+1));
                Block paving = z < COMBAT_START ? Blocks.POLISHED_ANDESITE : arena.palette.floor();
                b.put(x,-1,z,lit?Blocks.SEA_LANTERN:(x==0 || z%9==0)?arena.palette.inlay():paving);
                b.put(x,roof,z,z%7==0?Blocks.POLISHED_BASALT:Blocks.DEEPSLATE_BRICKS);
                if(z==8&&arena.floor(x,z+1))b.fill(x,roof+1,z,x,arena.height,z,Blocks.DEEPSLATE_BRICKS);
            } else {
                for (int y=-2;y<=roof+1;y++) {
                    Block material = z < COMBAT_START ? Blocks.DEEPSLATE_BRICKS : arena.palette.wall();
                    if (y==2 || y==roof || y<=0) material = Blocks.DEEPSLATE_TILES;
                    if (z%7==0 || x%7==0 && (z==0 || z==END_Z)) material = Blocks.POLISHED_BASALT;
                    b.put(x,y,z,material);
                }
            }
        }
        for(int x:new int[]{-4,4}){
            b.put(x,0,COMBAT_START,Blocks.CHISELED_DEEPSLATE);
            b.fill(x,1,COMBAT_START,x,3,COMBAT_START,Blocks.POLISHED_BASALT);
        }
        b.fill(-4,4,COMBAT_START,4,4,COMBAT_START,Blocks.DEEPSLATE_BRICKS);
        b.put(-3,3,COMBAT_START,Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.HALF,Half.TOP).setValue(StairBlock.FACING,Direction.WEST));
        b.put(3,3,COMBAT_START,Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.HALF,Half.TOP).setValue(StairBlock.FACING,Direction.EAST));
        b.hang(-3,3,3,5);
        b.hang(3,3,3,5);
        arena.decorations.accept(b);
    }

    private static void martialHall(Builder b) {
        // A neutral drill hall: iron stock racks, a quartz emblem and wall lanterns for borrowed dungeon mobs.
        for (int side : new int[]{-1,1}) {
            b.rack(side*15,44,Blocks.IRON_BLOCK);
            b.fill(side*15-2,1,43,side*15+2,2,43,Blocks.SPRUCE_FENCE);
            b.hang(side*15,41,4,9);
        }
        b.frame(0,46,4,6,Blocks.TUFF_BRICKS,Blocks.SMOOTH_QUARTZ);
        for (int z : new int[]{20,29,38}) {
            b.hang(-19,z,7,12);
            b.hang(19,z,7,12);
        }
    }

    private static void mint(Builder b) {
        // Press head and guides over the casting bed, with two barred stock racks.
        b.fill(-7,0,47,7,1,48,Blocks.POLISHED_BLACKSTONE);
        b.fill(-6,5,47,6,6,48,Blocks.CUT_COPPER.waxed().unaffected());
        for (int x : new int[]{-6,6}) {
            b.fill(x,2,47,x,5,48,Blocks.GILDED_BLACKSTONE);
            b.lamp(x,6,46);
        }
        b.fill(-2,4,47,2,5,47,Blocks.IRON_BLOCK);
        b.fill(-1,2,47,1,2,47,Blocks.CHIPPED_ANVIL);
        b.fill(-1,3,46,1,3,47,Blocks.IRON_BARS);
        for (int x : new int[]{-15,15}) {
            b.rack(x,46,Blocks.RAW_GOLD_BLOCK);
            b.fill(x-2,1,45,x+2,2,45,Blocks.IRON_BARS);
            b.hang(x,44,6,12);
        }
        for (int z : new int[]{19,28,37}) {
            b.hang(-18,z,7,12);
            b.hang(18,z,7,12);
        }
    }

    private static void engraving(Builder b) {
        for (int side : new int[]{-1,1}) {
            for (int z : new int[]{19,25,31,37}) {
                int x=side*22;
                b.fill(x,1,z-2,x,2,z+2,Blocks.POLISHED_BLACKSTONE);
                b.fill(x,3,z-1,x,4,z+1,Blocks.AMETHYST_BLOCK);
                b.put(x-side,3,z,Blocks.TINTED_GLASS);
                b.put(x,5,z,Blocks.SEA_LANTERN);
            }
            b.fill(side*17,8,17,side*17,8,41,Blocks.IRON_BLOCK);
            for(int z:new int[]{21,29,37}) b.hang(side*17,z,6,8);
        }
        b.frame(0,45,5,6,Blocks.POLISHED_BLACKSTONE,Blocks.AMETHYST_BLOCK);
    }

    private static void goblinFoundry(Builder b) {
        for (int x : new int[]{-15,0,15}) {
            b.rack(x,43,Blocks.RAW_IRON_BLOCK);
            b.fill(x-3,4,41,x+3,4,45,Blocks.SPRUCE_PLANKS);
            b.fill(x-3,0,43,x-3,4,43,Blocks.STRIPPED_SPRUCE_LOG);
            b.fill(x+3,0,43,x+3,4,43,Blocks.STRIPPED_SPRUCE_LOG);
            b.hang(x,42,2,4);
        }
        b.rack(-18,29,Blocks.WOOL.brown());
        b.rack(18,29,Blocks.COPPER_BLOCK.waxed().unaffected());
    }

    private static void towerLift(Builder b) {
        for (int x : new int[]{-8,8}) {
            b.fill(x,2,45,x,13,45,Blocks.IRON_BLOCK);
            b.hang(x,43,6,14);
        }
        b.fill(-8,8,43,8,8,46,Blocks.CUT_COPPER.waxed().unaffected());
        b.fill(-5,9,43,5,9,43,Blocks.IRON_BARS);
        b.fill(-2,9,44,2,10,45,Blocks.AMETHYST_BLOCK);
        for (int y : new int[]{4,8,12}) b.fill(-8,y,46,8,y,46,Blocks.POLISHED_DIORITE);
    }

    private static void palaceGallery(Builder b) {
        for (int x : new int[]{-18,-10,0,10,18}) {
            int z=x==0?44:40;
            b.frame(x,z,3,6,Blocks.QUARTZ_PILLAR,Blocks.GLOWSTONE);
            b.fill(x-3,0,z-1,x+3,0,z,Blocks.SMOOTH_QUARTZ);
            b.hang(x,z-2,5,12);
        }
        b.fill(-22,9,39,22,9,39,Blocks.QUARTZ_BRICKS);
    }

    private static void reliquary(Builder b) {
        b.frame(0,46,6,9,Blocks.CHISELED_STONE_BRICKS,Blocks.STAINED_GLASS.white());
        for (int x : new int[]{-18,-11,11,18}) {
            b.frame(x,37,2,5,Blocks.CHISELED_STONE_BRICKS,Blocks.GLOWSTONE);
            b.put(x,1,36,Blocks.BONE_BLOCK);
            b.hang(x,35,6,13);
        }
        b.fill(-5,0,45,5,1,46,Blocks.POLISHED_ANDESITE);
    }

    private static void ribCavern(Builder b) {
        for (int z : new int[]{24,31,38}) {
            for (int side : new int[]{-1,1}) {
                int edge=side*(z==38?19:21);
                b.fill(edge,1,z,edge,8,z,Blocks.BONE_BLOCK);
                b.fill(edge,8,z,side*12,8,z,Blocks.BONE_BLOCK);
                b.hang(side*12,z,6,8);
            }
            b.fill(-12,10,z,12,10,z,Blocks.DEEPSLATE_BRICKS);
        }
        b.frame(0,45,4,4,Blocks.TUFF_BRICKS,Blocks.AMETHYST_BLOCK);
    }

    private static void boneVault(Builder b) {
        for (int x : new int[]{-19,-12,12,19}) {
            b.frame(x,41,2,6,Blocks.POLISHED_BLACKSTONE,Blocks.BONE_BLOCK);
            b.put(x,2,40,Blocks.SOUL_LANTERN);
        }
        for (int x : new int[]{-7,7}) {
            b.fill(x,4,24,x,4,34,Blocks.POLISHED_BASALT);
            b.fill(x,1,22,x,5,22,Blocks.CHISELED_DEEPSLATE);
            b.fill(x,1,36,x,5,36,Blocks.CHISELED_DEEPSLATE);
        }
    }

    private static void iceCave(Builder b) {
        for (int side : new int[]{-1,1}) {
            for (int i=0;i<4;i++) {
                int x=side*(8+i*2);
                b.fill(x,1,44-i,x,3+i,44-i,Blocks.BLUE_ICE);
                b.put(x,1,43-i,Blocks.SEA_LANTERN);
                b.put(x,4+i,44-i,Blocks.CALCITE);
            }
            b.fill(side*8,8,36,side*8,11,36,Blocks.PACKED_ICE);
            b.hang(side*8,36,6,8);
        }
        b.frame(0,46,5,5,Blocks.PACKED_ICE,Blocks.CALCITE);
    }

    private static void tavernCellar(Builder b) {
        b.fill(-10,0,43,8,1,44,Blocks.STRIPPED_DARK_OAK_LOG);
        b.fill(8,0,39,9,1,44,Blocks.DARK_OAK_PLANKS);
        for (int x : new int[]{-13,-6,1,13}) {
            b.rack(x,44,Blocks.BARREL);
            b.fill(x-2,0,43,x+2,2,43,Blocks.SPRUCE_FENCE);
            b.hang(x,41,4,9);
        }
        b.fill(-13,6,42,15,6,42,Blocks.DARK_OAK_PLANKS);
    }

    private static void crookedCourts(Builder b) {
        for (int side : new int[]{-1,1}) {
            int x=side*18,z=29+side*12;
            b.fill(x-3,0,z,x+3,4,z,Blocks.MOSSY_COBBLESTONE);
            b.fill(x-4,4,z-2,x+4,4,z+1,Blocks.STRIPPED_SPRUCE_LOG);
            b.fill(x-3,5,z-1,x+2,5,z,Blocks.SPRUCE_PLANKS);
            b.rack(x,z+side,Blocks.WOOL.brown());
            b.hang(x,z-side,2,4);
        }
        b.fill(-7,6,29,7,6,29,Blocks.STRIPPED_SPRUCE_LOG);
        b.hang(0,29,4,6);
    }

    private static void armory(Builder b) {
        for (int x : new int[]{-14,-7,0,7,14}) {
            int z=46-Math.abs(x)/2;
            b.rack(x,z,Blocks.IRON_BLOCK);
            b.fill(x-2,3,z,x+2,3,z,Blocks.CUT_COPPER.waxed().unaffected());
            b.lamp(x,4,z);
        }
        b.fill(-3,0,45,3,1,46,Blocks.COBBLED_DEEPSLATE);
        b.fill(-2,2,46,2,3,46,Blocks.RAW_IRON_BLOCK);
    }

    private static void freightPlatforms(Builder b) {
        for (int z=15;z<=45;z++) {
            b.put(0,-3,z,Blocks.STONE_BRICKS);
            b.put(0,-2,z,Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE,RailShape.NORTH_SOUTH));
            b.put(0,-1,z,Blocks.GLASS);
            if (z%4==0) {
                b.put(-1,-1,z,Blocks.SEA_LANTERN);
                b.put(1,-1,z,Blocks.SEA_LANTERN);
            }
        }
        for (int x : new int[]{-15,15}) {
            b.rack(x,47,Blocks.RAW_IRON_BLOCK);
            b.rack(x,44,Blocks.STRIPPED_SPRUCE_LOG);
            b.fill(x-2,7,44,x+2,7,48,Blocks.DARK_PRISMARINE);
            b.hang(x,46,4,7);
        }
        b.fill(-18,8,28,18,8,28,Blocks.IRON_BLOCK);
        for(int x : new int[]{-13,0,13}) b.hang(x,28,5,8);
    }

    private static void timeGates(Builder b) {
        for (int x : new int[]{-13,13}) {
            b.frame(x,45,4,7,Blocks.CHISELED_TUFF_BRICKS,Blocks.AMETHYST_BLOCK);
            b.hang(x,42,5,13);
        }
        for (int x : new int[]{-6,6}) for (int z : new int[]{23,35}) {
            b.fill(x-1,0,z-1,x+1,6,z+1,Blocks.POLISHED_BLACKSTONE);
            b.fill(x,2,z-1,x,4,z-1,Blocks.AMETHYST_BLOCK);
            b.put(x,7,z,Blocks.LODESTONE);
        }
    }

    private static void memoryArchive(Builder b) {
        for (int x : new int[]{-15,-9,-3,3,9,15}) {
            int z=45-Math.abs(x)/5;
            b.frame(x,z,2,8,Blocks.PURPUR_PILLAR,Blocks.AMETHYST_BLOCK);
            b.fill(x-2,0,z-1,x+2,0,z,Blocks.SMOOTH_QUARTZ);
            b.hang(x,z-2,5,12);
        }
        b.fill(-15,9,42,15,9,42,Blocks.SMOOTH_QUARTZ);
    }

    private static void generatorHall(Builder b) {
        for (int x : new int[]{-18,-12,12,18}) {
            b.fill(x-2,0,42,x+2,1,44,Blocks.POLISHED_DEEPSLATE);
            b.fill(x-1,2,43,x+1,6,43,Blocks.CUT_COPPER.waxed().unaffected());
            b.fill(x,6,43,x,6,45,Blocks.CUT_COPPER.waxed().unaffected());
            b.fill(x,6,45,x,10,45,Blocks.CUT_COPPER.waxed().unaffected());
            for(int y:new int[]{3,5}) b.lamp(x,y,42);
        }
        b.fill(-21,9,29,21,9,29,Blocks.CUT_COPPER.waxed().unaffected());
        for(int x:new int[]{-19,-10,0,10,19}) b.lamp(x,8,29);
    }

    private static void rootCaverns(Builder b) {
        for (int x : new int[]{-16,-10,9,17}) {
            int z=x<0?33:44;
            b.fill(x,0,z,x,8,z,Blocks.STRIPPED_DARK_OAK_LOG);
            b.fill(x-2,0,z-1,x+2,0,z,Blocks.MOSS_BLOCK);
            b.fill(x-2,7,z-2,x+2,7,z+1,Blocks.DARK_OAK_WOOD);
            b.put(x,2,z-1,Blocks.AMETHYST_BLOCK);
            b.hang(x,z-2,4,7);
        }
        b.fill(-5,8,29,12,8,29,Blocks.DARK_OAK_WOOD);
        b.hang(2,29,6,8);
    }

    private record Builder(ServerLevel world, BlockPos origin) {
        void put(int x,int y,int z,Block block) { put(x,y,z,block.defaultBlockState()); }
        void put(int x,int y,int z,BlockState state) {
            world.setBlock(origin.offset(x,y,z),state,Block.UPDATE_CLIENTS|Block.UPDATE_SUPPRESS_DROPS);
        }

        void fill(int x1,int y1,int z1,int x2,int y2,int z2,Block block) {
            for(int x=Math.min(x1,x2);x<=Math.max(x1,x2);x++)
                for(int y=y1;y<=y2;y++)
                    for(int z=z1;z<=z2;z++) put(x,y,z,block);
        }

        void hang(int x,int z,int y,int roof) {
            for (int i=y+1;i<roof;i++) put(x,i,z,Blocks.IRON_CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS,Direction.Axis.Y));
            put(x,y,z,Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true));
        }

        void lamp(int x,int y,int z) {
            put(x,y,z,Blocks.COPPER_BULB.waxed().unaffected().defaultBlockState().setValue(CopperBulbBlock.LIT,true));
        }

        void frame(int x,int z,int halfWidth,int height,Block casing,Block backing) {
            fill(x-halfWidth,0,z,x+halfWidth,height,z,casing);
            fill(x-halfWidth+1,2,z,x+halfWidth-1,height-1,z,backing);
            fill(x-halfWidth+1,height,z-1,x+halfWidth-1,height,z,casing);
        }

        void rack(int x,int z,Block stock) {
            fill(x-2,0,z,x-2,3,z,Blocks.STRIPPED_SPRUCE_LOG);
            fill(x+2,0,z,x+2,3,z,Blocks.STRIPPED_SPRUCE_LOG);
            fill(x-2,0,z,x+2,0,z,Blocks.SPRUCE_PLANKS);
            fill(x-2,2,z,x+2,2,z,Blocks.SPRUCE_PLANKS);
            fill(x-1,1,z,x+1,1,z,stock);
            put(x,3,z,stock);
        }
    }
}
