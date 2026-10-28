// Ported from the user-owned YDNF visual reference, as requested on 2026-10-26.
package cn.yumis.fight.modules.dungeons.content;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
/** Fixed forest route. Gates open forwards and never lock the respawn return path. */
public class GoblinCamp {
    public static void build(ServerLevel world,BlockPos origin){
        for(int room=0;room<4;room++){
            int size=room==3?32:24,half=size/2,start=room*36;
            for(int x=-half;x<=half;x++)for(int z=0;z<=size;z++){
                put(world,origin,x,-2,start+z,Blocks.DIRT);
                boolean trail=Math.abs(x)<=2;
                put(world,origin,x,-1,start+z,trail?Blocks.DIRT_PATH:((x*x+z*7)%13==0?Blocks.PODZOL:Blocks.MOSS_BLOCK));
                boolean edge=Math.abs(x)==half||z==0||z==size;
                boolean doorway=Math.abs(x)<=2&&((z==0&&room>0)||(z==size&&room<3));
                if(edge&&!doorway){
                    boolean post=Math.abs(x)==half?z%6==0:x%6==0;
                    for(int y=0;y<14;y++)put(world,origin,x,y,start+z,y<2?(post?Blocks.SPRUCE_LOG:Blocks.SPRUCE_FENCE):Blocks.BARRIER);
                }
                put(world,origin,x,14,start+z,Blocks.BARRIER);
            }
            for(int sign:new int[]{
                -1,1
            }
            ){
                for(int z=4;z<size;z+=9)tree(world,origin,sign*(half+3),start+z);
                int x=sign*(half-3);
                for(int y=0;y<3;y++)put(world,origin,x,y,start+5,Blocks.SPRUCE_LOG);
                put(world,origin,x,3,start+5,Blocks.LANTERN);
                put(world,origin,x,0,start+size-5,Blocks.BARREL);
                put(world,origin,x-sign,0,start+size-5,Blocks.HAY_BLOCK);
                if(room<3)tent(world,origin,x-sign*2,start+size-7);
                if(room==0)outpost(world,origin,x,start+6);
            }
            if(room<3){
                for(int z=size+1;z<36;z++)for(int x=-3;x<=3;x++){
                    put(world,origin,x,-1,start+z,Math.abs(x)<3?Blocks.COARSE_DIRT:Blocks.SPRUCE_PLANKS);
                    if(Math.abs(x)==3)for(int y=0;y<14;y++)put(world,origin,x,y,start+z,y<2?Blocks.SPRUCE_FENCE:Blocks.BARRIER);
                    put(world,origin,x,14,start+z,Blocks.BARRIER);
                }
                gate(world,origin,room,false);
            }
        }
        int center=126;
        for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)if(x*x+z*z<=25)put(world,origin,x,-1,center+z,Math.abs(x)==Math.abs(z)?Blocks.CALCITE:Blocks.POLISHED_ANDESITE);
        for(int x:new int[]{
            -7,7
        }
        )for(int z:new int[]{
            119,133
        }
        ){
            for(int y=0;y<3;y++)put(world,origin,x,y,z,Blocks.COPPER_BLOCK.waxed().weathered());
            put(world,origin,x,3,z,Blocks.AMETHYST_BLOCK);
            put(world,origin,x,4,z,Blocks.LANTERN);
        }
    }
    private static void tree(ServerLevel world,BlockPos o,int x,int z){
        for(int y=0;y<6;y++)put(world,o,x,y,z,Blocks.OAK_LOG);
        for(int y=4;y<=7;y++)for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)if(Math.abs(dx)+Math.abs(dz)<=4-(y==7?2:0))world.setBlock(o.offset(x+dx,y,z+dz),Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true),2);
    }
    private static void tent(ServerLevel world,BlockPos o,int x,int z){
        for(int dz=-2;dz<=2;dz++)for(int dx=-2;dx<=2;dx++)put(world,o,x+dx,2-Math.abs(dx),z+dz,Blocks.WOOL.brown());
        put(world,o,x,0,z+2,Blocks.SPRUCE_LOG);
        put(world,o,x,1,z+2,Blocks.SPRUCE_LOG);
    }
    private static void outpost(ServerLevel world,BlockPos o,int x,int z){
        for(int dx:new int[]{
            -1,1
        }
        )for(int dz:new int[]{
            -1,1
        }
        )for(int y=0;y<=5;y++)put(world,o,x+dx,y,z+dz,Blocks.SPRUCE_LOG);
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            put(world,o,x+dx,3,z+dz,Blocks.SPRUCE_PLANKS);
            if(Math.abs(dx)==1||Math.abs(dz)==1)put(world,o,x+dx,4,z+dz,Blocks.SPRUCE_FENCE);
        }
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)put(world,o,x+dx,6,z+dz,Blocks.SPRUCE_SLAB);
        put(world,o,x,5,z,Blocks.LANTERN);
    }
    public static void gate(ServerLevel world,BlockPos o,int room,boolean open){
        for(int x=-2;x<=2;x++)for(int y=0;y<=3;y++)put(world,o,x,y,room*36+24,open?Blocks.AIR:Blocks.SPRUCE_FENCE);
        for(int x=-2;x<=2;x++)for(int y=4;y<14;y++)put(world,o,x,y,room*36+24,Blocks.BARRIER);
    }
    private static void put(ServerLevel world,BlockPos o,int x,int y,int z,Block block){
        world.setBlock(o.offset(x,y,z),block.defaultBlockState(),2);
    }
    protected GoblinCamp(){
    }
}
