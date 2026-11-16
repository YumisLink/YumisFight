package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.modules.industry.api.EnergyMath;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.*;
import java.util.*;

/** Loaded pipe component. Fluid storage and rollback remain in the member quantity stores. */
final class FluidPipeNetwork {
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    final List<FluidPipeBlockEntity> members;
    final long capacity;
    private long lastTick=Long.MIN_VALUE,lastError=Long.MIN_VALUE;
    private int cursor;
    private FluidResource intakeType=FluidResource.EMPTY;
    private FluidPipeNetwork(List<FluidPipeBlockEntity> members){this.members=members;capacity=members.stream().mapToLong(p->p.grade().capacity()).sum();members.forEach(p->p.network=this);}
    static FluidPipeNetwork collect(FluidPipeBlockEntity root){
        var members=new ArrayList<FluidPipeBlockEntity>();var seen=new HashSet<FluidPipeBlockEntity>();var queue=new ArrayDeque<FluidPipeBlockEntity>();queue.add(root);seen.add(root);
        FluidResource type=root.contents.getResource(0);
        while(!queue.isEmpty()){
            var pipe=queue.removeFirst();members.add(pipe);
            for(Direction side:Direction.values()){
                if(!pipe.linked(side))continue;var next=pipe.neighbor(side);if(next==null||!next.linked(side.getOpposite()))continue;
                var fluid=next.network==null?next.contents.getResource(0):next.network.resource();
                // A sleeping branch may return with its old liquid after the active side changed type.
                // Keep the saved physical link; incompatible loaded fluid components wait separately.
                if(!type.isEmpty()&&!fluid.isEmpty()&&!type.equals(fluid))continue;
                if(!fluid.isEmpty())type=fluid;
                if(seen.add(next)){if(next.network!=null)next.invalidateNetwork();queue.add(next);}
            }
        }
        members.sort(Comparator.comparingLong(p->p.getBlockPos().asLong()));return new FluidPipeNetwork(members);
    }
    FluidResource resource(){for(var pipe:members){var fluid=pipe.contents.getResource(0);if(!fluid.isEmpty())return fluid;}return FluidResource.EMPTY;}
    long amount(){long result=0;for(var pipe:members)result=Math.addExact(result,pipe.contents.getAmountAsLong(0));return result;}
    boolean compatible(FluidPipeNetwork other){var a=resource();var b=other.resource();return a.isEmpty()||b.isEmpty()||a.equals(b);}
    boolean accepts(FluidResource fluid){var stored=resource();return !fluid.isEmpty()&&(stored.isEmpty()||stored.equals(fluid))&&(intakeType.isEmpty()||intakeType.equals(fluid));}
    int insert(FluidResource fluid,int amount,TransactionContext tx){
        if(!accepts(fluid))return 0;int moved=0;
        for(var pipe:members){moved+=pipe.contents.insert(0,fluid,amount-moved,tx);if(moved==amount)break;}return moved;
    }
    int extract(FluidResource fluid,int amount,TransactionContext tx){int moved=0;for(var pipe:members){moved+=pipe.contents.extract(0,fluid,amount-moved,tx);if(moved==amount)break;}return moved;}
    private static void distribute(List<FluidPipeBlockEntity> pipes,FluidResource fluid,long amount,int cursor){
        long[] capacities=pipes.stream().mapToLong(p->p.grade().capacity()).toArray();var grants=EnergyMath.allocate(amount,capacities,cursor).grants();
        for(int i=0;i<pipes.size();i++)if(pipes.get(i).contents.getAmountAsLong(0)!=grants[i]||!pipes.get(i).contents.getResource(0).equals(grants[i]==0?FluidResource.EMPTY:fluid))pipes.get(i).contents.set(0,fluid,grants[i]);
    }
    private void balance(){distribute(members,resource(),amount(),cursor);}
    void invalidate(){balance();for(var pipe:members)if(pipe.network==this)pipe.network=null;}
    void remove(FluidPipeBlockEntity removed){
        FluidResource fluid=resource();long amount=amount();var survivors=members.stream().filter(p->p!=removed).toList();
        long remainingCapacity=capacity-removed.grade().capacity();distribute(survivors,fluid,Math.min(amount,remainingCapacity),cursor);
        removed.contents.set(0,FluidResource.EMPTY,0);for(var pipe:members)if(pipe.network==this)pipe.network=null;
    }
    private record Endpoint(FluidPipeBlockEntity pipe,Direction side,ResourceHandler<FluidResource> machine){}
    private List<Endpoint> endpoints(boolean input){
        var result=new ArrayList<Endpoint>();
        for(var pipe:members)for(Direction side:Direction.values()){
            if(pipe.mode(side)!=(input?FluidPipeBlockEntity.Port.EXTRACT:FluidPipeBlockEntity.Port.INPUT)||pipe.remaining(side)<=0||(pipe.machineMask()&FluidPipeBlock.bit(side))==0)continue;
            var machine=pipe.machine(side);if(machine!=null&&machine.size()>0)result.add(new Endpoint(pipe,side,machine));
        }
        return result;
    }
    private long move(Endpoint endpoint,boolean input,int amount,boolean commit){
        var port=endpoint.pipe.automation(endpoint.side);
        try(var tx=Transaction.openRoot()){
            long moved=CrystalTransfers.move(input?endpoint.machine:port,input?port:endpoint.machine,amount,cursor,tx);
            if(commit)tx.commit();return moved;
        }catch(IllegalStateException failure){
            // A source may advertise stock reserved by its own production batch; rollback both ends.
            long now=endpoint.pipe.getLevel().getGameTime();if(lastError==Long.MIN_VALUE||now-lastError>=20){lastError=now;LOG.warn("液体管道端口本次运输回滚：{} {}",endpoint.pipe.getBlockPos(),endpoint.side,failure);}return 0;
        }
    }
    /** Weighted allocation with saturation: a nearly full target must not distort other port weights. */
    private long[] allocation(long available,long[] demands,long[] weights){
        long[] grants=new long[demands.length];long remaining=available;
        while(remaining>0){
            long[] active=new long[weights.length];boolean any=false;for(int i=0;i<active.length;i++)if(grants[i]<demands[i]){active[i]=weights[i];any=true;}
            if(!any)break;var share=EnergyMath.allocate(remaining,active,cursor).grants();long spent=0;
            for(int i=0;i<share.length;i++){long accepted=Math.min(share[i],demands[i]-grants[i]);grants[i]+=accepted;spent+=accepted;}
            if(spent==0)break;remaining-=spent;
        }
        return grants;
    }
    private void transfer(boolean input){
        var endpoints=endpoints(input);if(endpoints.isEmpty())return;
        if(input&&resource().isEmpty()){
            // Choose the first resource that can actually be extracted, without committing the probe.
            for(int i=0;i<endpoints.size()&&intakeType.isEmpty();i++){
                var endpoint=endpoints.get(Math.floorMod(i+cursor,endpoints.size()));var port=endpoint.pipe.automation(endpoint.side);
                try(var tx=Transaction.openRoot()){
                    if(CrystalTransfers.move(endpoint.machine,port,1,cursor,tx)>0)intakeType=resource();
                }catch(IllegalStateException ignored){/* This source's visible stock is reserved. */}
            }
            if(intakeType.isEmpty())return;
        }
        long[] demands=new long[endpoints.size()],weights=new long[endpoints.size()];
        for(int i=0;i<demands.length;i++){var endpoint=endpoints.get(i);weights[i]=endpoint.pipe.grade().flow();demands[i]=move(endpoint,input,endpoint.pipe.remaining(endpoint.side),false);}
        var grants=allocation(input?Math.max(0,capacity-amount()):amount(),demands,weights);
        for(int i=0;i<grants.length;i++)if(grants[i]>0)move(endpoints.get(i),input,(int)grants[i],true);
        intakeType=FluidResource.EMPTY;
    }
    void tick(){
        long now=members.getFirst().getLevel().getGameTime();if(lastTick==now)return;lastTick=now;
        for(var pipe:members)if(now%20==0||pipe.machineMask()==0)pipe.refreshShape();
        // Full buffers may make room first. Both output passes spend the same per-port tick budget.
        transfer(false);transfer(true);transfer(false);balance();cursor=(cursor+1)&0x7fffffff;
        var fluid=resource();long stored=amount();for(var pipe:members)pipe.sync(fluid,stored,capacity,false);
        // Only already-saved links resume here. Explicitly closed/conflicting placement links stay off.
        for(var pipe:members)for(Direction side:Direction.values())if(pipe.linked(side)){
            var next=pipe.neighbor(side);if(next==null||!next.linked(side.getOpposite())||next.network==null||next.network==this)continue;
            if(compatible(next.network)){var other=next.network;invalidate();other.invalidate();return;}
        }
    }
}
