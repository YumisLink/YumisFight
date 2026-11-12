package cn.yumis.fight.modules.mapgeneration.application;

import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import java.util.*;

/** Applies a fully planned blueprint, with the same placement path for commands and world startup. */
public final class MapPlacer {
    private final GenerationPlan p;
    private final ServerLevel level;
    private int writes,supports,bridgeCells;
    private final Set<BlockPos> shapeUpdates=new HashSet<>();
    private final Set<Integer> facilityGround=new HashSet<>();
    private final Set<Integer> islandGround=new HashSet<>(),guardedIslandGround=new HashSet<>();
    private final RandomSource random;
    private MapPlacer(GenerationPlan plan) {p=plan;level=p.level;random=RandomSource.create(level.getSeed()^p.origin.asLong()^p.id.hashCode());}
    public static void place(GenerationPlan p) {
        var writer=new MapPlacer(p);writer.validateStructureCollisions();
        for(int x=p.minX>>4;x<=p.maxX>>4;x++)for(int z=p.minZ>>4;z<=p.maxZ>>4;z++)if(p.level.setChunkForced(x,z,true))p.acquiredChunks.add(new ChunkPos(x,z));
        try {writer.prepare();writer.islands();writer.roads();writer.grounds();writer.facilities();writer.platformApproaches();writer.clearRoadFoliage();writer.plants();writer.externalLandings();writer.platformGuards();writer.clearActivityAreas();writer.finishShapes();writer.safeAnimals();writer.npcs();}
        catch(RuntimeException e){releaseChunks(p);throw e;}
        p.metrics.put("block_writes",writer.writes);p.metrics.put("support_columns",writer.supports);p.metrics.put("bridge_cells",writer.bridgeCells);
        levelLight(p.level);
    }
    static void releaseChunks(GenerationPlan p) {for(var c:p.acquiredChunks)p.level.setChunkForced(c.x(),c.z(),false);p.acquiredChunks.clear();}
    private void npcs(){
        int spawned=0;
        for(var facility:p.facilities)for(var group:facility.definition().npcGroups){
            String identity=level.dimension().identifier()+"|"+p.id+"|"+p.origin.asLong()+"|"+facility.definition().id+"|"+group.id;
            long seed=level.getSeed()^p.origin.asLong()^p.id.hashCode()^((long)facility.definition().id.hashCode()<<32)^group.id.hashCode()^0x4e50435f5354414cL;
            var anchors=group.anchors.stream().map(a->new cn.yumis.fight.modules.npc.api.NpcSpawns.Anchor(a.id,new net.minecraft.world.phys.Vec3(facility.position().getX()+a.pos[0],facility.position().getY()+a.pos[1],facility.position().getZ()+a.pos[2]),a.yaw)).toList();
            spawned+=cn.yumis.fight.modules.npc.api.NpcSpawns.group(level,identity,anchors,group.definitions,seed);
        }
        p.metrics.put("npc_spawns",spawned);
    }
    static void validate(GenerationPlan p) {new MapPlacer(p).validateStructureCollisions();}
    static void levelLight(ServerLevel level) {level.getChunkSource().getLightEngine().tryScheduleUpdate();}
    private void put(int x,int y,int z,BlockState state) {
        if(!level.isInsideBuildHeight(y))throw new IllegalStateException("放置高度越界");
        if(level.setBlock(new BlockPos(x,y,z),state,Block.UPDATE_CLIENTS|Block.UPDATE_SUPPRESS_DROPS))writes++;
        if(state.getBlock() instanceof FenceBlock||state.getBlock() instanceof WallBlock||state.getBlock() instanceof StairBlock)shapeUpdates.add(new BlockPos(x,y,z));
    }
    private void finishShapes() {for(BlockPos pos:shapeUpdates){var state=level.getBlockState(pos);level.setBlock(pos,Block.updateFromNeighbourShapes(state,level,pos),Block.UPDATE_CLIENTS|Block.UPDATE_SUPPRESS_DROPS);}}
    private void validateStructureCollisions() {
        record Cell(String owner,String material) {}
        Map<Long,Cell> occupied=new HashMap<>();
        for(var f:p.facilities) {
            var tag=f.template().save(new CompoundTag());var palette=tag.getListOrEmpty("palette");
            for(Tag item:tag.getListOrEmpty("blocks")) {
                CompoundTag block=(CompoundTag)item;var pos=block.getListOrEmpty("pos");var entry=palette.getCompoundOrEmpty(block.getIntOr("state",0));
                String name=entry.getStringOr("Name","minecraft:air");if(name.equals("minecraft:air"))continue;
                int x=f.position().getX()+pos.getIntOr(0,0),y=f.position().getY()+pos.getIntOr(1,0),z=f.position().getZ()+pos.getIntOr(2,0);
                long key=BlockPos.asLong(x,y,z);Cell old=occupied.putIfAbsent(key,new Cell(f.definition().id,name));
                if(old!=null&&!old.owner.equals(f.definition().id)&&!old.material.equals(name)&&!old.material.endsWith("_leaves")&&!name.endsWith("_leaves")&&!old.material.equals("minecraft:vine")&&!name.equals("minecraft:vine"))throw new IllegalArgumentException("设施重叠: "+old.owner+" / "+f.definition().id+" @ "+x+","+y+","+z+" "+old.material+" / "+name);
                Integer road=p.inside(x,z)?p.roadHeights.get(p.index(x,z)):null;
                if(road!=null&&!f.definition().tags.contains("wall")&&!name.endsWith("_leaves")&&!name.equals("minecraft:lantern")&&!name.equals("minecraft:iron_chain")&&!name.equals("minecraft:vine")) {
                    int feet=Math.floorDiv(road+1,2);
                    if(y>=feet-2&&y<feet+2) {
                        var state=NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(Registries.BLOCK),entry);
                        if(!state.canBeReplaced()) {
                            var body=new net.minecraft.world.phys.AABB(x+.2,feet,z+.2,x+.8,feet+2,z+.8);
                            for(var box:state.getCollisionShape(level,new BlockPos(x,y,z)).toAabbs())if(box.move(x,y,z).intersects(body))throw new IllegalArgumentException("道路净空冲突: "+f.definition().id+" @ "+x+","+y+","+z+" "+name);
                        }
                    }
                }
            }
        }
        for(var e:p.foundationBlocks.entrySet()) {
            BlockPos pos=e.getKey();Cell old=occupied.get(pos.asLong());
            String material=BuiltInRegistries.BLOCK.getKey(e.getValue().getBlock()).toString();
            if(old!=null&&!old.material.equals(material))throw new IllegalArgumentException("地基与设施重叠: "+old.owner+" @ "+pos.toShortString());
            Integer road=p.roadHeights.get(p.index(pos.getX(),pos.getZ()));
            if(road!=null&&pos.getY()>=Math.floorDiv(road-1,2))throw new IllegalArgumentException("地基占用道路踏面: "+pos.toShortString());
        }
    }
    private void prepare() {
        if(p.mode==GenerationPlan.Mode.TERRAIN) {
            for(int i=0;i<p.terrain.length;i++) {
                int x=p.x(i),z=p.z(i),top=level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z);
                if(p.blueprint.grounds.stream().noneMatch(g->g.clearVegetation&&inGround(g,x,z)))continue;
                for(int y=p.terrain[i];y<top;y++) {
                    var pos=new BlockPos(x,y,z);var state=level.getBlockState(pos);
                    if(state.is(BlockTags.LOGS)||state.is(BlockTags.LEAVES)||state.canBeReplaced()&&!state.getFluidState().isSource())put(x,y,z,Blocks.AIR.defaultBlockState());
                }
            }
        }
        for(var f:p.facilities) {
            int surface=(int)f.surface(),w=f.template().getSize().getX(),d=f.template().getSize().getZ(),top=f.position().getY()+f.template().getSize().getY();
            int x0=f.position().getX(),z0=f.position().getZ();
            if(f.footprint()!=null) {
                for(var e:f.footprint().columns().entrySet()) {
                    BlockPos local=BlockPos.of(e.getKey());int x=x0+local.getX(),z=z0+local.getZ();
                    boolean ground=f.containsGround(x,z);int bottom=f.position().getY()+e.getValue().bottom(),ceiling=f.position().getY()+e.getValue().top();
                    if(ground){int i=p.index(x,z);facilityGround.add(i);bottom=Math.min(surface,bottom);ceiling=Math.max(Math.max(top,surface+3),p.terrain[i]+2);}
                    for(int y=bottom;y<ceiling;y++)if(!level.getBlockState(new BlockPos(x,y,z)).isAir())put(x,y,z,Blocks.AIR.defaultBlockState());
                }
                continue;
            }
            for(int z=z0;z<z0+d;z++)for(int x=x0;x<x0+w;x++) {
                if(f.definition().platform&&p.inside(x,z))facilityGround.add(p.index(x,z));
                for(int y=surface;y<top;y++)if(!level.getBlockState(new BlockPos(x,y,z)).isAir())put(x,y,z,Blocks.AIR.defaultBlockState());
            }
            if(p.mode==GenerationPlan.Mode.TERRAIN&&f.definition().flatten) {
                for(int z=z0-3;z<z0+d+3;z++)for(int x=x0-3;x<x0+w+3;x++)if(p.inside(x,z)) {
                    int distance=Math.max(Math.max(x0-x,x-(x0+w-1)),Math.max(z0-z,z-(z0+d-1)));distance=Math.max(0,distance);
                    int target=surface+distance,old=p.ground(x,z);
                    if(old>target) {
                        for(int y=target;y<=old;y++)put(x,y,z,Blocks.AIR.defaultBlockState());
                        put(x,target-1,z,surfaceMaterial(p.biomes[p.index(x,z)]));
                    }
                }
            }
        }
        for(int i:p.roadCells) {
            int x=p.x(i),z=p.z(i),height=Math.floorDiv(p.roadHeights.get(i)+1,2);
            for(int y=height;y<=Math.max(height+2,p.terrain[i]+1);y++)put(x,y,z,Blocks.AIR.defaultBlockState());
        }
    }
    static BlockState surfaceMaterial(String biome) {
        if(biome.contains("desert")||biome.contains("beach")||biome.contains("warm_ocean"))return Blocks.SAND.defaultBlockState();
        if(biome.contains("badlands"))return Blocks.RED_SAND.defaultBlockState();
        if(biome.contains("mushroom"))return Blocks.MYCELIUM.defaultBlockState();
        if(biome.contains("old_growth"))return Blocks.PODZOL.defaultBlockState();
        return Blocks.GRASS_BLOCK.defaultBlockState();
    }
    private void islands() {
        if(p.mode!=GenerationPlan.Mode.FLOATING)return;
        Map<Integer,GenerationPlan.Facility> owners=new HashMap<>();Map<Integer,Double> scores=new HashMap<>();
        for(var f:p.facilities)if(f.definition().island) {
            int x0=f.position().getX(),z0=f.position().getZ(),w=f.template().getSize().getX(),d=f.template().getSize().getZ(),m=p.blueprint.settings.islandMargin;
            double cx=x0+(w-1)/2.0,cz=z0+(d-1)/2.0;
            for(int z=z0-m;z<z0+d+m;z++)for(int x=x0-m;x<x0+w+m;x++)if(p.inside(x,z)) {
                double dx=Math.max(0,Math.abs(x-cx)-(w-1)/2.0),dz=Math.max(0,Math.abs(z-cz)-(d-1)/2.0);
                double distance=f.footprint()==null?Math.hypot(dx,dz):f.footprint().distance(x-x0,z-z0,m);
                if(distance>m-.6+(hash(x,z,47)%100)/100.0)continue;
                int i=p.index(x,z);double score=distance*distance+.01*(Math.abs(x-cx)+Math.abs(z-cz));
                if(score<scores.getOrDefault(i,Double.MAX_VALUE)){owners.put(i,f);scores.put(i,score);}
            }
        }
        for(var ground:p.blueprint.grounds)if(!ground.island.isEmpty()) {
            var owner=p.facilities.stream().filter(f->f.definition().id.equals(ground.island)&&f.definition().island).findFirst().orElseThrow();
            for(int i=0;i<p.terrain.length;i++)if(inGround(ground,p.x(i),p.z(i)))owners.putIfAbsent(i,owner);
        }
        Set<Integer> seams=new HashSet<>();
        for(var e:owners.entrySet())for(int j:MapPlanner.neighbors(p,e.getKey()))if(owners.containsKey(j)&&owners.get(j)!=e.getValue())seams.add(e.getKey());
        for(var e:owners.entrySet()) {
            int i=e.getKey();if(seams.contains(i))continue;
            var f=e.getValue();int x=p.x(i),z=p.z(i),height=(int)f.surface();
            islandGround.add(i);if(f.definition().platform)guardedIslandGround.add(i);
            double cx=f.position().getX()+(f.template().getSize().getX()-1)/2.0,cz=f.position().getZ()+(f.template().getSize().getZ()-1)/2.0;
            double radius=f.footprint()==null?Math.max(Math.abs(x-cx)/(f.template().getSize().getX()/2.0+p.blueprint.settings.islandMargin),Math.abs(z-cz)/(f.template().getSize().getZ()/2.0+p.blueprint.settings.islandMargin)):f.footprint().distance(x-f.position().getX(),z-f.position().getZ(),p.blueprint.settings.islandMargin)/Math.max(1,p.blueprint.settings.islandMargin);
            int depth=3+(int)((p.blueprint.settings.islandDepth-3)*Math.pow(Math.max(0,1-radius),.65))+(int)(hash(x,z,71)%3);
            if(height-depth<=p.terrain[i]+1)throw new IllegalStateException("岛体接近原地形，规划需要调整");
            for(int y=height-depth;y<height;y++)put(x,y,z,y==height-1?surfaceMaterial(p.biomes[i]):y>=height-3?Blocks.DIRT.defaultBlockState():hash(x,z,y)%7==0?Blocks.ANDESITE.defaultBlockState():Blocks.STONE.defaultBlockState());
        }
    }
    private void roads() {
        for(int i:p.roadCells) {
            int x=p.x(i),z=p.z(i),h=p.roadHeights.get(i),ceiling=Math.floorDiv(h+1,2),floor=Math.floorDiv(h,2);
            boolean suspended=p.mode!=GenerationPlan.Mode.TERRAIN||floor-p.terrain[i]>=3||p.water[i];
            if(suspended)bridgeCells++;
            else for(int y=Math.min(p.terrain[i]-1,floor-2);y<floor-1;y++)put(x,y,z,Blocks.COBBLESTONE.defaultBlockState());
            BlockState state=roadSurface(i);
            int blockY=Math.floorDiv(h-1,2);
            put(x,blockY,z,state);
            if(suspended) {
                var theme=BiomeMaterials.theme(p.biomes[i]);
                put(x,blockY-1,z,wood(theme.accent(),"log").setValue(BlockStateProperties.AXIS,Direction.Axis.X));
                if(p.mode==GenerationPlan.Mode.TERRAIN&&Math.floorMod(x,6)==0&&Math.floorMod(z,6)==0)column(x,z,p.bearing[i],blockY-2,theme.accent());
                for(Direction direction:Direction.Plane.HORIZONTAL) {
                    int nx=x+direction.getStepX(),nz=z+direction.getStepZ();if(!p.inside(nx,nz))continue;int j=p.index(nx,nz);
                    if(p.roadCells.contains(j)||facilityGround.contains(j))continue;
                    put(nx,ceiling-1,nz,stone(nx,nz,""));put(nx,ceiling,nz,wood(theme.accent(),"fence"));
                }
            }
        }
    }
    private BlockState roadSurface(int i) {
        int x=p.x(i),z=p.z(i),h=p.roadHeights.get(i);
        if((h&1)!=0)return stone(x,z,"_slab").setValue(BlockStateProperties.SLAB_TYPE,SlabType.BOTTOM);
        Direction high=null;int lowest=h;
        for(Direction direction:Direction.Plane.HORIZONTAL) {
            int nx=x+direction.getStepX(),nz=z+direction.getStepZ();if(!p.inside(nx,nz))continue;
            Integer other=p.roadHeights.get(p.index(nx,nz));if(other!=null&&other<lowest){lowest=other;high=direction.getOpposite();}
        }
        Integer next=p.platformApproaches.get(i);
        if(high!=null&&next!=null&&p.roadHeights.get(next)>=h) {
            Direction toward=Direction.getApproximateNearest(p.x(next)-x,0,p.z(next)-z);
            int bx=x-toward.getStepX(),bz=z-toward.getStepZ();
            Integer behind=p.inside(bx,bz)?p.roadHeights.get(p.index(bx,bz)):null;
            if(behind!=null&&behind<h)high=toward;
        }
        return high==null?stone(x,z,""):stone(x,z,"_stairs").setValue(BlockStateProperties.HORIZONTAL_FACING,high).setValue(BlockStateProperties.HALF,Half.BOTTOM);
    }
    private void platformApproaches() {
        int stairs=0;
        for(int i:p.platformApproaches.keySet()) {
            int x=p.x(i),z=p.z(i),y=Math.floorDiv(p.roadHeights.get(i)-1,2);BlockPos pos=new BlockPos(x,y,z);
            if(level.getBlockEntity(pos)!=null)continue;
            BlockState state=roadSurface(i);put(x,y,z,state);
            if(state.getBlock() instanceof StairBlock)stairs++;
        }
        p.metrics.put("platform_transition_stairs",stairs);
    }
    private void facilities() {
        for(var e:p.foundationBlocks.entrySet())put(e.getKey().getX(),e.getKey().getY(),e.getKey().getZ(),e.getValue());
        for(var f:p.facilities) {
            int height=(int)f.surface(),x0=f.position().getX(),z0=f.position().getZ(),w=f.template().getSize().getX(),d=f.template().getSize().getZ();
            boolean onVoid=p.mode!=GenerationPlan.Mode.TERRAIN;
            if(f.definition().platform&&f.footprint()==null)for(int z=z0;z<z0+d;z++)for(int x=x0;x<x0+w;x++) {
                if(!p.inside(x,z))continue;int i=p.index(x,z);
                if(p.roadCells.contains(i))continue;
                if(p.mode==GenerationPlan.Mode.OCEAN||p.mode==GenerationPlan.Mode.FLOATING&&!f.definition().island||p.mode==GenerationPlan.Mode.TERRAIN&&p.terrain[i]<height)put(x,height-1,z,wood(f.theme().main(),"planks"));
                if(p.mode==GenerationPlan.Mode.TERRAIN&&height-p.terrain[i]<=2)for(int y=p.terrain[i];y<height-1;y++)put(x,y,z,Blocks.DIRT.defaultBlockState());
            }
            if(p.mode==GenerationPlan.Mode.TERRAIN&&f.definition().platform&&f.footprint()==null) {
                for(int z=z0+1;z<z0+d;z+=5)for(int x=x0+1;x<x0+w;x+=5)if(p.inside(x,z)) {
                    int i=p.index(x,z);if(p.terrain[i]<height-2)column(x,z,p.bearing[i],height-2,f.theme().accent());
                }
            }
            if(!f.definition().platform) {
                var blocks=f.template().save(new CompoundTag()).getListOrEmpty("blocks");
                Map<Long,Integer> feet=new HashMap<>();
                for(Tag item:blocks) {
                    var cell=((CompoundTag)item).getListOrEmpty("pos");int xx=x0+cell.getIntOr(0,0),zz=z0+cell.getIntOr(2,0),yy=f.position().getY()+cell.getIntOr(1,0);
                    if(yy<=height+1)feet.merge(BlockPos.asLong(xx,0,zz),yy,Math::min);
                }
                for(var foot:feet.entrySet()) {
                    BlockPos column=BlockPos.of(foot.getKey());int xx=column.getX(),zz=column.getZ();if(!p.inside(xx,zz))continue;
                    if(p.mode==GenerationPlan.Mode.TERRAIN) {
                        int i=p.index(xx,zz);if(p.terrain[i]<foot.getValue())column(xx,zz,p.bearing[i],foot.getValue()-1,f.theme().accent());
                    } else {
                        put(xx,height-1,zz,wood(f.theme().accent(),"planks"));
                        int near=p.nearestRoad(xx,zz),tx=p.x(near),tz=p.z(near),steps=Math.max(Math.abs(tx-xx),Math.abs(tz-zz));
                        if(steps<=6)for(int k=0;k<=steps;k++) {
                            int px=steps==0?xx:(int)Math.round(xx+(tx-xx)*k/(double)steps),pz=steps==0?zz:(int)Math.round(zz+(tz-zz)*k/(double)steps);
                            BlockPos under=new BlockPos(px,height-1,pz);
                            if(p.level.getBlockState(under).isAir()||!p.level.getFluidState(under).isEmpty())put(px,height-1,pz,wood(f.theme().accent(),"planks"));
                        }
                    }
                }
            }
            var settings=new StructurePlaceSettings().setIgnoreEntities(false).setKnownShape(true).addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR);
            if(!f.template().placeInWorld(level,f.position(),f.position(),settings,random,Block.UPDATE_CLIENTS|Block.UPDATE_SUPPRESS_DROPS))throw new IllegalStateException("结构放置失败: "+f.definition().id);
            for(var slot:f.definition().loot) {
                var pos=f.position().offset(slot.pos[0],slot.pos[1],slot.pos[2]);
                if(!(level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity container))throw new IllegalStateException("战利品位置不是可用容器: "+f.definition().id+" "+pos);
                container.setLootTable(ResourceKey.create(Registries.LOOT_TABLE,Identifier.parse(slot.table)));container.setLootTableSeed(level.getSeed()^pos.asLong()^p.id.hashCode());container.setChanged();
            }
            for(var light:f.template().filterBlocks(f.position(),settings,Blocks.LANTERN))p.authoredLights.add(light.pos());
        }
        // Preserve original bridges between separate units; give their approaches a solid surface in void modes.
        if(p.mode!=GenerationPlan.Mode.TERRAIN)for(var f:p.facilities)if(!f.definition().anchor.equals("surface")&&!f.definition().tags.contains("wall")) {
            int x=p.origin.getX()+f.definition().entrance[0],z=p.origin.getZ()+f.definition().entrance[1],near=p.nearestRoad(x,z),tx=p.x(near),tz=p.z(near),steps=Math.max(Math.abs(tx-x),Math.abs(tz-z));
            if(steps>16)continue;
            for(int k=0;k<=steps;k++) {
                int xx=steps==0?x:(int)Math.round(x+(tx-x)*k/(double)steps),zz=steps==0?z:(int)Math.round(z+(tz-z)*k/(double)steps);
                for(int side=-1;side<=1;side++) {
                    int px=Math.abs(tx-x)>Math.abs(tz-z)?xx:xx+side,pz=Math.abs(tx-x)>Math.abs(tz-z)?zz+side:zz;
                    if(level.getBlockState(new BlockPos(px,(int)f.surface()-1,pz)).isAir()||!level.getFluidState(new BlockPos(px,(int)f.surface()-1,pz)).isEmpty())put(px,(int)f.surface()-1,pz,stone(px,pz,""));
                }
            }
        }
        for(var f:p.facilities)if(p.mode==GenerationPlan.Mode.OCEAN&&f.definition().platform&&f.footprint()==null) {
            int x0=f.position().getX(),z0=f.position().getZ(),x1=x0+f.template().getSize().getX()-1,z1=z0+f.template().getSize().getZ()-1,y=(int)f.surface()-1;
            for(int z=z0;z<=z1;z++)for(int x=x0;x<=x1;x++)if(x==x0||x==x1||z==z0||z==z1) {
                BlockPos pos=new BlockPos(x,y,z);if(!level.getBlockState(pos).isCollisionShapeFullBlock(level,pos))put(x,y,z,wood(f.theme().accent(),"log").setValue(BlockStateProperties.AXIS,z==z0||z==z1?Direction.Axis.X:Direction.Axis.Z));
            }
        }
        // Road openings and level entrance aprons are explicit interfaces, after all rigid bodies are present.
        for(var f:p.facilities)if(f.definition().tags.contains("wall"))for(int i:p.roadCells) {
            int x=p.x(i),z=p.z(i);if(x<f.position().getX()||x>=f.position().getX()+f.template().getSize().getX()||z<f.position().getZ()||z>=f.position().getZ()+f.template().getSize().getZ())continue;
            int y=Math.floorDiv(p.roadHeights.get(i)+1,2);for(int h=0;h<3;h++)put(x,y+h,z,Blocks.AIR.defaultBlockState());
        }
        for(int[] zone:p.flatZones)for(int i:zone) {
            int y=Math.floorDiv(p.roadHeights.get(i),2)-1;BlockPos pos=new BlockPos(p.x(i),y,p.z(i));var state=level.getBlockState(pos);
            if(state.getBlock() instanceof StairBlock||state.getBlock() instanceof SlabBlock)put(pos.getX(),y,pos.getZ(),stone(pos.getX(),pos.getZ(),""));
        }
    }
    private void column(int x,int z,int bearing,int top,String family) {
        if(top<bearing)return;
        Integer road=p.inside(x,z)?p.roadHeights.get(p.index(x,z)):null;
        if(road!=null&&top>=Math.floorDiv(road+1,2)&&bearing-1<Math.floorDiv(road+1,2)+3)return;
        supports++;
        for(int y=bearing-1;y<=top;y++)put(x,y,z,y<=bearing+1?Blocks.COBBLESTONE.defaultBlockState():wood(family,"log").setValue(BlockStateProperties.AXIS,Direction.Axis.Y));
        for(int y=bearing+6;y<top;y+=6)for(Direction direction:Direction.Plane.HORIZONTAL) {
            int nx=x+direction.getStepX(),nz=z+direction.getStepZ();if(p.inside(nx,nz)&&!inRoadClearance(nx,y,nz)&&level.getBlockState(new BlockPos(nx,y,nz)).isAir())put(nx,y,nz,wood(family,"stairs").setValue(BlockStateProperties.HALF,Half.TOP).setValue(BlockStateProperties.HORIZONTAL_FACING,direction.getOpposite()));
        }
    }
    private boolean inRoadClearance(int x,int y,int z) {
        if(!p.inside(x,z))return false;Integer half=p.roadHeights.get(p.index(x,z));
        return half!=null&&y>=Math.floorDiv(half+1,2)&&y<Math.floorDiv(half+1,2)+3;
    }
    private void clearRoadFoliage() {
        for(int i:p.roadCells) {
            int x=p.x(i),z=p.z(i),floor=Math.floorDiv(p.roadHeights.get(i)+1,2);
            for(int y=floor;y<floor+2;y++) {
                BlockPos pos=new BlockPos(x,y,z);var state=level.getBlockState(pos);
                if(state.is(BlockTags.LEAVES)||state.is(Blocks.VINE)||state.canBeReplaced()&&state.getFluidState().isEmpty())put(x,y,z,Blocks.AIR.defaultBlockState());
                if(state.is(Blocks.LANTERN)&&state.getValue(BlockStateProperties.HANGING)) {
                    int yy=y;while(yy<floor+2&&level.getBlockState(new BlockPos(x,yy+1,z)).is(Blocks.IRON_CHAIN))yy++;
                    if(yy>=floor+2){put(x,y,z,Blocks.AIR.defaultBlockState());for(int k=y+1;k<yy;k++)put(x,k,z,Blocks.AIR.defaultBlockState());put(x,yy,z,state);}
                    else {put(x,y,z,Blocks.AIR.defaultBlockState());for(int k=y+1;level.getBlockState(new BlockPos(x,k,z)).is(Blocks.IRON_CHAIN);k++)put(x,k,z,Blocks.AIR.defaultBlockState());}
                }
            }
        }
    }
    private void plants() {
        for(var region:p.blueprint.grounds)for(var plant:region.plants) {
            int x=p.origin.getX()+plant.x,z=p.origin.getZ()+plant.z;if(!p.inside(x,z)||!inGround(region,x,z))continue;int i=p.index(x,z);
            if(p.keepClear.contains(i)||p.roadCells.contains(i)||p.water[i]&&p.mode!=GenerationPlan.Mode.FLOATING)continue;
            int y=p.mode==GenerationPlan.Mode.FLOATING?p.origin.getY():p.terrain[i];BlockPos pos=new BlockPos(x,y,z);
            if(p.mode==GenerationPlan.Mode.TERRAIN&&facilityGround.contains(i)) {
                for(var f:p.facilities)if(f.definition().platform&&f.containsGround(x,z)){y=(int)f.surface();pos=new BlockPos(x,y,z);break;}
            }
            if(!level.getBlockState(pos).isAir()||!level.getFluidState(pos.below()).isEmpty())continue;
            String biome=p.biomes[i];BlockState state;
            boolean dry=level.getBlockState(pos.below()).is(Blocks.SAND)||level.getBlockState(pos.below()).is(Blocks.RED_SAND);
            if(dry)state=hash(x,z,101)%5==0?Blocks.DEAD_BUSH.defaultBlockState():(plant.tall?Blocks.TALL_DRY_GRASS:Blocks.SHORT_DRY_GRASS).defaultBlockState();
            else state=block(plant.block);
            if(!state.canSurvive(level,pos))continue;
            if(state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
                if(!level.getBlockState(pos.above()).isAir())continue;
                put(x,y,z,state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.LOWER));put(x,y+1,z,state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.UPPER));
            } else put(x,y,z,state);
        }
    }
    private void safeAnimals() {
        var homes=p.facilities.stream().filter(f->!f.template().save(new CompoundTag()).getListOrEmpty("entities").isEmpty()).toList();
        int moved=0;
        for(var entity:level.getAllEntities())if(entity instanceof net.minecraft.world.entity.animal.Animal&&entity.entityTags().contains("ydnf_map_animal")&&p.inside(entity.blockPosition().getX(),entity.blockPosition().getZ())) {
            var home=homes.stream().min(Comparator.comparingDouble(f->{double dx=Math.max(0,Math.max(f.position().getX()-entity.getX(),entity.getX()-f.position().getX()-f.template().getSize().getX())),dz=Math.max(0,Math.max(f.position().getZ()-entity.getZ(),entity.getZ()-f.position().getZ()-f.template().getSize().getZ()));return dx*dx+dz*dz+Math.abs(entity.getY()-f.surface());})).orElse(null);
            if(home==null)continue;
            if(entity.getY()>=home.surface()&&level.noCollision(entity,entity.getBoundingBox())&&level.getFluidState(entity.blockPosition()).isEmpty()&&level.getFluidState(entity.blockPosition().below()).isEmpty()&&Block.canSupportCenter(level,entity.blockPosition().below(),Direction.UP))continue;
            BlockPos chosen=null;int y=(int)home.surface(),cx=entity.blockPosition().getX(),cz=entity.blockPosition().getZ();
            search:for(int radius=0;radius<Math.max(home.template().getSize().getX(),home.template().getSize().getZ());radius++)for(int dz=-radius;dz<=radius;dz++)for(int dx=-radius;dx<=radius;dx++) {
                if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;int x=cx+dx,z=cz+dz;
                if(x<=home.position().getX()||x>=home.position().getX()+home.template().getSize().getX()-1||z<=home.position().getZ()||z>=home.position().getZ()+home.template().getSize().getZ()-1)continue;
                BlockPos feet=new BlockPos(x,y,z),floor=feet.below();
                var box=entity.getBoundingBox().move(x+.5-entity.getX(),y-entity.getY(),z+.5-entity.getZ());
                if(level.getBlockState(floor).isCollisionShapeFullBlock(level,floor)&&level.getFluidState(feet).isEmpty()&&level.noCollision(entity,box)){chosen=feet;break search;}
            }
            if(chosen==null)throw new IllegalStateException("动物没有安全落脚点: "+home.definition().id);
            entity.setPos(chosen.getX()+.5,chosen.getY(),chosen.getZ()+.5);entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);entity.resetFallDistance();moved++;
        }
        p.metrics.put("animals_relocated",moved);
    }
    private void platformGuards() {
        if(p.mode==GenerationPlan.Mode.TERRAIN)return;
        Set<Integer> all=p.mode==GenerationPlan.Mode.OCEAN?facilityGround:islandGround;
        Set<Integer> edges=p.mode==GenerationPlan.Mode.OCEAN?facilityGround:guardedIslandGround;
        int y=p.origin.getY(),count=0;
        for(int i:edges) {
            if(p.keepClear.contains(i)||p.roadCells.contains(i))continue;int x=p.x(i),z=p.z(i);BlockPos pos=new BlockPos(x,y,z);
            if(!level.getBlockState(pos).isAir()||!Block.canSupportCenter(level,pos.below(),Direction.UP))continue;
            boolean exposed=false;
            for(Direction direction:Direction.Plane.HORIZONTAL) {
                int nx=x+direction.getStepX(),nz=z+direction.getStepZ();
                if(p.inside(nx,nz)&&(all.contains(p.index(nx,nz))||p.roadCells.contains(p.index(nx,nz))))continue;
                BlockPos outside=new BlockPos(nx,y-1,nz);
                if(!Block.canSupportCenter(level,outside,Direction.UP))exposed=true;
            }
            if(exposed){put(x,y,z,wood(BiomeMaterials.theme(p.biomes[i]).accent(),"fence"));count++;}
        }
        p.metrics.put("platform_guards",count);
    }
    private void clearActivityAreas() {
        for(var f:p.facilities)if(f.definition().keepClear)for(int z=f.position().getZ();z<f.position().getZ()+f.template().getSize().getZ();z++)for(int x=f.position().getX();x<f.position().getX()+f.template().getSize().getX();x++)if(f.containsGround(x,z))for(int y=(int)f.surface();y<f.surface()+3;y++)put(x,y,z,Blocks.AIR.defaultBlockState());
    }
    private void externalLandings() {
        if(p.mode!=GenerationPlan.Mode.FLOATING)return;
        for(var road:p.blueprint.roads)if(road.external) {
            int[] end=road.points[road.points.length-1],previous=road.points[road.points.length-2];int x=Math.clamp(p.origin.getX()+end[0],p.minX+3,p.maxX-3),z=Math.clamp(p.origin.getZ()+end[1],p.minZ+3,p.maxZ-3),y=p.origin.getY();
            int towardX=previous[0]-end[0],towardZ=previous[1]-end[1];
            for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++) {
                put(x+dx,y-1,z+dz,stone(x+dx,z+dz,""));
                if(Math.abs(dx)==3||Math.abs(dz)==3) {
                    if(p.inside(x+dx,z+dz)&&p.roadCells.contains(p.index(x+dx,z+dz))&&dx*towardX+dz*towardZ>0)continue;
                    put(x+dx,y,z+dz,wood(BiomeMaterials.theme(p.biomes[p.index(x,z)]).accent(),"fence"));
                    if(p.inside(x+dx,z+dz))p.roadCells.remove(p.index(x+dx,z+dz));
                }
            }
        }
    }
    private BlockState stone(int x,int z,String suffix) {
        int[] weights=p.blueprint.settings.roadWeights;int sum=Arrays.stream(weights).sum(),roll=(int)(hash(Math.floorDiv(x,3),Math.floorDiv(z,3),19)%sum),choice=0;
        while(choice<weights.length-1&&roll>=weights[choice])roll-=weights[choice++];
        return block((suffix.equals("_slab")?p.blueprint.settings.roadSlabs:suffix.equals("_stairs")?p.blueprint.settings.roadStairs:p.blueprint.settings.roadBlocks)[choice]);
    }
    private boolean inGround(cn.yumis.fight.modules.mapgeneration.api.MapBlueprint.Ground g,int x,int z) {
        int xx=x-p.origin.getX(),zz=z-p.origin.getZ();if(xx<g.bounds[0]||zz<g.bounds[1]||xx>g.bounds[2]||zz>g.bounds[3])return false;
        if(g.polygon==null)return true;boolean inside=false;
        for(int i=0,j=g.polygon.length-1;i<g.polygon.length;j=i++) {
            int[] a=g.polygon[i],b=g.polygon[j];if((a[1]>zz)!=(b[1]>zz)&&xx<(double)(b[0]-a[0])*(zz-a[1])/(b[1]-a[1])+a[0])inside=!inside;
        }
        return inside;
    }
    private void grounds() {
        for(var g:p.blueprint.grounds)if(!g.surface.equals("native"))for(int i=0;i<p.terrain.length;i++) {
            int x=p.x(i),z=p.z(i);if(!inGround(g,x,z)||p.roadCells.contains(i)||facilityGround.contains(i))continue;
            if(p.water[i]&&p.mode!=GenerationPlan.Mode.FLOATING)continue;
            int y=p.mode==GenerationPlan.Mode.FLOATING?p.origin.getY()-1:p.terrain[i]-1;BlockPos pos=new BlockPos(x,y,z);
            if(p.mode==GenerationPlan.Mode.FLOATING&&level.getBlockState(pos).isAir())continue;
            put(x,y,z,g.surface.equals("biome")?surfaceMaterial(p.biomes[i]):block(g.surface));
        }
    }
    static BlockState wood(String family,String suffix) {return block("minecraft:"+family+"_"+suffix);}
    static BlockState block(String id) {
        Identifier key=Identifier.parse(id);if(!BuiltInRegistries.BLOCK.containsKey(key))throw new IllegalArgumentException("不存在的方块: "+id);
        return BuiltInRegistries.BLOCK.getValue(key).defaultBlockState();
    }
    private long hash(int x,int z,int salt) {long v=level.getSeed()^((long)x*341873128712L)^((long)z*132897987541L)^salt;v=(v^(v>>>30))*0xbf58476d1ce4e5b9L;v=(v^(v>>>27))*0x94d049bb133111ebL;return (v^(v>>>31))&Long.MAX_VALUE;}
}
