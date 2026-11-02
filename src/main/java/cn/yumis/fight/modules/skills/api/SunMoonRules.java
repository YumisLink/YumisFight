package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.character.api.*;

/** The same pure heart, cost and cast arithmetic is used by preview and accepted casts. */
public final class SunMoonRules {
    private static final double[] SUN_DAMAGE={0,.5,1.3,2.5},MOON_REGEN={0,1,10,100},REVERSE_SPEED={0,.3,.8,2};
    private SunMoonRules(){}
    public static Stats current(Stats source,Progress progress,SunMoonState state){
        if(!progress.hasJob(JobCatalog.SUN_MOON))return source;var result=source.copy();
        result.modifiers.put("sunMoonHeart",(double)state.heart);result.modifiers.put("sunMoonStacks",(double)state.stacks);result.modifiers.put("sunMoonHarmony",(double)state.harmony);result.modifiers.put("sunMoonParadox",(double)state.paradox);
        if(state.heart==2)result.mut(Stat.MANA_REGENERATION,MOON_REGEN[Math.clamp(state.stacks,0,3)]);
        if(state.heart==1&&state.stacks>0)result.modifiers.put("manaRecoveryBlocked",1d);
        return result;
    }
    public static Stats forCast(Stats source,SkillDefinition skill){
        if(!SunMoonSkills.contains(skill.id))return source;var result=source.copy();
        int heart=source.modifiers.getOrDefault("sunMoonHeart",0d).intValue(),stacks=Math.clamp(source.modifiers.getOrDefault("sunMoonStacks",0d).intValue(),0,3),harmony=source.modifiers.getOrDefault("sunMoonHarmony",0d).intValue();
        int aspect=skill.aspect==SkillDefinition.Aspect.SUN?1:skill.aspect==SkillDefinition.Aspect.MOON?2:0;
        boolean reverse=aspect>0&&heart>0&&heart!=aspect&&stacks>0;
        double damage=aspect==1&&heart==1?SUN_DAMAGE[stacks]:0,speed=reverse?REVERSE_SPEED[stacks]:0,penalty=reverse?(aspect==1?.2:.6):1;
        result.modifier("final").mut+=damage-result.modifiers.getOrDefault("sunMoonDamageApplied",0d);
        result.mut(Stat.CAST_SPEED,speed-result.modifiers.getOrDefault("sunMoonSpeedApplied",0d));
        result.modifier("skillProduct").alls*=penalty/result.modifiers.getOrDefault("sunMoonPenaltyApplied",1d);
        result.modifiers.put("sunMoonDamageApplied",damage);result.modifiers.put("sunMoonSpeedApplied",speed);result.modifiers.put("sunMoonPenaltyApplied",penalty);
        boolean useHarmony=aspect==1&&!reverse&&harmony>0;
        result.modifiers.put("skillManaMultiplier",reverse||useHarmony?0:aspect==1&&heart==1?1d+stacks:1d);
        result.modifiers.put("sunMoonUseHarmony",useHarmony?1d:0d);return result;
    }
    public static SunMoonState paid(SunMoonState state,SkillDefinition skill,Stats prepared){
        var next=state.copy();
        if(skill.id.equals(SunMoonSkills.PARADOX)){if(next.paradox<=0)throw new IllegalArgumentException("尚未获得悖论");next.paradox--;}
        if(prepared.modifiers.getOrDefault("sunMoonUseHarmony",0d)>0){if(next.harmony<=0)throw new IllegalArgumentException("调和状态已改变");next.harmony--;}
        next.revision++;return next;
    }
    public static SunMoonState released(SunMoonState state,SkillDefinition skill,boolean awakened){
        var next=state.copy();int aspect=skill.aspect==SkillDefinition.Aspect.SUN?1:skill.aspect==SkillDefinition.Aspect.MOON?2:0;
        if(aspect>0){
            boolean reverse=next.heart>0&&next.heart!=aspect;
            if(reverse&&next.stacks==3&&awakened)next.paradox=1;
            int gained=skill.id.equals(SunMoonSkills.REFLUX)||skill.id.equals(SunMoonSkills.FLARE)?3:1;
            next.stacks=Math.min(3,(reverse?0:next.stacks)+gained);next.heart=aspect;
        }
        if(skill.id.equals(SunMoonSkills.REFLUX)||skill.id.equals(SunMoonSkills.STARFALL))next.harmony=3;
        next.revision++;return next;
    }
}
