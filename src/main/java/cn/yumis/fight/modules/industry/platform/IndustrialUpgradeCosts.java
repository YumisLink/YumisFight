package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.modules.industry.api.DeviceType;
import cn.yumis.fight.modules.industry.api.IndustrialTechnology;
import java.util.*;

/** The authored crafting catalog also supplies on-site upgrade costs. */
final class IndustrialUpgradeCosts {
    private IndustrialUpgradeCosts(){}
    static Map<String,Integer> costs(DeviceType kind,int next){
        var costs=IndustrialTechnology.upgradeCosts(IndustrialContent.spec(kind,next).id());
        if(costs.isEmpty())throw new IllegalArgumentException("此设备没有该档升级配方");return costs;
    }
    static String held(DeviceType kind,int next){return costs(kind,next).keySet().iterator().next();}
}
