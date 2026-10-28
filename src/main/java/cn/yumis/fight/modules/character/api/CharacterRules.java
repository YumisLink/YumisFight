package cn.yumis.fight.modules.character.api;
import java.util.*;

public final class CharacterRules {
    private CharacterRules(){}
    public static final int MAX_LEVEL=80;
    public static int spAt(int level){
        int result=0;
        for(int l=1;l<=Math.clamp(level,1,80);l++)result+=l<20?15:l<30?25:l<40?40:65;
        return result;
    }
    public static long nextExperience(int level){
        return level>=80?0:(10L+2*level)*(20+5L*level);
    }
}
