package cn.yumis.fight.modules.dungeons.content;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/** Seven connected rooms with a flat return route and a high ceiling for Sieghart's real flight. */
public final class CastlePalace {
    public static final int SPACING=36,MAX_Z=258;
    public static final String[] ROOMS={"前庭","斧兵厅","傀儡厅","浮游回廊","王宫长廊","指挥厅","城主大厅"};
    public static int half(int room){return room==6?20:14;}
    public static int length(int room){return room==6?40:28;}
    public static Vec3 inside(int cell,int room,Vec3 point){double h=half(room)-2;return new Vec3(Math.clamp(point.x,cell*512-h,cell*512+h),80,Math.clamp(point.z,room*SPACING+3,room*SPACING+length(room)-3));}
    public static boolean contains(int cell,int room,Vec3 p){return Math.abs(p.x-cell*512)<=half(room)-1&&p.z>=room*SPACING+1&&p.z<=room*SPACING+length(room)-1;}
    public static void build(ServerLevel w,BlockPos o){
        for(int room=0;room<7;room++){
            int start=room*SPACING,half=half(room),length=length(room),roof=room==6?20:13;
            for(int x=-half;x<=half;x++)for(int z=0;z<=length;z++){
                put(w,o,x,-2,start+z,Blocks.DEEPSLATE_BRICKS);
                Block floor=(Math.abs(x)==3||z%8==0&&Math.abs(x)<4)?Blocks.GOLD_BLOCK:((x+z)%6==0?Blocks.POLISHED_DIORITE:Blocks.POLISHED_ANDESITE);
                if(Math.abs(x)<3)floor=Blocks.SMOOTH_QUARTZ;
                if(x%8==0&&z%8==0)floor=Blocks.SEA_LANTERN;
                put(w,o,x,-1,start+z,floor);
                boolean edge=Math.abs(x)==half||z==0||z==length;
                boolean door=Math.abs(x)<=3&&((z==0&&room>0)||(z==length&&room<6));
                if(edge&&!door)for(int y=0;y<roof;y++){
                    boolean window=Math.abs(x)==half&&z>3&&z<length-3&&y>=4&&y<=8;
                    put(w,o,x,y,start+z,window?Blocks.GLASS:y==2||y==roof-2?Blocks.CHISELED_QUARTZ_BLOCK:y<3?Blocks.DEEPSLATE_TILES:Blocks.DYED_TERRACOTTA.cyan());
                }
                put(w,o,x,roof,start+z,(x%8==0&&z%8==0)?Blocks.SEA_LANTERN:Blocks.SMOOTH_QUARTZ);
            }
            for(int side:new int[]{-1,1})for(int z:new int[]{5,14,23}){
                int x=side*(half-3),height=roof-2;
                for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                    put(w,o,x+dx,0,start+z+dz,Blocks.CHISELED_QUARTZ_BLOCK);
                    for(int y=1;y<height;y++)put(w,o,x+dx,y,start+z+dz,Blocks.QUARTZ_PILLAR);
                    put(w,o,x+dx,height,start+z+dz,Blocks.GOLD_BLOCK);
                }
                put(w,o,x-side*2,3,start+z,Blocks.SEA_LANTERN);
            }
            if(room<6){
                for(int z=length+1;z<SPACING;z++)for(int x=-4;x<=4;x++){
                    put(w,o,x,-2,start+z,Blocks.DEEPSLATE_BRICKS);put(w,o,x,-1,start+z,Math.abs(x)<3?Blocks.SMOOTH_QUARTZ:Blocks.POLISHED_ANDESITE);
                    if(Math.abs(x)==4)for(int y=0;y<13;y++)put(w,o,x,y,start+z,Blocks.DYED_TERRACOTTA.cyan());
                    put(w,o,x,13,start+z,Blocks.SMOOTH_QUARTZ);
                }
                gate(w,o,room,false);
            }
        }
        // Rear dais is outside the combat space; the arena and lightning floor remain completely flat.
        for(int x=-6;x<=6;x++)for(int z=250;z<=254;z++)put(w,o,x,0,z,Blocks.CHISELED_QUARTZ_BLOCK);
        for(int x=-4;x<=4;x++)for(int y=1;y<7;y++)put(w,o,x,y,254,Math.abs(x)==4||y==6?Blocks.GOLD_BLOCK:Blocks.DYED_TERRACOTTA.blue());
        for(int x=-3;x<=3;x++)put(w,o,x,1,252,Blocks.QUARTZ_SLAB);
        for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++){int r=x*x+z*z;if(r>=81&&r<=100)put(w,o,x,-1,234+z,Blocks.GOLD_BLOCK);}
    }
    public static void gate(ServerLevel w,BlockPos o,int room,boolean open){
        if(room<0||room>=6)return;
        int z=room*SPACING+28;
        for(int x=-3;x<=3;x++)for(int y=0;y<13;y++){
            var state=(open&&y<6?Blocks.AIR:y<6?Blocks.IRON_BARS:Blocks.SMOOTH_QUARTZ).defaultBlockState();
            if(!open&&y<6)state=state.setValue(BlockStateProperties.EAST,true).setValue(BlockStateProperties.WEST,true);
            w.setBlock(o.offset(x,y,z),state,2);
        }
    }
    private static void put(ServerLevel w,BlockPos o,int x,int y,int z,Block block){w.setBlock(o.offset(x,y,z),block.defaultBlockState(),2);}
    private CastlePalace(){}
}
