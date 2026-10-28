package cn.yumis.fight.modules.dungeons.api;
import static cn.yumis.fight.modules.dungeons.api.EncounterScaling.partyMultiplier;

import java.util.*;

import cn.yumis.fight.modules.combat.api.Life;

/** dungeons模块拥有的数据合同；仅接收当前存档代际。 */
public class Target implements cn.yumis.fight.modules.combat.api.CombatTarget {
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
    public double defense(int level,boolean magic){return (magic?20+5*(level-1):30+8*(level-1))*(elitePower.equals("armor")?1.5:1);}
    public double[] resistances(){double[] copy=elementalResists.clone();if(elitePower.equals("elemental"))for(int i=0;i<4;i++)copy[i]+=30;return copy;}
    public double speciesHealth=1,speciesAttack=1;
    public Life life=new Life();
    public Map<String,Double> contributions=new HashMap<>();
    public int level(int playerLevel,boolean sync){
        return sync?Math.max(originalLevel,playerLevel):originalLevel;
    }
    public double health(int level){
        double n=level-1,base=switch(kind){
            case "BOSS","yumis_dungeon_and_fight:dragon"->2000+1000*n;
            case "WITHER"->1500+750*n;
            default->50+25*n;
        }
        ;
        return base*difficulty.health*partyMultiplier(partySize)*(elite?3:1)*speciesHealth*(elitePower.equals("strong")?1.5:1);
    }
    public double attack(int level){
        return (kind.equals("yumis_dungeon_and_fight:dragon")?15+3*(level-1):kind.equals("WITHER")?12.5+2.5*(level-1):kind.equals("BOSS")?10+2*(level-1):5+(level-1))*difficulty.attack*(elite?1.5:1)*speciesAttack*(elitePower.equals("rage")?1.3:1);
    }
}
