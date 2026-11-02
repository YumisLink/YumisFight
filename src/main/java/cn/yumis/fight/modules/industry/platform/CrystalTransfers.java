package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.modules.industry.api.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import java.util.*;

/** A single allocator participant owns every pair; each pair has its own payment and time ledger. */
final class CrystalTransfers {
    private CrystalTransfers(){}
    static long product(long a,long b){return b!=0&&a>Long.MAX_VALUE/b?Long.MAX_VALUE:a*b;}
    static IndustrialFunctions.FunctionJob prepare(MachineBlockEntity be){
        if(be.device().has(DeviceType.Capability.FLUID_LOGISTICS))return FluidCrystalTransfers.prepare(be);
        var rules=IndustrialRules.current();int tier=be.tier()-1;
        for(var route:be.routes){
            if(route.quota>0)continue;
            route.time=route.time.add(be.pluginSpeed().divide(rules.transferIntervals[tier]));
            long whole=route.time.numerator().divide(route.time.denominator()).longValueExact();route.time=route.time.subtract(Fraction.of(whole));
            if(whole>0){route.quota=route.credit>0?1:whole;if(route.credit==0)route.cost=Math.multiplyExact(be.device().has(DeviceType.Capability.LOGISTICS)?rules.transferEnergy[tier]:rules.fluidTransferOperationEnergy[tier],be.energyMultiplier());}
        }
        if(be.functionCredit>0&&!be.routes.isEmpty()){
            long[] wants=new long[be.routes.size()];for(int i=0;i<wants.length;i++){var route=be.routes.get(i);if(route.quota>0)wants[i]=Math.max(0,product(route.cost,route.quota)-route.credit);}
            var returned=EnergyMath.allocate(be.functionCredit,wants,be.routeCursor);for(int i=0;i<wants.length;i++){be.routes.get(i).credit+=returned.grants()[i];be.functionCredit-=returned.grants()[i];}
        }
        return new IndustrialFunctions.FunctionJob(be){
            long demand(){long sum=0;for(var route:be.routes)if(route.quota>0&&valid(be,route)){long need=Math.max(0,product(route.cost,route.quota)-route.credit);sum=sum>Long.MAX_VALUE-need?Long.MAX_VALUE:sum+need;}return sum;}
            long run(long budget){
                int count=be.routes.size();if(count==0){done=true;be.status="尚未绑定容器配对";return -1;}
                long[] wants=new long[count];for(int i=0;i<count;i++){var route=be.routes.get(i);if(route.quota>0&&valid(be,route))wants[i]=Math.max(0,product(route.cost,route.quota)-route.credit);}
                var allocation=EnergyMath.allocate(budget,wants,be.routeCursor);be.routeCursor=allocation.nextCursor();long paid=0;
                for(int n=0;n<count;n++){int index=Math.floorMod(n+be.routeCursor,count);var route=be.routes.get(index);if(route.quota<=0)continue;
                    if(!valid(be,route)){route.quota=0;continue;}long offered=allocation.grants()[index];
                    if(be.device().has(DeviceType.Capability.LOGISTICS))paid+=transfer(be,route,offered,Capabilities.Item.BLOCK,rules.transferAmounts[tier]);
                    else paid+=transfer(be,route,offered,Capabilities.Fluid.BLOCK,rules.fluidTransferAmounts[tier]);
                }
                done=be.routes.stream().noneMatch(r->r.quota>0);be.status="已绑定 "+be.routes.size()+" / "+be.routeLimit()+" 对容器";be.setChanged();return paid==0&&done?-1:paid;
            }
        };
    }
    static boolean sameContainer(ServerLevel level,BlockPos a,BlockPos b){var state=level.getBlockState(a);return a.equals(b)||state.getBlock() instanceof ChestBlock&&state.getValue(ChestBlock.TYPE)!=ChestType.SINGLE&&a.relative(ChestBlock.getConnectedDirection(state)).equals(b);}
    static Direction portFace(ServerLevel level,BlockPos position,Direction clicked,boolean source,boolean fluid){
        if(!(level.getBlockEntity(position) instanceof MachineBlockEntity machine))return clicked;
        if(source)return Direction.DOWN;
        return !fluid&&machine.device().has(DeviceType.Capability.FUEL)?machine.getBlockState().getValue(MachineBlock.FACING).getOpposite():Direction.UP;
    }
    static boolean valid(MachineBlockEntity be,CrystalRoute route){
        var level=(ServerLevel)be.getLevel();BlockPos from=BlockPos.of(route.source),to=BlockPos.of(route.target);
        if(!route.dimension.equals(level.dimension().identifier().toString())){route.status="不在同一维度";return false;}
        if(!level.hasChunkAt(from)||!level.hasChunkAt(to)){route.status="端点区块未加载";return false;}
        double range=IndustrialRules.current().transferRanges[be.tier()-1];if(be.tier()<3&&(be.getBlockPos().distSqr(from)>range*range||be.getBlockPos().distSqr(to)>range*range)){route.status="端点超出范围";return false;}
        if(sameContainer(level,from,to)){route.status="源与目标为同一容器";return false;}return true;
    }
    private static <R extends Resource> long transfer(MachineBlockEntity be,CrystalRoute route,long offered,net.neoforged.neoforge.capabilities.BlockCapability<ResourceHandler<R>,Direction> capability,int limit){
        var level=(ServerLevel)be.getLevel();var from=BlockPos.of(route.source);var to=BlockPos.of(route.target);
        boolean fluid=be.device().has(DeviceType.Capability.FLUID_LOGISTICS);
        Direction sourceFace=portFace(level,from,route.sourceFace,true,fluid),targetFace=portFace(level,to,route.targetFace,false,fluid);
        ResourceHandler<R> source=level.getCapability(capability,from,sourceFace),target=level.getCapability(capability,to,targetFace);
        if(source==null||target==null||source==target){route.status="端口不可用";route.quota=0;return 0;}
        long available=Math.addExact(route.credit,offered),batches=Math.min(route.quota,available/route.cost);
        // Probe one operation before accepting a partial payment; blocked pairs never consume a new payment.
        if(batches==0){try(var tx=Transaction.openRoot()){if(move(source,target,limit,route.cursor,tx)==0){route.status="源为空或目标已满";route.quota=0;return 0;}}route.credit=available;route.status="等待本次运输供能";return offered;}
        long moved;try(var tx=Transaction.openRoot()){moved=move(source,target,product(batches,limit),route.cursor,tx);if(moved==0){route.status="源为空或目标已满";route.quota=0;return 0;}tx.commit();}
        long completed=(moved+limit-1)/limit,spent=Math.multiplyExact(completed,route.cost),newPayment=Math.max(0,spent-route.credit);
        route.credit=Math.max(0,route.credit-spent);route.quota-=completed;route.cursor=(route.cursor+1)%Math.max(1,source.size());route.status="已移动 "+moved+(be.device().has(DeviceType.Capability.LOGISTICS)?" 件":" mB");
        if(moved<product(batches,limit))route.quota=0;return newPayment;
    }
    static <R extends Resource> long move(ResourceHandler<R> source,ResourceHandler<R> target,long limit,int cursor,net.neoforged.neoforge.transfer.transaction.TransactionContext tx){
        long moved=0;for(int i=0;i<source.size()&&moved<limit;i++){int index=Math.floorMod(i+cursor,source.size());R resource=source.getResource(index);if(resource.isEmpty())continue;
            long remaining=Math.min(limit-moved,source.getAmountAsLong(index));while(remaining>0){int n=(int)Math.min(Integer.MAX_VALUE,remaining),accepted=target.insert(resource,n,tx);if(accepted==0)break;if(source.extract(index,resource,accepted,tx)!=accepted)throw new IllegalStateException("运输源库存已变化");moved+=accepted;remaining-=accepted;if(accepted<n)break;}
        }return moved;
    }
}
