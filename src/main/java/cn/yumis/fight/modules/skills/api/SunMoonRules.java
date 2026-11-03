package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.character.api.*;

/** The same pure heart, cost and cast arithmetic is used by preview and accepted casts. */
public final class SunMoonRules {
    private static final double[] SUN_DAMAGE={0,.5,1.3,2.5},MOON_REGEN={0,1,10,100},REVERSE_SPEED={0,.3,.8,2};
    private SunMoonRules(){}
    public static double sunDamage(int rank,int stacks){return rank<=0?0:SUN_DAMAGE[Math.clamp(stacks,0,3)]*Math.pow(1.12,rank-1);}
    public static int moonHearts(int rank){return rank>=10?3:rank>=5?2:1;}
    public static int refluxHarmony(int rank){return 3+(rank>=5?1:0)+(rank>=10?1:0);}
    public static String unavailableReason(SkillDefinition skill,int heart,int stacks){
        if(skill.id.equals(SunMoonSkills.CORONA)&&(heart!=1||stacks<1))return "需要拥有至少一层日曜心才能释放日冕";
        if(skill.id.equals(SunMoonSkills.STARFALL)&&(heart!=2||stacks<1))return "需要拥有月弦心才能释放月见星辉";
        return "";
    }
    public static Stats current(Stats source,Progress progress,SunMoonState state){
        if(!progress.hasJob(JobCatalog.SUN_MOON))return source;var result=source.copy();
        result.modifiers.put("sunMoonHeart",(double)state.heart);result.modifiers.put("sunMoonStacks",(double)state.stacks);result.modifiers.put("sunMoonHarmony",(double)state.harmony);result.modifiers.put("sunMoonParadox",(double)state.paradox);
        result.modifiers.put("sunMoonCycleRank",(double)SkillLearning.effective(source,progress,SkillCatalog.require(SunMoonSkills.CYCLE)));
        if(state.heart==2)result.mut(Stat.MANA_REGENERATION,MOON_REGEN[Math.clamp(state.stacks,0,3)]);
        if(state.heart==1&&state.stacks>0)result.modifiers.put("manaRecoveryBlocked",1d);
        return result;
    }
    public static Stats forCast(Stats source,SkillDefinition skill){
        if(!SunMoonSkills.contains(skill.id))return source;var result=source.copy();
        int heart=source.modifiers.getOrDefault("sunMoonHeart",0d).intValue(),stacks=Math.clamp(source.modifiers.getOrDefault("sunMoonStacks",0d).intValue(),0,3),harmony=source.modifiers.getOrDefault("sunMoonHarmony",0d).intValue();
        int aspect=skill.aspect==SkillDefinition.Aspect.SUN?1:skill.aspect==SkillDefinition.Aspect.MOON?2:0;
        boolean reverse=aspect>0&&heart>0&&heart!=aspect&&stacks>0;
        double damage=aspect==1&&heart==1?sunDamage(source.modifiers.getOrDefault("sunMoonCycleRank",1d).intValue(),stacks):0,speed=reverse?REVERSE_SPEED[stacks]:0,penalty=reverse?(aspect==1?.2:.6):1;
        result.modifier("final").mut+=damage-result.modifiers.getOrDefault("sunMoonDamageApplied",0d);
        result.mut(Stat.CAST_SPEED,speed-result.modifiers.getOrDefault("sunMoonSpeedApplied",0d));
        result.modifier("skillProduct").alls*=penalty/result.modifiers.getOrDefault("sunMoonPenaltyApplied",1d);
        result.modifiers.put("sunMoonDamageApplied",damage);result.modifiers.put("sunMoonSpeedApplied",speed);result.modifiers.put("sunMoonPenaltyApplied",penalty);
        boolean useHarmony=aspect==1&&!reverse&&harmony>0;
        // Recompute from the cast's heart snapshot; preview may prepare the same stats twice.
        double manaMultiplier=aspect==1?3d*(heart==1?1d+stacks:1d):1d;
        result.modifiers.put("skillManaMultiplier",reverse||useHarmony?0:manaMultiplier);
        result.modifiers.put("sunMoonUseHarmony",useHarmony?1d:0d);return result;
    }
    public static SunMoonState paid(SunMoonState state,SkillDefinition skill,Stats prepared){
        var next=state.copy();
        String unavailable=unavailableReason(skill,state.heart,state.stacks);if(!unavailable.isEmpty())throw new IllegalArgumentException(unavailable);
        if(skill.id.equals(SunMoonSkills.PARADOX)){if(next.paradox<=0)throw new IllegalArgumentException("尚未获得悖论");next.paradox--;}
        if(prepared.modifiers.getOrDefault("sunMoonUseHarmony",0d)>0){if(next.harmony<=0)throw new IllegalArgumentException("调和状态已改变");next.harmony--;}
        next.revision++;return next;
    }
    public static SunMoonState released(SunMoonState state,SkillDefinition skill,int rank,boolean awakened){
        var next=state.copy();int aspect=skill.aspect==SkillDefinition.Aspect.SUN?1:skill.aspect==SkillDefinition.Aspect.MOON?2:0;
        if(aspect>0&&!skill.id.equals(SunMoonSkills.CORONA)&&!skill.id.equals(SunMoonSkills.STARFALL)){
            boolean reverse=next.heart>0&&next.heart!=aspect;
            if(reverse&&next.stacks==3&&awakened)next.paradox=1;
            int gained=skill.id.equals(SunMoonSkills.REFLUX)||skill.id.equals(SunMoonSkills.FLARE)?3:skill.id.equals(SunMoonSkills.MOON)?moonHearts(rank):1;
            next.stacks=Math.min(3,(reverse?0:next.stacks)+gained);next.heart=aspect;
        }
        if(skill.id.equals(SunMoonSkills.REFLUX))next.harmony=Math.max(next.harmony,refluxHarmony(rank));
        if(skill.id.equals(SunMoonSkills.STARFALL))next.harmony=Math.max(next.harmony,3);
        next.revision++;return next;
    }
}
