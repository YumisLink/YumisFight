package cn.yumis.fight.modules.professions.api;
import java.util.Locale;
/** 草药稀有度；与鱼同一套档位与配色。 */
public enum HerbTier {
    COMMON("普通",0xffffff,6000),
    RARE("稀有",0x65aaff,2500),
    ARTIFACT("神器",0xb787ff,800),
    LEGENDARY("传说",0xff80c8,400),
    EPIC("史诗",0xffa12d,200),
    MYTHIC("神话",0xffd778,100);
    private final String title;
    private final int color;
    private final int weight;
    HerbTier(String title,int color,int weight){
        this.title=title;
        this.color=color;
        this.weight=weight;
    }
    public String title(){return title;}
    public int color(){return color;}
    public int weight(){return weight;}
    /** 普通档有六个品种（见 HerbSpecies），这里返回默认的宁神花作为兜底。 */
    public String itemId(){
        return this==COMMON?"herb_peacebloom":"herb_"+name().toLowerCase(Locale.ROOT);
    }
}
