package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.modules.industry.api.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.*;
import java.util.*;

/** One independent production line, including its start-time recipe and payment snapshot. */
public final class MachineLane {
    public final MachineBlockEntity machine;
    public final int index;
    public String selected="",queued="",status="等待原料",batchRecipe="";
    public long batchEnergy,paid,batchSequence;
    public Fraction batchDuration=Fraction.ZERO,work=Fraction.ZERO,tickTime=Fraction.of(128);
    public double workThisTick;
    public List<ItemStack> batchResults=new ArrayList<>(),batchInputs=new ArrayList<>();
    public FluidStack batchFluid=FluidStack.EMPTY;
    public String batchMiningFluid="";
    public int batchFluidRate,roundRobin;
    public long batchFluidPaid;

    MachineLane(MachineBlockEntity machine,int index){this.machine=machine;this.index=index;}
    public int inputSlot(int input){return machine.slots().inputStart(index)+input;}
    public int fluidTank(int local){return machine.fluidTank(index,local);}
    String displayStatus(){return !machine.running?"已停止":machine.device().needsStructure()&&!machine.structureValid?"结构不完整":status;}
    public void beginTick(){tickTime=Fraction.of(128);workThisTick=0;}
    public void clearBatch(){
        batchInputs.clear();batchRecipe="";batchResults=new ArrayList<>();batchFluid=FluidStack.EMPTY;
        batchMiningFluid="";batchFluidRate=0;batchFluidPaid=0;work=Fraction.ZERO;paid=0;batchEnergy=0;batchDuration=Fraction.ZERO;
    }
    private Fraction usableTime(){
        if(batchFluidRate==0)return tickTime;
        int tank=fluidTank(0);
        long available=IndustrialFluids.matches(machine.fluids.getResource(tank),batchMiningFluid)?machine.fluids.getAmountAsLong(tank):0;
        Fraction end=Fraction.of(batchFluidPaid+available).multiply(2560).divide(batchFluidRate);
        return end.compareTo(work)<=0?Fraction.ZERO:tickTime.min(end.subtract(work));
    }
    public long demand(){
        if(batchRecipe.isEmpty()||!batchDuration.positive()||!tickTime.positive()||!canFinish())return 0;
        return ProcessingWork.demand(batchEnergy,batchDuration,work,paid,usableTime());
    }
    public boolean canFinish(){
        try(var tx=Transaction.openRoot()){
            for(ItemStack result:batchResults)if(insertOutput(result,tx)!=result.getCount())return false;
            return batchFluid.isEmpty()||machine.fluids.insert(fluidTank(machine.device().fluidOutput()),FluidResource.of(batchFluid),batchFluid.getAmount(),tx)==batchFluid.getAmount();
        }
    }
    public int insertOutput(ItemStack stack,TransactionContext tx){
        int inserted=0;
        for(int slot=machine.slots().outputStart(index);slot<machine.slots().outputEnd(index)&&inserted<stack.getCount();slot++)
            inserted+=machine.items.insert(slot,net.neoforged.neoforge.transfer.item.ItemResource.of(stack),stack.getCount()-inserted,tx);
        return inserted;
    }
    public void advance(long supplied){
        if(batchRecipe.isEmpty()||!canFinish())return;
        Fraction time=usableTime();if(!time.positive()){status="批次冷却液不足，进度已保留";return;}
        Fraction before=work;long beforePaid=paid;
        var step=ProcessingWork.advance(batchEnergy,batchDuration,work,paid,time,supplied);
        long fluidDue=batchFluidRate==0?0:step.work().multiply(batchFluidRate).divide(2560).ceil()-batchFluidPaid;
        try(var tx=Transaction.openRoot()){
            int inputTank=fluidTank(0);
            if(fluidDue>0&&machine.fluids.extract(inputTank,machine.fluids.getResource(inputTank),(int)fluidDue,tx)!=fluidDue)return;
            if(step.finished()){
                for(ItemStack result:batchResults)if(insertOutput(result,tx)!=result.getCount())return;
                if(!batchFluid.isEmpty()&&machine.fluids.insert(fluidTank(machine.device().fluidOutput()),FluidResource.of(batchFluid),batchFluid.getAmount(),tx)!=batchFluid.getAmount())return;
            }
            tx.commit();
        }
        work=step.work();tickTime=tickTime.subtract(work.subtract(before));paid=step.paid();batchFluidPaid+=fluidDue;
        machine.consumed+=paid-beforePaid;workThisTick+=Math.max(0,work.subtract(before).value());
        status=step.finished()?"加工完成":paid==beforePaid&&batchEnergy>paid?"当前供能不足，进度已保留":"加工中";
        if(step.finished()){
            clearBatch();
            if(machine.device().selectedRecipe()&&!queued.isEmpty())selected=queued.equals("@auto")?"":queued;
            queued="";
        }
        machine.setChanged();
    }
    public void save(ValueOutput out){
        out.putString("selected",selected);out.putString("queued",queued);out.putInt("round_robin",roundRobin);
        out.putLong("sequence",batchSequence);out.putString("recipe",batchRecipe);out.putLong("energy",batchEnergy);out.putLong("paid",paid);
        out.putString("duration",batchDuration.toString());out.putString("work",work.toString());
        out.store("results",ItemStack.CODEC.listOf(),batchResults);out.store("inputs",ItemStack.CODEC.listOf(),batchInputs);
        out.store("fluid",FluidStack.OPTIONAL_CODEC,batchFluid);out.putString("mining_fluid",batchMiningFluid);
        out.putInt("fluid_rate",batchFluidRate);out.putLong("fluid_paid",batchFluidPaid);
    }
    public void load(ValueInput in){
        selected=in.getStringOr("selected","");queued=in.getStringOr("queued","");roundRobin=in.getIntOr("round_robin",0);
        batchSequence=in.getLongOr("sequence",0);batchRecipe=in.getStringOr("recipe","");batchEnergy=in.getLongOr("energy",0);paid=in.getLongOr("paid",0);
        batchDuration=Fraction.parse(in.getStringOr("duration","0"));work=Fraction.parse(in.getStringOr("work","0"));
        batchResults=new ArrayList<>(in.read("results",ItemStack.CODEC.listOf()).orElse(List.of()));
        batchInputs=new ArrayList<>(in.read("inputs",ItemStack.CODEC.listOf()).orElse(List.of()));
        batchFluid=in.read("fluid",FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        batchMiningFluid=in.getStringOr("mining_fluid","");batchFluidRate=in.getIntOr("fluid_rate",0);batchFluidPaid=in.getLongOr("fluid_paid",0);
        status=batchRecipe.isEmpty()?"等待原料":"恢复中的加工批次";beginTick();
    }
}
