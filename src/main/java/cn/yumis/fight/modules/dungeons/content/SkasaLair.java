package cn.yumis.fight.modules.dungeons.content;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Four flat fighting spaces inside the snow mountain, with the dragon's body behind the arena. */
public final class SkasaLair {
    public static final int SPACING=40,MAX_Z=166;
    public static final String[] AREAS={"部族雪径","冰蛛裂谷","幼龙冰窟","冰龙巢穴"};
    public static int area(int wave){return Math.min(3,wave/2);}
    public static boolean contains(int cell,int area,Vec3 p){
        double x=p.x-cell*512;int start=area*SPACING;
        return Math.abs(x)<(area==3?19.5:15.5)&&p.z>start+.5&&p.z<(area==3?151.5:start+31.5);
    }
    public static Vec3 inside(int cell,int area,Vec3 p){
        double half=area==3?18:14;
        return new Vec3(Math.clamp(p.x,cell*512-half,cell*512+half),80,Math.clamp(p.z,area*SPACING+2,area==3?150.5:area*SPACING+30));
    }
    public static void gate(ServerLevel world,BlockPos origin,int gate,boolean open){GblChurch.gate(world,origin,gate,open);}
    public static void build(ServerLevel world,BlockPos origin){
        for(int area=0;area<4;area++){
            int start=area*SPACING,size=area==3?40:32,half=size/2;
            for(int x=-half;x<=half;x++)for(int z=0;z<=size;z++){
                put(world,origin,x,-2,start+z,Blocks.STONE);
                // The fighting floor has ordinary traction; ice is reserved for trim and scenery.
                int pattern=Math.floorMod(x*11+z*7,23);
                Block floor=pattern<3?Blocks.CALCITE:pattern==4?Blocks.POLISHED_DIORITE:Blocks.SNOW_BLOCK;
                if(Math.abs(x)==half-1)floor=Blocks.PACKED_ICE;
                put(world,origin,x,-1,start+z,floor);
                boolean edge=Math.abs(x)==half||z==0||z==size;
                boolean door=Math.abs(x)<=3&&((z==0&&area>0)||(z==size&&area<3));
                if(edge&&!door)for(int y=0;y<24;y++){
                    int shelf=Math.floorMod(x*3+z*5+y,11);
                    put(world,origin,x,y,start+z,y<2?Blocks.SNOW_BLOCK:shelf<3?Blocks.BLUE_ICE:shelf<7?Blocks.PACKED_ICE:Blocks.DEEPSLATE);
                }
                int roof=area==3?24:20+Math.floorMod(x+z,3);
                put(world,origin,x,roof,start+z,Blocks.PACKED_ICE);
            }
            for(int side:new int[]{-1,1})for(int z:new int[]{5,15,26}){
                int x=side*(half-2),height=5+Math.floorMod(z+area*3,7);
                crystal(world,origin,x,start+z,height,side);
                put(world,origin,x-side,-1,start+z,Blocks.SEA_LANTERN);
                if(area==0)totem(world,origin,side*11,start+z);
                if(area==1)for(int y=4;y<9;y++)put(world,origin,side*(half-1),y,start+z,Blocks.COBWEB);
                if(area==2)for(int i=0;i<4;i++)put(world,origin,side*(half-1),i+1,start+z+i,Blocks.BONE_BLOCK);
            }
            if(area<3){
                for(int z=size+1;z<SPACING;z++)for(int x=-4;x<=4;x++){
                    put(world,origin,x,-2,start+z,Blocks.STONE);
                    put(world,origin,x,-1,start+z,Math.abs(x)==4?Blocks.PACKED_ICE:Blocks.SNOW_BLOCK);
                    if(Math.abs(x)==4)for(int y=0;y<20;y++)put(world,origin,x,y,start+z,Blocks.PACKED_ICE);
                    put(world,origin,x,20,start+z,Blocks.BLUE_ICE);
                }
                gate(world,origin,area,false);
            }
        }
        // Backstage is physically sealed; the reachable head and both neck flanks remain in front.
        for(int x=-19;x<=19;x++)for(int y=0;y<24;y++)put(world,origin,x,y,152,y<2?Blocks.PACKED_ICE:Blocks.BARRIER);
        for(int x:new int[]{-17,-12,12,17})crystal(world,origin,x,158,12+Math.abs(x)%5,x<0?-1:1);
        for(int x=-18;x<=18;x++)for(int z=153;z<=164;z++)put(world,origin,x,-1,z,Blocks.BLUE_ICE);
        // Pale guide stones mark the approach without marking permanent combat-safe positions.
        for(int z=123;z<=143;z+=4)for(int x:new int[]{-8,8})put(world,origin,x,-1,z,Blocks.SEA_LANTERN);
    }
    private static void crystal(ServerLevel w,BlockPos o,int x,int z,int height,int side){
        for(int y=0;y<height;y++){
            int width=y<height/2?1:0;
            for(int dx=-width;dx<=width;dx++)for(int dz=-width;dz<=width;dz++)put(w,o,x+dx,y,z+dz,y%4==0?Blocks.BLUE_ICE:Blocks.PACKED_ICE);
        }
        for(int y=0;y<height/2;y++)put(w,o,x+side,y,z+2,Blocks.BLUE_ICE);
    }
    private static void totem(ServerLevel w,BlockPos o,int x,int z){
        for(int y=0;y<4;y++)put(w,o,x,y,z,y==3?Blocks.CHISELED_STONE_BRICKS:Blocks.SPRUCE_LOG);
        for(int dx=-1;dx<=1;dx++)put(w,o,x+dx,2,z,Blocks.SPRUCE_PLANKS);
        put(w,o,x,4,z,Blocks.SNOW_BLOCK);
    }
    private static void put(ServerLevel w,BlockPos o,int x,int y,int z,Block block){w.setBlock(o.offset(x,y,z),block.defaultBlockState(),2);}
    private SkasaLair(){}
}
