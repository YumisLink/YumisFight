package cn.yumis.fight.modules.dungeons.content;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;

/** Three circular arenas, combat radius 32, with decorations outside the usable floor. */
public final class TreasureArena {
    public static void build(ServerLevel world,BlockPos o,String theme){
        boolean gold=theme.equals("palace"),forest=theme.equals("forest");
        for(int x=-43;x<=43;x++)for(int z=-43;z<=43;z++){
            int r=x*x+z*z;if(r>43*43)continue;
            for(int y=-2;y<=22;y++)put(world,o,x,y,z,Blocks.AIR);
            put(world,o,x,-2,z,Blocks.STONE);
            Block floor=forest?(r%13==0?Blocks.COARSE_DIRT:Blocks.MOSS_BLOCK):gold?(Math.abs(x)<=2||Math.abs(z)<=2?Blocks.GOLD_BLOCK:((x+z)%8==0?Blocks.CHISELED_QUARTZ_BLOCK:Blocks.SMOOTH_QUARTZ)):((x%8==0||z%8==0)?Blocks.POLISHED_DIORITE:Blocks.SMOOTH_QUARTZ);
            put(world,o,x,-1,z,floor);
            if(r>=33*33){for(int y=0;y<=16;y++)put(world,o,x,y,z,y<2?(forest?Blocks.MOSSY_STONE_BRICKS:gold?Blocks.GOLD_BLOCK:Blocks.STONE_BRICKS):Blocks.BARRIER);}
            put(world,o,x,22,z,Blocks.BARRIER);
        }
        for(int i=0;i<12;i++){
            double a=i*Math.PI/6;int x=(int)Math.round(Math.cos(a)*36),z=(int)Math.round(Math.sin(a)*36);
            for(int y=0;y<12;y++)for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)put(world,o,x+dx,y,z+dz,forest?Blocks.OAK_LOG:gold&&(y==0||y==10)?Blocks.GOLD_BLOCK:Blocks.QUARTZ_PILLAR);
            if(forest){for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++)for(int y=10;y<=15;y++)if(dx*dx+dz*dz+(y-12)*(y-12)<22)world.setBlock(o.offset(x+dx,y,z+dz),Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true),2);}
            else{put(world,o,x,12,z,gold?Blocks.GLOWSTONE:Blocks.SEA_LANTERN);for(int step=-3;step<=3;step++)put(world,o,x+step,13,z,gold?Blocks.GOLD_BLOCK:Blocks.QUARTZ_BLOCK);}
        }
        if(gold){
            for(int x=-6;x<=6;x++)for(int z=18;z<=26;z++)put(world,o,x,0,z,Blocks.CHISELED_QUARTZ_BLOCK);
            for(int y=1;y<=7;y++)for(int x=-3;x<=3;x++)put(world,o,x,y,25,y==7||Math.abs(x)==3?Blocks.GOLD_BLOCK:Blocks.REDSTONE_BLOCK);
            for(int x=-2;x<=2;x++)for(int z=21;z<=24;z++)put(world,o,x,1,z,Blocks.GOLD_BLOCK);
            for(int z=-27;z<=20;z++)for(int x=-2;x<=2;x++)put(world,o,x,0,z,Blocks.CARPET.red());
        }
        for(int side:new int[]{-1,1}){
            for(int y=0;y<=6;y++)for(int dz=-3;dz<=3;dz++)if(y==6||Math.abs(dz)==3)put(world,o,side*29,y,dz,gold?Blocks.GOLD_BLOCK:forest?Blocks.MOSSY_STONE_BRICKS:Blocks.CHISELED_QUARTZ_BLOCK);
            put(world,o,side*29,0,0,Blocks.SEA_LANTERN);
        }
    }
    private static void put(ServerLevel w,BlockPos o,int x,int y,int z,Block b){w.setBlock(o.offset(x,y,z),b.defaultBlockState(),2);}
    private TreasureArena(){}
}
