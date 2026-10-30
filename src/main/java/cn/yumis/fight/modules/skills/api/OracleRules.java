package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.character.api.*;
import java.util.*;

/** Shared Oracle resource and modifier arithmetic; input resolution never bypasses skill costs. */
public final class OracleRules {
    private OracleRules(){}
    public static int rank(Stats stats,Progress progress){return progress.hasJob(JobCatalog.ORACLE)?SkillLearning.effective(stats,progress,SkillCatalog.require(OracleSkills.WILL)):0;}
    public static int cap(Progress progress){return progress.learned(OracleSkills.REPLICA)>0?9:8;}
    /** Remember actual learning transitions and continuously measure time outside combat. */
    public static void preparation(OracleState state,Progress progress,boolean outOfCombat,boolean busy,long now){
        if(!progress.hasJob(JobCatalog.ORACLE))return;
        boolean learned=progress.learned(OracleSkills.REPLICA)>0;
        if(!state.awakeningObserved){state.awakeningObserved=true;state.awakeningLearned=learned;}
        else{if(learned&&!state.awakeningLearned)state.awakeningGrantPending=true;state.awakeningLearned=learned;}
        if(!learned||!outOfCombat)state.outOfCombatSince=0;
        else if(state.outOfCombatSince==0||now<state.outOfCombatSince)state.outOfCombatSince=now;
        if(learned&&outOfCombat&&now-state.outOfCombatSince>=60000)state.awakeningGrantPending=true;
        if(!learned)state.awakeningGrantPending=false;
        if(learned&&state.awakeningGrantPending&&!busy){
            state.hermes=9;state.hermesUntil=0;state.instruction=OracleSkills.REPLICA;state.instructionUntil=0;state.awakeningGrantPending=false;
        }
    }
    public static long delay(int rank){return Math.max(100,5000-100L*Math.max(0,rank-1));}
    public static double layer(int rank){return Math.round(Math.pow(1.12,Math.max(0,rank-1)))/100d;}
    public static double weaponReward(int rank){return Math.round(30*Math.pow(1.12,Math.max(0,rank-1)))/100d;}
    public static double weaponMut(OracleState state,int rank,String type){return state.weapon.isEmpty()?0:state.weapon.equals(type)?weaponReward(rank):-.5;}
    public static Stats current(Stats source,Progress progress,OracleState state,long now){
        int rank=rank(source,progress);if(rank<=0)return source;Stats result=source.copy();double weapon=state.castingUntil>now?weaponReward(rank):weaponMut(state,rank,source.weaponValid?source.weaponType:"");
        result.modifier("final").mut+=state.hermes*layer(rank)-(state.karmaUntil>now?state.karma*.05:0)+weapon;
        result.modifiers.put("oracleWeaponMut",weapon);result.modifiers.put("oracleRank",(double)rank);result.modifiers.put("oracleHermes",(double)state.hermes);return result;
    }
    public static boolean matches(OracleState state,SkillDefinition skill,long now){return state.instruction.equals(skill.id)&&(state.hermes==9&&skill.id.equals(OracleSkills.REPLICA)||state.instructionUntil>now);}
    public static boolean succeed(OracleState state,Progress progress,SkillDefinition skill,int rank,long now){
        if(rank<=0||!matches(state,skill,now))return false;
        if(!skill.id.equals(OracleSkills.REPLICA)){state.hermes=Math.min(cap(progress),state.hermes+1);state.hermesUntil=now+30000;}
        state.instruction="";state.instructionUntil=0;state.nextInstruction=now+delay(rank);return true;
    }
    public static boolean bound(Progress progress,OracleState state,int slot,String skill,long now){return slot>=0&&slot<progress.slots.length&&progress.slots[slot].equals(OracleSkills.WILL)&&progress.learned(OracleSkills.WILL)>0&&state.instruction.equals(skill)&&(state.hermes==9&&skill.equals(OracleSkills.REPLICA)||state.instructionUntil>now);}
    public static Stats instruction(Stats source){Stats result=source.copy();double weapon=result.modifiers.getOrDefault("oracleWeaponMut",0d);if(weapon<0){result.modifier("final").mut-=weapon;result.modifiers.put("oracleWeaponMut",0d);}result.modifier("final").mut+=.5;result.modifiers.put("oracleInstruction",1d);return result;}
    public static Stats stepStats(Stats frozen,OracleState state,String actualType,boolean weaponPresent){
        Stats result=frozen.copy();int rank=result.modifiers.getOrDefault("oracleRank",0d).intValue();double current=rank<=0||!weaponPresent?0:weaponReward(rank);
        result.modifier("final").mut+=current-result.modifiers.getOrDefault("oracleWeaponMut",0d)+(weaponPresent?.3:0);result.modifiers.put("oracleWeaponMut",current);
        if(!weaponPresent){if(result.modifiers.getOrDefault("oracleInstruction",0d)>0){result.modifier("final").mut-=.5;result.modifiers.put("oracleInstruction",0d);}result.modifier("skillProduct").alls*=.7*.5;}
        return result;
    }
}
