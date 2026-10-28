package cn.yumis.fight.architecture;

import java.util.*;
/** 底层架构文件：通用机制。修改前须用户明确授权，并逐批通过 question tool 确认已 GitHub fetch。 */
public final class CommandRouter<C,P> {
    @FunctionalInterface public interface Handler<C,P>{void handle(C context,P payload)throws Exception;}
    private final Map<String,Handler<C,P>> handlers=new LinkedHashMap<>();
    private final Map<String,String> owners=new LinkedHashMap<>();
    private boolean sealed;
    public void register(String owner,String action,Handler<C,P> handler){
        if(sealed)throw new IllegalStateException("Router is sealed");
        Objects.requireNonNull(owner);Objects.requireNonNull(action);Objects.requireNonNull(handler);
        if(handlers.putIfAbsent(action,handler)!=null)throw new IllegalArgumentException("Duplicate command: "+action);
        owners.put(action,owner);
    }
    public void seal(){sealed=true;}
    public boolean dispatch(C context,String action,P payload)throws Exception{
        if(!sealed)throw new IllegalStateException("Router has not been sealed");
        var handler=handlers.get(action);if(handler==null)return false;handler.handle(context,payload);return true;
    }
    public Map<String,String> owners(){return Collections.unmodifiableMap(owners);}
}

