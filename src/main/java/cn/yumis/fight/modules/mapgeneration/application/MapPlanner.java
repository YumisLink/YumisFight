package cn.yumis.fight.modules.mapgeneration.application;

import cn.yumis.fight.modules.mapgeneration.api.MapBlueprint;
import net.minecraft.core.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import java.util.*;

/** Resolves a complete height plan before modifying any blocks. Half-block units are used throughout. */
public final class MapPlanner {
    private record Surface(int top,int bearing,boolean water,String biome) {}
    private record Loaded(MapBlueprint.Facility definition,StructureTemplate template,MapFoundations.Footprint footprint) {}
    private record Node(int index,int value) {}
    private MapPlanner() {}

    public static GenerationPlan plan(ServerLevel level,Identifier id,BlockPos requested) throws Exception {
        MapBlueprint blueprint=MapBlueprint.load(level,id);
        for(int i=0;i<blueprint.settings.roadWeights.length;i++) {
            MapPlacer.block(blueprint.settings.roadBlocks[i]);
            if(!(MapPlacer.block(blueprint.settings.roadSlabs[i]).getBlock() instanceof net.minecraft.world.level.block.SlabBlock)||!(MapPlacer.block(blueprint.settings.roadStairs[i]).getBlock() instanceof net.minecraft.world.level.block.StairBlock))throw new IllegalArgumentException("道路坡段材料必须是半砖和楼梯");
        }
        for(var ground:blueprint.grounds) {if(!ground.surface.equals("native")&&!ground.surface.equals("biome"))MapPlacer.block(ground.surface);for(var plant:ground.plants)MapPlacer.block(plant.block);}
        if(GenerationStore.overlaps(level,requested.getX(),requested.getZ(),requested.getX(),requested.getZ()))throw new IllegalArgumentException("指定点已属于完成的地图，保留现有改动，不重复放置");
        Surface initial=surface(level,requested.getX(),requested.getZ());
        boolean floating=requested.getY()-initial.top>blueprint.settings.floatingThreshold;
        boolean ocean=initial.water&&initial.biome.contains("ocean");
        GenerationPlan.Mode mode=floating?GenerationPlan.Mode.FLOATING:ocean?GenerationPlan.Mode.OCEAN:GenerationPlan.Mode.TERRAIN;
        List<Loaded> templates=new ArrayList<>();
        for(var f:blueprint.facilities) {
            if(mode==GenerationPlan.Mode.FLOATING&&f.tags.contains("wall")||mode==GenerationPlan.Mode.OCEAN&&f.skipInOcean)continue;
            var template=level.getStructureManager().get(Identifier.parse(f.template)).orElseThrow(()->new IllegalArgumentException("找不到结构: "+f.template));
            if(template.getSize().getY()==0)throw new IllegalArgumentException("空结构: "+f.template);
            templates.add(new Loaded(f,template,f.fittedFoundation()?MapFoundations.footprint(level,template,f.referenceY):null));
        }
        List<Loaded> ordered=new ArrayList<>();Set<String> available=new HashSet<>();
        while(!templates.isEmpty()) {
            int before=templates.size();
            for(var iterator=templates.iterator();iterator.hasNext();) {var t=iterator.next();if(t.definition.parent.isEmpty()||available.contains(t.definition.parent)){ordered.add(t);available.add(t.definition.id);iterator.remove();}}
            if(before==templates.size())throw new IllegalArgumentException("设施 parent 不存在、被当前模式跳过或存在循环");
        }
        templates=ordered;
        List<int[]> offsets=new ArrayList<>();offsets.add(new int[]{0,0});
        for(int radius=16;radius<=blueprint.settings.searchRadius;radius*=2)
            for(int[] direction:new int[][]{{1,0},{0,1},{-1,0},{0,-1},{1,1},{-1,1},{-1,-1},{1,-1}})
                if(Math.hypot(direction[0]*radius,direction[1]*radius)<=blueprint.settings.searchRadius)offsets.add(new int[]{direction[0]*radius,direction[1]*radius});
        String last="没有符合环境要求的位置";int attempts=0;
        for(int[] offset:offsets) {
            int x=requested.getX()+offset[0],z=requested.getZ()+offset[1];Surface ground=surface(level,x,z);
            if(!ground.biome.equals(initial.biome)||ocean!=(ground.water&&ground.biome.contains("ocean")))continue;
            List<Integer> heights=new ArrayList<>();heights.add(floating?requested.getY():ground.top);
            if(floating)for(int delta=1;delta<=blueprint.settings.floatingSearchHeight;delta++) {heights.add(requested.getY()-delta);heights.add(requested.getY()+delta);}
            for(int y:heights) {
                if(floating&&y-ground.top<=blueprint.settings.floatingThreshold)continue;
                try {
                    GenerationPlan result=at(level,id,blueprint,new BlockPos(x,y,z),mode,templates);
                    result.metrics.put("candidate_attempts",++attempts);result.metrics.put("relocation_x",offset[0]);result.metrics.put("relocation_z",offset[1]);return result;
                } catch(IllegalArgumentException e) {if(e.getMessage().startsWith("道路与平整设施")||mode!=GenerationPlan.Mode.TERRAIN&&e.getMessage().startsWith("设施重叠"))throw e;last=e.getMessage();attempts++;}
            }
        }
        throw new IllegalArgumentException("附近没有可行落点，未放置任何建筑。最后原因: "+last);
    }

    private static GenerationPlan at(ServerLevel level,Identifier id,MapBlueprint blueprint,BlockPos origin,GenerationPlan.Mode mode,List<Loaded> templates) {
        GenerationPlan p=new GenerationPlan(level,id,blueprint,origin,mode);
        if(GenerationStore.overlaps(level,p.minX,p.minZ,p.maxX,p.maxZ))throw new IllegalArgumentException("生成范围与已完成地图重叠");
        int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;
        for(int z=p.minZ;z<=p.maxZ;z++)for(int x=p.minX;x<=p.maxX;x++) {
            Surface s=surface(level,x,z);int i=p.index(x,z);
            p.terrain[i]=s.top;p.bearing[i]=s.bearing;p.water[i]=s.water;p.biomes[i]=s.biome;min=Math.min(min,s.top);max=Math.max(max,s.top);
        }
        p.metrics.put("terrain_relief",max-min);
        if(mode==GenerationPlan.Mode.OCEAN)for(int i=0;i<p.terrain.length;i++)
            if(!p.water[i]||p.terrain[i]!=origin.getY())throw new IllegalArgumentException("海上区域包含陆地或不同水位，需要换一个完整海面落点");
        Set<Integer> blocked=new HashSet<>();
        for(Loaded loaded:templates)if(!loaded.definition.tags.contains("wall"))for(int[] b:loaded.definition.blocked) {
            int x=origin.getX()+loaded.definition.position[0]+b[0],z=origin.getZ()+loaded.definition.position[1]+b[1];
            if(p.inside(x,z))blocked.add(p.index(x,z));
        }
        Map<Integer,Integer> desired=new HashMap<>();
        for(var r:blueprint.roads)for(int segment=1;segment<r.points.length;segment++) {
            int ax=origin.getX()+r.points[segment-1][0],az=origin.getZ()+r.points[segment-1][1],bx=origin.getX()+r.points[segment][0],bz=origin.getZ()+r.points[segment][1];
            if(!p.inside(ax,az)||!p.inside(bx,bz))throw new IllegalArgumentException("道路控制点越过蓝图范围: "+r.id);
            double dx=bx-ax,dz=bz-az,len=dx*dx+dz*dz;
            int margin=(int)Math.ceil(r.width/2);
            for(int z=Math.max(p.minZ,Math.min(az,bz)-margin);z<=Math.min(p.maxZ,Math.max(az,bz)+margin);z++)
                for(int x=Math.max(p.minX,Math.min(ax,bx)-margin);x<=Math.min(p.maxX,Math.max(ax,bx)+margin);x++) {
                    double t=Math.max(0,Math.min(1,((x-ax)*dx+(z-az)*dz)/(len==0?1:len)));
                    if(Math.hypot(x-ax-dx*t,z-az-dz*t)>r.width/2)continue;
                    int i=p.index(x,z);if(blocked.contains(i))continue;
                    int natural=p.terrain[i];double chord=p.ground(ax,az)*(1-t)+p.ground(bx,bz)*t;
                    int target=(int)Math.round(chord-natural>=3?chord:natural);
                    if(p.water[i]&&mode==GenerationPlan.Mode.TERRAIN)target=Math.max(target,natural+1);
                    desired.merge(i,target*2,Math::max);p.roadCells.add(i);p.roadIds.putIfAbsent(i,r.id);
                }
        }
        if(p.roadCells.isEmpty())throw new IllegalArgumentException("道路没有可用栅格");
        int[] parent=new int[p.terrain.length];for(int i=0;i<parent.length;i++)parent[i]=i;
        Set<Integer> flattened=new HashSet<>();Map<String,Integer> entranceNodes=new HashMap<>();
        for(Loaded loaded:templates) {
            var f=loaded.definition;int ex=origin.getX()+f.entrance[0],ez=origin.getZ()+f.entrance[1];
            if(!p.inside(ex,ez))throw new IllegalArgumentException("设施入口越界: "+f.id);
            int root=p.nearestRoad(ex,ez);entranceNodes.put(f.id,root);
            if(!f.flatten)continue;
            if(!f.parent.isEmpty())union(parent,root,entranceNodes.get(f.parent));
            var nearest=nearestSegment(blueprint,origin,p.x(root),p.z(root));
            int rad=blueprint.settings.entranceFlatRadius;
            double ux=nearest[0],uz=nearest[1],halfWidth=nearest[2];
            List<Integer> zone=new ArrayList<>();
            for(int cell:p.roadCells) {
                double along=(p.x(cell)-p.x(root))*ux+(p.z(cell)-p.z(root))*uz;
                double across=Math.abs(-(p.x(cell)-p.x(root))*uz+(p.z(cell)-p.z(root))*ux);
                int fx=origin.getX()+f.position[0],fz=origin.getZ()+f.position[1];
                boolean inFootprint=f.platform&&(loaded.footprint==null?p.x(cell)>=fx&&p.x(cell)<fx+loaded.template.getSize().getX()&&p.z(cell)>=fz&&p.z(cell)<fz+loaded.template.getSize().getZ():loaded.footprint.contains(p.x(cell)-fx,p.z(cell)-fz));
                if(Math.abs(along)<=rad+.1&&across<=halfWidth+.25||inFootprint) {union(parent,root,cell);flattened.add(cell);zone.add(cell);}
            }
            if(!zone.isEmpty())p.flatZones.add(zone.stream().mapToInt(Integer::intValue).toArray());
        }
        // Reserve a landing-height road cell OUTSIDE each flat zone. Its stair may have a lower half tread,
        // while the seven-cell entrance and the facility floor remain fully level.
        Map<Integer,Integer> landingEdges=new LinkedHashMap<>();
        for(int[] zone:p.flatZones)for(int inside:zone)for(int outside:neighbors(p,inside)) {
            if(!p.roadCells.contains(outside)||flattened.contains(outside))continue;
            union(parent,inside,outside);landingEdges.putIfAbsent(outside,inside);
        }
        if(mode!=GenerationPlan.Mode.TERRAIN)for(int i:p.roadCells)p.roadHeights.put(i,origin.getY()*2);
        else solve(p,desired,parent,templates,entranceNodes,flattened);
        // Reapply up to two rows of the actual slope after templates have placed their floors.
        for(var edge:landingEdges.entrySet())for(int slope:neighbors(p,edge.getKey())) {
            if(!p.roadCells.contains(slope)||flattened.contains(slope)||p.roadHeights.get(slope).equals(p.roadHeights.get(edge.getKey())))continue;
            p.platformApproaches.putIfAbsent(edge.getKey(),edge.getValue());
            p.platformApproaches.putIfAbsent(slope,edge.getKey());
        }
        p.metrics.put("platform_transition_cells",p.platformApproaches.size());
        int[] connected=parent.clone();for(int i:p.roadCells)for(int j:neighbors(p,i))if(p.roadCells.contains(j))union(connected,i,j);
        int components=(int)p.roadCells.stream().map(i->find(connected,i)).distinct().count();p.metrics.put("network_components",components);
        if(components!=1)throw new IllegalArgumentException("道路与平整设施组成的网络不连通，需要修正蓝图接点");
        Map<String,GenerationPlan.Facility> placed=new HashMap<>();Map<String,StructureTemplate> themed=new HashMap<>();
        int minFloor=Integer.MAX_VALUE,maxFloor=Integer.MIN_VALUE;
        for(Loaded loaded:templates) {
            var f=loaded.definition;int x=origin.getX()+f.position[0],z=origin.getZ()+f.position[1],ex=origin.getX()+f.entrance[0],ez=origin.getZ()+f.entrance[1];
            int i=p.index(ex,ez);int height;
            if(mode!=GenerationPlan.Mode.TERRAIN)height=origin.getY();
            else if(!f.parent.isEmpty()&&placed.containsKey(f.parent))height=(int)placed.get(f.parent).surface();
            else if(f.anchor.equals("surface"))height=p.terrain[i];
            else {
                int reference=p.roadHeights.get(entranceNodes.get(f.id));
                if(!f.flatten)for(int cell:p.roadCells)if(p.x(cell)>=x-1&&p.x(cell)<=x+loaded.template.getSize().getX()&&p.z(cell)>=z-1&&p.z(cell)<=z+loaded.template.getSize().getZ())reference=Math.max(reference,p.roadHeights.get(cell));
                height=Math.floorDiv(reference+1,2);
            }
            int bottom=height-f.referenceY,top=bottom+loaded.template.getSize().getY()-1;
            if(!p.inside(x,z)||!p.inside(x+loaded.template.getSize().getX()-1,z+loaded.template.getSize().getZ()-1))throw new IllegalArgumentException("设施占地越过蓝图范围: "+f.id);
            if(bottom<level.getMinY()+1||top>level.getMaxY()-1)throw new IllegalArgumentException("设施超出世界高度: "+f.id);
            if(mode==GenerationPlan.Mode.FLOATING&&f.island) {
                int depth=blueprint.settings.islandDepth;
                int margin=blueprint.settings.islandMargin;
                for(int zz=z-margin;zz<z+loaded.template.getSize().getZ()+margin;zz++)for(int xx=x-margin;xx<x+loaded.template.getSize().getX()+margin;xx++)
                    if(p.inside(xx,zz)&&p.ground(xx,zz)+4>=height-depth)throw new IllegalArgumentException("浮岛底与原地形相交: "+f.id);
            }
            String biome=p.biomes[i];var theme=BiomeMaterials.theme(biome);
            String cache=f.template+"/"+theme.main()+"/"+theme.accent()+"/"+theme.leaves();
            var template=themed.computeIfAbsent(cache,k->BiomeMaterials.adapt(level,loaded.template,theme,blueprint.settings));
            var facility=new GenerationPlan.Facility(f,template,new BlockPos(x,bottom,z),height,biome,theme,loaded.footprint);
            p.facilities.add(facility);placed.put(f.id,facility);
            if(f.flatten){minFloor=Math.min(minFloor,height);maxFloor=Math.max(maxFloor,height);}
        }
        int minRoad=p.roadHeights.values().stream().mapToInt(Integer::intValue).min().orElse(0),maxRoad=p.roadHeights.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        for(var f:p.facilities)if(f.definition().keepClear)for(int zz=f.position().getZ();zz<f.position().getZ()+f.template().getSize().getZ();zz++)for(int xx=f.position().getX();xx<f.position().getX()+f.template().getSize().getX();xx++)if(p.inside(xx,zz)&&f.containsGround(xx,zz))p.keepClear.add(p.index(xx,zz));
        for(var ground:blueprint.grounds)if(!ground.island.isEmpty()&&mode==GenerationPlan.Mode.FLOATING) {
            var owner=placed.get(ground.island);
            if(owner==null||!owner.definition().island)throw new IllegalArgumentException("地面浮岛关联不存在: "+ground.id);
            for(int zz=origin.getZ()+ground.bounds[1];zz<=origin.getZ()+ground.bounds[3];zz++)for(int xx=origin.getX()+ground.bounds[0];xx<=origin.getX()+ground.bounds[2];xx++)
                if(!p.inside(xx,zz)||p.ground(xx,zz)+4>=owner.surface()-blueprint.settings.islandDepth)throw new IllegalArgumentException("关联的地面岛体越界或接近原地形: "+ground.id);
        }
        p.metrics.put("road_relief_half",maxRoad-minRoad);p.metrics.put("facility_relief",maxFloor-minFloor);
        p.metrics.put("loot_slots",p.facilities.stream().mapToInt(f->f.definition().loot.size()).sum());
        p.metrics.put("islands",mode==GenerationPlan.Mode.FLOATING?(int)p.facilities.stream().filter(f->f.definition().island).count():0);
        MapFoundations.plan(p);
        MapPlacer.validate(p);
        return p;
    }

    private static void solve(GenerationPlan p,Map<Integer,Integer> desired,int[] parent,List<Loaded> templates,Map<String,Integer> entrances,Set<Integer> flattened) {
        Map<Integer,List<Integer>> members=new HashMap<>();
        for(int i:p.roadCells)members.computeIfAbsent(find(parent,i),unused->new ArrayList<>()).add(i);
        Map<Integer,Set<Integer>> edges=new HashMap<>();
        for(int i:p.roadCells) {
            int root=find(parent,i);edges.computeIfAbsent(root,unused->new HashSet<>());
            for(int j:neighbors(p,i))if(p.roadCells.contains(j)&&root!=find(parent,j))edges.get(root).add(find(parent,j));
        }
        int size=parent.length,limit=(int)Math.floor(p.blueprint.settings.maxRise*2);
        int[] pref=new int[size],low=new int[size],high=new int[size],lb=new int[size],ub=new int[size];
        Map<Integer,Integer> terrainFloors=new HashMap<>();
        for(var e:members.entrySet()) {
            var sorted=e.getValue().stream().mapToInt(desired::get).sorted().toArray();pref[e.getKey()]=sorted[sorted.length/2];
            lb[e.getKey()]=(p.level.getMinY()+5)*2;ub[e.getKey()]=(p.level.getMaxY()-4)*2;
        }
        for(Loaded l:templates)if(l.definition.flatten) {
            int root=find(parent,entrances.get(l.definition.id));
            ub[root]=Math.min(ub[root],(p.level.getMaxY()-l.template.getSize().getY()+l.definition.referenceY-1)*2);
            lb[root]=Math.max(lb[root],(p.level.getMinY()+l.definition.referenceY+1)*2);
            var f=l.definition;int x=p.origin.getX()+f.position[0],z=p.origin.getZ()+f.position[1],minimum=Integer.MAX_VALUE;
            if(f.platform) {
                for(int zz=z;zz<z+l.template.getSize().getZ();zz++)for(int xx=x;xx<x+l.template.getSize().getX();xx++)if(p.inside(xx,zz)&&(l.footprint==null||l.footprint.contains(xx-x,zz-z)))minimum=Math.min(minimum,p.ground(xx,zz));
                int natural=p.ground(p.origin.getX()+f.entrance[0],p.origin.getZ()+f.entrance[1]);
                if(minimum<natural-2)terrainFloors.merge(root,(natural+1)*2,Math::max);
            }
        }
        int start=find(parent,p.nearestRoad(p.origin.getX(),p.origin.getZ()));lb[start]=ub[start]=p.origin.getY()*2;
        propagate(lb,edges,limit,true);propagate(ub,edges,limit,false);
        for(int root:members.keySet())if(lb[root]>ub[root]) {
            var owners=entrances.entrySet().stream().filter(e->find(parent,e.getValue())==root).map(Map.Entry::getKey).sorted().toList();
            throw new IllegalArgumentException("起点高度与设施净高／道路坡度冲突，起点="+p.origin.toShortString()+"，区域="+p.x(root)+","+p.z(root)+"，设施="+owners+"，允许高度="+lb[root]/2.0+".."+ub[root]/2.0);
        }
        // Terrain elevation is a preference: rigid floors follow the reachable road height, and earthworks
        // adapt the original hillside. It must not make a valid spawn anchor or its level approaches infeasible.
        int adjusted=0;
        for(var e:terrainFloors.entrySet()) {
            int reachable=Math.floorDiv(Math.min(e.getValue(),ub[e.getKey()]),2)*2;
            lb[e.getKey()]=Math.max(lb[e.getKey()],reachable);
            if(reachable<e.getValue())adjusted++;
        }
        propagate(lb,edges,limit,true);
        p.metrics.put("terrain_grade_adjustments",adjusted);
        System.arraycopy(pref,0,low,0,size);System.arraycopy(pref,0,high,0,size);
        propagate(low,edges,limit,true);propagate(high,edges,limit,false);
        List<Integer> rigid=new ArrayList<>();rigid.add(start);
        for(Loaded l:templates)if(l.definition.flatten) {int r=find(parent,entrances.get(l.definition.id));if(!rigid.contains(r))rigid.add(r);}
        for(int root:rigid) {
            int value=root==start?p.origin.getY()*2:(int)Math.ceil((low[root]+high[root])/4.0)*2;
            value=Math.max(lb[root],Math.min(ub[root],value));value=Math.floorDiv(value+1,2)*2;
            if(value<lb[root]||value>ub[root])throw new IllegalArgumentException("设施没有整数高度落点");
            lb[root]=ub[root]=value;propagate(lb,edges,limit,true);propagate(ub,edges,limit,false);
        }
        for(var e:members.entrySet()) {
            int root=e.getKey();int value=(int)Math.ceil((low[root]+high[root])/4.0)*2;
            value=Math.max(lb[root],Math.min(ub[root],value));
            for(int i:e.getValue())p.roadHeights.put(i,value);
        }
        // Half slabs join the level approach to a full-height stair rise without changing entrance plateaus.
        Map<Integer,Integer> initial=new HashMap<>(p.roadHeights);
        for(int i:p.roadCells)if(!flattened.contains(i)) {
            int h=initial.get(i);boolean higher=false,lower=false;
            for(int j:neighbors(p,i))if(initial.containsKey(j)) {int n=initial.get(j);higher|=n==h+2;lower|=n<h;}
            if(higher&&!lower)p.roadHeights.put(i,h+1);
        }
        for(int i:p.roadCells)for(int j:neighbors(p,i))if(p.roadCells.contains(j)&&Math.abs(p.roadHeights.get(i)-p.roadHeights.get(j))>2)throw new IllegalArgumentException("道路离散坡度超限");
        for(int[] zone:p.flatZones)for(int i:zone)if(!p.roadHeights.get(i).equals(p.roadHeights.get(zone[0])))throw new IllegalArgumentException("入口平段未保持同高");
    }
    private static void propagate(int[] values,Map<Integer,Set<Integer>> edges,int limit,boolean maximum) {
        PriorityQueue<Node> queue=new PriorityQueue<>(maximum?Comparator.comparingInt(Node::value).reversed():Comparator.comparingInt(Node::value));
        for(int i:edges.keySet())queue.add(new Node(i,values[i]));
        while(!queue.isEmpty()) {
            Node n=queue.remove();if(values[n.index]!=n.value)continue;
            for(int j:edges.get(n.index)) {
                int candidate=n.value+(maximum?-limit:limit);
                if(maximum?candidate>values[j]:candidate<values[j]) {values[j]=candidate;queue.add(new Node(j,candidate));}
            }
        }
    }
    static int[] neighbors(GenerationPlan p,int i) {
        int x=i%p.width,z=i/p.width;int[] result=new int[4];int count=0;
        if(x>0)result[count++]=i-1;if(x+1<p.width)result[count++]=i+1;if(z>0)result[count++]=i-p.width;if(z+1<p.depth)result[count++]=i+p.width;
        return Arrays.copyOf(result,count);
    }
    private static int find(int[] parent,int i) {while(parent[i]!=i){parent[i]=parent[parent[i]];i=parent[i];}return i;}
    private static void union(int[] parent,int a,int b) {int x=find(parent,a),y=find(parent,b);if(x!=y)parent[y]=x;}
    private static double[] nearestSegment(MapBlueprint b,BlockPos origin,int x,int z) {
        double best=Double.MAX_VALUE;double[] result={0,1,2.5};
        for(var r:b.roads)for(int i=1;i<r.points.length;i++) {
            double ax=origin.getX()+r.points[i-1][0],az=origin.getZ()+r.points[i-1][1],dx=r.points[i][0]-r.points[i-1][0],dz=r.points[i][1]-r.points[i-1][1],len=Math.hypot(dx,dz);
            if(len==0)continue;double t=Math.max(0,Math.min(1,((x-ax)*dx+(z-az)*dz)/(len*len))),distance=Math.hypot(x-ax-dx*t,z-az-dz*t);
            if(distance<best){best=distance;result=new double[]{dx/len,dz/len,r.width/2};}
        }
        return result;
    }
    private static Surface surface(ServerLevel level,int x,int z) {
        level.getChunk(x>>4,z>>4);
        int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z),min=level.getMinY();
        BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
        boolean water=false;
        while(y>min+1) {
            var state=level.getBlockState(pos.set(x,y-1,z));
            if(state.getFluidState().is(FluidTags.WATER)) {water=true;break;}
            if(naturalGround(state)&&state.isCollisionShapeFullBlock(level,pos))break;
            y--;
        }
        int bearing=y;
        if(water)while(bearing>min+1) {
            var state=level.getBlockState(pos.set(x,bearing-1,z));
            if(state.getFluidState().isEmpty()&&state.isCollisionShapeFullBlock(level,pos)&&!state.is(BlockTags.LOGS)&&!state.is(BlockTags.LEAVES))break;
            bearing--;
        }
        String biome=level.getBiome(new BlockPos(x,y-1,z)).unwrapKey().orElseThrow().identifier().toString();
        return new Surface(y,bearing,water,biome);
    }
    private static boolean naturalGround(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(BlockTags.DIRT)||state.is(Blocks.GRASS_BLOCK)||state.is(Blocks.PODZOL)||state.is(Blocks.MUDDY_MANGROVE_ROOTS)||state.is(BlockTags.BASE_STONE_OVERWORLD)||state.is(BlockTags.BASE_STONE_NETHER)||state.is(BlockTags.SAND)||state.is(BlockTags.TERRACOTTA)
            ||state.is(Blocks.GRAVEL)||state.is(Blocks.CLAY)||state.is(Blocks.MUD)||state.is(Blocks.PACKED_MUD)||state.is(Blocks.BEDROCK)||state.is(Blocks.SNOW_BLOCK)||state.is(Blocks.SANDSTONE)||state.is(Blocks.RED_SANDSTONE)||state.is(Blocks.MYCELIUM)||state.is(Blocks.MOSS_BLOCK)||state.is(Blocks.PALE_MOSS_BLOCK)
            ||state.is(Blocks.ICE)||state.is(Blocks.PACKED_ICE)||state.is(Blocks.BLUE_ICE)||state.is(Blocks.CALCITE)||state.is(Blocks.DRIPSTONE_BLOCK)||state.is(Blocks.END_STONE)
            ||net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().endsWith("_ore");
    }
}
