package cn.yumis.fight.architecture;

import java.util.*;
/** 底层架构文件：通用机制。修改前须用户明确授权，并逐批通过 question tool 确认已 GitHub fetch。 */
public final class ModuleRegistry {
    private final Map<String,ModuleDescriptor> modules;
    private final List<String> order;
    public ModuleRegistry(Collection<ModuleDescriptor> entries){
        Map<String,ModuleDescriptor> copy=new LinkedHashMap<>();
        for(var entry:entries)if(copy.putIfAbsent(entry.id(),entry)!=null)throw new IllegalArgumentException("Duplicate module: "+entry.id());
        List<String> sorted=new ArrayList<>();Set<String> visiting=new HashSet<>(),done=new HashSet<>();
        for(String id:copy.keySet())visit(id,copy,visiting,done,sorted);
        modules=Collections.unmodifiableMap(copy);order=List.copyOf(sorted);
    }
    private static void visit(String id,Map<String,ModuleDescriptor> all,Set<String> visiting,Set<String> done,List<String> order){
        if(done.contains(id))return;
        var entry=all.get(id);if(entry==null)throw new IllegalArgumentException("Missing module: "+id);
        if(!visiting.add(id))throw new IllegalArgumentException("Module dependency cycle at "+id);
        for(String dependency:new TreeSet<>(entry.dependencies()))visit(dependency,all,visiting,done,order);
        visiting.remove(id);done.add(id);order.add(id);
    }
    public List<String> order(){return order;}
    public ModuleDescriptor require(String id){var entry=modules.get(id);if(entry==null)throw new IllegalArgumentException("Unknown module: "+id);return entry;}
}

