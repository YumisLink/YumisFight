package cn.yumis.fight.modules.industry.platform;

import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
import java.util.function.Function;

/** The client planner and server builder use the same legal placement and port rules. */
public final class ConveyorRoute {
    public static final int MAX_LENGTH=256;
    public record Step(BlockPos pos,Direction facing){}
    public record Plan(List<Step> steps,Map<BlockPos,BlockState> states,Set<BlockPos> fresh){}
    private ConveyorRoute(){}
    public static BlockState state(Level level,BlockPos pos){return ConveyorConnections.loaded(level,pos)?level.getBlockState(pos):Blocks.AIR.defaultBlockState();}
    public static boolean available(Level level,BlockPos pos){
        if(pos.getY()<level.getMinY()||pos.getY()>level.getMaxY()||!ConveyorConnections.loaded(level,pos)||!ConveyorConnections.loaded(level,pos.below())||!level.getWorldBorder().isWithinBounds(pos))return false;
        BlockState state=level.getBlockState(pos);
        return state.isAir()&&state.getFluidState().isEmpty()&&!state.hasBlockEntity()
            &&level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP);
    }
    public static Plan plan(Level level,List<Step> steps,ConveyorBlock block){
        if(steps.size()<2||steps.size()>MAX_LENGTH)throw new IllegalArgumentException("请选择不同的起终点，路线最多256段");
        Map<BlockPos,BlockState> states=new LinkedHashMap<>();Set<BlockPos> fresh=new LinkedHashSet<>();
        for(int i=0;i<steps.size();i++){
            Step step=steps.get(i);BlockPos pos=step.pos();
            if(!step.facing().getAxis().isHorizontal()||states.containsKey(pos)||!ConveyorConnections.loaded(level,pos))throw new IllegalArgumentException("路线重复或经过未加载区块");
            BlockState old=level.getBlockState(pos);
            if(ConveyorConnections.belt(old)){
                if(i!=0&&i!=steps.size()-1)throw new IllegalArgumentException("路线中途有已有传送带");
                if(old.getValue(ConveyorBlock.FACING)!=step.facing())throw new IllegalArgumentException("端点传送带方向不匹配");
                states.put(pos,old);
            }else{
                if(!available(level,pos))throw new IllegalArgumentException("路线被占用、缺少承托或超出世界边界");
                states.put(pos,block.defaultBlockState().setValue(ConveyorBlock.FACING,step.facing()));fresh.add(pos);
            }
        }
        Function<BlockPos,BlockState> view=pos->states.getOrDefault(pos,state(level,pos));
        for(var entry:states.entrySet())entry.setValue(ConveyorConnections.adjusted(entry.getKey(),entry.getValue(),view));
        for(int i=1;i<steps.size();i++){
            BlockPos from=steps.get(i-1).pos(),to=steps.get(i).pos();
            if(!ConveyorConnections.connects(from,states.get(from),to,states.get(to)))throw new IllegalArgumentException("路线坡度或端点方向无法接通");
        }
        return new Plan(List.copyOf(steps),states,fresh);
    }
}
