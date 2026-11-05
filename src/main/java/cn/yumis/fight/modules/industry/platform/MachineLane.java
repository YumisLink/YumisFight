package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.modules.industry.api.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.*;
import java.util.*;

/** One independent production line, including its start-time recipe and payment snapshot. */
public final class MachineLane {
    public record FluidInput(int tank,FluidStack stack) {
        static final Codec<FluidInput> CODEC=RecordCodecBuilder.create(i->i.group(Codec.INT.fieldOf("tank").forGetter(FluidInput::tank),FluidStack.CODEC.fieldOf("fluid").forGetter(FluidInput::stack)).apply(i,FluidInput::new));
    }
    public final MachineBlockEntity machine;
    public final int index;
    public String selected="",queued="",status="等待原料",batchRecipe="";
    public long batchEnergy,paid,batchSequence;
    public Fraction batchDuration=Fraction.ZERO,work=Fraction.ZERO,tickTime=Fraction.of(128);
    public double workThisTick;
    public List<ItemStack> batchResults=new ArrayList<>(),batchInputs=new ArrayList<>();
    public List<FluidInput> batchFluidInputs=new ArrayList<>();
    public boolean progressive;
    public boolean deferredInputs;
    public FluidStack batchFluid=FluidStack.EMPTY;
    public String batchMiningFluid="";
    public int batchFluidRate,roundRobin;
    public long batchFluidPaid;

    private record State(String selected,String queued,String status,String recipe,long energy,long paid,long sequence,Fraction duration,Fraction work,Fraction time,double rate,List<ItemStack> results,List<ItemStack> inputs,List<FluidInput> fluids,boolean progressive,boolean deferred,FluidStack output,String cooling,int coolingRate,long coolingPaid,int cursor) {}
    private final SnapshotJournal<State> journal=new SnapshotJournal<>() {
        protected State createSnapshot(){return new State(selected,queued,status,batchRecipe,batchEnergy,paid,batchSequence,batchDuration,work,tickTime,workThisTick,new ArrayList<>(batchResults),new ArrayList<>(batchInputs),new ArrayList<>(batchFluidInputs),progressive,deferredInputs,batchFluid,batchMiningFluid,batchFluidRate,batchFluidPaid,roundRobin);}
        protected void revertToSnapshot(State s){selected=s.selected;queued=s.queued;status=s.status;batchRecipe=s.recipe;batchEnergy=s.energy;paid=s.paid;batchSequence=s.sequence;batchDuration=s.duration;work=s.work;tickTime=s.time;workThisTick=s.rate;batchResults=s.results;batchInputs=s.inputs;batchFluidInputs=s.fluids;progressive=s.progressive;deferredInputs=s.deferred;batchFluid=s.output;batchMiningFluid=s.cooling;batchFluidRate=s.coolingRate;batchFluidPaid=s.coolingPaid;roundRobin=s.cursor;}
        protected void onRootCommit(State previous){machine.setChanged();}
    };
    void record(TransactionContext tx){journal.updateSnapshots(tx);}
    MachineLane(MachineBlockEntity machine,int index){this.machine=machine;this.index=index;}
    public int inputSlot(int input){return machine.slots().inputStart(index)+input;}
    public int fluidTank(int local){return machine.fluidTank(index,local);}
    String displayStatus(){return !machine.running?"已停止":machine.device().needsStructure()&&!machine.structureValid?"结构不完整":status;}
    public void beginTick(){tickTime=Fraction.of(128);workThisTick=0;}
    public void clearBatch(){
        batchInputs=new ArrayList<>();batchFluidInputs=new ArrayList<>();batchRecipe="";batchResults=new ArrayList<>();batchFluid=FluidStack.EMPTY;progressive=false;deferredInputs=false;
        batchMiningFluid="";batchFluidRate=0;batchFluidPaid=0;work=Fraction.ZERO;paid=0;batchEnergy=0;batchDuration=Fraction.ZERO;
    }
    private long cumulative(long total,Fraction at,boolean consume){
        if(total==0||!batchDuration.positive())return 0;
        Fraction value=at.multiply(total).divide(batchDuration);
        return consume?value.ceil():value.numerator().divide(value.denominator()).longValueExact();
    }
    public long reservedFluid(int tank){
        if(!progressive||batchRecipe.isEmpty())return 0;long reserved=0;
        for(var input:batchFluidInputs)if(input.tank==tank)reserved+=input.stack.getAmount()-cumulative(input.stack.getAmount(),work,true);
        return reserved;
    }
    public List<ItemStack> refundableInputs(){
        if(deferredInputs)return List.of();
        List<ItemStack> result=new ArrayList<>();
        var consumed=new LinkedHashMap<net.neoforged.neoforge.transfer.item.ItemResource,Long>();
        if(progressive){for(var input:batchInputs)consumed.merge(net.neoforged.neoforge.transfer.item.ItemResource.of(input),(long)input.getCount(),Math::addExact);consumed.replaceAll((resource,total)->cumulative(total,work,true));}
        for(var input:batchInputs){var resource=net.neoforged.neoforge.transfer.item.ItemResource.of(input);long used=Math.min(input.getCount(),consumed.getOrDefault(resource,0L));consumed.computeIfPresent(resource,(r,n)->n-used);int count=input.getCount()-(int)used;if(count>0)result.add(input.copyWithCount(count));}
        return result;
    }
    private boolean transferStep(Fraction end,TransactionContext tx){
        if(deferredInputs&&end.equals(batchDuration)&&!takeDeferredInputs(tx))return false;
        if(progressive)for(var input:batchFluidInputs){int due=(int)(cumulative(input.stack.getAmount(),end,true)-cumulative(input.stack.getAmount(),work,true));if(due>0&&machine.fluids.extractReserved(this,input.tank,FluidResource.of(input.stack),due,tx)!=due)return false;}
        long cooling=batchFluidRate==0?0:end.multiply(batchFluidRate).divide(2560).ceil()-batchFluidPaid;
        if(cooling>0&&machine.fluids.extract(fluidTank(0),machine.fluids.getResource(fluidTank(0)),Math.toIntExact(cooling),tx)!=cooling)return false;
        if(progressive||end.equals(batchDuration)){
            for(var output:batchResults){int count=progressive?(int)(cumulative(output.getCount(),end,false)-cumulative(output.getCount(),work,false)):output.getCount();if(count>0&&insertOutput(output.copyWithCount(count),tx)!=count)return false;}
            int count=batchFluid.isEmpty()?0:progressive?(int)(cumulative(batchFluid.getAmount(),end,false)-cumulative(batchFluid.getAmount(),work,false)):batchFluid.getAmount();
            if(count>0&&machine.fluids.insert(fluidTank(machine.device().fluidOutput()),FluidResource.of(batchFluid),count,tx)!=count)return false;
        }
        return true;
    }
    private boolean produces(Fraction end){for(var output:batchResults)if(cumulative(output.getCount(),end,false)>cumulative(output.getCount(),work,false))return true;return !batchFluid.isEmpty()&&cumulative(batchFluid.getAmount(),end,false)>cumulative(batchFluid.getAmount(),work,false);}
    private Fraction usableTime(){return usableTime(tickTime);}
    private Fraction usableTime(Fraction availableTime){
        if(!progressive&&!canFinish())return Fraction.ZERO;
        Fraction time=availableTime.min(batchDuration.subtract(work));
        if(batchFluidRate>0){
            int tank=fluidTank(0);long available=IndustrialFluids.matches(machine.fluids.getResource(tank),batchMiningFluid)?machine.fluids.availableAmount(tank):0;
            Fraction end=Fraction.of(batchFluidPaid+available).multiply(2560).divide(batchFluidRate);
            if(end.compareTo(work)<=0)return Fraction.ZERO;time=time.min(end.subtract(work));
        }
        // A partial tick can fill remaining output space; all probes roll back.
        for(int attempt=0;attempt<48&&time.positive();attempt++){
            try(var tx=Transaction.openRoot()){if(transferStep(work.add(time),tx))return attempt==0||produces(work.add(time))?time:Fraction.ZERO;}
            time=time.divide(2);
        }
        return Fraction.ZERO;
    }
    public long demand(){
        if(deferredInputs&&!hasDeferredInputs()){cancelDeferred();return 0;}
        if(batchRecipe.isEmpty()||!batchDuration.positive()||!tickTime.positive())return 0;
        return ProcessingWork.demand(batchEnergy,batchDuration,work,paid,usableTime());
    }
    public boolean canFinish(){
        if(progressive)return !batchRecipe.isEmpty()&&batchDuration.positive()&&usableTime(Fraction.of(128)).positive();
        try(var tx=Transaction.openRoot()){
            for(ItemStack output:batchResults){int count=progressive?(cumulative(output.getCount(),work,false)<output.getCount()?1:0):output.getCount();if(count>0&&insertOutput(output.copyWithCount(count),tx)!=count)return false;}
            int count=batchFluid.isEmpty()?0:progressive?(cumulative(batchFluid.getAmount(),work,false)<batchFluid.getAmount()?1:0):batchFluid.getAmount();
            return count==0||machine.fluids.insert(fluidTank(machine.device().fluidOutput()),FluidResource.of(batchFluid),count,tx)==count;
        }
    }
    public int insertOutput(ItemStack stack,TransactionContext tx){
        int inserted=0;
        for(int slot=machine.slots().outputStart(index);slot<machine.slots().outputEnd(index)&&inserted<stack.getCount();slot++)inserted+=machine.items.insert(slot,net.neoforged.neoforge.transfer.item.ItemResource.of(stack),stack.getCount()-inserted,tx);
        return inserted;
    }
    /** Returns accepted energy; callers retain every unaccepted grid/local unit. */
    public long advance(long supplied){
        if(batchRecipe.isEmpty())return 0;
        if(deferredInputs&&!hasDeferredInputs()){cancelDeferred();return 0;}
        Fraction time=usableTime();if(!time.positive()){status="原料或产物空间不足，进度已保留";return 0;}
        Fraction before=work;long beforePaid=paid;
        long accepted=Math.min(supplied,ProcessingWork.demand(batchEnergy,batchDuration,work,paid,time));
        var step=ProcessingWork.advance(batchEnergy,batchDuration,work,paid,time,accepted);
        long cooling=batchFluidRate==0?0:step.work().multiply(batchFluidRate).divide(2560).ceil()-batchFluidPaid;
        try(var tx=Transaction.openRoot()){
            if(!transferStep(step.work(),tx))return 0;
            record(tx);work=step.work();tickTime=tickTime.subtract(work.subtract(before));paid=step.paid();batchFluidPaid+=cooling;workThisTick+=Math.max(0,work.subtract(before).value());
            status=step.finished()?"加工完成":paid==beforePaid&&batchEnergy>paid?"当前供能不足，进度已保留":"加工中";
            if(step.finished()){clearBatch();if(machine.device().selectedRecipe()&&!queued.isEmpty())selected=queued.equals("@auto")?"":queued;queued="";}
            tx.commit();
        }
        machine.consumed+=accepted;if(!step.work().equals(before))machine.workedThisTick=true;machine.setChanged();return accepted;
    }
    public void save(ValueOutput out){
        out.putString("selected",selected);out.putString("queued",queued);out.putInt("round_robin",roundRobin);
        out.putLong("sequence",batchSequence);out.putString("recipe",batchRecipe);out.putLong("energy",batchEnergy);out.putLong("paid",paid);
        out.putString("duration",batchDuration.toString());out.putString("work",work.toString());
        out.store("results",ItemStack.CODEC.listOf(),batchResults);out.store("inputs",ItemStack.CODEC.listOf(),batchInputs);
        out.putBoolean("progressive",progressive);out.store("fluid_inputs",FluidInput.CODEC.listOf(),batchFluidInputs);
        out.putBoolean("deferred_inputs",deferredInputs);
        out.store("fluid",FluidStack.OPTIONAL_CODEC,batchFluid);out.putString("mining_fluid",batchMiningFluid);out.putInt("fluid_rate",batchFluidRate);out.putLong("fluid_paid",batchFluidPaid);
    }
    public void load(ValueInput in){
        selected=in.getStringOr("selected","");queued=in.getStringOr("queued","");roundRobin=in.getIntOr("round_robin",0);
        batchSequence=in.getLongOr("sequence",0);batchRecipe=in.getStringOr("recipe","");batchEnergy=in.getLongOr("energy",0);paid=in.getLongOr("paid",0);
        batchDuration=Fraction.parse(in.getStringOr("duration","0"));work=Fraction.parse(in.getStringOr("work","0"));
        batchResults=new ArrayList<>(in.read("results",ItemStack.CODEC.listOf()).orElse(List.of()));batchInputs=new ArrayList<>(in.read("inputs",ItemStack.CODEC.listOf()).orElse(List.of()));
        progressive=in.getBooleanOr("progressive",false);batchFluidInputs=new ArrayList<>(in.read("fluid_inputs",FluidInput.CODEC.listOf()).orElse(List.of()));
        deferredInputs=in.getBooleanOr("deferred_inputs",false);
        batchFluid=in.read("fluid",FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);batchMiningFluid=in.getStringOr("mining_fluid","");batchFluidRate=in.getIntOr("fluid_rate",0);batchFluidPaid=in.getLongOr("fluid_paid",0);
        status=batchRecipe.isEmpty()?"等待原料":"恢复中的加工批次";beginTick();
    }
    public boolean hasDeferredInputs(){try(var tx=Transaction.openRoot()){return takeDeferredInputs(tx);}}
    private boolean takeDeferredInputs(TransactionContext tx){
        for(var need:batchInputs){var resource=net.neoforged.neoforge.transfer.item.ItemResource.of(need);int left=need.getCount();
            for(int i=0;i<machine.device().inputs()&&left>0;i++)left-=(int)machine.items.extract(inputSlot(i),resource,left,tx);
            if(left>0)return false;
        }return true;
    }
    void cancelDeferred(){clearBatch();if(machine.device().selectedRecipe()&&!queued.isEmpty())selected=queued.equals("@auto")?"":queued;queued="";status="原料不足，已取消读条；已耗魔能不退";machine.setChanged();}
}

