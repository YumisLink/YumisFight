package cn.yumis.fight.modules.equipment.api;
import java.util.*;
public enum ArmorType {
        UNTYPED("通用"),CLOTH("布甲"),LEATHER("皮甲"),LIGHT("轻甲"),HEAVY("重甲"),PLATE("板甲");
        private final String title;
        ArmorType(String title){this.title=title;}
        public String title(){return title;}
    }
