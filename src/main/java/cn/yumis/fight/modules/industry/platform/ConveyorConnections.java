package cn.yumis.fight.modules.industry.platform;

import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.Function;

/** Belt ports are paired by their world height, including the raised end of a ramp. */
public final class ConveyorConnections {
    private ConveyorConnections(){}
    public static boolean belt(BlockState state){return state.getBlock() instanceof ConveyorBlock;}
    public static boolean loaded(Level level,BlockPos pos){return level.getChunkSource().hasChunk(pos.getX()>>4,pos.getZ()>>4);}
    public static double lift(BlockState state,double x,double z){
        Direction facing=state.getValue(ConveyorBlock.FACING);
        double t=Math.clamp(.5+(x-.5)*facing.getStepX()+(z-.5)*facing.getStepZ(),0,1);
        return switch(state.getValue(ConveyorBlock.SLOPE)){case UP->t;case DOWN->1-t;default->0;};
    }
    public static int edge(BlockState state,Direction side){
        Direction facing=state.getValue(ConveyorBlock.FACING);
        return switch(state.getValue(ConveyorBlock.SLOPE)){case UP->side==facing?1:0;case DOWN->side==facing.getOpposite()?1:0;default->0;};
    }
    public static boolean accepts(BlockState state,Direction side){
        Direction facing=state.getValue(ConveyorBlock.FACING);
        return state.getValue(ConveyorBlock.SLOPE)==ConveyorBlock.Slope.FLAT?side!=facing:side==facing.getOpposite();
    }
    public static boolean connects(BlockPos from,BlockState source,BlockPos to,BlockState target){
        if(!belt(source)||!belt(target))return false;
        Direction facing=source.getValue(ConveyorBlock.FACING);
        return from.relative(facing).getX()==to.getX()&&from.relative(facing).getZ()==to.getZ()
            &&accepts(target,facing.getOpposite())
            &&from.getY()+edge(source,facing)==to.getY()+edge(target,facing.getOpposite());
    }
    public static BlockState adjusted(BlockPos pos,BlockState state,Function<BlockPos,BlockState> view){
        Direction facing=state.getValue(ConveyorBlock.FACING),high=null;
        // Vanilla RailState checks north then south, east then west; the latter wins ties.
        Direction[] axis=facing.getAxis()==Direction.Axis.Z?new Direction[]{Direction.NORTH,Direction.SOUTH}:new Direction[]{Direction.EAST,Direction.WEST};
        for(Direction side:axis)if(belt(view.apply(pos.relative(side).above()))
            &&(side!=facing||!belt(view.apply(pos.relative(side)))))high=side;
        var slope=high==null?ConveyorBlock.Slope.FLAT:high==facing?ConveyorBlock.Slope.UP:ConveyorBlock.Slope.DOWN;
        if(slope!=ConveyorBlock.Slope.FLAT&&!view.apply(pos.above()).isAir())slope=ConveyorBlock.Slope.FLAT;
        return state.setValue(ConveyorBlock.SLOPE,slope);
    }
    public static BlockPos downstream(Level level,BlockPos pos,BlockState state){
        Direction facing=state.getValue(ConveyorBlock.FACING);
        int height=pos.getY()+edge(state,facing);
        for(int y:new int[]{height,height-1}){
            BlockPos target=new BlockPos(pos.getX()+facing.getStepX(),y,pos.getZ()+facing.getStepZ());
            if(loaded(level,target)&&connects(pos,state,target,level.getBlockState(target)))return target;
        }
        return new BlockPos(pos.getX()+facing.getStepX(),height,pos.getZ()+facing.getStepZ());
    }
    public static BlockPos upstream(Level level,BlockPos pos,BlockState state,Direction side){
        if(!accepts(state,side))return null;
        int height=pos.getY()+edge(state,side);
        for(int y:new int[]{height,height-1}){
            BlockPos source=new BlockPos(pos.getX()+side.getStepX(),y,pos.getZ()+side.getStepZ());
            if(loaded(level,source)&&connects(source,level.getBlockState(source),pos,state))return source;
        }
        return null;
    }
    public static void refresh(Level level,BlockPos center){
        if(level.isClientSide())return;
        update(level,center);
        for(Direction side:Direction.Plane.HORIZONTAL)for(int dy=-1;dy<=1;dy++)update(level,center.relative(side).offset(0,dy,0));
    }
    private static void update(Level level,BlockPos pos){
        if(!loaded(level,pos))return;BlockState old=level.getBlockState(pos);
        if(belt(old)){
            BlockState next=adjusted(pos,old,p->ConveyorRoute.state(level,p));
            if(next!=old)level.setBlock(pos,next,net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            // Placement callbacks may adjust a ramp before the outer placement attaches its BE.
            if(level.getBlockEntity(pos) instanceof ConveyorBlockEntity belt&&belt.getBlockState()!=next)belt.setBlockState(next);
        }
    }
}
