package cn.yumis.fight.modules.combat.api;
import static cn.yumis.fight.modules.attributes.api.AttributeNumbers.finite;

import java.util.*;

/** combat模块拥有的数据合同；仅接收当前存档代际。 */
public class Runtime {
    public transient cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot equipment=new cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot();
    public Map<String,EquipmentBuffs.State> equipmentBuffs=new LinkedHashMap<>();
    public double mp=-1,shield=-1,trainingBoundary=-1;
    public long combatUntil,shieldRecoveryAt,recordedAt,controlEpoch;
    public boolean training;
    public Map<String,Long> cooldowns=new HashMap<>(),buffs=new HashMap<>(),absorptionUntil=new HashMap<>();
    public Map<String,Double> absorption=new HashMap<>();
    public void validate(){
        finite(mp);
        finite(shield);
        if(mp<0||shield<0)throw new IllegalArgumentException("无效资源");
        for(double x:absorption.values()){
            finite(x);
            if(x<0)throw new IllegalArgumentException("无效护盾");
        }
    }
}
