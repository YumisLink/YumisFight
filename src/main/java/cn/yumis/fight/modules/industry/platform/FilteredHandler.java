package cn.yumis.fight.modules.industry.platform;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import java.util.function.BiPredicate;

/** A stable sided view; permission checks apply to both hints and actual mutation. */
final class FilteredHandler<T extends Resource> implements ResourceHandler<T> {
    private final ResourceHandler<T> inner;private final int[] indices;private final BiPredicate<Integer,T> insert,extract;
    FilteredHandler(ResourceHandler<T> inner,int[] indices,BiPredicate<Integer,T> insert,BiPredicate<Integer,T> extract){this.inner=inner;this.indices=indices;this.insert=insert;this.extract=extract;}
    private boolean valid(int i){return i>=0&&i<indices.length;}
    public int size(){return indices.length;}
    public T getResource(int i){return inner.getResource(indices[i]);}
    public long getAmountAsLong(int i){return valid(i)?inner.getAmountAsLong(indices[i]):0;}
    public long getCapacityAsLong(int i,T r){return valid(i)&&insert.test(indices[i],r)?inner.getCapacityAsLong(indices[i],r):0;}
    public boolean isValid(int i,T r){return valid(i)&&insert.test(indices[i],r)&&inner.isValid(indices[i],r);}
    public int insert(int i,T r,int n,TransactionContext tx){return isValid(i,r)?inner.insert(indices[i],r,n,tx):0;}
    public int extract(int i,T r,int n,TransactionContext tx){return valid(i)&&extract.test(indices[i],r)?inner.extract(indices[i],r,n,tx):0;}
}
