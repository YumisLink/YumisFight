package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.modules.industry.api.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** A paid operation keeps its fixed fee and releases its original fluid quota over time. */
final class FluidCrystalTransfers {
    private FluidCrystalTransfers(){}
    private static void begin(MachineBlockEntity be,CrystalRoute route){
        if(route.fluidWindow.duration().positive())return;var r=IndustrialRules.current();int tier=be.tier()-1;
        if(route.credit==0&&route.quota==0||route.cost==0)route.cost=Math.multiplyExact(r.fluidTransferOperationEnergy[tier],be.energyMultiplier());
        route.fluidWindow=new CrystalRoute.FluidWindow(route.quota>0?Fraction.of(1):Fraction.of(r.transferIntervals[tier]).divide(be.pluginSpeed()),Fraction.ZERO,r.fluidTransferAmounts[tier],0,false);
        be.setChanged();
    }
    private record Ports(ResourceHandler<FluidResource> source,ResourceHandler<FluidResource> target){}
    private static Ports ports(MachineBlockEntity be,CrystalRoute route){
        if(!CrystalTransfers.valid(be,route))return null;var level=(ServerLevel)be.getLevel();var from=BlockPos.of(route.source);var to=BlockPos.of(route.target);
        var source=level.getCapability(Capabilities.Fluid.BLOCK,from,CrystalTransfers.portFace(level,from,route.sourceFace,true,true));
        var target=level.getCapability(Capabilities.Fluid.BLOCK,to,CrystalTransfers.portFace(level,to,route.targetFace,false,true));
        return source==null||target==null||source==target?null:new Ports(source,target);
    }
    private static long demand(MachineBlockEntity be,CrystalRoute route){
        begin(be,route);var p=ports(be,route);if(p==null)return 0;
        try(var tx=Transaction.openRoot()){if(CrystalTransfers.move(p.source,p.target,1,route.cursor,tx)==0)return 0;}
        if(route.quota>0)return Math.max(0,Math.multiplyExact(route.cost,route.quota)-route.credit);
        var w=route.fluidWindow;var r=IndustrialRules.current();int tier=be.tier()-1;
        Fraction rest=Fraction.of(1).subtract(Fraction.of(1).min(w.duration().subtract(w.elapsed())));
        long later=rest.divide(Fraction.of(r.transferIntervals[tier]).divide(be.pluginSpeed())).ceil();
        long cost=Math.multiplyExact(r.fluidTransferOperationEnergy[tier],be.energyMultiplier());
        return Math.max(0,Math.addExact(w.paid()?0:route.cost,Math.multiplyExact(later,cost))-route.credit);
    }
    static IndustrialFunctions.FunctionJob prepare(MachineBlockEntity be){
        if(be.functionCredit>0){long[] wants=be.routes.stream().mapToLong(r->demand(be,r)).toArray();var allocation=EnergyMath.allocate(be.functionCredit,wants,be.routeCursor);for(int i=0;i<wants.length;i++){be.routes.get(i).credit+=allocation.grants()[i];be.functionCredit-=allocation.grants()[i];}}
        return new IndustrialFunctions.FunctionJob(be){
            long demand(){long total=0;for(var route:be.routes)total=Math.addExact(total,FluidCrystalTransfers.demand(be,route));return total;}
            long run(long budget){
                long[] wants=be.routes.stream().mapToLong(r->FluidCrystalTransfers.demand(be,r)).toArray();var allocation=EnergyMath.allocate(budget,wants,be.routeCursor);be.routeCursor=allocation.nextCursor();long spent=0;
                for(int n=0;n<be.routes.size();n++){int i=Math.floorMod(n+be.routeCursor,be.routes.size());spent+=advance(be,be.routes.get(i),allocation.grants()[i]);}
                done=true;be.status="逐tick供液 · "+be.routes.size()+" 对容器";be.setChanged();return spent;
            }
        };
    }
    private static long advance(MachineBlockEntity be,CrystalRoute route,long budget){
        Fraction time=Fraction.of(1);long spent=0,movedTotal=0;
        while(time.positive()){
            begin(be,route);var p=ports(be,route);if(p==null){route.status="端点不可用，已保留运输进度";break;}
            var w=route.fluidWindow;boolean ready=route.quota>0;Fraction slice=ready?Fraction.ZERO:time.min(w.duration().subtract(w.elapsed())),end=ready?w.duration():w.elapsed().add(slice);
            Fraction available=end.multiply(w.limit()).divide(w.duration());long limit=available.numerator().divide(available.denominator()).longValueExact()-w.moved();
            if(!w.paid()&&limit<=0){route.fluidWindow=new CrystalRoute.FluidWindow(w.duration(),end,w.limit(),w.moved(),false);time=time.subtract(slice);continue;}
            long moved;
            try(var tx=Transaction.openRoot()){
                moved=CrystalTransfers.move(p.source,p.target,Math.max(0,limit),route.cursor,tx);
                if(!w.paid()){
                    if(moved==0){route.status="源为空或目标已满";break;}
                    long need=Math.max(0,route.cost-route.credit),payment=Math.min(budget-spent,need);
                    if(payment<need){route.credit+=payment;spent+=payment;route.status="等待本次供液供能";break;}
                    tx.commit();spent+=payment;route.credit=Math.max(0,route.credit-route.cost);
                }else tx.commit();
            }
            time=time.subtract(slice);movedTotal+=moved;
            if(ready){if(moved<limit)route.quota=0;else route.quota--;}
            route.fluidWindow=end.equals(w.duration())?CrystalRoute.FluidWindow.EMPTY:new CrystalRoute.FluidWindow(w.duration(),end,w.limit(),w.moved()+moved,true);
            if(moved>0)route.cursor=(route.cursor+1)%Math.max(1,p.source.size());
            if(!route.fluidWindow.duration().positive()&&route.quota==0)route.cost=0;
        }
        if(movedTotal>0){be.workedThisTick=true;route.status="逐tick供液 · 已移动 "+movedTotal+" mB";}return spent;
    }
}
