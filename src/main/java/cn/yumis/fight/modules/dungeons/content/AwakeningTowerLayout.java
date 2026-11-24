package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.AwakeningTower;
import cn.yumis.fight.platform.ItemData;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

/** Places the authored arena and its south gate at the same floor height. */
public final class AwakeningTowerLayout {
    private static final String VERSION_PREFIX="AWAKENING_TOWER_";
    public static final String VERSION=VERSION_PREFIX+"2";
    private static final int GATE_Z=102, ENTRY_Z=GATE_Z+8, CENTER_Z=50;
    private static final int FLOOR_Y=-1;
    private static final int FLAGS=Block.UPDATE_CLIENTS|Block.UPDATE_SUPPRESS_DROPS;
    // The outermost column on each side is reserved for the void barriers.
    private static final DungeonLayouts.Bounds BOUNDS=new DungeonLayouts.Bounds(-51,-4,-1,50,36,114);

    private AwakeningTowerLayout(){}

    public static DungeonLayouts.Layout layout(AwakeningTower tower){
        return new DungeonLayouts.Layout(VERSION,BOUNDS,0,1,(world,origin)->build(world,origin,tower),
            (world,origin,gate,open)->{});
    }

    public static boolean recorded(String marker){return marker.contains(VERSION_PREFIX);}

    private static int entranceX(AwakeningTower tower){return tower==AwakeningTower.SAND_MARKET?16:0;}

    public static Vec3 entry(int cell,AwakeningTower tower){return new Vec3(cell*512+entranceX(tower)+.5,80,ENTRY_Z+.5);}

    public static Vec3 guard(int cell){return new Vec3(cell*512+.5,80,CENTER_Z+.5);}

    public static boolean containsBattle(int cell, Vec3 point) {
        return Math.abs(point.x - cell * 512) <= 48 && point.z >= 2 && point.z < GATE_Z - 2
            && point.y >= 76 && point.y < 112;
    }

    public static Vec3 insideBattle(int cell, Vec3 point) {
        return new Vec3(Math.clamp(point.x, cell * 512 - 47, cell * 512 + 47),
            Math.clamp(point.y, 77, 111), Math.clamp(point.z, 3, GATE_Z - 3));
    }

    public static Vec3 ground(ServerLevel world, int cell, Vec3 point, Vec3 fallback) {
        Vec3 inside = insideBattle(cell, point);
        BlockPos block = BlockPos.containing(inside);
        for (int offset : new int[]{0, 1, -1, 2, -2}) {
            BlockPos position = block.above(offset);
            if (walkable(world, position)) return Vec3.atBottomCenterOf(position);
        }
        return fallback;
    }

    public static boolean walkable(ServerLevel world, BlockPos position) {
        BlockPos below = position.below();
        if (!world.getBlockState(below).isFaceSturdy(world, below, Direction.UP)
            || !world.getFluidState(position).isEmpty()) return false;
        Vec3 p = Vec3.atBottomCenterOf(position);
        return !world.getBlockCollisions(null, new AABB(p.x - .38, p.y, p.z - .38,
            p.x + .38, p.y + 2.1, p.z + .38)).iterator().hasNext();
    }

    private static void build(ServerLevel world,BlockPos origin,AwakeningTower tower){
        var arena=template(world,tower,"arena");
        var gate=template(world,tower,"gate");
        int x=entranceX(tower);
        var arenaAt=new BlockPos(-50,-4,0);
        var gateAt=new BlockPos(x-gate.getSize().getX()/2,-3,GATE_Z);
        validateBounds(tower,arena,arenaAt);
        validateBounds(tower,gate,gateAt);
        place(world,arena,origin.offset(arenaAt),tower);
        place(world,gate,origin.offset(gateAt),tower);

        int bridgeStart=tower==AwakeningTower.SAND_MARKET?96:100;
        var paving=world.getBlockState(origin.offset(x,FLOOR_Y,bridgeStart-1));
        for(int z=bridgeStart;z<=GATE_Z;z++)for(int lane=-1;lane<=1;lane++){
            BlockPos floor=origin.offset(x+lane,FLOOR_Y,z);
            if(world.getBlockState(floor).isAir())world.setBlock(floor,paving,FLAGS);
        }
        protectVoidEdges(world,origin);
    }

    private static void protectVoidEdges(ServerLevel world, BlockPos origin) {
        int minY = origin.getY() + BOUNDS.minY();
        int floorY = origin.getY() + FLOOR_Y;
        int maxY = origin.getY() + BOUNDS.maxY();
        var cursor = new BlockPos.MutableBlockPos();
        Set<BlockPos> foundation = new HashSet<>();
        for (int x = BOUNDS.minX() + 1; x < BOUNDS.maxX(); x++) {
            for (int z = BOUNDS.minZ() + 1; z < BOUNDS.maxZ(); z++) {
                int worldX = origin.getX() + x;
                int worldZ = origin.getZ() + z;
                // A recessed pool still has a foundation below the main floor.
                for (int y = floorY; y >= minY; y--) {
                    if (world.getBlockState(cursor.set(worldX, y, worldZ)).isAir()) continue;
                    foundation.add(new BlockPos(worldX, floorY, worldZ));
                    break;
                }
            }
        }

        Set<BlockPos> edges = new HashSet<>();
        for (BlockPos floor : foundation) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos neighbor = floor.relative(direction);
                if (!foundation.contains(neighbor)) edges.add(neighbor);
            }
        }

        var barrier = Blocks.BARRIER.defaultBlockState();
        int flags = FLAGS | Block.UPDATE_KNOWN_SHAPE;
        for (BlockPos edge : edges) {
            for (int y = minY; y <= maxY; y++) {
                cursor.set(edge.getX(), y, edge.getZ());
                if (world.getBlockState(cursor).isAir()) world.setBlock(cursor, barrier, flags);
            }
        }
    }

    private static StructureTemplate template(ServerLevel world,AwakeningTower tower,String part){
        var id=Identifier.fromNamespaceAndPath(ItemData.NS,"towers/"+tower.template()+"/"+part);
        return world.getStructureManager().get(id)
            .orElseThrow(()->new IllegalStateException("缺少觉醒塔结构："+id));
    }

    private static void validateBounds(AwakeningTower tower,StructureTemplate template,BlockPos first){
        var last=first.offset(template.getSize()).offset(-1,-1,-1);
        if(first.getX()<=BOUNDS.minX()||first.getY()<BOUNDS.minY()||first.getZ()<=BOUNDS.minZ()
            ||last.getX()>=BOUNDS.maxX()||last.getY()>BOUNDS.maxY()||last.getZ()>=BOUNDS.maxZ())
            throw new IllegalArgumentException("觉醒塔结构超出场地边界："+tower.dungeon().name());
    }

    private static void place(ServerLevel world,StructureTemplate template,BlockPos at,AwakeningTower tower){
        var settings=new StructurePlaceSettings().setIgnoreEntities(true).setKnownShape(true);
        if(!template.placeInWorld(world,at,at,settings,world.getRandom(),FLAGS))
            throw new IllegalStateException("无法放置觉醒塔结构："+tower.dungeon().name());
    }
}
