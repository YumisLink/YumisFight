// Cosmetic event contract adapted from the user-owned YDNF reference.
package cn.yumis.fight.modules.skills.api;
import static cn.yumis.fight.modules.attributes.api.AttributeNumbers.finite;

import java.util.*;
/** Cosmetic wire contract; no damage, MP or cooldown values belong here. */
public record SkillVisualEvent(UUID caster,UUID session,UUID cast,long generation,String dimension,String instance,
String skill,Phase phase,int sequence,long startedAt,long at,long duration,long seed,
Point origin,Point position,Point direction,double charge,int color,double forwardOffset,double horizontalScale,boolean sweep,String weaponType,double slashAngle) {
    public SkillVisualEvent(UUID caster,UUID session,UUID cast,long generation,String dimension,String instance,String skill,Phase phase,int sequence,long startedAt,long at,long duration,long seed,Point origin,Point position,Point direction,double charge,int color){
        this(caster,session,cast,generation,dimension,instance,skill,phase,sequence,startedAt,at,duration,seed,origin,position,direction,charge,color,0,1,false,"",0);
    }
    public SkillVisualEvent(UUID caster,UUID session,UUID cast,long generation,String dimension,String instance,String skill,Phase phase,int sequence,long startedAt,long at,long duration,long seed,Point origin,Point position,Point direction,double charge,int color,double forwardOffset){
        this(caster,session,cast,generation,dimension,instance,skill,phase,sequence,startedAt,at,duration,seed,origin,position,direction,charge,color,forwardOffset,1,false,"",0);
    }
    public enum Element {
        LIGHT,FIRE,ICE,DARK
    }
    public enum Phase {
        START, RELEASE, SEGMENT, TRAIL, HIT, FINISH, END, CANCEL
    }
    public record Point(double x,double y,double z) {
        public boolean finite(){
            return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z);
        }
    }
    public static final int CYAN=0xB4F2FF,GOLD=0xFFD368,DARK=0x8E56D9;
    public static boolean gilded(SkillDefinition skill){
        return cn.yumis.fight.modules.skills.api.SkillPresentation.require(skill).overdriveColor();
    }
    public static int color(SkillDefinition skill,Element element,boolean overdrive){
        if(overdrive&&gilded(skill))return GOLD;
        if(skill==SkillCatalog.GHOST)return DARK;
        if(element==null)return CYAN;
        return switch(element){
            case LIGHT->0xFFF0AB;
            case FIRE->0xFF713D;
            case ICE->0x69BFFF;
            case DARK->DARK;
        }
        ;
    }
    public boolean valid(){
        return caster!=null&&session!=null&&cast!=null&&generation>0&&dimension!=null&&dimension.length()<=128&&instance!=null&&instance.length()<=128
        &&skill!=null&&SkillVisualRules.known(skill)&&phase!=null
        &&sequence>=0&&sequence<=4096&&startedAt>0&&at>=startedAt&&at-startedAt<=45000&&duration>=0&&duration<=40000
        &&origin!=null&&position!=null&&direction!=null&&origin.finite()&&position.finite()&&direction.finite()
        &&Double.isFinite(charge)&&charge>=0&&charge<=1&&(color&0xFF000000)==0&&Double.isFinite(forwardOffset)&&Double.isFinite(horizontalScale)&&horizontalScale>0&&horizontalScale<=16&&weaponType!=null&&weaponType.length()<=32&&Double.isFinite(slashAngle)&&slashAngle>=0&&slashAngle<=180;
    }
    public SkillVisualEvent cancel(int next,long now){
        return new SkillVisualEvent(caster,session,cast,generation,dimension,instance,skill,Phase.CANCEL,next,startedAt,Math.max(startedAt,now),duration,seed,origin,position,direction,charge,color,forwardOffset,horizontalScale,sweep,weaponType,slashAngle);
    }
}
