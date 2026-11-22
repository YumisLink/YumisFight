package cn.yumis.fight.modules.presentation.api;
import java.util.*;
/** Menu content registration and visible tab order. */
public final class PlayerPages {
    public record Page(String id,String title,String icon,String openCommand,boolean screen){}
    private static final List<Page> PAGES=new ArrayList<>();
    static {
        register(new Page("character","角色属性","character","",true));
        register(new Page("skills","职业技能","skills","",true));
        register(new Page("talents","天赋盘","skills","",true));
        register(new Page("tutorial","任务栏","professions","",true));
        register(new Page("guide","玩法看板","job","",true));
        register(new Page("professions","副职业","professions","professions",true));
        register(new Page("party","战斗队伍","party","",true));
        register(new Page("dungeons","地下城","dungeons","",true));
        register(new Page("other_games","其他玩法","dungeons","",true));
        register(new Page("shop","商城","shop","",true));
        register(new Page("bag","次元背包","dimensional_bag","bag",false));
        register(new Page("sale","出售物品","shop","",true));
        register(new Page("affix_compendium","词条宝典","skills","",true));
        register(new Page("damage_analysis","伤害分析","skills","",true));
        register(new Page("settings","设置","settings","",true));
    }
    private PlayerPages(){}
    public static synchronized int register(Page page){
        Objects.requireNonNull(page);if(page.id()==null||page.id().isBlank()||page.title()==null||page.icon()==null||page.openCommand()==null)throw new IllegalArgumentException("Invalid page");
        if(PAGES.stream().anyMatch(existing->existing.id().equals(page.id())))throw new IllegalArgumentException("Duplicate page: "+page.id());if(!page.id().equals("settings")){for(int i=0;i<PAGES.size();i++)if(PAGES.get(i).id().equals("settings")){PAGES.add(i,page);return i;}}PAGES.add(page);return PAGES.size()-1;
    }
    public static synchronized List<Page> all(){return List.copyOf(PAGES);}
    public static Page at(int index){return PAGES.get(index);}
}
