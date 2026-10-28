package cn.yumis.fight.modules.equipment.api;
import java.util.*;
public enum Slot {
        WEAPON, CHEST,HEAD,LEGS,FEET,BRACELET,NECKLACE,RING,SUB_EQUIPMENT,MAGIC_STONE,EARRING;
        public boolean armor(){
            return ordinal()>=1&&ordinal()<=4;
        }
        public boolean jewelry(){
            return ordinal()>=5&&ordinal()<=7;
        }
        public int minimum(){
            return switch(this){
                case SUB_EQUIPMENT->50;
                case MAGIC_STONE->60;
                case EARRING->70;
                default->1;
            }
            ;
        }
        public int maximumSockets(){
            return this==WEAPON||armor()?2:1;
        }
        public int materialCost(){
            return switch(this){
                case WEAPON->24;
                case CHEST->12;
                case HEAD,FEET->4;
                case LEGS->8;
                case BRACELET,NECKLACE,RING->3;
                default->5;
            }
            ;
        }
        public String title(){
            return new String[]{
                "武器","胸甲","头盔","护腿","战靴","手镯","项链","戒指","辅助装备","魔法石","耳环"
            }
            [ordinal()];
        }
        public String item(){
            return switch(this){
                case CHEST->"chest_armor";
                case HEAD->"head_armor";
                case LEGS->"legs_armor";
                case FEET->"feet_armor";
                default->name().toLowerCase(Locale.ROOT);
            }
            ;
        }
    }
