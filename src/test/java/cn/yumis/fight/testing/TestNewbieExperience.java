package cn.yumis.fight.testing;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.character.api.CharacterRules;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.dungeons.api.PracticeRoom;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.economy.api.RewardRules;
import cn.yumis.fight.modules.economy.api.RewardSettings;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 新手经验加成：10级内（含）战斗击杀经验翻倍，超过10级恢复正常；服务器倍率、经验词条、引路石与练功房系数照常叠乘。 */
class TestNewbieExperience {
    private static Stats stats(double experienceGain){
        var stats=new Stats();
        stats.values.put(Stat.EXPERIENCE_GAIN,experienceGain);
        return stats;
    }
    private static Progress progress(int level){
        var p=new Progress();
        p.level=level;
        return p;
    }
    private static long kill(int playerLevel,int targetLevel){
        return RewardRules.killExperience(progress(playerLevel),targetLevel,new Target(),new RewardSettings(1,1),stats(1),1,false);
    }
    @Test void factorDoublesAtOrBelowTenAndNormalizesAfter(){
        for(int level=1;level<=CharacterRules.NEWBIE_EXPERIENCE_MAX_LEVEL;level++)assertEquals(2,CharacterRules.newbieExperienceFactor(level),"等级 "+level+" 须翻倍");
        for(int level=CharacterRules.NEWBIE_EXPERIENCE_MAX_LEVEL+1;level<=CharacterRules.MAX_LEVEL;level++)assertEquals(1,CharacterRules.newbieExperienceFactor(level),"等级 "+level+" 须恢复正常");
    }
    @Test void killExperienceDoublesUnderTenAndNormalizesAfter(){
        assertEquals(60,kill(10,10),"10级击杀同等级普通怪：(10＋2×10)×2＝60");
        assertEquals(32,kill(11,11),"11级击杀同等级普通怪：(10＋2×11)×1＝32，恢复正常");
        assertEquals(28,kill(1,2),"1级击杀2级怪：(10＋4)×2＝28");
    }
    @Test void newcomerDoublingStacksWithConfigStatStoneAndPractice(){
        var target=new Target();
        assertEquals(90,RewardRules.killExperience(progress(10),10,target,new RewardSettings(1,1),stats(1.5),1,false),"10级：基础30×词条1.5×新手2＝90");
        assertEquals(48,RewardRules.killExperience(progress(11),11,target,new RewardSettings(1,1),stats(1.5),1,false),"11级：基础32×词条1.5＝48，恢复正常");
        assertEquals(240,RewardRules.killExperience(progress(10),10,target,new RewardSettings(2,1),stats(1),2,false),"10级：基础30×配置2×引路石2×新手2＝240");
        assertEquals(14,RewardRules.killExperience(progress(9),9,target,new RewardSettings(1,1),stats(1),1,true),"9级练功房小怪：基础28×新手2×练功房1/4＝14");
    }
    @Test void displayHintFollowsConfigStatAndNewcomerRule(){
        assertEquals(2,RewardRules.experienceMultiplier(1,stats(1),10),0,"10级显示＋100%");
        assertEquals(1,RewardRules.experienceMultiplier(1,stats(1),11),0,"11级显示＋0%");
        assertEquals(3,RewardRules.experienceMultiplier(1.5,stats(1),10),0,"配置1.5在10级显示＋200%");
        assertEquals(1.5,RewardRules.experienceMultiplier(1.5,stats(1),11),0,"配置1.5在11级显示＋50%");
        assertEquals(2.5,RewardRules.experienceMultiplier(1,stats(1.25),10),0,"词条1.25在10级显示＋150%");
    }
}
