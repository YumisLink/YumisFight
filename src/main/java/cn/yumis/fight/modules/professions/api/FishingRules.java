package cn.yumis.fight.modules.professions.api;
/** 渔获抽取与经验换算；纯函数，不读世界状态，便于单独校验概率。 */
public final class FishingRules {
    private FishingRules(){}
    /** 每级给高一档稀有度的权重加成比例。 */
    public static final double LUCK_PER_LEVEL=0.15;
    /** 神器鱼竿每次渔获出钻石的概率。 */
    public static final double DIAMOND_CHANCE=0.05;
    /** 传说鱼竿每次渔获出黑曜石的概率。 */
    public static final double OBSIDIAN_CHANCE=0.10;
    /** 史诗鱼竿每次渔获出烈焰棒的概率。 */
    public static final double BLAZE_ROD_CHANCE=0.10;
    /** 神话鱼竿每次渔获出下界之星的概率。 */
    public static final double NETHER_STAR_CHANCE=0.01;
    /** 该次渔获是否替换为钻石；random 取 [0,1)。 */
    public static boolean isDiamond(double random){
        return random<DIAMOND_CHANCE;
    }
    /** 该次渔获是否替换为黑曜石；random 取 [0,1)。 */
    public static boolean isObsidian(double random){
        return random<OBSIDIAN_CHANCE;
    }
    /** 该次渔获是否替换为烈焰棒；random 取 [0,1)。 */
    public static boolean isBlazeRod(double random){
        return random<BLAZE_ROD_CHANCE;
    }
    /** 该次渔获是否替换为下界之星；random 取 [0,1)。 */
    public static boolean isNetherStar(double random){
        return random<NETHER_STAR_CHANCE;
    }
    /** 基础经验：每档 +10。 */
    public static long experience(FishTier tier){
        return 20L+10L*tier.ordinal();
    }
    /** random 取 [0,1)，等级越高越偏向高稀有度；0 级即基础权重。 */
    public static FishTier roll(int level,double random){
        int effective=Math.max(0,level);
        double total=0;
        double[] weights=new double[FishTier.values().length];
        for(FishTier tier:FishTier.values()){
            weights[tier.ordinal()]=tier.weight()*(1+LUCK_PER_LEVEL*effective*tier.ordinal());
            total+=weights[tier.ordinal()];
        }
        double target=Math.max(0,Math.min(1,random))*total;
        double cursor=0;
        for(FishTier tier:FishTier.values()){
            cursor+=weights[tier.ordinal()];
            if(target<cursor)return tier;
        }
        return FishTier.MYTHIC;
    }
    /** 某档在给定等级下的实际概率，用于文档与验收核对。 */
    public static double chance(FishTier tier,int level){
        int effective=Math.max(0,level);
        double total=0;
        for(FishTier each:FishTier.values())total+=each.weight()*(1+LUCK_PER_LEVEL*effective*each.ordinal());
        return tier.weight()*(1+LUCK_PER_LEVEL*effective*tier.ordinal())/total;
    }
}
