package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.DungeonPorts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import java.util.List;

/** Spatial content for the open forest. Encounters and scenery consume the same anchors. */
public final class GoblinCamp {
    public static final String VERSION="GOBLIN_FOREST_LAYOUT_1";
    public static final DungeonLayouts.Bounds BOUNDS=new DungeonLayouts.Bounds(-90,-3,-8,89,49,191);
    public record Area(String id,int x,int z,int radius) {
        public boolean contains(double px,double pz,double margin){
            double dx=px-x,dz=pz-z,a=Math.atan2(dz,dx);
            double edge=radius+Math.sin(a*3+x)*1.1+Math.cos(a*5+z)*.8+margin;
            return dx*dx+dz*dz<=edge*edge;
        }
        public BlockPos at(BlockPos origin,int dx,int dy,int dz){return origin.offset(x+dx,dy,z+dz);}
        public List<BlockPos> spawns(BlockPos origin){return List.of(at(origin,-3,0,0),at(origin,3,0,0),at(origin,5,0,3),at(origin,0,0,5));}
    }
    public static final Area SALVAGE=new Area("salvage",-44,45,15);
    public static final Area LIVING=new Area("living",43,83,16);
    public static final Area RITUAL=new Area("ritual",-35,122,17);
    public static final Area BOSS=new Area("boss",23,163,21);
    public static final List<Area> CAMPS=List.of(SALVAGE,LIVING,RITUAL);
    private static final List<Area> CLEARINGS=List.of(SALVAGE,LIVING,RITUAL,BOSS);
    private static final int[][] RUINS={{-14,6},{18,44},{-54,91},{-16,29},{26,111}};
    // Three winding passages, cross-links, and two approaches to each ordinary clearing.
    // Their clear width is independent of the deliberately intermittent ground markings.
    private static final int[][][] TRAILS={
        {{0,2},{4,22},{-5,47},{7,70},{-4,96},{9,120},{6,143},{23,163}},
        {{0,2},{-20,20},{-68,42},{-71,77},{-64,105},{-66,144},{-13,162},{23,163}},
        {{0,2},{27,25},{66,50},{71,88},{65,120},{55,149},{23,163}},
        {{-71,77},{-45,83},{-18,72},{7,70},{34,62},{68,70}},
        {{-66,144},{-48,152},{-24,146},{6,143},{33,133},{65,120}},
        {{-68,42},{-59,50},{-44,45},{-31,48},{-5,47}},
        {{71,88},{57,91},{43,83},{32,86},{17,98},{-4,96}},
        {{-64,105},{-51,109},{-35,122},{-20,128},{9,120}}
    };
    public record Relic(String id,Area area,double x,double y,double z,Item item,float pitch,float yaw,float roll,float scale){}
    public static List<Relic> relics(){return List.of(
        new Relic("discarded_shield",SALVAGE,-3.4,.24,-9.6,Items.SHIELD,78,23,-13,.8f),
        new Relic("stolen_sword",SALVAGE,2.5,1.12,10.5,Items.IRON_SWORD,83,-24,18,.7f),
        new Relic("old_supply_flag",SALVAGE,-7.4,3.2,10.8,Items.BANNER.brown(),0,180,-14,.7f),
        new Relic("drying_hide",LIVING,-10.5,1.95,8.5,Items.LEATHER,0,10,-16,.9f),
        new Relic("drying_hide_small",LIVING,-8.7,1.85,8.5,Items.LEATHER,0,-10,21,.65f),
        new Relic("camp_flag",LIVING,9.5,2.45,-11.1,Items.BANNER.gray(),0,0,11,.55f),
        new Relic("ritual_bone_left",RITUAL,-9.5,1.9,-4.5,Items.BONE,8,10,28,.85f),
        new Relic("ritual_bone_right",RITUAL,-11.5,2.3,-4.5,Items.BONE,0,0,-24,.7f),
        new Relic("offering",RITUAL,1.5,1.2,10.5,Items.BONE,86,38,0,.65f),
        new Relic("deep_forest_flag",BOSS,-13.5,3.2,11.5,Items.BANNER.black(),0,25,-9,.8f),
        new Relic("deep_forest_bone",BOSS,-11.5,2.2,11.5,Items.BONE,0,0,30,.9f)
    );}
    public static void build(ServerLevel world,BlockPos origin,DungeonPorts.CampDecorations decorations){new Builder(world,origin,decorations).build();}

    private static double trailDistance(int x,int z){
        double best=Double.POSITIVE_INFINITY;
        for(int[][] trail:TRAILS)for(int i=1;i<trail.length;i++){
            double ax=trail[i-1][0],az=trail[i-1][1],dx=trail[i][0]-ax,dz=trail[i][1]-az;
            double t=Math.clamp(((x-ax)*dx+(z-az)*dz)/(dx*dx+dz*dz),0,1);
            best=Math.min(best,Math.hypot(x-ax-t*dx,z-az-t*dz));
        }
        return best;
    }
    private static boolean clearing(int x,int z,double margin){return CLEARINGS.stream().anyMatch(a->a.contains(x,z,margin));}
    private static boolean nearRuin(int x,int z,int radius){for(int[] p:RUINS)if(Math.hypot(x-p[0],z-p[1])<radius)return true;return false;}
    private static int hash(int x,int z,int salt){int n=x*73428767^z*912931^salt*19349663;n^=n>>>13;n*=1274126177;return n^(n>>>16);}
    private static int pick(int x,int z,int salt,int count){return Math.floorMod(hash(x,z,salt),count);}
    private static int height(int x,int z){
        if(clearing(x,z,4)||nearRuin(x,z,8)||trailDistance(x,z)<4.5||x*x+(z-2)*(z-2)<100)return 0;
        return Math.max(0,(int)Math.floor((Math.sin(x/19d)+Math.cos(z/23d)+Math.sin((x+z)/31d))*.75));
    }
    private static boolean walking(int x,int z){return trailDistance(x,z)<2||x*x+(z-2)*(z-2)<49;}

    private static final class Builder {
        private final ServerLevel world;
        private final BlockPos origin;
        private final DungeonPorts.CampDecorations decorations;
        private final RandomSource random=RandomSource.create(0x474F424C494EL);
        Builder(ServerLevel world,BlockPos origin,DungeonPorts.CampDecorations decorations){this.world=world;this.origin=origin;this.decorations=decorations;}
        void build(){ground();forest();salvage();living();ritual();boss();abandonedCamps();undergrowth();}
        private void ground(){
            for(int x=BOUNDS.minX();x<=BOUNDS.maxX();x++)for(int z=BOUNDS.minZ();z<=BOUNDS.maxZ();z++){
                int h=height(x,z),n=pick(x/3,z/3,1,17);
                for(int y=-3;y<h-1;y++)put(x,y,z,Blocks.DIRT);
                Block surface=n<3?Blocks.PODZOL:n<6?Blocks.COARSE_DIRT:n==6?Blocks.MOSS_BLOCK:Blocks.GRASS_BLOCK;
                if(trailDistance(x,z)<1.1&&pick(x/4,z/4,2,7)<3)surface=pick(x,z,3,5)==0?Blocks.DIRT_PATH:Blocks.COARSE_DIRT;
                if(clearing(x,z,-2)&&pick(x/2,z/2,4,5)<3)surface=Blocks.COARSE_DIRT;
                put(x,h-1,z,surface);
                // Safety boundaries surround the forest, never the individual clearings.
                if(x==BOUNDS.minX()||x==BOUNDS.maxX()||z==BOUNDS.minZ()||z==BOUNDS.maxZ())
                    for(int y=h;y<49;y++)put(x,y,z,Blocks.BARRIER);
                put(x,49,z,Blocks.BARRIER);
            }
        }
        private void forest(){
            for(int gx=-82;gx<=80;gx+=7)for(int gz=1;gz<=180;gz+=7){
                int x=gx+pick(gx,gz,5,5)-2,z=gz+pick(gx,gz,6,5)-2;
                if(x<BOUNDS.minX()+8||x>BOUNDS.maxX()-8||z<BOUNDS.minZ()+8||z>BOUNDS.maxZ()-8)continue;
                if(clearing(x,z,4)||nearRuin(x,z,9)||trailDistance(x,z)<4.7||x*x+(z-2)*(z-2)<100)continue;
                int h=height(x,z),tall=pick(x,z,7,4)==0?9+pick(x,z,8,4):14+pick(x,z,8,8);
                tree(x,h,z,tall,pick(x,z,9,3)==0);
            }
            for(int x=-87;x<=86;x++)for(int z=-5;z<=188;z++){
                int rim=Math.min(Math.min(x+90,89-x),Math.min(z+8,191-z));
                if(rim>4||pick(x,z,10,4)==0)continue;
                int h=height(x,z);
                if(pick(x,z,11,3)==0)putIfAir(x,h,z,Blocks.MOSSY_COBBLESTONE);else leaves(x,h,z,Blocks.DARK_OAK_LEAVES);
                if(pick(x,z,12,3)>0)leaves(x,h+1,z,Blocks.DARK_OAK_LEAVES);
            }
        }
        private void tree(int x,int y,int z,int tall,boolean dark){
            Block wood=dark?Blocks.DARK_OAK_LOG:Blocks.OAK_LOG,leaf=dark?Blocks.DARK_OAK_LEAVES:Blocks.OAK_LEAVES;
            boolean thick=tall>18;
            for(int dy=0;dy<tall;dy++){
                put(x,y+dy,z,wood);
                if(thick&&dy<tall-3)put(x+1,y+dy,z,wood);
            }
            for(Direction d:Direction.Plane.HORIZONTAL){
                int length=2+pick(x+d.getStepX(),z+d.getStepZ(),13,2);
                for(int k=1;k<=length;k++){
                    int rx=x+d.getStepX()*k,rz=z+d.getStepZ()*k;
                    if(walking(rx,rz))break;
                    int ry=height(rx,rz);
                    put(rx,ry,rz,wood.defaultBlockState().setValue(RotatedPillarBlock.AXIS,d.getAxis()));
                    if(k==length)putIfAir(rx,ry+1,rz,Blocks.MOSS_CARPET);
                }
                int by=y+tall-4,ex=x+d.getStepX()*3,ez=z+d.getStepZ()*3;
                for(int k=1;k<=3;k++)put(x+d.getStepX()*k,by,z+d.getStepZ()*k,wood.defaultBlockState().setValue(RotatedPillarBlock.AXIS,d.getAxis()));
                put(ex,by+1,ez,wood);crown(ex,by+2,ez,3,leaf);
            }
            crown(x,y+tall-1,z,4+(thick?1:0),leaf);
            if(pick(x,z,14,3)==0)
                for(int dy=3;dy<Math.min(8,tall-3);dy++)if(air(x,y+dy,z+1))put(x,y+dy,z+1,Blocks.VINE.defaultBlockState().setValue(VineBlock.NORTH,true));
        }
        private void crown(int x,int y,int z,int radius,Block leaf){
            for(int dy=-2;dy<=3;dy++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)
                if(dx*dx+dz*dz+dy*dy*2<=radius*radius+pick(x+dx,z+dz,15,4))leaves(x+dx,y+dy,z+dz,leaf);
        }
        private void leaves(int x,int y,int z,Block block){
            if(air(x,y,z))put(x,y,z,block.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true));
        }
        private void salvage(){
            Area a=SALVAGE;
            leanTo(a,-10,6,7,6,false);
            for(int i=0;i<3;i++){crate(a.x()-9+i*2,a.z()+10,i==1);decor(a.x()-9+i*2,1,a.z()+10,"firewood_bundle",i);}
            crate(a.x()+2,a.z()+10,false);crate(a.x()+3,a.z()+11,true);
            barrel(a.x()+1,0,a.z()+11,Direction.EAST);
            mini(a.x()+10,a.z()-4,"log_cart","oak",Rotation.CLOCKWISE_90,true);
            mini(a.x()-8,a.z()-6,"fallen_tree","oak",Rotation.CLOCKWISE_90,false);
            mini(a.x()+5,a.z()-9,"log_bundle","spruce",Rotation.NONE,false);
            tent(a.x()-2,a.z()-12,true,Blocks.WOOL.brown(),Direction.SOUTH);
            decor(a.x()-5,0,a.z()+8,"goblin_weapon_rack",2);
            decor(a.x()+7,0,a.z()-1,"goblin_sling_stones",5);
            decor(a.x()-13,0,a.z()+8,"rope_waypost",1);
            lantern(a.x()-10,a.z()+6,4,Direction.EAST);
        }
        private void living(){
            Area a=LIVING;
            tent(a.x()-11,a.z()-6,false,Blocks.WOOL.brown(),Direction.SOUTH);
            tent(a.x()-5,a.z()+11,false,Blocks.WOOL.gray(),Direction.NORTH);
            tent(a.x()+9,a.z()-9,false,Blocks.WOOL.brown(),Direction.SOUTH);
            firePit(a.x(),a.z()-7);dryingRack(a.x()-12,a.z()+8);
            mini(a.x()+10,a.z()+11,"fallen_tree","dark_oak",Rotation.CLOCKWISE_90,false);
            barrel(a.x()+9,0,a.z()+1,Direction.WEST);crate(a.x()+10,a.z()+1,false);
            for(int i=0;i<3;i++)decor(a.x()-2+i,0,a.z()-11,"firewood_bundle",i);
            decor(a.x()-9,0,a.z()-4,"goblin_hide_mat",1);
            decor(a.x()-4,0,a.z()+10,"goblin_hide_mat",3);
            decor(a.x()+10,0,a.z()-8,"goblin_hide_mat",2);
            decor(a.x()+12,0,a.z()-6,"goblin_weapon_rack",0);
            lantern(a.x()-13,a.z()-3,4,Direction.EAST);
            lantern(a.x()+7,a.z()+11,4,Direction.WEST);
        }
        private void ritual(){
            Area a=RITUAL;
            leanTo(a,-12,5,6,5,true);
            for(int dx=-1;dx<=3;dx++)for(int dz=9;dz<=11;dz++){
                put(a.x()+dx,0,a.z()+dz,(dx+dz)%3==0?Blocks.MOSSY_COBBLESTONE:Blocks.COBBLESTONE);
                if(dx==-1||dx==3)put(a.x()+dx,1,a.z()+dz,Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.BOTTOM));
            }
            for(int i=0;i<5;i++){
                double angle=Math.toRadians(40+i*31);int dx=(int)Math.round(Math.cos(angle)*14),dz=(int)Math.round(Math.sin(angle)*14);
                if(!walking(a.x()+dx,a.z()+dz))totem(a.x()+dx,a.z()+dz,4+i%2);
            }
            totem(a.x()-11,a.z()-5,5);
            chain(a.x()-10,a.z()-5,2,3);chain(a.x()-12,a.z()-5,3,3);
            boulder(a.x()+8,a.z()-7,2);
            mini(a.x()-5,a.z()-13,"fallen_tree","dark_oak",Rotation.CLOCKWISE_90,false);
            stump(a.x()+1,a.z()-8,2);
            decor(a.x()+9,0,a.z()+9,"goblin_sling_stones",3);
            decor(a.x()-8,0,a.z()+8,"goblin_tribal_sign",0);
            lantern(a.x()+11,a.z()+8,4,Direction.WEST);
        }
        private void boss(){
            Area a=BOSS;
            for(int dx=-5;dx<=5;dx++)for(int dz=-5;dz<=5;dz++)if(dx*dx+dz*dz<26&&pick(dx,dz,17,4)!=0)
                put(a.x()+dx,-1,a.z()+dz,pick(dx,dz,18,3)==0?Blocks.MOSSY_COBBLESTONE:Blocks.POLISHED_ANDESITE);
            splitTree(a.x()+4,a.z()+15);
            for(int i=0;i<8;i++){
                double angle=i*Math.PI/4;int x=a.x()+(int)Math.round(Math.cos(angle)*19),z=a.z()+(int)Math.round(Math.sin(angle)*19);
                if(walking(x,z))continue;
                if(i%2==0)boulder(x,z,1);else totem(x,z,4+i%3);
            }
            totem(a.x()-14,a.z()+11,5);chain(a.x()-12,a.z()+11,2,3);
            lantern(a.x()-15,a.z()-9,5,Direction.EAST);
            lantern(a.x()+15,a.z()+8,5,Direction.WEST);
        }
        private void abandonedCamps(){
            tent(-14,6,true,Blocks.WOOL.gray(),Direction.EAST);
            tent(18,44,true,Blocks.WOOL.brown(),Direction.WEST);
            tent(-54,91,true,Blocks.WOOL.gray(),Direction.SOUTH);
            extinctFire(-15,20);extinctFire(20,49);
            decor(-9,0,17,"old_waypost",1);decor(15,0,46,"rope_waypost",3);
            mini(-16,29,"log_cart","spruce",Rotation.COUNTERCLOCKWISE_90,true);
            mini(26,111,"fallen_tree","oak",Rotation.NONE,false);
        }
        private void undergrowth(){
            for(int x=-87;x<=86;x++)for(int z=-5;z<=188;z++){
                int y=height(x,z);BlockPos pos=origin.offset(x,y,z);
                if(!air(x,y,z)||!world.getBlockState(pos.below()).isFaceSturdy(world,pos.below(),Direction.UP))continue;
                double trail=trailDistance(x,z);boolean field=clearing(x,z,-1),walk=walking(x,z);
                int n=pick(x,z,19,100);
                if(walk){if(n<9)decor(x,y,z,"ground_branches",n);continue;}
                if(field){if(n<12)decor(x,y,z,n<7?"ground_branches":"dark_pebbles",n);else if(n<18)plant(x,y,z,Blocks.FERN);continue;}
                if(trail>4.5&&n<7){
                    for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(!walking(x+dx,z+dz)&&!clearing(x+dx,z+dz,2)&&pick(x+dx,z+dz,20,3)!=0){
                        int h=height(x+dx,z+dz);leaves(x+dx,h,z+dz,Blocks.OAK_LEAVES);
                        if(dx==0&&dz==0)leaves(x,h+1,z,Blocks.OAK_LEAVES);
                    }
                }else if(n<28)decor(x,y,z,z>110?"dark_oak_leaf_litter":"oak_leaf_litter",n);
                else if(n<33)decor(x,y,z,"ground_branches",n);
                else if(n<37)decor(x,y,z,n%2==0?"gray_pebbles":"dark_pebbles",n);
                else if(n<41)plant(x,y,z,n%2==0?Blocks.BROWN_MUSHROOM:Blocks.RED_MUSHROOM);
                else if(n<89){
                    // Planting palette: 48% short grass, 12% tall grass, the rest mainly ferns.
                    int p=pick(x,z,21,100);
                    if(p<48)plant(x,y,z,Blocks.SHORT_GRASS);
                    else if(p<60)tallPlant(x,y,z,Blocks.TALL_GRASS);
                    else if(p<92)plant(x,y,z,Blocks.FERN);
                    else if(p<98)tallPlant(x,y,z,Blocks.LARGE_FERN);
                    else plant(x,y,z,Blocks.DANDELION);
                }
            }
        }
        private void leanTo(Area a,int dx,int dz,int width,int depth,boolean ritual){
            int x=a.x()+dx,z=a.z()+dz;
            for(int px:new int[]{0,width-1})for(int pz:new int[]{0,depth-1}){
                put(x+px,0,z+pz,Blocks.MOSSY_COBBLESTONE);
                for(int y=1;y<=4;y++)put(x+px,y,z+pz,Blocks.STRIPPED_SPRUCE_LOG);
            }
            for(int ix=0;ix<width;ix++){
                beam(x+ix,4,z,Direction.Axis.X);beam(x+ix,4,z+depth-1,Direction.Axis.X);
                for(int iz=-1;iz<=depth;iz++){
                    if(ix==0)beam(x+ix,5,z+iz,Direction.Axis.Z);
                    if(!(ix==width-2&&iz==depth-1))put(x+ix,5+(ix==0?1:0),z+iz,(ix+iz)%4==0?Blocks.SPRUCE_SLAB:ritual?Blocks.WOOL.gray():Blocks.WOOL.brown());
                }
            }
            for(int iz=1;iz<depth-1;iz++){
                beam(x,4,z+iz,Direction.Axis.Z);beam(x+width-1,4,z+iz,Direction.Axis.Z);
                if(iz%2==0)put(x+width-1,2,z+iz,Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING,Direction.WEST).setValue(TrapDoorBlock.OPEN,true));
            }
            for(int ix=1;ix<width-1;ix++)put(x+ix,2,z+depth-1,Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.TOP));
            put(x,4,z-1,Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.SOUTH).setValue(StairBlock.HALF,Half.TOP));
            put(x+width-1,4,z-1,Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.SOUTH).setValue(StairBlock.HALF,Half.TOP));
        }
        private void tent(int cx,int cz,boolean ruined,Block cloth,Direction facing){
            for(int i=-3;i<=3;i++)for(int j=-2;j<=2;j++){
                int x=cx+(facing.getAxis()==Direction.Axis.Z?j:i),z=cz+(facing.getAxis()==Direction.Axis.Z?i:j),y=3-Math.abs(j);
                if(ruined&&(i>0&&j>=0||pick(i,j,cx+cz,5)==0))continue;
                put(x,y,z,cloth);
            }
            for(int sign:new int[]{-1,1}){
                int x=cx+(facing.getAxis()==Direction.Axis.X?sign*3:0),z=cz+(facing.getAxis()==Direction.Axis.Z?sign*3:0);
                for(int side:new int[]{-2,2})for(int y=0;y<(ruined&&sign==1?1:2);y++)
                    put(x+(facing.getAxis()==Direction.Axis.Z?side:0),y,z+(facing.getAxis()==Direction.Axis.X?side:0),Blocks.SPRUCE_FENCE);
                int ex=cx+(facing.getAxis()==Direction.Axis.X?sign*4:0),ez=cz+(facing.getAxis()==Direction.Axis.Z?sign*4:0);
                if(!walking(ex,ez))decor(ex,0,ez,"rope_waypost",sign+2);
            }
            if(ruined){
                beam(cx+2,0,cz+2,Direction.Axis.X);put(cx+3,0,cz+2,Blocks.WOOL.brown());
                decor(cx-1,0,cz,"ground_branches",3);decor(cx+1,0,cz-1,"oak_leaf_litter",2);
            }else{decor(cx,0,cz,"goblin_hide_mat",cx+cz);decor(cx+1,0,cz,"goblin_hide_mat",cx-cz);}
        }
        private void firePit(int x,int z){
            put(x,0,z,Blocks.CAMPFIRE.defaultBlockState());
            for(Direction d:Direction.Plane.HORIZONTAL)put(x+d.getStepX(),0,z+d.getStepZ(),Blocks.COBBLESTONE_SLAB);
            for(int side:new int[]{-2,2})for(int y=0;y<=3;y++)put(x+side,y,z,Blocks.SPRUCE_FENCE);
            for(int dx=-2;dx<=2;dx++)beam(x+dx,3,z,Direction.Axis.X);
            chain(x,z,1,2);put(x,1,z,Blocks.IRON_BARS);
            for(int dx=-2;dx<=2;dx++)if(dx!=0)put(x+dx,0,z+3,Blocks.SPRUCE_SLAB);
        }
        private void extinctFire(int x,int z){
            put(x,0,z,Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT,false));
            for(Direction d:Direction.Plane.HORIZONTAL)putIfAir(x+d.getStepX(),0,z+d.getStepZ(),Blocks.MOSSY_COBBLESTONE_SLAB);
            decor(x+2,0,z+1,"short_log_stack",1);decor(x-2,0,z,"ground_branches",4);
        }
        private void dryingRack(int x,int z){
            for(int dx:new int[]{0,4})for(int y=0;y<=3;y++)put(x+dx,y,z,Blocks.SPRUCE_FENCE);
            for(int dx=0;dx<=4;dx++)beam(x+dx,3,z,Direction.Axis.X);
            chain(x+1,z,2,2);chain(x+3,z,2,2);
            decor(x,0,z+1,"firewood_bundle",1);barrel(x+4,0,z+1,Direction.SOUTH);
        }
        private void totem(int x,int z,int tall){
            put(x,0,z,Blocks.MOSSY_COBBLESTONE);
            for(int y=1;y<=tall;y++)put(x,y,z,y%3==0?Blocks.STRIPPED_DARK_OAK_LOG:Blocks.DARK_OAK_LOG);
            for(int dx=-2;dx<=2;dx++)beam(x+dx,tall-1,z,Direction.Axis.X);
            put(x-1,tall,z,Blocks.MOSSY_COBBLESTONE_WALL);put(x+1,tall,z,Blocks.MOSSY_COBBLESTONE_WALL);
            decor(x,0,z+1,"goblin_tribal_sign",2);
        }
        private void splitTree(int x,int z){
            for(int y=0;y<15;y++){
                put(x,y,z,Blocks.DARK_OAK_LOG);
                if(y<9)put(x+1,y,z,Blocks.DARK_OAK_LOG);
                if(y<6)put(x,y,z+1,y%3==0?Blocks.COAL_BLOCK:Blocks.DARK_OAK_LOG);
            }
            for(int dx=-4;dx<=4;dx++){
                int y=dx<0?7:10;put(x+dx,y,z,Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS,Direction.Axis.X));
                if(Math.abs(dx)==4)for(int dy=1;dy<=3;dy++)put(x+dx,y+dy,z,Blocks.DARK_OAK_LOG);
            }
            for(Direction d:Direction.Plane.HORIZONTAL)for(int k=1;k<=4;k++){
                int rx=x+d.getStepX()*k,rz=z+d.getStepZ()*k;
                if(!walking(rx,rz))put(rx,0,rz,Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS,d.getAxis()));
            }
            decor(x-2,0,z-2,"dark_pebbles",5);decor(x+3,0,z-1,"ground_branches",7);
        }
        private void stump(int x,int z,int tall){
            for(int y=0;y<tall;y++)put(x,y,z,y==0?Blocks.COAL_BLOCK:Blocks.DARK_OAK_LOG);
            put(x+1,0,z,Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS,Direction.Axis.X));
            decor(x-1,0,z,"dark_pebbles",3);
        }
        private void boulder(int x,int z,int size){
            for(int dx=-size;dx<=size;dx++)for(int dz=-size;dz<=size;dz++)for(int y=0;y<=size;y++)
                if(dx*dx+dz*dz+y*y<size*size+2&&!walking(x+dx,z+dz))put(x+dx,y,z+dz,pick(dx,dz,22,3)==0?Blocks.MOSSY_COBBLESTONE:Blocks.ANDESITE);
        }
        private void crate(int x,int z,boolean open){
            put(x,0,z,Blocks.SPRUCE_PLANKS);
            for(Direction d:Direction.Plane.HORIZONTAL)put(x+d.getStepX(),0,z+d.getStepZ(),Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING,d).setValue(TrapDoorBlock.OPEN,true));
            if(!open)put(x,1,z,Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF,Half.BOTTOM));
        }
        private void barrel(int x,int y,int z,Direction facing){put(x,y,z,Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING,facing));}
        private void lantern(int x,int z,int tall,Direction out){
            int h=height(x,z);
            for(int y=h;y<=tall;y++)put(x,y,z,y==h?Blocks.MOSSY_COBBLESTONE:Blocks.SPRUCE_LOG);
            for(int k=1;k<=2;k++)beam(x+out.getStepX()*k,tall,z+out.getStepZ()*k,out.getAxis());
            int ex=x+out.getStepX()*2,ez=z+out.getStepZ()*2;
            put(ex,tall-1,ez,Blocks.IRON_CHAIN);put(ex,tall-2,ez,Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true));
        }
        private void chain(int x,int z,int low,int high){for(int y=low;y<=high;y++)put(x,y,z,Blocks.IRON_CHAIN);}
        private void beam(int x,int y,int z,Direction.Axis axis){put(x,y,z,Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS,axis));}
        private void mini(int cx,int cz,String kind,String wood,Rotation rotation,boolean broken){
            var id=Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight","micro_decorations/"+kind+"/"+wood);
            var template=world.getStructureManager().get(id).orElseThrow(()->new IllegalStateException("缺少营地素材："+id));
            var settings=new StructurePlaceSettings().setRotation(rotation).setIgnoreEntities(true).setKnownShape(true).addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR);
            var box=template.getBoundingBox(settings,BlockPos.ZERO);
            BlockPos at=origin.offset(cx-Math.floorDiv(box.minX()+box.maxX(),2),0,cz-Math.floorDiv(box.minZ()+box.maxZ(),2));
            if(!template.placeInWorld(world,at,at,settings,random,Block.UPDATE_CLIENTS|Block.UPDATE_SUPPRESS_DROPS))throw new IllegalStateException("营地素材放置失败："+id);
            if(broken){
                // Damage the native cart locally; never alter the shared source template.
                for(BlockPos part:List.of(new BlockPos(0,0,2),new BlockPos(0,1,2),new BlockPos(1,1,1))){
                    BlockPos transformed=StructureTemplate.transform(part,Mirror.NONE,rotation,BlockPos.ZERO);
                    world.setBlock(at.offset(transformed),Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_SUPPRESS_DROPS);
                }
                decor(cx+3,0,cz+2,"ground_branches",4);
            }
        }
        private void plant(int x,int y,int z,Block plant){
            BlockPos at=origin.offset(x,y,z);var state=plant.defaultBlockState();
            if(air(x,y,z)&&state.canSurvive(world,at))put(x,y,z,state);
        }
        private void tallPlant(int x,int y,int z,Block plant){
            var state=plant.defaultBlockState().setValue(DoublePlantBlock.HALF,DoubleBlockHalf.LOWER);
            if(air(x,y,z)&&air(x,y+1,z)&&state.canSurvive(world,origin.offset(x,y,z))){put(x,y,z,state);put(x,y+1,z,state.setValue(DoublePlantBlock.HALF,DoubleBlockHalf.UPPER));}
        }
        private void decor(int x,int y,int z,String id,int seed){
            BlockPos at=origin.offset(x,y,z);
            if(!air(x,y,z)||!world.getBlockState(at.below()).isFaceSturdy(world,at.below(),Direction.UP))return;
            var state=decorations.block(id).defaultBlockState();
            if(state.hasProperty(HorizontalDirectionalBlock.FACING))state=state.setValue(HorizontalDirectionalBlock.FACING,Direction.from2DDataValue(Math.floorMod(seed,4)));
            for(var property:state.getProperties())if(List.of("layout","variant","segment_amount").contains(property.getName()))state=variant(state,property,seed);
            put(x,y,z,state);
        }
        private <T extends Comparable<T>> BlockState variant(BlockState state,Property<T> property,int seed){var values=List.copyOf(property.getPossibleValues());return state.setValue(property,values.get(Math.floorMod(seed,values.size())));}
        private boolean air(int x,int y,int z){return world.getBlockState(origin.offset(x,y,z)).isAir();}
        private void putIfAir(int x,int y,int z,Block block){if(air(x,y,z))put(x,y,z,block);}
        private void put(int x,int y,int z,Block block){put(x,y,z,block.defaultBlockState());}
        private void put(int x,int y,int z,BlockState state){
            if(x<BOUNDS.minX()||x>BOUNDS.maxX()||z<BOUNDS.minZ()||z>BOUNDS.maxZ()||y<BOUNDS.minY()||y>BOUNDS.maxY())throw new IllegalArgumentException("哥布林营地构件超出地图范围");
            world.setBlock(origin.offset(x,y,z),state,Block.UPDATE_CLIENTS|Block.UPDATE_SUPPRESS_DROPS);
        }
    }
    private GoblinCamp(){}
}
