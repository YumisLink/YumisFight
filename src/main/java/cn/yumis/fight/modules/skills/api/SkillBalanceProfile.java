package cn.yumis.fight.modules.skills.api;

import java.util.List;

/** Each content registration supplies its own actual hit schedule, including persistent tails. */
public record SkillBalanceProfile(double weight,double period,boolean redCoverage,boolean fullRed,double personalMut,Pattern pattern) {
    public record Context(String job,String weapon,boolean mastery){}
    public record Hit(long at,double coefficient,boolean passive) {
        public Hit {if(at<0||coefficient<0||!Double.isFinite(coefficient))throw new IllegalArgumentException("Invalid reference hit");}
    }
    @FunctionalInterface public interface Pattern {List<Hit> hits(SkillDefinition skill,int rank,Context context);}
    public SkillBalanceProfile {if(weight<=0||!Double.isFinite(weight)||period<0||!Double.isFinite(period)||pattern==null)throw new IllegalArgumentException("Invalid balance profile");}
}
