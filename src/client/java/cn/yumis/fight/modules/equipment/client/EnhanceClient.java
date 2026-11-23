package cn.yumis.fight.modules.equipment.client;

import cn.yumis.fight.modules.equipment.api.EquipmentChanges;

/** 强化/增幅判定的本机回执：装备与费用由本人客户端判定一次，界面据此演出结果。 */
public final class EnhanceClient {
    public record Report(String operation,EquipmentChanges.EnhanceOutcome outcome,boolean rejected){}
    private static Report last;
    private EnhanceClient(){}
    public static void dispatched(String operation,EquipmentChanges.EnhanceOutcome outcome){last=new Report(operation,outcome,false);}
    public static void failed(String operation){last=new Report(operation,null,true);}
    /** 取走指定操作的回执；没有匹配回执时返回 null。 */
    public static Report take(String operation){
        if(last==null||!operation.equals(last.operation()))return null;
        var report=last;last=null;return report;
    }
    public static void clear(){last=null;}
}
