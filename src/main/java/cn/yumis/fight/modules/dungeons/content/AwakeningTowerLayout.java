package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.AwakeningTower;
import cn.yumis.fight.platform.ItemData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/** Places the authored arena and its south gate at the same floor height. */
public final class AwakeningTowerLayout {
    private static final String VERSION_PREFIX="AWAKENING_TOWER_";
    public static final String VERSION=VERSION_PREFIX+"1";
    private static final int GATE_Z=102, ENTRY_Z=GATE_Z+8, CENTER_Z=50;
    private static final int FLAGS=Block.UPDATE_CLIENTS|Block.UPDATE_SUPPRESS_DROPS;
    private static final DungeonLayouts.Bounds BOUNDS=new DungeonLayouts.Bounds(-50,-4,0,49,36,113);

    private AwakeningTowerLayout(){}

    public static DungeonLayouts.Layout layout(AwakeningTower tower){
        return new DungeonLayouts.Layout(VERSION,BOUNDS,0,1,(world,origin)->build(world,origin,tower),
            (world,origin,gate,open)->{});
    }

    public static boolean recorded(String marker){return marker.contains(VERSION_PREFIX);}

    private static int entranceX(AwakeningTower tower){return tower==AwakeningTower.SAND_MARKET?16:0;}

    public static Vec3 entry(int cell,AwakeningTower tower){return new Vec3(cell*512+entranceX(tower)+.5,80,ENTRY_Z+.5);}

    public static Vec3 guard(int cell){return new Vec3(cell*512+.5,80,CENTER_Z+.5);}

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
        var paving=world.getBlockState(origin.offset(x,-1,bridgeStart-1));
        for(int z=bridgeStart;z<=GATE_Z;z++)for(int lane=-1;lane<=1;lane++){
            BlockPos floor=origin.offset(x+lane,-1,z);
            if(world.getBlockState(floor).isAir())world.setBlock(floor,paving,FLAGS);
        }
    }

    private static StructureTemplate template(ServerLevel world,AwakeningTower tower,String part){
        var id=Identifier.fromNamespaceAndPath(ItemData.NS,"towers/"+tower.template()+"/"+part);
        return world.getStructureManager().get(id)
            .orElseThrow(()->new IllegalStateException("缺少觉醒塔结构："+id));
    }

    private static void validateBounds(AwakeningTower tower,StructureTemplate template,BlockPos first){
        var last=first.offset(template.getSize()).offset(-1,-1,-1);
        if(first.getX()<BOUNDS.minX()||first.getY()<BOUNDS.minY()||first.getZ()<BOUNDS.minZ()
            ||last.getX()>BOUNDS.maxX()||last.getY()>BOUNDS.maxY()||last.getZ()>BOUNDS.maxZ())
            throw new IllegalArgumentException("觉醒塔结构超出场地边界："+tower.dungeon().name());
    }

    private static void place(ServerLevel world,StructureTemplate template,BlockPos at,AwakeningTower tower){
        var settings=new StructurePlaceSettings().setIgnoreEntities(true).setKnownShape(true);
        if(!template.placeInWorld(world,at,at,settings,world.getRandom(),FLAGS))
            throw new IllegalStateException("无法放置觉醒塔结构："+tower.dungeon().name());
    }
}
