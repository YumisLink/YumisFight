package cn.yumis.fight.modules.dungeons.content;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Four ground-level rooms. Previously opened gates remain traversable after death and late joining. */
public final class GblChurch {
    public static final int SPACING=40,MAX_Z=166;
    public static final String[] AREAS={"外围广场","残破柱廊","女神内殿","诅咒祭坛"};
    public static int area(int wave){return Math.min(3,wave/2);}
    public static boolean contains(int cell,int area,double x,double z){return Math.abs(x-cell*512)<= (area==3?19:15)&&z>=area*SPACING+1&&z<=area*SPACING+(area==3?39:31);}
    public static void build(ServerLevel world,BlockPos origin){
        for(int area=0;area<4;area++){
            int start=area*SPACING,size=area==3?40:32,half=size/2;
            for(int x=-half;x<=half;x++)for(int z=0;z<=size;z++){
                put(world,origin,x,-2,start+z,Blocks.STONE_BRICKS);
                Block floor=area<2?((x+z)%9==0?Blocks.CRACKED_STONE_BRICKS:Blocks.POLISHED_ANDESITE):Blocks.SMOOTH_QUARTZ;
                if(Math.abs(x)<=2)floor=area<2?Blocks.POLISHED_TUFF:Blocks.WOOL.purple();
                if(Math.abs(x)==3)floor=area<2?Blocks.CHISELED_STONE_BRICKS:Blocks.DYED_TERRACOTTA.yellow();
                put(world,origin,x,-1,start+z,floor);
                boolean edge=Math.abs(x)==half||z==0||z==size;
                boolean door=Math.abs(x)<=3&&((z==0&&area>0)||(z==size&&area<3));
                if(edge&&!door)for(int y=0;y<19;y++){
                    Block wall=area<2?(y<3?Blocks.STONE_BRICKS:Blocks.BARRIER):(y<14?(y%5==0?Blocks.CHISELED_QUARTZ_BLOCK:Blocks.SMOOTH_QUARTZ):Blocks.BARRIER);
                    put(world,origin,x,y,start+z,wall);
                }
                put(world,origin,x,19,start+z,area>=2?Blocks.SMOOTH_QUARTZ:Blocks.BARRIER);
            }
            for(int side:new int[]{-1,1})for(int z:new int[]{6,16,26}){
                int x=side*(half-4),height=area==0?4:area==1?(z==16?6:12):13;
                column(world,origin,x,start+z,height,area>=2);
                put(world,origin,x-side*2,2,start+z,Blocks.SEA_LANTERN);
                if(area>=2){put(world,origin,x-side,0,start+z+2,Blocks.CHISELED_BOOKSHELF);put(world,origin,x-side,1,start+z+2,Blocks.BOOKSHELF);}
            }
            if(area==1)for(int z:new int[]{6,26})for(int x=-12;x<=12;x++)put(world,origin,x,12,start+z,Blocks.CHISELED_STONE_BRICKS);
            if(area==2)for(int z=3;z<32;z+=7)for(int x=-12;x<=12;x++)if(Math.abs(x)>3||x==0)put(world,origin,x,14,start+z,Blocks.PURPUR_BLOCK);
            if(area<3){
                for(int z=size+1;z<SPACING;z++)for(int x=-4;x<=4;x++){
                    put(world,origin,x,-1,start+z,Math.abs(x)<4?Blocks.STONE_BRICKS:Blocks.CHISELED_STONE_BRICKS);
                    if(Math.abs(x)==4)for(int y=0;y<19;y++)put(world,origin,x,y,start+z,y<3?Blocks.STONE_BRICK_WALL:Blocks.BARRIER);
                    put(world,origin,x,19,start+z,Blocks.BARRIER);
                }
                gate(world,origin,area,false);
            }
        }
        // The altar remains flat inside the fight space; the idol stands at the rear wall.
        for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++){
            int rr=x*x+z*z;if(rr<=100)put(world,origin,x,-1,140+z,rr>=64?Blocks.AMETHYST_BLOCK:Math.abs(x)==Math.abs(z)?Blocks.GOLD_BLOCK:Blocks.POLISHED_BLACKSTONE);
        }
        idol(world,origin,0,156);
        for(int x:new int[]{-13,13})for(int z:new int[]{129,151}){put(world,origin,x,0,z,Blocks.GILDED_BLACKSTONE);put(world,origin,x,1,z,Blocks.SEA_LANTERN);}
        for(int area=2;area<4;area++)for(int x=-8;x<=8;x+=8)for(int z=8;z<=(area==3?32:24);z+=8)put(world,origin,x,-1,area*SPACING+z,Blocks.SEA_LANTERN);
    }
    private static void column(ServerLevel w,BlockPos o,int x,int z,int height,boolean quartz){
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            put(w,o,x+dx,0,z+dz,quartz?Blocks.CHISELED_QUARTZ_BLOCK:Blocks.CHISELED_STONE_BRICKS);
            for(int y=1;y<height;y++)put(w,o,x+dx,y,z+dz,quartz?Blocks.QUARTZ_PILLAR:Blocks.STONE_BRICKS);
            put(w,o,x+dx,height,z+dz,quartz?Blocks.SMOOTH_QUARTZ:Blocks.CRACKED_STONE_BRICKS);
        }
    }
    private static void idol(ServerLevel w,BlockPos o,int x,int z){
        for(int dx=-4;dx<=4;dx++)for(int dz=-2;dz<=2;dz++)put(w,o,x+dx,0,z+dz,Blocks.GILDED_BLACKSTONE);
        for(int y=1;y<=7;y++)for(int dx=-2;dx<=2;dx++)for(int dz=-1;dz<=1;dz++)put(w,o,x+dx,y,z+dz,Blocks.SMOOTH_QUARTZ);
        for(int dx=-1;dx<=1;dx++)for(int y=8;y<=10;y++)put(w,o,x+dx,y,z,Blocks.GOLD_BLOCK);
        for(int side:new int[]{-1,1})for(int y=5;y<=9;y++)put(w,o,x+side*4,y,z,Blocks.QUARTZ_PILLAR);
        put(w,o,x,11,z,Blocks.SEA_LANTERN);
    }
    public static void gate(ServerLevel world,BlockPos o,int area,boolean open){
        int z=area*SPACING+32;
        for(int x=-3;x<=3;x++)for(int y=0;y<19;y++){
            var state=(y<6?(open?Blocks.AIR:Blocks.IRON_BARS):Blocks.BARRIER).defaultBlockState();
            if(y<6&&!open)state=state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.EAST,true).setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WEST,true);
            world.setBlock(o.offset(x,y,z),state,2);
        }
    }
    private static void put(ServerLevel w,BlockPos o,int x,int y,int z,Block b){w.setBlock(o.offset(x,y,z),b.defaultBlockState(),2);}
    private GblChurch(){}
}
