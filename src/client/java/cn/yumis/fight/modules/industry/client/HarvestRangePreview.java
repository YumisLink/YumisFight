package cn.yumis.fight.modules.industry.client;

import cn.yumis.fight.modules.industry.api.DeviceType;
import cn.yumis.fight.modules.industry.api.HarvestArea;
import cn.yumis.fight.modules.industry.platform.MachineBlockEntity;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.*;
import java.util.List;

/** A local preview of the exact crop layer; never requests or loads terrain. */
public final class HarvestRangePreview {
    private static ClientLevel world;
    private static BlockPos pinned;
    private HarvestRangePreview(){}
    public static void clear(){world=null;pinned=null;}
    public static boolean pinned(MachineBlockEntity machine){return world==machine.getLevel()&&machine.getBlockPos().equals(pinned);}
    public static void toggle(MachineBlockEntity machine){
        if(pinned(machine)){clear();return;}
        world=Minecraft.getInstance().level;pinned=machine.getBlockPos().immutable();
    }
    static void append(List<Quad> quads){
        var mc=Minecraft.getInstance();
        if(world!=mc.level)clear();
        if(mc.level==null)return;
        if(pinned!=null){
            var machine=machine(pinned);
            if(machine==null)clear();else area(quads,machine);
        }
        if(mc.hitResult instanceof BlockHitResult hit&&!hit.getBlockPos().equals(pinned)){
            var machine=machine(hit.getBlockPos());if(machine!=null)area(quads,machine);
        }
    }
    private static MachineBlockEntity machine(BlockPos pos){
        var level=Minecraft.getInstance().level;
        return level!=null&&level.hasChunkAt(pos)&&level.getBlockEntity(pos) instanceof MachineBlockEntity machine
            &&!machine.isRemoved()&&machine.device().has(DeviceType.Capability.HARVEST)?machine:null;
    }
    private static void area(List<Quad> out,MachineBlockEntity machine){
        var box=HarvestArea.bounds(machine.getBlockPos(),machine.tier());int width=HarvestArea.width(machine.tier());
        double y=box.minY+.025;
        floor(out,box.minX,y,box.minZ,box.maxX,box.maxZ,14);
        for(int i=0;i<=width;i++){
            double x=box.minX+i,z=box.minZ+i;
            floor(out,x-.012,y+.004,box.minZ,x+.012,box.maxZ,105);
            floor(out,box.minX,y+.004,z-.012,box.maxX,z+.012,105);
        }
        for(int axis=0;axis<3;axis++)for(int a=0;a<2;a++)for(int b=0;b<2;b++){
            double[] from={box.minX,box.minY+.02,box.minZ},size={box.getXsize(),.96,box.getZsize()};
            from[(axis+1)%3]+=a*size[(axis+1)%3];from[(axis+2)%3]+=b*size[(axis+2)%3];
            double[] to=from.clone();to[axis]+=size[axis];
            edge(out,new Vec3(from[0],from[1],from[2]),new Vec3(to[0],to[1],to[2]));
        }
    }
    private static void floor(List<Quad> out,double x1,double y,double z1,double x2,double z2,int alpha){
        out.add(new Quad(new Vec3(x1,y,z1),new Vec3(x1,y,z2),new Vec3(x2,y,z2),new Vec3(x2,y,z1),0x8be5a4,alpha));
    }
    private static void edge(List<Quad> out,Vec3 from,Vec3 to){
        Vec3 direction=to.subtract(from).normalize();
        Vec3 u=direction.cross(Math.abs(direction.y)>.95?new Vec3(1,0,0):new Vec3(0,1,0)).normalize(),v=direction.cross(u).normalize();
        for(int i=0;i<4;i++){
            double a=i*Math.PI/2,b=(i+1)*Math.PI/2;
            Vec3 left=u.scale(Math.cos(a)*.025).add(v.scale(Math.sin(a)*.025)),right=u.scale(Math.cos(b)*.025).add(v.scale(Math.sin(b)*.025));
            out.add(new Quad(from.add(left),to.add(left),to.add(right),from.add(right),0x8be5a4,210));
        }
    }
}
