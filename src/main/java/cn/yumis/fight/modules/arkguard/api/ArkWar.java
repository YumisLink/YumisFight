package cn.yumis.fight.modules.arkguard.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** The shared Ark scene's address and boundaries, also consumed by its menu. */
public final class ArkWar {
    public static final Identifier BLUEPRINT=Identifier.parse("yumis_dungeon_and_fight:ark_war");
    public static final ResourceKey<Level> DIMENSION=ResourceKey.create(Registries.DIMENSION,Identifier.parse("yumis_dungeon_and_fight:dungeon"));
    public static final BlockPos ORIGIN=new BlockPos(0,96,-8192);
    private static final int FIELD_EDGE=18;
    private static final double EDGE_BAND=3, MIN_KNOCKBACK=.05;
    public record Entrance(BlockPos pos,float yaw) {}
    public static final BlockPos CORE=ORIGIN.offset(0,4,92);
    public static final BlockPos CORE_HOME_SIGN=ORIGIN.offset(7,3,84);
    public record CoreAccess(Vec3 contact,Vec3 approach) {}
    public static final List<Plot> PLOTS=List.of(new Plot(0,-32,-46,-1),new Plot(1,32,-46,1),new Plot(2,-32,18,-1),new Plot(3,32,18,1));
    public enum SignAction {
        READY, REFRESH, UPGRADE, CORE, HOME;
        public boolean requiresOwnPlot(){return this==READY||this==REFRESH||this==UPGRADE;}
    }
    public record Sign(SignAction action,int plot) {}
    public record Teleport(Vec3 position,float yaw) {}
    /** A board on one side of the enemy entrance, facing into the battlefield. */
    public record FieldBoard(int centerX,int edgeZ,Direction facing) {
        public BlockPos backing(int offset,int y){return ORIGIN.offset(centerX+offset,y,edgeZ);}
        private BlockPos sign(int offset){return backing(offset,1).relative(facing);}
        public BlockPos readySign(){return sign(-2);}
        public BlockPos coreSign(){return sign(-1);}
        public BlockPos homeSign(){return sign(0);}
        public Vec3 informationOrigin(){return new Vec3(ORIGIN.getX()+centerX+2,ORIGIN.getY()+2.7,ORIGIN.getZ()+edgeZ+(facing==Direction.SOUTH?1.08:-.08));}
    }
    public record Plot(int index,int centerX,int centerZ,int side) {
        public boolean contains(BlockPos pos){double x=pos.getX()-ORIGIN.getX()-centerX,z=pos.getZ()-ORIGIN.getZ()-centerZ;return x*x+z*z<=28*28;}
        /** The stone-slab outline, including its openings for the enemy road. */
        public boolean battlefield(BlockPos pos){return Math.abs(pos.getX()-ORIGIN.getX()-centerX)<=FIELD_EDGE&&Math.abs(pos.getZ()-ORIGIN.getZ()-centerZ)<=FIELD_EDGE;}
        private double fieldX(Vec3 point){return point.x-(ORIGIN.getX()+centerX+.5);}
        private double fieldZ(Vec3 point){return point.z-(ORIGIN.getZ()+centerZ+.5);}
        private double edgeDistance(Vec3 point){return FIELD_EDGE+.5-Math.max(Math.abs(fieldX(point)),Math.abs(fieldZ(point)));}
        public double battlefieldKnockbackScale(Vec3 point){
            double distance=edgeDistance(point);
            if(distance<0||distance>=EDGE_BAND)return 1;
            return MIN_KNOCKBACK+(1-MIN_KNOCKBACK)*distance/EDGE_BAND;
        }
        public Vec3 limitBattlefieldVelocity(Vec3 point,Vec3 velocity,double halfWidth){
            double distance=edgeDistance(point);
            if(distance<0||distance>=EDGE_BAND)return velocity;
            double limit=FIELD_EDGE+.5-halfWidth;
            // Bound the distance the unit can coast under ordinary air drag.
            return new Vec3(limitPushAxis(fieldX(point),velocity.x,limit),velocity.y,limitPushAxis(fieldZ(point),velocity.z,limit));
        }
        private static double limitPushAxis(double offset,double speed,double limit){
            double decay=1.0-LivingEntity.BASE_HORIZONTAL_AIR_DRAG;
            return Math.clamp(speed,Math.min(0,(-limit-offset)*decay),Math.max(0,(limit-offset)*decay));
        }
        public Vec3 shop(){return new Vec3(ORIGIN.getX()+(side<0?-10:11),ORIGIN.getY(),ORIGIN.getZ()+centerZ+.5);}
        public BlockPos readySign(){return ORIGIN.offset(side<0?-6:6,1,centerZ);}
        public BlockPos refreshSign(){return ORIGIN.offset(side<0?-6:6,1,centerZ-1);}
        public BlockPos upgradeSign(){return ORIGIN.offset(side<0?-6:6,1,centerZ+5);}
        public List<FieldBoard> fieldBoards(){
            int edge=centerZ+(index<2?-FIELD_EDGE:FIELD_EDGE);Direction facing=index<2?Direction.SOUTH:Direction.NORTH;
            return List.of(new FieldBoard(centerX-10,edge,facing),new FieldBoard(centerX+10,edge,facing));
        }
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
    static Vec3 limitFieldContact(Entity entity,double x,double y,double z){
        var impulse=new Vec3(x,y,z);
        if(!impulse.isFinite()||x==0&&z==0)return impulse;
        var position=entity.blockPosition();
        for(var plot:PLOTS)if(plot.battlefield(position)){
            Vec3 velocity=entity.getDeltaMovement(),next=velocity.add(impulse);
            Vec3 limited=plot.limitBattlefieldVelocity(entity.position(),next,entity.getBbWidth()*.5);
            if(limited!=next)return new Vec3(limited.x-velocity.x,y,limited.z-velocity.z);
            break;
        }
        return impulse;
    }
    public static Entrance entrance(){return new Entrance(ORIGIN.offset(0,3,84),0);}
    public static Sign signAt(BlockPos pos){
        if(pos==null)return null;
        if(CORE_HOME_SIGN.equals(pos))return new Sign(SignAction.HOME,-1);
        for(var plot:PLOTS){
            if(plot.readySign().equals(pos))return new Sign(SignAction.READY,plot.index());
            if(plot.refreshSign().equals(pos))return new Sign(SignAction.REFRESH,plot.index());
            if(plot.upgradeSign().equals(pos))return new Sign(SignAction.UPGRADE,plot.index());
            for(var board:plot.fieldBoards()){
                if(board.readySign().equals(pos))return new Sign(SignAction.READY,plot.index());
                if(board.coreSign().equals(pos))return new Sign(SignAction.CORE,plot.index());
                if(board.homeSign().equals(pos))return new Sign(SignAction.HOME,plot.index());
            }
        }
        return null;
    }
    /** The owner chooses the destination; the server only relays the resulting position. */
    public static Teleport teleport(SignAction action,int seat){
        if(action==SignAction.CORE){var entry=entrance();return new Teleport(Vec3.atBottomCenterOf(entry.pos()),entry.yaw());}
        if(action==SignAction.HOME&&seat>=0&&seat<PLOTS.size()){var plot=PLOTS.get(seat);return new Teleport(plot.shop(),plot.facing());}
        return null;
    }
    public static boolean coreArea(Vec3 position){double x=position.x-ORIGIN.getX()-.5,z=position.z-ORIGIN.getZ()-92.5;return x*x+z*z<=26*26;}
    /** Strike the base's outer face and approach from the walkway with room for wide enemies. */
    public static CoreAccess coreAccess(Vec3 from){
        Vec3 base=Vec3.atBottomCenterOf(CORE.below());
        double dx=from.x-base.x,dz=from.z-base.z;
        Direction side;
        if(Math.abs(dx)>Math.abs(dz))side=dx>0?Direction.EAST:Direction.WEST;
        else side=dz>0?Direction.SOUTH:Direction.NORTH;
        return new CoreAccess(base.relative(side,3.5),base.relative(side,5));
    }
    public static Vec3 standing(Level level,BlockPos support){
        var shape=level.getBlockState(support).getCollisionShape(level,support);
        return new Vec3(support.getX()+.5,support.getY()+(shape.isEmpty()?1:shape.max(net.minecraft.core.Direction.Axis.Y)),support.getZ()+.5);
    }
    public static boolean contains(Level level,BlockPos pos){
        return level.dimension().equals(DIMENSION)&&pos.getX()>=ORIGIN.getX()-116&&pos.getX()<ORIGIN.getX()+116
            &&pos.getZ()>=ORIGIN.getZ()-128&&pos.getZ()<ORIGIN.getZ()+128;
    }
}
