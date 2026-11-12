package cn.yumis.fight.modules.mapgeneration.application;

import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import java.util.*;

/** Plans foundations from authored ground contacts, independently of the roof/canopy bounding box. */
final class MapFoundations {
    record Column(int bottom,int top) {}
    record Footprint(Map<Long,Column> columns,Map<Long,Integer> feet) {
        boolean contains(int x,int z) {return feet.containsKey(BlockPos.asLong(x,0,z));}
        double distance(int x,int z,int radius) {
            double nearest=Double.MAX_VALUE;
            for(int dz=-radius;dz<=radius;dz++)for(int dx=-radius;dx<=radius;dx++)if(contains(x+dx,z+dz))nearest=Math.min(nearest,Math.hypot(dx,dz));
            return nearest;
        }
    }
    private MapFoundations() {}

    static Footprint footprint(ServerLevel level,StructureTemplate template,int referenceY) {
        CompoundTag tag=template.save(new CompoundTag());var palette=tag.getListOrEmpty("palette");
        List<BlockState> states=new ArrayList<>();
        for(Tag item:palette)states.add(NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(Registries.BLOCK),(CompoundTag)item));
        Map<Long,Column> columns=new HashMap<>();Map<Long,Integer> feet=new HashMap<>();
        for(Tag item:tag.getListOrEmpty("blocks")) {
            CompoundTag cell=(CompoundTag)item;var pos=cell.getListOrEmpty("pos");
            int x=pos.getIntOr(0,0),y=pos.getIntOr(1,0),z=pos.getIntOr(2,0);BlockState state=states.get(cell.getIntOr("state",0));
            if(state.isAir())continue;long key=BlockPos.asLong(x,0,z);
            columns.merge(key,new Column(y,y+1),(a,b)->new Column(Math.min(a.bottom,b.bottom),Math.max(a.top,b.top)));
            if(y<referenceY&&!state.is(BlockTags.LEAVES)&&!state.canBeReplaced()&&state.getFluidState().isEmpty()&&!state.getCollisionShape(level,new BlockPos(x,y,z)).isEmpty())feet.merge(key,y,Math::min);
        }
        if(feet.isEmpty())throw new IllegalArgumentException("贴地设施缺少基准面下的承托方块");
        return new Footprint(Map.copyOf(columns),Map.copyOf(feet));
    }

    static void plan(GenerationPlan p) {
        Set<Integer> reserved=new HashSet<>();
        for(var f:p.facilities)for(int z=f.position().getZ();z<f.position().getZ()+f.template().getSize().getZ();z++)for(int x=f.position().getX();x<f.position().getX()+f.template().getSize().getX();x++)
            if(p.inside(x,z)&&(f.footprint()==null||f.containsGround(x,z)))reserved.add(p.index(x,z));
        for(var f:p.facilities) {
            if(f.footprint()==null)continue;
            boolean retaining=f.definition().foundation.equals("terrain_retaining");
            Map<Integer,Integer> tops=new TreeMap<>();Set<Integer> ground=new HashSet<>();
            for(var foot:f.footprint().feet.entrySet()) {
                BlockPos local=BlockPos.of(foot.getKey());int x=f.position().getX()+local.getX(),z=f.position().getZ()+local.getZ(),i=p.index(x,z);
                ground.add(i);
                boolean edge=false;
                for(Direction direction:Direction.Plane.HORIZONTAL)if(!f.footprint().contains(local.getX()+direction.getStepX(),local.getZ()+direction.getStepZ()))edge=true;
                if(retaining||edge&&hash(p,Math.floorDiv(x,3),0,Math.floorDiv(z,3))%3==0)tops.put(i,f.position().getY()+foot.getValue()-1);
            }
            // A small stepped skirt joins the roots to local terrain; it never extends into another facility or road.
            if(retaining&&p.mode==GenerationPlan.Mode.TERRAIN)for(int i:ground) {
                int top=f.position().getY()+f.footprint().feet.get(BlockPos.asLong(p.x(i)-f.position().getX(),0,p.z(i)-f.position().getZ()))-1;
                for(int dz=-2;dz<=2;dz++)for(int dx=-2;dx<=2;dx++) {
                    int distance=Math.abs(dx)+Math.abs(dz),x=p.x(i)+dx,z=p.z(i)+dz;
                    if(distance==0||distance>2||!p.inside(x,z))continue;int j=p.index(x,z);
                    if(reserved.contains(j)||p.roadCells.contains(j)||p.keepClear.contains(j)||p.water[j])continue;
                    tops.merge(j,top-distance,Math::max);
                }
            }
            for(var e:tops.entrySet()) {
                int i=e.getKey(),x=p.x(i),z=p.z(i),top=e.getValue();Integer road=p.roadHeights.get(i);
                if(road!=null)top=Math.min(top,Math.floorDiv(road-1,2)-1);
                int bottom=retaining&&p.mode==GenerationPlan.Mode.TERRAIN?p.bearing[i]-1:Math.max(p.mode==GenerationPlan.Mode.TERRAIN?p.bearing[i]-1:p.level.getMinY(),top-1);
                for(int y=Math.max(bottom,p.level.getMinY());y<=top;y++) {
                    if(!p.level.isInsideBuildHeight(y))throw new IllegalArgumentException("设施地基超出世界高度: "+f.definition().id);
                    BlockState material=retaining&&!ground.contains(i)&&y==top?MapPlacer.surfaceMaterial(p.biomes[i]):stone(p,x,y,z);
                    p.foundationBlocks.putIfAbsent(new BlockPos(x,y,z),material);
                }
            }
        }
        p.metrics.put("fitted_foundation_blocks",p.foundationBlocks.size());
    }

    private static BlockState stone(GenerationPlan p,int x,int y,int z) {
        String biome=p.biomes[p.index(x,z)];long roll=hash(p,Math.floorDiv(x,2),Math.floorDiv(y,3),Math.floorDiv(z,2))%10;
        if(biome.contains("badlands"))return (roll<6?Blocks.RED_SANDSTONE:roll<8?Blocks.CUT_RED_SANDSTONE:Blocks.TERRACOTTA).defaultBlockState();
        if(biome.contains("desert")||biome.contains("beach")||biome.contains("warm_ocean"))return (roll<6?Blocks.SANDSTONE:roll<8?Blocks.CUT_SANDSTONE:Blocks.SMOOTH_SANDSTONE).defaultBlockState();
        boolean lush=biome.contains("forest")||biome.contains("jungle")||biome.contains("swamp");
        return (lush&&roll<2?Blocks.MOSSY_COBBLESTONE:roll<5?Blocks.STONE:roll<8?Blocks.COBBLESTONE:Blocks.ANDESITE).defaultBlockState();
    }
    private static long hash(GenerationPlan p,int x,int y,int z) {
        long value=p.level.getSeed()^((long)x*341873128712L)^((long)z*132897987541L)^((long)y*42317861L);
        value=(value^(value>>>30))*0xbf58476d1ce4e5b9L;return (value^(value>>>27))&Long.MAX_VALUE;
    }
}
