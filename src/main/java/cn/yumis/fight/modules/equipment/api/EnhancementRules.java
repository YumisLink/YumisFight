package cn.yumis.fight.modules.equipment.api;

import cn.yumis.fight.modules.attributes.api.Stat;
import java.util.*;

/** 强化与增幅的数值、概率和消耗规则；随机结果由本人客户端判定一次并随库存事务提交。 */
public final class EnhancementRules {
    public static final int MAXIMUM=24;
    public static final String OTHERWORLD_AURA="otherworld_aura";
    public static final String INTENSE_AURA="intense_aura";
    public static final String AURA_TRANSFORM_BOX="aura_transform_box";
    public static final int AMPLIFY_GOLD_MULTIPLE=10;
    public static final int AMPLIFY_PINK=0xff66cc;
    private static final long GOLD_BASE=500;
    private static final int SUCCESS_FLOOR=20,SUCCESS_CEILING=100,SUCCESS_STEP=5;
    /** 普通、稀有、神器、传说、史诗、神话、太初的金币倍率。 */
    private static final long[] RARITY_MULTIPLIERS={1,2,3,5,8,12,20};
    public static final List<Stat> DIMENSIONS=List.of(Stat.STRENGTH,Stat.INTELLIGENCE,Stat.SPIRIT);

    private EnhancementRules(){}

    /** 第N级累计收益：+1=1、+2=3、+3=6、+4=10 … */
    public static int cumulative(int level){
        if(level<0||level>MAXIMUM)throw new IllegalArgumentException("强化等级无效");
        return level*(level+1)/2;
    }
    /** 冲+1=100%，每级−5%，冲+17起固定20%。 */
    public static int successRate(int target){
        if(target<1||target>MAXIMUM)throw new IllegalArgumentException("强化目标无效");
        return Math.max(SUCCESS_FLOOR,SUCCESS_CEILING-SUCCESS_STEP*(target-1));
    }
    /** 冲+N 的金币消耗；增幅为同公式10倍。 */
    public static long goldCost(Gear g,int target,boolean amplified){
        if(g.rarity==null)throw new IllegalArgumentException("装备品质无效");
        long cost=Math.multiplyExact(Math.multiplyExact(GOLD_BASE,(long)target*target),RARITY_MULTIPLIERS[g.rarity.ordinal()]);
        return amplified?Math.multiplyExact(cost,AMPLIFY_GOLD_MULTIPLE):cost;
    }
    public static long rarityMultiplier(Rarity rarity){return RARITY_MULTIPLIERS[rarity.ordinal()];}
    /** 冲+N 消耗N个燃料：强化用煤/木炭，增幅用强烈的气息。 */
    public static int fuel(int target){return target;}

    public static boolean contaminated(Gear g){return g.contamination!=null&&!g.contamination.isEmpty();}
    /** 侵染后按增幅等级计算收益；原强化等级冻结作废。 */
    public static int level(Gear g){return contaminated(g)?g.amplifyLevel:g.enhanceLevel;}
    public static boolean amplified(Gear g){return contaminated(g);}
    public static Stat contaminationStat(Gear g){
        if(!contaminated(g))return null;
        try{return Stat.valueOf(g.contamination);}catch(IllegalArgumentException invalid){return null;}
    }

    public enum Outcome{SUCCESS,FAILURE,SHATTER}
    /** 从 current 冲下一级的一次判定；失败后果按目标等级分层。 */
    public static Outcome roll(int current,int successPercent,Random random){
        if(current<0||current>=MAXIMUM)throw new IllegalArgumentException("强化等级无效");
        if(random.nextInt(100)<Math.clamp(successPercent,SUCCESS_FLOOR,SUCCESS_CEILING))return Outcome.SUCCESS;
        return current+1>=MAXIMUM?Outcome.SHATTER:Outcome.FAILURE;
    }
    /** 失败掉级数：冲+10～+19 失败−1，冲+20～+23 失败−2，其余不掉级。 */
    public static int failureLoss(int current){
        int target=current+1;
        if(target>=MAXIMUM)throw new IllegalArgumentException("冲顶失败会粉碎");
        if(target>=20)return 2;
        if(target>=10)return 1;
        return 0;
    }

    public static String rollContamination(Random random){return DIMENSIONS.get(random.nextInt(DIMENSIONS.size())).name();}
    /** 异界气息侵染：随机定向一条异次元三维，之后禁止强化、只能增幅。 */
    public static void contaminate(Gear g,Random random){
        if(contaminated(g))throw new IllegalArgumentException("该装备已被异界气息侵染");
        if(g.rarity==Rarity.PRIMORDIAL)throw new IllegalArgumentException("太初装备为管理员定值模板，不可侵染");
        g.contamination=rollContamination(random);
    }
    /** 气息变换箱：重新随机异次元三维，侵染状态本身不可清除。 */
    public static void transform(Gear g,Random random){
        if(!contaminated(g))throw new IllegalArgumentException("该装备尚未被异界气息侵染");
        g.contamination=rollContamination(random);
    }

    /** 强化/增幅的额外点数贡献：武器/防具按基础面板百分比，首饰与特殊装备三维三线同加，增幅再叠加异次元一条。 */
    public static Map<Stat,Double> contribution(Gear g){return contribution(g,level(g));}
    public static Map<Stat,Double> contribution(Gear g,int level){
        Map<Stat,Double> out=new EnumMap<>(Stat.class);
        if(level<=0)return out;
        double bonus=cumulative(level);
        if(g.slot==Slot.WEAPON){
            addBaseShare(out,g,Stat.PHYSICAL_ATTACK,bonus);
            addBaseShare(out,g,Stat.MAGICAL_ATTACK,bonus);
        }else if(g.slot.armor()){
            addBaseShare(out,g,Stat.EVASION,bonus);
            addBaseShare(out,g,Stat.SHIELD,bonus);
            addBaseShare(out,g,Stat.PHYSICAL_DEFENSE,bonus);
            addBaseShare(out,g,Stat.MAGICAL_DEFENSE,bonus);
        }else{
            for(Stat stat:DIMENSIONS)out.merge(stat,bonus,Double::sum);
        }
        Stat dimensional=contaminationStat(g);
        if(dimensional!=null)out.merge(dimensional,bonus,Double::sum);
        return out;
    }
    private static void addBaseShare(Map<Stat,Double> out,Gear g,Stat stat,double bonusPercent){
        double[] values=g.baseGrowth.get(stat);
        if(values==null||values.length==0)return;
        double base=EquipmentRules.growthValue(values,g.level);
        if(base>0)out.merge(stat,base*bonusPercent/100,Double::sum);
    }
}
