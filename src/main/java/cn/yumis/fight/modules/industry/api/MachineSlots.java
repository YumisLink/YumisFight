package cn.yumis.fight.modules.industry.api;

import java.util.stream.IntStream;

/** Industry-owned per-line addresses; upgrading unlocks reserved lines without moving inventory. */
public record MachineSlots(int inputs,int outputOffset,int outputs,int stride,int lineCount,int maximumLines,int crystal,int fuel,int size){
    public static final int MAXIMUM_LINES=16;
    public int lineOf(int slot){return slot>=0&&slot<maximumLines*stride?slot/stride:-1;}
    public int offset(int slot){return lineOf(slot)<0?-1:slot%stride;}
    public boolean input(int slot){int line=lineOf(slot);return line>=0&&line<lineCount&&offset(slot)<inputs;}
    public boolean output(int slot){int line=lineOf(slot),offset=offset(slot);return line>=0&&line<lineCount&&offset>=outputOffset&&offset<outputOffset+outputs;}
    public int inputStart(int line){return line*stride;}
    public int outputStart(int line){return line*stride+outputOffset;}
    public int outputEnd(int line){return outputStart(line)+outputs;}
    public int[] inputSlots(){return IntStream.range(0,lineCount).flatMap(line->IntStream.range(inputStart(line),inputStart(line)+inputs)).toArray();}
    public int[] outputSlots(){return IntStream.range(0,lineCount).flatMap(line->IntStream.range(outputStart(line),outputEnd(line))).toArray();}
    public static MachineSlots ordinary(int inputs,int lines,boolean parallel){
        int maximum=parallel?MAXIMUM_LINES:1,crystal=maximum*13;
        if(lines<1||lines>maximum)throw new IllegalArgumentException("无效工业产线数");
        return new MachineSlots(inputs,4,9,13,lines,maximum,crystal,crystal+1,crystal+2);
    }
    public static MachineSlots currencyUpgrade(){return new MachineSlots(9,9,9,18,1,1,18,-1,19);}
}
