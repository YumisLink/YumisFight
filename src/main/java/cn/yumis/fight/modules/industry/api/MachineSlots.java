package cn.yumis.fight.modules.industry.api;
/** Per-device storage addresses. Existing machines retain their exact persisted indices. */
public record MachineSlots(int inputs,int outputStart,int outputEnd,int crystal,int fuel,int size){
    public boolean input(int slot){return slot>=0&&slot<inputs;}
    public boolean output(int slot){return slot>=outputStart&&slot<outputEnd;}
    public static MachineSlots ordinary(int inputs){return new MachineSlots(inputs,4,13,13,14,15);}
    public static MachineSlots currencyUpgrade(){return new MachineSlots(9,9,18,18,-1,19);}
}
