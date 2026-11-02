package cn.yumis.fight.modules.industry.platform;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import java.util.function.BiPredicate;
import java.util.function.ToLongFunction;

/** A stable sided view; permission checks apply to both hints and actual mutation. */
final class FilteredHandler<T extends Resource> implements ResourceHandler<T> {
    private final ResourceHandler<T> inner;private final int[] indices;private final BiPredicate<Integer,T> insert,extract;
    private final ToLongFunction<Integer> priority;private final int partitions;
    FilteredHandler(ResourceHandler<T> inner,int[] indices,BiPredicate<Integer,T> insert,BiPredicate<Integer,T> extract,ToLongFunction<Integer> priority,int partitions){this.inner=inner;this.indices=indices;this.insert=insert;this.extract=extract;this.priority=priority;this.partitions=Math.max(1,partitions);}
    private boolean valid(int i){return i>=0&&i<indices.length;}
    public int size(){return indices.length;}
    public T getResource(int i){return inner.getResource(indices[i]);}
    public long getAmountAsLong(int i){return valid(i)?inner.getAmountAsLong(indices[i]):0;}
    public long getCapacityAsLong(int i,T r){return valid(i)&&insert.test(indices[i],r)?inner.getCapacityAsLong(indices[i],r):0;}
    public boolean isValid(int i,T r){return valid(i)&&insert.test(indices[i],r)&&inner.isValid(indices[i],r);}
    public int insert(int i,T r,int n,TransactionContext tx){return isValid(i,r)?inner.insert(indices[i],r,n,tx):0;}
    @Override public int insert(T resource,int amount,TransactionContext tx){
        if(priority==null)return ResourceHandler.super.insert(resource,amount,tx);
        int inserted=0;
        while(inserted<amount){
            int best=-1;long load=Long.MAX_VALUE;
            for(int slot=0;slot<size();slot++){
                if(!isValid(slot,resource)||!getResource(slot).isEmpty()&&!getResource(slot).equals(resource)||getCapacityAsLong(slot,resource)<=getAmountAsLong(slot))continue;
                long candidate=priority.applyAsLong(indices[slot]);if(best<0||candidate<load){best=slot;load=candidate;}
            }
            if(best<0)break;
            int portion=(int)Math.max(1,((long)amount-inserted+partitions-1)/partitions);
            int moved=insert(best,resource,portion,tx);if(moved==0)break;inserted+=moved;
        }
        return inserted;
    }
    public int extract(int i,T r,int n,TransactionContext tx){return valid(i)&&extract.test(indices[i],r)?inner.extract(indices[i],r,n,tx):0;}
}
