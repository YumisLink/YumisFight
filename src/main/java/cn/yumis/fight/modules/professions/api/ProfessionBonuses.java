package cn.yumis.fight.modules.professions.api;
/** 副职业等级换算的生活被动；只做纯计算，不读取世界状态、不改存档。 */
public final class ProfessionBonuses {
    private ProfessionBonuses(){}
    /** 与 ProfessionRules 的等级上限保持一致。 */
    public static final int MAX_LEVEL=10;
    /** 作物/矿石额外产出概率：每级 2%，10 级 20%。 */
    public static double extraYieldChance(int level){
        return 0.02*clamp(level);
    }
    /** 钓鱼稀有渔获权重加成：每级 1.5%，10 级 15%。 */
    public static double fishingLuck(int level){
        return 0.015*clamp(level);
    }
    /** 熟食额外产出概率：每级 2%，10 级 20%。 */
    public static double cookingYieldChance(int level){
        return 0.02*clamp(level);
    }
    /** 药水时长加成：每级 3%，10 级 30%。 */
    public static double potionDuration(int level){
        return 0.03*clamp(level);
    }
    /** 附魔经验消耗折扣：每级 2%，最多 20%。 */
    public static double enchantCostDiscount(int level){
        return 0.02*clamp(level);
    }
    /** 皮甲耐久消耗折扣：每级 2%，最多 20%。 */
    public static double leatherDurabilityDiscount(int level){
        return 0.02*clamp(level);
    }
    /** 锻造材料返还概率：每级 2%，最多 20%。 */
    public static double smithingRefundChance(int level){
        return 0.02*clamp(level);
    }
    /** 铭文/制皮/机械的工艺额外产出概率：每级 2%，最多 20%。 */
    public static double craftYieldChance(int level){
        return 0.02*clamp(level);
    }
    /** 工业能耗减免：每级 1 点，取整后再减，调用方保证不低于 1。 */
    public static int industryEnergyDiscount(int level){
        return clamp(level);
    }
    private static int clamp(int level){
        return Math.max(0,Math.min(MAX_LEVEL,level));
    }
}
