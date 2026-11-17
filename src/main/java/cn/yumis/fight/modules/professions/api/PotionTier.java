package cn.yumis.fight.modules.professions.api;
import java.util.Locale;
/** 药剂稀有度六档；效果与名称后续补充，这里只定义档位、配色与占位时长。 */
public enum PotionTier {
    COMMON("普通",0xffffff,120,0),
    RARE("稀有",0x65aaff,200,0),
    ARTIFACT("神器",0xb787ff,300,1),
    LEGENDARY("传说",0xff80c8,400,1),
    EPIC("史诗",0xffa12d,600,2),
    MYTHIC("神话",0xffd778,900,2);
    private final String title;
    private final int color;
    private final int duration;
    private final int amplifier;
    PotionTier(String title,int color,int duration,int amplifier){
        this.title=title;
        this.color=color;
        this.duration=duration;
        this.amplifier=amplifier;
    }
    public String title(){return title;}
    public int color(){return color;}
    /** 占位时长（游戏刻）；效果定稿后由具体药剂覆盖。 */
    public int duration(){return duration;}
    public int amplifier(){return amplifier;}
    public String itemId(){return "elixir_"+name().toLowerCase(Locale.ROOT);}
}
