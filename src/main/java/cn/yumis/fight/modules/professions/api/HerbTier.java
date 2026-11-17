package cn.yumis.fight.modules.professions.api;
/** Harvest pool tiers and weights; item presentation uses the shared Rarity system. */
public enum HerbTier {
    COMMON(6000),RARE(2500),ARTIFACT(800),LEGENDARY(400),EPIC(200),MYTHIC(100);
    private final int weight;
    HerbTier(int weight){this.weight=weight;}
    public int weight(){return weight;}
}
