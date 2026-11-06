package cn.yumis.fight.modules.dungeons.api;

import java.util.*;

/** Rules owned by dungeons; tiers are independent of character and equipment levels. */
public final class WaystoneRules {
    public record Affix(String id,String title,String effect,double minimum,double maximum,String group,boolean enabled){}
    public int firstMonsterLevel=81,levelsPerTier=1,maximumPlayers=8;
    public double statGrowth=.15,tierReward=.10,affixReward=.30,rewardVariance=.20,starterChance=.75;
    public long countdownMillis=10000,deathPenaltyMillis=15000;
    public long[] timeLimitsMillis={300000,480000,900000};
    public int[] tierChanges={3,2,1,-1};
    public int[][] affixCounts={{0,0},{1,2},{3,4},{5,8}};
    public List<Affix> affixes=new ArrayList<>();
    public static WaystoneRules current(){return DungeonCatalog.current().waystones;}
    public static WaystoneRules defaults(){
        var r=new WaystoneRules();
        r.affixes.add(new Affix("sturdy","强韧","monster_health",.20,.30,"",true));
        r.affixes.add(new Affix("ferocious","凶暴","monster_attack",.12,.18,"",true));
        r.affixes.add(new Affix("armored","铁壁","monster_defense",.12,.18,"",true));
        r.affixes.add(new Affix("elemental_barrier","元素壁垒","monster_resistance",4,8,"",true));
        r.affixes.add(new Affix("depletion","枯竭","mana_regeneration",.16,.24,"cycle",true));
        r.affixes.add(new Affix("time_lag","时滞","cooldown_recovery",.12,.18,"cycle",true));
        r.affixes.add(new Affix("quagmire","泥泞","movement_speed",.08,.12,"",true));
        r.affixes.add(new Affix("armor_break","破甲","player_defense",.12,.18,"",true));
        r.affixes.add(new Affix("elemental_erosion","元素侵蚀","player_resistance",4,8,"",true));
        r.affixes.add(new Affix("slow_cast","迟咏","cast_speed",.08,.12,"cycle",true));
        return r;
    }
    public static final Set<String> EFFECTS=Set.of("monster_health","monster_attack","monster_defense","monster_resistance","mana_regeneration","cooldown_recovery","movement_speed","player_defense","player_resistance","cast_speed");
    public void validate(){
        if(firstMonsterLevel<1||levelsPerTier<1||maximumPlayers<1||maximumPlayers>8||countdownMillis<0||deathPenaltyMillis<0)throw new IllegalArgumentException("引路石基础规则无效");
        for(double v:new double[]{statGrowth,tierReward,affixReward,rewardVariance,starterChance})if(!Double.isFinite(v)||v<0)throw new IllegalArgumentException("引路石数值无效");
        if(rewardVariance>1||starterChance>1||timeLimitsMillis==null||timeLimitsMillis.length!=3||tierChanges==null||tierChanges.length!=4||affixCounts==null||affixCounts.length!=4||affixes==null)throw new IllegalArgumentException("引路石规则不完整");
        if(timeLimitsMillis[0]<=0||timeLimitsMillis[1]<=timeLimitsMillis[0]||timeLimitsMillis[2]<=timeLimitsMillis[1])throw new IllegalArgumentException("引路石时限顺序无效");
        Set<String> ids=new HashSet<>();Map<String,Integer> groups=new HashMap<>();int available=0;
        for(var a:affixes){
            if(a==null||a.id()==null||!a.id().matches("[a-z0-9_./-]+")||!ids.add(a.id())||a.title()==null||a.title().isBlank()||!EFFECTS.contains(a.effect())||a.group()==null||!Double.isFinite(a.minimum())||!Double.isFinite(a.maximum())||a.minimum()<0||a.maximum()<a.minimum())throw new IllegalArgumentException("引路石词条定义无效");
            if(!a.effect().endsWith("resistance")&&a.maximum()>=1)throw new IllegalArgumentException("引路石百分比词条须小于100%");
            if(a.effect().endsWith("resistance")&&(a.minimum()!=Math.rint(a.minimum())||a.maximum()!=Math.rint(a.maximum())))throw new IllegalArgumentException("引路石抗性范围须为整数点数");
            if(a.enabled()){if(a.group().isEmpty())available++;else groups.put(a.group(),1);}
        }
        available+=groups.size();
        for(var c:affixCounts)if(c==null||c.length!=2||c[0]<0||c[1]<c[0]||c[1]>8||c[1]>available)throw new IllegalArgumentException("引路石可用词条不足或品质条数无效");
    }
    public int monsterLevel(int tier){return Math.addExact(firstMonsterLevel,Math.multiplyExact(tier-1,levelsPerTier));}
    public double statMultiplier(int tier){double result=Math.pow(1+statGrowth,tier);if(!Double.isFinite(result))throw new IllegalArgumentException("当前阶数超出可计算范围");return result;}
    public double rewardMultiplier(int tier,int count){double result=(1+tierReward*tier)*(1+affixReward*count);if(!Double.isFinite(result))throw new IllegalArgumentException("引路石收益超出可计算范围");return result;}
    public int nextTier(int tier,long elapsed,boolean completed){int change=tierChanges[3];if(completed)for(int i=0;i<3;i++)if(elapsed<=timeLimitsMillis[i]){change=tierChanges[i];break;}return Math.max(1,Math.addExact(tier,change));}
}
