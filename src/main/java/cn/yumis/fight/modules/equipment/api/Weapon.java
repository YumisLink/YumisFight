package cn.yumis.fight.modules.equipment.api;
import java.util.*;
public enum Weapon {
        KATANA,LIGHTSABER,GREATSWORD,SHORTSWORD,BLUNT,CLUB,STAFF,WAND,BROOM,WARGLAIVE,SPEAR;
        public static java.util.List<Weapon> standard(){return java.util.List.of(KATANA,LIGHTSABER,GREATSWORD,SHORTSWORD,BLUNT,STAFF,WAND,CLUB,BROOM,WARGLAIVE,SPEAR);}
        public String title(){
            return new String[]{
                "太刀","光剑","巨剑","短剑","钝器","棍棒","法杖","魔杖","扫把","战刃","矛"
            }
            [ordinal()];
        }
    }
