package cn.yumis.fight.modules.equipment.api;
import java.util.*;
public enum JewelryType {
        UNTYPED("通用"),HEALTH("生命"),MP("魔力"),STRENGTH("力量"),INTELLIGENCE("智力"),SPIRIT("精神"),LIGHT_RESIST("御光"),FIRE_RESIST("耐火"),ICE_RESIST("御寒"),DARK_RESIST("辟暗"),ALL_RESIST("庇护"),DROP_RATE("寻宝");
        private final String title;
        JewelryType(String title){this.title=title;}
        public String title(){return title;}
    }
