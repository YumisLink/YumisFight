package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.modules.industry.api.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Exact, batched pumping; payment is held by the industrial function job. */
final class IndustrialFluidWork {
    private IndustrialFluidWork(){}
    private static boolean water(ServerLevel level,BlockPos machine){for(Direction side:Direction.values()){var pos=machine.relative(side);if(level.hasChunkAt(pos)&&level.getFluidState(pos).isSource()&&level.getFluidState(pos).getType()==Fluids.WATER)return true;}return false;}
    static IndustrialFunctions.FunctionJob pump(MachineBlockEntity be){
        var level=(ServerLevel)be.getLevel();var rules=IndustrialRules.current();
        be.functionTime=be.functionTime.add(be.pluginSpeed().divide(rules.pumpInterval));long whole=be.functionTime.numerator().divide(be.functionTime.denominator()).longValueExact();be.functionTime=be.functionTime.subtract(Fraction.of(whole));
        if(whole==0)return null;if(!water(level,be.getBlockPos())){be.status="相邻六面需要水源";return null;}
        int amount=rules.pumpAmount,tank=be.device().fluidOutput();if(be.functionCredit==0)be.functionCost=Math.multiplyExact(rules.pumpEnergy,be.energyMultiplier());long cost=be.functionCost;
        return new IndustrialFunctions.FunctionJob(be){long remaining=whole;
            long demand(){return CrystalTransfers.product(remaining,cost);}
            long run(long budget){
                if(!water(level,be.getBlockPos())){done=true;return -1;}
                var resource=FluidResource.of(Fluids.WATER);long fit=(be.fluids.getCapacityAsLong(tank,resource)-be.fluids.getAmountAsLong(tank))/amount;
                if(fit<=0){done=true;be.status="出水缓存已满";return -1;}
                long batches=Math.min(Math.min(remaining,fit),budget/cost);if(batches==0){be.status="抽水待供能";return 0;}
                long total=Math.multiplyExact(batches,amount),left=total;
                try(var tx=Transaction.openRoot()){while(left>0){int n=(int)Math.min(Integer.MAX_VALUE,left);if(be.fluids.insert(tank,resource,n,tx)!=n)return -1;left-=n;}tx.commit();}
                remaining-=batches;done=remaining==0||batches==fit;be.status="已抽取 "+total+" mB 水";return Math.multiplyExact(batches,cost);
            }
        };
    }
}
