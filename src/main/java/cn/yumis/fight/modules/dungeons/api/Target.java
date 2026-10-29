package cn.yumis.fight.modules.dungeons.api;
import static cn.yumis.fight.modules.dungeons.api.EncounterScaling.partyMultiplier;

import java.util.*;

import cn.yumis.fight.modules.combat.api.Life;
import cn.yumis.fight.modules.character.api.CharacterRules;

/** dungeons模块拥有的数据合同；仅接收当前存档代际。 */
public class Target implements cn.yumis.fight.modules.combat.api.CombatTarget {
    /** Index 0 is level 1; multipliers are built once when this class is initialized. */
    private static final double[] HEALTH_GROWTH=healthGrowth();
    private static double[] healthGrowth(){
        double[] growth=new double[CharacterRules.MAX_LEVEL];
        growth[0]=1;
        for(int i=1;i<growth.length;i++)growth[i]=growth[i-1]*1.08;
        return growth;
    }
    public UUID id(){return id;}
    public Life life(){return life;}
    public void life(Life value){life=value;}
    public Map<String,Double> contributions(){return contributions;}
    public UUID id;
    public int entityId,originalLevel=1,partySize=1;
    public String dimension="",instance="",kind="NORMAL";
    public Difficulty difficulty=Difficulty.NORMAL;
    public boolean elite,noRewards;
    public String elitePower="";
    public double[] elementalResists={0,0,0,0};
    public double maximumHealth(int playerLevel,boolean sync){return health(level(playerLevel,sync));}
    public String power(){return elitePower;}
    public boolean rewardsEligible(){return !noRewards;}
    public boolean training(){return kind.equals("TRAINING");}
    public boolean consumesHealth(){return !training();}
    @Override public cn.yumis.fight.modules.combat.api.Receipt receive(double fraction,boolean heal){
        if(!training())return cn.yumis.fight.modules.combat.api.CombatTarget.super.receive(fraction,heal);
        if(!Double.isFinite(fraction)||fraction<0)throw new IllegalArgumentException("伤害份额无效");
        life.q=1;life.dead=false;life.revision++;
        return new cn.yumis.fight.modules.combat.api.Receipt(true,"",heal?0:fraction,1,life.revision,"");
    }
    public double defense(int level,boolean magic){return training()?0:(magic?20+5*(level-1):30+8*(level-1))*(elitePower.equals("armor")?1.5:1);}
    public double[] resistances(){double[] copy=elementalResists.clone();if(elitePower.equals("elemental"))for(int i=0;i<4;i++)copy[i]+=30;return copy;}
    public double speciesHealth=1,speciesAttack=1;
    public Life life=new Life();
    public Map<String,Double> contributions=new HashMap<>();
    public int level(int playerLevel,boolean sync){
        return training()?playerLevel:sync?Math.max(originalLevel,playerLevel):originalLevel;
    }
    public double health(int level){
        if(training())return 10000;
        double base=switch(kind){
            case "BOSS","yumis_dungeon_and_fight:dragon"->2000;
            case "WITHER"->1500;
            default->50;
        }
        ;
        double growth=HEALTH_GROWTH[level-1];
        return base*growth*difficulty.health*partyMultiplier(partySize)*(elite?3:1)*speciesHealth*(elitePower.equals("strong")?1.5:1);
    }
    public double attack(int level){
        return (kind.equals("yumis_dungeon_and_fight:dragon")?15+3*(level-1):kind.equals("WITHER")?12.5+2.5*(level-1):kind.equals("BOSS")?10+2*(level-1):5+(level-1))*difficulty.attack*(elite?1.5:1)*speciesAttack*(elitePower.equals("rage")?1.3:1);
    }
}
