package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.character.api.*;
import cn.yumis.fight.modules.combat.api.CombatBalanceRules;
import cn.yumis.fight.modules.dungeons.api.TowerBattleCard;
import java.util.*;
import java.util.random.RandomGenerator;

/** Shared Oracle resource and modifier arithmetic; input resolution never bypasses skill costs. */
public final class OracleRules {
    public enum CastMode { NONE, INSTRUCTION, HERMES_EMPOWERED }
    public record CastResult(CastMode mode, int replicaStacks) {
        public boolean empowered() { return mode != CastMode.NONE; }
        public boolean fulfilledInstruction() { return mode == CastMode.INSTRUCTION; }
    }

    private OracleRules(){}
    public static int rank(Stats stats,Progress progress){return progress.hasJob(JobCatalog.ORACLE)?SkillLearning.effective(stats,progress,SkillCatalog.require(OracleSkills.WILL)):0;}
    public static int cap(Progress progress){return progress.learned(OracleSkills.REPLICA)>0?9:8;}
    /** Promoted Oracle skills eligible for 指令之意 random skill instructions (excludes base skills and awakening). */
    public static boolean instructionEligible(SkillDefinition skill,Progress progress){
        return skill!=null&&!skill.passive()&&!skill.equipmentGranted&&!skill.cardGranted&&skill.advanced()
            &&!skill.id.equals(OracleSkills.REPLICA)&&skill.available(progress)&&progress.learned(skill.id)>0;
    }
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
            state.hermes=9;state.hermesUntil=0;state.instruction=OracleSkills.REPLICA;state.instructionUntil=0;state.nextForcedInstruction="";state.awakeningGrantPending=false;
        }
    }
    public static long delay(int rank){return Math.max(100,5000-100L*Math.max(0,rank-1));}
    public static long delay(int rank,Stats stats){
        long base=delay(rank);
        if(!TowerBattleCard.ORACLE_WILL_HASTE.held(stats))return base;
        double recovery=stats.get(Stat.COOLDOWN_RECOVERY)*CombatBalanceRules.current().sustain.cooldownRecoveryMultiplier();
        if(recovery<=0)recovery=1;
        double divisor=recovery*(1+stats.modifiers.getOrDefault("cooldownRecovery",0d));
        return Math.max(100L,Math.round(base/Math.max(.01,divisor)));
    }
    public static int hermesGain(Stats stats){
        return TowerBattleCard.ORACLE_HERMES_SURGE.held(stats)?TowerBattleCard.ORACLE_HERMES_SURGE.count("gain"):1;
    }
    public static boolean canCastReplica(Stats stats,Progress progress,OracleState state){
        if(TowerBattleCard.ORACLE_REPLICA_UNLEASHED.held(stats))return progress.hasJob(JobCatalog.ORACLE);
        return rank(stats,progress)>0&&state.hermes>=1;
    }
    public static int replicaStacks(Stats stats,Progress progress,int currentHermes){
        if(TowerBattleCard.ORACLE_REPLICA_UNLEASHED.held(stats))return 9;
        return Math.clamp(currentHermes,1,cap(progress));
    }
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
        return succeed(state,progress,skill,null,rank,now);
    }
    public static boolean succeed(OracleState state,Progress progress,SkillDefinition skill,Stats stats,int rank,long now){
        if(rank<=0||!matches(state,skill,now))return false;
        if(!skill.id.equals(OracleSkills.REPLICA)){state.hermes=Math.min(cap(progress),state.hermes+hermesGain(stats));state.hermesUntil=now+30000;}
        state.instruction="";state.instructionUntil=0;state.nextInstruction=now+delay(rank,stats);return true;
    }
    public static CastResult resolveCast(OracleState state,Progress progress,SkillDefinition skill,Stats stats,long now){
        int rank=rank(stats,progress);
        int beforeHermes=state.hermes;
        if(skill.id.equals(OracleSkills.REPLICA)&&!canCastReplica(stats,progress,state))throw new IllegalArgumentException("至少需要一层代行-赫尔墨斯");
        int stacks=skill.id.equals(OracleSkills.REPLICA)?replicaStacks(stats,progress,beforeHermes):0;
        if(rank>0&&succeed(state,progress,skill,stats,rank,now))return new CastResult(CastMode.INSTRUCTION,stacks);
        if(rank>0&&TowerBattleCard.ORACLE_HERMES_SURGE.held(stats)&&!skill.passive()&&SkillBalanceRules.current().damaging(skill)){
            int cost=TowerBattleCard.ORACLE_HERMES_SURGE.count("cost");
            if(state.hermes>=cost){
                state.hermes=Math.max(0,state.hermes-cost);
                if(state.hermes==0)state.hermesUntil=0;
                else if(state.hermesUntil<=now)state.hermesUntil=now+30000;
                if(state.hermes<9&&OracleSkills.REPLICA.equals(state.instruction)&&state.instructionUntil==0){
                    state.instruction="";
                    state.nextInstruction=now;
                }
                return new CastResult(CastMode.HERMES_EMPOWERED,stacks);
            }
        }
        return new CastResult(CastMode.NONE,stacks);
    }
    public static boolean bound(Progress progress,OracleState state,int slot,String skill,long now){return slot>=0&&slot<progress.slots.length&&progress.slots[slot].equals(OracleSkills.WILL)&&progress.learned(OracleSkills.WILL)>0&&state.instruction.equals(skill)&&(state.hermes==9&&skill.equals(OracleSkills.REPLICA)||state.instructionUntil>now);}
    public static Stats instruction(Stats source){Stats result=source.copy();double weapon=result.modifiers.getOrDefault("oracleWeaponMut",0d);if(weapon<0){result.modifier("final").mut-=weapon;result.modifiers.put("oracleWeaponMut",0d);}result.modifier("final").mut+=.5;result.modifiers.put("oracleInstruction",1d);return result;}
    /** Grants both Skill Instruction (+50%) and full Weapon Instruction (+weaponReward) bonuses, removing any weapon mismatch penalty. */
    public static Stats fullInstruction(Stats source){
        Stats result=source.copy();
        int rank=result.modifiers.getOrDefault("oracleRank",0d).intValue();
        double reward=rank>0?weaponReward(rank):0d;
        double currentWeapon=result.modifiers.getOrDefault("oracleWeaponMut",0d);
        if(currentWeapon<reward){
            result.modifier("final").mut+=reward-currentWeapon;
            result.modifiers.put("oracleWeaponMut",reward);
        }
        if(result.modifiers.getOrDefault("oracleInstruction",0d)<=0){
            result.modifier("final").mut+=.5;
            result.modifiers.put("oracleInstruction",1d);
        }
        return result;
    }
    public static Stats applyCastBonus(Stats source,CastResult result){
        if(result==null)return source;
        return switch(result.mode()){
            case INSTRUCTION->instruction(source);
            case HERMES_EMPOWERED->fullInstruction(source);
            case NONE->source;
        };
    }
    public static Stats castStats(Stats source,SkillDefinition skill){
        if(skill!=null&&OracleSkills.ACTIONS.contains(skill.id)&&"KATANA".equals(source.weaponType)&&TowerBattleCard.ORACLE_KATANA_FLASH.held(source)){
            Stats copy=source.copy();
            copy.mut(Stat.CAST_SPEED,TowerBattleCard.ORACLE_KATANA_FLASH.ratio("speed"));
            return copy;
        }
        return source;
    }
    public static int bluntStunTicks(Stats stats,SkillDefinition skill,boolean boss){
        if(skill==null||!OracleSkills.ACTIONS.contains(skill.id)||!"BLUNT".equals(stats.weaponType)||!TowerBattleCard.ORACLE_BLUNT_QUAKE.held(stats))return 0;
        double seconds=TowerBattleCard.ORACLE_BLUNT_QUAKE.value("stun");
        if(boss)seconds/=TowerBattleCard.ORACLE_BLUNT_QUAKE.value("bossdivisor");
        return Math.max(1,(int)Math.round(seconds*20));
    }
    public static String resetSmallSkillCooldown(OracleState state,Map<String,Long> cooldowns,String castSkillId,boolean fulfilledInstruction,Stats stats,long now,RandomGenerator random){
        if(!TowerBattleCard.ORACLE_CYCLE_RESET.held(stats)||!OracleSkills.ACTIONS.contains(castSkillId))return "";
        var others=new ArrayList<String>();
        boolean selfCooling=false;
        for(String id:OracleSkills.ACTIONS){
            if(cooldowns.getOrDefault(id,0L)>now){
                if(id.equals(castSkillId))selfCooling=true;
                else others.add(id);
            }
        }
        String picked=!others.isEmpty()?others.get(random.nextInt(others.size())):selfCooling?castSkillId:"";
        if(picked.isEmpty())return "";
        cooldowns.remove(picked);
        if(fulfilledInstruction&&state.hermes<9)state.nextForcedInstruction=picked;
        return picked;
    }
    public static boolean pickNextInstruction(OracleState state,Progress progress,Map<String,Long> cooldowns,long localNow,long serverNow,RandomGenerator random){
        if(!state.instruction.isEmpty()||serverNow<state.nextInstruction)return false;
        String forced=state.nextForcedInstruction==null?"":state.nextForcedInstruction;
        if(!forced.isEmpty()){
            state.nextForcedInstruction="";
            if(SkillCatalog.contains(forced)&&instructionEligible(SkillCatalog.require(forced),progress)){
                state.instruction=forced;
                state.instructionUntil=serverNow+10000;
                return true;
            }
        }
        var pool=SkillCatalog.all().stream()
            .filter(s->instructionEligible(s,progress)&&cooldowns.getOrDefault(s.id,0L)-localNow<5000)
            .toList();
        if(pool.isEmpty()){
            state.nextInstruction=serverNow+1000;
            return false;
        }
        state.instruction=pool.get(random.nextInt(pool.size())).id;
        state.instructionUntil=serverNow+10000;
        return true;
    }
    public static Stats stepStats(Stats frozen,OracleState state,String actualType,boolean weaponPresent){
        Stats result=frozen.copy();int rank=result.modifiers.getOrDefault("oracleRank",0d).intValue();double current=rank<=0||!weaponPresent?0:weaponReward(rank);
        result.modifier("final").mut+=current-result.modifiers.getOrDefault("oracleWeaponMut",0d)+(weaponPresent?.3:0);result.modifiers.put("oracleWeaponMut",current);
        if(!weaponPresent){if(result.modifiers.getOrDefault("oracleInstruction",0d)>0){result.modifier("final").mut-=.5;result.modifiers.put("oracleInstruction",0d);}result.modifier("skillProduct").alls*=.7*.5;}
        return result;
    }
}
