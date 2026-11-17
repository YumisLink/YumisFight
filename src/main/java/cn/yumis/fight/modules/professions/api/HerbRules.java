package cn.yumis.fight.modules.professions.api;
/** 草药抽取、经验与"哪些方块能采"的纯规则；不读世界状态，便于单独校验。 */
public final class HerbRules {
    private HerbRules(){}
    /** 每级给高一档稀有度的权重加成比例。 */
    public static final double LUCK_PER_LEVEL=0.15;
    /** 镰刀单次收割的草药数量上限（每次 1 株）。 */
    public static final int PER_HARVEST=1;
    /** 经验：普通 10，每高一档 +10。 */
    public static long experience(HerbTier tier){
        return 10L+10L*tier.ordinal();
    }
    public static HerbTier roll(int level,double random){
        int effective=Math.max(0,level);
        double total=0;
        double[] weights=new double[HerbTier.values().length];
        for(HerbTier tier:HerbTier.values()){
            weights[tier.ordinal()]=tier.weight()*(1+LUCK_PER_LEVEL*effective*tier.ordinal());
            total+=weights[tier.ordinal()];
        }
        double target=Math.max(0,Math.min(1,random))*total;
        double cursor=0;
        for(HerbTier tier:HerbTier.values()){
            cursor+=weights[tier.ordinal()];
            if(target<cursor)return tier;
        }
        return HerbTier.MYTHIC;
    }
    public static double chance(HerbTier tier,int level){
        int effective=Math.max(0,level);
        double total=0;
        for(HerbTier each:HerbTier.values())total+=each.weight()*(1+LUCK_PER_LEVEL*effective*each.ordinal());
        return tier.weight()*(1+LUCK_PER_LEVEL*effective*tier.ordinal())/total;
    }
}
