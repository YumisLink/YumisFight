package cn.yumis.fight.modules.equipment.api;
import java.util.*;

public final class EquipmentCurves {
    private EquipmentCurves(){}
    public static double[] defenses(int level,boolean magical){
        double total=(magical?40:80)+(magical?10:15)*(level-1);
        double[] weights={
            .4,.15,.3,.15
        }
        ,out=new double[4];
        int used=0;
        for(int i=0;i<4;i++){
            out[i]=Math.floor(total*weights[i]);
            used+= (int)out[i];
        }
        int[] order={
            0,2,1,3
        }
        ;
        for(int i=0;i<(int)total-used;i++)out[order[i%4]]++;
        return out;
    }
}
