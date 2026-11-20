package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.DungeonPorts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Continuous open spiral; construction, arrivals, pursuit and scenery share the same anchors. */
public final class DragonTower {
    public static final String VERSION="DRAGON_SKY_LAYOUT_2";
    public static final DungeonLayouts.Bounds BOUNDS=new DungeonLayouts.Bounds(-45,-6,-45,45,183,45);
    public static final int LEVELS=7,FLOOR_HEIGHT=26;
    private static final double TAU=Math.PI*2,STAIR_RADIUS=32,LANDING_HALF=.10;
    private static final int CROWN_BRIDGE_START = 14, LANDING_BRIDGE_HALF_WIDTH = 3;
    private static final double[] PHASE={0,2.02,4.13,6.20,8.34,10.43,TAU*2};
    private static final String[] TITLES={"塔基守望庭","战备军械层","翼龙栖息层","蓝晶供能层","坍塌观测廊","龙卫残殿","鲁卡斯断裂塔冠"};
    public record Floor(int index,String title,int y,double x,double z,double rx,double rz,double angle){
        public Vec3 at(double u,double h,double v){return new Vec3(x+u*Math.cos(angle)-v*Math.sin(angle),y+h,z+u*Math.sin(angle)+v*Math.cos(angle));}
        public Vec3 local(double px,double pz){double dx=px-x,dz=pz-z;return new Vec3(dx*Math.cos(angle)+dz*Math.sin(angle),0,-dx*Math.sin(angle)+dz*Math.cos(angle));}
        public boolean contains(double px,double pz,double margin){
            Vec3 p=local(px,pz);double a=Math.atan2(p.z,p.x),edge=1+(.75*Math.sin(a*3+index)+.45*Math.cos(a*5-index))/Math.min(rx,rz);
            return Math.hypot(p.x/(rx+margin),p.z/(rz+margin))<=edge;
        }
    }
    private static final List<Floor> FLOORS=createFloors();
    private static List<Floor> createFloors(){
        var result=new ArrayList<Floor>();
        for(int i=0;i<LEVELS;i++){
            double a=PHASE[i],r=i==6?0:20+i%3,side=i==6?0:(i%2==0?1:-1);
            result.add(new Floor(i,TITLES[i],i*FLOOR_HEIGHT,r*Math.cos(a)-side*Math.sin(a),r*Math.sin(a)+side*Math.cos(a),i==6?22:14+i%2,i==6?21:13+(i+1)%2,a));
        }
        return List.copyOf(result);
    }
    public static List<Floor> floors(){return FLOORS;}
    public static Vec3 landing(int floor){return new Vec3(STAIR_RADIUS*Math.cos(PHASE[floor]),floor*FLOOR_HEIGHT,STAIR_RADIUS*Math.sin(PHASE[floor]));}
    public static Vec3 entry(BlockPos origin){return landing(0).add(origin.getX()+.5,origin.getY(),origin.getZ()+.5);}
    public static boolean arrives(int floor, Vec3 local) {
        Vec3 landing = landing(floor);
        return Math.abs(local.y - landing.y) < 3.5
                && (Math.hypot(local.x - landing.x, local.z - landing.z) < 8
                || floor == LEVELS - 1 && onCrown(local, landing));
    }
    private static boolean onCrown(Vec3 local, Vec3 landing) {
        Floor crown = FLOORS.getLast();
        if (crown.contains(local.x, local.z, 1)) return true;

        Vec3 position = crown.local(local.x, local.z);
        double bridgeEnd = crown.local(landing.x, landing.z).x;
        return position.x >= CROWN_BRIDGE_START && position.x <= bridgeEnd
                && Math.abs(position.z) <= LANDING_BRIDGE_HALF_WIDTH + .5;
    }
    public static List<Vec3> spawns(int floor){
        Floor f=FLOORS.get(floor);
        return floor==6?List.of(f.at(-2,0,0)):List.of(f.at(-5,0,-4),f.at(4,0,5),f.at(-6,0,5),f.at(3,0,-7));
    }
    /** Half-block treads; neighbouring cardinal cells rise at most half a block. */
    public static double stairHeight(double phase){
        phase=Math.clamp(phase,0,TAU*2);
        for(int i=0;i<LEVELS-1;i++)if(phase<=PHASE[i+1]){
            double start=PHASE[i]+LANDING_HALF,end=PHASE[i+1]-LANDING_HALF;
            return Math.floor((i*FLOOR_HEIGHT+Math.clamp((phase-start)/(end-start),0,1)*FLOOR_HEIGHT)*2+1e-7)/2;
        }
        return (LEVELS-1)*FLOOR_HEIGHT;
    }
    private static double angle(double x,double z){double a=Math.atan2(z,x);return a<0?a+TAU:a;}
    private static int nearestFloor(double y){return Math.clamp((int)Math.round(y/FLOOR_HEIGHT),0,LEVELS-1);}
    private static double phase(Vec3 p){
        double a=angle(p.x,p.z),best=a,error=Double.MAX_VALUE;
        for(int turn=0;turn<3;turn++){
            double candidate=a+turn*TAU;if(candidate>TAU*2+.001)continue;
            double d=Math.abs(stairHeight(candidate)-p.y);if(d<error){error=d;best=candidate;}
        }
        return Math.hypot(p.x,p.z)<27||error>3?PHASE[nearestFloor(p.y)]:best;
    }
    public static Vec3 pursuitWaypoint(Vec3 from,Vec3 target){
        double current=phase(from),destination=phase(target);
        if(Math.hypot(from.x,from.z)<27||Math.abs(stairHeight(current)-from.y)>3)return landing(nearestFloor(from.y));
        if(Math.abs(current-destination)<.10)return target;
        double next=Math.clamp(current+Math.copySign(.15,destination-current),Math.min(current,destination),Math.max(current,destination));
        return new Vec3(STAIR_RADIUS*Math.cos(next),stairHeight(next),STAIR_RADIUS*Math.sin(next));
    }
    public record Relic(String id,Vec3 position,Item item,float pitch,float yaw,float roll,float scale){}
    public static List<Relic> relics(){
        var result=new ArrayList<Relic>();
        result.add(new Relic("gate_standard",FLOORS.get(0).at(-7,4,8),Items.BANNER.lightBlue(),0,90,-8,.9f));
        result.add(new Relic("old_guard_shield",FLOORS.get(0).at(-8,1.2,7),Items.SHIELD,12,65,16,.9f));
        result.add(new Relic("armoury_blade",FLOORS.get(1).at(-8,2,8),Items.IRON_SWORD,5,30,22,1.1f));
        result.add(new Relic("armoury_axe",FLOORS.get(1).at(-6,2.2,8),Items.IRON_AXE,0,10,-18,1));
        result.add(new Relic("roost_bone",FLOORS.get(2).at(-7,1.2,-8),Items.BONE,82,35,0,1.4f));
        result.add(new Relic("survey_lens",FLOORS.get(4).at(-7,2.1,8),Items.SPYGLASS,22,65,-18,1.3f));
        result.add(new Relic("survey_dial",FLOORS.get(4).at(-8,1.3,7),Items.CLOCK,78,10,0,1.2f));
        result.add(new Relic("royal_standard",FLOORS.get(5).at(-7,5,-9),Items.BANNER.cyan(),0,160,19,1.2f));
        for(int i=3;i<LEVELS;i++)for(int j=0;j<i;j++){
            Floor f=FLOORS.get(i);double a=.7+j*1.7;
            result.add(new Relic("floating_stone_"+i+"_"+j,f.at(Math.cos(a)*(f.rx-2),4+j*1.6,Math.sin(a)*(f.rz-2)),
                    (j%2==0?Blocks.QUARTZ_STAIRS:Blocks.QUARTZ_BLOCK).asItem(),j*17,40+j*41,13+j*11,.9f+j*.18f));
        }
        return List.copyOf(result);
    }
    public static void build(ServerLevel world,BlockPos origin,DungeonPorts.CampDecorations decorations){new Builder(world,origin,decorations).build();}
    private static int hash(int x,int z,int seed){int n=x*73428767^z*912931^seed*19349663;n^=n>>>13;return Math.floorMod(n*1274126177,1000);}

    private static final class Builder {
        private final ServerLevel world;
        private final BlockPos origin;
        private final DungeonPorts.CampDecorations decorations;
        private final List<BlockPos> connectors=new ArrayList<>();
        Builder(ServerLevel world,BlockPos origin,DungeonPorts.CampDecorations decorations){this.world=world;this.origin=origin;this.decorations=decorations;}
        void build(){
            foundation();shell();
            for(Floor f:FLOORS){platform(f);pillars(f);obstacles(f);furnish(f);}
            for(int i=1;i<LEVELS;i++)catchBelow(FLOORS.get(i));
            crown();spiral();lighting();dressing();finishConnections();
            // Last pass only adds to air, preserving every original structural and decorative block.
            sealExits();
        }
        private void foundation(){
            for(int x=-42;x<=42;x++)for(int z=-42;z<=42;z++){
                double r=Math.hypot(x,z);if(r>42)continue;
                for(int y=-6;y<0;y++)put(x,y,z,y==-1?floorStone(x,z,0):r>39?Blocks.STONE_BRICKS:Blocks.POLISHED_DIORITE);
                if(r>40.5){put(x,0,z,Blocks.STONE_BRICKS);put(x,1,z,Blocks.COBBLESTONE_WALL);}
            }
        }
        private void shell(){
            for(int x=-39;x<=39;x++)for(int z=-39;z<=39;z++){
                double r=Math.hypot(x,z),a=angle(x,z);if(r<37||r>39)continue;
                double rib=Math.abs(Math.sin(a*8));
                for(int y=0;y<=178;y++){
                    int level=Math.min(6,y/FLOOR_HEIGHT),band=Math.floorMod(y,FLOOR_HEIGHT);
                    double fracture=Math.sin(a*3+y*.047)+Math.cos(a*7-y*.063)*.5;
                    boolean intact=fracture>(level<2?-1.3:level<4?-.3:level==4?.25:level==5?.65:1);
                    if(rib<.12&&y<176-hash(x/3,z/3,1)%12)intact=true;
                    if(!intact)continue;
                    boolean window=band>=6&&band<=15&&Math.abs(Math.cos(a*8))<.26;
                    if(window){if(band==6||band==15)put(x,y,z,Blocks.CHISELED_QUARTZ_BLOCK);else if(level<3)put(x,y,z,Blocks.GLASS);}
                    else put(x,y,z,rib<.12?Blocks.QUARTZ_PILLAR:band<2?Blocks.CHISELED_STONE_BRICKS:wallStone(x,y,z));
                }
            }
            for(int i=0;i<8;i++){
                double a=i*Math.PI/4+.12;int x=(int)Math.round(Math.cos(a)*38),z=(int)Math.round(Math.sin(a)*38),top=171+hash(i,0,2)%12;
                for(int y=-4;y<=top;y++)for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)
                    put(x+dx,y,z+dz,y%26<2?Blocks.CHISELED_QUARTZ_BLOCK:Blocks.QUARTZ_PILLAR);
                for(int y=4;y<top;y+=26){put(x,y,z,Blocks.COPPER_BLOCK.waxed().weathered());put(x,y+1,z,Blocks.SEA_LANTERN);}
            }
        }
        private Block wallStone(int x,int y,int z){int h=hash(x/2+z,y/3,3);return h<100?Blocks.CRACKED_STONE_BRICKS:h<210?Blocks.POLISHED_DIORITE:h<320?Blocks.QUARTZ_BRICKS:Blocks.SMOOTH_QUARTZ;}
        private Block floorStone(int x,int z,int level){int h=hash(x/2,z/2,level+5);return h<120?Blocks.CRACKED_STONE_BRICKS:h<280?Blocks.POLISHED_DIORITE:h<350?Blocks.CHISELED_QUARTZ_BLOCK:Blocks.SMOOTH_QUARTZ;}
        private Vec3 gap(Floor f){
            Floor previous=FLOORS.get(f.index-1);double dx=previous.x-f.x,dz=previous.z-f.z,len=Math.hypot(dx,dz);
            return new Vec3(f.x+dx/len*(f.rx-.3),f.y,f.z+dz/len*(f.rz-.3));
        }
        private boolean gapAt(Floor f,int x,int z){if(f.index==0)return false;Vec3 p=gap(f);return Math.hypot(x-p.x,z-p.z)<2.1;}
        private void platform(Floor f){
            for(int x=-37;x<=37;x++)for(int z=-37;z<=37;z++)if(f.contains(x,z,0)){
                boolean edge=!f.contains(x+1,z,0)||!f.contains(x-1,z,0)||!f.contains(x,z+1,0)||!f.contains(x,z-1,0);
                if(edge&&gapAt(f,x,z))continue;
                put(x,f.y-1,z,floorStone(x,z,f.index));put(x,f.y-2,z,edge?Blocks.CHISELED_STONE_BRICKS:Blocks.STONE_BRICKS);
                if(edge){
                    Vec3 local=f.local(x,z);boolean entrance=(f.index==6?local.x>15:Math.hypot(x,z)>26)&&Math.abs(local.z)<4;
                    if(!entrance){put(x,f.y,z,Blocks.STONE_BRICKS);put(x,f.y+1,z,Blocks.COBBLESTONE_WALL);}
                }
            }
            bridge(f.at(f.index == LEVELS - 1 ? CROWN_BRIDGE_START : 6, 0, 0),
                    landing(f.index), f.y, LANDING_BRIDGE_HALF_WIDTH, false);
        }
        private void catchBelow(Floor f){
            Vec3 g=gap(f);Floor lower=FLOORS.get(f.index-1);
            double dx=lower.x-g.x,dz=lower.z-g.z,length=Math.hypot(dx,dz);
            for(int x=(int)Math.floor(g.x)-3;x<=(int)Math.floor(g.x)+3;x++)for(int z=(int)Math.floor(g.z)-3;z<=(int)Math.floor(g.z)+3;z++){
                put(x,lower.y-1,z,Blocks.POLISHED_DIORITE);put(x,lower.y-2,z,Blocks.STONE_BRICKS);
                boolean exit=length<1||(x-g.x)*dx+(z-g.z)*dz>length&&Math.abs((x-g.x)*dz-(z-g.z)*dx)<length*2.4;
                if(!exit&&(Math.abs(x-(int)Math.floor(g.x))==3||Math.abs(z-(int)Math.floor(g.z))==3))putIfAir(x,lower.y,z,Blocks.COBBLESTONE_WALL);
            }
            bridge(new Vec3(g.x,lower.y,g.z),new Vec3(lower.x,lower.y,lower.z),lower.y,2,true);
            put((int)Math.floor(g.x),lower.y-1,(int)Math.floor(g.z),Blocks.SEA_LANTERN);
        }
        private void bridge(Vec3 from,Vec3 to,int y,int halfWidth,boolean rails){
            double dx=to.x-from.x,dz=to.z-from.z,length=Math.hypot(dx,dz);if(length<.1)return;
            double nx=-dz/length,nz=dx/length;int steps=(int)Math.ceil(length*2);
            for(int i=0;i<=steps;i++)for(int side=-halfWidth;side<=halfWidth;side++){
                double t=i/(double)steps;int x=(int)Math.round(from.x+dx*t+nx*side),z=(int)Math.round(from.z+dz*t+nz*side);
                boolean light=side==0&&i%6==0||world.getBlockState(origin.offset(x,y-1,z)).is(Blocks.SEA_LANTERN);
                put(x,y-1,z,light?Blocks.SEA_LANTERN:Blocks.POLISHED_DIORITE);
                for(int h=2;h<=2+(int)Math.round(Math.sin(t*Math.PI)*2);h++)putIfAir(x,y-h,z,Blocks.STONE_BRICKS);
                if(rails&&Math.abs(side)<halfWidth)for(int h=0;h<4;h++)put(x,y+h,z,Blocks.AIR);
                if(rails&&Math.abs(side)==halfWidth&&i>6&&i<steps-6)putIfAir(x,y,z,Blocks.COBBLESTONE_WALL);
            }
        }
        private void pillars(Floor f){
            for(int i=0;i<5;i++){
                double a=.5+i*1.13;int u=(int)Math.round(Math.cos(a)*(f.rx-3)),v=(int)Math.round(Math.sin(a)*(f.rz-3));
                if(reserved(f,u,v,1.2))continue;
                int height=f.index<2?12+i%3:f.index<4?7+i%4:3+(i*5+f.index)%10;
                for(int du=-1;du<=1;du++)for(int dv=-1;dv<=1;dv++)local(f,u+du,0,v+dv,Blocks.CHISELED_STONE_BRICKS);
                for(int h=1;h<height;h++)local(f,u,h,v,Blocks.QUARTZ_PILLAR);
                local(f,u,height,v,Blocks.CHISELED_QUARTZ_BLOCK);
                for(Direction d:Direction.Plane.HORIZONTAL)local(f,u+d.getStepX(),height,v+d.getStepZ(),Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.HALF,Half.TOP).setValue(StairBlock.FACING,d.getOpposite()));
                if(f.index<3&&i%2==0)local(f,u,height-2,v,Blocks.SEA_LANTERN);
            }
        }
        private boolean reserved(Floor f,double u,double v,double margin){
            double line=Math.sin(u*(f.index==6?.16:.24)+f.index*.3)*1.6;
            if(Math.abs(v-line)<2.8+margin)return true;
            Vec3 point=f.at(u,0,v);
            for(Vec3 spawn:spawns(f.index))if(Math.hypot(point.x-spawn.x,point.z-spawn.z)<2+margin)return true;
            return f.index>0&&point.distanceToSqr(gap(f))<Math.pow(3+margin,2);
        }
        private void obstacles(Floor f){
            // Six deliberate groups flank the winding activity strip; two smaller groups fill suitable recesses.
            int[][] groups={{-9,-8},{-2,-10},{7,-9},{-10,6},{-3,9},{6,9},{10,-5},{0,11}};
            for(int i=0;i<groups.length;i++){
                int u=(int)Math.round(groups[i][0]*(f.index==6?1.45:1)),v=(int)Math.round(groups[i][1]*(f.index==6?1.4:1));
                Vec3 p=f.at(u,0,v);BlockPos at=relative(p);
                if(i>=6&&(reserved(f,u,v,.35)||!f.contains(p.x,p.z,-1)||!world.getBlockState(origin.offset(at)).isAir()))continue;
                int n=i+f.index;
                if(n%3==0)fallenColumn(f,u,v,n);else if(n%3==1)rubble(f,u,v,2+n%2,n);else brokenWall(f,u,v,n);
            }
        }
        private void rubble(Floor f,int u,int v,int size,int salt){
            for(int du=-size;du<=size;du++)for(int dv=-size;dv<=size;dv++){
                if(du*du+dv*dv>size*size+1||reserved(f,u+du,v+dv,.15)||!supported(f,u+du,v+dv))continue;
                int h=1+hash(u+du,v+dv,f.index+salt)%3;if(Math.abs(du)+Math.abs(dv)>=size)h=1;
                for(int y=0;y<h;y++)localIfAir(f,u+du,y,v+dv,(y+h+salt)%3==0?Blocks.ANDESITE:Blocks.CRACKED_STONE_BRICKS);
                if(h==1)localIfAir(f,u+du,h,v+dv,Blocks.STONE_SLAB);
            }
        }
        private void fallenColumn(Floor f,int u,int v,int salt){
            for(int n=-2;n<=2;n++){
                if(reserved(f,u+n,v,.2)||!supported(f,u+n,v))continue;
                localIfAir(f,u+n,0,v,Blocks.QUARTZ_PILLAR.defaultBlockState().setValue(RotatedPillarBlock.AXIS,Math.abs(Math.cos(f.angle))>.7?Direction.Axis.X:Direction.Axis.Z));
                if(n==0||n==1)localIfAir(f,u+n,1,v,Blocks.CHISELED_QUARTZ_BLOCK);
                localIfAir(f,u+n,0,v+1,Blocks.POLISHED_DIORITE_SLAB);
            }
            rubble(f,u-2,v-1,1,salt);
        }
        private void brokenWall(Floor f,int u,int v,int salt){
            for(int n=-2;n<=2;n++)for(int y=0;y<1+Math.floorMod(n+salt,3);y++)
                if(!reserved(f,u,v+n,.2)&&supported(f,u,v+n))localIfAir(f,u,y,v+n,y==Math.floorMod(n+salt,3)?Blocks.CHISELED_STONE_BRICKS:Blocks.STONE_BRICKS);
            localIfAir(f,u+1,0,v,Blocks.COBBLESTONE_SLAB);
        }
        private void furnish(Floor f){
            switch(f.index){
                case 0->{arch(f,-7,8,9);localIfAir(f,-9,0,7,Blocks.MOSSY_STONE_BRICKS);rack(f,-8,-8);}
                case 1->{rack(f,-8,8);rack(f,-3,-10);table(f,4,9);localIfAir(f,7,0,8,Blocks.BARREL);}
                case 2->{nest(f,-7,-8);nest(f,6,8);perch(f,-9,8,6);perch(f,8,-8,9);}
                case 3->{crystal(f,-6,-8,4);crystal(f,7,7,3);table(f,-8,8);decor(f,-8,1,8,"dragon_rune_disc",3);}
                case 4->{arch(f,-8,8,6);table(f,-7,8);fallenColumn(f,7,-9,4);}
                case 5->{arch(f,-8,-9,10);rack(f,7,9);crystal(f,-7,8,3);}
                case 6->{arch(f,-13,13,15);arch(f,12,-13,19);crystal(f,-14,-11,4);crystal(f,13,12,5);}
                default->throw new IllegalArgumentException("未知龙塔层");
            }
        }
        private void arch(Floor f,int u,int v,int height){
            for(int s:new int[]{-3,3})for(int y=0;y<=height;y++)localIfAir(f,u+s,y,v,y==0||y==height?Blocks.CHISELED_QUARTZ_BLOCK:Blocks.QUARTZ_PILLAR);
            for(int n=-3;n<=3;n++)if(f.index<3||n<0)for(int h=0;h<2;h++)localIfAir(f,u+n,height-h,v,Blocks.QUARTZ_BRICKS);
        }
        private void rack(Floor f,int u,int v){
            for(int n=-2;n<=2;n++)localIfAir(f,u+n,0,v,Blocks.POLISHED_DIORITE);
            decor(f,u-1,1,v,"dragon_bronze_spear_rack",f.index);decor(f,u+1,1,v,"dragon_bronze_spear_rack",f.index+1);
            for(int s:new int[]{-3,3}){localIfAir(f,u+s,0,v,Blocks.CHISELED_QUARTZ_BLOCK);localIfAir(f,u+s,1,v,Blocks.COPPER_BLOCK.waxed().weathered());}
        }
        private void table(Floor f,int u,int v){
            for(int n=-2;n<=2;n++)localIfAir(f,u+n,0,v,Blocks.POLISHED_DIORITE);
            localIfAir(f,u-2,1,v,Blocks.POLISHED_DIORITE_SLAB);localIfAir(f,u+2,1,v,Blocks.POLISHED_DIORITE_SLAB);
        }
        private void nest(Floor f,int u,int v){
            for(int du=-2;du<=2;du++)for(int dv=-2;dv<=2;dv++)if(du*du+dv*dv<=5)
                localIfAir(f,u+du,0,v+dv,du*du+dv*dv>=3?Blocks.MOSSY_COBBLESTONE_SLAB:Blocks.HAY_BLOCK);
            decor(f,u,1,v,"dragon_scale_scatter",f.index);decor(f,u+1,1,v,"ground_branches",2);
        }
        private void perch(Floor f,int u,int v,int height){
            for(int y=0;y<height;y++)localIfAir(f,u,y,v,Blocks.QUARTZ_PILLAR);
            for(int n=-2;n<=2;n++)localIfAir(f,u+n,height,v,Blocks.SMOOTH_QUARTZ_SLAB);
        }
        private void crystal(Floor f,int u,int v,int height){
            for(int du=-1;du<=1;du++)for(int dv=-1;dv<=1;dv++)localIfAir(f,u+du,0,v+dv,Blocks.COPPER_BLOCK.waxed().weathered());
            local(f,u,1,v,Blocks.SEA_LANTERN);
            for(int y=2;y<height;y++)localIfAir(f,u,y,v,Blocks.AMETHYST_BLOCK);
            localIfAir(f,u,height,v,Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING,Direction.UP));
        }
        private void crown(){
            for(int i=0;i<12;i++){
                double a=i*TAU/12;int x=(int)Math.round(Math.cos(a)*31),z=(int)Math.round(Math.sin(a)*31),top=168+hash(i,1,9)%15;
                for(int y=156;y<=top;y++)putIfAir(x,y,z,y==top?Blocks.CHISELED_QUARTZ_BLOCK:Blocks.QUARTZ_PILLAR);
                if(i%3==0)for(int n=1;n<=4;n++)putIfAir(x+n,top-2,z,Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.HALF,Half.TOP).setValue(StairBlock.FACING,Direction.WEST));
            }
        }
        private void spiral(){
            // Carve after every other structure so crossing floors cannot cover the treads or headroom.
            for(int turn=0;turn<2;turn++)for(int x=-35;x<=35;x++)for(int z=-35;z<=35;z++){
                double r=Math.hypot(x,z);if(r<29||r>35)continue;
                double h=stairHeight(angle(x,z)+turn*TAU);int y=(int)Math.floor(h);
                supportStair(x,z,y-1);
                for(int clear=y;clear<=y+4;clear++)put(x,clear,z,Blocks.AIR);
                put(x,y-1,z,hash(x,z,turn)<160?Blocks.SEA_LANTERN:Blocks.POLISHED_DIORITE);
                if(h-y>.1)put(x,y,z,Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.BOTTOM));
            }
            for(int i=0;i<LEVELS;i++){
                Vec3 p=landing(i);int x=(int)Math.round(p.x),z=(int)Math.round(p.z),y=i*FLOOR_HEIGHT;
                for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)if(dx*dx+dz*dz<=10){
                    put(x+dx,y-1,z+dz,Blocks.POLISHED_DIORITE);
                    for(int h=0;h<=4;h++)put(x+dx,y+h,z+dz,Blocks.AIR);
                }
                put(x,y-1,z,Blocks.SEA_LANTERN);
            }
            // Rasterise both entire guard bands; sparse angle samples can leave one-block holes.
            for(int turn=0;turn<2;turn++)for(int x=-37;x<=37;x++)for(int z=-37;z<=37;z++){
                double r=Math.hypot(x,z);if(!(r>=27.6&&r<29||r>35&&r<=36.7))continue;
                double a=angle(x,z),h=stairHeight(a+turn*TAU);int y=(int)Math.ceil(h);
                if(mouth(a,h))continue;
                supportStair(x,z,y-1);
                putIfAir(x,y-1,z,Blocks.STONE_BRICKS);putIfAir(x,y,z,Blocks.COBBLESTONE_WALL);
            }
            for(int turn=0;turn<2;turn++)for(int n=0;n<360;n+=8){
                double a=Math.toRadians(n),h=stairHeight(a+turn*TAU);int y=(int)Math.ceil(h);
                if(mouth(a,h))continue;
                for(double r:new double[]{27.8,36.2}){
                    int x=(int)Math.round(Math.cos(a)*r),z=(int)Math.round(Math.sin(a)*r);
                    putIfAir(x,y+1,z,Blocks.SEA_LANTERN);putIfAir(x,y+2,z,Blocks.POLISHED_DIORITE_SLAB);
                }
            }
            for(int i=0;i<10;i++){
                double a=.75+i*1.13;if(FLOORS.stream().anyMatch(f->Math.abs(f.angle-a)<.22))continue;
                int y=(int)Math.ceil(stairHeight(a));
                for(int r=29;r<=30;r++){
                    int x=(int)Math.round(Math.cos(a)*r),z=(int)Math.round(Math.sin(a)*r);
                    putIfAir(x,y,z,Blocks.CRACKED_STONE_BRICKS);putIfAir(x,y+1,z,Blocks.COBBLESTONE_SLAB);
                }
            }
        }
        private boolean mouth(double a,double h){return FLOORS.stream().anyMatch(f->Math.abs(f.y-h)<2&&Math.abs(Math.IEEEremainder(a-f.angle,TAU))<.17);}
        private void supportStair(int x,int z,int underside){
            for(Floor f:FLOORS){
                if(underside<f.y||underside-f.y>3||!f.contains(x,z,0))continue;
                BlockPos below=origin.offset(x,f.y-1,z);
                if(!world.getBlockState(below).isFaceSturdy(world,below,Direction.UP))continue;
                for(int y=f.y;y<=underside;y++)putIfAir(x,y,z,Blocks.STONE_BRICKS);
            }
        }
        private void finishConnections(){
            for(BlockPos pos:connectors){
                BlockState state=world.getBlockState(pos);
                if(state.getBlock() instanceof WallBlock||state.getBlock() instanceof StairBlock)
                    world.setBlock(pos,Block.updateFromNeighbourShapes(state,world,pos),Block.UPDATE_CLIENTS|Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        private void lighting(){
            for(Floor f:FLOORS)for(int x=-36;x<=36;x++)for(int z=-36;z<=36;z++)if(Math.floorMod(x,5)==0&&Math.floorMod(z,5)==0&&f.contains(x,z,-1)){
                if(Math.hypot(x,z)>28)continue;
                BlockPos at=origin.offset(x,f.y-1,z);if(!world.getBlockState(at).isAir())put(x,f.y-1,z,Blocks.SEA_LANTERN);
            }
            for(int x=-40;x<=40;x+=5)for(int z=-40;z<=40;z+=5)if(x*x+z*z<40*40)put(x,-1,z,Blocks.SEA_LANTERN);
            for(Floor f:FLOORS)for(int s:new int[]{-1,1}){
                int u=-6,v=s*(f.index==6?15:10);
                for(int h=0;h<5;h++)localIfAir(f,u,h,v,Blocks.QUARTZ_PILLAR);
                for(int du=0;du<=2;du++)localIfAir(f,u+du,5,v,Blocks.COPPER_BLOCK.waxed().weathered());
                localIfAir(f,u+2,4,v,Blocks.IRON_CHAIN);localIfAir(f,u+2,3,v,Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true));
            }
        }
        private void dressing(){
            for(Floor f:FLOORS)for(int x=-36;x<=36;x++)for(int z=-36;z<=36;z++){
                if(!f.contains(x,z,-1)||hash(x,z,f.index+25)>190)continue;
                Vec3 p=f.local(x,z);if(reserved(f,p.x,p.z,.2)||Math.hypot(x,z)>27)continue;
                if(!world.getBlockState(origin.offset(x,f.y,z)).isAir())continue;
                int choice=hash(x,z,31+f.index)%6;
                decorAt(x,f.y,z,choice==0?"dragon_scale_scatter":choice==1?"dragon_rune_disc":choice==2?"gray_pebbles":"dragon_white_stone_chips",hash(x,z,f.index));
            }
            for(int x=-40;x<=40;x++)for(int z=-40;z<=40;z++){
                double r=Math.hypot(x,z);if(r<36.5||r>40||hash(x,z,77)>240)continue;
                BlockPos at=origin.offset(x,0,z);
                if(!world.getBlockState(at).isAir())continue;
                if(hash(x,z,78)<550)putIfAir(x,0,z,Blocks.MOSS_CARPET);
                else decorAt(x,0,z,hash(x,z,79)<500?"oak_leaf_litter":"ground_branches",hash(x,z,80));
            }
        }
        private void sealExits(){
            for(int x=-44;x<=44;x++)for(int z=-44;z<=44;z++){
                double r=Math.hypot(x,z);if(r>44)continue;
                putIfAir(x,-6,z,Blocks.BARRIER);putIfAir(x,183,z,Blocks.BARRIER);
                if(r>=42)for(int y=-5;y<183;y++)putIfAir(x,y,z,Blocks.BARRIER);
            }
        }
        private void decor(Floor f,int u,int h,int v,String id,int seed){BlockPos p=relative(f.at(u,h,v));decorAt(p.getX(),p.getY(),p.getZ(),id,seed);}
        private void decorAt(int x,int y,int z,String id,int seed){
            BlockPos at=origin.offset(x,y,z);if(!world.getBlockState(at).isAir()||!world.getBlockState(at.below()).isFaceSturdy(world,at.below(),Direction.UP))return;
            BlockState state=decorations.block(id).defaultBlockState();
            if(state.hasProperty(HorizontalDirectionalBlock.FACING))state=state.setValue(HorizontalDirectionalBlock.FACING,Direction.from2DDataValue(Math.floorMod(seed,4)));
            for(var property:state.getProperties())if(property.getName().equals("variant")||property.getName().equals("layout"))state=variant(state,property,seed);
            put(x,y,z,state);
        }
        private <T extends Comparable<T>> BlockState variant(BlockState state,Property<T> property,int seed){var values=List.copyOf(property.getPossibleValues());return state.setValue(property,values.get(Math.floorMod(seed,values.size())));}
        private BlockPos relative(Vec3 p){return BlockPos.containing(p);}
        private boolean supported(Floor f,int u,int v){BlockPos at=origin.offset(relative(f.at(u,0,v))).below();return world.getBlockState(at).isFaceSturdy(world,at,Direction.UP);}
        private void local(Floor f,int u,int h,int v,Block b){local(f,u,h,v,b.defaultBlockState());}
        private void local(Floor f,int u,int h,int v,BlockState state){BlockPos p=relative(f.at(u,h,v));put(p.getX(),p.getY(),p.getZ(),state);}
        private void localIfAir(Floor f,int u,int h,int v,Block b){localIfAir(f,u,h,v,b.defaultBlockState());}
        private void localIfAir(Floor f,int u,int h,int v,BlockState state){BlockPos p=relative(f.at(u,h,v));if(world.getBlockState(origin.offset(p)).isAir())put(p.getX(),p.getY(),p.getZ(),state);}
        private void putIfAir(int x,int y,int z,Block b){putIfAir(x,y,z,b.defaultBlockState());}
        private void putIfAir(int x,int y,int z,BlockState state){if(world.getBlockState(origin.offset(x,y,z)).isAir())put(x,y,z,state);}
        private void put(int x,int y,int z,Block b){put(x,y,z,b.defaultBlockState());}
        private void put(int x,int y,int z,BlockState state){
            if(x<BOUNDS.minX()||x>BOUNDS.maxX()||y<BOUNDS.minY()||y>BOUNDS.maxY()||z<BOUNDS.minZ()||z>BOUNDS.maxZ())throw new IllegalArgumentException("龙人之塔构件超出登记边界");
            world.setBlock(origin.offset(x,y,z),state,Block.UPDATE_CLIENTS|Block.UPDATE_SUPPRESS_DROPS);
            if(state.getBlock() instanceof WallBlock||state.getBlock() instanceof StairBlock)connectors.add(origin.offset(x,y,z));
        }
    }
    private DragonTower(){}
}
