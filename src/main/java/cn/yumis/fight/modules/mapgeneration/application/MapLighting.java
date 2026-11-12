package cn.yumis.fight.modules.mapgeneration.application;

import net.minecraft.core.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.util.*;

/** Native light readings and supported fixtures. Runs after light-engine updates on later server ticks. */
final class MapLighting {
    record Sample(BlockPos pos,int target,String area) {}
    private final GenerationPlan p;
    final List<Sample> samples=new ArrayList<>();
    int additions;
    private final Map<Long,List<Integer>> walkingHeights=new HashMap<>();
    MapLighting(GenerationPlan plan) {p=plan;sample();for(var s:samples)walkingHeights.computeIfAbsent(BlockPos.asLong(s.pos.getX(),0,s.pos.getZ()),k->new ArrayList<>()).add(s.pos.getY()-1);}
    private boolean clear(BlockPos pos) {return p.level.getBlockState(pos).getCollisionShape(p.level,pos).isEmpty();}
    private boolean decorAllowed(BlockPos pos) {return p.inside(pos.getX(),pos.getZ())&&!p.keepClear.contains(p.index(pos.getX(),pos.getZ()));}
    private void sample() {
        Map<BlockPos,Sample> unique=new HashMap<>();
        for(int i:p.roadCells) {
            int x=p.x(i),z=p.z(i),height=Math.floorDiv(p.roadHeights.get(i)+1,2);
            BlockPos pos=new BlockPos(x,height+1,z);
            if(!p.keepClear.contains(i)&&clear(pos)&&clear(pos.below()))unique.put(pos,new Sample(pos,p.blueprint.settings.streetLightTarget,"street"));
            for(Direction d:Direction.Plane.HORIZONTAL)for(int k=1;k<=2;k++) {
                BlockPos side=pos.relative(d,k),floor=side.below(2);
                if(!decorAllowed(side)||p.roadCells.contains(p.index(side.getX(),side.getZ())))continue;
                if(p.level.getBlockState(floor).isCollisionShapeFullBlock(p.level,floor)&&clear(side)&&clear(side.below()))unique.putIfAbsent(side,new Sample(side,p.blueprint.settings.streetLightTarget,"verge"));
            }
        }
        for(var f:p.facilities) {
            if(f.definition().lightTarget<=0)continue;
            for(int z=f.position().getZ();z<f.position().getZ()+f.template().getSize().getZ();z++)for(int x=f.position().getX();x<f.position().getX()+f.template().getSize().getX();x++)
                for(int y=(int)f.surface()-1;y<f.position().getY()+f.template().getSize().getY()-3;y++) {
                    BlockPos floor=new BlockPos(x,y,z),head=floor.above(2);if(!decorAllowed(head))continue;
                    if(!p.level.getBlockState(floor).isCollisionShapeFullBlock(p.level,floor)||!clear(head)||!clear(head.below()))continue;
                    boolean roof=false;
                    for(int yy=y+3;yy<=Math.min(y+10,f.position().getY()+f.template().getSize().getY()-1);yy++) {
                        BlockPos above=new BlockPos(x,yy,z);var state=p.level.getBlockState(above);
                        if(!state.isAir()&&!state.is(BlockTags.LEAVES)&&!state.getCollisionShape(p.level,above).isEmpty()){roof=true;break;}
                    }
                    if(roof)unique.merge(head,new Sample(head,f.definition().lightTarget,f.definition().id),(a,b)->a.target>b.target?a:b);
                }
        }
        samples.addAll(unique.values());samples.sort(Comparator.comparing((Sample s)->s.pos().getX()).thenComparing(s->s.pos().getZ()).thenComparing(s->s.pos().getY()));
    }
    List<Sample> dark() {
        record Reading(Sample sample,int value) {}
        // The light engine propagates on another executor: snapshot before sorting, so its comparator is stable.
        return samples.stream().map(s->new Reading(s,p.level.getBrightness(LightLayer.BLOCK,s.pos))).filter(r->r.value<r.sample.target).sorted(Comparator.comparingInt(Reading::value)).map(Reading::sample).toList();
    }
    int supplement(int budget) {
        int placed=0;List<BlockPos> fresh=new ArrayList<>();
        for(Sample s:dark()) {
            if(placed>=budget)break;
            if(fresh.stream().anyMatch(q->q.distManhattan(s.pos)<5))continue;
            BlockPos lamp=fixture(s);
            if(lamp!=null){fresh.add(lamp);placed++;additions++;}
        }
        MapPlacer.levelLight(p.level);return placed;
    }
    private void set(BlockPos pos,net.minecraft.world.level.block.state.BlockState state) {p.level.setBlock(pos,state,Block.UPDATE_CLIENTS|Block.UPDATE_SUPPRESS_DROPS);}
    private boolean clearsWalkers(BlockPos pos) {
        for(int feet:walkingHeights.getOrDefault(BlockPos.asLong(pos.getX(),0,pos.getZ()),List.of()))if(pos.getY()>=feet&&pos.getY()<feet+2)return false;
        return true;
    }
    private BlockPos fixture(Sample s) {
        // Ceiling lights keep two full blocks of player clearance and use an actual downward support face.
        for(int radius=0;radius<=3;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            for(int dy=1;dy<=3;dy++) {
                BlockPos lamp=s.pos.offset(dx,dy,dz);if(!p.level.getBlockState(lamp).isAir())continue;
                if(!decorAllowed(lamp))continue;
                if(!clearsWalkers(lamp))continue;
                for(int chains=0;chains<=2;chains++) {
                    BlockPos anchor=lamp.above(chains+1);boolean free=true;
                    for(int k=1;k<=chains;k++)if(!p.level.getBlockState(lamp.above(k)).isAir()||!clearsWalkers(lamp.above(k)))free=false;
                    if(!free||!Block.canSupportCenter(p.level,anchor,Direction.DOWN))continue;
                    for(int k=chains;k>=1;k--)set(lamp.above(k),Blocks.IRON_CHAIN.defaultBlockState());
                    set(lamp,Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING,true));return lamp;
                }
            }
        }
        // Low rooms use a non-colliding wall fixture, never replacing ladders or furnishings.
        for(int r=0;r<=3;r++)for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++) {
            BlockPos pos=s.pos.offset(dx,0,dz);if(!p.level.getBlockState(pos).isAir())continue;
            if(!decorAllowed(pos))continue;
            for(Direction d:Direction.Plane.HORIZONTAL)if(p.level.getBlockState(pos.relative(d)).isFaceSturdy(p.level,pos.relative(d),d.getOpposite())) {
                set(pos,Blocks.WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,d.getOpposite()));return pos;
            }
        }
        // Verge gallows: base, post, beam, chain and lantern form one connected object.
        for(int r=2;r<=6;r++)for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=r)continue;
            int x=s.pos.getX()+dx,z=s.pos.getZ()+dz,y=s.pos.getY()-1;
            if(!p.inside(x,z)||p.keepClear.contains(p.index(x,z))||p.roadCells.contains(p.index(x,z)))continue;
            BlockPos base=new BlockPos(x,y,z);
            if(!Block.canSupportCenter(p.level,base.below(),Direction.UP))continue;
            Direction toward=Math.abs(dx)>Math.abs(dz)?dx>0?Direction.WEST:Direction.EAST:dz>0?Direction.NORTH:Direction.SOUTH;
            BlockPos arm=base.relative(toward),lamp=arm.above(2);
            if(!decorAllowed(lamp)||!clearsWalkers(lamp)||!clearsWalkers(arm.above(3))||!clearsWalkers(arm.above(4)))continue;
            boolean space=true;
            for(int yy=0;yy<=4;yy++) {
                var state=p.level.getBlockState(base.above(yy));
                if(!state.isAir()&&!state.is(BlockTags.FENCES)&&!state.canBeReplaced())space=false;
            }
            for(int yy=2;yy<=4;yy++)if(!p.level.getBlockState(arm.above(yy)).isAir())space=false;
            if(!space)continue;
            var theme=BiomeMaterials.theme(p.biomes[p.index(x,z)]);
            set(base,Blocks.MOSSY_COBBLESTONE.defaultBlockState());
            for(int yy=1;yy<=3;yy++)set(base.above(yy),MapPlacer.wood(theme.accent(),"fence"));
            var axis=toward.getAxis();set(base.above(4),MapPlacer.wood(theme.accent(),"log").setValue(BlockStateProperties.AXIS,axis));set(arm.above(4),MapPlacer.wood(theme.accent(),"log").setValue(BlockStateProperties.AXIS,axis));
            set(arm.above(3),Blocks.IRON_CHAIN.defaultBlockState());set(lamp,Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING,true));return lamp;
        }
        // Dense junctions may have no free verge: extend a short supported beam from an existing frame.
        for(int radius=0;radius<=3;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            for(int dy=1;dy<=3;dy++)for(int chains=1;chains<=3;chains++) {
                BlockPos lamp=s.pos.offset(dx,dy,dz),end=lamp.above(chains+1);
                if(!decorAllowed(lamp)||!clearsWalkers(lamp)||!p.level.getBlockState(lamp).isAir())continue;
                boolean vertical=true;for(int k=1;k<=chains+1;k++)if(!p.level.getBlockState(lamp.above(k)).isAir()||!clearsWalkers(lamp.above(k)))vertical=false;
                if(!vertical)continue;
                for(Direction direction:Direction.Plane.HORIZONTAL)for(int reach=1;reach<=4;reach++) {
                    BlockPos anchor=end.relative(direction,reach);
                    if(!p.inside(anchor.getX(),anchor.getZ())||!p.level.getBlockState(anchor).isFaceSturdy(p.level,anchor,direction.getOpposite()))continue;
                    boolean free=true;for(int k=0;k<reach;k++)if(!decorAllowed(end.relative(direction,k))||!p.level.getBlockState(end.relative(direction,k)).isAir()||!clearsWalkers(end.relative(direction,k)))free=false;
                    if(!free)continue;
                    var theme=BiomeMaterials.theme(p.biomes[p.index(lamp.getX(),lamp.getZ())]);
                    for(int k=reach-1;k>=0;k--)set(end.relative(direction,k),MapPlacer.wood(theme.accent(),"log").setValue(BlockStateProperties.AXIS,direction.getAxis()));
                    for(int k=chains;k>=1;k--)set(lamp.above(k),Blocks.IRON_CHAIN.defaultBlockState());
                    set(lamp,Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING,true));return lamp;
                }
            }
        }
        // A rare enclosed corner can be lit through a flush inset without adding a collision in the passage.
        BlockPos floor=s.pos.below(2);var floorState=p.level.getBlockState(floor);
        if(decorAllowed(floor)&&p.level.getBlockEntity(floor)==null&&(floorState.isCollisionShapeFullBlock(p.level,floor)||floorState.getBlock() instanceof SlabBlock||floorState.getBlock() instanceof StairBlock)) {
            BlockPos inset=floorState.isCollisionShapeFullBlock(p.level,floor)?floor:floor.below();
            if(p.level.getBlockEntity(inset)==null&&!p.level.getBlockState(inset).is(Blocks.SEA_LANTERN)) {set(inset,Blocks.SEA_LANTERN.defaultBlockState());return inset;}
        }
        return null;
    }
    Map<String,Integer> metrics() {
        Map<String,Integer> result=new TreeMap<>();
        for(Sample s:samples) {int light=p.level.getBrightness(LightLayer.BLOCK,s.pos);String key=s.area.equals("street")||s.area.equals("verge")?s.area:s.target>=10?"market":"inside";result.merge(key+"_min",light,Math::min);}
        result.put("light_samples",samples.size());result.put("light_under_target",dark().size());result.put("extra_lights",additions);return result;
    }
}
