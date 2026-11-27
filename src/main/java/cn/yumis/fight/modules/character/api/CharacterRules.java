package cn.yumis.fight.modules.character.api;
import java.util.*;

public final class CharacterRules {
    private CharacterRules(){}
    public static final int MAX_LEVEL=80;
    public static int maxLevel(){return CharacterBalanceRules.current().maxLevel;}
    public static int spAt(int level){
        var rules=CharacterBalanceRules.current();
        int result=0;
        for(int l=1;l<=Math.clamp(level,1,rules.maxLevel);l++)result+=rules.spForLevel(l);
        return result;
    }
    public static long nextExperience(int level){
        var rules=CharacterBalanceRules.current();
        return level>=rules.maxLevel?0:(rules.experienceBase+rules.experiencePerLevel*level)*(rules.experienceFactorBase+rules.experienceFactorPerLevel*level);
    }
    /** 新手经验：10级内（含10级）战斗击杀经验翻倍，超过10级恢复正常。 */
    public static final int NEWBIE_EXPERIENCE_MAX_LEVEL=10;
    public static double newbieExperienceFactor(int level){
        var rules=CharacterBalanceRules.current();
        return level<=rules.newbieExperienceMaxLevel?rules.newbieExperienceFactor:1;
    }
}
