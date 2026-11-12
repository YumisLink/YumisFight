package cn.yumis.fight.modules.professions.api;
import java.util.Locale;
/** 渔获稀有度；与装备的 Rarity 同名同色，但不依赖 equipment 模块（避免模块环）。 */
public enum FishTier {
    COMMON("普通",0xffffff,6000),
    RARE("稀有",0xb36bff,2500),
    ARTIFACT("神器",0xff00f0,800),
    LEGENDARY("传说",0xff8000,400),
    EPIC("史诗",0xffb100,200),
    MYTHIC("神话",0xffd778,100);
    private final String title;
    private final int color;
    private final int weight;
    FishTier(String title,int color,int weight){
        this.title=title;
        this.color=color;
        this.weight=weight;
    }
    public String title(){return title;}
    public int color(){return color;}
    /** 权重基数 10000，抽取概率 = weight / 当次总权重。 */
    public int weight(){return weight;}
    public String itemId(){return "fish_"+name().toLowerCase(Locale.ROOT);}
}
