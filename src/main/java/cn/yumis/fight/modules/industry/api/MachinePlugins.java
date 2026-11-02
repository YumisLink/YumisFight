package cn.yumis.fight.modules.industry.api;

import java.util.*;

/** Industry-owned plugin definitions. Installed stack limits are independent of inventory stack limits. */
public final class MachinePlugins {
    public enum Effect { STORAGE, SPEED }
    public record Definition(String id,int installedStackLimit,Effect effect,Set<String> deviceIds) {
        public Definition {if(id==null||!id.matches("[a-z][a-z0-9_]*")||effect==null||installedStackLimit<1||installedStackLimit>64)throw new IllegalArgumentException("无效插件定义");deviceIds=Set.copyOf(deviceIds);}
        public boolean accepts(DeviceType device){
            if(!deviceIds.isEmpty()&&!deviceIds.contains(device.id))return false;
            return switch(effect){case SPEED->device.processing()||device.functional()||device.currencyUpgrader();case STORAGE->device.inputs()>0||device.outputs()||device.has(DeviceType.Capability.FLUID_PROCESSING)||device.has(DeviceType.Capability.FLUID_FUEL)||device.has(DeviceType.Capability.FLUID_STORAGE);};
        }
    }
    private static final Map<String,Definition> DEFINITIONS=new LinkedHashMap<>();
    static {register(new Definition("storage_plugin",8,Effect.STORAGE,Set.of()));register(new Definition("speed_plugin",8,Effect.SPEED,Set.of()));}
    private MachinePlugins(){}
    public static void register(Definition definition){
        // Three full slots must remain representable by the integer energy contract.
        if(definition.effect()==Effect.SPEED&&definition.installedStackLimit()*3>35||definition.effect()==Effect.STORAGE&&definition.installedStackLimit()*3>42)
            throw new IllegalArgumentException("插件最大组合超出工业整数范围");
        if(DEFINITIONS.putIfAbsent(definition.id(),definition)!=null)throw new IllegalArgumentException("重复插件ID");
    }
    public static Definition find(String id){return DEFINITIONS.get(id);}
    public static Collection<Definition> all(){return Collections.unmodifiableCollection(DEFINITIONS.values());}
    public static long powerOfTwo(int count){if(count<0||count>62)throw new IllegalArgumentException("插件倍率溢出");return 1L<<count;}
    public static Fraction speed(int count){Fraction value=Fraction.of(1);for(int i=0;i<count;i++)value=value.multiply(3).divide(2);return value;}
}
