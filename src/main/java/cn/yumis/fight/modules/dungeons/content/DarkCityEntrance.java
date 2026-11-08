package cn.yumis.fight.modules.dungeons.content;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** A flat approach to Underfoot: a bone outpost, guard gallery, hammer court and sealed city gate. */
public final class DarkCityEntrance {
    public static final int SPACING=GblChurch.SPACING,MAX_Z=GblChurch.MAX_Z;
    public static final String[] AREAS={"遗骨前哨","护卫回廊","重锤庭院","暗黑城大门"};
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
                put(world,origin,x,-2,start+z,Blocks.DEEPSLATE_BRICKS);
                Block floor=Math.floorMod(x*7+z*11,17)==0?Blocks.CRACKED_DEEPSLATE_TILES:Blocks.POLISHED_DEEPSLATE;
                if(Math.abs(x)<=2)floor=area==0?Blocks.COBBLED_DEEPSLATE:Blocks.POLISHED_BLACKSTONE;
                if(Math.abs(x)==3)floor=Blocks.CHISELED_DEEPSLATE;
                if(area>=1&&Math.abs(x)==3&&z%8==4)floor=Blocks.AMETHYST_BLOCK;
                put(world,origin,x,-1,start+z,floor);
                boolean edge=Math.abs(x)==half||z==0||z==size;
                boolean door=Math.abs(x)<=3&&((z==0&&area>0)||(z==size&&area<3));
                if(edge&&!door)for(int y=0;y<19;y++){
                    Block wall=y<3?Blocks.DEEPSLATE_TILES:y<13?(y%4==0?Blocks.CHISELED_POLISHED_BLACKSTONE:Blocks.DEEPSLATE_BRICKS):Blocks.BARRIER;
                    put(world,origin,x,y,start+z,wall);
                }
                put(world,origin,x,19,start+z,Blocks.BARRIER);
            }
            for(int side:new int[]{-1,1})for(int z:new int[]{6,16,26}){
                int x=side*(half-2),height=area==0?5:area==1?11:area==2?7:13;
                pillar(world,origin,x,start+z,height);
                put(world,origin,x-side,2,start+z,Blocks.SOUL_LANTERN);
                put(world,origin,x-side,1,start+z,Blocks.CHISELED_POLISHED_BLACKSTONE);
                if(area==0&&z!=16){put(world,origin,x-side*2,0,start+z+2,Blocks.BONE_BLOCK);put(world,origin,x-side*2,1,start+z+2,Blocks.SKELETON_SKULL);}
                if(area>=1)banner(world,origin,x-side,start+z+2,side);
            }
            if(area==1||area==3)for(int z:new int[]{5,size-5})for(int x=-half+2;x<=half-2;x++){
                int y=11+Math.min(4,(half-Math.abs(x))/3);
                put(world,origin,x,y,start+z,Blocks.POLISHED_BLACKSTONE_BRICKS);
                if(x%5==0)put(world,origin,x,y-1,start+z,Blocks.SEA_LANTERN);
            }
            // Recessed light and edge decorations keep all combat and jump space level and unobstructed.
            for(int x:new int[]{-8,8})for(int z=8;z<size;z+=8)put(world,origin,x,-1,start+z,Blocks.SEA_LANTERN);
            if(area==2)for(int side:new int[]{-1,1})for(int z:new int[]{9,23}){
                put(world,origin,side*(half-3),0,start+z,Blocks.ANVIL);
                put(world,origin,side*(half-2),0,start+z+1,Blocks.CHISELED_DEEPSLATE);
            }
            if(area<3){
                for(int z=size+1;z<SPACING;z++)for(int x=-4;x<=4;x++){
                    put(world,origin,x,-1,start+z,Blocks.POLISHED_BLACKSTONE);
                    if(Math.abs(x)==4)for(int y=0;y<19;y++)put(world,origin,x,y,start+z,y<9?Blocks.DEEPSLATE_BRICKS:Blocks.BARRIER);
                    put(world,origin,x,19,start+z,Blocks.BARRIER);
                }
                gate(world,origin,area,false);
            }
        }
        // The city door is scenery behind the arena, not a platform or a second progression gate.
        for(int x=-8;x<=8;x++)for(int y=0;y<15;y++)put(world,origin,x,y,159,
            Math.abs(x)>=7||y>=13?Blocks.CHISELED_POLISHED_BLACKSTONE:Math.abs(x)==0||y==4||y==10?Blocks.CRYING_OBSIDIAN:Blocks.POLISHED_BLACKSTONE_BRICKS);
        for(int side:new int[]{-1,1})for(int y=2;y<=11;y++)put(world,origin,side*5,y,158,y%3==0?Blocks.AMETHYST_BLOCK:Blocks.PURPUR_PILLAR);
        for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++)if(x*x+z*z>=81&&x*x+z*z<=100)put(world,origin,x,-1,140+z,Blocks.CHISELED_DEEPSLATE);
    }
    private static void pillar(ServerLevel w,BlockPos o,int x,int z,int height){
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(int y=0;y<=height;y++)put(w,o,x+dx,y,z+dz,y==0||y==height?Blocks.CHISELED_POLISHED_BLACKSTONE:Blocks.DEEPSLATE_BRICKS);
    }
    private static void banner(ServerLevel w,BlockPos o,int x,int z,int side){
        for(int y=4;y<9;y++)put(w,o,x,y,z,Math.abs(y-6)==0?Blocks.WOOL.magenta():Blocks.WOOL.purple());
        put(w,o,x+side,8,z,Blocks.WOOL.purple());put(w,o,x-side,8,z,Blocks.WOOL.purple());
    }
    private static void put(ServerLevel w,BlockPos o,int x,int y,int z,Block b){w.setBlock(o.offset(x,y,z),b.defaultBlockState(),2);}
    private DarkCityEntrance(){}
}
