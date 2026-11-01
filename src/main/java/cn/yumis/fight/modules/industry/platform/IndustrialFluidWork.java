package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.modules.industry.api.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Fluid-only jobs use the existing industrial allocator and transfer transactions. */
final class IndustrialFluidWork {
    private IndustrialFluidWork(){}
    static IndustrialFunctions.FunctionJob pump(MachineBlockEntity be){
        ServerLevel level=(ServerLevel)be.getLevel();var rules=IndustrialRules.current();
        if(Math.floorMod(level.getGameTime()+be.getBlockPos().asLong(),rules.pumpInterval)!=0)return null;
        boolean water=false;for(Direction side:Direction.values()){BlockPos p=be.getBlockPos().relative(side);if(level.hasChunkAt(p)&&level.getFluidState(p).isSource()&&level.getFluidState(p).getType()==Fluids.WATER){water=true;break;}}
        if(!water){be.status="相邻六面需要水源";return null;}
        int amount=rules.pumpAmount,tank=be.device().fluidOutput();long cost=rules.pumpEnergy;
        return new IndustrialFunctions.FunctionJob(be){
            long demand(){return cost;}
            long run(long budget){
                boolean source=false;for(Direction side:Direction.values()){var p=be.getBlockPos().relative(side);if(level.hasChunkAt(p)&&level.getFluidState(p).isSource()&&level.getFluidState(p).getType()==Fluids.WATER){source=true;break;}}
                if(!source){done=true;return -1;}
                try(var tx=Transaction.openRoot()){
                    if(be.fluids.insert(tank,FluidResource.of(Fluids.WATER),amount,tx)!=amount){be.status="出水缓存已满";done=true;return -1;}
                    if(budget<cost){be.status="抽水待供能";return 0;}
                    tx.commit();done=true;be.status="已抽取 "+amount+" mB 水";return cost;
                }
            }
        };
    }
    static IndustrialFunctions.FunctionJob transfer(MachineBlockEntity be){
        ServerLevel level=(ServerLevel)be.getLevel();String dimension=level.dimension().identifier().toString();
        if(!dimension.equals(be.sourceDimension)||!dimension.equals(be.targetDimension)){be.status="请用权杖绑定同维度源出口与目标入口";return null;}
        BlockPos source=BlockPos.of(be.sourcePosition),target=BlockPos.of(be.targetPosition);Direction from=be.sourceFace,to=be.targetFace;
        if(source.equals(target)){be.status="源与目标不能相同";return null;}
        if(!level.hasChunkAt(source)||!level.hasChunkAt(target)){be.status="源或目标区块未加载";return null;}
        int tier=be.tier()-1;double range=IndustrialRules.current().transferRanges[tier];
        if(be.tier()<3&&(be.getBlockPos().distSqr(source)>range*range||be.getBlockPos().distSqr(target)>range*range)){be.status="源或目标超出供液范围";return null;}
        var sourceEntity=level.getBlockEntity(source);var targetEntity=level.getBlockEntity(target);
        int limit=IndustrialRules.current().fluidTransferAmounts[tier];long price=IndustrialRules.current().fluidTransferEnergyPerBucket[tier];
        return new IndustrialFunctions.FunctionJob(be){
            int moved;long paid;
            long cost(int amount){return ((long)amount*price+999)/1000;}
            long demand(){return cost(limit)-paid;}
            long run(long budget){
                if(be.sourcePosition!=source.asLong()||be.targetPosition!=target.asLong()||be.sourceFace!=from||be.targetFace!=to||!be.sourceDimension.equals(dimension)||!be.targetDimension.equals(dimension)||!level.hasChunkAt(source)||!level.hasChunkAt(target)||level.getBlockEntity(source)!=sourceEntity||level.getBlockEntity(target)!=targetEntity){done=true;return -1;}
                var input=level.getCapability(Capabilities.Fluid.BLOCK,source,from);var output=level.getCapability(Capabilities.Fluid.BLOCK,target,to);
                if(input==null||output==null||input==output){done=true;be.status="液体接口不可用";return -1;}
                boolean possible=false;
                try(var probe=Transaction.openRoot()){for(int i=0;i<input.size();i++){var fluid=input.getResource(i);if(!fluid.isEmpty()&&input.extract(i,fluid,1,probe)==1&&output.insert(fluid,1,probe)==1){possible=true;break;}}}
                if(!possible){done=true;be.status="源无液或目标已满／不接受该液体";return -1;}
                int quota=price==0?limit-moved:(int)Math.min(limit-moved,Math.max(0,(paid+budget)*1000/price-moved));
                if(quota<=0){be.status="供液待供能";return 0;}
                int transferred=0;
                for(int i=0;i<input.size()&&transferred<quota;i++){
                    var fluid=input.getResource(i);if(fluid.isEmpty())continue;
                    try(var tx=Transaction.openRoot()){
                        int accepted=output.insert(fluid,Math.min(quota-transferred,input.getAmountAsInt(i)),tx);
                        if(accepted>0&&input.extract(i,fluid,accepted,tx)==accepted){tx.commit();transferred+=accepted;}
                    }
                }
                if(transferred==0){done=true;be.status="目标剩余容量不足";return -1;}
                moved+=transferred;long due=cost(moved)-paid;paid+=due;done=moved==limit;be.status="已供液 "+transferred+" mB";return due;
            }
        };
    }
}
