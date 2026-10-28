// Ported from the user-owned YDNF visual reference, as requested on 2026-10-26.
package cn.yumis.fight.modules.dungeons.content;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
/** Four real floors, with alternating three-wide interior stairs and persistent return routes. */
public class DragonTower {
    public static final int FLOOR=12;
    public static void build(ServerLevel world,BlockPos o){
        for(int floor=0;floor<4;floor++){
            int base=floor*FLOOR;
            for(int x=-18;x<=18;x++)for(int z=-2;z<=34;z++){
                boolean edge=Math.abs(x)==18||z==-2||z==34;
                for(int y=0;y<12;y++)put(world,o,x,base+y,z,edge?((y>=3&&y<=7&&(x%6==0||z%6==0))?Blocks.GLASS:(x%6==0||z%6==0)?Blocks.CHISELED_STONE_BRICKS:Blocks.SMOOTH_QUARTZ):Blocks.AIR);
                put(world,o,x,base-1,z,x%8==0&&z%8==0?Blocks.SEA_LANTERN:(Math.abs(x)<=2||z%12==0)?Blocks.POLISHED_DIORITE:Blocks.SMOOTH_QUARTZ);
                for(int y=0;y<=8;y++)if((Math.abs(x)==14)&&(z==4||z==27))put(world,o,x,base+y,z,y==0||y==8?Blocks.CHISELED_STONE_BRICKS:Blocks.QUARTZ_PILLAR);
                if(floor==3)put(world,o,x,base+11,z,x%8==0&&z%8==0?Blocks.SEA_LANTERN:Blocks.SMOOTH_QUARTZ);
            }
            for(int side:new int[]{
                -1,1
            }
            )for(int z:new int[]{
                4,27
            }
            ){
                put(world,o,side*14,base+9,z,Blocks.SEA_LANTERN);
                put(world,o,side*13,base+1,z,Blocks.COPPER_BLOCK.waxed().weathered());
            }
            for(int x=-4;x<=4;x++)for(int z=12;z<=20;z++)if(Math.abs(x)+Math.abs(z-16)<=5)put(world,o,x,base-1,z,(x==0||z==16)?Blocks.CHISELED_QUARTZ_BLOCK:Blocks.DEEPSLATE_TILES);
        }
        // Carve each staircase after all floors exist; no upper floor can overwrite the headroom.
        for(int floor=0;floor<3;floor++){
            int side=floor%2==0?1:-1,base=floor*FLOOR;
            for(int step=0;step<=12;step++)for(int dx=-1;dx<=1;dx++){
                int x=side*10+dx,z=18+step;
                for(int y=0;y<=3;y++)put(world,o,x,base+step+y,z,Blocks.AIR);
                world.setBlock(o.offset(x,base+step-1,z),Blocks.POLISHED_DIORITE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,net.minecraft.core.Direction.SOUTH),2);
                if(dx==-1||dx==1)put(world,o,side*10+dx*2,base+step,z,Blocks.IRON_BARS);
            }
            gate(world,o,floor,false);
        }
    }
    public static void gate(ServerLevel world,BlockPos o,int floor,boolean open){
        int side=floor%2==0?1:-1;
        for(int dx=-1;dx<=1;dx++)for(int y=0;y<4;y++)put(world,o,side*10+dx,floor*FLOOR+y,18,open?Blocks.AIR:Blocks.IRON_BARS);
    }
    public static BlockPos spawn(BlockPos o,int floor,int index){
        return o.offset((index-2)*3,floor*FLOOR,12+(index%2)*5);
    }
    private static void put(ServerLevel w,BlockPos o,int x,int y,int z,Block b){
        w.setBlock(o.offset(x,y,z),b.defaultBlockState(),2);
    }
    protected DragonTower(){
    }
}
