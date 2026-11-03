package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.modules.industry.api.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Exact pump production and transaction-backed output to adjacent fluid inputs. */
final class IndustrialFluidWork {
    private IndustrialFluidWork(){}
    static void outputAdjacent(MachineBlockEntity be){
        if(!be.device().has(DeviceType.Capability.PUMP)||!(be.getLevel() instanceof ServerLevel level))return;
        if(be.fluids.availableAmount(be.device().fluidOutput())<=0)return;
        var source=be.fluidsFor(Direction.DOWN);var directions=Direction.values();
        int first=Math.floorMod(level.getGameTime(),directions.length);
        for(int i=0;i<directions.length;i++){
            if(be.fluids.availableAmount(be.device().fluidOutput())<=0)break;
            var side=directions[(first+i)%directions.length];var position=be.getBlockPos().relative(side);
            if(!level.hasChunkAt(position))continue;
            var target=level.getCapability(Capabilities.Fluid.BLOCK,position,side.getOpposite());if(target==null||target==source)continue;
            try(var tx=Transaction.openRoot()){
                if(CrystalTransfers.move(source,target,Long.MAX_VALUE,0,tx)>0)tx.commit();
            }
        }
    }
    static final class Cycle {
        Fraction duration=Fraction.ZERO,work=Fraction.ZERO;
        long energy,paid,amount;
        void clear(){duration=work=Fraction.ZERO;energy=paid=amount=0;}
        void begin(MachineBlockEntity be){if(duration.positive())return;var r=IndustrialRules.current();boolean prepaid=be.functionCredit>0&&be.functionCost>0;duration=prepaid?Fraction.of(1):Fraction.of(r.pumpInterval).divide(be.pluginSpeed());energy=prepaid?be.functionCost:Math.multiplyExact(r.pumpEnergy,be.energyMultiplier());amount=r.pumpAmount;work=Fraction.ZERO;paid=0;}
        long produced(){var n=work.multiply(amount).divide(duration);return n.numerator().divide(n.denominator()).longValueExact();}
        void save(ValueOutput out){out.putString("duration",duration.toString());out.putString("work",work.toString());out.putLong("energy",energy);out.putLong("paid",paid);out.putLong("amount",amount);}
        void load(ValueInput in){duration=Fraction.parse(in.getStringOr("duration","0"));work=Fraction.parse(in.getStringOr("work","0"));energy=in.getLongOr("energy",0);paid=in.getLongOr("paid",0);amount=in.getLongOr("amount",0);}
    }
    private static boolean water(ServerLevel level,BlockPos machine){for(Direction side:Direction.values()){var pos=machine.relative(side);if(level.hasChunkAt(pos)&&level.getFluidState(pos).isSource()&&level.getFluidState(pos).getType()==Fluids.WATER)return true;}return false;}
    static IndustrialFunctions.FunctionJob pump(MachineBlockEntity be){
        var level=(ServerLevel)be.getLevel();
        if(!water(level,be.getBlockPos())){be.status="相邻六面需要水源";return null;}
        var resource=FluidResource.of(Fluids.WATER);int tank=be.device().fluidOutput();var c=be.pumpCycle;c.begin(be);
        return new IndustrialFunctions.FunctionJob(be){
            long demand(){
                if(be.fluids.getCapacityAsLong(tank,resource)<=be.fluids.getAmountAsLong(tank))return 0;
                Fraction first=Fraction.of(1).min(c.duration.subtract(c.work)),rest=Fraction.of(1).subtract(first);
                var r=IndustrialRules.current();Fraction nextDuration=Fraction.of(r.pumpInterval).divide(be.pluginSpeed());
                long total=ProcessingWork.demand(c.energy,c.duration,c.work,c.paid,first)+rest.multiply(Math.multiplyExact(r.pumpEnergy,be.energyMultiplier())).divide(nextDuration).ceil();
                return Math.max(0,total-be.functionCredit);
            }
            long run(long budget){
                long spent=0,total=0;Fraction time=Fraction.of(1);
                while(time.positive()&&water(level,be.getBlockPos())){
                    c.begin(be);long before=c.produced(),space=be.fluids.getCapacityAsLong(tank,resource)-be.fluids.getAmountAsLong(tank);
                    if(space<=0)break;
                    long possible=Math.min(c.amount,Math.addExact(before,Math.min(space,c.amount-before)));
                    Fraction room=c.duration.multiply(possible).divide(c.amount).subtract(c.work);
                    Fraction usable=time.min(c.duration.subtract(c.work)).min(room);
                    if(!usable.positive())break;
                    long need=ProcessingWork.demand(c.energy,c.duration,c.work,c.paid,usable);
                    long offered=Math.min(need,Math.addExact(budget-spent,be.functionCredit));
                    var step=ProcessingWork.advance(c.energy,c.duration,c.work,c.paid,usable,offered);
                    Fraction value=step.work().multiply(c.amount).divide(c.duration);long due=value.numerator().divide(value.denominator()).longValueExact()-before;
                    if(step.work().equals(c.work))break;
                    try(var tx=Transaction.openRoot()){
                        long left=due;while(left>0){int n=(int)Math.min(Integer.MAX_VALUE,left);if(be.fluids.insert(tank,resource,n,tx)!=n){done=true;return spent;}left-=n;}tx.commit();
                    }
                    long credit=Math.min(be.functionCredit,offered);be.functionCredit-=credit;spent+=offered-credit;
                    time=time.subtract(step.work().subtract(c.work));be.workedThisTick=true;c.work=step.work();c.paid=step.paid();total+=due;if(step.finished())c.clear();be.setChanged();
                    if(offered<need)break;
                }
                done=true;be.status=total>0?"正在抽水 · 本tick "+total+" mB":"出水缓存已满或等待供能";return spent;
            }
        };
    }
}

