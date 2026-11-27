package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.character.api.*;
import cn.yumis.fight.modules.dungeons.api.TowerBattleCard;

/** The same pure heart, cost and cast arithmetic is used by preview and accepted casts. */
public final class SunMoonRules {
    private static final double[] SUN_DAMAGE={0,.5,1.3,2.5},MOON_REGEN={0,1,10,100},REVERSE_SPEED={0,.3,.8,2};
    private SunMoonRules(){}
    /** Layers past the calibrated third extrapolate linearly, so equipment-raised caps keep growing smoothly. */
    public static double sunStackMut(int stacks){return stacks<=3?SUN_DAMAGE[Math.clamp(stacks,0,3)]:SUN_DAMAGE[3]+1.2*(stacks-3);}
    public static double moonRegen(int stacks){return stacks<=3?MOON_REGEN[Math.clamp(stacks,0,3)]:MOON_REGEN[3]+90*(stacks-3);}
    public static double reverseSpeed(int stacks){return stacks<=3?REVERSE_SPEED[Math.clamp(stacks,0,3)]:REVERSE_SPEED[3]+1.2*(stacks-3);}
    public static double chantSpeed(Stats stats){
        double speed=Math.max(.01,stats.get(Stat.CAST_SPEED)+stats.modifiers.getOrDefault("sunMoonSpeedApplied",0d));
        if(TowerBattleCard.SUNMOON_ASTRAL_STEP.held(stats))speed*=1+TowerBattleCard.SUNMOON_ASTRAL_STEP.ratio("speed");
        if(stats.modifiers.getOrDefault("sunMoonInLeyline",0d)>0&&TowerBattleCard.SUNMOON_REFLUX_LEYLINES.held(stats))
            speed*=1+TowerBattleCard.SUNMOON_REFLUX_LEYLINES.ratio("speed");
        return speed;
    }
    public static double chantSeconds(SkillDefinition skill,Stats stats){
        double base=skill.chantSeconds;
        if(base<=0)return 0;
        if(skill.id.equals(SunMoonSkills.MOON)&&TowerBattleCard.SUNMOON_MOON_ECHO.held(stats))return 0;
        if(skill.id.equals(SunMoonSkills.STARFALL)&&TowerBattleCard.SUNMOON_STARFALL_AURORA.held(stats))return 0;
        if(skill.id.equals(SunMoonSkills.FLARE)&&stats.modifiers.getOrDefault("sunMoonSupernova",0d)>0)return 0;
        if(skill.id.equals(SunMoonSkills.CORONA)&&TowerBattleCard.SUNMOON_CORONA_COLOSSUS.held(stats))
            return base*(1+TowerBattleCard.SUNMOON_CORONA_COLOSSUS.ratio("slow"));
        return base;
    }
    public static double sunDamage(int rank,int stacks){return rank<=0?0:sunStackMut(stacks)*Math.pow(1.12,rank-1);}
    /** Equipment and career mythic cards raise the heart cap; one layer minimum always applies. */
    public static int stackCap(Stats stats){
        int base=stats.modifiers.getOrDefault("sunMoonStackCap",3d).intValue();
        int bonus=TowerBattleCard.SUNMOON_FIVE_HEARTS.held(stats)?TowerBattleCard.SUNMOON_FIVE_HEARTS.count("cap"):0;
        return Math.max(1,base+bonus);
    }
    public static int paradoxCap(Stats stats){
        return TowerBattleCard.SUNMOON_PARADOX_LOOP.held(stats)?TowerBattleCard.SUNMOON_PARADOX_LOOP.count("cap"):1;
    }
    public static boolean isSupernovaReady(Stats stats,SunMoonState state,double currentMp,double maxMp){
        if(!TowerBattleCard.SUNMOON_FLARE_SUPERNOVA.held(stats)||state==null)return false;
        if(state.heart!=1||state.stacks<stackCap(stats))return false;
        return maxMp>0&&currentMp>=1d&&currentMp<maxMp*TowerBattleCard.SUNMOON_FLARE_SUPERNOVA.ratio("threshold");
    }
    public static boolean inLeyline(SunMoonState state,double x,double y,double z,String dimension,String instance,long now){
        if(state==null||state.leylineUntil<=now)return false;
        if(dimension!=null&&!dimension.equals(state.leylineDimension))return false;
        if(instance!=null&&!instance.equals(state.leylineInstance))return false;
        double dx=x-state.leylineX,dy=y-state.leylineY,dz=z-state.leylineZ;
        double r=TowerBattleCard.SUNMOON_REFLUX_LEYLINES.value("radius");
        return dx*dx+dz*dz<=r*r&&Math.abs(dy)<=4.0;
    }
    public static int moonHearts(int rank){return rank>=10?3:rank>=5?2:1;}
    public static int refluxHarmony(int rank){return 3+(rank>=5?1:0)+(rank>=10?1:0);}
    public static String unavailableReason(SkillDefinition skill,int heart,int stacks){
        if(skill.id.equals(SunMoonSkills.CORONA)&&(heart!=1||stacks<1))return "需要拥有至少一层日曜心才能释放日冕";
        if(skill.id.equals(SunMoonSkills.STARFALL)&&(heart!=2||stacks<1))return "需要拥有月弦心才能释放月见星辉";
        return "";
    }
    public static Stats current(Stats source,Progress progress,SunMoonState state){
        return current(source,progress,state,0L);
    }
    public static Stats current(Stats source,Progress progress,SunMoonState state,long now){
        if(!progress.hasJob(JobCatalog.SUN_MOON))return source;var result=source.copy();
        result.modifiers.put("sunMoonHeart",(double)state.heart);result.modifiers.put("sunMoonStacks",(double)state.stacks);result.modifiers.put("sunMoonHarmony",(double)state.harmony);result.modifiers.put("sunMoonParadox",(double)state.paradox);
        result.modifiers.put("sunMoonCycleRank",(double)SkillLearning.effective(source,progress,SkillCatalog.require(SunMoonSkills.CYCLE)));
        result.modifiers.put("sunMoonSparks",(double)state.sunfireSparks);
        if(state.heart==2)result.mut(Stat.MANA_REGENERATION,moonRegen(state.stacks));
        if(state.heart==1&&state.stacks>0)result.modifiers.put("manaRecoveryBlocked",1d);
        if(now>0&&state.eclipseUntil>now&&TowerBattleCard.SUNMOON_ECLIPSE.held(result)){
            double factor=1+TowerBattleCard.SUNMOON_ECLIPSE.ratio("damage");
            double prev=result.modifiers.getOrDefault("sunMoonEclipseApplied",1d);
            result.modifier("skillProduct").alls*=factor/prev;
            result.modifiers.put("sunMoonEclipseApplied",factor);
        }
        return result;
    }
    public static Stats forCast(Stats source,SkillDefinition skill){
        if(!SunMoonSkills.contains(skill.id))return source;var result=source.copy();
        int heart=source.modifiers.getOrDefault("sunMoonHeart",0d).intValue(),stacks=Math.clamp(source.modifiers.getOrDefault("sunMoonStacks",0d).intValue(),0,stackCap(source)),harmony=source.modifiers.getOrDefault("sunMoonHarmony",0d).intValue();
        int cycleRank=source.modifiers.getOrDefault("sunMoonCycleRank",1d).intValue();
        int aspect=skill.aspect==SkillDefinition.Aspect.SUN?1:skill.aspect==SkillDefinition.Aspect.MOON?2:0;
        boolean reverse=aspect>0&&heart>0&&heart!=aspect&&stacks>0;
        boolean supernova=skill.id.equals(SunMoonSkills.FLARE)&&source.modifiers.getOrDefault("sunMoonSupernovaReady",0d)>0&&TowerBattleCard.SUNMOON_FLARE_SUPERNOVA.held(source);
        boolean sparkCast=skill.id.equals(SunMoonSkills.SUN)&&source.modifiers.getOrDefault("sunMoonSparkCast",0d)>0;
        double damage=0;
        if(aspect==1&&heart==1)damage=sunDamage(cycleRank,stacks);
        else if(skill.id.equals(SunMoonSkills.PARADOX)&&heart==1&&stacks>0&&TowerBattleCard.SUNMOON_PARADOX_POLARITY.held(source))
            damage=sunDamage(cycleRank,stacks)*TowerBattleCard.SUNMOON_PARADOX_POLARITY.ratio("sun");
        double speed=reverse?reverseSpeed(stacks):0;
        double product=reverse&&!TowerBattleCard.SUNMOON_ECLIPSE.held(source)?(aspect==1?.2:.6):1;
        if(supernova)product*=1+TowerBattleCard.SUNMOON_FLARE_SUPERNOVA.ratio("damage");
        if(skill.id.equals(SunMoonSkills.PARADOX)){
            if(heart==2&&stacks>0&&TowerBattleCard.SUNMOON_PARADOX_POLARITY.held(source))
                product*=1+TowerBattleCard.SUNMOON_PARADOX_POLARITY.ratio("moon");
            if(TowerBattleCard.SUNMOON_PARADOX_LOOP.held(source))
                product*=1+TowerBattleCard.SUNMOON_PARADOX_LOOP.ratio("damage");
        }
        result.modifier("final").mut+=damage-result.modifiers.getOrDefault("sunMoonDamageApplied",0d);
        result.modifier("skillProduct").alls*=product/result.modifiers.getOrDefault("sunMoonPenaltyApplied",1d);
        result.modifiers.put("sunMoonDamageApplied",damage);result.modifiers.put("sunMoonSpeedApplied",speed);result.modifiers.put("sunMoonPenaltyApplied",product);
        boolean useHarmony=aspect==1&&!reverse&&!supernova&&!sparkCast&&harmony>0;
        // Recompute from the cast's heart snapshot; preview may prepare the same stats twice.
        double manaMultiplier=aspect==1?3d*(heart==1?1d+stacks:1d):1d;
        result.modifiers.put("skillManaMultiplier",reverse||useHarmony||supernova||sparkCast?0:manaMultiplier);
        result.modifiers.put("sunMoonUseHarmony",useHarmony?1d:0d);
        result.modifiers.put("sunMoonSupernova",supernova?1d:0d);
        return result;
    }
    public static SunMoonState paid(SunMoonState state,SkillDefinition skill,Stats prepared){
        var next=state.copy();
        String unavailable=unavailableReason(skill,state.heart,state.stacks);if(!unavailable.isEmpty())throw new IllegalArgumentException(unavailable);
        if(skill.id.equals(SunMoonSkills.PARADOX)){if(next.paradox<=0)throw new IllegalArgumentException("尚未获得悖论");next.paradox--;}
        if(prepared.modifiers.getOrDefault("sunMoonUseHarmony",0d)>0){if(next.harmony<=0)throw new IllegalArgumentException("调和状态已改变");next.harmony--;}
        next.revision++;return next;
    }
    public static SunMoonState released(SunMoonState state,SkillDefinition skill,int rank,boolean awakened,SkillParameters.Snapshot parameters,Stats stats){
        return released(state,skill,rank,awakened,parameters,stats,0L);
    }
    public static SunMoonState released(SunMoonState state,SkillDefinition skill,int rank,boolean awakened,SkillParameters.Snapshot parameters,Stats stats,long now){
        var next=state.copy();int aspect=skill.aspect==SkillDefinition.Aspect.SUN?1:skill.aspect==SkillDefinition.Aspect.MOON?2:0;
        int cap=stackCap(stats);
        int pCap=paradoxCap(stats);
        boolean sparkCast=skill.id.equals(SunMoonSkills.SUN)&&stats.modifiers.getOrDefault("sunMoonSparkCast",0d)>0;
        if(aspect>0&&!skill.id.equals(SunMoonSkills.CORONA)&&!skill.id.equals(SunMoonSkills.STARFALL)){
            boolean reverse=next.heart>0&&next.heart!=aspect;
            if(reverse&&next.stacks>=cap&&awakened)next.paradox=Math.min(pCap,next.paradox+1);
            if(reverse&&now>0&&TowerBattleCard.SUNMOON_ECLIPSE.held(stats))
                next.eclipseUntil=now+TowerBattleCard.SUNMOON_ECLIPSE.millis("duration");
            int bonus=(int)parameters.get("heartGrantBonus",0);
            int gained=skill.id.equals(SunMoonSkills.REFLUX)||skill.id.equals(SunMoonSkills.FLARE)?3+bonus
                :skill.id.equals(SunMoonSkills.MOON)?(TowerBattleCard.SUNMOON_MOON_ECHO.held(stats)?Math.max(moonHearts(rank),TowerBattleCard.SUNMOON_MOON_ECHO.count("hearts")):moonHearts(rank))+bonus
                :1+bonus;
            next.stacks=Math.min(cap,(reverse?0:next.stacks)+gained);next.heart=aspect;
        }
        if(skill.id.equals(SunMoonSkills.MOON)&&now>0&&TowerBattleCard.SUNMOON_MOON_ECHO.held(stats))
            next.moonHaloUntil=now+TowerBattleCard.SUNMOON_MOON_ECHO.millis("duration");
        if(aspect==1&&!sparkCast&&TowerBattleCard.SUNMOON_SUNFIRE_SPARK.held(stats)){
            next.sunCasts++;
            int every=Math.max(1,TowerBattleCard.SUNMOON_SUNFIRE_SPARK.count("every"));
            if(next.sunCasts>=every){
                next.sunCasts-=every;
                next.sunfireSparks=Math.min(TowerBattleCard.SUNMOON_SUNFIRE_SPARK.count("cap"),next.sunfireSparks+1);
            }
        }
        if(skill.id.equals(SunMoonSkills.REFLUX))next.harmony=Math.max(next.harmony,refluxHarmony(rank)+(int)parameters.get("harmonyGrantBonus",0));
        if(skill.id.equals(SunMoonSkills.STARFALL))next.harmony=Math.max(next.harmony,3+(int)parameters.get("harmonyGrantBonus",0));
        next.revision++;return next;
    }
}
