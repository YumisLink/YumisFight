package cn.yumis.fight.modules.industry.platform;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.storage.*;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.*;
import java.util.*;

/** A transaction-aware industrial quantity store; ordinary stacks are only transfer/display samples. */
abstract class IndustrialResourceStore<R extends Resource> extends SnapshotJournal<List<IndustrialResourceStore.Stored<R>>> implements ResourceHandler<R> {
    record Stored<R>(R resource,long amount){}
    private final List<Stored<R>> cells;
    private final R empty;
    private final Codec<List<Stored<R>>> codec;
    IndustrialResourceStore(int size,R empty,Codec<R> resourceCodec){
        this.empty=empty;cells=new ArrayList<>(Collections.nCopies(size,new Stored<>(empty,0)));
        Codec<Stored<R>> entry=RecordCodecBuilder.create(i->i.group(resourceCodec.fieldOf("resource").forGetter(Stored<R>::resource),Codec.LONG.validate(amount->amount>=0?com.mojang.serialization.DataResult.success(amount):com.mojang.serialization.DataResult.error(()->"负储量")).fieldOf("amount").forGetter(Stored<R>::amount)).apply(i,Stored<R>::new));
        codec=entry.listOf();
    }
    public int size(){return cells.size();}
    public R getResource(int slot){return cells.get(slot).resource();}
    public long getAmountAsLong(int slot){return cells.get(slot).amount();}
    protected abstract void changed(int slot);
    private void write(int slot,R resource,long amount){if(amount<0||resource.isEmpty()&&amount!=0)throw new IllegalArgumentException("无效工业储量");cells.set(slot,new Stored<>(amount==0?empty:resource,amount));}
    public void set(int slot,R resource,long amount){write(slot,resource,amount);changed(slot);}
    public int insert(int slot,R resource,int amount,TransactionContext transaction){
        if(amount<0||resource.isEmpty())throw new IllegalArgumentException("无效插入");
        if(!isValid(slot,resource)||!getResource(slot).isEmpty()&&!getResource(slot).equals(resource))return 0;
        int accepted=(int)Math.min(amount,Math.max(0,getCapacityAsLong(slot,resource)-getAmountAsLong(slot)));
        if(accepted>0){updateSnapshots(transaction);write(slot,resource,Math.addExact(getAmountAsLong(slot),accepted));}return accepted;
    }
    @Override public int insert(R resource,int amount,TransactionContext transaction){
        int moved=0;
        for(int pass=0;pass<2&&moved<amount;pass++)for(int slot=0;slot<size()&&moved<amount;slot++)
            if(pass==0?getResource(slot).equals(resource):getResource(slot).isEmpty())moved+=insert(slot,resource,amount-moved,transaction);
        return moved;
    }
    public int extract(int slot,R resource,int amount,TransactionContext transaction){
        if(amount<0||resource.isEmpty())throw new IllegalArgumentException("无效取出");
        if(!getResource(slot).equals(resource))return 0;int taken=(int)Math.min(amount,getAmountAsLong(slot));
        if(taken>0){updateSnapshots(transaction);write(slot,resource,getAmountAsLong(slot)-taken);}return taken;
    }
    protected List<Stored<R>> createSnapshot(){return new ArrayList<>(cells);}
    protected void revertToSnapshot(List<Stored<R>> snapshot){for(int i=0;i<size();i++)cells.set(i,snapshot.get(i));}
    @Override protected void onRootCommit(List<Stored<R>> previous){for(int i=0;i<size();i++)if(!cells.get(i).equals(previous.get(i)))changed(i);}
    public void serialize(ValueOutput output){output.store("cells",codec,new ArrayList<>(cells));}
    public void deserialize(ValueInput input){List<Stored<R>> values=input.read("cells",codec).orElse(List.of());for(int i=0;i<size();i++){Stored<R> value=i<values.size()?values.get(i):new Stored<>(empty,0);set(i,value.resource(),value.amount());}}
}
