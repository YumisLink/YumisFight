package cn.yumis.fight.modules.equipment.api;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.Function;

/** Bounded, weak object identity. Entity.equals compares network IDs across client/server worlds. */
final class WeakIdentityCache<K,V> {
    private static final class IdentityReference<T> extends WeakReference<T> {
        private final int hash;
        IdentityReference(T value,ReferenceQueue<T> queue){super(Objects.requireNonNull(value),queue);hash=System.identityHashCode(value);}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){
            if(this==other)return true;
            if(!(other instanceof IdentityReference<?> reference))return false;
            Object value=get();return value!=null&&value==reference.get();
        }
    }
    private final ReferenceQueue<K> queue=new ReferenceQueue<>();
    private final LinkedHashMap<IdentityReference<K>,V> values=new LinkedHashMap<>(16,.75f,true);
    private final int limit;
    WeakIdentityCache(int limit){if(limit<1)throw new IllegalArgumentException("Cache limit must be positive");this.limit=limit;}
    private void expunge(){java.lang.ref.Reference<? extends K> stale;while((stale=queue.poll())!=null)values.remove(stale);}
    V get(K key){expunge();return values.get(new IdentityReference<>(key,null));}
    V computeIfAbsent(K key,Function<? super K,? extends V> factory){
        expunge();var lookup=new IdentityReference<>(key,null);V existing=values.get(lookup);if(existing!=null)return existing;
        V created=Objects.requireNonNull(factory.apply(key));values.put(new IdentityReference<>(key,queue),created);
        while(values.size()>limit)values.remove(values.keySet().iterator().next());return created;
    }
    V remove(K key){expunge();return values.remove(new IdentityReference<>(key,null));}
    void clear(){values.clear();while(queue.poll()!=null){/* discard references from the old generation */}}
    int size(){expunge();return values.size();}
    Collection<V> values(){expunge();return Collections.unmodifiableCollection(values.values());}
}
