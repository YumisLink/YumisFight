package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.modules.industry.api.DeviceType;
import java.util.*;

/** On-site upgrades omit only the preceding machine item from its workbench recipe. */
final class IndustrialUpgradeCosts {
    private IndustrialUpgradeCosts(){}
    static Map<String,Integer> costs(DeviceType kind,int next){
        var items=new LinkedHashMap<String,Integer>();
        if(kind==DeviceType.TANK){
            if(next==2){items.put("vanadium_steel",4);items.put("zircon_ceramic",4);}
            else {items.put("mithril_niobium_alloy",4);items.put("lattice_substrate",2);items.put("energy_crystal_4",2);}
        }else if(kind==DeviceType.ACCUMULATOR){
            items.put(next==2?"reinforced_frame":next==3?"heatproof_frame":"precision_frame",1);
            if(next==2){items.put("vanadium_steel",3);items.put("energy_crystal_2",4);}
            else if(next==3){items.put("industrial_magic_stone",3);items.put("energy_crystal_4",4);}
            else {items.put("mana_superconductor",2);items.put("superconducting_control",1);items.put("energy_crystal_5",4);}
        }else {
            items.put("industrial_upgrade_"+(kind.graded?next:next==2?2:kind==DeviceType.HARVEST?4:5),1);
            if(kind==DeviceType.MINER){items.put(next==2?"basic_frame":next==3?"reinforced_frame":next==4?"heatproof_frame":"precision_frame",1);items.put(next==5?"mithril_mana_component":"mana_component",1);if(next==5)items.put("control_component",1);}
            if(kind==DeviceType.FLUID_TRANSFER){if(next==2){items.put("reinforced_frame",1);items.put("mana_component",2);items.put("minecraft:glass",4);}else{items.put("mithril_mana_component",2);items.put("control_component",1);items.put("zircon_ceramic",4);}}
        }
        return items;
    }
    static String held(DeviceType kind,int next){return costs(kind,next).keySet().iterator().next();}
}
