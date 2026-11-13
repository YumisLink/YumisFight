package cn.yumis.fight.modules.mapgeneration.platform;

import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;

/** Small vanilla templates use the native loader/rotation, with all terrain checked before writing. */
public final class MiniDecorationPlacement {
    private MiniDecorationPlacement(){}
    public static boolean place(WorldGenLevel level,ChunkPos chunk,BlockPos center,StructureTemplate template,Rotation rotation,RandomSource random){
        var size=template.getSize();
        if(size.getX()<1||size.getZ()<1||size.getY()<1||size.getX()>7||size.getZ()>7||size.getY()>4)
            throw new IllegalArgumentException("Miniature decoration exceeds its 7 x 7 x 4 native-block envelope");
        var settings=new StructurePlaceSettings().setRotation(rotation).setIgnoreEntities(true).setKnownShape(true)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR);
        var local=template.getBoundingBox(settings,BlockPos.ZERO);
        var position=new BlockPos(center.getX()-Math.floorDiv(local.minX()+local.maxX(),2),center.getY(),center.getZ()-Math.floorDiv(local.minZ()+local.maxZ(),2));
        var box=template.getBoundingBox(settings,position);
        if(box.minX()<chunk.getMinBlockX()||box.maxX()>chunk.getMaxBlockX()||box.minZ()<chunk.getMinBlockZ()||box.maxZ()>chunk.getMaxBlockZ()||box.maxY()>=level.getMaxY())return false;
        var cursor=new BlockPos.MutableBlockPos();
        // A rigid little cart/pile must sit on a level natural surface, never on a road or over water.
        for(int x=box.minX();x<=box.maxX();x++)for(int z=box.minZ();z<=box.maxZ();z++){
            var support=cursor.set(x,box.minY()-1,z);
            var ground=level.getBlockState(support);
            if(!DecorationWorldgen.soil(ground)||!ground.isFaceSturdy(level,support,Direction.UP))return false;
            for(int y=box.minY();y<=box.maxY();y++){
                cursor.set(x,y,z);
                if(!DecorationWorldgen.open(level.getBlockState(cursor))||!level.getFluidState(cursor).isEmpty())return false;
            }
        }
        return template.placeInWorld(level,position,position,settings,random,Block.UPDATE_CLIENTS|Block.UPDATE_SUPPRESS_DROPS);
    }
}
