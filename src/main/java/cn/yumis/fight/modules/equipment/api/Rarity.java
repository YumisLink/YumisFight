package cn.yumis.fight.modules.equipment.api;
import java.util.*;
public enum Rarity {
        COMMON,RARE,ARTIFACT,LEGENDARY,EPIC,MYTHIC;
        public int randomCount(){
            return switch(this){
                case RARE->1;
                case ARTIFACT->2;
                case LEGENDARY->4;
                default->0;
            }
            ;
        }
        public String title(){
            return new String[]{
                "普通","稀有","神器","传说","史诗","神话"
            }
            [ordinal()];
        }
        public int color(){
            return new int[]{
                0xffffff,0xb36bff,0xff00f0,0xff8000,0xffb100,0xffd778
            }
            [ordinal()];
        }
    }
