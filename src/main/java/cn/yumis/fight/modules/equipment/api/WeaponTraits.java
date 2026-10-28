package cn.yumis.fight.modules.equipment.api;
import cn.yumis.fight.modules.attributes.api.Stat;
import java.util.*;
public final class WeaponTraits {
    public record Basic(double range,double projectileSpeed,boolean magic){}
    public static Basic basic(String weapon){return weapon.equals("STAFF")?new Basic(12,20,true):new Basic(sword(weapon)?4.5:3,0,false);}
    private WeaponTraits(){}
    public static double attackScale(Weapon weapon,Stat stat){
        boolean physical=stat==Stat.PHYSICAL_ATTACK;
        return switch(weapon){case KATANA->physical?.8:1;case LIGHTSABER->physical?1:.8;case SHORTSWORD->physical?.8:1.4;case BLUNT->1.2;case GREATSWORD->physical?1.4:.8;case CLUB->1;case STAFF->physical?.8:1.4;};
    }
    public static double speed(String weapon){return switch(weapon){case "KATANA","CLUB"->10;case "LIGHTSABER"->20;case "GREATSWORD"->-10;default->0;};}
    public static boolean sword(String weapon){return Set.of("KATANA","LIGHTSABER","SHORTSWORD","GREATSWORD").contains(weapon);}
}
