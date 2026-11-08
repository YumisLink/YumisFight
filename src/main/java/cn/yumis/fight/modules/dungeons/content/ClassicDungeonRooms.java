package cn.yumis.fight.modules.dungeons.content;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/** Content-local seven-room construction; flat combat space, six persistent progression gates. */
public final class ClassicDungeonRooms {
    public static final int SPACING=40,MAX_Z=286;
    public static boolean contains(int cell,int area,Vec3 p){
        return area>=0&&area<=6&&Math.abs(p.x-cell*512)<= (area==6?19:15)&&p.z>=area*SPACING+1&&p.z<=area*SPACING+(area==6?39:31)&&p.y>=78&&p.y<100;
    }
    public static Vec3 inside(int cell,int area,Vec3 p){
        double half=area==6?17:13;
        return new Vec3(Math.clamp(p.x,cell*512-half,cell*512+half),80,Math.clamp(p.z,area*SPACING+3,area*SPACING+(area==6?36:29)));
    }
    private record Palette(Block floor,Block trim,Block wall,Block pillar,Block light){}
    private static Palette palette(String dungeon,int area){
        return switch(dungeon){
            case "moonlight_tavern" -> new Palette(Blocks.SPRUCE_PLANKS,Blocks.POLISHED_ANDESITE,Blocks.SPRUCE_LOG,Blocks.STRIPPED_OAK_LOG,Blocks.LANTERN);
            case "hamelin" -> new Palette(Blocks.COARSE_DIRT,Blocks.MOSSY_COBBLESTONE,Blocks.MUD_BRICKS,Blocks.SPRUCE_LOG,Blocks.SOUL_LANTERN);
            case "pursuit" -> new Palette(Blocks.SMOOTH_STONE,Blocks.IRON_BLOCK,Blocks.DEEPSLATE_BRICKS,Blocks.IRON_BLOCK,Blocks.SEA_LANTERN);
            case "sea_train" -> new Palette(Blocks.SPRUCE_PLANKS,Blocks.IRON_BLOCK,Blocks.DARK_PRISMARINE,Blocks.SPRUCE_LOG,Blocks.SEA_LANTERN);
            case "time_rift" -> switch(area){
                case 0 -> new Palette(Blocks.COARSE_DIRT,Blocks.NETHERRACK,Blocks.OAK_LOG,Blocks.SPRUCE_LOG,Blocks.SHROOMLIGHT);
                case 1 -> new Palette(Blocks.MOSSY_STONE_BRICKS,Blocks.POLISHED_BLACKSTONE,Blocks.DEEPSLATE_BRICKS,Blocks.BONE_BLOCK,Blocks.SOUL_LANTERN);
                case 2 -> new Palette(Blocks.SANDSTONE,Blocks.RED_SANDSTONE,Blocks.TERRACOTTA,Blocks.OAK_LOG,Blocks.LANTERN);
                case 3 -> new Palette(Blocks.POLISHED_BLACKSTONE,Blocks.CRYING_OBSIDIAN,Blocks.BLACKSTONE,Blocks.BASALT,Blocks.SOUL_LANTERN);
                case 4 -> new Palette(Blocks.SMOOTH_STONE,Blocks.IRON_BLOCK,Blocks.DEEPSLATE_TILES,Blocks.IRON_BLOCK,Blocks.SEA_LANTERN);
                case 5 -> new Palette(Blocks.DEEPSLATE,Blocks.AMETHYST_BLOCK,Blocks.COBBLED_DEEPSLATE,Blocks.BASALT,Blocks.SHROOMLIGHT);
                default -> new Palette(Blocks.PACKED_ICE,Blocks.BLUE_ICE,Blocks.SNOW_BLOCK,Blocks.PACKED_ICE,Blocks.SEA_LANTERN);
            };
            case "mysterious_awareness" -> new Palette(Blocks.SMOOTH_QUARTZ,Blocks.WOOL.purple(),Blocks.QUARTZ_BRICKS,Blocks.PURPUR_PILLAR,Blocks.SEA_LANTERN);
            case "grandi" -> new Palette(Blocks.POLISHED_DEEPSLATE,Blocks.CHISELED_DEEPSLATE,Blocks.DEEPSLATE_BRICKS,Blocks.BASALT,Blocks.SHROOMLIGHT);
            case "central_park" -> new Palette(Blocks.MOSS_BLOCK,Blocks.MOSSY_STONE_BRICKS,Blocks.OAK_LOG,Blocks.SPRUCE_LOG,Blocks.SHROOMLIGHT);
            default -> throw new IllegalArgumentException("Unknown classic room palette: "+dungeon);
        };
    }
    public static void build(ServerLevel world,BlockPos origin,String dungeon){
        for(int area=0;area<7;area++){
            Palette p=palette(dungeon,area);int start=area*SPACING,size=area==6?40:32,half=size/2;
            for(int x=-half;x<=half;x++)for(int z=0;z<=size;z++){
                put(world,origin,x,-2,start+z,Blocks.DEEPSLATE_BRICKS);
                Block floor=Math.abs(x)==3||z%8==0?p.trim:p.floor;
                // Decorative heat and water stay below glass so native terrain never adds a second damage path.
                if((dungeon.equals("grandi")||dungeon.equals("sea_train"))&&Math.abs(x)>half-4){
                    put(world,origin,x,-2,start+z,dungeon.equals("grandi")?Blocks.MAGMA_BLOCK:Blocks.DARK_PRISMARINE);floor=Blocks.GLASS;
                }
                if(Math.abs(x)<=2)floor=p.floor;
                put(world,origin,x,-1,start+z,floor);
                boolean edge=Math.abs(x)==half||z==0||z==size;
                boolean door=Math.abs(x)<=3&&((z==0&&area>0)||(z==size&&area<6));
                if(edge&&!door)for(int y=0;y<19;y++)put(world,origin,x,y,start+z,y<3?p.trim:y<11?p.wall:Blocks.BARRIER);
                put(world,origin,x,19,start+z,Blocks.BARRIER);
            }
            for(int side:new int[]{-1,1})for(int z:new int[]{6,16,26}){
                int x=side*(half-2),height=dungeon.equals("central_park")?11:8;
                for(int y=0;y<height;y++)put(world,origin,x,y,start+z,p.pillar);
                put(world,origin,x-side,2,start+z,p.light);
                if(dungeon.equals("central_park")||dungeon.equals("time_rift")&&area==0){
                    for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)if(Math.abs(dx)+Math.abs(dz)<4)
                        world.setBlock(origin.offset(x+dx,10,start+z+dz),Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true),2);
                }
                if(dungeon.equals("moonlight_tavern")||dungeon.equals("hamelin")){
                    put(world,origin,x-side,0,start+z+2,Blocks.BARREL);put(world,origin,x-side,1,start+z+2,Blocks.BARREL);
                }
                if(dungeon.equals("pursuit")||dungeon.equals("grandi")){
                    put(world,origin,x-side,0,start+z+2,Blocks.IRON_BLOCK);put(world,origin,x-side,1,start+z+2,Blocks.CHISELED_DEEPSLATE);
                }
            }
            if(dungeon.equals("sea_train"))for(int x:new int[]{-half+1,half-1})for(int z=3;z<size;z++){
                put(world,origin,x,3,start+z,Blocks.GLASS);put(world,origin,x,4,start+z,Blocks.GLASS);
            }
            for(int x:new int[]{-8,8})for(int z=8;z<size;z+=8)put(world,origin,x,-1,start+z,Blocks.SEA_LANTERN);
            if(area<6){
                for(int z=size+1;z<SPACING;z++)for(int x=-4;x<=4;x++){
                    put(world,origin,x,-2,start+z,Blocks.DEEPSLATE_BRICKS);put(world,origin,x,-1,start+z,p.floor);
                    if(Math.abs(x)==4)for(int y=0;y<19;y++)put(world,origin,x,y,start+z,y<3?p.wall:Blocks.BARRIER);
                    put(world,origin,x,19,start+z,Blocks.BARRIER);
                }
                gate(world,origin,area,false);
            }
        }
    }
    public static void gate(ServerLevel world,BlockPos origin,int gate,boolean open){
        for(int x=-3;x<=3;x++)for(int y=0;y<19;y++){
            var state=(y<6?(open?Blocks.AIR:Blocks.IRON_BARS):Blocks.BARRIER).defaultBlockState();
            if(y<6&&!open)state=state.setValue(BlockStateProperties.EAST,true).setValue(BlockStateProperties.WEST,true);
            world.setBlock(origin.offset(x,y,gate*SPACING+32),state,2);
        }
    }
    private static void put(ServerLevel world,BlockPos origin,int x,int y,int z,Block block){world.setBlock(origin.offset(x,y,z),block.defaultBlockState(),2);}
    private ClassicDungeonRooms(){}
}
