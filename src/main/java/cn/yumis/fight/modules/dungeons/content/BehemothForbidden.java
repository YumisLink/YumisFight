package cn.yumis.fight.modules.dungeons.content;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** The GBL four-area footprint, rebuilt as a level-floored temple inside Behemoth. */
public final class BehemothForbidden {
    public static final int SPACING=GblChurch.SPACING,MAX_Z=GblChurch.MAX_Z;
    public static final String[] AREAS={"入口神殿","复活回廊","禁咒内殿","审判祭坛"};
    public static int area(int wave){return Math.min(3,wave/2);}
    public static boolean contains(int cell,int area,Vec3 p){return GblChurch.contains(cell,area,p.x,p.z);}
    public static Vec3 inside(int cell,int area,Vec3 p){
        double half=area==3?17:13;
        return new Vec3(Math.clamp(p.x,cell*512-half,cell*512+half),80,Math.clamp(p.z,area*SPACING+3,area*SPACING+(area==3?36:29)));
    }
    public static void gate(ServerLevel world,BlockPos origin,int gate,boolean open){GblChurch.gate(world,origin,gate,open);}
    public static void build(ServerLevel world,BlockPos origin){
        for(int area=0;area<4;area++){
            int start=area*SPACING,size=area==3?40:32,half=size/2;
            for(int x=-half;x<=half;x++)for(int z=0;z<=size;z++){
                put(world,origin,x,-2,start+z,Blocks.POLISHED_BLACKSTONE_BRICKS);
                Block floor=Math.floorMod(x*7+z*11,13)==0?Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS:Blocks.POLISHED_BLACKSTONE;
                if(Math.abs(x)<=2)floor=area<2?Blocks.RED_NETHER_BRICKS:Blocks.DYED_TERRACOTTA.red();
                if(Math.abs(x)==3)floor=Blocks.CHISELED_POLISHED_BLACKSTONE;
                put(world,origin,x,-1,start+z,floor);
                boolean edge=Math.abs(x)==half||z==0||z==size;
                boolean door=Math.abs(x)<=3&&((z==0&&area>0)||(z==size&&area<3));
                if(edge&&!door)for(int y=0;y<19;y++){
                    Block wall=y<3?Blocks.POLISHED_BLACKSTONE_BRICKS:y<12?(y%5==0?Blocks.CHISELED_POLISHED_BLACKSTONE:Blocks.RED_NETHER_BRICKS):Math.floorMod(x+z+y,5)==0?Blocks.NETHER_WART_BLOCK:Blocks.NETHERRACK;
                    put(world,origin,x,y,start+z,wall);
                }
                put(world,origin,x,19,start+z,Math.floorMod(x+z,7)==0?Blocks.BASALT:Blocks.NETHERRACK);
            }
            for(int side:new int[]{-1,1})for(int z:new int[]{6,16,26}){
                int x=side*(half-4),height=area==0?(z==16?7:4):area==1?(z==16?5:11):area==2?12:14;
                column(world,origin,x,start+z,height);
                put(world,origin,x-side*2,0,start+z,Blocks.SHROOMLIGHT);
                if(area>=2){put(world,origin,x-side,0,start+z+2,Blocks.CHISELED_BOOKSHELF);put(world,origin,x-side,1,start+z+2,Blocks.BOOKSHELF);}
            }
            // Ceiling ribs and inset lights leave the central navigation space clear.
            for(int z=5;z<size;z+=8)for(int x=-half+1;x<half;x++)put(world,origin,x,17,start+z,Math.abs(x)<3?Blocks.SHROOMLIGHT:Blocks.BASALT);
            for(int x:new int[]{-7,7})for(int z=8;z<size;z+=8)put(world,origin,x,-1,start+z,Blocks.SHROOMLIGHT);
            if(area<3){
                for(int z=size+1;z<SPACING;z++)for(int x=-4;x<=4;x++){
                    put(world,origin,x,-1,start+z,Math.abs(x)<4?Blocks.POLISHED_BLACKSTONE:Blocks.RED_NETHER_BRICKS);
                    if(Math.abs(x)==4)for(int y=0;y<19;y++)put(world,origin,x,y,start+z,y<8?Blocks.RED_NETHER_BRICKS:Blocks.NETHERRACK);
                    put(world,origin,x,19,start+z,Blocks.NETHERRACK);
                }
                gate(world,origin,area,false);
            }
        }
        for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++){
            int r=x*x+z*z;if(r<=100)put(world,origin,x,-1,140+z,r>=81?Blocks.GILDED_BLACKSTONE:Math.abs(x)==Math.abs(z)?Blocks.SHROOMLIGHT:Blocks.RED_NETHER_BRICKS);
        }
        // A sealed reliquary at the rear, outside the flat boss arena.
        for(int x=-5;x<=5;x++)for(int y=0;y<10;y++)put(world,origin,x,y,158,Math.abs(x)==5||y==0||y==9?Blocks.CHISELED_POLISHED_BLACKSTONE:Blocks.DYED_TERRACOTTA.red());
        for(int y=2;y<8;y++){put(world,origin,0,y,157,Blocks.GILDED_BLACKSTONE);if(y==5)for(int x=-3;x<=3;x++)put(world,origin,x,y,157,Blocks.GILDED_BLACKSTONE);}
    }
    private static void column(ServerLevel w,BlockPos o,int x,int z,int height){
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(int y=0;y<=height;y++)put(w,o,x+dx,y,z+dz,y==0||y==height?Blocks.CHISELED_POLISHED_BLACKSTONE:y%4==0?Blocks.RED_NETHER_BRICKS:Blocks.POLISHED_BASALT);
    }
    private static void put(ServerLevel w,BlockPos o,int x,int y,int z,Block b){w.setBlock(o.offset(x,y,z),b.defaultBlockState(),2);}
    private BehemothForbidden(){}
}
