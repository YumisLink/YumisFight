package cn.yumis.fight.modules.arkguard.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** The shared Ark scene's address and boundaries, also consumed by its menu. */
public final class ArkWar {
    public static final Identifier BLUEPRINT=Identifier.parse("yumis_dungeon_and_fight:ark_war");
    public static final ResourceKey<Level> DIMENSION=ResourceKey.create(Registries.DIMENSION,Identifier.parse("yumis_dungeon_and_fight:dungeon"));
    public static final BlockPos ORIGIN=new BlockPos(0,96,-8192);
    public record Entrance(BlockPos pos,float yaw) {}
    public static final BlockPos CORE=ORIGIN.offset(0,4,92);
    public static final List<Plot> PLOTS=List.of(new Plot(0,-32,-46,-1),new Plot(1,32,-46,1),new Plot(2,-32,18,-1),new Plot(3,32,18,1));
    public record Plot(int index,int centerX,int centerZ,int side) {
        public boolean contains(BlockPos pos){double x=pos.getX()-ORIGIN.getX()-centerX,z=pos.getZ()-ORIGIN.getZ()-centerZ;return x*x+z*z<=28*28;}
        public Vec3 shop(){return new Vec3(ORIGIN.getX()+(side<0?-10:11),ORIGIN.getY(),ORIGIN.getZ()+centerZ+.5);}
        public BlockPos readySign(){return ORIGIN.offset(side<0?-6:6,1,centerZ);}
        public Vec3 spawn(){return Vec3.atBottomCenterOf(ORIGIN.offset(centerX,0,index<2?-73:45));}
        public List<Vec3> route(){return List.of(spawn(),Vec3.atBottomCenterOf(ORIGIN.offset(centerX,0,index<2?-17:-11)),Vec3.atBottomCenterOf(ORIGIN.offset(centerX,0,-14)),Vec3.atBottomCenterOf(ORIGIN.offset(0,0,-14)),Vec3.atBottomCenterOf(ORIGIN.offset(0,0,70)),Vec3.atBottomCenterOf(ORIGIN.offset(0,3,86)));}
        public List<BlockPos> benches(Level level){
            var result=new ArrayList<BlockPos>();int z0=index<2?-52:12,z1=index<2?-40:24;
            for(int z=z0;z<=z1;z++)for(int ax=8;ax<=14;ax++){var pos=ORIGIN.offset(side*ax,0,z);if(level.getBlockState(pos).is(Blocks.POLISHED_ANDESITE_SLAB))result.add(pos);}
            if(result.isEmpty())for(int z:index<2?new int[]{-51,-48,-45,-42}:new int[]{13,16,19,22})for(int ax:new int[]{9,13})result.add(ORIGIN.offset(side*ax,0,z));
            result.sort(Comparator.<BlockPos>comparingInt(p->p.getZ()).thenComparingInt(p->p.getX()));return List.copyOf(result);
        }
        public int bench(Level level,BlockPos pos){return benches(level).indexOf(pos);}
        public boolean deployment(Level level,BlockPos pos){return contains(pos)&&(level.getBlockState(pos).is(Blocks.GRASS_BLOCK)||level.getBlockState(pos).is(Blocks.DIRT_PATH));}
        public int frame(Vec3 position){
            double x=position.x-ORIGIN.getX(),z=position.z-ORIGIN.getZ();
            if(Math.abs(x-(side<0?-5.03125:6.03125))>.3)return -1;
            int[] offsets={-5,-4,-3,-2,1,2,3,4};
            for(int i=0;i<offsets.length;i++)if(Math.abs(z-(centerZ+.5+offsets[i]))<.2)return i;
            return -1;
        }
        public Vec3 traitOrigin(){return new Vec3(ORIGIN.getX()+(side<0?-5.08:6.08),ORIGIN.getY()+2.7,ORIGIN.getZ()+centerZ-7);}
        public Vec3 informationOrigin(){return new Vec3(ORIGIN.getX()+(side<0?-5.08:6.08),ORIGIN.getY()+2.7,ORIGIN.getZ()+centerZ+7);}
        public float facing(){return side<0?-90:90;}
        public Vec3 rally(int ordinal){return new Vec3(ORIGIN.getX()-12+index*8+(ordinal%3-1)*1.4,ORIGIN.getY(),ORIGIN.getZ()+73+(ordinal/3)*2);}
    }
    private ArkWar(){}
    public static Entrance entrance(){return new Entrance(ORIGIN.offset(0,3,84),0);}
    public static boolean coreArea(Vec3 position){double x=position.x-ORIGIN.getX()-.5,z=position.z-ORIGIN.getZ()-92.5;return x*x+z*z<=26*26;}
    public static Vec3 standing(Level level,BlockPos support){
        var shape=level.getBlockState(support).getCollisionShape(level,support);
        return new Vec3(support.getX()+.5,support.getY()+(shape.isEmpty()?1:shape.max(net.minecraft.core.Direction.Axis.Y)),support.getZ()+.5);
    }
    public static boolean contains(Level level,BlockPos pos){
        return level.dimension().equals(DIMENSION)&&pos.getX()>=ORIGIN.getX()-116&&pos.getX()<ORIGIN.getX()+116
            &&pos.getZ()>=ORIGIN.getZ()-128&&pos.getZ()<ORIGIN.getZ()+128;
    }
}
