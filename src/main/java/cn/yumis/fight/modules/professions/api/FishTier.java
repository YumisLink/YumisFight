package cn.yumis.fight.modules.professions.api;
import java.util.Locale;
/** 渔获稀有度；与装备的 Rarity 同名同色，但不依赖 equipment 模块（避免模块环）。 */
public enum FishTier {
    COMMON("普通",0xffffff,6000),
    RARE("稀有",0x65aaff,2500),
    ARTIFACT("神器",0xb787ff,800),
    LEGENDARY("传说",0xff80c8,400),
    EPIC("史诗",0xffa12d,200),
    MYTHIC("神话",0xffd778,100);
    private final String title;
    private final int color;
    private final int weight;
    FishTier(String title,int color,int weight){
        this.title=title;
        this.color=color;
        this.weight=weight;
    }
    private static final java.util.Map<String,FishTier> ITEM_TIERS=itemTiers();
    private static java.util.Map<String,FishTier> itemTiers(){
        var result=new java.util.HashMap<String,FishTier>();
        var fish=java.util.List.of(FishSpecies.COMMON,FishSpecies.RARE,FishSpecies.ARTIFACT_ALL,FishSpecies.LEGENDARY_ALL,FishSpecies.EPIC_ALL,FishSpecies.MYTHIC_ALL);
        var rods=java.util.List.of("fisher_rod","lucky_rod","graphite_rod","tidal_rod","styx_rod","farer_rod");
        for(var tier:values()){for(String id:fish.get(tier.ordinal()))result.put(id,tier);result.put(rods.get(tier.ordinal()),tier);}
        return java.util.Map.copyOf(result);
    }
    public static FishTier itemTier(String id){return ITEM_TIERS.get(id);}
    public String title(){return title;}
    public int color(){return color;}
    /** 权重基数 10000，抽取概率 = weight / 当次总权重。 */
    public int weight(){return weight;}
    public String itemId(){return "fish_"+name().toLowerCase(Locale.ROOT);}
}
