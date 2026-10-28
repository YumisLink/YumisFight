package cn.yumis.fight.modules.dungeons.api;
import java.util.*;
public enum Difficulty {
        NORMAL(1,1,1),ADVANCED(1.6,1.25,1.2),WARRIOR(2.5,1.6,1.45),KING(4,2.1,1.8),HELL(6,3,2.2);
        public final double health,attack,xp;
        Difficulty(double h,double a,double x){
            health=h;
            attack=a;
            xp=x;
        }
        public String title(){
            return new String[]{
                "普通","冒险","勇士","王者","地狱"
            }
            [ordinal()];
        }
    }
